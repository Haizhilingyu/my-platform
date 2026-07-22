package com.example.aiagent.chat;

import com.example.aiagent.agent.AgentService;
import com.example.aiagent.agent.event.AgentEvent;
import com.example.aiagent.chat.dto.ChatRequest;
import com.example.aiagent.chat.dto.HistoryMessage;
import com.example.aiagent.config.AgentProperties;
import com.example.common.result.Result;
import com.example.common.security.CurrentUser;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 对话入口。POST {@code /api/ai/chat}，响应 {@code text/event-stream}。
 *
 * <p>走内部 {@code /api} JWT 链，{@link CurrentUser} 即当前登录用户。任何已登录用户可用；工具按其权限受限。
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class ChatController {

  private final AgentService agentService;
  private final AgentProperties properties;
  private final ChatRateLimiter rateLimiter;
  private final ChatHistoryService chatHistoryService;
  private final com.example.sys.SysApi sysApi;

  @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter chat(@RequestBody @Valid ChatRequest request, HttpServletResponse response) {
    // 禁用代理层（Caddy/Cloudflare/nginx）对 SSE 的缓冲，否则流式事件被攒在代理缓冲区，
    // 浏览器直到连接关闭或缓冲区满才能收到数据，表现为「一直 loading」。
    response.setHeader("Cache-Control", "no-cache, no-transform");
    response.setHeader("X-Accel-Buffering", "no");
    response.setHeader("Connection", "keep-alive");
    CurrentUser.UserInfo user = CurrentUser.get();
    SseEmitter emitter = new SseEmitter(60_000L);
    // 限流：同步检查，超出立即返回错误事件，避免占用异步线程与 DeepSeek 调用。
    if (!rateLimiter.tryAcquire(user.userId())) {
      sendQuietly(emitter, AgentEvent.error("请求过于频繁，请稍后再试"));
      emitter.complete();
      return emitter;
    }
    // 入参兜底：既无消息又无二次确认回执，直接拒绝，避免空跑一次 DeepSeek 调用。
    boolean hasMessage = request.getMessage() != null && !request.getMessage().isBlank();
    boolean hasConfirm =
        request.getConfirm() != null
            && request.getConfirm().tool() != null
            && !request.getConfirm().tool().isBlank();
    if (!hasMessage && !hasConfirm) {
      sendQuietly(emitter, AgentEvent.error("消息不能为空"));
      emitter.complete();
      return emitter;
    }
    // 预检：DeepSeek 模式下若 API Key 未配置，同步返回友好提示，不进异步流程（避免 SSE 流被代理缓冲吞掉 error 事件）
    if (properties.isEnabled() && "deepseek".equalsIgnoreCase(properties.getProvider())) {
      String keyHint = checkDeepSeekKey();
      if (keyHint != null) {
        sendQuietly(emitter, AgentEvent.error(keyHint));
        emitter.complete();
        return emitter;
      }
    }
    CompletableFuture.runAsync(
        () -> {
          try {
            CurrentUser.set(user);
            if (!properties.isEnabled()) {
              send(emitter, AgentEvent.error("AI 助手未启用"));
              emitter.complete();
              return;
            }
            // 落库用户消息（confirm 回执的 message 是系统生成的「✓ 确认…」文案，非用户意图——不存）。
            if (hasMessage && !hasConfirm) {
              chatHistoryService.save(user.userId(), "user", request.getMessage());
            }
            // 选最多 3 条与当前消息最相关的历史，喂给大脑做多轮意图理解。
            List<HistoryMessage> relevant =
                hasMessage && !hasConfirm
                    ? chatHistoryService.relevant(user.userId(), request.getMessage())
                    : List.of();
            List<AgentEvent> events =
                agentService.handle(request.getMessage(), relevant, request.getConfirm());
            for (AgentEvent e : events) {
              send(emitter, e);
            }
            // 聚合 result/token 文本落库为 assistant 消息（error/confirm/action/tool 不入库）。
            if (hasMessage) {
              String assistantText = aggregateAssistant(events);
              if (!assistantText.isBlank()) {
                chatHistoryService.save(user.userId(), "assistant", assistantText);
              }
            }
            emitter.complete();
          } catch (Exception ex) {
            sendQuietly(emitter, AgentEvent.error(messageOf(ex)));
            emitter.complete();
          } finally {
            CurrentUser.clear();
          }
        });
    return emitter;
  }

  /** 加载当前用户最近 10 条对话历史（时间升序，含消息 id 供前端单条删除）。 */
  @GetMapping("/chat/history")
  public Result<List<HistoryMessage>> history() {
    return Result.ok(chatHistoryService.recent(CurrentUser.get().userId()));
  }

  /** 单条删除历史消息；不存在或不属于当前用户统一 404（不泄露他人数据存在性）。 */
  @DeleteMapping("/chat/history/{id}")
  public Result<Void> deleteHistoryMessage(@PathVariable Long id) {
    chatHistoryService.delete(CurrentUser.get().userId(), id);
    return Result.ok();
  }

  private void send(SseEmitter emitter, AgentEvent e) throws IOException {
    emitter.send(
        SseEmitter.event()
            .name(e.type())
            .data(e.data() == null ? "" : e.data(), MediaType.APPLICATION_JSON));
  }

  private void sendQuietly(SseEmitter emitter, AgentEvent e) {
    try {
      send(emitter, e);
    } catch (IOException ignored) {
      // 客户端已断开，忽略
    }
  }

  /** 聚合事件序列中 result/token 的文本（按序、换行连接）为 assistant 回复，供落库。 */
  private static String aggregateAssistant(List<AgentEvent> events) {
    StringBuilder sb = new StringBuilder();
    for (AgentEvent e : events) {
      if (("result".equals(e.type()) || "token".equals(e.type())) && e.data() instanceof String s) {
        if (!s.isBlank()) {
          if (!sb.isEmpty()) {
            sb.append('\n');
          }
          sb.append(s);
        }
      }
    }
    return sb.toString();
  }

  /** DeepSeek 模式预检：api-key 为空时返回友好提示，否则返回 null（已就绪）。 */
  private String checkDeepSeekKey() {
    // sys_config 可能有空串记录（种子），空时 fallback 到 properties（环境变量 APP_AI_DEEPSEEK_API_KEY）
    String dbKey = sysApi.getConfig("ai.deepseek.api-key", null);
    String envKey = properties.getDeepseek().getApiKey();
    String apiKey = (dbKey != null && !dbKey.isBlank()) ? dbKey : envKey;
    if (apiKey == null || apiKey.isBlank()) {
      return "AI 助手尚未配置模型 API Key。请前往「系统管理 → 系统配置」设置 ai.deepseek.api-key 后重试。";
    }
    return null;
  }

  private static String messageOf(Exception ex) {
    return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
  }
}

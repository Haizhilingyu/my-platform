package com.example.aiagent.chat;

import com.example.aiagent.agent.AgentService;
import com.example.aiagent.agent.event.AgentEvent;
import com.example.aiagent.chat.dto.ChatRequest;
import com.example.aiagent.config.AgentProperties;
import com.example.common.security.CurrentUser;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
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
            String contextMessage =
                request.getHistory() != null && !request.getHistory().isEmpty()
                    ? buildContext(request.getMessage(), request.getHistory())
                    : request.getMessage();
            for (AgentEvent e : agentService.handle(contextMessage, request.getConfirm())) {
              send(emitter, e);
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

  /** 将历史对话拼成上下文前缀，供大脑做多轮意图理解。仅 Mock/关键词匹配受益；DeepSeek 直接读 history 更精确。 */
  private static String buildContext(
      String currentMessage, List<ChatRequest.HistoryMessage> history) {
    StringBuilder sb = new StringBuilder();
    for (ChatRequest.HistoryMessage h : history) {
      if (h != null && h.text() != null && !h.text().isBlank()) {
        sb.append("user".equals(h.role()) ? "[U] " : "[A] ")
            .append(h.text(), 0, Math.min(h.text().length(), 200))
            .append('\n');
      }
    }
    sb.append("[U] ").append(currentMessage);
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

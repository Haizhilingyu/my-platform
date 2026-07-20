package com.example.aiagent.chat;

import com.example.aiagent.agent.AgentService;
import com.example.aiagent.agent.event.AgentEvent;
import com.example.aiagent.chat.dto.ChatRequest;
import com.example.aiagent.config.AgentProperties;
import com.example.common.security.CurrentUser;
import jakarta.validation.Valid;
import java.io.IOException;
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

  @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter chat(@RequestBody @Valid ChatRequest request) {
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
    CompletableFuture.runAsync(
        () -> {
          try {
            CurrentUser.set(user);
            if (!properties.isEnabled()) {
              send(emitter, AgentEvent.error("AI 助手未启用"));
              emitter.complete();
              return;
            }
            for (AgentEvent e : agentService.handle(request.getMessage(), request.getConfirm())) {
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

  private static String messageOf(Exception ex) {
    return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
  }
}

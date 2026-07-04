package com.example.notify.websocket;

import com.example.common.security.JwtUtil;
import com.example.notify.service.WebSocketSessionRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 消息中心 WebSocket 处理器。
 *
 * <p>握手阶段从 query string 解析 token，校验后绑定 session → userId。 客户端首帧 {@code {"lastSeqReceived": N}}
 * 触发断线重连补播。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyWebSocketHandler extends TextWebSocketHandler {

  private final JwtUtil jwtUtil;
  private final WebSocketSessionRegistry sessionRegistry;
  private final ReplayService replayService;
  private final ObjectMapper objectMapper;

  @Value("${app.notify.replay-ttl-hours:24}")
  private long replayTtlHours;

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    Long userId = authenticate(session);
    if (userId == null) {
      close(session, CloseStatus.POLICY_VIOLATION);
      return;
    }
    sessionRegistry.register(userId, session);
    log.info("WS connected: userId={}, sessionId={}", userId, session.getId());
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    Long userId = (Long) session.getAttributes().get("userId");
    if (userId == null) {
      close(session, CloseStatus.POLICY_VIOLATION);
      return;
    }
    try {
      JsonNode root = objectMapper.readTree(message.getPayload());
      if (root.has(WebSocketMessages.FIELD_LAST_SEQ)) {
        long lastSeq = root.get(WebSocketMessages.FIELD_LAST_SEQ).asLong(0L);
        replayService.replay(userId, lastSeq, replayTtlHours);
      }
    } catch (IOException e) {
      log.warn("WS message parse failed for session {}: {}", session.getId(), e.getMessage());
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    sessionRegistry.unregister(session);
    log.info("WS closed: sessionId={}, status={}", session.getId(), status);
  }

  private Long authenticate(WebSocketSession session) {
    URI uri = session.getUri();
    if (uri == null || uri.getQuery() == null) {
      return null;
    }
    Map<String, String> params = parseQuery(uri.getQuery());
    String token = params.get("token");
    if (token == null || !jwtUtil.isValid(token)) {
      return null;
    }
    try {
      Claims claims = jwtUtil.parse(token);
      return Long.valueOf(claims.getSubject());
    } catch (Exception e) {
      log.warn("WS token parse failed: {}", e.getMessage());
      return null;
    }
  }

  private static Map<String, String> parseQuery(String query) {
    Map<String, String> result = new LinkedHashMap<>();
    for (String pair : query.split("&")) {
      int idx = pair.indexOf('=');
      if (idx > 0) {
        result.put(pair.substring(0, idx), pair.substring(idx + 1));
      }
    }
    return result;
  }

  private static void close(WebSocketSession session, CloseStatus status) {
    try {
      session.close(status);
    } catch (IOException e) {
      log.warn("WS close failed: {}", e.getMessage());
    }
  }
}

package com.example.notify.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * 维护在线用户的 WebSocket 会话集合。
 *
 * <p>每用户可能有多端登录（PC + Mobile），因此使用 {@code Set<WebSocketSession>} 而非单 session。 ConcurrentHashMap +
 * ConcurrentHashMap.newKeySet() 提供并发安全的 add/remove/forEach。
 */
@Component
public class WebSocketSessionRegistry {

  private static final String ATTR_USER_ID = "userId";

  private final ConcurrentHashMap<Long, Set<WebSocketSession>> userSessions =
      new ConcurrentHashMap<>();

  public void register(Long userId, WebSocketSession session) {
    userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
    session.getAttributes().put(ATTR_USER_ID, userId);
  }

  public void unregister(WebSocketSession session) {
    Long userId = (Long) session.getAttributes().get(ATTR_USER_ID);
    if (userId == null) {
      return;
    }
    Set<WebSocketSession> sessions = userSessions.get(userId);
    if (sessions != null) {
      sessions.remove(session);
      if (sessions.isEmpty()) {
        userSessions.remove(userId, sessions);
      }
    }
  }

  public Set<WebSocketSession> getSessions(Long userId) {
    return userSessions.getOrDefault(userId, Set.of());
  }

  public boolean isOnline(Long userId) {
    Set<WebSocketSession> sessions = userSessions.get(userId);
    return sessions != null && !sessions.isEmpty();
  }

  public int onlineUserCount() {
    return userSessions.size();
  }
}

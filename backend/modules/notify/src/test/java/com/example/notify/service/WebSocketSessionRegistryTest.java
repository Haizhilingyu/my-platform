package com.example.notify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

@DisplayName("WebSocket 会话注册表")
class WebSocketSessionRegistryTest {

  @Test
  @DisplayName("register：相同用户多个 session 都被记录")
  void should_trackMultipleSessions_for_sameUser() {
    WebSocketSessionRegistry registry = new WebSocketSessionRegistry();
    WebSocketSession s1 = mockSession(7L);
    WebSocketSession s2 = mockSession(7L);

    registry.register(7L, s1);
    registry.register(7L, s2);

    assertThat(registry.getSessions(7L)).hasSize(2);
    assertThat(registry.isOnline(7L)).isTrue();
  }

  @Test
  @DisplayName("unregister：移除指定 session，保留同用户其他 session")
  void should_removeOnlyTargetSession() {
    WebSocketSessionRegistry registry = new WebSocketSessionRegistry();
    WebSocketSession s1 = mockSession(7L);
    WebSocketSession s2 = mockSession(7L);
    registry.register(7L, s1);
    registry.register(7L, s2);

    registry.unregister(s1);

    assertThat(registry.getSessions(7L)).hasSize(1);
    assertThat(registry.getSessions(7L)).contains(s2);
  }

  @Test
  @DisplayName("unregister：最后一个 session 移除后用户下线")
  void should_markOffline_when_lastSessionRemoved() {
    WebSocketSessionRegistry registry = new WebSocketSessionRegistry();
    WebSocketSession s1 = mockSession(7L);
    registry.register(7L, s1);

    registry.unregister(s1);

    assertThat(registry.isOnline(7L)).isFalse();
    assertThat(registry.getSessions(7L)).isEmpty();
  }

  @Test
  @DisplayName("onlineUserCount：反映当前在线用户数")
  void should_reportOnlineUserCount() {
    WebSocketSessionRegistry registry = new WebSocketSessionRegistry();
    registry.register(1L, mockSession(1L));
    registry.register(2L, mockSession(2L));

    assertThat(registry.onlineUserCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("unregister：未注册 session 安全无 NPE")
  void should_handleUnregister_when_sessionNotRegistered() {
    WebSocketSessionRegistry registry = new WebSocketSessionRegistry();
    WebSocketSession orphan = mock(WebSocketSession.class);
    when(orphan.getAttributes()).thenReturn(new HashMap<>());

    registry.unregister(orphan);

    assertThat(registry.onlineUserCount()).isEqualTo(0);
  }

  private WebSocketSession mockSession(Long userId) {
    WebSocketSession session = mock(WebSocketSession.class);
    Map<String, Object> attrs = new HashMap<>();
    attrs.put("userId", userId);
    when(session.getAttributes()).thenReturn(attrs);
    return session;
  }
}

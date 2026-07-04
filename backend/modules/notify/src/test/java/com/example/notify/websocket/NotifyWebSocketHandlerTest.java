package com.example.notify.websocket;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.example.common.security.JwtUtil;
import com.example.notify.service.WebSocketSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WebSocket 握手认证与首帧补播")
class NotifyWebSocketHandlerTest {

  @Mock private JwtUtil jwtUtil;
  @Mock private WebSocketSessionRegistry sessionRegistry;
  @Mock private ReplayService replayService;
  @Mock private ObjectMapper objectMapper;

  @InjectMocks private NotifyWebSocketHandler handler;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(handler, "replayTtlHours", 24L);
  }

  @Test
  @DisplayName("合法 token：注册会话")
  void should_registerSession_when_validToken() throws Exception {
    WebSocketSession session = mockSession("ws://x/ws/notify?token=abc");
    when(jwtUtil.isValid("abc")).thenReturn(true);
    io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
    when(claims.getSubject()).thenReturn("42");
    when(jwtUtil.parse("abc")).thenReturn(claims);

    handler.afterConnectionEstablished(session);

    verify(sessionRegistry).register(42L, session);
  }

  @Test
  @DisplayName("非法 token：关闭会话，不注册")
  void should_rejectSession_when_invalidToken() throws Exception {
    WebSocketSession session = mockSession("ws://x/ws/notify?token=bad");
    when(jwtUtil.isValid("bad")).thenReturn(false);

    handler.afterConnectionEstablished(session);

    verify(sessionRegistry, never()).register(anyLong(), any());
    verify(session).close(CloseStatus.POLICY_VIOLATION);
  }

  @Test
  @DisplayName("无 token：拒绝连接")
  void should_rejectSession_when_noToken() throws Exception {
    WebSocketSession session = mockSession("ws://x/ws/notify");

    handler.afterConnectionEstablished(session);

    verify(sessionRegistry, never()).register(anyLong(), any());
    verify(session).close(CloseStatus.POLICY_VIOLATION);
  }

  @Test
  @DisplayName("首帧 lastSeqReceived=5：触发补播")
  void should_triggerReplay_when_firstFrameContainsSeq() throws Exception {
    WebSocketSession session = mockSession("ws://x/ws/notify?token=abc");
    Map<String, Object> attrs = new HashMap<>();
    attrs.put("userId", 42L);
    when(session.getAttributes()).thenReturn(attrs);
    when(session.isOpen()).thenReturn(true);
    com.fasterxml.jackson.databind.node.ObjectNode node =
        new ObjectMapper().createObjectNode().put("lastSeqReceived", 5);
    when(objectMapper.readTree(any(String.class))).thenReturn(node);

    handler.handleMessage(session, new TextMessage("{\"lastSeqReceived\":5}"));

    verify(replayService).replay(eq(42L), eq(5L), eq(24L));
  }

  @Test
  @DisplayName("断开连接：从注册表移除")
  void should_unregisterSession_when_closed() throws Exception {
    WebSocketSession session = mock(WebSocketSession.class);

    handler.afterConnectionClosed(session, CloseStatus.NORMAL);

    verify(sessionRegistry).unregister(session);
  }

  private WebSocketSession mockSession(String uri) throws Exception {
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getUri()).thenReturn(new URI(uri));
    when(session.getAttributes()).thenReturn(new HashMap<>());
    when(session.getId()).thenReturn("s1");
    when(session.isOpen()).thenReturn(true);
    return session;
  }
}

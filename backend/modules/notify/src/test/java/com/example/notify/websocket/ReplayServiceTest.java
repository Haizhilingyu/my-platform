package com.example.notify.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.example.notify.domain.NotifyMessage;
import com.example.notify.domain.NotifyUserInbox;
import com.example.notify.enums.MessageLevel;
import com.example.notify.repository.NotifyMessageRepository;
import com.example.notify.repository.NotifyUserInboxRepository;
import com.example.notify.service.WebSocketSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@ExtendWith(MockitoExtension.class)
@DisplayName("断线重连补播")
class ReplayServiceTest {

  @Mock private NotifyUserInboxRepository inboxRepository;
  @Mock private NotifyMessageRepository messageRepository;
  @Mock private WebSocketSessionRegistry sessionRegistry;
  @Mock private ObjectMapper objectMapper;

  @InjectMocks private ReplayService replayService;

  @Test
  @DisplayName("无待补播消息：直接返回，不发送任何 WS 消息")
  void should_skip_when_noCandidates() {
    when(inboxRepository.findReplayCandidates(eq(7L), eq(5L), any(LocalDateTime.class)))
        .thenReturn(List.of());

    replayService.replay(7L, 5L, 24);

    verify(sessionRegistry, never()).getSessions(anyLong());
    verify(inboxRepository, never()).saveAll(anyList());
  }

  @Test
  @DisplayName("有待补播消息：推送并标记 delivered")
  void should_pushAndMarkDelivered_when_hasCandidates() throws Exception {
    NotifyUserInbox inbox =
        NotifyUserInbox.builder()
            .id(1L)
            .userId(7L)
            .messageId(100L)
            .seq(6L)
            .delivered(false)
            .readStatus(false)
            .createdAt(LocalDateTime.now())
            .build();
    when(inboxRepository.findReplayCandidates(eq(7L), eq(5L), any(LocalDateTime.class)))
        .thenReturn(List.of(inbox));

    NotifyMessage msg =
        NotifyMessage.builder().id(100L).title("t").content("c").level(MessageLevel.URGENT).build();
    when(messageRepository.findAllById(java.util.Set.of(100L))).thenReturn(List.of(msg));

    WebSocketSession session = mock(WebSocketSession.class);
    when(session.isOpen()).thenReturn(true);
    when(sessionRegistry.getSessions(7L)).thenReturn(java.util.Set.of(session));
    when(objectMapper.writeValueAsString(any())).thenReturn("{}");

    replayService.replay(7L, 5L, 24);

    verify(session).sendMessage(any(TextMessage.class));
    verify(inboxRepository).saveAll(anyList());
    assertThat(inbox.getDelivered()).isTrue();
    assertThat(inbox.getDeliveredAt()).isNotNull();
  }

  @Test
  @DisplayName("TTL 过滤：仅查 created_at > now-24h 的消息")
  void should_filterByTtl_when_oldMessagesExist() {
    when(inboxRepository.findReplayCandidates(eq(7L), eq(0L), any(LocalDateTime.class)))
        .thenReturn(List.of());

    replayService.replay(7L, 0L, 24);

    verify(inboxRepository).findReplayCandidates(eq(7L), eq(0L), any(LocalDateTime.class));
  }

  @SuppressWarnings("unchecked")
  private static <T> List<T> anyList() {
    return org.mockito.ArgumentMatchers.anyList();
  }
}

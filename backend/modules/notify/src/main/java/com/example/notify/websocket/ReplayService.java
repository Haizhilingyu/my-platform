package com.example.notify.websocket;

import com.example.notify.domain.NotifyMessage;
import com.example.notify.domain.NotifyUserInbox;
import com.example.notify.dto.PushMessage;
import com.example.notify.repository.NotifyMessageRepository;
import com.example.notify.repository.NotifyUserInboxRepository;
import com.example.notify.service.WebSocketSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReplayService {

  private final NotifyUserInboxRepository inboxRepository;
  private final NotifyMessageRepository messageRepository;
  private final WebSocketSessionRegistry sessionRegistry;
  private final ObjectMapper objectMapper;

  @Transactional
  public void replay(Long userId, long lastSeq, long ttlHours) {
    LocalDateTime since = LocalDateTime.now().minusHours(ttlHours);
    List<NotifyUserInbox> candidates = inboxRepository.findReplayCandidates(userId, lastSeq, since);
    if (candidates.isEmpty()) {
      return;
    }
    Set<Long> messageIds =
        candidates.stream()
            .map(NotifyUserInbox::getMessageId)
            .collect(Collectors.toCollection(HashSet::new));
    Map<Long, NotifyMessage> messageCache =
        messageRepository.findAllById(messageIds).stream()
            .collect(Collectors.toMap(NotifyMessage::getId, Function.identity()));

    for (NotifyUserInbox inbox : candidates) {
      NotifyMessage msg = messageCache.get(inbox.getMessageId());
      if (msg == null) {
        continue;
      }
      PushMessage push =
          PushMessage.of(
              msg.getId(), inbox.getSeq(), msg.getLevel(), msg.getTitle(), msg.getContent());
      send(userId, push);
      inbox.setDelivered(true);
      inbox.setDeliveredAt(LocalDateTime.now());
    }
    inboxRepository.saveAll(candidates);
  }

  private void send(Long userId, PushMessage push) {
    for (WebSocketSession session : sessionRegistry.getSessions(userId)) {
      if (!session.isOpen()) {
        continue;
      }
      try {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(push)));
      } catch (IOException e) {
        log.warn(
            "WS push failed: userId={}, msgId={}, err={}",
            userId,
            push.messageId(),
            e.getMessage());
      }
    }
  }
}

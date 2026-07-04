package com.example.notify.service;

import com.example.notify.domain.NotifyMessage;
import com.example.notify.domain.NotifyRecipient;
import com.example.notify.domain.NotifyUserInbox;
import com.example.notify.dto.PublishDTO;
import com.example.notify.dto.PushMessage;
import com.example.notify.enums.MessageLevel;
import com.example.notify.enums.RecipientType;
import com.example.notify.repository.NotifyMessageRepository;
import com.example.notify.repository.NotifyRecipientRepository;
import com.example.notify.repository.NotifyUserInboxRepository;
import com.example.sys.repository.SysUnitRepository;
import com.example.sys.repository.SysUserRepository;
import com.example.sys.repository.SysUserRoleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
public class MessageService {

  private final NotifyMessageRepository messageRepository;
  private final NotifyRecipientRepository recipientRepository;
  private final NotifyUserInboxRepository inboxRepository;
  private final SysUserRepository sysUserRepository;
  private final SysUserRoleRepository sysUserRoleRepository;
  private final SysUnitRepository sysUnitRepository;
  private final WebSocketSessionRegistry sessionRegistry;
  private final ObjectMapper objectMapper;

  @Transactional
  public PublishResult publish(PublishDTO dto, Long senderId) {
    NotifyMessage message =
        NotifyMessage.builder()
            .title(dto.getTitle())
            .content(dto.getContent())
            .level(dto.getLevel())
            .senderId(senderId)
            .businessType(dto.getBusinessType())
            .expireTime(dto.getExpireTime())
            .createdAt(LocalDateTime.now())
            .build();
    message = messageRepository.save(message);
    final Long messageId = message.getId();

    Set<Long> userIds = expandRecipients(dto.getRecipients());
    List<NotifyRecipient> recipientRows =
        dto.getRecipients().stream()
            .map(
                spec ->
                    NotifyRecipient.builder()
                        .messageId(messageId)
                        .recipientType(spec.getType())
                        .recipientId(spec.getId())
                        .build())
            .collect(Collectors.toList());
    recipientRepository.saveAll(recipientRows);

    List<NotifyUserInbox> inserted = new ArrayList<>();
    for (Long userId : userIds) {
      Long nextSeq = nextSeqForUser(userId);
      NotifyUserInbox inbox =
          NotifyUserInbox.builder()
              .userId(userId)
              .messageId(messageId)
              .seq(nextSeq)
              .delivered(false)
              .readStatus(false)
              .createdAt(LocalDateTime.now())
              .build();
      inserted.add(inbox);
    }
    List<NotifyUserInbox> savedInboxes = inboxRepository.saveAll(inserted);

    if (dto.getLevel() == MessageLevel.URGENT) {
      for (NotifyUserInbox inbox : savedInboxes) {
        PushMessage push =
            PushMessage.of(
                messageId,
                inbox.getSeq(),
                message.getLevel(),
                message.getTitle(),
                message.getContent());
        deliver(inbox.getUserId(), push);
      }
    }
    return new PublishResult(messageId, userIds.size());
  }

  private Long nextSeqForUser(Long userId) {
    Long current = inboxRepository.findMaxSeqByUserId(userId);
    return (current == null ? 0L : current) + 1L;
  }

  private Set<Long> expandRecipients(List<PublishDTO.RecipientSpec> specs) {
    Set<Long> userIds = new HashSet<>();
    for (PublishDTO.RecipientSpec spec : specs) {
      if (spec.getType() == RecipientType.USER) {
        userIds.add(spec.getId());
      } else if (spec.getType() == RecipientType.ROLE) {
        sysUserRoleRepository.findByRoleId(spec.getId()).forEach(ur -> userIds.add(ur.getUserId()));
      } else if (spec.getType() == RecipientType.UNIT) {
        Set<Long> unitIds = sysUnitRepository.findDescendantUnitIds(spec.getId());
        if (!unitIds.isEmpty()) {
          sysUserRepository.findByUnitIdIn(unitIds).forEach(u -> userIds.add(u.getId()));
        }
      }
    }
    return userIds;
  }

  private void deliver(Long userId, PushMessage push) {
    for (WebSocketSession session : sessionRegistry.getSessions(userId)) {
      if (!session.isOpen()) {
        continue;
      }
      try {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(push)));
      } catch (IOException e) {
        log.warn(
            "URGENT push failed: userId={}, msgId={}, err={}",
            userId,
            push.messageId(),
            e.getMessage());
      }
    }
  }

  public record PublishResult(Long messageId, int recipientCount) {}
}

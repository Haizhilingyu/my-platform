package com.example.notify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.exception.NotFoundException;
import com.example.notify.domain.NotifyUserInbox;
import com.example.notify.repository.NotifyMessageRepository;
import com.example.notify.repository.NotifyRecipientRepository;
import com.example.notify.repository.NotifyUserInboxRepository;
import com.example.sys.repository.SysUnitRepository;
import com.example.sys.repository.SysUserRepository;
import com.example.sys.repository.SysUserRoleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("收件箱服务")
class InboxServiceTest {

  @Mock private NotifyMessageRepository messageRepository;
  @Mock private NotifyRecipientRepository recipientRepository;
  @Mock private NotifyUserInboxRepository inboxRepository;
  @Mock private SysUserRepository sysUserRepository;
  @Mock private SysUserRoleRepository sysUserRoleRepository;
  @Mock private SysUnitRepository sysUnitRepository;
  @Mock private WebSocketSessionRegistry sessionRegistry;
  @Mock private ObjectMapper objectMapper;

  @InjectMocks private MessageService messageService;

  @Test
  @DisplayName("countUnread：透传仓储返回的未读计数")
  void countUnread_returnsCount() {
    when(inboxRepository.countByUserIdAndReadStatusFalse(7L)).thenReturn(3L);

    assertThat(messageService.countUnread(7L)).isEqualTo(3L);
  }

  @Test
  @DisplayName("markRead：未读消息会被标记为已读并保存")
  void markRead_existingUnread_updatesStatus() {
    NotifyUserInbox inbox = NotifyUserInbox.builder().id(10L).userId(7L).readStatus(false).build();
    when(inboxRepository.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(inbox));

    messageService.markRead(7L, 10L);

    assertThat(inbox.getReadStatus()).isTrue();
    assertThat(inbox.getReadTime()).isNotNull();
    verify(inboxRepository).save(inbox);
  }

  @Test
  @DisplayName("markRead：已读消息不重复保存（幂等）")
  void markRead_alreadyRead_doesNotSave() {
    NotifyUserInbox inbox = NotifyUserInbox.builder().id(10L).userId(7L).readStatus(true).build();
    when(inboxRepository.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(inbox));

    messageService.markRead(7L, 10L);

    verify(inboxRepository, never()).save(any(NotifyUserInbox.class));
  }

  @Test
  @DisplayName("markRead：找不到消息或不属于当前用户 → NotFoundException")
  void markRead_notFound_throws() {
    when(inboxRepository.findByIdAndUserId(99L, 7L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> messageService.markRead(7L, 99L))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  @DisplayName("batchMarkRead：仅未读项被更新")
  void batchMarkRead_filtersAndUpdates() {
    NotifyUserInbox unread = NotifyUserInbox.builder().id(1L).userId(7L).readStatus(false).build();
    NotifyUserInbox alreadyRead =
        NotifyUserInbox.builder().id(2L).userId(7L).readStatus(true).build();
    when(inboxRepository.findByIdInAndUserId(List.of(1L, 2L), 7L))
        .thenReturn(List.of(unread, alreadyRead));

    messageService.batchMarkRead(7L, List.of(1L, 2L));

    assertThat(unread.getReadStatus()).isTrue();
    assertThat(unread.getReadTime()).isNotNull();
    assertThat(alreadyRead.getReadStatus()).isTrue();
    assertThat(alreadyRead.getReadTime()).isNull();
    verify(inboxRepository).saveAll(anyList());
  }
}

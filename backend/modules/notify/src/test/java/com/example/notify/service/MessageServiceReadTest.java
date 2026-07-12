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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 消息服务-已读/批量已读单元测试（独立类，避免与 publish 测试的 setUp stub 冲突）。 */
@ExtendWith(MockitoExtension.class)
@DisplayName("消息服务-已读/批量已读")
class MessageServiceReadTest {

  @Mock private NotifyMessageRepository messageRepository;
  @Mock private NotifyRecipientRepository recipientRepository;
  @Mock private NotifyUserInboxRepository inboxRepository;
  @Mock private SysUserRepository sysUserRepository;
  @Mock private SysUserRoleRepository sysUserRoleRepository;
  @Mock private SysUnitRepository sysUnitRepository;
  @Mock private WebSocketSessionRegistry sessionRegistry;
  @Mock private ObjectMapper objectMapper;

  @InjectMocks private MessageService messageService;

  @Nested
  @DisplayName("标记单条已读")
  class MarkRead {

    @Test
    @DisplayName("收件箱消息不存在：抛出 NotFoundException")
    void should_throwNotFound_when_inboxMissing() {
      // Given
      when(inboxRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.empty());

      // When & Then
      assertThatThrownBy(() -> messageService.markRead(1L, 5L))
          .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("存在且未读：置为已读并保存")
    void should_markRead_when_exists() {
      // Given
      NotifyUserInbox inbox = NotifyUserInbox.builder().id(5L).userId(1L).readStatus(false).build();
      when(inboxRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(inbox));

      // When
      messageService.markRead(1L, 5L);

      // Then
      assertThat(inbox.getReadStatus()).isTrue();
      verify(inboxRepository).save(inbox);
    }

    @Test
    @DisplayName("已读消息再次标记：幂等不调用 save")
    void should_notSave_when_alreadyRead() {
      // Given
      NotifyUserInbox inbox = NotifyUserInbox.builder().id(5L).userId(1L).readStatus(true).build();
      when(inboxRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(inbox));

      // When
      messageService.markRead(1L, 5L);

      // Then
      verify(inboxRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("批量标记已读")
  class BatchMarkRead {

    @Test
    @DisplayName("仅更新未读项，已读项保持并一并保存")
    void should_updateOnlyUnread() {
      // Given
      NotifyUserInbox unread =
          NotifyUserInbox.builder().id(1L).userId(1L).readStatus(false).build();
      NotifyUserInbox read = NotifyUserInbox.builder().id(2L).userId(1L).readStatus(true).build();
      when(inboxRepository.findByIdInAndUserId(List.of(1L, 2L), 1L))
          .thenReturn(List.of(unread, read));

      // When
      messageService.batchMarkRead(1L, List.of(1L, 2L));

      // Then
      assertThat(unread.getReadStatus()).isTrue();
      assertThat(read.getReadStatus()).isTrue();
      verify(inboxRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("查询结果为空（ID 不属于该用户）：saveAll 收到空列表，不抛异常")
    void should_saveEmptyList_when_noMatchingIds() {
      // Given
      when(inboxRepository.findByIdInAndUserId(List.of(99L), 1L)).thenReturn(List.of());

      // When
      messageService.batchMarkRead(1L, List.of(99L));

      // Then
      verify(inboxRepository).saveAll(List.of());
    }
  }
}

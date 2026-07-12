package com.example.notify.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.result.PageResult;
import com.example.common.result.Result;
import com.example.common.security.CurrentUser;
import com.example.notify.dto.BatchReadDTO;
import com.example.notify.dto.InboxVO;
import com.example.notify.dto.PublishDTO;
import com.example.notify.enums.MessageLevel;
import com.example.notify.service.MessageService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

/** InternalNotifyController API 集成测试（管理端，依赖当前登录用户）。 */
@DisplayName("InternalNotifyController 请求-响应链路")
class InternalNotifyControllerTest {

  private final MessageService messageService = mock(MessageService.class);
  private final InternalNotifyController controller = new InternalNotifyController(messageService);

  @AfterEach
  void clearUser() {
    CurrentUser.clear();
  }

  private void asUser(long userId) {
    CurrentUser.set(new CurrentUser.UserInfo(userId, "u", userId, Set.of(), Set.of()));
  }

  @Test
  @DisplayName("publish：以当前登录用户 ID 作为 senderId 调用服务")
  void publish_usesCurrentUserId_asSender() {
    asUser(1L);
    when(messageService.publish(any(PublishDTO.class), eq(1L)))
        .thenReturn(new MessageService.PublishResult(100L, 2));

    Result<MessageService.PublishResult> result = controller.publish(new PublishDTO());

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.data().messageId()).isEqualTo(100L);
    verify(messageService).publish(any(PublishDTO.class), eq(1L));
  }

  @Test
  @DisplayName("inbox：以当前用户 ID 查询并返回分页结果")
  void inbox_queriesForCurrentUser() {
    asUser(1L);
    Page<InboxVO> page = new PageImpl<>(List.of(new InboxVO()));
    when(messageService.queryInbox(eq(1L), any(), any(), any(), anyInt(), anyInt()))
        .thenReturn(page);

    Result<PageResult<InboxVO>> result = controller.inbox(MessageLevel.NORMAL, true, "k", 1, 10);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.data().list()).hasSize(1);
    verify(messageService).queryInbox(eq(1L), any(), any(), any(), anyInt(), anyInt());
  }

  @Test
  @DisplayName("unreadCount：以当前用户 ID 统计未读")
  void unreadCount_usesCurrentUserId() {
    asUser(1L);
    when(messageService.countUnread(1L)).thenReturn(3L);

    Result<Long> result = controller.unreadCount();

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.data()).isEqualTo(3L);
    verify(messageService).countUnread(1L);
  }

  @Test
  @DisplayName("markRead：以当前用户 ID 标记单条已读")
  void markRead_usesCurrentUserId() {
    asUser(1L);

    Result<Void> result = controller.markRead(5L);

    assertThat(result.isSuccess()).isTrue();
    verify(messageService).markRead(1L, 5L);
  }

  @Test
  @DisplayName("batchMarkRead：以当前用户 ID 批量标记已读并透传 ID 列表")
  void batchMarkRead_usesCurrentUserId() {
    asUser(1L);
    BatchReadDTO dto = new BatchReadDTO();
    dto.setIds(List.of(1L, 2L));

    Result<Void> result = controller.batchMarkRead(dto);

    assertThat(result.isSuccess()).isTrue();
    verify(messageService).batchMarkRead(1L, List.of(1L, 2L));
  }
}

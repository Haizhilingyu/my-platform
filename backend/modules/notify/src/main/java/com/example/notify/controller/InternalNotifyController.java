package com.example.notify.controller;

import com.example.common.result.PageResult;
import com.example.common.result.Result;
import com.example.common.security.CurrentUser;
import com.example.common.security.RequiresPermission;
import com.example.common.web.PageUtils;
import com.example.notify.dto.BatchReadDTO;
import com.example.notify.dto.InboxVO;
import com.example.notify.dto.PublishDTO;
import com.example.notify.enums.MessageLevel;
import com.example.notify.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端消息接口。
 *
 * <p>发布接口要求 {@code sys:notify:publish} 权限；收件箱查询/已读接口仅需登录（用户只能访问自己的收件箱）。
 */
@RestController
@RequestMapping("/api/sys/notify")
@RequiredArgsConstructor
public class InternalNotifyController {

  private final MessageService messageService;

  @RequiresPermission("sys:notify:publish")
  @PostMapping("/publish")
  public Result<MessageService.PublishResult> publish(@RequestBody @Valid PublishDTO dto) {
    Long senderId = CurrentUser.getUserId();
    return Result.ok(messageService.publish(dto, senderId));
  }

  @GetMapping("/inbox")
  public Result<PageResult<InboxVO>> inbox(
      @RequestParam(required = false) MessageLevel level,
      @RequestParam(required = false) Boolean readStatus,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") int pageNum,
      @RequestParam(defaultValue = "10") int pageSize) {
    Long userId = CurrentUser.getUserId();
    var page = messageService.queryInbox(userId, level, readStatus, keyword, pageNum, pageSize);
    return Result.ok(PageUtils.toPageResult(page));
  }

  @GetMapping("/inbox/unread-count")
  public Result<Long> unreadCount() {
    return Result.ok(messageService.countUnread(CurrentUser.getUserId()));
  }

  @PutMapping("/inbox/{id}/read")
  public Result<Void> markRead(@PathVariable Long id) {
    messageService.markRead(CurrentUser.getUserId(), id);
    return Result.ok();
  }

  @PutMapping("/inbox/batch-read")
  public Result<Void> batchMarkRead(@RequestBody @Valid BatchReadDTO dto) {
    messageService.batchMarkRead(CurrentUser.getUserId(), dto.getIds());
    return Result.ok();
  }
}

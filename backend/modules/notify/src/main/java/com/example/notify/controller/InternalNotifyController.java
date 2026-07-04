package com.example.notify.controller;

import com.example.common.result.Result;
import com.example.common.security.CurrentUser;
import com.example.common.security.RequiresPermission;
import com.example.notify.dto.PublishDTO;
import com.example.notify.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端消息发布接口。
 *
 * <p>仅平台内部使用，要求登录且持有 {@code sys:notify:publish} 权限。
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
}

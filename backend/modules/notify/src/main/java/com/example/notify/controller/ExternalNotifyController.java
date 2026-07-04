package com.example.notify.controller;

import com.example.common.result.Result;
import com.example.notify.dto.PublishDTO;
import com.example.notify.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对外开放 API（供外部应用/SDK 调用）。
 *
 * <p>TODO: T16 will add @RequiresAppScope("notify:publish") for OAuth2 / App Scope protection. For
 * now the endpoint is open; protect at gateway or wire T16 before production exposure.
 */
@RestController
@RequestMapping("/openapi/notify")
@RequiredArgsConstructor
public class ExternalNotifyController {

  private final MessageService messageService;

  // TODO: T16 will add @RequiresAppScope("notify:publish")
  @PostMapping("/publish")
  public Result<MessageService.PublishResult> publish(
      @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
      @RequestBody @Valid PublishDTO dto) {
    return Result.ok(messageService.publish(dto, null));
  }
}

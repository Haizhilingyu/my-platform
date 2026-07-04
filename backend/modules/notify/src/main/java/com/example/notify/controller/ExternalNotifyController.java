package com.example.notify.controller;

import com.example.common.result.Result;
import com.example.common.security.RequiresAppScope;
import com.example.notify.dto.PublishDTO;
import com.example.notify.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 对外开放 API（供外部应用/SDK 调用）。需 OAuth2 access_token 携带 {@code notify:publish} scope。 */
@RestController
@RequestMapping("/openapi/notify")
@RequiredArgsConstructor
public class ExternalNotifyController {

  private final MessageService messageService;

  @RequiresAppScope("notify:publish")
  @PostMapping("/publish")
  public Result<MessageService.PublishResult> publish(
      @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
      @RequestBody @Valid PublishDTO dto) {
    return Result.ok(messageService.publish(dto, null));
  }
}

package com.example.notify.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.result.Result;
import com.example.notify.dto.PublishDTO;
import com.example.notify.service.MessageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ExternalNotifyController API 集成测试（对外 OpenAPI，需 OAuth2 scope）。 */
@DisplayName("ExternalNotifyController 请求-响应链路")
class ExternalNotifyControllerTest {

  private final MessageService messageService = mock(MessageService.class);
  private final ExternalNotifyController controller = new ExternalNotifyController(messageService);

  @Test
  @DisplayName("publish：忽略 X-Api-Key，以 senderId=null 调用服务并返回 PublishResult")
  void publish_forwardsToService_withNullSender() {
    // Given
    when(messageService.publish(any(PublishDTO.class), isNull()))
        .thenReturn(new MessageService.PublishResult(100L, 1));

    // When
    Result<MessageService.PublishResult> result = controller.publish("key-123", new PublishDTO());

    // Then
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.data().messageId()).isEqualTo(100L);
    assertThat(result.data().recipientCount()).isEqualTo(1);
    verify(messageService).publish(any(PublishDTO.class), isNull());
  }
}

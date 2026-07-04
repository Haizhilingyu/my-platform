package com.example.clientsdk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from {@code POST /openapi/notify/publish}. The platform wraps the payload in a {@code
 * Result} envelope: {@code {code, msg, data}}. This class binds the inner {@code data} object.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PublishResponse {

  @JsonProperty("messageId")
  private Long messageId;

  @JsonProperty("recipientCount")
  private Integer recipientCount;

  public Long getMessageId() {
    return messageId;
  }

  public void setMessageId(Long messageId) {
    this.messageId = messageId;
  }

  public Integer getRecipientCount() {
    return recipientCount;
  }

  public void setRecipientCount(Integer recipientCount) {
    this.recipientCount = recipientCount;
  }
}

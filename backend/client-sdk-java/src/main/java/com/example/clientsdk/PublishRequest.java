package com.example.clientsdk;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Request body for {@code POST /openapi/notify/publish}. Recipients is a list because the platform
 * supports fan-out to multiple user/role/unit targets in a single publish call.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PublishRequest {

  private String title;
  private String content;
  private MessageLevel level;

  @JsonProperty("businessType")
  private String businessType;

  @JsonProperty("expireTime")
  private String expireTime;

  private List<Recipient> recipients = new ArrayList<>();

  public PublishRequest() {}

  public static PublishRequest urgent(String title, String content) {
    PublishRequest req = new PublishRequest();
    req.title = title;
    req.content = content;
    req.level = MessageLevel.URGENT;
    return req;
  }

  public PublishRequest addRecipient(RecipientType type, long id) {
    recipients.add(new Recipient(type, id));
    return this;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public MessageLevel getLevel() {
    return level;
  }

  public void setLevel(MessageLevel level) {
    this.level = level;
  }

  public String getBusinessType() {
    return businessType;
  }

  public void setBusinessType(String businessType) {
    this.businessType = businessType;
  }

  public String getExpireTime() {
    return expireTime;
  }

  public void setExpireTime(String expireTime) {
    this.expireTime = expireTime;
  }

  public List<Recipient> getRecipients() {
    return recipients;
  }

  public void setRecipients(List<Recipient> recipients) {
    this.recipients = recipients;
  }

  public static class Recipient {
    private RecipientType type;
    private Long id;

    public Recipient() {}

    public Recipient(RecipientType type, Long id) {
      this.type = type;
      this.id = id;
    }

    public RecipientType getType() {
      return type;
    }

    public void setType(RecipientType type) {
      this.type = type;
    }

    public Long getId() {
      return id;
    }

    public void setId(Long id) {
      this.id = id;
    }
  }
}

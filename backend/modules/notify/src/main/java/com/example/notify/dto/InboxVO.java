package com.example.notify.dto;

import com.example.notify.enums.MessageLevel;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class InboxVO {
  private Long id;
  private Long messageId;
  private Long seq;
  private String title;
  private String content;
  private MessageLevel level;
  private String businessType;
  private Boolean readStatus;
  private LocalDateTime createdAt;
}

package com.example.notify.dto;

import com.example.notify.enums.MessageLevel;
import com.example.notify.enums.RecipientType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class PublishDTO {

  @NotBlank private String title;

  @NotBlank private String content;

  @NotNull private MessageLevel level;

  private String businessType;

  private LocalDateTime expireTime;

  @NotEmpty @Valid private List<RecipientSpec> recipients;

  @Data
  public static class RecipientSpec {
    @NotNull private RecipientType type;

    @NotNull private Long id;
  }
}

package com.example.aiagent.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 对话请求。 */
@Data
public class ChatRequest {

  @NotBlank(message = "消息不能为空")
  @Size(max = 1000, message = "消息最长 1000 字符")
  private String message;
}

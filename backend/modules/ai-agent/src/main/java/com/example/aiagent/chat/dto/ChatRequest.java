package com.example.aiagent.chat.dto;

import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/** 对话请求。 */
@Data
public class ChatRequest {

  /** 用户消息。发起对话时必填；二次确认回执（confirm 非空）时可留空。 */
  @Size(max = 1000, message = "消息最长 1000 字符")
  private String message;

  /** 二次确认回执：客户端对破坏性工具点「执行」后，回传大脑建议的工具调用；服务端直接执行（不再过大脑），
   * 避免重复 LLM 调用与决策漂移。 */
  private ConfirmTool confirm;

  /** 历史对话上下文（最近 N 条），供大脑做意图理解与多轮交互。可为空。 */
  private List<HistoryMessage> history;

  /** 二次确认携带的工具调用。 */
  public record ConfirmTool(String tool, java.util.Map<String, Object> args) {}

  /** 历史消息。 */
  public record HistoryMessage(String role, String text) {}
}

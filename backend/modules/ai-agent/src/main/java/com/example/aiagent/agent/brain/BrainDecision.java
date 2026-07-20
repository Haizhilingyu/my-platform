package com.example.aiagent.agent.brain;

import java.util.Map;

/**
 * 大脑决策。
 *
 * @param replyText 直接回复文本（无工具调用时）
 * @param toolName 要调用的工具名（有工具调用时）
 * @param toolArgs 工具参数
 */
public record BrainDecision(String replyText, String toolName, Map<String, Object> toolArgs) {

  public static BrainDecision reply(String text) {
    return new BrainDecision(text, null, Map.of());
  }

  public static BrainDecision tool(String name, Map<String, Object> args) {
    return new BrainDecision(null, name, args);
  }

  public boolean hasToolCall() {
    return toolName != null && !toolName.isBlank();
  }
}

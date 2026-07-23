package com.example.aiagent.agent.brain;

import com.example.aiagent.chat.dto.HistoryMessage;
import com.example.common.ai.AgentTool;
import java.util.List;

/**
 * Agent 大脑：决定针对用户消息调用哪个工具（或直接回复）。
 *
 * <p>隔离 LLM 的可替换点。当前实现 {@link MockAgentBrain}（无需真实 provider）；后续接 Spring AI 时新增实现。
 */
public interface AgentBrain {

  /**
   * @param userMessage 用户消息
   * @param availableTools 当前用户可见的工具集合（已按权限筛选）
   * @param relevantHistory 与当前消息最相关的历史对话（时间正序，已截断），供多轮意图理解
   */
  BrainDecision decide(
      String userMessage, List<AgentTool> availableTools, List<HistoryMessage> relevantHistory);

  /** 工具执行后，用自然语言向用户总结结果。返回空串表示无需额外总结（如 Mock 直接展示工具结果）。 */
  String summarize(String userMessage, String toolName, String toolResult);
}

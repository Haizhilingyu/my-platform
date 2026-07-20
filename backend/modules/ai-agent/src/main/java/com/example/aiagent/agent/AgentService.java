package com.example.aiagent.agent;

import com.example.aiagent.agent.brain.AgentBrain;
import com.example.aiagent.agent.brain.BrainDecision;
import com.example.aiagent.agent.event.AgentEvent;
import com.example.aiagent.agent.tool.AgentTool;
import com.example.aiagent.agent.tool.ToolRegistry;
import com.example.aiagent.agent.tool.ToolResult;
import com.example.common.exception.ForbiddenException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Agent 编排：取当前用户可见工具 → 大脑决策 →（若调用工具）仅在该集合内查找并执行 → 产出事件序列。
 *
 * <p>权限双层保障：工具可见性筛选在 {@link ToolRegistry}；执行器内部兜底校验在工具执行体。
 */
@Service
@RequiredArgsConstructor
public class AgentService {

  private final ToolRegistry toolRegistry;
  private final AgentBrain brain;

  public List<AgentEvent> handle(String userMessage) {
    List<AgentEvent> events = new ArrayList<>();
    List<AgentTool> tools = toolRegistry.toolsForCurrentUser();
    BrainDecision decision = brain.decide(userMessage, tools);

    if (decision.hasToolCall()) {
      AgentTool tool =
          tools.stream()
              .filter(t -> t.name().equals(decision.toolName()))
              .findFirst()
              .orElseThrow(
                  () -> ForbiddenException.i18n("error.permission.denied", decision.toolName()));
      events.add(AgentEvent.tool(tool.name(), decision.toolArgs()));
      ToolResult result;
      try {
        result = tool.execute().apply(decision.toolArgs());
      } catch (Exception ex) {
        events.add(AgentEvent.error(messageOf(ex)));
        events.add(AgentEvent.done());
        return events;
      }
      events.add(
          result.success()
              ? AgentEvent.result(result.message())
              : AgentEvent.error(result.message()));
      String summary = brain.summarize(userMessage, tool.name(), result.message());
      if (summary != null && !summary.isBlank()) {
        events.add(AgentEvent.token(summary));
      }
      if (result.success() && result.navigatePath() != null) {
        events.add(AgentEvent.action(result.navigatePath(), result.resourceId()));
      }
    } else {
      events.add(AgentEvent.token(decision.replyText()));
    }
    events.add(AgentEvent.done());
    return events;
  }

  private static String messageOf(Exception ex) {
    return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
  }
}

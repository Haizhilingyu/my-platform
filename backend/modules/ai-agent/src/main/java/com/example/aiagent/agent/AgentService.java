package com.example.aiagent.agent;

import com.example.aiagent.agent.brain.AgentBrain;
import com.example.aiagent.agent.brain.BrainDecision;
import com.example.aiagent.agent.event.AgentEvent;
import com.example.aiagent.agent.tool.AgentTool;
import com.example.aiagent.agent.tool.ToolRegistry;
import com.example.aiagent.agent.tool.ToolResult;
import com.example.aiagent.chat.dto.ChatRequest;
import com.example.aiagent.chat.dto.HistoryMessage;
import com.example.common.exception.ForbiddenException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Agent 编排：取当前用户可见工具 → 大脑决策 →（若调用工具）仅在该集合内查找并执行 → 产出事件序列。
 *
 * <p>权限双层保障：工具可见性筛选在 {@link ToolRegistry}；执行器内部兜底校验在工具执行体。
 *
 * <p>破坏性工具（删除/覆盖类）走「二次确认」：先发 {@code confirm} 事件请用户确认，客户端点「执行」后回传 {@link
 * ChatRequest.ConfirmTool}，本服务直接执行该工具（不再过大脑），避免重复 LLM 调用与决策漂移。
 */
@Service
@RequiredArgsConstructor
public class AgentService {

  /** 破坏性/覆盖性工具：执行前需用户二次确认。 */
  private static final Set<String> DESTRUCTIVE =
      Set.of("deleteUser", "assignRoles", "assignRoleMenus");

  private final ToolRegistry toolRegistry;
  private final AgentBrain brain;

  /**
   * 处理一次对话。
   *
   * @param userMessage 用户消息（二次确认回执时可留空）
   * @param relevantHistory 与当前消息最相关的历史对话，透传给大脑做多轮意图理解
   * @param confirm 二次确认回执；非空时直接执行其携带的工具调用，跳过大脑
   */
  public List<AgentEvent> handle(
      String userMessage, List<HistoryMessage> relevantHistory, ChatRequest.ConfirmTool confirm) {
    List<AgentEvent> events = new ArrayList<>();
    List<AgentTool> tools = toolRegistry.toolsForCurrentUser();

    // 二次确认回执：直接执行大脑建议的工具，不再过大脑。
    if (confirm != null && confirm.tool() != null && !confirm.tool().isBlank()) {
      AgentTool tool = resolveTool(tools, confirm.tool());
      Map<String, Object> args = confirm.args() == null ? Map.of() : confirm.args();
      events.add(AgentEvent.tool(tool.name(), args));
      return runTool(userMessage, tool, args, events);
    }

    BrainDecision decision = brain.decide(userMessage, tools, relevantHistory);
    if (decision.hasToolCall()) {
      AgentTool tool = resolveTool(tools, decision.toolName());
      events.add(AgentEvent.tool(tool.name(), decision.toolArgs()));
      // 破坏性工具：先请求二次确认，暂不执行。
      if (DESTRUCTIVE.contains(tool.name())) {
        events.add(
            AgentEvent.confirm(
                tool.name(),
                decision.toolArgs(),
                confirmMessage(tool.name(), decision.toolArgs())));
        events.add(AgentEvent.done());
        return events;
      }
      return runTool(userMessage, tool, decision.toolArgs(), events);
    }
    events.add(AgentEvent.token(decision.replyText()));
    events.add(AgentEvent.done());
    return events;
  }

  /** 执行工具 + 总结 + （成功时）跳转动作，产出事件序列（含 {@code done}）。 */
  private List<AgentEvent> runTool(
      String userMessage, AgentTool tool, Map<String, Object> args, List<AgentEvent> events) {
    ToolResult result;
    try {
      result = tool.execute().apply(args);
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
    events.add(AgentEvent.done());
    return events;
  }

  private AgentTool resolveTool(List<AgentTool> tools, String name) {
    return tools.stream()
        .filter(t -> t.name().equals(name))
        .findFirst()
        .orElseThrow(() -> ForbiddenException.i18n("error.permission.denied", name));
  }

  /** 破坏性工具的确认提示文案。 */
  private String confirmMessage(String tool, Map<String, Object> args) {
    return switch (tool) {
      case "deleteUser" -> "确认删除用户 #" + args.get("id") + "？此操作不可撤销。";
      case "assignRoles" ->
          "确认将角色 " + args.get("roleIds") + " 覆盖分配给用户 #" + args.get("userId") + "？（会替换其原有角色）";
      case "assignRoleMenus" ->
          "确认将菜单 " + args.get("menuIds") + " 覆盖分配给角色 #" + args.get("roleId") + "？（会替换其原有菜单）";
      default -> "确认执行操作 " + tool + "？";
    };
  }

  private static String messageOf(Exception ex) {
    return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
  }
}

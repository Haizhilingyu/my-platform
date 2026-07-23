package com.example.aiagent.agent.tool;

import com.example.common.ai.AgentTool;
import com.example.common.ai.AiToolProvider;
import com.example.common.ai.ToolResult;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * ai-agent 模块自有的 AI 工具（与域无关的通用工具）。
 *
 * <p>当前仅 {@code navigateTo}（页面跳转）。后续 ai-agent 自有的跨域工具也注册在此。
 */
@Component
public class AiAgentToolProvider implements AiToolProvider {

  @Override
  public List<AgentTool> getTools() {
    return List.of(
        new AgentTool(
            "navigateTo",
            "跳转到指定页面，向用户展示操作结果。当工具执行成功后需要引导用户查看时触发。"
                + "参数：path(前端路由,如/sys/user)、highlightId(可选,高亮资源ID)。",
            null,
            NavigateInput.class,
            new String[] {"跳转", "打开页面", "navigate"},
            false,
            args -> {
              Object pathVal = args.get("path");
              String path = pathVal == null ? null : pathVal.toString();
              Long highlightId = null;
              Object hl = args.get("highlightId");
              if (hl instanceof Number n) {
                highlightId = n.longValue();
              } else if (hl != null) {
                highlightId = Long.valueOf(hl.toString());
              }
              return new ToolResult(true, "已跳转 " + path, highlightId, path);
            }));
  }

  /** navigateTo 工具入参 schema。 */
  public record NavigateInput(String path, java.util.Optional<Integer> highlightId) {}
}

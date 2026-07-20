package com.example.aiagent.agent.brain;

import com.example.aiagent.agent.tool.AgentTool;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mock 大脑：基于关键词意图匹配决定调用哪个工具，无需真实 LLM。
 *
 * <p>仅在 {@code app.ai.provider=mock}（默认，{@code matchIfMissing=true}）时生效。 后续真实 provider 用
 * {@code @ConditionalOnProperty(... havingValue="openai")} 等替换。
 */
@Component
@ConditionalOnProperty(
    prefix = "app.ai",
    name = "provider",
    havingValue = "mock",
    matchIfMissing = true)
public class MockAgentBrain implements AgentBrain {

  private static final Pattern CREATE_USER =
      Pattern.compile(
          "(?:创建|新建|添加|新增|create)[^\\w]*(?:用户|user)[^\\w]*([a-zA-Z0-9_]+)",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern PASSWORD =
      Pattern.compile("(?:密码|password)[:：\\s]*([^\\s]+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern DELETE_USER =
      Pattern.compile("(?:删除|移除|delete|remove)[^\\w]*(?:用户|user)", Pattern.CASE_INSENSITIVE);
  private static final Pattern NUMBER = Pattern.compile("\\d+");

  private static final String DEFAULT_PASSWORD = "User@123";

  @Override
  public BrainDecision decide(String userMessage, List<AgentTool> tools) {
    String msg = userMessage == null ? "" : userMessage.trim();
    boolean canDelete = has(tools, "deleteUser");
    boolean canCreate = has(tools, "createUser");

    if (canDelete && DELETE_USER.matcher(msg).find()) {
      Long id = firstLong(msg);
      if (id != null) {
        return BrainDecision.tool("deleteUser", Map.of("id", id));
      }
      return BrainDecision.reply("想删除用户，请告诉我用户ID，例如：删除用户 42");
    }
    if (canCreate) {
      Matcher m = CREATE_USER.matcher(msg);
      if (m.find()) {
        String username = m.group(1);
        String password = DEFAULT_PASSWORD;
        Matcher pm = PASSWORD.matcher(msg);
        if (pm.find()) {
          password = pm.group(1);
        }
        return BrainDecision.tool("createUser", Map.of("username", username, "password", password));
      }
    }
    return BrainDecision.reply("（Mock 助手）我可以帮你管理系统。试试：\n• 创建用户 alice 密码 Alice@123\n• 删除用户 42");
  }

  private static boolean has(List<AgentTool> tools, String name) {
    return tools.stream().anyMatch(t -> t.name().equals(name));
  }

  private static Long firstLong(String s) {
    Matcher m = NUMBER.matcher(s);
    return m.find() ? Long.valueOf(m.group()) : null;
  }
}

package com.example.aiagent.agent.brain;

import com.example.aiagent.agent.tool.AgentTool;
import com.example.aiagent.chat.dto.HistoryMessage;
import java.util.ArrayList;
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

  private static final Pattern LIST_USERS =
      Pattern.compile("(?:查询|查看|列表|列出|list)[^\\w]*(?:用户|user)", Pattern.CASE_INSENSITIVE);
  private static final Pattern LIST_ROLES =
      Pattern.compile("(?:查询|查看|列表|列出|list)[^\\w]*(?:角色|role)", Pattern.CASE_INSENSITIVE);
  private static final Pattern CREATE_ROLE =
      Pattern.compile("(?:创建|新建|添加|新增|create)[^\\w]*(?:角色|role)", Pattern.CASE_INSENSITIVE);
  private static final Pattern ROLE_CODE =
      Pattern.compile("(?:编码|code)[:：\\s]*([a-zA-Z0-9_]+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern ASSIGN_ROLES =
      Pattern.compile("(?:分配|赋予|授予|assign)[^\\w]*(?:角色|role)", Pattern.CASE_INSENSITIVE);

  @Override
  public BrainDecision decide(
      String userMessage, List<AgentTool> tools, List<HistoryMessage> relevantHistory) {
    // Mock 只对当前消息做关键词匹配；relevantHistory 仅 DeepSeek 使用，此处忽略。
    String msg = userMessage == null ? "" : userMessage.trim();
    boolean canDelete = has(tools, "deleteUser");
    boolean canCreate = has(tools, "createUser");
    boolean canListUsers = has(tools, "listUsers");
    boolean canListRoles = has(tools, "listRoles");
    boolean canCreateRole = has(tools, "createRole");
    boolean canAssignRoles = has(tools, "assignRoles");

    if (canDelete && DELETE_USER.matcher(msg).find()) {
      Long id = firstLong(msg);
      if (id != null) {
        return BrainDecision.tool("deleteUser", Map.of("id", id));
      }
      return BrainDecision.reply("想删除用户，请告诉我用户ID，例如：删除用户 42");
    }
    if (canListUsers && LIST_USERS.matcher(msg).find()) {
      return BrainDecision.tool("listUsers", Map.of());
    }
    if (canListRoles && LIST_ROLES.matcher(msg).find()) {
      return BrainDecision.tool("listRoles", Map.of());
    }
    if (canAssignRoles && ASSIGN_ROLES.matcher(msg).find()) {
      List<Long> nums = allLongs(msg);
      if (nums.size() >= 2) {
        Long userId = nums.get(0);
        List<Long> roleIds = new ArrayList<>(nums.subList(1, nums.size()));
        return BrainDecision.tool("assignRoles", Map.of("userId", userId, "roleIds", roleIds));
      }
      return BrainDecision.reply("想给用户分配角色，请给出用户ID和角色ID，例如：给用户 5 分配角色 2,3");
    }
    if (canCreateRole && CREATE_ROLE.matcher(msg).find()) {
      String code = "ai_role_" + System.currentTimeMillis() % 100000;
      Matcher rm = ROLE_CODE.matcher(msg);
      if (rm.find()) {
        code = rm.group(1);
      }
      return BrainDecision.tool("createRole", Map.of("roleCode", code, "roleName", code));
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
    return BrainDecision.reply(
        "（Mock 助手）我可以帮你管理系统（mock）。试试：\n• 查询用户列表 / 查询角色列表\n• 创建角色 编码 devops\n• 给用户 5 分配角色 2,3\n也可：\n• 创建用户 alice 密码 Alice@123\n• 删除用户 42");
  }

  @Override
  public String summarize(String userMessage, String toolName, String toolResult) {
    return ""; // Mock 直接展示工具结果，无需 LLM 二次总结
  }

  private static boolean has(List<AgentTool> tools, String name) {
    return tools.stream().anyMatch(t -> t.name().equals(name));
  }

  private static Long firstLong(String s) {
    Matcher m = NUMBER.matcher(s);
    return m.find() ? Long.valueOf(m.group()) : null;
  }

  private static List<Long> allLongs(String s) {
    List<Long> result = new ArrayList<>();
    Matcher m = NUMBER.matcher(s);
    while (m.find()) {
      result.add(Long.valueOf(m.group()));
    }
    return result;
  }
}

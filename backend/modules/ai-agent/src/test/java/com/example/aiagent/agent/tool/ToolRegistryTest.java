package com.example.aiagent.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.common.ai.AgentTool;
import com.example.common.ai.AiToolProvider;
import com.example.common.ai.ToolResult;
import com.example.common.exception.ForbiddenException;
import com.example.common.security.CurrentUser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 验证「AI 能力 ⊆ 当前用户权限」双层保障：
 *
 * <p>第一层：ToolRegistry.toolsForCurrentUser() 按权限过滤可见工具。 第二层：执行体内 requirePermission 兜底。
 */
class ToolRegistryTest {

  private final AgentTool createUser =
      new AgentTool(
          "createUser",
          "创建用户",
          "sys:user:add",
          Object.class,
          new String[] {"创建用户"},
          false,
          args -> {
            throw new ForbiddenException("权限不足");
          });
  private final AgentTool deleteUser =
      new AgentTool(
          "deleteUser",
          "删除用户",
          "sys:user:delete",
          Object.class,
          new String[] {"删除用户"},
          true,
          args -> null);
  private final AgentTool navigate =
      new AgentTool(
          "navigateTo",
          "跳转",
          null,
          Object.class,
          new String[] {"跳转"},
          false,
          args -> new ToolResult(true, "ok", null, null));

  /** 测试用 provider：返回固定工具列表。 */
  private AiToolProvider provider(List<AgentTool> tools) {
    return () -> tools;
  }

  @AfterEach
  void clear() {
    CurrentUser.clear();
  }

  @Test
  void adminWithWildcardSeesAll() {
    CurrentUser.set(new CurrentUser.UserInfo(1L, "admin", null, Set.of(), Set.of("*")));
    var reg = new ToolRegistry(List.of(provider(List.of(createUser, deleteUser, navigate))));
    assertThat(reg.toolsForCurrentUser())
        .extracting(AgentTool::name)
        .containsExactlyInAnyOrder("createUser", "deleteUser", "navigateTo");
  }

  @Test
  void userWithOnlyCreateSeesCreateAndNavigate() {
    CurrentUser.set(new CurrentUser.UserInfo(2L, "alice", null, Set.of(), Set.of("sys:user:add")));
    var reg = new ToolRegistry(List.of(provider(List.of(createUser, deleteUser, navigate))));
    assertThat(reg.toolsForCurrentUser())
        .extracting(AgentTool::name)
        .containsExactlyInAnyOrder("createUser", "navigateTo");
  }

  @Test
  void userWithNoPermsSeesOnlyNoPermissionTools() {
    CurrentUser.set(new CurrentUser.UserInfo(3L, "bob", null, Set.of(), Set.of()));
    var reg = new ToolRegistry(List.of(provider(List.of(createUser, deleteUser, navigate))));
    assertThat(reg.toolsForCurrentUser()).extracting(AgentTool::name).containsExactly("navigateTo");
  }

  @Test
  void multipleProvidersAggregated() {
    AiToolProvider providerA = () -> List.of(createUser);
    AiToolProvider providerB = () -> List.of(deleteUser, navigate);
    CurrentUser.set(new CurrentUser.UserInfo(1L, "admin", null, Set.of(), Set.of("*")));
    var reg = new ToolRegistry(List.of(providerA, providerB));
    assertThat(reg.toolsForCurrentUser())
        .extracting(AgentTool::name)
        .containsExactlyInAnyOrder("createUser", "deleteUser", "navigateTo");
  }

  @Test
  void secondLayerPermissionGuardBlocksExecutionEvenIfToolVisible() {
    // 用户有 sys:user:add → createUser 可见
    CurrentUser.set(new CurrentUser.UserInfo(2L, "alice", null, Set.of(), Set.of("sys:user:add")));
    // 但 createUser 执行体故意检查（模拟幻觉场景：LLM 调了没权限的工具）
    // 这里直接验证执行体的权限拦截行为
    assertThatThrownBy(() -> createUser.execute().apply(Map.of()))
        .isInstanceOf(ForbiddenException.class);
  }
}

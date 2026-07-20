package com.example.aiagent.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.common.security.CurrentUser;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 验证「AI 能力 ⊆ 当前用户权限」的第一层保障：工具按权限筛选。 */
class ToolRegistryTest {

  private final AgentTool createUser = new AgentTool("createUser", "d", "sys:user:add", a -> null);
  private final AgentTool deleteUser =
      new AgentTool("deleteUser", "d", "sys:user:delete", a -> null);
  private final AgentTool navigate = new AgentTool("navigateTo", "d", null, a -> null);

  @AfterEach
  void clear() {
    CurrentUser.clear();
  }

  @Test
  void adminWithWildcardSeesAll() {
    CurrentUser.set(new CurrentUser.UserInfo(1L, "admin", null, Set.of(), Set.of("*")));
    var reg = new ToolRegistry(List.of(createUser, deleteUser, navigate));
    assertThat(reg.toolsForCurrentUser())
        .extracting(AgentTool::name)
        .containsExactlyInAnyOrder("createUser", "deleteUser", "navigateTo");
  }

  @Test
  void userWithOnlyCreateSeesCreateAndNavigate() {
    CurrentUser.set(new CurrentUser.UserInfo(2L, "alice", null, Set.of(), Set.of("sys:user:add")));
    var reg = new ToolRegistry(List.of(createUser, deleteUser, navigate));
    assertThat(reg.toolsForCurrentUser())
        .extracting(AgentTool::name)
        .containsExactlyInAnyOrder("createUser", "navigateTo");
  }

  @Test
  void userWithNoPermsSeesOnlyNoPermissionTools() {
    CurrentUser.set(new CurrentUser.UserInfo(3L, "bob", null, Set.of(), Set.of()));
    var reg = new ToolRegistry(List.of(createUser, deleteUser, navigate));
    assertThat(reg.toolsForCurrentUser()).extracting(AgentTool::name).containsExactly("navigateTo");
  }
}

package com.example.aiagent.agent.brain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.aiagent.agent.tool.AgentTool;
import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

/** Mock 大脑意图匹配测试。 */
class MockAgentBrainTest {

  private final MockAgentBrain brain = new MockAgentBrain();
  private final AgentTool createUser = new AgentTool("createUser", "d", "sys:user:add", a -> null);
  private final AgentTool deleteUser =
      new AgentTool("deleteUser", "d", "sys:user:delete", a -> null);
  private final AgentTool listUsers = new AgentTool("listUsers", "d", "sys:user:list", a -> null);
  private final AgentTool listRoles = new AgentTool("listRoles", "d", "sys:role:list", a -> null);
  private final AgentTool createRole = new AgentTool("createRole", "d", "sys:role:add", a -> null);
  private final AgentTool assignRoles =
      new AgentTool("assignRoles", "d", "sys:user:role", a -> null);

  @Test
  void createsUserFromChineseMessage() {
    var d = brain.decide("帮我创建用户 alice 密码 Alice@123", List.of(createUser, deleteUser), List.of());
    assertThat(d.hasToolCall()).isTrue();
    assertThat(d.toolName()).isEqualTo("createUser");
    assertThat(d.toolArgs())
        .containsEntry("username", "alice")
        .containsEntry("password", "Alice@123");
  }

  @Test
  void createsUserWithDefaultPasswordWhenOmitted() {
    var d = brain.decide("create user bob", List.of(createUser), List.of());
    assertThat(d.toolName()).isEqualTo("createUser");
    assertThat(d.toolArgs()).containsEntry("username", "bob").containsKey("password");
  }

  @Test
  void deleteUserById() {
    var d = brain.decide("删除用户 42", List.of(createUser, deleteUser), List.of());
    assertThat(d.toolName()).isEqualTo("deleteUser");
    assertThat(d.toolArgs()).containsEntry("id", 42L);
  }

  @Test
  void deleteUserWithoutIdAsksForId() {
    var d = brain.decide("删除用户", List.of(deleteUser), List.of());
    assertThat(d.hasToolCall()).isFalse();
    assertThat(d.replyText()).contains("用户ID");
  }

  @Test
  void cannotCreateIfToolNotVisible() {
    var d = brain.decide("创建用户 alice", List.of(deleteUser), List.of());
    assertThat(d.hasToolCall()).isFalse();
  }

  @Test
  void unknownIntentFallbackReply() {
    var d = brain.decide("今天天气如何", List.of(createUser), List.of());
    assertThat(d.hasToolCall()).isFalse();
    assertThat(d.replyText()).isNotBlank();
  }

  @Test
  void listsUsersByKeyword() {
    var d = brain.decide("查询用户列表", List.of(listUsers), List.of());
    assertThat(d.toolName()).isEqualTo("listUsers");
  }

  @Test
  void listsRolesByKeyword() {
    var d = brain.decide("列出角色", List.of(listRoles), List.of());
    assertThat(d.toolName()).isEqualTo("listRoles");
  }

  @Test
  void createsRoleFromCode() {
    var d = brain.decide("创建角色 编码 devops", List.of(createRole), List.of());
    assertThat(d.toolName()).isEqualTo("createRole");
    assertThat(d.toolArgs()).containsEntry("roleCode", "devops");
  }

  @Test
  void assignsRolesByNumbers() {
    var d = brain.decide("给用户 5 分配角色 2,3", List.of(assignRoles), List.of());
    assertThat(d.toolName()).isEqualTo("assignRoles");
    assertThat(d.toolArgs()).containsEntry("userId", 5L);
    assertThat(d.toolArgs().get("roleIds"))
        .asInstanceOf(InstanceOfAssertFactories.LIST)
        .containsExactly(2L, 3L);
  }

  @Test
  void assignRolesWithoutNumbersAsksForInput() {
    var d = brain.decide("给用户分配角色", List.of(assignRoles), List.of());
    assertThat(d.hasToolCall()).isFalse();
    assertThat(d.replyText()).contains("用户ID");
  }
}

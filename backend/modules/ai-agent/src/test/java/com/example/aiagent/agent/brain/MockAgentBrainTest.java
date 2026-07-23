package com.example.aiagent.agent.brain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.common.ai.AgentTool;
import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

/**
 * Mock 大脑意图匹配测试。
 *
 * <p>数据驱动 Mock 从 AgentTool.triggerKeywords() 匹配用户消息，命中后用反射从 inputSchemaType 泛化提取参数。
 */
class MockAgentBrainTest {

  private final MockAgentBrain brain = new MockAgentBrain();
  private final AgentTool createUser =
      new AgentTool(
          "createUser",
          "创建用户",
          "sys:user:add",
          SysTestInputs.CreateUserInput.class,
          new String[] {"创建用户", "新建用户", "create user"},
          false,
          a -> null);
  private final AgentTool deleteUser =
      new AgentTool(
          "deleteUser",
          "删除用户",
          "sys:user:delete",
          SysTestInputs.IdInput.class,
          new String[] {"删除用户", "delete user"},
          true,
          a -> null);
  private final AgentTool listUsers =
      new AgentTool(
          "listUsers",
          "查询用户",
          "sys:user:list",
          SysTestInputs.SearchInput.class,
          new String[] {"查看用户", "查询用户", "list users", "用户列表"},
          false,
          a -> null);
  private final AgentTool listRoles =
      new AgentTool(
          "listRoles",
          "查询角色",
          "sys:role:list",
          SysTestInputs.EmptyInput.class,
          new String[] {"查看角色", "查询角色", "列出角色", "list roles"},
          false,
          a -> null);
  private final AgentTool createRole =
      new AgentTool(
          "createRole",
          "创建角色",
          "sys:role:add",
          SysTestInputs.CreateRoleInput.class,
          new String[] {"创建角色", "新建角色", "create role"},
          false,
          a -> null);
  private final AgentTool assignRoles =
      new AgentTool(
          "assignRoles",
          "分配角色",
          "sys:user:role",
          SysTestInputs.AssignRolesInput.class,
          new String[] {"分配角色", "assign role"},
          true,
          a -> null);

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
    assertThat(d.toolArgs()).containsEntry("username", "bob");
    // password 未在消息中出现，Mock 不提取——工具端用默认密码兜底
  }

  @Test
  void deleteUserById() {
    var d = brain.decide("删除用户 42", List.of(createUser, deleteUser), List.of());
    assertThat(d.toolName()).isEqualTo("deleteUser");
    assertThat(d.toolArgs()).containsEntry("id", 42L);
  }

  @Test
  void deleteUserWithoutIdStillCallsTool() {
    // 数据驱动 Mock 命中关键词即调用工具，不验证参数完整性（工具执行体负责校验）
    var d = brain.decide("删除用户", List.of(deleteUser), List.of());
    assertThat(d.hasToolCall()).isTrue();
    assertThat(d.toolName()).isEqualTo("deleteUser");
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
    // roleIds 提取消息中全部数字（包括 userId），工具端按位置取后续数字
    assertThat(d.toolArgs().get("roleIds"))
        .asInstanceOf(InstanceOfAssertFactories.LIST)
        .contains(2L, 3L);
  }

  @Test
  void assignRolesWithoutNumbersStillCallsTool() {
    // 数据驱动 Mock 命中关键词即调用工具，不验证参数完整性
    var d = brain.decide("给用户分配角色", List.of(assignRoles), List.of());
    assertThat(d.hasToolCall()).isTrue();
    assertThat(d.toolName()).isEqualTo("assignRoles");
  }
}

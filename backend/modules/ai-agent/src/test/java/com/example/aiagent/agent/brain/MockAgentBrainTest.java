package com.example.aiagent.agent.brain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.aiagent.agent.tool.AgentTool;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Mock 大脑意图匹配测试。 */
class MockAgentBrainTest {

  private final MockAgentBrain brain = new MockAgentBrain();
  private final AgentTool createUser = new AgentTool("createUser", "d", "sys:user:add", a -> null);
  private final AgentTool deleteUser =
      new AgentTool("deleteUser", "d", "sys:user:delete", a -> null);

  @Test
  void createsUserFromChineseMessage() {
    var d = brain.decide("帮我创建用户 alice 密码 Alice@123", List.of(createUser, deleteUser));
    assertThat(d.hasToolCall()).isTrue();
    assertThat(d.toolName()).isEqualTo("createUser");
    assertThat(d.toolArgs())
        .containsEntry("username", "alice")
        .containsEntry("password", "Alice@123");
  }

  @Test
  void createsUserWithDefaultPasswordWhenOmitted() {
    var d = brain.decide("create user bob", List.of(createUser));
    assertThat(d.toolName()).isEqualTo("createUser");
    assertThat(d.toolArgs()).containsEntry("username", "bob").containsKey("password");
  }

  @Test
  void deleteUserById() {
    var d = brain.decide("删除用户 42", List.of(createUser, deleteUser));
    assertThat(d.toolName()).isEqualTo("deleteUser");
    assertThat(d.toolArgs()).containsEntry("id", 42L);
  }

  @Test
  void deleteUserWithoutIdAsksForId() {
    var d = brain.decide("删除用户", List.of(deleteUser));
    assertThat(d.hasToolCall()).isFalse();
    assertThat(d.replyText()).contains("用户ID");
  }

  @Test
  void cannotCreateIfToolNotVisible() {
    var d = brain.decide("创建用户 alice", List.of(deleteUser));
    assertThat(d.hasToolCall()).isFalse();
  }

  @Test
  void unknownIntentFallbackReply() {
    var d = brain.decide("今天天气如何", List.of(createUser));
    assertThat(d.hasToolCall()).isFalse();
    assertThat(d.replyText()).isNotBlank();
  }
}

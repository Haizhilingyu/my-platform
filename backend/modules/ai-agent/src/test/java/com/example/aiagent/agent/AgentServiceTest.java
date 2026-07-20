package com.example.aiagent.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.aiagent.agent.brain.AgentBrain;
import com.example.aiagent.agent.brain.BrainDecision;
import com.example.aiagent.agent.event.AgentEvent;
import com.example.aiagent.agent.tool.AgentTool;
import com.example.aiagent.agent.tool.ToolRegistry;
import com.example.aiagent.agent.tool.ToolResult;
import com.example.aiagent.chat.dto.ChatRequest;
import com.example.common.security.CurrentUser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Agent 编排测试：破坏性工具二次确认流程。 */
class AgentServiceTest {

  @AfterEach
  void clear() {
    CurrentUser.clear();
  }

  private static void asAdmin() {
    CurrentUser.set(new CurrentUser.UserInfo(1L, "admin", null, Set.of(), Set.of("*")));
  }

  /** 总是返回固定决策的大脑。 */
  private static AgentBrain deciding(BrainDecision decision) {
    return new AgentBrain() {
      @Override
      public BrainDecision decide(String userMessage, List<AgentTool> tools) {
        return decision;
      }

      @Override
      public String summarize(String userMessage, String toolName, String toolResult) {
        return "";
      }
    };
  }

  @Test
  void destructiveToolEmitsConfirmAndDoesNotExecute() {
    asAdmin();
    AtomicBoolean executed = new AtomicBoolean(false);
    AgentTool deleteUser =
        new AgentTool(
            "deleteUser",
            "d",
            "sys:user:delete",
            args -> {
              executed.set(true);
              return new ToolResult(true, "已删除", 5L, "/sys/user");
            });
    var service =
        new AgentService(
            new ToolRegistry(List.of(deleteUser)),
            deciding(BrainDecision.tool("deleteUser", Map.of("id", 5))));

    List<AgentEvent> events = service.handle("删除用户 5", null);

    assertThat(events).extracting(AgentEvent::type).contains("tool", "confirm", "done");
    assertThat(events).extracting(AgentEvent::type).doesNotContain("result", "action");
    assertThat(executed).isFalse(); // 暂未执行，等待用户确认
    AgentEvent confirm =
        events.stream().filter(e -> "confirm".equals(e.type())).findFirst().orElseThrow();
    AgentEvent.ConfirmInfo info = (AgentEvent.ConfirmInfo) confirm.data();
    assertThat(info.tool()).isEqualTo("deleteUser");
    assertThat(info.args()).isEqualTo(Map.of("id", 5));
    assertThat(info.message()).contains("删除");
  }

  @Test
  void nonDestructiveToolExecutesImmediately() {
    asAdmin();
    AgentTool createUser =
        new AgentTool(
            "createUser",
            "d",
            "sys:user:add",
            args -> new ToolResult(true, "已创建", 1L, "/sys/user"));
    var service =
        new AgentService(
            new ToolRegistry(List.of(createUser)),
            deciding(BrainDecision.tool("createUser", Map.of("username", "alice"))));

    List<AgentEvent> events = service.handle("创建用户 alice", null);

    assertThat(events).extracting(AgentEvent::type).contains("tool", "result", "action", "done");
    assertThat(events).extracting(AgentEvent::type).doesNotContain("confirm");
  }

  @Test
  void confirmRoundtripExecutesToolWithoutBrain() {
    asAdmin();
    AtomicBoolean executed = new AtomicBoolean(false);
    AgentTool deleteUser =
        new AgentTool(
            "deleteUser",
            "d",
            "sys:user:delete",
            args -> {
              executed.set(true);
              return new ToolResult(true, "已删除", 5L, "/sys/user");
            });
    // 大脑会 reply（不应被调用，因为 confirm 直接执行）
    var service =
        new AgentService(
            new ToolRegistry(List.of(deleteUser)), deciding(BrainDecision.reply("unused")));

    List<AgentEvent> events =
        service.handle(null, new ChatRequest.ConfirmTool("deleteUser", Map.of("id", 5)));

    assertThat(events).extracting(AgentEvent::type).contains("tool", "result", "done");
    assertThat(executed).isTrue();
  }
}

package com.example.aiagent.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.aiagent.agent.brain.AgentBrain;
import com.example.aiagent.agent.brain.BrainDecision;
import com.example.aiagent.agent.event.AgentEvent;
import com.example.aiagent.agent.tool.ToolRegistry;
import com.example.aiagent.chat.dto.ChatRequest;
import com.example.aiagent.chat.dto.HistoryMessage;
import com.example.common.ai.AgentTool;
import com.example.common.ai.AiToolProvider;
import com.example.common.ai.ToolResult;
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
      public BrainDecision decide(
          String userMessage, List<AgentTool> tools, List<HistoryMessage> relevantHistory) {
        return decision;
      }

      @Override
      public String summarize(String userMessage, String toolName, String toolResult) {
        return "";
      }
    };
  }

  /** 单工具 provider 工厂（便于测试构造）。 */
  private static AiToolProvider provider(AgentTool... tools) {
    return () -> List.of(tools);
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
            Object.class,
            new String[] {"删除用户"},
            true,
            args -> {
              executed.set(true);
              return new ToolResult(true, "已删除", 5L, "/sys/user");
            });
    var service =
        new AgentService(
            new ToolRegistry(List.of(provider(deleteUser))),
            deciding(BrainDecision.tool("deleteUser", Map.of("id", 5))));

    List<AgentEvent> events = service.handle("删除用户 5", List.of(), null);

    assertThat(events).extracting(AgentEvent::type).contains("tool", "confirm", "done");
    assertThat(events).extracting(AgentEvent::type).doesNotContain("result", "action");
    assertThat(executed).isFalse(); // 暂未执行，等待用户确认
    AgentEvent confirm =
        events.stream().filter(e -> "confirm".equals(e.type())).findFirst().orElseThrow();
    AgentEvent.ConfirmInfo info = (AgentEvent.ConfirmInfo) confirm.data();
    assertThat(info.tool()).isEqualTo("deleteUser");
    assertThat(info.args()).isEqualTo(Map.of("id", 5));
    assertThat(info.message()).contains("确认");
  }

  @Test
  void nonDestructiveToolExecutesImmediately() {
    asAdmin();
    AgentTool createUser =
        new AgentTool(
            "createUser",
            "d",
            "sys:user:add",
            Object.class,
            new String[] {"创建用户"},
            false,
            args -> new ToolResult(true, "已创建", 1L, "/sys/user"));
    var service =
        new AgentService(
            new ToolRegistry(List.of(provider(createUser))),
            deciding(BrainDecision.tool("createUser", Map.of("username", "alice"))));

    List<AgentEvent> events = service.handle("创建用户 alice", List.of(), null);

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
            Object.class,
            new String[] {"删除用户"},
            true,
            args -> {
              executed.set(true);
              return new ToolResult(true, "已删除", 5L, "/sys/user");
            });
    // 大脑会 reply（不应被调用，因为 confirm 直接执行）
    var service =
        new AgentService(
            new ToolRegistry(List.of(provider(deleteUser))),
            deciding(BrainDecision.reply("unused")));

    List<AgentEvent> events =
        service.handle(null, List.of(), new ChatRequest.ConfirmTool("deleteUser", Map.of("id", 5)));

    assertThat(events).extracting(AgentEvent::type).contains("tool", "result", "done");
    assertThat(executed).isTrue();
  }
}

package com.example.aiagent.agent.brain;

import com.example.aiagent.agent.tool.AgentTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * DeepSeek 真实 LLM 大脑（Spring AI）。{@code app.ai.provider=deepseek} 时生效。
 *
 * <p>用 Spring AI 的 {@link ChatModel}（OpenAiChatModel，base-url 经 spring.ai.openai.base-url 指向
 * DeepSeek）做模型调用。 工具 schema 经 {@link FunctionToolCallback} 提供；{@code
 * internalToolExecutionEnabled=false} 让模型只「建议」工具调用， 由 AgentService 执行（保留权限双层校验 + @Auditable + 事件流
 * tool/result/action/token）。
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "deepseek")
public class DeepSeekAgentBrain implements AgentBrain {

  private final ChatModel chatModel;
  private final ObjectMapper objectMapper;

  public DeepSeekAgentBrain(ChatModel chatModel, ObjectMapper objectMapper) {
    this.chatModel = chatModel;
    this.objectMapper = objectMapper;
  }

  @Override
  public BrainDecision decide(String userMessage, List<AgentTool> tools) {
    OpenAiChatOptions opts =
        OpenAiChatOptions.builder()
            .toolCallbacks(buildCallbacks(tools))
            .internalToolExecutionEnabled(false)
            .build();
    Prompt prompt =
        new Prompt(
            List.of(systemMessage(), new UserMessage(userMessage == null ? "" : userMessage)),
            opts);
    ChatResponse resp = chatModel.call(prompt);
    AssistantMessage am = resp.getResult().getOutput();
    if (am.hasToolCalls()) {
      List<AssistantMessage.ToolCall> calls = am.getToolCalls();
      if (calls != null && !calls.isEmpty()) {
        AssistantMessage.ToolCall tc = calls.get(0);
        return BrainDecision.tool(tc.name(), parseArgs(tc.arguments()));
      }
    }
    return BrainDecision.reply(am.getText() == null ? "" : am.getText());
  }

  @Override
  public String summarize(String userMessage, String toolName, String toolResult) {
    List<Message> msgs =
        List.of(
            systemMessage(),
            new UserMessage(userMessage == null ? "" : userMessage),
            new AssistantMessage("已调用工具 " + toolName + "。"),
            new UserMessage("工具执行结果：" + toolResult + "\n请用一句简短的中文向用户总结，不要调用任何工具。"));
    ChatResponse resp = chatModel.call(new Prompt(msgs));
    String c = resp.getResult().getOutput().getText();
    return c == null ? "" : c;
  }

  private SystemMessage systemMessage() {
    return new SystemMessage(
        "你是 my-platform 的系统管理 AI 助手。你只能调用提供的工具来执行操作（工具已按当前用户权限过滤）。"
            + "从用户的中文/英文指令中提取工具参数。用简短的中文回复。");
  }

  /** 按工具名构建 FunctionToolCallback（仅用于向模型提供参数 schema；执行由 AgentService 完成）。 */
  private List<ToolCallback> buildCallbacks(List<AgentTool> tools) {
    List<ToolCallback> list = new ArrayList<>();
    for (AgentTool t : tools) {
      switch (t.name()) {
        case "createUser" ->
            list.add(
                FunctionToolCallback.builder("createUser", (CreateUserInput in) -> "")
                    .description(t.description())
                    .inputType(CreateUserInput.class)
                    .build());
        case "deleteUser" ->
            list.add(
                FunctionToolCallback.builder("deleteUser", (DeleteUserInput in) -> "")
                    .description(t.description())
                    .inputType(DeleteUserInput.class)
                    .build());
        case "navigateTo" ->
            list.add(
                FunctionToolCallback.builder("navigateTo", (NavigateInput in) -> "")
                    .description(t.description())
                    .inputType(NavigateInput.class)
                    .build());
        default -> {
          // 未知工具忽略
        }
      }
    }
    return list;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> parseArgs(String arguments) {
    if (arguments == null || arguments.isBlank()) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(arguments, Map.class);
    } catch (Exception e) {
      return Map.of();
    }
  }

  // 工具入参 schema（Spring AI 据此生成 function 参数 JSON schema）。Optional 字段不会被标为 required。
  record CreateUserInput(
      String username,
      String password,
      java.util.Optional<String> realName,
      java.util.Optional<String> email,
      java.util.Optional<String> phone,
      java.util.Optional<Integer> unitId) {}

  record DeleteUserInput(Integer id) {}

  record NavigateInput(String path, java.util.Optional<Integer> highlightId) {}
}

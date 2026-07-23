package com.example.aiagent.agent.brain;

import com.example.aiagent.chat.dto.HistoryMessage;
import com.example.aiagent.config.DeepSeekChatModelFactory;
import com.example.common.ai.AgentTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * DeepSeek 真实 LLM 大脑（Spring AI 2.0）。{@code app.ai.provider=deepseek} 时生效。
 *
 * <p>用 Spring AI 的 OpenAiChatModel（由 {@link DeepSeekChatModelFactory} 按 sys_config 动态构建， base-url
 * 指向 DeepSeek）做模型调用。 工具 schema 经 {@link FunctionToolCallback} 提供；Spring AI 2.0 已移除 {@code
 * internalToolExecutionEnabled}——直接调用 {@code ChatModel.call(prompt)} 不再自动执行工具， 由 AgentService
 * 执行（保留权限双层校验 + @Auditable + 事件流 tool/result/action/token）。
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "deepseek")
public class DeepSeekAgentBrain implements AgentBrain {

  private final DeepSeekChatModelFactory chatModelFactory;
  private final ObjectMapper objectMapper;

  public DeepSeekAgentBrain(DeepSeekChatModelFactory chatModelFactory, ObjectMapper objectMapper) {
    this.chatModelFactory = chatModelFactory;
    this.objectMapper = objectMapper;
  }

  @Override
  public BrainDecision decide(
      String userMessage, List<AgentTool> tools, List<HistoryMessage> relevantHistory) {
    OpenAiChatOptions opts =
        OpenAiChatOptions.builder().toolCallbacks(buildCallbacks(tools)).build();
    List<Message> messages = new ArrayList<>();
    messages.add(systemMessage());
    // 注入相关历史对话（时间正序）：让模型做多轮意图理解。
    if (relevantHistory != null) {
      for (HistoryMessage h : relevantHistory) {
        if (h == null || h.text() == null || h.text().isBlank()) {
          continue;
        }
        String text = h.text().length() > 200 ? h.text().substring(0, 200) : h.text();
        messages.add("user".equals(h.role()) ? new UserMessage(text) : new AssistantMessage(text));
      }
    }
    messages.add(new UserMessage(userMessage == null ? "" : userMessage));
    Prompt prompt = new Prompt(messages, opts);
    ChatResponse resp = chatModelFactory.model().call(prompt);
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
    ChatResponse resp = chatModelFactory.model().call(new Prompt(msgs));
    String c = resp.getResult().getOutput().getText();
    return c == null ? "" : c;
  }

  private SystemMessage systemMessage() {
    return new SystemMessage(
        "你是 my-platform 的系统管理 AI 助手。你只能调用提供的工具来执行操作（工具已按当前用户权限过滤）。"
            + "从用户的中文/英文指令中提取工具参数。用简短的中文回复。");
  }

  /** 按工具定义数据驱动构建 FunctionToolCallback（仅用于向模型提供参数 schema；执行由 AgentService 完成）。 */
  private List<ToolCallback> buildCallbacks(List<AgentTool> tools) {
    List<ToolCallback> list = new ArrayList<>();
    for (AgentTool t : tools) {
      list.add(
          FunctionToolCallback.builder(t.name(), (Object in) -> "")
              .description(t.description())
              .inputType(t.inputSchemaType())
              .build());
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
}

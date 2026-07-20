package com.example.aiagent.agent.brain;

import com.example.aiagent.agent.tool.AgentTool;
import com.example.aiagent.config.AgentProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * DeepSeek 真实 LLM 大脑（OpenAI 兼容，支持 function calling）。{@code app.ai.provider=deepseek} 时生效。
 *
 * <p>流程：把权限筛后的工具转成 OpenAI function schema → 调 /v1/chat/completions → 若返回 tool_calls 则交给
 * AgentService 执行 → 再调一次（不带 tools）让模型用自然语言总结结果。用 Spring RestClient，不依赖 Spring AI。
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "deepseek")
public class DeepSeekAgentBrain implements AgentBrain {

  private final AgentProperties properties;
  private final ObjectMapper objectMapper;
  private final RestClient restClient;

  public DeepSeekAgentBrain(AgentProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.restClient = RestClient.create();
  }

  @Override
  public BrainDecision decide(String userMessage, List<AgentTool> tools) {
    List<Map<String, Object>> messages =
        List.of(
            systemMessage(),
            Map.of("role", "user", "content", userMessage == null ? "" : userMessage));
    ChatResponse resp = post(messages, buildTools(tools));
    Message msg = resp.choices().get(0).message();
    if (msg.tool_calls() != null && !msg.tool_calls().isEmpty()) {
      ToolCallDto tc = msg.tool_calls().get(0);
      return BrainDecision.tool(tc.function().name(), parseArgs(tc.function().arguments()));
    }
    return BrainDecision.reply(msg.content() == null ? "" : msg.content());
  }

  @Override
  public String summarize(String userMessage, String toolName, String toolResult) {
    List<Map<String, Object>> messages =
        List.of(
            systemMessage(),
            Map.of("role", "user", "content", userMessage == null ? "" : userMessage),
            Map.of("role", "assistant", "content", "已调用工具 " + toolName + "。"),
            Map.of(
                "role", "user", "content", "工具执行结果：" + toolResult + "\n请用一句简短的中文向用户总结，不要调用任何工具。"));
    ChatResponse resp = post(messages, null);
    String c = resp.choices().get(0).message().content();
    return c == null ? "" : c;
  }

  private ChatResponse post(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
    AgentProperties.Deepseek cfg = properties.getDeepseek();
    if (cfg.getApiKey() == null || cfg.getApiKey().isBlank()) {
      throw new IllegalStateException("app.ai.deepseek.api-key 未配置");
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", cfg.getModel());
    body.put("messages", messages);
    if (tools != null && !tools.isEmpty()) {
      body.put("tools", tools);
      body.put("tool_choice", "auto");
    }
    return restClient
        .post()
        .uri(cfg.getBaseUrl() + "/v1/chat/completions")
        .header("Authorization", "Bearer " + cfg.getApiKey())
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(ChatResponse.class);
  }

  private Map<String, Object> systemMessage() {
    return Map.of(
        "role",
        "system",
        "content",
        "你是 my-platform 的系统管理 AI 助手。你只能调用提供的工具来执行操作（工具已按当前用户权限过滤）。"
            + "从用户的中文/英文指令中提取工具参数。用简短的中文回复。");
  }

  private List<Map<String, Object>> buildTools(List<AgentTool> tools) {
    return tools.stream()
        .map(
            t -> {
              Map<String, Object> fn = new LinkedHashMap<>();
              fn.put("name", t.name());
              fn.put("description", t.description());
              fn.put("parameters", schemaFor(t.name()));
              return Map.of("type", "function", "function", fn);
            })
        .toList();
  }

  /** 按工具名给出 OpenAI function 参数 schema。 */
  private Map<String, Object> schemaFor(String name) {
    return switch (name) {
      case "createUser" ->
          Map.of(
              "type", "object",
              "properties",
                  Map.of(
                      "username", Map.of("type", "string", "description", "登录用户名，字母数字下划线3-32"),
                      "password", Map.of("type", "string", "description", "初始密码，6-32"),
                      "realName", Map.of("type", "string", "description", "真实姓名（可选）"),
                      "email", Map.of("type", "string", "description", "邮箱（可选）"),
                      "phone", Map.of("type", "string", "description", "手机号（可选）"),
                      "unitId", Map.of("type", "integer", "description", "单位ID（可选）")),
              "required", List.of("username", "password"));
      case "deleteUser" ->
          Map.of(
              "type", "object",
              "properties", Map.of("id", Map.of("type", "integer", "description", "要删除的用户ID")),
              "required", List.of("id"));
      case "navigateTo" ->
          Map.of(
              "type", "object",
              "properties",
                  Map.of(
                      "path", Map.of("type", "string", "description", "前端路由，如 /sys/user"),
                      "highlightId", Map.of("type", "integer", "description", "高亮资源ID（可选）")),
              "required", List.of("path"));
      default -> Map.of("type", "object", "properties", Map.of());
    };
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

  // ---- DeepSeek（OpenAI 兼容）响应 DTO ----
  record ChatResponse(List<Choice> choices) {}

  record Choice(Message message) {}

  record Message(String role, String content, List<ToolCallDto> tool_calls) {}

  record ToolCallDto(String id, String type, FunctionDto function) {}

  record FunctionDto(String name, String arguments) {}
}

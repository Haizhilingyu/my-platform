package com.example.aiagent.agent.event;

/**
 * SSE 事件。{@code type} 为事件名（SSE {@code event:} 行），{@code data} 序列化为 JSON。
 *
 * <p>类型：token(文本片段) / tool(工具调用) / result(工具结果摘要) / action(前端跳转) / done / error。
 */
public record AgentEvent(String type, Object data) {

  public static AgentEvent token(String text) {
    return new AgentEvent("token", text);
  }

  public static AgentEvent tool(String name, Object args) {
    return new AgentEvent("tool", new ToolInfo(name, args));
  }

  public static AgentEvent result(String summary) {
    return new AgentEvent("result", summary);
  }

  public static AgentEvent action(String path, Long highlightId) {
    return new AgentEvent("action", new NavAction(path, highlightId));
  }

  /** 破坏性工具二次确认：请求用户确认后再执行。 */
  public static AgentEvent confirm(String tool, Object args, String message) {
    return new AgentEvent("confirm", new ConfirmInfo(tool, args, message));
  }

  public static AgentEvent done() {
    return new AgentEvent("done", null);
  }

  public static AgentEvent error(String message) {
    return new AgentEvent("error", message);
  }

  /** 工具调用信息。 */
  public record ToolInfo(String name, Object args) {}

  /** 前端跳转动作。 */
  public record NavAction(String path, Long highlightId) {}

  /** 二次确认信息：破坏性工具执行前，请用户确认。 */
  public record ConfirmInfo(String tool, Object args, String message) {}
}

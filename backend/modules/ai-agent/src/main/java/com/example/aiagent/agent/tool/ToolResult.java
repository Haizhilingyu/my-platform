package com.example.aiagent.agent.tool;

/**
 * 工具执行结果。
 *
 * @param success 是否成功
 * @param message 给用户的摘要（会作为 {@code result} 事件回传）
 * @param resourceId 受影响资源 ID（用于前端高亮/审计）
 * @param navigatePath 操作完成后建议跳转的前端路由；{@code null} 表示不跳转
 */
public record ToolResult(boolean success, String message, Long resourceId, String navigatePath) {}

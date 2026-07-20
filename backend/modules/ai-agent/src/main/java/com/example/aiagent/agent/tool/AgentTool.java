package com.example.aiagent.agent.tool;

import java.util.Map;
import java.util.function.Function;

/**
 * Agent 工具定义。
 *
 * @param name 工具名（LLM/Mock 用以选择）
 * @param description 自然语言描述（供真实 LLM 理解用途）
 * @param requiredPermission 所需权限标识（如 {@code sys:user:add}）；{@code null} 表示无需权限（如跳转工具）
 * @param execute 执行器：入参为参数 Map，返回 {@link ToolResult}。执行器内部须做权限兜底校验
 */
public record AgentTool(
    String name,
    String description,
    String requiredPermission,
    Function<Map<String, Object>, ToolResult> execute) {}

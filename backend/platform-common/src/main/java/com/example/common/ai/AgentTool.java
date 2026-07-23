package com.example.common.ai;

import java.util.Map;
import java.util.function.Function;

/**
 * AI 工具定义。由各业务模块通过 {@link AiToolProvider} 提供，经 ToolRegistry 按权限聚合后注册给 Agent 大脑。
 *
 * @param name 工具名（LLM/Mock 用以选择，需全局唯一）
 * @param description 自然语言描述（供真实 LLM 理解用途与触发场景）
 * @param requiredPermission 所需权限标识（如 {@code sys:user:add}）；{@code null} 表示无需权限（如跳转工具）
 * @param inputSchemaType 工具入参 schema 的 record 类型（Spring AI 据此生成 function 参数 JSON schema）
 * @param triggerKeywords Mock 大脑关键词匹配（中英混合，如 "查看用户"、"list users"）；空数组 = Mock 不自动触发
 * @param destructive 是否为破坏性操作（true = 需用户二次确认）
 * @param execute 执行器：入参为参数 Map，返回 {@link ToolResult}。执行器内部须做权限兜底校验
 */
public record AgentTool(
    String name,
    String description,
    String requiredPermission,
    Class<?> inputSchemaType,
    String[] triggerKeywords,
    boolean destructive,
    Function<Map<String, Object>, ToolResult> execute) {}

package com.example.common.ai;

/** 工具执行结果。 */
public record ToolResult(boolean success, String message, Long resourceId, String navigatePath) {}

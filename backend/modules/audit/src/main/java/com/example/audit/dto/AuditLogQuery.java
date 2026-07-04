package com.example.audit.dto;

import java.time.LocalDateTime;

/**
 * 审计日志查询过滤条件。所有字段可空，空表示不过滤该维度。
 *
 * @param actor 操作人（模糊匹配）
 * @param action 操作类型（精确）
 * @param result 执行结果 success/fail（精确）
 * @param targetType 操作对象类型（精确）
 * @param targetId 操作对象 ID（精确）
 * @param startTime 起始时间（含）
 * @param endTime 结束时间（含）
 */
public record AuditLogQuery(
    String actor,
    String action,
    String result,
    String targetType,
    String targetId,
    LocalDateTime startTime,
    LocalDateTime endTime) {}

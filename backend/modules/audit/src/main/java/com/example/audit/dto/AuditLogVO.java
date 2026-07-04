package com.example.audit.dto;

import com.example.audit.domain.AuditLog;
import java.time.LocalDateTime;

/**
 * 审计日志列表展示对象。
 *
 * @param id 主键
 * @param actor 操作人
 * @param actorType 操作人类型
 * @param action 操作类型
 * @param targetType 操作对象类型
 * @param targetId 操作对象 ID
 * @param ip IP 地址
 * @param userAgent User-Agent
 * @param params 已脱敏的方法参数 JSON
 * @param result 执行结果 success/fail
 * @param errorMsg 失败原因
 * @param createdAt 发生时间
 */
public record AuditLogVO(
    Long id,
    String actor,
    String actorType,
    String action,
    String targetType,
    String targetId,
    String ip,
    String userAgent,
    String params,
    String result,
    String errorMsg,
    LocalDateTime createdAt) {

  public static AuditLogVO of(AuditLog log) {
    return new AuditLogVO(
        log.getId(),
        log.getActor(),
        log.getActorType(),
        log.getAction(),
        log.getTargetType(),
        log.getTargetId(),
        log.getIp(),
        log.getUserAgent(),
        log.getParams(),
        log.getResult(),
        log.getErrorMsg(),
        log.getCreatedAt());
  }
}

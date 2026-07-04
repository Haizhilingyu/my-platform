package com.example.common.audit;

import java.time.LocalDateTime;

/**
 * 审计事件。由 {@link AuditAspect} 在请求线程中同步组装（包含所有需要持久化的上下文）， 再交给 {@link AuditRecorder} 在异步线程落库，从而保证 AOP
 * 开销 &lt; 5ms。
 *
 * <p>这是一个不可变的数据载体（record），不持有任何请求作用域对象，可安全跨线程传递。
 *
 * @param actor 操作人标识（用户名），无登录态时为 "anonymous" / "system"
 * @param actorType 操作人类型，如 USER / SYSTEM / ANONYMOUS
 * @param action 操作类型（来自 {@link Auditable#action()}）
 * @param targetType 操作对象类型（来自 {@link Auditable#targetType()}，可空）
 * @param targetId 操作对象 ID（可空）
 * @param ip 请求方 IP（已解析 X-Forwarded-For，无请求上下文时为 null）
 * @param userAgent 请求方 User-Agent（无请求上下文时为 null）
 * @param params 已脱敏的方法参数 JSON（password/secret/token 等字段已替换为 "***"）
 * @param result 执行结果：success / fail
 * @param errorMsg 失败时的异常消息（成功时为 null）
 * @param occurredAt 事件发生时间（切面执行时刻）
 */
public record AuditEvent(
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
    LocalDateTime occurredAt) {}

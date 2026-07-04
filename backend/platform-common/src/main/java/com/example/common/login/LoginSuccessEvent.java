package com.example.common.login;

import java.time.LocalDateTime;

/**
 * 登录成功事件。
 *
 * <p>由 {@link LoginMethodProvider} 实现在认证成功后发布（通过
 * {@code ApplicationEventPublisher.publishEvent}），允许其他模块（如 T11 会话管理）
 * 对登录事件做出反应，而无需修改登录链路本身。
 *
 * <p>采用普通记录（POJO event，Spring 4.2+ 支持任意对象作为事件），
 * 监听方使用 {@code @EventListener} 注解接收。
 *
 * @param userId    用户 ID
 * @param username  用户名
 * @param jti       JWT ID（用于黑名单 / 会话追踪）
 * @param ip        登录请求 IP（已解析 X-Forwarded-For，无请求上下文时为 null）
 * @param userAgent 登录请求 User-Agent（无请求上下文时为 null）
 * @param loginTime 登录时间
 */
public record LoginSuccessEvent(
        Long userId,
        String username,
        String jti,
        String ip,
        String userAgent,
        LocalDateTime loginTime) {}

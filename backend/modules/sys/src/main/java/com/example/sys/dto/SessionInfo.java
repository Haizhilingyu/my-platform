package com.example.sys.dto;

import java.time.LocalDateTime;

/**
 * 在线会话信息。存储在 Redis（key = {@code session:active:{jti}}）并作为 API 响应返回。
 *
 * @param jti        会话对应的 JWT 唯一标识
 * @param userId     用户 ID
 * @param username   用户名
 * @param ip         登录 IP
 * @param userAgent  User-Agent 原始值
 * @param deviceType 设备类型（Chrome/Firefox/Safari/Edge/Mobile/Postman/Unknown）
 * @param loginAt    登录时刻
 * @param expiresAt  token 过期时刻
 */
public record SessionInfo(
        String jti,
        Long userId,
        String username,
        String ip,
        String userAgent,
        String deviceType,
        LocalDateTime loginAt,
        LocalDateTime expiresAt) {}

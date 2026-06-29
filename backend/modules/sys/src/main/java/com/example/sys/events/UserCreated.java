package com.example.sys.events;

import java.time.LocalDateTime;

/**
 * 用户创建事件。其他模块可监听此事件做后续处理（如发送通知、初始化数据）。
 *
 * @param userId   用户 ID
 * @param username 用户名
 * @param occurredAt 发生时间
 */
public record UserCreated(Long userId, String username, LocalDateTime occurredAt) {

    public UserCreated(Long userId, String username) {
        this(userId, username, LocalDateTime.now());
    }
}

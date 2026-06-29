package com.example.sys.events;

import java.time.LocalDateTime;

/**
 * 角色权限变更事件。其他模块可监听此事件清除权限缓存。
 *
 * @param roleId    角色 ID
 * @param roleCode  角色编码
 * @param occurredAt 发生时间
 */
public record RolePermissionChanged(Long roleId, String roleCode, LocalDateTime occurredAt) {

    public RolePermissionChanged(Long roleId, String roleCode) {
        this(roleId, roleCode, LocalDateTime.now());
    }
}

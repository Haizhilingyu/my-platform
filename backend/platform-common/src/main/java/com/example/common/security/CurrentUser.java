package com.example.common.security;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * 当前登录用户上下文。由认证模块填充，业务代码通过此类获取当前用户信息。
 */
public class CurrentUser {

    private static final ThreadLocal<UserInfo> HOLDER = new ThreadLocal<>();

    private CurrentUser() {}

    public static void set(UserInfo user) {
        HOLDER.set(user);
    }

    public static UserInfo get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static Long getUserId() {
        UserInfo u = get();
        return u != null ? u.userId() : null;
    }

    public static String getUsername() {
        UserInfo u = get();
        return u != null ? u.username() : null;
    }

    public static Set<String> getPermissions() {
        UserInfo u = get();
        return u != null ? u.permissions() : Collections.emptySet();
    }

    public static Set<String> getRoles() {
        UserInfo u = get();
        return u != null ? u.roles() : Collections.emptySet();
    }

    public static boolean hasPermission(String permission) {
        return getPermissions().contains(permission);
    }

    public static boolean hasAnyPermission(Collection<String> permissions) {
        Set<String> userPerms = getPermissions();
        return permissions.stream().anyMatch(userPerms::contains);
    }

    public static boolean hasRole(String role) {
        return getRoles().contains(role);
    }

    /**
     * 用户信息记录。
     *
     * @param userId      用户 ID
     * @param username    用户名
     * @param unitId      所属单位 ID
     * @param roles       角色编码集合
     * @param permissions 权限标识集合
     */
    public record UserInfo(Long userId, String username, Long unitId, Set<String> roles,
                           Set<String> permissions) {}
}

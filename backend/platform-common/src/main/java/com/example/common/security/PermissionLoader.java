package com.example.common.security;

import java.util.Set;

/**
 * 权限加载 SPI。由 platform-common 定义，业务模块（如 sys）提供实现，
 * 上层（app/SecurityConfig）只依赖本接口，从而解耦对具体业务模块的编译期依赖。
 *
 * <p>这是应用层安全（JWT 过滤器、权限填充）所需的最小契约：
 * <ul>
 *   <li>{@link #loadPermissions(Long)} / {@link #loadRoles(Long)}：加载标识集合</li>
 *   <li>{@link #loadUserInfo(Long)}：组装 {@link CurrentUser.UserInfo} 记录</li>
 *   <li>{@link #hasPermission(Long, String)}：单权限校验</li>
 * </ul>
 *
 * <p>实现方负责具体的查库逻辑与（未来的）缓存策略；本接口不关心数据来源。
 */
public interface PermissionLoader {

    /** 加载用户的全部权限标识。 */
    Set<String> loadPermissions(Long userId);

    /** 加载用户的全部角色编码。 */
    Set<String> loadRoles(Long userId);

    /** 组装用户信息记录（含权限与角色），用于填充 {@link CurrentUser} 上下文。 */
    CurrentUser.UserInfo loadUserInfo(Long userId);

    /** 校验指定用户是否拥有某项权限。 */
    boolean hasPermission(Long userId, String permission);
}

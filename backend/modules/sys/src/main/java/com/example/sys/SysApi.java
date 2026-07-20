package com.example.sys;

import com.example.sys.dto.UserCreateDTO;
import com.example.sys.service.ConfigService;
import com.example.sys.service.PermissionService;
import com.example.sys.service.UserService;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 系统设置模块对外 API 门面。
 *
 * <p>其他模块或应用通过此类调用 sys 模块的核心能力，避免直接依赖内部 Service。
 */
@Component
@RequiredArgsConstructor
public class SysApi {

  private final PermissionService permissionService;
  private final ConfigService configService;
  private final UserService userService;

  /** 获取用户权限标识集合。 */
  public Set<String> getUserPermissions(Long userId) {
    return permissionService.getUserPermissions(userId);
  }

  /** 获取用户角色编码集合。 */
  public Set<String> getUserRoles(Long userId) {
    return permissionService.getUserRoleCodes(userId);
  }

  /** 校验用户是否拥有指定权限。 */
  public boolean hasPermission(Long userId, String permission) {
    return permissionService.getUserPermissions(userId).contains(permission);
  }

  /** 按 key 获取配置值，支持默认值。 */
  public String getConfig(String key, String defaultValue) {
    return configService.getValue(key, defaultValue);
  }

  /** 创建用户（供 ai-agent 等模块复用，复用 UserService 全部校验/加密逻辑）。 */
  public Long createUser(UserCreateDTO dto) {
    return userService.create(dto);
  }

  /** 删除用户（供 ai-agent 等模块复用）。 */
  public void deleteUser(Long id) {
    userService.delete(id);
  }
}

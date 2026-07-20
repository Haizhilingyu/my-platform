package com.example.sys;

import com.example.sys.domain.SysRole;
import com.example.sys.dto.RoleDTO;
import com.example.sys.dto.UserCreateDTO;
import com.example.sys.dto.UserVO;
import com.example.sys.service.ConfigService;
import com.example.sys.service.PermissionService;
import com.example.sys.service.RoleService;
import com.example.sys.service.UserService;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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
  private final RoleService roleService;

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

  /** 用户列表（关键字模糊匹配，限 limit 条，供 AI 工具只读展示）。 */
  public List<UserVO> listUsers(String keyword, int limit) {
    int size = Math.max(1, Math.min(limit <= 0 ? 20 : limit, 50));
    return userService
        .search(
            keyword == null || keyword.isBlank() ? null : keyword,
            null,
            null,
            PageRequest.of(0, size))
        .getContent();
  }

  /** 给用户分配角色（roleIds 覆盖式）。 */
  public void assignRoles(Long userId, List<Long> roleIds) {
    userService.assignRoles(userId, roleIds == null ? List.of() : roleIds);
  }

  /** 查询用户已分配的角色 ID。 */
  public List<Long> getUserRoleIds(Long userId) {
    return userService.getUserRoleIds(userId);
  }

  /** 全部角色列表（供 AI 工具只读展示）。 */
  public List<SysRole> listRoles() {
    return roleService.findAll();
  }

  /** 创建角色（供 ai-agent 等模块复用）。 */
  public Long createRole(RoleDTO dto) {
    return roleService.create(dto);
  }

  /** 给角色分配菜单（menuIds 覆盖式）。 */
  public void assignRoleMenus(Long roleId, List<Long> menuIds) {
    roleService.assignMenus(roleId, menuIds == null ? List.of() : menuIds);
  }
}

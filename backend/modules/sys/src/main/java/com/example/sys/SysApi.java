package com.example.sys;

import com.example.sys.domain.SysConfig;
import com.example.sys.domain.SysRole;
import com.example.sys.dto.ConfigDTO;
import com.example.sys.dto.MenuDTO;
import com.example.sys.dto.MenuTreeNode;
import com.example.sys.dto.RoleDTO;
import com.example.sys.dto.SessionInfo;
import com.example.sys.dto.UnitDTO;
import com.example.sys.dto.UnitTreeNode;
import com.example.sys.dto.UserCreateDTO;
import com.example.sys.dto.UserUpdateDTO;
import com.example.sys.dto.UserVO;
import com.example.sys.service.ConfigService;
import com.example.sys.service.LoginSecurityService;
import com.example.sys.service.MenuService;
import com.example.sys.service.PermissionService;
import com.example.sys.service.RoleService;
import com.example.sys.service.SessionService;
import com.example.sys.service.UnitService;
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
  private final MenuService menuService;
  private final UnitService unitService;
  private final SessionService sessionService;
  private final LoginSecurityService loginSecurityService;

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

  // ===== 用户 =====

  /** 创建用户（供 ai-agent 等模块复用，复用 UserService 全部校验/加密逻辑）。 */
  public Long createUser(UserCreateDTO dto) {
    return userService.create(dto);
  }

  /** 删除用户（供 ai-agent 等模块复用）。 */
  public void deleteUser(Long id) {
    userService.delete(id);
  }

  /** 批量删除用户（数据权限保护）。 */
  public void deleteUsers(List<Long> ids) {
    userService.deleteBatch(ids);
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

  /** 查询单个用户。 */
  public UserVO getUser(Long id) {
    return userService.getById(id);
  }

  /** 修改用户信息。 */
  public void updateUser(Long id, UserUpdateDTO dto) {
    userService.update(id, dto);
  }

  /** 给用户分配角色（roleIds 覆盖式）。 */
  public void assignRoles(Long userId, List<Long> roleIds) {
    userService.assignRoles(userId, roleIds == null ? List.of() : roleIds);
  }

  /** 查询用户已分配的角色 ID。 */
  public List<Long> getUserRoleIds(Long userId) {
    return userService.getUserRoleIds(userId);
  }

  /** 重置用户密码（同时解锁账号）。 */
  public void resetPassword(Long id, String newPassword) {
    userService.resetPassword(id, newPassword);
  }

  /** 解锁用户账号（按 userId 查用户名后解锁）。 */
  public void unlockUser(Long id) {
    UserVO user = userService.getById(id);
    if (user != null) {
      loginSecurityService.unlock(user.getUsername());
    }
  }

  /** 查询用户活跃会话列表。 */
  public List<SessionInfo> listUserSessions(Long userId) {
    return sessionService.listSessions(userId);
  }

  /** 撤销指定会话（强制下线）。 */
  public void revokeSession(String jti) {
    sessionService.revokeSession(jti);
  }

  // ===== 角色 =====

  /** 全部角色列表（供 AI 工具只读展示）。 */
  public List<SysRole> listRoles() {
    return roleService.findAll();
  }

  /** 查询单个角色。 */
  public SysRole getRole(Long id) {
    return roleService.getById(id);
  }

  /** 创建角色（供 ai-agent 等模块复用）。 */
  public Long createRole(RoleDTO dto) {
    return roleService.create(dto);
  }

  /** 修改角色信息。 */
  public void updateRole(Long id, RoleDTO dto) {
    roleService.update(id, dto);
  }

  /** 删除角色（级联删除角色-菜单关联）。 */
  public void deleteRole(Long id) {
    roleService.delete(id);
  }

  /** 给角色分配菜单（menuIds 覆盖式）。 */
  public void assignRoleMenus(Long roleId, List<Long> menuIds) {
    roleService.assignMenus(roleId, menuIds == null ? List.of() : menuIds);
  }

  /** 查询角色已分配的菜单 ID 列表。 */
  public List<Long> getRoleMenuIds(Long roleId) {
    return roleService.getRoleMenuIds(roleId);
  }

  // ===== 菜单 =====

  /** 查询菜单树结构。 */
  public List<MenuTreeNode> getMenuTree() {
    return menuService.getTree();
  }

  /** 创建菜单。 */
  public Long createMenu(MenuDTO dto) {
    return menuService.create(dto);
  }

  /** 修改菜单。 */
  public void updateMenu(Long id, MenuDTO dto) {
    menuService.update(id, dto);
  }

  /** 删除菜单（有子菜单时不允许删除）。 */
  public void deleteMenu(Long id) {
    menuService.delete(id);
  }

  // ===== 组织 =====

  /** 查询组织树结构。 */
  public List<UnitTreeNode> getUnitTree() {
    return unitService.getTree();
  }

  /** 创建组织/部门。 */
  public Long createUnit(UnitDTO dto) {
    return unitService.create(dto);
  }

  /** 修改组织信息。 */
  public void updateUnit(Long id, UnitDTO dto) {
    unitService.update(id, dto);
  }

  /** 删除组织（有子组织时不允许删除）。 */
  public void deleteUnit(Long id) {
    unitService.delete(id);
  }

  // ===== 配置 =====

  /** 查询配置列表（按分类筛选）。 */
  public List<SysConfig> listConfigs(String category) {
    return configService.findByCategory(category);
  }

  /** 按 key 查询单条配置。 */
  public SysConfig getConfigByKey(String key) {
    return configService.getByKey(key);
  }

  /** 创建配置。 */
  public Long createConfig(ConfigDTO dto) {
    return configService.create(dto);
  }

  /** 修改配置。 */
  public void updateConfig(Long id, ConfigDTO dto) {
    configService.update(id, dto);
  }
}

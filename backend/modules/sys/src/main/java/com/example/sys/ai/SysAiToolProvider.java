package com.example.sys.ai;

import com.example.common.ai.*;
import com.example.sys.SysApi;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SysAiToolProvider implements AiToolProvider {

  private final SysApi sysApi;

  @Override
  public List<AgentTool> getTools() {
    return List.of(
        // 用户管理（12 个）
        tool(
            "listUsers",
            "查询系统用户列表（只读）。当用户说'查看用户'、'有哪些用户'、'用户列表'时触发。"
                + "参数：keyword(可选,按用户名/姓名模糊搜索)、limit(可选,返回条数,默认20)。",
            "sys:user:list",
            SysToolInputs.SearchInput.class,
            new String[] {"查看用户", "列出用户", "用户列表", "list users", "查询用户"},
            false,
            this::listUsers),
        tool(
            "getUser",
            "查询单个用户详情。当用户说'查看用户详情'、'用户5的信息'时触发。参数：id(必填)。",
            "sys:user:list",
            SysToolInputs.IdInput.class,
            new String[] {"用户详情", "查看用户信息"},
            false,
            this::getUser),
        tool(
            "createUser",
            "创建系统用户。当用户说'创建用户'、'新建用户'时触发。"
                + "参数：username(必填,字母数字下划线3-32)、password(必填,6-32)；"
                + "可选 realName/email/phone/unitId。",
            "sys:user:add",
            SysToolInputs.CreateUserInput.class,
            new String[] {"创建用户", "新建用户", "添加用户", "create user"},
            false,
            this::createUser),
        tool(
            "updateUser",
            "修改用户信息。当用户说'修改用户'、'更新用户'时触发。参数：id(必填)，" + "可选 realName/email/phone/unitId/status。",
            "sys:user:edit",
            SysToolInputs.UpdateUserInput.class,
            new String[] {"修改用户", "更新用户", "编辑用户"},
            false,
            this::updateUser),
        tool(
            "deleteUser",
            "删除单个用户。当用户说'删除用户'时触发。参数：id(必填)。",
            "sys:user:delete",
            SysToolInputs.IdInput.class,
            new String[] {"删除用户", "移除用户", "delete user"},
            true,
            this::deleteUser),
        tool(
            "batchDeleteUsers",
            "批量删除用户。当用户说'批量删除用户'时触发。参数：ids(必填,用户ID数组)。",
            "sys:user:delete",
            SysToolInputs.BatchDeleteInput.class,
            new String[] {"批量删除"},
            true,
            this::batchDeleteUsers),
        tool(
            "assignRoles",
            "给用户分配角色（覆盖原有角色）。当用户说'给用户分配角色'时触发。" + "参数：userId(必填)、roleIds(必填,角色ID数组)。",
            "sys:user:role",
            SysToolInputs.AssignRolesInput.class,
            new String[] {"分配角色", "赋予角色", "assign role"},
            true,
            this::assignRoles),
        tool(
            "getUserRoles",
            "查询用户已分配的角色。当用户说'用户有什么角色'时触发。参数：id(必填)。",
            "sys:user:role",
            SysToolInputs.IdInput.class,
            new String[] {"用户角色", "用户有什么角色"},
            false,
            this::getUserRoles),
        tool(
            "resetUserPassword",
            "重置用户密码（同时解锁账号）。当用户说'重置密码'时触发。" + "参数：userId(必填)、newPassword(必填,6-32)。",
            "sys:user:reset",
            SysToolInputs.ResetPasswordInput.class,
            new String[] {"重置密码", "修改密码"},
            true,
            this::resetUserPassword),
        tool(
            "unlockUser",
            "解锁被锁定的用户账号。当用户说'解锁用户'时触发。参数：id(必填)。",
            "sys:user:unlock",
            SysToolInputs.IdInput.class,
            new String[] {"解锁用户", "解锁账号"},
            false,
            this::unlockUser),
        tool(
            "listUserSessions",
            "查询用户的活跃登录会话。当用户说'用户登录了哪些设备'时触发。参数：id(必填)。",
            "sys:user:session",
            SysToolInputs.IdInput.class,
            new String[] {"登录会话", "在线设备", "用户会话"},
            false,
            this::listUserSessions),
        tool(
            "revokeUserSession",
            "强制下线用户的指定会话。当用户说'踢下线'、'强制登出'时触发。" + "参数：userId(必填)、jti(必填,会话令牌ID)。",
            "sys:user:session",
            SysToolInputs.RevokeSessionInput.class,
            new String[] {"踢下线", "强制登出", "撤销会话"},
            false,
            this::revokeUserSession),

        // 角色管理（7 个）
        tool(
            "listRoles",
            "查询全部角色列表（只读）。当用户说'查看角色'、'角色列表'时触发。无参数。",
            "sys:role:list",
            SysToolInputs.EmptyInput.class,
            new String[] {"查看角色", "列出角色", "角色列表", "list roles", "查询角色"},
            false,
            this::listRoles),
        tool(
            "getRole",
            "查询单个角色详情。当用户说'角色详情'时触发。参数：id(必填)。",
            "sys:role:list",
            SysToolInputs.IdInput.class,
            new String[] {"角色详情", "角色信息"},
            false,
            this::getRole),
        tool(
            "createRole",
            "创建系统角色。当用户说'创建角色'时触发。"
                + "参数：roleCode(必填,字母数字下划线3-50)、roleName(必填)；可选 dataScope/remark。",
            "sys:role:add",
            SysToolInputs.CreateRoleInput.class,
            new String[] {"创建角色", "新建角色", "添加角色", "create role"},
            false,
            this::createRole),
        tool(
            "updateRole",
            "修改角色信息。当用户说'修改角色'时触发。参数：id(必填)，可选 roleName/dataScope/status。",
            "sys:role:edit",
            SysToolInputs.UpdateRoleInput.class,
            new String[] {"修改角色", "更新角色", "编辑角色"},
            false,
            this::updateRole),
        tool(
            "deleteRole",
            "删除角色（级联删除角色-菜单关联）。当用户说'删除角色'时触发。参数：id(必填)。",
            "sys:role:delete",
            SysToolInputs.IdInput.class,
            new String[] {"删除角色", "移除角色"},
            true,
            this::deleteRole),
        tool(
            "assignRoleMenus",
            "给角色分配菜单权限（覆盖原有菜单）。当用户说'给角色分配菜单'时触发。" + "参数：roleId(必填)、menuIds(必填,菜单ID数组)。",
            "sys:role:perm",
            SysToolInputs.AssignRoleMenusInput.class,
            new String[] {"分配菜单", "角色权限", "assign menus"},
            true,
            this::assignRoleMenus),
        tool(
            "getRoleMenus",
            "查询角色已分配的菜单ID列表。当用户说'角色有哪些菜单'时触发。参数：id(必填)。",
            "sys:role:perm",
            SysToolInputs.IdInput.class,
            new String[] {"角色菜单", "角色有哪些菜单"},
            false,
            this::getRoleMenus),

        // 菜单管理（4 个）
        tool(
            "listMenus",
            "查询菜单树结构（只读）。当用户说'查看菜单'、'菜单列表'时触发。无参数。",
            "sys:menu:list",
            SysToolInputs.EmptyInput.class,
            new String[] {"查看菜单", "菜单列表", "菜单树", "list menus"},
            false,
            this::listMenus),
        tool(
            "createMenu",
            "创建菜单项。当用户说'创建菜单'时触发。参数：menuName(必填)、menuType(必填)，"
                + "可选 parentId/path/permission/sort。",
            "sys:menu:add",
            SysToolInputs.CreateMenuInput.class,
            new String[] {"创建菜单", "新建菜单", "添加菜单"},
            false,
            this::createMenu),
        tool(
            "updateMenu",
            "修改菜单项。当用户说'修改菜单'时触发。参数：id(必填)，可选 menuName/path/sort/visible/status。",
            "sys:menu:edit",
            SysToolInputs.UpdateMenuInput.class,
            new String[] {"修改菜单", "更新菜单"},
            false,
            this::updateMenu),
        tool(
            "deleteMenu",
            "删除菜单（有子菜单时不允许删除）。当用户说'删除菜单'时触发。参数：id(必填)。",
            "sys:menu:delete",
            SysToolInputs.IdInput.class,
            new String[] {"删除菜单", "移除菜单"},
            true,
            this::deleteMenu),

        // 组织管理（4 个）
        tool(
            "listUnits",
            "查询组织树结构（只读）。当用户说'查看组织'、'部门列表'时触发。无参数。",
            "sys:unit:list",
            SysToolInputs.EmptyInput.class,
            new String[] {"查看组织", "组织列表", "部门列表", "单位列表", "list units"},
            false,
            this::listUnits),
        tool(
            "createUnit",
            "创建组织/部门。当用户说'创建组织'、'新建部门'时触发。"
                + "参数：unitCode(必填,字母数字下划线3-50)、unitName(必填)，可选 parentId/sort。",
            "sys:unit:add",
            SysToolInputs.CreateUnitInput.class,
            new String[] {"创建组织", "新建组织", "创建部门", "新建部门", "添加组织"},
            false,
            this::createUnit),
        tool(
            "updateUnit",
            "修改组织信息。当用户说'修改组织'时触发。参数：id(必填)，可选 unitName/sort/status。",
            "sys:unit:edit",
            SysToolInputs.UpdateUnitInput.class,
            new String[] {"修改组织", "更新组织", "修改部门"},
            false,
            this::updateUnit),
        tool(
            "deleteUnit",
            "删除组织（有子组织时不允许删除）。当用户说'删除组织'时触发。参数：id(必填)。",
            "sys:unit:delete",
            SysToolInputs.IdInput.class,
            new String[] {"删除组织", "删除部门", "移除组织"},
            true,
            this::deleteUnit),

        // 配置管理（4 个）
        tool(
            "listConfigs",
            "查询系统配置列表（只读）。当用户说'查看配置'、'系统配置'时触发。" + "参数：keyword(可选,按分类筛选)。",
            "sys:config:list",
            SysToolInputs.SearchInput.class,
            new String[] {"查看配置", "系统配置", "配置列表", "list configs", "查询配置"},
            false,
            this::listConfigs),
        tool(
            "getConfig",
            "按 key 查询单条配置。当用户说'配置值'时触发。参数：key(必填,配置键名)。",
            "sys:config:list",
            SysToolInputs.GetConfigInput.class,
            new String[] {"配置值", "查看配置项"},
            false,
            this::getConfig),
        tool(
            "createConfig",
            "创建系统配置。当用户说'创建配置'时触发。参数：configKey(必填)，" + "可选 configValue/description/category。",
            "sys:config:add",
            SysToolInputs.CreateConfigInput.class,
            new String[] {"创建配置", "新建配置", "添加配置"},
            false,
            this::createConfig),
        tool(
            "updateConfig",
            "修改配置值。当用户说'修改配置'时触发。参数：id(必填)，可选 configValue/description。",
            "sys:config:edit",
            SysToolInputs.UpdateConfigInput.class,
            new String[] {"修改配置", "更新配置"},
            false,
            this::updateConfig));
  }

  // === 私有工厂方法（减少重复）===
  private static AgentTool tool(
      String name,
      String desc,
      String perm,
      Class<?> schema,
      String[] keywords,
      boolean destructive,
      java.util.function.Function<Map<String, Object>, ToolResult> exec) {
    return new AgentTool(name, desc, perm, schema, keywords, destructive, exec);
  }

  // === 执行体（private 方法，调用 SysApi + AiToolSupport）===

  // 用户管理
  private ToolResult listUsers(Map<String, Object> args) {
    AiToolSupport.requirePermission("sys:user:list");
    String keyword = AiToolSupport.strOrNull(args, "keyword");
    int limit = AiToolSupport.intOr(args, "limit", 20);
    List<UserVO> users = sysApi.listUsers(keyword, limit);
    if (users.isEmpty()) {
      return new ToolResult(true, "没有匹配的用户", null, null);
    }
    String summary =
        "找到 "
            + users.size()
            + " 个用户：\n"
            + users.stream()
                .map(u -> "• #" + u.getId() + " " + u.getUsername() + "（" + u.getRealName() + "）")
                .collect(Collectors.joining("\n"));
    return new ToolResult(true, summary, null, "/sys/user");
  }

  private ToolResult getUser(Map<String, Object> args) {
    AiToolSupport.requirePermission("sys:user:list");
    Long id = AiToolSupport.longOrNull(args, "id");
    UserVO user = sysApi.getUser(id);
    String summary =
        "用户详情：\n"
            + "• ID: "
            + user.getId()
            + "\n"
            + "• 用户名: "
            + user.getUsername()
            + "\n"
            + "• 姓名: "
            + user.getRealName()
            + "\n"
            + "• 邮箱: "
            + user.getEmail()
            + "\n"
            + "• 电话: "
            + user.getPhone()
            + "\n"
            + "• 组织ID: "
            + user.getUnitId()
            + "\n"
            + "• 状态: "
            + (user.getStatus() == 1 ? "启用" : "禁用");
    return new ToolResult(true, summary, id, "/sys/user");
  }

  private ToolResult createUser(Map<String, Object> args) {
    AiToolSupport.requirePermission("sys:user:add");
    String username = AiToolSupport.str(args, "username");
    String password = AiToolSupport.str(args, "password");
    UserCreateDTO dto = new UserCreateDTO();
    dto.setUsername(username);
    dto.setPassword(password);
    dto.setRealName(AiToolSupport.strOrNull(args, "realName"));
    dto.setEmail(AiToolSupport.strOrNull(args, "email"));
    dto.setPhone(AiToolSupport.strOrNull(args, "phone"));
    dto.setUnitId(AiToolSupport.longOrNull(args, "unitId"));
    Long id = sysApi.createUser(dto);
    return new ToolResult(true, "已创建用户 " + username + "（id=" + id + "）", id, "/sys/user");
  }

  private ToolResult updateUser(Map<String, Object> args) {
    AiToolSupport.requirePermission("sys:user:edit");
    Long id = AiToolSupport.longOrNull(args, "id");
    UserUpdateDTO dto = new UserUpdateDTO();
    dto.setRealName(AiToolSupport.strOrNull(args, "realName"));
    dto.setEmail(AiToolSupport.strOrNull(args, "email"));
    dto.setPhone(AiToolSupport.strOrNull(args, "phone"));
    dto.setUnitId(AiToolSupport.longOrNull(args, "unitId"));
    dto.setStatus(AiToolSupport.intOrNull(args, "status"));
    sysApi.updateUser(id, dto);
    return new ToolResult(true, "已更新用户（id=" + id + "）", id, "/sys/user");
  }

  private ToolResult deleteUser(Map<String, Object> args) {
    AiToolSupport.requirePermission("sys:user:delete");
    Long id = AiToolSupport.longOrNull(args, "id");
    sysApi.deleteUser(id);
    return new ToolResult(true, "已删除用户（id=" + id + "）", id, "/sys/user");
  }

  private ToolResult batchDeleteUsers(Map<String, Object> args) {
    AiToolSupport.requirePermission("sys:user:delete");
    List<Long> ids = AiToolSupport.longList(args, "ids");
    sysApi.deleteUsers(ids);
    return new ToolResult(true, "已批量删除 " + ids.size() + " 个用户", null, "/sys/user");
  }

  private ToolResult assignRoles(Map<String, Object> args) {
    AiToolSupport.requirePermission("sys:user:role");
    Long userId = AiToolSupport.longOrNull(args, "userId");
    List<Long> roleIds = AiToolSupport.longList(args, "roleIds");
    sysApi.assignRoles(userId, roleIds);
    return new ToolResult(
        true, "已为用户（id=" + userId + "）分配 " + roleIds.size() + " 个角色", userId, "/sys/user");
  }

  private ToolResult getUserRoles(Map<String, Object> args) {
    AiToolSupport.requirePermission("sys:user:role");
    Long id = AiToolSupport.longOrNull(args, "id");
    List<Long> roleIds = sysApi.getUserRoleIds(id);
    if (roleIds.isEmpty()) {
      return new ToolResult(true, "用户未分配任何角色", id, "/sys/user");
    }
    String summary =
        "用户已分配的角色ID：\n"
            + roleIds.stream().map(rid -> "• #" + rid).collect(Collectors.joining("\n"));
    return new ToolResult(true, summary, id, "/sys/user");
  }

  private ToolResult resetUserPassword(Map<String, Object> args) {
    AiToolSupport.requirePermission("sys:user:reset");
    Long userId = AiToolSupport.longOrNull(args, "userId");
    String newPassword = AiToolSupport.str(args, "newPassword");
    sysApi.resetPassword(userId, newPassword);
    return new ToolResult(true, "已重置用户密码并解锁账号（id=" + userId + "）", userId, "/sys/user");
  }

  private ToolResult unlockUser(Map<String, Object> args) {
    AiToolSupport.requirePermission("sys:user:unlock");
    Long id = AiToolSupport.longOrNull(args, "id");
    sysApi.unlockUser(id);
    return new ToolResult(true, "已解锁用户账号（id=" + id + "）", id, "/sys/user");
  }

  private ToolResult listUserSessions(Map<String, Object> args) {
    AiToolSupport.requirePermission("sys:user:session");
    Long id = AiToolSupport.longOrNull(args, "id");
    List<SessionInfo> sessions = sysApi.listUserSessions(id);
    if (sessions.isEmpty()) {
      return new ToolResult(true, "用户当前无活跃会话", id, "/sys/user");
    }
    String summary =
        "用户有 "
            + sessions.size()
            + " 个活跃会话：\n"
            + sessions.stream()
                .map(
                    s ->
                        "• JTI: "
                            + s.jti()
                            + " | IP: "
                            + s.ip()
                            + " | 设备: "
                            + s.deviceType()
                            + " | 登录时间: "
                            + s.loginAt())
                .collect(Collectors.joining("\n"));
    return new ToolResult(true, summary, id, "/sys/user");
  }

  private ToolResult revokeUserSession(Map<String, Object> args) {
    AiToolSupport.requirePermission("sys:user:session");
    String jti = AiToolSupport.str(args, "jti");
    sysApi.revokeSession(jti);
    return new ToolResult(true, "已撤销会话（jti=" + jti + "）", null, "/sys/user");
  }

  // 角色管理
  private ToolResult listRoles(Map<String, Object> args) {
    AiToolSupport.requirePermission("sys:role:list");
    List<SysRole> roles = sysApi.listRoles();
    if (roles.isEmpty()) {
      return new ToolResult(true, "没有角色数据", null, null);
    }
    String summary =
        "找到 "
            + roles.size()
            + " 个角色：\n"
            + roles.stream()
                .map(r -> "• #" + r.getId() + " " + r.getRoleCode() + " " + r.getRoleName())
                .collect(Collectors.joining("\n"));
    return new ToolResult(true, summary, null, "/sys/role");
  }

  private ToolResult getRole(Map<String, Object> args) {
    AiToolSupport.requirePermission("sys:role:list");
    Long id = AiToolSupport.longOrNull(args, "id");
    SysRole role = sysApi.getRole(id);
    String summary =
        "角色详情：\n"
            + "• ID: "
            + role.getId()
            + "\n"
            + "• 角色编码: "
            + role.getRoleCode()
            + "\n"
            + "• 角色名称: "
            + role.getRoleName()
            + "\n"
            + "• 数据范围: "
            + role.getDataScope()
            + "\n"
            + "• 状态: "
            + (role.getStatus() == 1 ? "启用" : "禁用")
            + "\n"
            + "• 备注: "
            + role.getRemark();
    return new ToolResult(true, summary, id, "/sys/role");
  }

  private ToolResult createRole(Map<String, Object> args) {
    AiToolSupport.requirePermission("sys:role:add");
    String roleCode = AiToolSupport.str(args, "roleCode");
    RoleDTO dto = new RoleDTO();
    dto.setRoleCode(roleCode);
    dto.setRoleName(AiToolSupport.str(args, "roleName"));
    dto.setDataScope(AiToolSupport.strOrNull(args, "dataScope"));
    dto.setRemark(AiToolSupport.strOrNull(args, "remark"));
    Long id = sysApi.createRole(dto);
    return new ToolResult(true, "已创建角色 " + roleCode + "（id=" + id + "）", id, "/sys/role");
  }

  private ToolResult updateRole(Map<String, Object> args) {
    AiToolSupport.requirePermission("sys:role:edit");
    Long id = AiToolSupport.longOrNull(args, "id");
    RoleDTO dto = new RoleDTO();
    dto.setRoleName(AiToolSupport.strOrNull(args, "roleName"));
    dto.setDataScope(AiToolSupport.strOrNull(args, "dataScope"));
    dto.setStatus(AiToolSupport.intOrNull(args, "status"));
    dto.setRemark(AiToolSupport.strOrNull(args, "remark"));
    sysApi.updateRole(id, dto);
    return new ToolResult(true, "已更新角色（id=" + id + "）", id, "/sys/role");
  }

  private ToolResult deleteRole(Map<String, Object> args) {
    AiToolSupport.requirePermission("sys:role:delete");
    Long id = AiToolSupport.longOrNull(args, "id");
    sysApi.deleteRole(id);
    return new ToolResult(true, "已删除角色（id=" + id + "）", id, "/sys/role");
  }

  private ToolResult assignRoleMenus(Map<String, Object> args) {
    AiToolSupport.requirePermission("sys:role:perm");
    Long roleId = AiToolSupport.longOrNull(args, "roleId");
    List<Long> menuIds = AiToolSupport.longList(args, "menuIds");
    sysApi.assignRoleMenus(roleId, menuIds);
    return new ToolResult(
        true, "已为角色（id=" + roleId + "）分配 " + menuIds.size() + " 个菜单", roleId, "/sys/role");
  }

  private ToolResult getRoleMenus(Map<String, Object> args) {
    AiToolSupport.requirePermission("sys:role:perm");
    Long id = AiToolSupport.longOrNull(args, "id");
    List<Long> menuIds = sysApi.getRoleMenuIds(id);
    if (menuIds.isEmpty()) {
      return new ToolResult(true, "角色未分配任何菜单", id, "/sys/role");
    }
    String summary =
        "角色已分配的菜单ID：\n"
            + menuIds.stream().map(mid -> "• #" + mid).collect(Collectors.joining("\n"));
    return new ToolResult(true, summary, id, "/sys/role");
  }

  // 菜单管理
  private ToolResult listMenus(Map<String, Object> args) {
    AiToolSupport.requirePermission("sys:menu:list");
    List<MenuTreeNode> tree = sysApi.getMenuTree();
    if (tree.isEmpty()) {
      return new ToolResult(true, "没有菜单数据", null, null);
    }
    String summary = formatMenuTree(tree, 0);
    return new ToolResult(true, summary, null, "/sys/menu");
  }

  private String formatMenuTree(List<MenuTreeNode> nodes, int indent) {
    String prefix = "  ".repeat(indent);
    return nodes.stream()
        .map(
            node -> {
              StringBuilder sb = new StringBuilder();
              sb.append(prefix)
                  .append("• #")
                  .append(node.getId())
                  .append(" ")
                  .append(node.getMenuName())
                  .append(" [")
                  .append(node.getMenuType())
                  .append("]");
              if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                sb.append("\n").append(formatMenuTree(node.getChildren(), indent + 1));
              }
              return sb.toString();
            })
        .collect(Collectors.joining("\n"));
  }

  private ToolResult createMenu(Map<String, Object> args) {
    AiToolSupport.requirePermission("sys:menu:add");
    String menuName = AiToolSupport.str(args, "menuName");
    MenuDTO dto = new MenuDTO();
    dto.setParentId(AiToolSupport.longOrNull(args, "parentId"));
    dto.setMenuName(menuName);
    dto.setMenuType(AiToolSupport.str(args, "menuType"));
    dto.setPath(AiToolSupport.strOrNull(args, "path"));
    dto.setPermission(AiToolSupport.strOrNull(args, "permission"));
    dto.setSort(AiToolSupport.intOrNull(args, "sort"));
    Long id = sysApi.createMenu(dto);
    return new ToolResult(true, "已创建菜单 " + menuName + "（id=" + id + "）", id, "/sys/menu");
  }

  private ToolResult updateMenu(Map<String, Object> args) {
    AiToolSupport.requirePermission("sys:menu:edit");
    Long id = AiToolSupport.longOrNull(args, "id");
    MenuDTO dto = new MenuDTO();
    dto.setMenuName(AiToolSupport.strOrNull(args, "menuName"));
    dto.setPath(AiToolSupport.strOrNull(args, "path"));
    dto.setSort(AiToolSupport.intOrNull(args, "sort"));
    dto.setVisible(AiToolSupport.intOrNull(args, "visible"));
    dto.setStatus(AiToolSupport.intOrNull(args, "status"));
    sysApi.updateMenu(id, dto);
    return new ToolResult(true, "已更新菜单（id=" + id + "）", id, "/sys/menu");
  }

  private ToolResult deleteMenu(Map<String, Object> args) {
    AiToolSupport.requirePermission("sys:menu:delete");
    Long id = AiToolSupport.longOrNull(args, "id");
    sysApi.deleteMenu(id);
    return new ToolResult(true, "已删除菜单（id=" + id + "）", id, "/sys/menu");
  }

  // 组织管理
  private ToolResult listUnits(Map<String, Object> args) {
    AiToolSupport.requirePermission("sys:unit:list");
    List<UnitTreeNode> tree = sysApi.getUnitTree();
    if (tree.isEmpty()) {
      return new ToolResult(true, "没有组织数据", null, null);
    }
    String summary = formatUnitTree(tree, 0);
    return new ToolResult(true, summary, null, "/sys/unit");
  }

  private String formatUnitTree(List<UnitTreeNode> nodes, int indent) {
    String prefix = "  ".repeat(indent);
    return nodes.stream()
        .map(
            node -> {
              StringBuilder sb = new StringBuilder();
              sb.append(prefix)
                  .append("• #")
                  .append(node.getId())
                  .append(" ")
                  .append(node.getUnitName());
              if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                sb.append("\n").append(formatUnitTree(node.getChildren(), indent + 1));
              }
              return sb.toString();
            })
        .collect(Collectors.joining("\n"));
  }

  private ToolResult createUnit(Map<String, Object> args) {
    AiToolSupport.requirePermission("sys:unit:add");
    String unitCode = AiToolSupport.str(args, "unitCode");
    String unitName = AiToolSupport.str(args, "unitName");
    UnitDTO dto = new UnitDTO();
    dto.setParentId(AiToolSupport.longOrNull(args, "parentId"));
    dto.setUnitCode(unitCode);
    dto.setUnitName(unitName);
    dto.setSort(AiToolSupport.intOrNull(args, "sort"));
    Long id = sysApi.createUnit(dto);
    return new ToolResult(
        true, "已创建组织 " + unitName + "（" + unitCode + "，id=" + id + "）", id, "/sys/unit");
  }

  private ToolResult updateUnit(Map<String, Object> args) {
    AiToolSupport.requirePermission("sys:unit:edit");
    Long id = AiToolSupport.longOrNull(args, "id");
    UnitDTO dto = new UnitDTO();
    dto.setUnitName(AiToolSupport.strOrNull(args, "unitName"));
    dto.setSort(AiToolSupport.intOrNull(args, "sort"));
    dto.setStatus(AiToolSupport.intOrNull(args, "status"));
    sysApi.updateUnit(id, dto);
    return new ToolResult(true, "已更新组织（id=" + id + "）", id, "/sys/unit");
  }

  private ToolResult deleteUnit(Map<String, Object> args) {
    AiToolSupport.requirePermission("sys:unit:delete");
    Long id = AiToolSupport.longOrNull(args, "id");
    sysApi.deleteUnit(id);
    return new ToolResult(true, "已删除组织（id=" + id + "）", id, "/sys/unit");
  }

  // 配置管理
  private ToolResult listConfigs(Map<String, Object> args) {
    AiToolSupport.requirePermission("sys:config:list");
    String keyword = AiToolSupport.strOrNull(args, "keyword");
    List<SysConfig> configs = sysApi.listConfigs(keyword);
    if (configs.isEmpty()) {
      return new ToolResult(true, "没有配置数据", null, null);
    }
    String summary =
        "找到 "
            + configs.size()
            + " 条配置：\n"
            + configs.stream()
                .map(
                    c ->
                        "• #"
                            + c.getId()
                            + " "
                            + c.getConfigKey()
                            + " = "
                            + c.getConfigValue()
                            + " ("
                            + c.getCategory()
                            + ")")
                .collect(Collectors.joining("\n"));
    return new ToolResult(true, summary, null, "/sys/config");
  }

  private ToolResult getConfig(Map<String, Object> args) {
    AiToolSupport.requirePermission("sys:config:list");
    String key = AiToolSupport.str(args, "key");
    SysConfig config = sysApi.getConfigByKey(key);
    String summary =
        "配置详情：\n"
            + "• ID: "
            + config.getId()
            + "\n"
            + "• 配置键: "
            + config.getConfigKey()
            + "\n"
            + "• 配置值: "
            + config.getConfigValue()
            + "\n"
            + "• 分类: "
            + config.getCategory()
            + "\n"
            + "• 描述: "
            + config.getDescription();
    return new ToolResult(true, summary, config.getId(), "/sys/config");
  }

  private ToolResult createConfig(Map<String, Object> args) {
    AiToolSupport.requirePermission("sys:config:add");
    String configKey = AiToolSupport.str(args, "configKey");
    ConfigDTO dto = new ConfigDTO();
    dto.setConfigKey(configKey);
    dto.setConfigValue(AiToolSupport.strOrNull(args, "configValue"));
    dto.setDescription(AiToolSupport.strOrNull(args, "description"));
    dto.setCategory(AiToolSupport.strOrNull(args, "category"));
    Long id = sysApi.createConfig(dto);
    return new ToolResult(true, "已创建配置 " + configKey + "（id=" + id + "）", id, "/sys/config");
  }

  private ToolResult updateConfig(Map<String, Object> args) {
    AiToolSupport.requirePermission("sys:config:edit");
    Long id = AiToolSupport.longOrNull(args, "id");
    ConfigDTO dto = new ConfigDTO();
    dto.setConfigValue(AiToolSupport.strOrNull(args, "configValue"));
    dto.setDescription(AiToolSupport.strOrNull(args, "description"));
    sysApi.updateConfig(id, dto);
    return new ToolResult(true, "已更新配置（id=" + id + "）", id, "/sys/config");
  }
}

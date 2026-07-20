package com.example.aiagent.agent.tool;

import com.example.common.audit.Auditable;
import com.example.common.exception.ForbiddenException;
import com.example.common.security.CurrentUser;
import com.example.sys.SysApi;
import com.example.sys.domain.SysRole;
import com.example.sys.dto.RoleDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 角色管理工具执行器。每个方法 = 一个工具的执行体：
 *
 * <ul>
 *   <li>权限兜底校验（防 LLM 幻觉调用）；
 *   <li>{@code @Auditable} 归属当前用户；
 *   <li>通过 {@link SysApi} 复用 sys 模块业务逻辑。
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class RoleToolExecutor {

  private final SysApi sysApi;

  /** 查询角色列表（只读）。 */
  @Auditable(action = "AI_ROLE_LIST", targetType = "ROLE")
  public ToolResult listRoles(Map<String, Object> args) {
    require("sys:role:list");
    List<SysRole> roles = sysApi.listRoles();
    if (roles.isEmpty()) {
      return new ToolResult(true, "系统中暂无角色", null, null);
    }
    StringBuilder sb = new StringBuilder("共有 " + roles.size() + " 个角色：");
    for (SysRole r : roles) {
      sb.append("\n• #")
          .append(r.getId())
          .append(" ")
          .append(r.getRoleCode())
          .append("（")
          .append(r.getRoleName())
          .append("）");
    }
    return new ToolResult(true, sb.toString(), null, "/sys/role");
  }

  /** 创建角色。 */
  @Auditable(action = "AI_ROLE_CREATE", targetType = "ROLE")
  public ToolResult createRole(Map<String, Object> args) {
    require("sys:role:add");
    RoleDTO dto = new RoleDTO();
    dto.setRoleCode(str(args, "roleCode"));
    dto.setRoleName(str(args, "roleName"));
    // 数据范围缺省 SELF（仅本人），状态缺省启用(1)。
    dto.setDataScope(strOr(args, "dataScope", "SELF"));
    dto.setStatus(1);
    dto.setRemark(strOrNull(args, "remark"));
    Long id = sysApi.createRole(dto);
    return new ToolResult(true, "已创建角色 " + dto.getRoleCode() + "（id=" + id + "）", id, "/sys/role");
  }

  /** 给角色分配菜单（覆盖式）。 */
  @Auditable(action = "AI_ROLE_ASSIGN_MENUS", targetType = "ROLE")
  public ToolResult assignRoleMenus(Map<String, Object> args) {
    require("sys:role:perm");
    Long roleId = longOrNull(args, "roleId");
    if (roleId == null) {
      throw new IllegalArgumentException("缺少参数 roleId");
    }
    List<Long> menuIds = longList(args, "menuIds");
    if (menuIds.isEmpty()) {
      throw new IllegalArgumentException("缺少参数 menuIds（菜单ID数组）");
    }
    sysApi.assignRoleMenus(roleId, menuIds);
    return new ToolResult(true, "已为角色（id=" + roleId + "）分配菜单 " + menuIds, roleId, "/sys/role");
  }

  private void require(String permission) {
    Set<String> perms = CurrentUser.getPermissions();
    if (!(perms.contains("*") || perms.contains(permission))) {
      throw ForbiddenException.i18n("error.permission.denied", permission);
    }
  }

  private static String str(Map<String, Object> args, String key) {
    Object v = args.get(key);
    if (v == null || v.toString().isBlank()) {
      throw new IllegalArgumentException("缺少参数 " + key);
    }
    return v.toString();
  }

  private static String strOrNull(Map<String, Object> args, String key) {
    Object v = args.get(key);
    return v == null ? null : v.toString();
  }

  private static String strOr(Map<String, Object> args, String key, String fallback) {
    Object v = args.get(key);
    return (v == null || v.toString().isBlank()) ? fallback : v.toString();
  }

  private static Long longOrNull(Map<String, Object> args, String key) {
    Object v = args.get(key);
    if (v == null) {
      return null;
    }
    if (v instanceof Number n) {
      return n.longValue();
    }
    return Long.valueOf(v.toString());
  }

  private static List<Long> longList(Map<String, Object> args, String key) {
    Object v = args.get(key);
    if (v instanceof List<?> list) {
      List<Long> result = new ArrayList<>(list.size());
      for (Object item : list) {
        if (item == null) {
          continue;
        }
        if (item instanceof Number n) {
          result.add(n.longValue());
        } else {
          result.add(Long.valueOf(item.toString()));
        }
      }
      return result;
    }
    return List.of();
  }
}

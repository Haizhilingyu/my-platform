package com.example.aiagent.agent.tool;

import com.example.common.audit.Auditable;
import com.example.common.exception.ForbiddenException;
import com.example.common.security.CurrentUser;
import com.example.sys.SysApi;
import com.example.sys.dto.UserCreateDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 用户管理工具执行器。每个方法 = 一个工具的执行体：
 *
 * <ul>
 *   <li>权限兜底校验（第二层保障，防 LLM/Mock 幻觉调用）；
 *   <li>{@code @Auditable} 归属当前用户；
 *   <li>通过 {@link SysApi} 复用 sys 模块业务逻辑。
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class UserToolExecutor {

  private final SysApi sysApi;

  @Auditable(action = "AI_USER_CREATE", targetType = "USER")
  public ToolResult createUser(Map<String, Object> args) {
    require("sys:user:add");
    UserCreateDTO dto = new UserCreateDTO();
    dto.setUsername(str(args, "username"));
    dto.setPassword(str(args, "password"));
    dto.setRealName(strOrNull(args, "realName"));
    dto.setEmail(strOrNull(args, "email"));
    dto.setPhone(strOrNull(args, "phone"));
    Long unitId = longOrNull(args, "unitId");
    if (unitId != null) {
      dto.setUnitId(unitId);
    }
    Long id = sysApi.createUser(dto);
    return new ToolResult(true, "已创建用户 " + dto.getUsername() + "（id=" + id + "）", id, "/sys/user");
  }

  @Auditable(action = "AI_USER_DELETE", targetType = "USER")
  public ToolResult deleteUser(Map<String, Object> args) {
    require("sys:user:delete");
    Long id = longOrNull(args, "id");
    if (id == null) {
      throw new IllegalArgumentException("缺少参数 id");
    }
    sysApi.deleteUser(id);
    return new ToolResult(true, "已删除用户（id=" + id + "）", id, "/sys/user");
  }

  /** 查询用户列表（只读，供 AI 展示）。 */
  @Auditable(action = "AI_USER_LIST", targetType = "USER")
  public ToolResult listUsers(Map<String, Object> args) {
    require("sys:user:list");
    String keyword = strOrNull(args, "keyword");
    int limit = intOr(args, "limit", 20);
    var users = sysApi.listUsers(keyword, limit);
    if (users.isEmpty()) {
      return new ToolResult(true, "没有匹配的用户", null, null);
    }
    StringBuilder sb = new StringBuilder("找到 " + users.size() + " 个用户：");
    for (var u : users) {
      sb.append("\n• #")
          .append(u.getId())
          .append(" ")
          .append(u.getUsername())
          .append(
              u.getRealName() == null || u.getRealName().isBlank()
                  ? ""
                  : "（" + u.getRealName() + "）");
    }
    return new ToolResult(true, sb.toString(), null, "/sys/user");
  }

  /** 给用户分配角色（覆盖式）。 */
  @Auditable(action = "AI_USER_ASSIGN_ROLES", targetType = "USER")
  public ToolResult assignRoles(Map<String, Object> args) {
    require("sys:user:role");
    Long userId = longOrNull(args, "userId");
    if (userId == null) {
      throw new IllegalArgumentException("缺少参数 userId");
    }
    List<Long> roleIds = longList(args, "roleIds");
    if (roleIds.isEmpty()) {
      throw new IllegalArgumentException("缺少参数 roleIds（角色ID数组）");
    }
    sysApi.assignRoles(userId, roleIds);
    return new ToolResult(true, "已为用户（id=" + userId + "）分配角色 " + roleIds, userId, "/sys/user");
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

  private static int intOr(Map<String, Object> args, String key, int fallback) {
    Object v = args.get(key);
    if (v == null) {
      return fallback;
    }
    if (v instanceof Number n) {
      return n.intValue();
    }
    try {
      return Integer.valueOf(v.toString());
    } catch (NumberFormatException e) {
      return fallback;
    }
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

package com.example.sys.ai;

import java.util.List;
import java.util.Optional;

public final class SysToolInputs {

  // 共享形状
  public record EmptyInput() {}

  public record IdInput(Long id) {}

  public record SearchInput(Optional<String> keyword, Optional<Integer> limit) {}

  // 用户
  public record CreateUserInput(
      String username,
      String password,
      Optional<String> realName,
      Optional<String> email,
      Optional<String> phone,
      Optional<Long> unitId) {}

  public record UpdateUserInput(
      Long id,
      Optional<String> realName,
      Optional<String> email,
      Optional<String> phone,
      Optional<Long> unitId,
      Optional<Integer> status) {}

  public record AssignRolesInput(Long userId, List<Long> roleIds) {}

  public record ResetPasswordInput(Long userId, String newPassword) {}

  public record RevokeSessionInput(Long userId, String jti) {}

  public record BatchDeleteInput(List<Long> ids) {}

  // 角色
  public record CreateRoleInput(
      String roleCode, String roleName, Optional<String> dataScope, Optional<String> remark) {}

  public record UpdateRoleInput(
      Long id,
      Optional<String> roleName,
      Optional<String> dataScope,
      Optional<Integer> status,
      Optional<String> remark) {}

  public record AssignRoleMenusInput(Long roleId, List<Long> menuIds) {}

  // 菜单
  public record CreateMenuInput(
      Optional<Long> parentId,
      String menuName,
      String menuType,
      Optional<String> path,
      Optional<String> permission,
      Optional<Integer> sort) {}

  public record UpdateMenuInput(
      Long id,
      Optional<String> menuName,
      Optional<String> path,
      Optional<Integer> sort,
      Optional<Integer> visible,
      Optional<Integer> status) {}

  // 组织
  public record CreateUnitInput(
      Optional<Long> parentId, String unitCode, String unitName, Optional<Integer> sort) {}

  public record UpdateUnitInput(
      Long id, Optional<String> unitName, Optional<Integer> sort, Optional<Integer> status) {}

  // 配置
  public record CreateConfigInput(
      String configKey,
      Optional<String> configValue,
      Optional<String> description,
      Optional<String> category) {}

  public record UpdateConfigInput(
      Long id, Optional<String> configValue, Optional<String> description) {}

  public record GetConfigInput(String key) {}
}

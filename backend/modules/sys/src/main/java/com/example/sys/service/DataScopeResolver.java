package com.example.sys.service;

import com.example.common.datapolicy.DataScope;
import com.example.common.datapolicy.DataScopeContext;
import com.example.sys.domain.SysUserRole;
import com.example.sys.repository.SysRoleDataScopeRepository;
import com.example.sys.repository.SysRoleRepository;
import com.example.sys.repository.SysUnitRepository;
import com.example.sys.repository.SysUserRoleRepository;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据权限解析器：根据当前用户的角色解析出有效的数据范围上下文。
 *
 * <p>这是 SysUser 参考实现的核心组件，其他业务模块可复用同样的模式：
 *
 * <ol>
 *   <li>查询用户的所有启用角色
 *   <li>取最宽松的范围（{@code ALL > UNIT_BELOW > UNIT > CUSTOM > SELF}）
 *   <li>按范围类型收集所需的单位 ID（CUSTOM 查 sys_role_data_scope，UNIT_BELOW 调递归 CTE）
 *   <li>返回 {@link ResolvedScope} 供 Specification 使用
 * </ol>
 *
 * ALL 范围返回 null（调用方据此跳过过滤）。
 */
@Component
@RequiredArgsConstructor
public class DataScopeResolver {

  private final SysUserRoleRepository userRoleRepository;
  private final SysRoleRepository roleRepository;
  private final SysUnitRepository unitRepository;
  private final SysRoleDataScopeRepository roleDataScopeRepository;

  /**
   * 解析用户的数据权限上下文。
   *
   * @param userId 当前用户 ID
   * @param unitId 当前用户所属单位 ID
   * @return 解析后的范围快照；ALL 范围返回 null（无需过滤）
   */
  @Transactional(readOnly = true)
  public ResolvedScope resolve(Long userId, Long unitId) {
    DataScope effective = resolveEffectiveScope(userId);
    if (effective == DataScope.ALL) {
      return null;
    }

    Set<Long> customUnitIds =
        effective == DataScope.CUSTOM ? findCustomUnitIds(userId) : Collections.emptySet();

    Set<Long> descendantUnitIds =
        effective == DataScope.UNIT_BELOW && unitId != null
            ? unitRepository.findDescendantUnitIds(unitId)
            : Collections.emptySet();

    return new ResolvedScope(effective, unitId, customUnitIds, descendantUnitIds, userId);
  }

  /**
   * 查询某用户可见的单位 ID 集合（用于批量操作的范围校验）。
   *
   * <p>ALL 返回 null（表示无限制）；其他范围返回具体 ID 集合。
   *
   * @param userId 当前用户 ID
   * @param unitId 当前用户所属单位 ID
   * @return 可见单位 ID 集合；ALL 返回 null
   */
  @Transactional(readOnly = true)
  public Set<Long> resolveVisibleUnitIds(Long userId, Long unitId) {
    ResolvedScope scope = resolve(userId, unitId);
    if (scope == null) {
      return null;
    }
    return switch (scope.scope()) {
      case SELF -> Collections.emptySet();
      case UNIT -> unitId != null ? Set.of(unitId) : Collections.emptySet();
      case UNIT_BELOW -> scope.descendantUnitIds();
      case CUSTOM -> scope.customUnitIds();
      case ALL -> null;
    };
  }

  private DataScope resolveEffectiveScope(Long userId) {
    List<SysUserRole> userRoles = userRoleRepository.findByUserId(userId);
    if (userRoles.isEmpty()) {
      return DataScope.SELF;
    }

    List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).toList();
    return roleRepository.findByIdIn(roleIds).stream()
        .filter(r -> r.getStatus() != null && r.getStatus() == 1)
        .map(r -> DataScope.fromString(r.getDataScope()))
        .reduce(DataScope.SELF, DataScopeResolver::mostPermissive);
  }

  private Set<Long> findCustomUnitIds(Long userId) {
    List<SysUserRole> userRoles = userRoleRepository.findByUserId(userId);
    if (userRoles.isEmpty()) {
      return Collections.emptySet();
    }
    List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).toList();
    Set<Long> unitIds = roleDataScopeRepository.findUnitIdsByRoleIdIn(roleIds);
    return unitIds != null ? unitIds : Collections.emptySet();
  }

  /** 取两个范围中更宽松的一个。优先级：ALL > UNIT_BELOW > UNIT > CUSTOM > SELF。 */
  static DataScope mostPermissive(DataScope a, DataScope b) {
    return scopeRank(a) <= scopeRank(b) ? a : b;
  }

  private static int scopeRank(DataScope s) {
    return switch (s) {
      case ALL -> 0;
      case UNIT_BELOW -> 1;
      case UNIT -> 2;
      case CUSTOM -> 3;
      case SELF -> 4;
    };
  }

  /**
   * 解析后的数据范围快照（不可变记录）。
   *
   * @param scope 有效范围
   * @param unitId 当前用户单位 ID
   * @param customUnitIds CUSTOM 范围的自定义单位集合
   * @param descendantUnitIds UNIT_BELOW 范围的后代单位集合（含自身）
   * @param userId 当前用户 ID
   */
  public record ResolvedScope(
      DataScope scope,
      Long unitId,
      Set<Long> customUnitIds,
      Set<Long> descendantUnitIds,
      Long userId) {

    /** 转换为 {@link DataScopeContext.ScopeData}，供 {@code DataScopeSpecification.of()} 使用。 */
    public DataScopeContext.ScopeData toScopeData() {
      return new DataScopeContext.ScopeData(scope, unitId, customUnitIds, userId);
    }
  }
}

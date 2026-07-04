package com.example.sys.repository;

import com.example.sys.domain.SysUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 单位 Repository。 */
public interface SysUnitRepository extends JpaRepository<SysUnit, Long> {

  boolean existsByUnitCode(String unitCode);

  List<SysUnit> findByParentId(Long parentId);

  List<SysUnit> findByStatus(Integer status);

  /**
   * 递归 CTE 查询某单位的所有后代单位 ID（含自身）。
   *
   * <p>使用标准 SQL {@code WITH RECURSIVE}（非 PG 专有语法），H2 2.x PostgreSQL 模式与 PostgreSQL 12+ 均支持。注意：CTE
   * 必须显式声明列名 {@code unit_tree(id, parent_id)}，否则 H2 在递归成员的 JOIN ON 中无法解析 {@code unit_tree.id}（PG
   * 不受此限制但兼容显式列名）。 用于 {@link com.example.common.datapolicy.DataScope#UNIT_BELOW} 范围解析。
   *
   * @param rootId 根单位 ID
   * @return 含 rootId 及其全部下级单位 ID 的集合
   */
  @Query(
      value =
          "WITH RECURSIVE unit_tree(id, parent_id) AS ("
              + "SELECT id, parent_id FROM sys_unit WHERE id = :rootId "
              + "UNION ALL "
              + "SELECT u.id, u.parent_id FROM sys_unit u "
              + "JOIN unit_tree ON u.parent_id = unit_tree.id"
              + ") SELECT id FROM unit_tree",
      nativeQuery = true)
  List<Long> findDescendantUnitIdsRaw(@Param("rootId") Long rootId);

  /** {@link #findDescendantUnitIdsRaw} 的 Set 视图，便于 {@code IN} 谓词直接使用。 */
  default Set<Long> findDescendantUnitIds(Long rootId) {
    return new HashSet<>(findDescendantUnitIdsRaw(rootId));
  }
}

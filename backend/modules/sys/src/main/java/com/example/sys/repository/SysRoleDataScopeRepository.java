package com.example.sys.repository;

import com.example.sys.domain.SysRoleDataScope;
import java.util.Collection;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 角色-自定义数据范围 Repository。 */
public interface SysRoleDataScopeRepository
    extends JpaRepository<SysRoleDataScope, SysRoleDataScope.PK> {

  /**
   * 查询给定角色集合配置的自定义单位 ID（{@code data_scope=CUSTOM} 时使用）。
   *
   * @param roleIds 角色 ID 集合
   * @return 去重后的单位 ID 集合；入参为空时返回空集
   */
  @Query("SELECT DISTINCT rds.unitId FROM SysRoleDataScope rds " + "WHERE rds.roleId IN :roleIds")
  Set<Long> findUnitIdsByRoleIdIn(@Param("roleIds") Collection<Long> roleIds);
}

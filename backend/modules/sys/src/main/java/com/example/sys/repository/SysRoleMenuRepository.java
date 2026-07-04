package com.example.sys.repository;

import com.example.sys.domain.SysRoleMenu;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 角色-菜单关联 Repository。 */
public interface SysRoleMenuRepository extends JpaRepository<SysRoleMenu, SysRoleMenu.PK> {

  List<SysRoleMenu> findByRoleId(Long roleId);

  @Modifying
  @Query("DELETE FROM SysRoleMenu rm WHERE rm.roleId = :roleId")
  void deleteByRoleId(@Param("roleId") Long roleId);

  @Query("SELECT rm.menuId FROM SysRoleMenu rm WHERE rm.roleId IN :roleIds")
  List<Long> findMenuIdsByRoleIds(@Param("roleIds") List<Long> roleIds);
}

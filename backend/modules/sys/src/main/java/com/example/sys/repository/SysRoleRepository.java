package com.example.sys.repository;

import com.example.sys.domain.SysRole;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 角色 Repository。 */
public interface SysRoleRepository extends JpaRepository<SysRole, Long> {

  boolean existsByRoleCode(String roleCode);

  List<SysRole> findByIdIn(List<Long> ids);

  List<SysRole> findByStatus(Integer status);
}

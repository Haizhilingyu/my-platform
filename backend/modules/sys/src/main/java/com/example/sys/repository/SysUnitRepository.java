package com.example.sys.repository;

import com.example.sys.domain.SysUnit;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 单位 Repository。 */
public interface SysUnitRepository extends JpaRepository<SysUnit, Long> {

    boolean existsByUnitCode(String unitCode);

    List<SysUnit> findByParentId(Long parentId);

    List<SysUnit> findByStatus(Integer status);
}

package com.example.sys.repository;

import com.example.sys.domain.SysMenu;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 菜单 Repository。 */
public interface SysMenuRepository extends JpaRepository<SysMenu, Long> {

    List<SysMenu> findByStatus(Integer status);

    List<SysMenu> findByIdIn(List<Long> ids);

    List<SysMenu> findByParentId(Long parentId);

    List<SysMenu> findByMenuTypeAndStatus(String menuType, Integer status);
}

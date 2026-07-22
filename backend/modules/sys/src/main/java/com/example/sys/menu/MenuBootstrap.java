package com.example.sys.menu;

import com.example.common.menu.MenuContributor;
import com.example.common.menu.MenuDefinition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 菜单启动注册器。
 *
 * <p>应用启动时收集所有 {@link MenuContributor} bean，将声明的菜单幂等注册到 {@code sys_menu} 表：
 * <ol>
 *   <li>按 path 深度排序（DIRECTORY → PAGE → BUTTON），保证父菜单先注册以解析 {@code parent_id}
 *   <li>按 {@code permission}（PAGE/BUTTON）或 {@code path}（DIRECTORY）做幂等 upsert
 *   <li>解析 {@code parentPath} → {@code parent_id}（查 {@code sys_menu.path}）
 *   <li>自动绑定 admin 角色（{@code role_code='admin'}），使超级管理员获得新菜单权限
 * </ol>
 *
 * <p>Flyway 先于此 runner 执行，因此引用核心模块种子数据中的目录（如 {@code /sys}）总能查到。
 * 与现有 Flyway 菜单 INSERT 并存幂等——同一 permission 不会重复插入。
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class MenuBootstrap implements ApplicationRunner {

  private final List<MenuContributor> contributors;
  private final JdbcTemplate jdbcTemplate;

  @Override
  public void run(ApplicationArguments args) {
    if (contributors.isEmpty()) {
      return;
    }

    List<MenuDefinition> all =
        contributors.stream().flatMap(c -> c.contributeMenus().stream()).toList();
    if (all.isEmpty()) {
      return;
    }

    List<MenuDefinition> sorted = sortByHierarchy(all);
    List<Long> registeredIds = new ArrayList<>();
    for (MenuDefinition def : sorted) {
      Long id = upsertMenu(def);
      if (id != null) {
        registeredIds.add(id);
      }
    }

    if (!registeredIds.isEmpty()) {
      int bound = bindAdminRole(registeredIds);
      log.info("菜单自动注册完成：{} 项菜单，{} 项新绑定 admin 角色", registeredIds.size(), bound);
    }
  }

  /** 排序：DIRECTORY → PAGE → BUTTON；同级按 path 深度（短在前），保证父先于子。 */
  private List<MenuDefinition> sortByHierarchy(List<MenuDefinition> menus) {
    return menus.stream()
        .sorted(
            Comparator.comparingInt((MenuDefinition m) -> typeOrder(m.menuType()))
                .thenComparingInt(m -> m.path() != null ? m.path().length() : Integer.MAX_VALUE))
        .toList();
  }

  private int typeOrder(String type) {
    return switch (type) {
      case "DIRECTORY" -> 0;
      case "PAGE" -> 1;
      case "BUTTON" -> 2;
      default -> 9;
    };
  }

  /**
   * 幂等 upsert：按 permission（PAGE/BUTTON）或 path+menuType（DIRECTORY）查重。 存在则更新可变属性，不存在则插入。
   *
   * <p>插入时显式分配 id = MAX(id)+1（子查询），不依赖 IDENTITY 序列——Flyway 种子用显式 id 绕过序列推进，
   * IDENTITY nextval 可能返回已占用的 id 导致主键冲突。
   *
   * @return 菜单 id；定义异常时返回 null
   */
  private Long upsertMenu(MenuDefinition def) {
    Long existingId = findExisting(def);
    Long parentId = resolveParentId(def.parentPath());

    if (existingId != null) {
      jdbcTemplate.update(
          "UPDATE sys_menu SET menu_name=?, path=?, component=?, icon=?, sort=?, visible=?, "
              + "parent_id=COALESCE(?, parent_id) WHERE id=?",
          def.menuName(),
          def.path(),
          def.component(),
          def.icon(),
          def.sort(),
          def.visible() ? 1 : 0,
          parentId,
          existingId);
      return existingId;
    }

    // 显式分配 id = MAX(id)+1，避免 IDENTITY 序列与 Flyway 显式 id 种子冲突。
    return jdbcTemplate.queryForObject(
        "INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, "
            + "icon, sort, visible, status) "
            + "SELECT (SELECT COALESCE(MAX(id), 0) + 1 FROM sys_menu), ?, ?, ?, ?, ?, ?, ?, ?, ?, 1 "
            + "RETURNING id",
        Long.class,
        parentId,
        def.menuName(),
        def.menuType(),
        def.path(),
        def.component(),
        def.permission(),
        def.icon(),
        def.sort(),
        def.visible() ? 1 : 0);
  }

  /** 幂等键查询：PAGE/BUTTON 按 permission；DIRECTORY 按 path + menuType。 */
  private Long findExisting(MenuDefinition def) {
    if (def.permission() != null && !def.permission().isBlank()) {
      List<Long> ids =
          jdbcTemplate.queryForList(
              "SELECT id FROM sys_menu WHERE permission = ?",
              Long.class,
              def.permission());
      return ids.isEmpty() ? null : ids.get(0);
    }
    if (def.path() != null) {
      List<Long> ids =
          jdbcTemplate.queryForList(
              "SELECT id FROM sys_menu WHERE path = ? AND menu_type = ?",
              Long.class,
              def.path(),
              def.menuType());
      return ids.isEmpty() ? null : ids.get(0);
    }
    return null;
  }

  /** 按 parentPath 查 sys_menu.path → parent_id；null/空返回 null（顶层菜单）。 */
  private Long resolveParentId(String parentPath) {
    if (parentPath == null || parentPath.isBlank()) {
      return null;
    }
    List<Long> ids =
        jdbcTemplate.queryForList(
            "SELECT id FROM sys_menu WHERE path = ?", Long.class, parentPath);
    return ids.isEmpty() ? null : ids.get(0);
  }

  /** 把菜单绑定到 admin 角色（幂等），返回新绑定的行数。 */
  private int bindAdminRole(List<Long> menuIds) {
    Long adminRoleId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM sys_role WHERE role_code = 'admin' LIMIT 1", Long.class);
    if (adminRoleId == null) {
      log.warn("admin 角色未找到，跳过菜单角色绑定");
      return 0;
    }
    int bound = 0;
    for (Long menuId : menuIds) {
      int inserted =
          jdbcTemplate.update(
              "INSERT INTO sys_role_menu (role_id, menu_id) SELECT ?, ? "
                  + "WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = ? AND menu_id = ?)",
              adminRoleId,
              menuId,
              adminRoleId,
              menuId);
      bound += inserted;
    }
    return bound;
  }
}

package com.example.common.menu;

/**
 * 菜单定义（不可变值对象）。
 *
 * <p>模块通过 {@link MenuContributor} 声明自己的菜单项。每个菜单项用 {@link #permission} 作为幂等键
 * （已存在则更新属性，不存在则插入）；用 {@link #parentPath} 关联父菜单（父菜单的 {@code path} 字段）。
 *
 * <h3>菜单类型</h3>
 * <ul>
 *   <li>{@code DIRECTORY} — 目录（如「系统管理」，有 path 如 {@code /sys}，permission 可为 null）
 *   <li>{@code PAGE} — 页面（有 path + component + permission）
 *   <li>{@code BUTTON} — 按钮权限点（无 path/component，有 permission）
 * </ul>
 *
 * @param permission 权限标识（幂等键）。PAGE/BUTTON 必填；DIRECTORY 选填
 * @param menuName 菜单显示名
 * @param menuType DIRECTORY / PAGE / BUTTON
 * @param path 前端路由路径（DIRECTORY/PAGE 有，BUTTON 为 null）
 * @param component 前端组件路径（仅 PAGE 有）
 * @param icon 图标名
 * @param sort 排序号（同级内升序）
 * @param parentPath 父菜单的 path（顶层菜单为 null；BUTTON 指向其 PAGE 的 path）
 * @param visible 是否可见（true=显示）
 */
public record MenuDefinition(
    String permission,
    String menuName,
    String menuType,
    String path,
    String component,
    String icon,
    int sort,
    String parentPath,
    boolean visible) {

  /** 目录菜单工厂。 */
  public static MenuDefinition directory(
      String menuName, String path, String icon, int sort, String parentPath) {
    return new MenuDefinition(null, menuName, "DIRECTORY", path, null, icon, sort, parentPath, true);
  }

  /** 页面菜单工厂。 */
  public static MenuDefinition page(
      String permission,
      String menuName,
      String path,
      String component,
      String icon,
      int sort,
      String parentPath) {
    return new MenuDefinition(
        permission, menuName, "PAGE", path, component, icon, sort, parentPath, true);
  }

  /** 按钮权限点工厂（无 path/component，parentPath 指向所属 PAGE 的 path）。 */
  public static MenuDefinition button(
      String permission, String menuName, int sort, String parentPath) {
    return new MenuDefinition(permission, menuName, "BUTTON", null, null, null, sort, parentPath, true);
  }
}

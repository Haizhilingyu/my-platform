package com.example.common.menu;

import java.util.List;

/**
 * 模块菜单贡献者 SPI。
 *
 * <p>每个业务模块实现此接口，声明自己提供的菜单项。应用启动时 {@code MenuBootstrap}（sys 模块）收集所有实现，
 * 幂等注册到 {@code sys_menu} 表并自动绑定到 admin 角色。
 *
 * <p>实现类需注册为 Spring bean（通常放在模块的 AutoConfiguration 里）。加 Maven 依赖即自动生效——
 * 这是模块自包含菜单注册的核心机制，替代各模块在 Flyway 迁移里硬编码 {@code INSERT sys_menu}。
 *
 * <h3>示例</h3>
 * <pre>{@code
 * @Bean
 * MenuContributor auditMenus() {
 *   return () -> List.of(
 *     MenuDefinition.page("sys:audit:list", "审计日志", "/sys/audit", "sys/audit/index", "Document", 6, "/sys")
 *   );
 * }
 * }</pre>
 */
public interface MenuContributor {

  /** 贡献的菜单定义列表。 */
  List<MenuDefinition> contributeMenus();
}

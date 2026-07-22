package com.example.audit.menu;

import com.example.common.menu.MenuContributor;
import com.example.common.menu.MenuDefinition;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 审计模块菜单注册。
 *
 * <p>声明「审计日志」页面菜单（挂在「系统管理」目录 /sys 下），替代此前 Flyway V21__audit_menu.sql 的硬编码 INSERT。加 audit-module
 * 依赖后，菜单由 {@link com.example.sys.menu.MenuBootstrap} 启动时自动注册并绑定 admin 角色，无需 DBA 手动跑 SQL。
 */
@Configuration
public class AuditMenuConfiguration {

  @Bean
  MenuContributor auditMenus() {
    return () ->
        List.of(
            MenuDefinition.page(
                "sys:audit:list", // 幂等键（与 V21 / AuditLogController @RequiresPermission 一致）
                "审计日志",
                "/sys/audit",
                "sys/audit/index",
                "Document",
                6,
                "/sys")); // 挂在「系统管理」目录下（path=/sys，由 sys V2 种子）
  }
}

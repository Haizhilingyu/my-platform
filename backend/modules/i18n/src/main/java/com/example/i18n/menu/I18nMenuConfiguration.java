package com.example.i18n.menu;

import com.example.common.menu.MenuContributor;
import com.example.common.menu.MenuDefinition;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * i18n 模块菜单注册。
 *
 * <p>声明「国际化管理」页面 + 3 个按钮权限点，替代此前 V105__i18n_permissions.sql 中的 sys_menu INSERT。 加 i18n-module
 * 依赖后自动注册并绑定 admin。V105 中的 i18n_message（菜单名翻译）数据由 V105 自身保留。
 */
@Configuration
public class I18nMenuConfiguration {

  @Bean
  MenuContributor i18nMenus() {
    return () ->
        List.of(
            MenuDefinition.page(
                "sys:i18n:list", "国际化管理", "/sys/i18n", "sys/i18n/index", "Language", 12, "/sys"),
            MenuDefinition.button("sys:i18n:edit", "编辑翻译", 1, "/sys/i18n"),
            MenuDefinition.button("sys:i18n:import", "导入翻译", 2, "/sys/i18n"),
            MenuDefinition.button("sys:i18n:export", "导出翻译", 3, "/sys/i18n"));
  }
}

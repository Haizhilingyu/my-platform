package com.example.openapp.menu;

import com.example.common.menu.MenuContributor;
import com.example.common.menu.MenuDefinition;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * openapp 模块菜单注册。
 *
 * <p>声明「外部应用」页面 + 3 个按钮权限点，替代此前 V32__openapp_menu.sql 的硬编码 INSERT。
 * 加 openapp-module 依赖后自动注册并绑定 admin。
 */
@Configuration
public class OpenAppMenuConfiguration {

  @Bean
  MenuContributor openappMenus() {
    return () ->
        List.of(
            MenuDefinition.page(
                "sys:openapp:list", "外部应用", "/sys/app", "sys/app/index", "Apps", 7, "/sys"),
            MenuDefinition.button("sys:openapp:add", "应用新增", 1, "/sys/app"),
            MenuDefinition.button("sys:openapp:edit", "应用编辑", 2, "/sys/app"),
            MenuDefinition.button("sys:openapp:delete", "应用删除", 3, "/sys/app"));
  }
}

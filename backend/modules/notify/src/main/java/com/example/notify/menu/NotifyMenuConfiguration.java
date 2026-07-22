package com.example.notify.menu;

import com.example.common.menu.MenuContributor;
import com.example.common.menu.MenuDefinition;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * notify 模块菜单注册。
 *
 * <p>notify 没有独立页面菜单（消息中心走 Layout 顶栏组件，非路由页面）。这里只注册「通知发布」按钮权限点， 替代此前 sys 模块 V101 里为 notify 补种的
 * sys:notify:publish。加 notify-module 依赖后自动注册并绑定 admin。
 */
@Configuration
public class NotifyMenuConfiguration {

  @Bean
  MenuContributor notifyMenus() {
    return () ->
        // parentPath=/sys：挂在「系统管理」目录下（无页面，仅作为权限点存在）
        List.of(MenuDefinition.button("sys:notify:publish", "通知发布", 9, "/sys"));
  }
}

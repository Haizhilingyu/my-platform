package com.example.sys.domain;

import com.example.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 菜单/权限实体。
 *
 * <p>menuType: DIRECTORY=目录, PAGE=页面, BUTTON=按钮(权限点)
 */
@Entity
@Table(name = "sys_menu")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SysMenu extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "menu_name", nullable = false, length = 64)
    private String menuName;

    /** DIRECTORY / PAGE / BUTTON */
    @Column(name = "menu_type", nullable = false, length = 20)
    private String menuType;

    /** 前端路由路径 */
    @Column(length = 200)
    private String path;

    /** 前端组件路径 */
    @Column(length = 200)
    private String component;

    /** 权限标识，如 sys:user:add */
    @Column(length = 100)
    private String permission;

    /** 图标 */
    @Column(length = 64)
    private String icon;

    @Column(nullable = false)
    @Builder.Default
    private Integer sort = 0;

    /** 0=隐藏 1=显示 */
    @Column(nullable = false)
    @Builder.Default
    private Integer visible = 1;

    /** 0=禁用 1=启用 */
    @Column(nullable = false)
    @Builder.Default
    private Integer status = 1;
}

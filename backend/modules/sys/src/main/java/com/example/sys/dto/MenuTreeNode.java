package com.example.sys.dto;

import com.example.sys.domain.SysMenu;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 菜单树节点 VO。 */
@Data
public class MenuTreeNode {

    private Long id;
    private Long parentId;
    private String menuName;
    private String menuType;
    private String path;
    private String component;
    private String permission;
    private String icon;
    private Integer sort;
    private Integer visible;
    private Integer status;
    private List<MenuTreeNode> children = new ArrayList<>();

    public static MenuTreeNode of(SysMenu menu) {
        MenuTreeNode node = new MenuTreeNode();
        node.setId(menu.getId());
        node.setParentId(menu.getParentId());
        node.setMenuName(menu.getMenuName());
        node.setMenuType(menu.getMenuType());
        node.setPath(menu.getPath());
        node.setComponent(menu.getComponent());
        node.setPermission(menu.getPermission());
        node.setIcon(menu.getIcon());
        node.setSort(menu.getSort());
        node.setVisible(menu.getVisible());
        node.setStatus(menu.getStatus());
        return node;
    }
}

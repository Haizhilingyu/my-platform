package com.example.sys.service;

import com.example.sys.domain.SysMenu;
import com.example.sys.dto.MenuTreeNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * MenuService 构树逻辑测试。
 */
@DisplayName("菜单树构建")
class MenuServiceTest {

    @Test
    @DisplayName("空列表：返回空树")
    void should_returnEmpty_when_noMenus() {
        List<MenuTreeNode> tree = MenuService.buildTree(List.of());
        assertThat(tree).isEmpty();
    }

    @Test
    @DisplayName("两级菜单：正确构建父子关系")
    void should_buildHierarchy_when_twoLevelMenus() {
        // Given
        var parent = SysMenu.builder().id(1L).parentId(null).menuName("系统管理").sort(1).build();
        var child1 = SysMenu.builder().id(2L).parentId(1L).menuName("用户管理").sort(1).build();
        var child2 = SysMenu.builder().id(3L).parentId(1L).menuName("角色管理").sort(2).build();

        // When
        List<MenuTreeNode> tree = MenuService.buildTree(List.of(parent, child1, child2));

        // Then
        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getMenuName()).isEqualTo("系统管理");
        assertThat(tree.get(0).getChildren()).hasSize(2);
        assertThat(tree.get(0).getChildren().get(0).getMenuName()).isEqualTo("用户管理");
        assertThat(tree.get(0).getChildren().get(1).getMenuName()).isEqualTo("角色管理");
    }

    @Test
    @DisplayName("多个根节点：按 sort 排序")
    void should_sortBySort_when_multipleRoots() {
        var root1 = SysMenu.builder().id(1L).parentId(null).menuName("B目录").sort(2).build();
        var root2 = SysMenu.builder().id(2L).parentId(null).menuName("A目录").sort(1).build();

        List<MenuTreeNode> tree = MenuService.buildTree(List.of(root1, root2));

        assertThat(tree).hasSize(2);
        assertThat(tree.get(0).getMenuName()).isEqualTo("A目录");
        assertThat(tree.get(1).getMenuName()).isEqualTo("B目录");
    }
}

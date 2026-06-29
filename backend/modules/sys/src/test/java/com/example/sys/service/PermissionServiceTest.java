package com.example.sys.service;

import com.example.sys.domain.SysRole;
import com.example.sys.domain.SysRoleMenu;
import com.example.sys.domain.SysMenu;
import com.example.sys.domain.SysUser;
import com.example.sys.domain.SysUserRole;
import com.example.sys.repository.SysMenuRepository;
import com.example.sys.repository.SysRoleMenuRepository;
import com.example.sys.repository.SysRoleRepository;
import com.example.sys.repository.SysUserRepository;
import com.example.sys.repository.SysUserRoleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * PermissionService 单元测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("权限服务")
class PermissionServiceTest {

    @Mock private SysUserRepository userRepository;
    @Mock private SysRoleRepository roleRepository;
    @Mock private SysMenuRepository menuRepository;
    @Mock private SysUserRoleRepository userRoleRepository;
    @Mock private SysRoleMenuRepository roleMenuRepository;

    @InjectMocks private PermissionService permissionService;

    @Test
    @DisplayName("无角色用户：返回空权限集")
    void should_returnEmpty_when_noRoles() {
        when(userRoleRepository.findByUserId(1L)).thenReturn(Collections.emptyList());

        Set<String> permissions = permissionService.getUserPermissions(1L);

        assertThat(permissions).isEmpty();
    }

    @Test
    @DisplayName("有角色有菜单：返回权限标识集合")
    void should_returnPermissions_when_userHasRolesAndMenus() {
        // Given — 用户有两个角色
        var userRole1 = new SysUserRole(1L, 10L);
        var userRole2 = new SysUserRole(1L, 20L);
        when(userRoleRepository.findByUserId(1L)).thenReturn(List.of(userRole1, userRole2));

        // 角色关联菜单
        when(roleMenuRepository.findMenuIdsByRoleIds(List.of(10L, 20L)))
                .thenReturn(List.of(100L, 200L));

        // 菜单有权限标识
        var menu1 = new SysMenu();
        menu1.setId(100L);
        menu1.setPermission("sys:user:add");
        var menu2 = new SysMenu();
        menu2.setId(200L);
        menu2.setPermission("sys:role:list");
        var menu3 = new SysMenu(); // 无权限标识
        menu3.setId(300L);
        when(menuRepository.findByIdIn(List.of(100L, 200L)))
                .thenReturn(List.of(menu1, menu2));

        // When
        Set<String> permissions = permissionService.getUserPermissions(1L);

        // Then
        assertThat(permissions).containsExactlyInAnyOrder("sys:user:add", "sys:role:list");
    }
}

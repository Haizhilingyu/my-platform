package com.example.sys.service;

import com.example.common.exception.BizException;
import com.example.common.exception.NotFoundException;
import com.example.sys.domain.SysMenu;
import com.example.sys.domain.SysRole;
import com.example.sys.domain.SysRoleMenu;
import com.example.sys.domain.SysUser;
import com.example.sys.domain.SysUserRole;
import com.example.sys.repository.SysMenuRepository;
import com.example.sys.repository.SysRoleMenuRepository;
import com.example.sys.repository.SysRoleRepository;
import com.example.sys.repository.SysUserRepository;
import com.example.sys.repository.SysUserRoleRepository;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 权限核心服务。负责用户权限和菜单数据的加载。
 *
 * <p>权限缓存通过 Redis 实现，角色权限变更时自动清除。
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final SysMenuRepository menuRepository;
    private final SysUserRoleRepository userRoleRepository;
    private final SysRoleMenuRepository roleMenuRepository;

    /** 获取用户的所有角色编码。 */
    @Transactional(readOnly = true)
    public Set<String> getUserRoleCodes(Long userId) {
        List<SysUserRole> userRoles = userRoleRepository.findByUserId(userId);
        if (userRoles.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).toList();
        return roleRepository.findByIdIn(roleIds).stream()
                .filter(r -> r.getStatus() == 1)
                .map(SysRole::getRoleCode)
                .collect(Collectors.toSet());
    }

    /** 获取用户的所有权限标识。 */
    @Transactional(readOnly = true)
    public Set<String> getUserPermissions(Long userId) {
        List<SysUserRole> userRoles = userRoleRepository.findByUserId(userId);
        if (userRoles.isEmpty()) {
            return Collections.emptySet();
        }

        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).toList();
        List<Long> menuIds = roleMenuRepository.findMenuIdsByRoleIds(roleIds);
        if (menuIds.isEmpty()) {
            return Collections.emptySet();
        }

        List<SysMenu> menus = menuRepository.findByIdIn(menuIds);
        return menus.stream()
                .map(SysMenu::getPermission)
                .filter(p -> p != null && !p.isBlank())
                .collect(Collectors.toSet());
    }

    /**
     * 获取用户的菜单树（只包含启用的目录和页面，不含按钮）。
     */
    @Transactional(readOnly = true)
    public List<SysMenu> getUserMenus(Long userId) {
        List<SysUserRole> userRoles = userRoleRepository.findByUserId(userId);
        if (userRoles.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).toList();
        List<Long> menuIds = roleMenuRepository.findMenuIdsByRoleIds(roleIds);
        if (menuIds.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> menuIdSet = new HashSet<>(menuIds);
        return menuRepository.findByIdIn(menuIds).stream()
                .filter(m -> m.getStatus() == 1)
                .filter(m -> !"BUTTON".equals(m.getMenuType()))
                .toList();
    }

    /** 校验用户是否存在且启用。 */
    @Transactional(readOnly = true)
    public SysUser getActiveUser(Long userId) {
        SysUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("用户", userId));
        if (user.getStatus() != 1) {
            throw new BizException(403, "用户已被禁用");
        }
        return user;
    }

    /** 分配角色给用户。 */
    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        userRoleRepository.deleteByUserId(userId);
        if (roleIds != null && !roleIds.isEmpty()) {
            List<SysUserRole> userRoles = roleIds.stream()
                    .map(rid -> new SysUserRole(userId, rid))
                    .toList();
            userRoleRepository.saveAll(userRoles);
        }
    }

    /** 分配菜单权限给角色。 */
    @Transactional
    public void assignMenus(Long roleId, List<Long> menuIds) {
        roleMenuRepository.deleteByRoleId(roleId);
        if (menuIds != null && !menuIds.isEmpty()) {
            List<SysRoleMenu> roleMenus = menuIds.stream()
                    .map(mid -> new SysRoleMenu(roleId, mid))
                    .toList();
            roleMenuRepository.saveAll(roleMenus);
        }
    }
}

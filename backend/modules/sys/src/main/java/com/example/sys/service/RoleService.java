package com.example.sys.service;

import com.example.common.exception.BizException;
import com.example.common.exception.NotFoundException;
import com.example.common.i18n.Messages;
import com.example.sys.domain.SysRole;
import com.example.sys.domain.SysRoleDataScope;
import com.example.sys.dto.RoleDTO;
import com.example.sys.events.RolePermissionChanged;
import com.example.sys.repository.SysRoleDataScopeRepository;
import com.example.sys.repository.SysRoleMenuRepository;
import com.example.sys.repository.SysRoleRepository;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 角色服务。 */
@Service
@RequiredArgsConstructor
public class RoleService {

  private final SysRoleRepository roleRepository;
  private final SysRoleMenuRepository roleMenuRepository;
  private final SysRoleDataScopeRepository roleDataScopeRepository;
  private final PermissionService permissionService;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional(readOnly = true)
  public List<SysRole> findAll() {
    return roleRepository.findAll();
  }

  @Transactional(readOnly = true)
  public SysRole getById(Long id) {
    return roleRepository
        .findById(id)
        .orElseThrow(
            () ->
                NotFoundException.i18n(
                    "error.resource.not.found", Messages.get("resource.role"), id));
  }

  @Transactional
  public Long create(RoleDTO dto) {
    if (roleRepository.existsByRoleCode(dto.getRoleCode())) {
      throw BizException.i18n("role.code.exists", dto.getRoleCode());
    }
    SysRole role =
        SysRole.builder()
            .roleCode(dto.getRoleCode())
            .roleName(dto.getRoleName())
            .dataScope(dto.getDataScope() != null ? dto.getDataScope() : "SELF")
            .status(dto.getStatus() != null ? dto.getStatus() : 1)
            .remark(dto.getRemark())
            .build();
    return roleRepository.save(role).getId();
  }

  @Transactional
  public void update(Long id, RoleDTO dto) {
    SysRole role = getById(id);
    if (dto.getRoleName() != null) {
      role.setRoleName(dto.getRoleName());
    }
    if (dto.getDataScope() != null) {
      role.setDataScope(dto.getDataScope());
    }
    if (dto.getStatus() != null) {
      role.setStatus(dto.getStatus());
    }
    if (dto.getRemark() != null) {
      role.setRemark(dto.getRemark());
    }
    roleRepository.save(role);
  }

  @Transactional
  public void delete(Long id) {
    SysRole role = getById(id);
    roleMenuRepository.deleteByRoleId(id);
    roleRepository.delete(role);
  }

  @Transactional
  public void assignMenus(Long roleId, List<Long> menuIds) {
    getById(roleId);
    permissionService.assignMenus(roleId, menuIds);
    SysRole role = getById(roleId);
    eventPublisher.publishEvent(new RolePermissionChanged(roleId, role.getRoleCode()));
  }

  @Transactional(readOnly = true)
  public List<Long> getRoleMenuIds(Long roleId) {
    return roleMenuRepository.findByRoleId(roleId).stream().map(rm -> rm.getMenuId()).toList();
  }

  @Transactional
  public void saveCustomUnits(Long roleId, List<Long> unitIds) {
    getById(roleId);
    roleDataScopeRepository.deleteByRoleId(roleId);
    if (unitIds != null && !unitIds.isEmpty()) {
      List<SysRoleDataScope> entries =
          unitIds.stream().distinct().map(unitId -> new SysRoleDataScope(roleId, unitId)).toList();
      roleDataScopeRepository.saveAll(entries);
    }
  }

  @Transactional(readOnly = true)
  public List<Long> getCustomUnitIds(Long roleId) {
    return roleDataScopeRepository.findUnitIdsByRoleIdIn(Set.of(roleId)).stream().sorted().toList();
  }
}

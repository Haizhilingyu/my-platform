package com.example.sys.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.exception.BizException;
import com.example.common.exception.NotFoundException;
import com.example.sys.domain.SysRole;
import com.example.sys.domain.SysRoleDataScope;
import com.example.sys.domain.SysRoleMenu;
import com.example.sys.dto.RoleDTO;
import com.example.sys.events.RolePermissionChanged;
import com.example.sys.repository.SysRoleDataScopeRepository;
import com.example.sys.repository.SysRoleMenuRepository;
import com.example.sys.repository.SysRoleRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** 角色服务单元测试（Mockito，不启动 Spring）。 */
@ExtendWith(MockitoExtension.class)
@DisplayName("角色服务")
class RoleServiceTest {

  @Mock private SysRoleRepository roleRepository;
  @Mock private SysRoleMenuRepository roleMenuRepository;
  @Mock private SysRoleDataScopeRepository roleDataScopeRepository;
  @Mock private PermissionService permissionService;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private RoleService roleService;

  @Nested
  @DisplayName("创建角色")
  class Create {

    @Test
    @DisplayName("正常创建：返回角色ID，默认值数据范围=SELF、状态=1")
    void should_returnId_and_applyDefaults_when_create() {
      // Given
      RoleDTO dto = new RoleDTO();
      dto.setRoleCode("editor");
      dto.setRoleName("编辑");
      dto.setRemark("备注");
      when(roleRepository.existsByRoleCode("editor")).thenReturn(false);
      var saved = SysRole.builder().id(7L).roleCode("editor").build();
      when(roleRepository.save(any(SysRole.class))).thenReturn(saved);

      // When
      Long id = roleService.create(dto);

      // Then
      assertThat(id).isEqualTo(7L);
      ArgumentCaptor<SysRole> cap = ArgumentCaptor.forClass(SysRole.class);
      verify(roleRepository).save(cap.capture());
      assertThat(cap.getValue().getRoleCode()).isEqualTo("editor");
      assertThat(cap.getValue().getDataScope()).isEqualTo("SELF");
      assertThat(cap.getValue().getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("角色编码重复：抛出 BizException")
    void should_throwBizException_when_roleCodeExists() {
      // Given
      RoleDTO dto = new RoleDTO();
      dto.setRoleCode("admin");
      when(roleRepository.existsByRoleCode("admin")).thenReturn(true);

      // When & Then
      assertThatThrownBy(() -> roleService.create(dto))
          .isInstanceOf(BizException.class)
          .hasMessageContaining("角色编码已存在");
      verify(roleRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("更新角色")
  class Update {

    @Test
    @DisplayName("部分更新：仅覆盖非 null 字段，保留其余")
    void should_updateOnlyNonNull_when_update() {
      // Given
      var existing =
          SysRole.builder().id(7L).roleName("旧").dataScope("SELF").status(1).remark("r").build();
      when(roleRepository.findById(7L)).thenReturn(Optional.of(existing));
      RoleDTO dto = new RoleDTO();
      dto.setRoleName("新名称");
      dto.setStatus(0);

      // When
      roleService.update(7L, dto);

      // Then
      ArgumentCaptor<SysRole> cap = ArgumentCaptor.forClass(SysRole.class);
      verify(roleRepository).save(cap.capture());
      assertThat(cap.getValue().getRoleName()).isEqualTo("新名称");
      assertThat(cap.getValue().getStatus()).isEqualTo(0);
      assertThat(cap.getValue().getDataScope()).isEqualTo("SELF");
      assertThat(cap.getValue().getRemark()).isEqualTo("r");
    }

    @Test
    @DisplayName("ID 不存在：抛出 NotFoundException")
    void should_throwNotFound_when_idMissing() {
      when(roleRepository.findById(99L)).thenReturn(Optional.empty());
      assertThatThrownBy(() -> roleService.update(99L, new RoleDTO()))
          .isInstanceOf(NotFoundException.class);
    }
  }

  @Nested
  @DisplayName("删除角色")
  class Delete {

    @Test
    @DisplayName("正常删除：先删菜单关联再删角色")
    void should_clearMenuAssoc_and_deleteRole() {
      // Given
      var role = SysRole.builder().id(7L).build();
      when(roleRepository.findById(7L)).thenReturn(Optional.of(role));

      // When
      roleService.delete(7L);

      // Then
      verify(roleMenuRepository).deleteByRoleId(7L);
      verify(roleRepository).delete(role);
    }

    @Test
    @DisplayName("ID 不存在：抛出 NotFoundException")
    void should_throwNotFound_when_idMissing() {
      when(roleRepository.findById(99L)).thenReturn(Optional.empty());
      assertThatThrownBy(() -> roleService.delete(99L)).isInstanceOf(NotFoundException.class);
    }
  }

  @Nested
  @DisplayName("分配菜单")
  class AssignMenus {

    @Test
    @DisplayName("正常分配：调用 permissionService 并发布 RolePermissionChanged 事件")
    void should_assignAndPublishEvent_when_assignMenus() {
      // Given
      var role = SysRole.builder().id(7L).roleCode("editor").build();
      when(roleRepository.findById(7L)).thenReturn(Optional.of(role));

      // When
      roleService.assignMenus(7L, List.of(1L, 2L));

      // Then
      verify(permissionService).assignMenus(7L, List.of(1L, 2L));
      ArgumentCaptor<RolePermissionChanged> eventCaptor =
          ArgumentCaptor.forClass(RolePermissionChanged.class);
      verify(eventPublisher).publishEvent(eventCaptor.capture());
      RolePermissionChanged event = eventCaptor.getValue();
      assertThat(event.roleId()).isEqualTo(7L);
      assertThat(event.roleCode()).isEqualTo("editor");
    }

    @Test
    @DisplayName("角色不存在：抛出 NotFoundException")
    void should_throwNotFound_when_roleMissing() {
      when(roleRepository.findById(99L)).thenReturn(Optional.empty());
      assertThatThrownBy(() -> roleService.assignMenus(99L, List.of(1L)))
          .isInstanceOf(NotFoundException.class);
      verify(permissionService, never()).assignMenus(any(), any());
    }
  }

  @Nested
  @DisplayName("查询角色菜单ID")
  class GetRoleMenuIds {

    @Test
    @DisplayName("返回关联菜单 ID 列表")
    void should_returnMenuIds_when_exist() {
      when(roleMenuRepository.findByRoleId(7L))
          .thenReturn(List.of(new SysRoleMenu(7L, 11L), new SysRoleMenu(7L, 22L)));
      assertThat(roleService.getRoleMenuIds(7L)).containsExactly(11L, 22L);
    }

    @Test
    @DisplayName("无关联：返回空列表")
    void should_returnEmpty_when_none() {
      when(roleMenuRepository.findByRoleId(7L)).thenReturn(List.of());
      assertThat(roleService.getRoleMenuIds(7L)).isEmpty();
    }
  }

  @Test
  @DisplayName("getById 不存在时抛 NotFoundException")
  void should_throwNotFound_when_getByIdMissing() {
    when(roleRepository.findById(1L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> roleService.getById(1L)).isInstanceOf(NotFoundException.class);
  }

  @Nested
  @DisplayName("保存自定义数据范围")
  class SaveCustomUnits {

    @Test
    @DisplayName("正常保存：先删旧关联再写入新关联")
    void should_deleteAndReinsert_when_save() {
      var role = SysRole.builder().id(7L).build();
      when(roleRepository.findById(7L)).thenReturn(Optional.of(role));

      roleService.saveCustomUnits(7L, List.of(1L, 2L));

      verify(roleDataScopeRepository).deleteByRoleId(7L);
      org.mockito.ArgumentCaptor<List<SysRoleDataScope>> cap =
          org.mockito.ArgumentCaptor.forClass(List.class);
      verify(roleDataScopeRepository).saveAll(cap.capture());
      assertThat(cap.getValue()).hasSize(2);
      assertThat(cap.getValue().get(0).getRoleId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("空列表：仅删除旧关联，不写入新数据")
    void should_onlyDelete_when_emptyList() {
      var role = SysRole.builder().id(7L).build();
      when(roleRepository.findById(7L)).thenReturn(Optional.of(role));

      roleService.saveCustomUnits(7L, List.of());

      verify(roleDataScopeRepository).deleteByRoleId(7L);
      verify(roleDataScopeRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("角色不存在：抛出 NotFoundException")
    void should_throwNotFound_when_roleMissing() {
      when(roleRepository.findById(99L)).thenReturn(Optional.empty());
      assertThatThrownBy(() -> roleService.saveCustomUnits(99L, List.of(1L)))
          .isInstanceOf(NotFoundException.class);
    }
  }

  @Nested
  @DisplayName("查询自定义单位 ID")
  class GetCustomUnitIds {

    @Test
    @DisplayName("返回已配置的单位 ID（升序）")
    void should_returnSortedIds_when_exist() {
      when(roleDataScopeRepository.findUnitIdsByRoleIdIn(Set.of(7L)))
          .thenReturn(Set.of(3L, 1L, 2L));
      assertThat(roleService.getCustomUnitIds(7L)).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("无配置：返回空列表")
    void should_returnEmpty_when_none() {
      when(roleDataScopeRepository.findUnitIdsByRoleIdIn(Set.of(7L))).thenReturn(Set.of());
      assertThat(roleService.getCustomUnitIds(7L)).isEmpty();
    }
  }
}

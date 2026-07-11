package com.example.sys.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.result.Result;
import com.example.sys.domain.SysRole;
import com.example.sys.dto.RoleDTO;
import com.example.sys.service.RoleService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RoleController API 集成测试：验证请求 → 服务调用 → Result 响应的完整链路。 */
@DisplayName("RoleController 请求-响应链路")
class RoleControllerTest {

  private final RoleService roleService = mock(RoleService.class);
  private final RoleController controller = new RoleController(roleService);

  @Test
  @DisplayName("list：返回角色列表")
  void list_returnsRoles() {
    when(roleService.findAll()).thenReturn(List.of(SysRole.builder().id(1L).roleCode("r").build()));
    Result<List<SysRole>> result = controller.list();
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.data()).hasSize(1);
  }

  @Test
  @DisplayName("get：按 ID 返回角色")
  void get_returnsRole() {
    when(roleService.getById(1L)).thenReturn(SysRole.builder().id(1L).build());
    Result<SysRole> result = controller.get(1L);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.data().getId()).isEqualTo(1L);
  }

  @Test
  @DisplayName("create：返回新角色 ID，并透传 DTO")
  void create_returnsId() {
    when(roleService.create(any(RoleDTO.class))).thenReturn(7L);
    Result<Long> result = controller.create(new RoleDTO());
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.data()).isEqualTo(7L);
    verify(roleService).create(any(RoleDTO.class));
  }

  @Test
  @DisplayName("update：调用服务更新并透传 ID 与 DTO")
  void update_invokesService() {
    Result<Void> result = controller.update(1L, new RoleDTO());
    assertThat(result.isSuccess()).isTrue();
    verify(roleService).update(eq(1L), any(RoleDTO.class));
  }

  @Test
  @DisplayName("delete：调用服务删除并透传 ID")
  void delete_invokesService() {
    Result<Void> result = controller.delete(1L);
    assertThat(result.isSuccess()).isTrue();
    verify(roleService).delete(1L);
  }

  @Test
  @DisplayName("assignMenus：调用服务分配并透传菜单 ID 列表")
  void assignMenus_invokesService() {
    Result<Void> result = controller.assignMenus(1L, List.of(10L, 20L));
    assertThat(result.isSuccess()).isTrue();
    verify(roleService).assignMenus(1L, List.of(10L, 20L));
  }

  @Test
  @DisplayName("getRoleMenus：返回菜单 ID 列表")
  void getRoleMenus_returnsIds() {
    when(roleService.getRoleMenuIds(1L)).thenReturn(List.of(10L, 20L));
    Result<List<Long>> result = controller.getRoleMenus(1L);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.data()).containsExactly(10L, 20L);
  }

  @Test
  @DisplayName("saveDataScope：调用服务保存并透传 ID 与单位 ID 列表")
  void saveDataScope_invokesService() {
    Result<Void> result = controller.saveDataScope(1L, List.of(4L, 5L));
    assertThat(result.isSuccess()).isTrue();
    verify(roleService).saveCustomUnits(1L, List.of(4L, 5L));
  }

  @Test
  @DisplayName("getDataScope：返回自定义单位 ID 列表")
  void getDataScope_returnsIds() {
    when(roleService.getCustomUnitIds(1L)).thenReturn(List.of(4L, 5L));
    Result<List<Long>> result = controller.getDataScope(1L);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.data()).containsExactly(4L, 5L);
  }
}

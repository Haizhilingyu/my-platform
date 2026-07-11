package com.example.sys.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.result.Result;
import com.example.sys.domain.SysMenu;
import com.example.sys.dto.MenuDTO;
import com.example.sys.dto.MenuTreeNode;
import com.example.sys.service.MenuService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** MenuController API 集成测试：验证请求 → 服务调用 → Result 响应的完整链路。 */
@DisplayName("MenuController 请求-响应链路")
class MenuControllerTest {

  private final MenuService menuService = mock(MenuService.class);
  private final MenuController controller = new MenuController(menuService);

  @Test
  @DisplayName("tree：返回菜单树")
  void tree_returnsNodes() {
    when(menuService.getTree()).thenReturn(List.of(new MenuTreeNode()));
    Result<List<MenuTreeNode>> result = controller.tree();
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.data()).hasSize(1);
  }

  @Test
  @DisplayName("get：按 ID 返回菜单")
  void get_returnsMenu() {
    when(menuService.getById(1L)).thenReturn(SysMenu.builder().id(1L).build());
    Result<SysMenu> result = controller.get(1L);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.data().getId()).isEqualTo(1L);
  }

  @Test
  @DisplayName("create：返回新菜单 ID，并透传 DTO")
  void create_returnsId() {
    when(menuService.create(any(MenuDTO.class))).thenReturn(9L);
    Result<Long> result = controller.create(new MenuDTO());
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.data()).isEqualTo(9L);
  }

  @Test
  @DisplayName("update：调用服务更新并透传 ID 与 DTO")
  void update_invokesService() {
    Result<Void> result = controller.update(1L, new MenuDTO());
    assertThat(result.isSuccess()).isTrue();
    verify(menuService).update(eq(1L), any(MenuDTO.class));
  }

  @Test
  @DisplayName("delete：调用服务删除并透传 ID")
  void delete_invokesService() {
    Result<Void> result = controller.delete(1L);
    assertThat(result.isSuccess()).isTrue();
    verify(menuService).delete(1L);
  }
}

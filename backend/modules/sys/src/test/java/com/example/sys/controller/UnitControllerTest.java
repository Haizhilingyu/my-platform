package com.example.sys.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.result.Result;
import com.example.sys.domain.SysUnit;
import com.example.sys.dto.UnitDTO;
import com.example.sys.dto.UnitTreeNode;
import com.example.sys.service.UnitService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** UnitController API 集成测试：验证请求 → 服务调用 → Result 响应的完整链路。 */
@DisplayName("UnitController 请求-响应链路")
class UnitControllerTest {

  private final UnitService unitService = mock(UnitService.class);
  private final UnitController controller = new UnitController(unitService);

  @Test
  @DisplayName("tree：返回单位树")
  void tree_returnsNodes() {
    when(unitService.getTree()).thenReturn(List.of(new UnitTreeNode()));
    Result<List<UnitTreeNode>> result = controller.tree();
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.data()).hasSize(1);
  }

  @Test
  @DisplayName("get：按 ID 返回单位")
  void get_returnsUnit() {
    when(unitService.getById(1L)).thenReturn(SysUnit.builder().id(1L).build());
    Result<SysUnit> result = controller.get(1L);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.data().getId()).isEqualTo(1L);
  }

  @Test
  @DisplayName("create：返回新单位 ID，并透传 DTO")
  void create_returnsId() {
    when(unitService.create(any(UnitDTO.class))).thenReturn(3L);
    Result<Long> result = controller.create(new UnitDTO());
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.data()).isEqualTo(3L);
  }

  @Test
  @DisplayName("update：调用服务更新并透传 ID 与 DTO")
  void update_invokesService() {
    Result<Void> result = controller.update(1L, new UnitDTO());
    assertThat(result.isSuccess()).isTrue();
    verify(unitService).update(eq(1L), any(UnitDTO.class));
  }

  @Test
  @DisplayName("delete：调用服务删除并透传 ID")
  void delete_invokesService() {
    Result<Void> result = controller.delete(1L);
    assertThat(result.isSuccess()).isTrue();
    verify(unitService).delete(1L);
  }
}

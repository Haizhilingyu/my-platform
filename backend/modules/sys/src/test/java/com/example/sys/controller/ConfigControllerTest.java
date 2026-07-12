package com.example.sys.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.result.Result;
import com.example.sys.domain.SysConfig;
import com.example.sys.dto.ConfigDTO;
import com.example.sys.service.ConfigService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ConfigController API 集成测试：验证请求 → 服务调用 → Result 响应的完整链路。 */
@DisplayName("ConfigController 请求-响应链路")
class ConfigControllerTest {

  private final ConfigService configService = mock(ConfigService.class);
  private final ConfigController controller = new ConfigController(configService);

  @Test
  @DisplayName("list 无 category：返回全部配置")
  void list_all() {
    when(configService.findAll()).thenReturn(List.of(SysConfig.builder().id(1L).build()));
    Result<List<SysConfig>> result = controller.list(null);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.data()).hasSize(1);
  }

  @Test
  @DisplayName("list 带 category：按分类查询")
  void list_byCategory() {
    when(configService.findByCategory("sys"))
        .thenReturn(List.of(SysConfig.builder().id(2L).build()));
    Result<List<SysConfig>> result = controller.list("sys");
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.data()).hasSize(1);
    verify(configService).findByCategory("sys");
  }

  @Test
  @DisplayName("getByKey：按 key 返回配置")
  void getByKey_returnsConfig() {
    when(configService.getByKey("app.name")).thenReturn(SysConfig.builder().id(1L).build());
    Result<SysConfig> result = controller.getByKey("app.name");
    assertThat(result.isSuccess()).isTrue();
    verify(configService).getByKey("app.name");
  }

  @Test
  @DisplayName("create：返回新配置 ID，并透传 DTO")
  void create_returnsId() {
    when(configService.create(any(ConfigDTO.class))).thenReturn(9L);
    Result<Long> result = controller.create(new ConfigDTO());
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.data()).isEqualTo(9L);
  }

  @Test
  @DisplayName("update：调用服务更新并透传 ID 与 DTO")
  void update_invokesService() {
    Result<Void> result = controller.update(1L, new ConfigDTO());
    assertThat(result.isSuccess()).isTrue();
    verify(configService).update(eq(1L), any(ConfigDTO.class));
  }

  @Test
  @DisplayName("batchUpdate：调用服务批量更新")
  void batchUpdate_invokesService() {
    Result<Void> result = controller.batchUpdate(List.of(new ConfigDTO()));
    assertThat(result.isSuccess()).isTrue();
    verify(configService).batchUpdate(anyList());
  }

  @Test
  @DisplayName("getAsMap：按分类返回键值映射")
  void getAsMap_returnsMap() {
    when(configService.getAsMap("sys")).thenReturn(Map.of("a", "1"));
    Result<Map<String, String>> result = controller.getAsMap("sys");
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.data()).containsEntry("a", "1");
  }
}

package com.example.sys.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.exception.BizException;
import com.example.common.exception.NotFoundException;
import com.example.sys.domain.SysConfig;
import com.example.sys.dto.ConfigDTO;
import com.example.sys.repository.SysConfigRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 系统配置服务单元测试（Mockito，不启动 Spring）。 */
@ExtendWith(MockitoExtension.class)
@DisplayName("系统配置服务")
class ConfigServiceTest {

  @Mock private SysConfigRepository configRepository;
  @InjectMocks private ConfigService configService;

  @Nested
  @DisplayName("查询")
  class Query {

    @Test
    @DisplayName("getByKey 不存在：抛 NotFoundException")
    void should_throwNotFound_when_keyMissing() {
      when(configRepository.findByConfigKey("k")).thenReturn(Optional.empty());
      assertThatThrownBy(() -> configService.getByKey("k")).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("getValue 缺失：返回默认值")
    void should_returnDefault_when_missing() {
      when(configRepository.findByConfigKey("k")).thenReturn(Optional.empty());
      assertThat(configService.getValue("k", "def")).isEqualTo("def");
    }

    @Test
    @DisplayName("getValue 存在：返回配置值")
    void should_returnValue_when_present() {
      when(configRepository.findByConfigKey("k"))
          .thenReturn(Optional.of(SysConfig.builder().configValue("v").build()));
      assertThat(configService.getValue("k", "def")).isEqualTo("v");
    }

    @Test
    @DisplayName("getAsMap 按分类聚合为键值对")
    void should_returnMap_when_byCategory() {
      when(configRepository.findByCategory("cat"))
          .thenReturn(
              List.of(
                  SysConfig.builder().configKey("a").configValue("1").build(),
                  SysConfig.builder().configKey("b").configValue("2").build()));
      Map<String, String> map = configService.getAsMap("cat");
      assertThat(map).containsEntry("a", "1").containsEntry("b", "2");
    }
  }

  @Nested
  @DisplayName("创建配置")
  class Create {

    @Test
    @DisplayName("配置键重复：抛 BizException")
    void should_throwBizException_when_keyExists() {
      ConfigDTO dto = new ConfigDTO();
      dto.setConfigKey("app.name");
      when(configRepository.existsByConfigKey("app.name")).thenReturn(true);
      assertThatThrownBy(() -> configService.create(dto))
          .isInstanceOf(BizException.class)
          .hasMessageContaining("配置键已存在");
    }

    @Test
    @DisplayName("正常创建：默认类型=STRING、分类=default")
    void should_returnId_and_defaults_when_create() {
      ConfigDTO dto = new ConfigDTO();
      dto.setConfigKey("app.name");
      dto.setConfigValue("Hello");
      var saved = SysConfig.builder().id(9L).build();
      when(configRepository.existsByConfigKey("app.name")).thenReturn(false);
      when(configRepository.save(any(SysConfig.class))).thenReturn(saved);

      Long id = configService.create(dto);

      assertThat(id).isEqualTo(9L);
      ArgumentCaptor<SysConfig> cap = ArgumentCaptor.forClass(SysConfig.class);
      verify(configRepository).save(cap.capture());
      assertThat(cap.getValue().getConfigType()).isEqualTo("STRING");
      assertThat(cap.getValue().getCategory()).isEqualTo("default");
    }
  }

  @Nested
  @DisplayName("更新配置")
  class Update {

    @Test
    @DisplayName("部分更新：仅覆盖非 null 字段")
    void should_updateNonNull_when_update() {
      var existing =
          SysConfig.builder()
              .id(9L)
              .configValue("old")
              .configType("STRING")
              .category("default")
              .build();
      when(configRepository.findById(9L)).thenReturn(Optional.of(existing));
      ConfigDTO dto = new ConfigDTO();
      dto.setConfigValue("new");
      dto.setCategory("sys");

      configService.update(9L, dto);

      ArgumentCaptor<SysConfig> cap = ArgumentCaptor.forClass(SysConfig.class);
      verify(configRepository).save(cap.capture());
      assertThat(cap.getValue().getConfigValue()).isEqualTo("new");
      assertThat(cap.getValue().getCategory()).isEqualTo("sys");
      assertThat(cap.getValue().getConfigType()).isEqualTo("STRING");
    }

    @Test
    @DisplayName("ID 不存在：抛 NotFoundException")
    void should_throwNotFound_when_idMissing() {
      when(configRepository.findById(99L)).thenReturn(Optional.empty());
      assertThatThrownBy(() -> configService.update(99L, new ConfigDTO()))
          .isInstanceOf(NotFoundException.class);
    }
  }

  @Nested
  @DisplayName("批量更新")
  class BatchUpdate {

    @Test
    @DisplayName("仅更新带 ID 的项，无 ID 的忽略")
    void should_updateOnlyWithId_when_batchUpdate() {
      var existing = SysConfig.builder().id(9L).configValue("v").build();
      when(configRepository.findById(9L)).thenReturn(Optional.of(existing));

      ConfigDTO withId = new ConfigDTO();
      withId.setId(9L);
      withId.setConfigValue("x");
      ConfigDTO withoutId = new ConfigDTO();
      withoutId.setConfigValue("y");

      configService.batchUpdate(List.of(withId, withoutId));

      verify(configRepository, times(1)).save(any());
      verify(configRepository).save(org.mockito.ArgumentMatchers.argThat(c -> "x".equals(c.getConfigValue())));
    }
  }
}

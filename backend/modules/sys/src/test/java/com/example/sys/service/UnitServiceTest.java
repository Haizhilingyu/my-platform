package com.example.sys.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.exception.BizException;
import com.example.common.exception.NotFoundException;
import com.example.sys.domain.SysUnit;
import com.example.sys.dto.UnitDTO;
import com.example.sys.dto.UnitTreeNode;
import com.example.sys.repository.SysUnitRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 单位服务单元测试（Mockito，不启动 Spring）。 */
@ExtendWith(MockitoExtension.class)
@DisplayName("单位服务")
class UnitServiceTest {

  @Mock private SysUnitRepository unitRepository;
  @InjectMocks private UnitService unitService;

  @Nested
  @DisplayName("创建单位")
  class Create {

    @Test
    @DisplayName("正常创建：返回 ID，默认排序=0、状态=1")
    void should_returnId_and_defaults_when_create() {
      // Given
      UnitDTO dto = new UnitDTO();
      dto.setUnitCode("dept_a");
      dto.setUnitName("部门A");
      var saved = SysUnit.builder().id(3L).unitCode("dept_a").build();
      when(unitRepository.existsByUnitCode("dept_a")).thenReturn(false);
      when(unitRepository.save(any(SysUnit.class))).thenReturn(saved);

      // When
      Long id = unitService.create(dto);

      // Then
      assertThat(id).isEqualTo(3L);
      ArgumentCaptor<SysUnit> cap = ArgumentCaptor.forClass(SysUnit.class);
      verify(unitRepository).save(cap.capture());
      assertThat(cap.getValue().getSort()).isEqualTo(0);
      assertThat(cap.getValue().getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("单位编码重复：抛出 BizException")
    void should_throwBizException_when_codeExists() {
      UnitDTO dto = new UnitDTO();
      dto.setUnitCode("dept_a");
      when(unitRepository.existsByUnitCode("dept_a")).thenReturn(true);
      assertThatThrownBy(() -> unitService.create(dto))
          .isInstanceOf(BizException.class)
          .hasMessageContaining("单位编码已存在");
    }
  }

  @Nested
  @DisplayName("更新单位")
  class Update {

    @Test
    @DisplayName("上级指向自己：抛出 BizException '上级单位不能是自己'")
    void should_throwBizException_when_parentIsSelf() {
      var existing = SysUnit.builder().id(5L).build();
      when(unitRepository.findById(5L)).thenReturn(Optional.of(existing));
      UnitDTO dto = new UnitDTO();
      dto.setParentId(5L);
      assertThatThrownBy(() -> unitService.update(5L, dto))
          .isInstanceOf(BizException.class)
          .hasMessageContaining("上级单位不能是自己");
    }

    @Test
    @DisplayName("正常更新：仅覆盖非 null 字段")
    void should_updateNonNull_when_update() {
      var existing = SysUnit.builder().id(5L).unitName("旧").sort(0).status(1).build();
      when(unitRepository.findById(5L)).thenReturn(Optional.of(existing));
      UnitDTO dto = new UnitDTO();
      dto.setUnitName("新名");
      dto.setSort(9);

      unitService.update(5L, dto);

      ArgumentCaptor<SysUnit> cap = ArgumentCaptor.forClass(SysUnit.class);
      verify(unitRepository).save(cap.capture());
      assertThat(cap.getValue().getUnitName()).isEqualTo("新名");
      assertThat(cap.getValue().getSort()).isEqualTo(9);
    }

    @Test
    @DisplayName("ID 不存在：抛出 NotFoundException")
    void should_throwNotFound_when_idMissing() {
      when(unitRepository.findById(99L)).thenReturn(Optional.empty());
      assertThatThrownBy(() -> unitService.update(99L, new UnitDTO()))
          .isInstanceOf(NotFoundException.class);
    }
  }

  @Nested
  @DisplayName("删除单位")
  class Delete {

    @Test
    @DisplayName("存在子单位：抛出 BizException，不删除")
    void should_throwBizException_when_hasChildren() {
      var existing = SysUnit.builder().id(5L).build();
      when(unitRepository.findById(5L)).thenReturn(Optional.of(existing));
      when(unitRepository.findByParentId(5L)).thenReturn(List.of(SysUnit.builder().id(6L).build()));

      assertThatThrownBy(() -> unitService.delete(5L))
          .isInstanceOf(BizException.class)
          .hasMessageContaining("存在子单位，无法删除");
      verify(unitRepository, never()).delete(any());
    }

    @Test
    @DisplayName("无子单位：正常删除")
    void should_delete_when_noChildren() {
      var existing = SysUnit.builder().id(5L).build();
      when(unitRepository.findById(5L)).thenReturn(Optional.of(existing));
      when(unitRepository.findByParentId(5L)).thenReturn(List.of());

      unitService.delete(5L);

      verify(unitRepository).delete(existing);
    }
  }

  @Nested
  @DisplayName("单位树构建（静态方法）")
  class BuildTree {

    @Test
    @DisplayName("按 parentId 组装层级，根与子节点均按 sort 升序")
    void should_buildHierarchy_sortedBySort() {
      var root1 = SysUnit.builder().id(1L).parentId(null).sort(2).build();
      var root2 = SysUnit.builder().id(2L).parentId(null).sort(1).build();
      var child = SysUnit.builder().id(3L).parentId(1L).sort(0).build();

      List<UnitTreeNode> tree = UnitService.buildTree(List.of(root1, root2, child));

      assertThat(tree).hasSize(2);
      assertThat(tree.get(0).getId()).isEqualTo(2L);
      assertThat(tree.get(1).getId()).isEqualTo(1L);
      assertThat(tree.get(1).getChildren()).hasSize(1);
      assertThat(tree.get(1).getChildren().get(0).getId()).isEqualTo(3L);
    }
  }

  @Test
  @DisplayName("getById 不存在时抛 NotFoundException")
  void should_throwNotFound_when_getByIdMissing() {
    when(unitRepository.findById(1L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> unitService.getById(1L)).isInstanceOf(NotFoundException.class);
  }
}

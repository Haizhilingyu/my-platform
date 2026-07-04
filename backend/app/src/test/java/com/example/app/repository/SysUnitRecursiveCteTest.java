package com.example.app.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.sys.repository.SysUnitRepository;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 递归 CTE 单位树解析测试。三级树 总部(100) → 分公司A(101) → 部门1(102) 验证
 * {@link SysUnitRepository#findDescendantUnitIds(Long)} 在 H2 PostgreSQL 模式下正确返回全部后代（含根）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SysUnitRecursiveCteTest {

    @Autowired
    SysUnitRepository unitRepository;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void seedTree() {
        jdbc.update(
                "INSERT INTO sys_unit (id, parent_id, unit_code, unit_name, sort, status)"
                        + " VALUES (100, NULL, 'CTE_ROOT', '总部', 0, 1)");
        jdbc.update(
                "INSERT INTO sys_unit (id, parent_id, unit_code, unit_name, sort, status)"
                        + " VALUES (101, 100, 'CTE_BRANCH_A', '分公司A', 1, 1)");
        jdbc.update(
                "INSERT INTO sys_unit (id, parent_id, unit_code, unit_name, sort, status)"
                        + " VALUES (102, 101, 'CTE_DEPT_1', '部门1', 1, 1)");
    }

    @Test
    @DisplayName("根节点返回自身及全部下级（三级树）")
    void root_returnsAllDescendants() {
        assertThat(unitRepository.findDescendantUnitIds(100L))
                .containsExactlyInAnyOrder(100L, 101L, 102L);
    }

    @Test
    @DisplayName("中间节点返回自身及其子树")
    void middle_returnsItsSubtree() {
        assertThat(unitRepository.findDescendantUnitIds(101L))
                .containsExactlyInAnyOrder(101L, 102L);
    }

    @Test
    @DisplayName("叶子节点仅返回自身")
    void leaf_returnsOnlyItself() {
        assertThat(unitRepository.findDescendantUnitIds(102L)).containsExactly(102L);
    }

    @Test
    @DisplayName("结果为 Set 类型，便于直接传入 IN 谓词")
    void returnsSet() {
        Set<Long> ids = unitRepository.findDescendantUnitIds(100L);
        assertThat(ids).isInstanceOf(Set.class).hasSize(3);
    }
}

package com.example.app.datapolicy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.common.datapolicy.DataScope;
import com.example.common.datapolicy.DataScopeContext;
import com.example.common.datapolicy.DataScopeSpecification;
import com.example.sys.domain.SysUser;
import com.example.sys.repository.SysUserRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据权限 Specification 五种范围的端到端校验：在 H2（PostgreSQL 模式）真实库上插入带 {@code unit_id} 与 {@code created_by}
 * 的样本行，断言每种 {@link DataScope} 生成的 Predicate 过滤结果正确。
 *
 * <p>使用 @SpringBootTest + test profile（复用 ApplicationContextLoadsTest 已验证的 H2+Flyway
 * 上下文）， @Transactional 保证每个用例的数据改动在用例结束时回滚。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DataScopeSpecificationTest {

  /** 当前用户（SELF 范围基准）的 created_by 标记。 */
  private static final String ME = "999";

  private static final String OTHER = "888";

  @Autowired SysUserRepository userRepository;

  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void seed() {
    jdbc.update(
        "INSERT INTO sys_user (id, username, password, unit_id, status, created_by)"
            + " VALUES (1001, 'u_hq', 'x', 1, 1, '"
            + ME
            + "')");
    jdbc.update(
        "INSERT INTO sys_user (id, username, password, unit_id, status, created_by)"
            + " VALUES (1002, 'u_unit2', 'x', 2, 1, '"
            + OTHER
            + "')");
    jdbc.update(
        "INSERT INTO sys_user (id, username, password, unit_id, status, created_by)"
            + " VALUES (1003, 'u_self_other_unit', 'x', 1, 1, '"
            + OTHER
            + "')");
  }

  @AfterEach
  void clearContext() {
    DataScopeContext.clear();
  }

  private List<Long> idsWith(DataScopeContext.ScopeData ctx, Set<Long> descendants) {
    return userRepository.findAll(DataScopeSpecification.<SysUser>of(ctx, descendants)).stream()
        .map(SysUser::getId)
        .sorted()
        .toList();
  }

  @Test
  @DisplayName("ALL：不附加任何限制，返回全部行")
  void all_returnsEverything() {
    DataScopeContext.ScopeData ctx =
        new DataScopeContext.ScopeData(DataScope.ALL, null, null, null);
    assertThat(idsWith(ctx, null)).contains(1L, 1001L, 1002L, 1003L);
  }

  @Test
  @DisplayName("SELF：仅返回 created_by=当前用户 的行")
  void self_returnsOnlyOwnRows() {
    DataScopeContext.ScopeData ctx =
        new DataScopeContext.ScopeData(DataScope.SELF, null, null, 999L);
    assertThat(idsWith(ctx, null)).containsExactly(1001L);
  }

  @Test
  @DisplayName("UNIT：仅返回 unit_id=本单位 的行")
  void unit_returnsOnlyOwnUnitRows() {
    DataScopeContext.ScopeData ctx = new DataScopeContext.ScopeData(DataScope.UNIT, 1L, null, null);
    // 单位 1：种子 admin(id=1)、u_hq(1001)、u_self_other_unit(1003)
    assertThat(idsWith(ctx, null)).containsExactlyInAnyOrder(1L, 1001L, 1003L);
  }

  @Test
  @DisplayName("UNIT_BELOW：返回 unit_id ∈ 后代单位集合 的全部行")
  void unitBelow_returnsAllDescendantUnitRows() {
    DataScopeContext.ScopeData ctx =
        new DataScopeContext.ScopeData(DataScope.UNIT_BELOW, 1L, null, null);
    assertThat(idsWith(ctx, Set.of(1L, 2L))).containsExactlyInAnyOrder(1L, 1001L, 1002L, 1003L);
  }

  @Test
  @DisplayName("CUSTOM：仅返回 unit_id ∈ 自定义单位集合 的行")
  void custom_returnsOnlyCustomUnitRows() {
    DataScopeContext.ScopeData ctx =
        new DataScopeContext.ScopeData(DataScope.CUSTOM, null, Set.of(2L), null);
    assertThat(idsWith(ctx, null)).containsExactly(1002L);
  }

  @Test
  @DisplayName("ScopedRepository.scopeFindById：SELF 范围排除他人创建的行")
  void scopeFindById_appliesSelfFilter() {
    DataScopeContext.set(DataScope.SELF, null, null, 999L);
    assertThat(userRepository.scopeFindById(1001L)).as("本人创建：可见").isPresent();
    assertThat(userRepository.scopeFindById(1002L)).as("他人创建：不可见").isEmpty();
  }

  @Test
  @DisplayName("ScopedRepository.scopeFindById：ALL 范围不过滤")
  void scopeFindById_allScopeBypassesFilter() {
    DataScopeContext.set(DataScope.ALL, null, null, null);
    assertThat(userRepository.scopeFindById(1002L)).isPresent();
  }
}

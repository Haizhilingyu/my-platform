package com.example.sys.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.common.datapolicy.DataScope;
import com.example.common.datapolicy.DataScopeContext;
import com.example.common.datapolicy.DataScopeSpecification;
import com.example.common.persistence.BaseEntity;
import com.example.sys.domain.SysRoleDataScope;
import com.example.sys.domain.SysUnit;
import com.example.sys.domain.SysUser;
import com.example.sys.repository.SysRoleDataScopeRepository;
import com.example.sys.repository.SysUnitRepository;
import com.example.sys.repository.SysUserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据权限端到端测试（@DataJpaTest + H2 PostgreSQL 兼容模式）。
 *
 * <p>验证 {@link DataScopeSpecification} 在 5 种范围下对 {@link SysUser} 查询的过滤效果。
 * 种子数据：三级单位树（总部→分公司A→部门1），每单位 2 个用户（含 admin 共 6 人）。
 *
 * <p>使用 create-drop（非 Flyway），避免 H2 IDENTITY 序列与 Flyway 显式 ID 种子冲突。
 */
@DataJpaTest(
    properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
@DisplayName("用户数据权限范围过滤")
class UserDataScopeTest {

  @Autowired private SysUserRepository userRepository;
  @Autowired private SysUnitRepository unitRepository;
  @Autowired private SysRoleDataScopeRepository roleDataScopeRepository;

  private Long hqUnitId;
  private Long branchUnitId;
  private Long deptUnitId;

  private Long adminUserId;
  private Long hqUserId;
  private Long branchUser1Id;
  private Long branchUser2Id;
  private Long deptUser1Id;
  private Long deptUser2Id;

  @BeforeEach
  void seedData() {
    // @Nested 测试方法在本 Spring Boot 版本下未能继承外层 @Transactional 的回滚，
    // 前一方法写入的种子会残留导致唯一键冲突；这里显式清空保证每个方法幂等。
    roleDataScopeRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
    unitRepository.deleteAllInBatch();

    SysUnit hq = SysUnit.builder().unitCode("HQ").unitName("总部").sort(0).status(1).build();
    stamp(hq, "system");
    hq = unitRepository.save(hq);
    hqUnitId = hq.getId();

    SysUnit branch =
        SysUnit.builder()
            .parentId(hqUnitId)
            .unitCode("BRANCH_A")
            .unitName("分公司A")
            .sort(1)
            .status(1)
            .build();
    stamp(branch, "system");
    branch = unitRepository.save(branch);
    branchUnitId = branch.getId();

    SysUnit dept =
        SysUnit.builder()
            .parentId(branchUnitId)
            .unitCode("DEPT_1")
            .unitName("部门1")
            .sort(1)
            .status(1)
            .build();
    stamp(dept, "system");
    dept = unitRepository.save(dept);
    deptUnitId = dept.getId();

    SysUser admin = new SysUser(null, "admin", "$2a$10$x", "管理员", null, null, null, 1, null);
    admin.setUnitId(hqUnitId);
    stamp(admin, "system");
    adminUserId = userRepository.save(admin).getId();
    String adminIdStr = String.valueOf(adminUserId);

    SysUser hqUser = new SysUser(null, "hq_user", "$2a$10$x", "总部用户", null, null, null, 1, null);
    hqUser.setUnitId(hqUnitId);
    stamp(hqUser, adminIdStr);
    hqUserId = userRepository.save(hqUser).getId();

    SysUser branchUser1 =
        new SysUser(null, "branch_user_1", "$2a$10$x", "分公司用户1", null, null, null, 1, null);
    branchUser1.setUnitId(branchUnitId);
    stamp(branchUser1, adminIdStr);
    branchUser1Id = userRepository.save(branchUser1).getId();

    SysUser branchUser2 =
        new SysUser(null, "branch_user_2", "$2a$10$x", "分公司用户2", null, null, null, 1, null);
    branchUser2.setUnitId(branchUnitId);
    stamp(branchUser2, adminIdStr);
    branchUser2Id = userRepository.save(branchUser2).getId();

    SysUser deptUser1 =
        new SysUser(null, "dept_user_1", "$2a$10$x", "部门用户1", null, null, null, 1, null);
    deptUser1.setUnitId(deptUnitId);
    stamp(deptUser1, String.valueOf(hqUserId));
    deptUser1Id = userRepository.save(deptUser1).getId();

    SysUser deptUser2 =
        new SysUser(null, "dept_user_2", "$2a$10$x", "部门用户2", null, null, null, 1, null);
    deptUser2.setUnitId(deptUnitId);
    stamp(deptUser2, adminIdStr);
    deptUser2Id = userRepository.save(deptUser2).getId();

    userRepository.flush();
  }

  private static void stamp(BaseEntity entity, String createdBy) {
    LocalDateTime now = LocalDateTime.now();
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    entity.setCreatedBy(createdBy);
    entity.setUpdatedBy(createdBy);
  }

  private long countWithScope(
      DataScope scope,
      Long userId,
      Long unitId,
      Set<Long> customUnitIds,
      Set<Long> descendantUnitIds) {
    DataScopeContext.ScopeData ctx =
        new DataScopeContext.ScopeData(scope, unitId, customUnitIds, userId);
    Specification<SysUser> spec = DataScopeSpecification.of(ctx, descendantUnitIds);
    return userRepository.findAll(spec, PageRequest.of(0, 100)).getTotalElements();
  }

  // ==================== ALL ====================

  @Test
  @DisplayName("ALL 范围：返回全部 6 个用户（admin + 5 个种子用户）")
  void should_returnAllUsers_when_allScope() {
    long count = countWithScope(DataScope.ALL, adminUserId, hqUnitId, Set.of(), Set.of());
    assertThat(count).isEqualTo(6);
  }

  // ==================== SELF ====================

  @Test
  @DisplayName("SELF 范围（userId=1）：返回 createdBy='1' 的 4 个用户")
  void should_returnOnlySelfCreated_when_selfScope() {
    long count = countWithScope(DataScope.SELF, adminUserId, hqUnitId, Set.of(), Set.of());
    assertThat(count).isEqualTo(4);
  }

  @Test
  @DisplayName("SELF 范围（userId=hqUser）：返回 createdBy=hqUserId 的 1 个用户")
  void should_returnOnlyOne_when_selfScopeWithLimitedCreator() {
    long count = countWithScope(DataScope.SELF, hqUserId, hqUnitId, Set.of(), Set.of());
    assertThat(count).isEqualTo(1);
  }

  // ==================== UNIT ====================

  @Test
  @DisplayName("UNIT 范围（unit=分公司A）：返回该单位 2 个用户")
  void should_returnUnitUsers_when_unitScope() {
    long count = countWithScope(DataScope.UNIT, adminUserId, branchUnitId, Set.of(), Set.of());
    assertThat(count).isEqualTo(2);
  }

  @Test
  @DisplayName("UNIT 范围（unit=总部）：返回总部 2 个用户（admin + hq_user）")
  void should_returnHqUsers_when_unitScopeAtHq() {
    long count = countWithScope(DataScope.UNIT, adminUserId, hqUnitId, Set.of(), Set.of());
    assertThat(count).isEqualTo(2);
  }

  // ==================== UNIT_BELOW ====================

  @Test
  @DisplayName("UNIT_BELOW 范围（unit=总部，后代={1,2,3}）：返回全部 6 个用户")
  void should_returnAll_when_unitBelowFromRoot() {
    Set<Long> descendants = unitRepository.findDescendantUnitIds(hqUnitId);
    assertThat(descendants).containsExactlyInAnyOrder(hqUnitId, branchUnitId, deptUnitId);

    long count = countWithScope(DataScope.UNIT_BELOW, adminUserId, hqUnitId, Set.of(), descendants);
    assertThat(count).isEqualTo(6);
  }

  @Test
  @DisplayName("UNIT_BELOW 范围（unit=分公司A，后代={2,3}）：返回 4 个用户")
  void should_returnBranchAndDept_when_unitBelowFromBranch() {
    Set<Long> descendants = unitRepository.findDescendantUnitIds(branchUnitId);
    assertThat(descendants).containsExactlyInAnyOrder(branchUnitId, deptUnitId);

    long count =
        countWithScope(DataScope.UNIT_BELOW, adminUserId, branchUnitId, Set.of(), descendants);
    assertThat(count).isEqualTo(4);
  }

  @Test
  @DisplayName("UNIT_BELOW 范围（unit=部门1，后代={3}）：返回 2 个用户")
  void should_returnOnlyDept_when_unitBelowFromLeaf() {
    Set<Long> descendants = unitRepository.findDescendantUnitIds(deptUnitId);
    assertThat(descendants).containsExactly(deptUnitId);

    long count =
        countWithScope(DataScope.UNIT_BELOW, adminUserId, deptUnitId, Set.of(), descendants);
    assertThat(count).isEqualTo(2);
  }

  // ==================== CUSTOM ====================

  @Test
  @DisplayName("CUSTOM 范围（customUnitIds={总部,部门1}）：返回 4 个用户")
  void should_returnCustomUnits_when_customScope() {
    Set<Long> customUnitIds = Set.of(hqUnitId, deptUnitId);
    long count = countWithScope(DataScope.CUSTOM, adminUserId, null, customUnitIds, Set.of());
    assertThat(count).isEqualTo(4);
  }

  @Test
  @DisplayName("CUSTOM 范围（customUnitIds={}）：返回 0 个用户")
  void should_returnZero_when_customScopeEmpty() {
    long count = countWithScope(DataScope.CUSTOM, adminUserId, null, Set.of(), Set.of());
    assertThat(count).isZero();
  }

  @Test
  @DisplayName("CUSTOM 范围：通过 sys_role_data_scope 表查询自定义单位 ID")
  void should_queryCustomUnitIds_fromRoleDataScopeTable() {
    Long roleId = 99L;
    roleDataScopeRepository.save(new SysRoleDataScope(roleId, branchUnitId));
    roleDataScopeRepository.save(new SysRoleDataScope(roleId, deptUnitId));
    roleDataScopeRepository.flush();

    Set<Long> unitIds = roleDataScopeRepository.findUnitIdsByRoleIdIn(List.of(roleId));
    assertThat(unitIds).containsExactlyInAnyOrder(branchUnitId, deptUnitId);
  }

  // ==================== 批量删除范围保护 ====================

  @Nested
  @DisplayName("批量删除范围保护")
  class BatchDeleteProtection {

    @Test
    @DisplayName("UNIT 范围内全部可见：可删除")
    void should_allowDelete_when_allIdsWithinUnitScope() {
      DataScopeContext.ScopeData ctx =
          new DataScopeContext.ScopeData(DataScope.UNIT, branchUnitId, Set.of(), adminUserId);
      Specification<SysUser> scopeSpec = DataScopeSpecification.of(ctx, Set.of());

      List<Long> targetIds = List.of(branchUser1Id, branchUser2Id);
      Specification<SysUser> idSpec = (root, query, cb) -> root.get("id").in(targetIds);

      long visibleCount = userRepository.count(scopeSpec.and(idSpec));
      assertThat(visibleCount).isEqualTo(targetIds.size());
    }

    @Test
    @DisplayName("UNIT 范围外存在目标：拒绝（visibleCount < targetIds.size）")
    void should_reject_when_someIdsOutOfUnitScope() {
      DataScopeContext.ScopeData ctx =
          new DataScopeContext.ScopeData(DataScope.UNIT, branchUnitId, Set.of(), adminUserId);
      Specification<SysUser> scopeSpec = DataScopeSpecification.of(ctx, Set.of());

      List<Long> targetIds = List.of(branchUser1Id, branchUser2Id, deptUser1Id);
      Specification<SysUser> idSpec = (root, query, cb) -> root.get("id").in(targetIds);

      long visibleCount = userRepository.count(scopeSpec.and(idSpec));
      assertThat(visibleCount).isLessThan(targetIds.size());
    }

    @Test
    @DisplayName("ALL 范围：所有目标均可见（无过滤）")
    void should_allowAll_when_allScope() {
      DataScopeContext.ScopeData ctx =
          new DataScopeContext.ScopeData(DataScope.ALL, null, Set.of(), adminUserId);
      Specification<SysUser> scopeSpec = DataScopeSpecification.of(ctx, Set.of());

      List<Long> targetIds = List.of(branchUser1Id, deptUser1Id, hqUserId);
      Specification<SysUser> idSpec = (root, query, cb) -> root.get("id").in(targetIds);

      long visibleCount = userRepository.count(scopeSpec.and(idSpec));
      assertThat(visibleCount).isEqualTo(targetIds.size());
    }
  }
}

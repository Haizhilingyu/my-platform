package com.example.common.datapolicy;

import java.util.Collections;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;

/**
 * 数据权限 {@link Specification} 工厂。
 *
 * <p>基于 JPA Criteria API（非 Hibernate @Filter、非字符串拼接 SQL）生成 {@code Predicate}， 从根上规避 SQL 注入风险。调用方通过
 * {@link #of(DataScopeContext.ScopeData, Set)} 传入上下文与 后代单位 ID 集合，得到可用于任意实体的 {@link Specification}。
 *
 * <p>泛型参数 {@code <T>} 不绑定 {@code ScopedEntity}：本类只通过列名（{@code unitId}/{@code createdBy}） 访问字段，类型安全由
 * {@code ScopedRepository} 在调用点以 {@code <T extends ScopedEntity>} 上界保证， 同时避免 {@code datapolicy} ↔
 * {@code persistence} 包间循环依赖（platform-common ArchUnit 约束）。
 *
 * <p>各范围语义：
 *
 * <ul>
 *   <li>{@link DataScope#ALL} — {@code conjunction()}（1=1，无限制）
 *   <li>{@link DataScope#SELF} — {@code created_by = userId}（createdBy 为 VARCHAR，存 userId 字符串）
 *   <li>{@link DataScope#UNIT} — {@code unit_id = unitId}
 *   <li>{@link DataScope#UNIT_BELOW} — {@code unit_id IN (descendantUnitIds)}
 *   <li>{@link DataScope#CUSTOM} — {@code unit_id IN (customUnitIds)}
 * </ul>
 */
public final class DataScopeSpecification {

  private DataScopeSpecification() {}

  /**
   * 构造数据权限 Specification。
   *
   * @param ctx 当前线程权限上下文；为 null 或 ALL 时返回无条件（放行全部）
   * @param descendantUnitIds UNIT_BELOW 范围的后代单位 ID（由递归 CTE 在外层解析后传入），可为 null
   * @param <T> 实体类型（调用方应保证含 {@code unitId}/{@code createdBy} 字段）
   * @return 可叠加到任意查询的 Specification
   */
  public static <T> Specification<T> of(
      DataScopeContext.ScopeData ctx, Set<Long> descendantUnitIds) {
    return (root, query, cb) -> {
      if (ctx == null || ctx.scope() == DataScope.ALL) {
        return cb.conjunction();
      }
      switch (ctx.scope()) {
        case SELF:
          Long userId = ctx.userId();
          if (userId == null) {
            return cb.disjunction();
          }
          return cb.equal(root.get("createdBy"), String.valueOf(userId));
        case UNIT:
          Long unitId = ctx.unitId();
          if (unitId == null) {
            return cb.disjunction();
          }
          return cb.equal(root.get("unitId"), unitId);
        case UNIT_BELOW:
          Set<Long> ids = descendantUnitIds != null ? descendantUnitIds : Collections.emptySet();
          if (ids.isEmpty()) {
            return ctx.unitId() != null
                ? cb.equal(root.get("unitId"), ctx.unitId())
                : cb.disjunction();
          }
          return root.get("unitId").in(ids);
        case CUSTOM:
          Set<Long> custom =
              ctx.customUnitIds() != null ? ctx.customUnitIds() : Collections.emptySet();
          if (custom.isEmpty()) {
            return cb.disjunction();
          }
          return root.get("unitId").in(custom);
        default:
          return cb.conjunction();
      }
    };
  }
}

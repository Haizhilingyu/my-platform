package com.example.common.persistence;

import com.example.common.datapolicy.DataScope;
import com.example.common.datapolicy.DataScopeContext;
import com.example.common.datapolicy.DataScopeSpecification;
import java.io.Serializable;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;

/**
 * 带数据权限的 Repository 基接口。
 *
 * <p>组合 {@link JpaRepository} 与 {@link JpaSpecificationExecutor}，并提供按数据权限范围过滤的
 * {@link #scopeFindById(Serializable)} 默认方法。实体类型上界为 {@link ScopedEntity}，
 * 保证 {@link DataScopeSpecification} 能统一引用 {@code unitId} / {@code createdBy} 字段。
 *
 * @param <T>  范围实体类型
 * @param <ID> 主键类型
 */
public interface ScopedRepository<T extends ScopedEntity, ID extends Serializable>
        extends JpaRepository<T, ID>, JpaSpecificationExecutor<T> {

    /**
     * 按主键查询并应用当前线程的数据权限过滤。{@code UNIT_BELOW} 范围需调用方预先解析后代单位 ID，
     * 传入 {@link #scopeFindById(Serializable, Set)} 重载；本重载以空集降级（等价于仅本单位）。
     *
     * @param id 主键
     * @return 命中且在权限范围内则返回实体，否则 empty
     */
    default Optional<T> scopeFindById(ID id) {
        return scopeFindById(id, Collections.emptySet());
    }

    /**
     * 按主键查询并应用数据权限过滤，显式传入后代单位 ID（用于 {@link DataScope#UNIT_BELOW}）。
     *
     * @param id                 主键
     * @param descendantUnitIds  后代单位 ID 集合（UNIT_BELOW 使用，其他范围可为空集）
     * @return 命中且在权限范围内则返回实体，否则 empty
     */
    default Optional<T> scopeFindById(ID id, Set<Long> descendantUnitIds) {
        DataScopeContext.ScopeData ctx = DataScopeContext.get();
        if (ctx == null || ctx.scope() == DataScope.ALL) {
            return findById(id);
        }
        Specification<T> scope = DataScopeSpecification.<T>of(ctx, descendantUnitIds);
        Specification<T> byId =
                (root, query, cb) -> cb.equal(root.get("id"), id);
        return findOne(scope.and(byId));
    }
}

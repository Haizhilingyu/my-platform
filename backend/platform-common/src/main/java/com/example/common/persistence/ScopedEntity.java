package com.example.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * 带数据权限范围的实体基类。
 *
 * <p>在 {@link BaseEntity} 之上增加 {@code unitId}，供 {@link com.example.common.datapolicy.DataScopeSpecification}
 * 按 UNIT / UNIT_BELOW / CUSTOM 维度过滤。{@code createdBy} 继承自 {@link BaseEntity}（VARCHAR），
 * 用于 SELF 范围过滤，因此不在本类重复声明，避免 JPA 重复列映射。
 *
 * <p>层级：{@code BaseEntity} → {@code ScopedEntity} → 具体实体（如 SysUser）。
 */
@Getter
@Setter
@MappedSuperclass
public abstract class ScopedEntity extends BaseEntity {

    @Column(name = "unit_id")
    private Long unitId;
}

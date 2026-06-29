package com.example.sys.domain;

import com.example.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 单位/组织实体（树形结构）。
 */
@Entity
@Table(name = "sys_unit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SysUnit extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "unit_code", nullable = false, unique = true, length = 64)
    private String unitCode;

    @Column(name = "unit_name", nullable = false, length = 128)
    private String unitName;

    @Column(nullable = false)
    @Builder.Default
    private Integer sort = 0;

    /** 0=禁用 1=启用 */
    @Column(nullable = false)
    @Builder.Default
    private Integer status = 1;

    @Column(length = 500)
    private String remark;
}

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
 * 角色实体。
 */
@Entity
@Table(name = "sys_role")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SysRole extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_code", nullable = false, unique = true, length = 64)
    private String roleCode;

    @Column(name = "role_name", nullable = false, length = 64)
    private String roleName;

    /**
     * 数据权限范围。
     * ALL=全部, UNIT=本单位, UNIT_BELOW=本单位及下属, SELF=仅本人, CUSTOM=自定义
     */
    @Column(name = "data_scope", nullable = false, length = 20)
    @Builder.Default
    private String dataScope = "SELF";

    /** 0=禁用 1=启用 */
    @Column(nullable = false)
    @Builder.Default
    private Integer status = 1;

    @Column(length = 500)
    private String remark;
}

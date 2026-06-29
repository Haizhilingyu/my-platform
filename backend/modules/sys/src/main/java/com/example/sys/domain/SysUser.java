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
 * 系统用户实体。
 */
@Entity
@Table(name = "sys_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SysUser extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(nullable = false, length = 128)
    private String password;

    @Column(name = "real_name", length = 64)
    private String realName;

    @Column(length = 128)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(name = "unit_id")
    private Long unitId;

    @Column(name = "avatar")
    private String avatar;

    /** 0=禁用 1=启用 */
    @Column(nullable = false)
    @Builder.Default
    private Integer status = 1;

    @Column(length = 500)
    private String remark;
}

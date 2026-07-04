package com.example.sys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 角色-自定义数据范围关联。
 *
 * <p>当 {@link SysRole#getDataScope()} 为 {@code CUSTOM} 时，本表存储该角色可见的自定义单位集合。 V3 迁移创建底层表 {@code
 * sys_role_data_scope}（role_id, unit_id 复合主键）。
 */
@Entity
@Table(name = "sys_role_data_scope")
@IdClass(SysRoleDataScope.PK.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SysRoleDataScope {

  @Id
  @Column(name = "role_id")
  private Long roleId;

  @Id
  @Column(name = "unit_id")
  private Long unitId;

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode
  public static class PK implements Serializable {
    private Long roleId;
    private Long unitId;
  }
}

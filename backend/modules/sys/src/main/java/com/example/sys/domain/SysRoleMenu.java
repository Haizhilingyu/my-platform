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

/** 角色-菜单关联。 */
@Entity
@Table(name = "sys_role_menu")
@IdClass(SysRoleMenu.PK.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SysRoleMenu {

    @Id
    @Column(name = "role_id")
    private Long roleId;

    @Id
    @Column(name = "menu_id")
    private Long menuId;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class PK implements Serializable {
        private Long roleId;
        private Long menuId;
    }
}

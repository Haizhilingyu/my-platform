package com.example.sys.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 角色创建/更新 DTO。 */
@Data
public class RoleDTO {

  @NotBlank(message = "{validation.role.roleCode.notBlank}")
  @Size(min = 3, max = 50, message = "{validation.role.roleCode.size}")
  @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "{validation.role.roleCode.pattern}")
  private String roleCode;

  @NotBlank(message = "{validation.role.roleName.notBlank}")
  @Size(max = 100, message = "{validation.role.roleName.size}")
  private String roleName;

  @NotBlank(message = "{validation.role.dataScope.notNull}")
  @Size(max = 20, message = "{validation.role.dataScope.size}")
  private String dataScope;

  @Min(value = 0, message = "{validation.role.status.invalid}")
  @Max(value = 1, message = "{validation.role.status.invalid}")
  private Integer status;

  @Size(max = 200, message = "{validation.role.remark.size}")
  private String remark;
}

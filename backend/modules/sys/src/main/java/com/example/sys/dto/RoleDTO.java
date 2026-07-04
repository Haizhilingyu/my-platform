package com.example.sys.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 角色创建/更新 DTO。 */
@Data
public class RoleDTO {

  @NotBlank(message = "角色编码不能为空")
  private String roleCode;

  @NotBlank(message = "角色名称不能为空")
  private String roleName;

  private String dataScope;
  private Integer status;
  private String remark;
}

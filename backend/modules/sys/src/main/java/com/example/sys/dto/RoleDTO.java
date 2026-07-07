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

  @NotBlank(message = "角色编码不能为空")
  @Size(min = 3, max = 50, message = "角色编码长度需在3-50之间")
  @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "角色编码只能包含字母、数字、下划线")
  private String roleCode;

  @NotBlank(message = "角色名称不能为空")
  @Size(max = 100, message = "角色名称长度不能超过100")
  private String roleName;

  @NotBlank(message = "数据范围不能为空")
  @Size(max = 20, message = "数据范围标识长度不能超过20")
  private String dataScope;

  @Min(value = 0, message = "状态值非法")
  @Max(value = 1, message = "状态值非法")
  private Integer status;

  @Size(max = 200, message = "备注长度不能超过200")
  private String remark;
}

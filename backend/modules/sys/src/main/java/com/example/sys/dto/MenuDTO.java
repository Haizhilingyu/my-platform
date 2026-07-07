package com.example.sys.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 菜单创建/更新 DTO。 */
@Data
public class MenuDTO {

  private Long parentId;

  @NotBlank(message = "菜单名称不能为空")
  @Size(max = 50, message = "菜单名称长度不能超过50")
  private String menuName;

  @NotBlank(message = "菜单类型不能为空")
  @Size(max = 20, message = "菜单类型长度不能超过20")
  private String menuType;

  @Size(max = 200, message = "路由路径长度不能超过200")
  private String path;

  @Size(max = 200, message = "组件路径长度不能超过200")
  private String component;

  @Size(max = 100, message = "权限标识长度不能超过100")
  private String permission;

  @Size(max = 100, message = "图标长度不能超过100")
  private String icon;

  @Min(value = 0, message = "排序值不能为负数")
  private Integer sort;

  @Min(value = 0, message = "可见性值非法")
  @Max(value = 1, message = "可见性值非法")
  private Integer visible;

  @Min(value = 0, message = "状态值非法")
  @Max(value = 1, message = "状态值非法")
  private Integer status;
}

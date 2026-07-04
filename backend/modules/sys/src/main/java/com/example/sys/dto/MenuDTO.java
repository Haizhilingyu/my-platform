package com.example.sys.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 菜单创建/更新 DTO。 */
@Data
public class MenuDTO {

  private Long parentId;

  @NotBlank(message = "菜单名称不能为空")
  private String menuName;

  @NotBlank(message = "菜单类型不能为空")
  private String menuType;

  private String path;
  private String component;
  private String permission;
  private String icon;
  private Integer sort;
  private Integer visible;
  private Integer status;
}

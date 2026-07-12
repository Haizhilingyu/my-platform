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

  @NotBlank(message = "{validation.menu.menuName.notBlank}")
  @Size(max = 50, message = "{validation.menu.menuName.size}")
  private String menuName;

  @NotBlank(message = "{validation.menu.menuType.notNull}")
  @Size(max = 20, message = "{validation.menu.menuType.size}")
  private String menuType;

  @Size(max = 200, message = "{validation.menu.path.size}")
  private String path;

  @Size(max = 200, message = "{validation.menu.component.size}")
  private String component;

  @Size(max = 100, message = "{validation.menu.permission.size}")
  private String permission;

  @Size(max = 100, message = "{validation.menu.icon.size}")
  private String icon;

  @Min(value = 0, message = "{validation.menu.sort.min}")
  private Integer sort;

  @Min(value = 0, message = "{validation.menu.visible.range}")
  @Max(value = 1, message = "{validation.menu.visible.range}")
  private Integer visible;

  @Min(value = 0, message = "{validation.menu.status.invalid}")
  @Max(value = 1, message = "{validation.menu.status.invalid}")
  private Integer status;
}

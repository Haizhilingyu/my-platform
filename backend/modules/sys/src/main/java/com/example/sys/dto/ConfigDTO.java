package com.example.sys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 系统配置更新 DTO。 */
@Data
public class ConfigDTO {

  private Long id;

  @NotBlank(message = "配置键不能为空")
  @Size(max = 100, message = "配置键长度不能超过100")
  @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "配置键只能包含字母、数字、点、下划线、连字符")
  private String configKey;

  @Size(max = 2000, message = "配置值长度不能超过2000")
  private String configValue;

  @Size(max = 50, message = "配置类型长度不能超过50")
  private String configType;

  @Size(max = 500, message = "描述长度不能超过500")
  private String description;

  @Size(max = 50, message = "分类长度不能超过50")
  private String category;
}

package com.example.sys.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 系统配置更新 DTO。 */
@Data
public class ConfigDTO {

  private Long id;

  @NotBlank(message = "配置键不能为空")
  private String configKey;

  private String configValue;
  private String configType;
  private String description;
  private String category;
}

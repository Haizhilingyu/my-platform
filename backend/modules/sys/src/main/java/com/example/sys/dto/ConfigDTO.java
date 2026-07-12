package com.example.sys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 系统配置更新 DTO。 */
@Data
public class ConfigDTO {

  private Long id;

  @NotBlank(message = "{validation.config.configKey.notBlank}")
  @Size(max = 100, message = "{validation.config.configKey.size}")
  @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "{validation.config.configKey.pattern}")
  private String configKey;

  @Size(max = 2000, message = "{validation.config.configValue.size}")
  private String configValue;

  @Size(max = 50, message = "{validation.config.configType.size}")
  private String configType;

  @Size(max = 500, message = "{validation.config.description.size}")
  private String description;

  @Size(max = 50, message = "{validation.config.category.size}")
  private String category;
}

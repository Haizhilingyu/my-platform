package com.example.sys.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 单位创建/更新 DTO。 */
@Data
public class UnitDTO {

  private Long parentId;

  @NotBlank(message = "{validation.unit.unitCode.notBlank}")
  @Size(min = 3, max = 50, message = "{validation.unit.unitCode.size}")
  @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "{validation.unit.unitCode.pattern}")
  private String unitCode;

  @NotBlank(message = "{validation.unit.unitName.notBlank}")
  @Size(max = 100, message = "{validation.unit.unitName.size}")
  private String unitName;

  @Min(value = 0, message = "{validation.unit.sort.min}")
  private Integer sort;

  @Min(value = 0, message = "{validation.unit.status.invalid}")
  @Max(value = 1, message = "{validation.unit.status.invalid}")
  private Integer status;

  @Size(max = 200, message = "{validation.unit.remark.size}")
  private String remark;
}

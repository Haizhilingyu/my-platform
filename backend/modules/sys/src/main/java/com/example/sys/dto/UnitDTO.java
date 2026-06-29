package com.example.sys.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 单位创建/更新 DTO。 */
@Data
public class UnitDTO {

    private Long parentId;

    @NotBlank(message = "单位编码不能为空")
    private String unitCode;

    @NotBlank(message = "单位名称不能为空")
    private String unitName;

    private Integer sort;
    private Integer status;
    private String remark;
}

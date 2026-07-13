package com.example.i18n.dto;

import lombok.Data;

/** 翻译分页查询参数（字段均可选）。 */
@Data
public class I18nMessageQueryDTO {

  private String locale;
  private String module;
  private String keyLike;
  private Integer pageNum = 1;
  private Integer pageSize = 20;
}

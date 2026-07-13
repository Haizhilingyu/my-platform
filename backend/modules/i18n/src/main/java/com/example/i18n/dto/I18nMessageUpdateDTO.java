package com.example.i18n.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 更新翻译值 DTO。 */
@Data
public class I18nMessageUpdateDTO {

  @NotBlank(message = "翻译值不能为空")
  @Size(max = 5000, message = "翻译值长度不能超过5000")
  private String value;
}

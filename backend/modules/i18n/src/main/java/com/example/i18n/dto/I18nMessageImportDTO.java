package com.example.i18n.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/** 批量导入翻译 DTO。locale 指定目标语言，items 为待更新条目。 */
@Data
public class I18nMessageImportDTO {

  @NotBlank(message = "语言不能为空")
  @Pattern(regexp = "^(zh-CN|en)$", message = "{validation.controller.locale.pattern}")
  private String locale;

  @Valid
  @NotEmpty(message = "{validation.controller.configList.notEmpty}")
  private List<Item> items;

  @Data
  public static class Item {

    @NotBlank(message = "消息 key 不能为空")
    @Size(max = 200, message = "消息 key 长度不能超过200")
    private String messageKey;

    @NotBlank(message = "翻译值不能为空")
    @Size(max = 5000, message = "翻译值长度不能超过5000")
    private String value;
  }
}

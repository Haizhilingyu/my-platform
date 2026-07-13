package com.example.sys.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class MenuTranslationImportDTO {

  @NotBlank
  @Size(max = 10)
  private String locale;

  @Valid private List<Item> items;

  @Data
  public static class Item {
    private Long menuId;

    @NotBlank
    @Size(max = 100)
    private String displayName;
  }
}

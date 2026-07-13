package com.example.sys.dto;

import lombok.Data;

@Data
public class MenuTranslationVO {
  private Long id;
  private Long menuId;
  private String menuName;
  private String locale;
  private String displayName;
}

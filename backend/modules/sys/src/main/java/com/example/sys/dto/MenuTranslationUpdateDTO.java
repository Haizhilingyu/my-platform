package com.example.sys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MenuTranslationUpdateDTO {

  @NotBlank
  @Size(max = 100)
  private String displayName;
}

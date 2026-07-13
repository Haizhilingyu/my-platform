package com.example.sys.controller;

import com.example.common.result.Result;
import com.example.sys.dto.MenuTranslationImportDTO;
import com.example.sys.dto.MenuTranslationUpdateDTO;
import com.example.sys.dto.MenuTranslationVO;
import com.example.sys.service.MenuTranslationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sys/menu/translations")
@RequiredArgsConstructor
public class MenuTranslationController {

  private final MenuTranslationService service;

  @GetMapping
  public Result<List<MenuTranslationVO>> list() {
    return Result.ok(service.list());
  }

  @PutMapping("/{id}")
  public Result<Void> update(
      @PathVariable Long id, @Valid @RequestBody MenuTranslationUpdateDTO dto) {
    service.update(id, dto.getDisplayName());
    return Result.ok();
  }

  @PostMapping("/import")
  public Result<Void> importTranslations(@Valid @RequestBody MenuTranslationImportDTO dto) {
    service.importTranslations(dto);
    return Result.ok();
  }

  @GetMapping("/export")
  public Result<List<MenuTranslationVO>> export(@RequestParam String locale) {
    return Result.ok(service.exportByLocale(locale));
  }
}

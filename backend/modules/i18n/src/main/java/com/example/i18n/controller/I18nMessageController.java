package com.example.i18n.controller;

import com.example.common.result.PageResult;
import com.example.common.result.Result;
import com.example.common.security.RequiresPermission;
import com.example.i18n.domain.I18nMessage;
import com.example.i18n.dto.I18nMessageImportDTO;
import com.example.i18n.dto.I18nMessageQueryDTO;
import com.example.i18n.dto.I18nMessageUpdateDTO;
import com.example.i18n.dto.I18nMessageVO;
import com.example.i18n.excel.I18nExcelExporter;
import com.example.i18n.excel.I18nExcelParser;
import com.example.i18n.service.I18nMessageService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 国际化消息管理 Controller。 */
@RestController
@RequestMapping("/api/i18n/messages")
@RequiredArgsConstructor
public class I18nMessageController {

  private final I18nMessageService service;

  @RequiresPermission("sys:i18n:list")
  @GetMapping
  public Result<PageResult<I18nMessageVO>> list(I18nMessageQueryDTO query) {
    return Result.ok(service.list(query));
  }

  @RequiresPermission("sys:i18n:list")
  @GetMapping("/all")
  public Result<Map<String, String>> all(@RequestParam String locale) {
    return Result.ok(service.getFlatMap(locale));
  }

  @RequiresPermission("sys:i18n:export")
  @GetMapping("/export")
  public Object export(
      @RequestParam String locale, @RequestParam(defaultValue = "json") String format)
      throws IOException {
    List<I18nMessageVO> data = service.exportByLocale(locale);
    if ("xlsx".equalsIgnoreCase(format)) {
      List<I18nMessage> entities =
          service.exportByLocale(locale).stream().map(I18nMessageController::toEntity).toList();
      byte[] bytes = I18nExcelExporter.export(entities);
      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=i18n_" + locale + ".xlsx")
          .contentType(
              MediaType.parseMediaType(
                  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
          .body(new ByteArrayResource(bytes));
    }
    return Result.ok(data);
  }

  @RequiresPermission("sys:i18n:edit")
  @PutMapping("/{id}")
  public Result<I18nMessageVO> update(
      @PathVariable Long id, @RequestBody @Valid I18nMessageUpdateDTO dto) {
    return Result.ok(service.update(id, dto));
  }

  @RequiresPermission("sys:i18n:import")
  @PostMapping(
      value = "/import",
      consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
  public Result<Integer> importMessages(
      @RequestBody(required = false) @Valid I18nMessageImportDTO dto,
      @RequestParam(value = "locale", required = false) String locale,
      @RequestParam(value = "file", required = false) MultipartFile file)
      throws IOException {
    if (file != null && !file.isEmpty()) {
      List<I18nMessageImportDTO.Item> items = I18nExcelParser.parse(file.getInputStream());
      I18nMessageImportDTO parsed = new I18nMessageImportDTO();
      parsed.setLocale(locale);
      parsed.setItems(items);
      return Result.ok(service.importMessages(parsed));
    }
    return Result.ok(service.importMessages(dto));
  }

  private static I18nMessage toEntity(I18nMessageVO vo) {
    I18nMessage m = new I18nMessage();
    m.setMessageKey(vo.getMessageKey());
    m.setModule(vo.getModule());
    m.setDescription(vo.getDescription());
    m.setValue(vo.getValue());
    return m;
  }
}

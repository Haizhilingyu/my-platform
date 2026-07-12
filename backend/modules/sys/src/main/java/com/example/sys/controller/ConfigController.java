package com.example.sys.controller;

import com.example.common.result.Result;
import com.example.common.security.RequiresPermission;
import com.example.sys.domain.SysConfig;
import com.example.sys.dto.ConfigDTO;
import com.example.sys.service.ConfigService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 系统配置 Controller。 */
@RestController
@RequestMapping("/api/sys/config")
@RequiredArgsConstructor
@Validated
public class ConfigController {

  private final ConfigService configService;

  @RequiresPermission("sys:config:list")
  @GetMapping
  public Result<List<SysConfig>> list(@RequestParam(required = false) String category) {
    if (category != null) {
      return Result.ok(configService.findByCategory(category));
    }
    return Result.ok(configService.findAll());
  }

  @RequiresPermission("sys:config:list")
  @GetMapping("/{key}")
  public Result<SysConfig> getByKey(@PathVariable String key) {
    return Result.ok(configService.getByKey(key));
  }

  @RequiresPermission("sys:config:add")
  @PostMapping
  public Result<Long> create(@RequestBody @Valid ConfigDTO dto) {
    return Result.ok(configService.create(dto));
  }

  @RequiresPermission("sys:config:edit")
  @PutMapping("/{id}")
  public Result<Void> update(@PathVariable Long id, @RequestBody @Valid ConfigDTO dto) {
    configService.update(id, dto);
    return Result.ok();
  }

  @RequiresPermission("sys:config:edit")
  @PutMapping("/batch")
  public Result<Void> batchUpdate(
      @RequestBody @NotEmpty(message = "{validation.controller.configList.notEmpty}")
          List<ConfigDTO> configs) {
    configService.batchUpdate(configs);
    return Result.ok();
  }

  @GetMapping("/map/{category}")
  public Result<Map<String, String>> getAsMap(@PathVariable String category) {
    return Result.ok(configService.getAsMap(category));
  }
}

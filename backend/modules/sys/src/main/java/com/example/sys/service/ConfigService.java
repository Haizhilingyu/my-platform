package com.example.sys.service;

import com.example.common.exception.BizException;
import com.example.common.exception.NotFoundException;
import com.example.common.i18n.Messages;
import com.example.sys.domain.SysConfig;
import com.example.sys.dto.ConfigDTO;
import com.example.sys.repository.SysConfigRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 系统配置服务。 */
@Service
@RequiredArgsConstructor
public class ConfigService {

  private final SysConfigRepository configRepository;

  @Transactional(readOnly = true)
  public List<SysConfig> findAll() {
    return configRepository.findAll();
  }

  @Transactional(readOnly = true)
  public List<SysConfig> findByCategory(String category) {
    return configRepository.findByCategory(category);
  }

  @Transactional(readOnly = true)
  public SysConfig getByKey(String key) {
    return configRepository
        .findByConfigKey(key)
        .orElseThrow(
            () ->
                NotFoundException.i18n(
                    "error.resource.not.found", Messages.get("resource.config"), key));
  }

  @Transactional(readOnly = true)
  public String getValue(String key, String defaultValue) {
    return configRepository
        .findByConfigKey(key)
        .map(SysConfig::getConfigValue)
        .orElse(defaultValue);
  }

  @Transactional
  public Long create(ConfigDTO dto) {
    if (configRepository.existsByConfigKey(dto.getConfigKey())) {
      throw BizException.i18n("config.key.exists", dto.getConfigKey());
    }
    SysConfig config =
        SysConfig.builder()
            .configKey(dto.getConfigKey())
            .configValue(dto.getConfigValue())
            .configType(dto.getConfigType() != null ? dto.getConfigType() : "STRING")
            .description(dto.getDescription())
            .category(dto.getCategory() != null ? dto.getCategory() : "default")
            .build();
    return configRepository.save(config).getId();
  }

  @Transactional
  public void update(Long id, ConfigDTO dto) {
    SysConfig config =
        configRepository
            .findById(id)
            .orElseThrow(
                () ->
                    NotFoundException.i18n(
                        "error.resource.not.found", Messages.get("resource.config"), id));
    if (dto.getConfigValue() != null) {
      config.setConfigValue(dto.getConfigValue());
    }
    if (dto.getConfigType() != null) {
      config.setConfigType(dto.getConfigType());
    }
    if (dto.getDescription() != null) {
      config.setDescription(dto.getDescription());
    }
    if (dto.getCategory() != null) {
      config.setCategory(dto.getCategory());
    }
    configRepository.save(config);
  }

  @Transactional
  public void batchUpdate(List<ConfigDTO> configs) {
    for (ConfigDTO dto : configs) {
      if (dto.getId() != null) {
        update(dto.getId(), dto);
      }
    }
  }

  @Transactional(readOnly = true)
  public Map<String, String> getAsMap(String category) {
    return configRepository.findByCategory(category).stream()
        .collect(Collectors.toMap(SysConfig::getConfigKey, SysConfig::getConfigValue));
  }
}

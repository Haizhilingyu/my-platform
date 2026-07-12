package com.example.sys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 语言偏好更新请求，仅允许 zh-CN 或 en。校验消息复用 B1 在 {@code messages.properties} 预置的 {@code
 * validation.controller.locale.pattern} 键。
 */
public record LocaleUpdateDTO(
    @NotBlank(message = "{validation.controller.locale.pattern}")
        @Pattern(regexp = "^(zh-CN|en)$", message = "{validation.controller.locale.pattern}")
        String locale) {}

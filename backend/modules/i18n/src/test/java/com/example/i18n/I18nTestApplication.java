package com.example.i18n;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * i18n 模块测试专用引导类（库模块无 @SpringBootApplication，切片测试需要 @SpringBootConfiguration）。
 *
 * <p>开启 {@link EnableJpaAuditing} 以便 {@link com.example.common.persistence.BaseEntity} 的审计字段在测试中
 * 自动填充。
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EnableJpaAuditing
public class I18nTestApplication {}

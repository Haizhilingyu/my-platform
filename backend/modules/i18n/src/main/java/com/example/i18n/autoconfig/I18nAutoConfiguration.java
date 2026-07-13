package com.example.i18n.autoconfig;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ComponentScan(basePackages = "com.example.i18n")
@EntityScan(basePackages = "com.example.i18n.domain")
@EnableJpaRepositories(basePackages = "com.example.i18n.repository")
public class I18nAutoConfiguration {}

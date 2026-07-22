package com.example.aiagent.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 启用 {@link AgentProperties}，并扫描 ai-agent 的 JPA 实体与仓库。组件扫描由应用根 {@code @SpringBootApplication} 覆盖
 * com.example.*。
 */
@Configuration
@EnableConfigurationProperties(AgentProperties.class)
@EntityScan(basePackages = "com.example.aiagent.chat.domain")
@EnableJpaRepositories(basePackages = "com.example.aiagent.chat.repository")
public class AgentAutoConfiguration {}

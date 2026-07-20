package com.example.aiagent.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 启用 {@link AgentProperties}。组件扫描由应用根 {@code @SpringBootApplication} 覆盖 com.example.*。 */
@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentAutoConfiguration {}

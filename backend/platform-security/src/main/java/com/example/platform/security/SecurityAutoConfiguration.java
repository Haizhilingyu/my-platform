package com.example.platform.security;

import com.example.common.security.JwtUtil;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 安全模块自动配置入口。
 *
 * <p>通过 {@code META-INF/spring/...AutoConfiguration.imports} SPI 注册， 任何 classpath 包含 Spring
 * Security + {@link JwtUtil} 的应用都会自动装配 {@link SecurityConfig}（SecurityFilterChain + JwtAuthFilter）。
 */
@AutoConfiguration
@ConditionalOnClass({SecurityFilterChain.class, JwtUtil.class})
@Import(SecurityConfig.class)
public class SecurityAutoConfiguration {}

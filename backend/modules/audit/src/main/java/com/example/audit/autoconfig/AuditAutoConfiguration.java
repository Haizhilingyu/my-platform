package com.example.audit.autoconfig;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 审计模块自动配置。
 *
 * <p>{@code @EnableAsync} 开启异步支持，使 {@code AuditLogService.record} 的 {@code @Async}
 * 注解生效——审计落库在独立线程池执行，AOP 切面调用后立即返回，主请求线程开销 &lt; 5ms。
 */
@Configuration
@EnableAsync
@ComponentScan(basePackages = "com.example.audit")
@EntityScan(basePackages = "com.example.audit.domain")
@EnableJpaRepositories(basePackages = "com.example.audit.repository")
public class AuditAutoConfiguration {}

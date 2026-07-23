package com.example.common.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Jackson 2（{@code com.fasterxml.jackson}）{@link ObjectMapper} bean 配置。
 *
 * <p><b>Spring Boot 4.1 背景</b>：Boot 4.1 的 {@code JacksonAutoConfiguration} 已迁移到 Jackson 3 （{@code
 * tools.jackson.databind.json.JsonMapper}），不再自动注册 Jackson 2 的 {@link ObjectMapper} bean。但本平台及
 * Spring AI 2.0 等依赖仍使用 Jackson 2 API（{@code com.fasterxml.jackson}）， 需要一个 容器级 {@link ObjectMapper}
 * bean（注入到 {@code AuditAspect}、{@code MessageService}、 {@code DeepSeekAgentBrain}、{@code
 * SpaErrorController} 等）。本类补齐该 bean。
 *
 * <p>注册 {@link JavaTimeModule} 并禁用 {@code WRITE_DATES_AS_TIMESTAMPS}，统一 Java 8 日期时间序列化。
 */
@AutoConfiguration
public class JacksonConfig {

  @Bean
  public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return mapper;
  }
}

package com.example.openapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 开放 API 资源服务器（Filter Chain Order=2）：校验外部应用 access_token。
 *
 * <p>与授权服务器共用同一个 JwtDecoder（由 AuthorizationServerConfig 提供，基于持久化 JWK）。 /openapi/** 上的方法可标注
 * {@code @RequiresAppScope} 做细粒度 scope 校验。
 */
@Configuration
public class OpenApiResourceServerConfig {

  @Bean
  @Order(2)
  public SecurityFilterChain openApiFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/openapi/**")
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
    return http.build();
  }
}

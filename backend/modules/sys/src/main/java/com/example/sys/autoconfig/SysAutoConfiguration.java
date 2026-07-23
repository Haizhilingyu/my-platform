package com.example.sys.autoconfig;

import com.example.common.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 系统设置模块自动配置。
 *
 * <p>引入此模块后自动完成：实体扫描、Repository 扫描、组件扫描、JWT 和密码编码器注册。
 */
@Configuration
@ComponentScan(basePackages = "com.example.sys")
@EntityScan(basePackages = "com.example.sys.domain")
@EnableJpaRepositories(basePackages = "com.example.sys.repository")
public class SysAutoConfiguration {

  @Bean
  public JwtUtil jwtUtil(
      @Value("${app.security.jwt.secret:my-platform-secret-key-must-be-at-least-32-bytes}")
          String secret,
      @Value("${app.security.jwt.expiration:86400000}") long expiration) {
    return new JwtUtil(secret, expiration);
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}

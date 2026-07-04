package com.example.platform.security;

import com.example.common.security.JwtUtil;
import com.example.common.security.PermissionLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 安全配置。JWT 无状态认证 + 权限注入。
 *
 * <p>本类由 {@link SecurityAutoConfiguration} 通过 {@code @Import} 加载，
 * 仅当 classpath 存在 Spring Security + {@link JwtUtil} 时才激活。
 * 应用引入 {@code platform-starter} 即自动获得此配置，无需额外声明。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final PermissionLoader permissionLoader;

    /**
     * 公开路径白名单。不需要认证即可访问。
     *
     * <p><b>注意</b>：修改此列表会影响 E2E 测试和前端路由守卫，需谨慎。
     */
    static final String[] PUBLIC_PATHS = {
            "/sys/auth/login",
            "/doc/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/actuator/**",
            "/favicon.ico"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthFilter(jwtUtil, permissionLoader),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

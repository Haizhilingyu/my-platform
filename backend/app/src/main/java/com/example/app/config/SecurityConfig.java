package com.example.app.config;

import com.example.common.security.CurrentUser;
import com.example.common.security.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.sys.service.PermissionService;

/**
 * 安全配置。JWT 无状态认证 + 权限注入。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final PermissionService permissionService;

    private static final String[] PUBLIC_PATHS = {
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
                .addFilterBefore(new JwtAuthFilter(jwtUtil, permissionService),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * JWT 认证过滤器。解析 token，注入当前用户上下文和权限。
     */
    @RequiredArgsConstructor
    static class JwtAuthFilter extends OncePerRequestFilter {

        private final JwtUtil jwtUtil;
        private final PermissionService permissionService;

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {
            String authHeader = request.getHeader("Authorization");
            String token = jwtUtil.extractToken(authHeader);

            if (token != null && jwtUtil.isValid(token)) {
                var claims = jwtUtil.parse(token);
                Long userId = Long.valueOf(claims.getSubject());
                String username = claims.get("username", String.class);

                @SuppressWarnings("unchecked")
                List<String> roleList = claims.get("roles", List.class);
                Set<String> roles = roleList != null ? new HashSet<>(roleList) : new HashSet<>();

                Set<String> permissions = permissionService.getUserPermissions(userId);

                CurrentUser.set(new CurrentUser.UserInfo(userId, username, null, roles, permissions));

                var authToken = new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList());
                var context = new org.springframework.security.core.context.SecurityContextImpl();
                context.setAuthentication(authToken);
                org.springframework.security.core.context.SecurityContextHolder.setContext(context);
            }

            try {
                chain.doFilter(request, response);
            } finally {
                CurrentUser.clear();
            }
        }
    }
}

package com.example.platform.security;

import com.example.common.cache.RedisCacheService;
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
 * <p>本类由 {@link SecurityAutoConfiguration} 通过 {@code @Import} 加载， 仅当 classpath 存在 Spring Security +
 * {@link JwtUtil} 时才激活。 应用引入 {@code platform-starter} 即自动获得此配置，无需额外声明。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtUtil jwtUtil;
  private final PermissionLoader permissionLoader;
  private final RedisCacheService redisCacheService;

  /**
   * 公开路径白名单。不需要认证即可访问。
   *
   * <p><b>注意</b>：修改此列表会影响 E2E 测试和前端路由守卫，需谨慎。 {@code /api/sys/auth/logout}
   * 不在此列表中——登出要求携带有效 token。
   *
   * <p>T31 起，内部 API 统一加 {@code /api} 前缀以与 SPA 页面路由（{@code /}）分离。 静态资源 + SPA
   * 入口也在此白名单，便于 Spring Boot 直接服务前端静态资源（前后端合并打包场景）。
   *
   * <p><b>三层授权策略（T31 修复）</b>：filter chain 按 {@link #filterChain(HttpSecurity)} 中声明顺序匹配：
   *
   * <ol>
   *   <li><b>PUBLIC_PATHS</b> → {@code permitAll}：登录入口、静态资源、SPA index、文档、监控、WebSocket。
   *       公开 API（如 {@code /api/sys/auth/login}）必须在此层先命中，否则会被下一层 {@code /api/**}
   *       要求认证。
   *   <li><b>{@code /api/**}</b> → {@code authenticated}：所有内部管理 API 需登录。SPA 自身在客户端做
   *       路由守卫（无 token 跳 /login），后端仅保护 API 命名空间。
   *   <li><b>{@code anyRequest}</b> → {@code permitAll}：SPA 页面路由（如 {@code /login}、{@code
   *       /dashboard}、{@code /sys/user}、{@code /random/deep/link}）一律放行，让请求穿过 Security
   *       到达 DispatcherServlet → 无匹配 controller → 404 → {@code SpaErrorController} forward 到
   *       {@code /index.html} 返回 200 HTML，实现 SPA deep-linking。
   * </ol>
   *
   * <p>{@code /openapi/**} 和 {@code /oauth2/**} 由独立的 {@link SecurityFilterChain}（Order=1/2）处理，
   * 不会走到本 default 链，故此处的 {@code anyRequest().permitAll()} 不影响它们。
   */
  static final String[] PUBLIC_PATHS = {
    // API 公开路径（登录、登录方式、验证码）
    "/api/sys/auth/login",
    "/api/sys/auth/login-methods",
    "/api/sys/auth/captcha",
    // 静态资源 + SPA 入口（Spring Boot 直接服务前端）
    "/",
    "/index.html",
    "/*.html",
    "/*.js",
    "/*.css",
    "/assets/**",
    "/error",
    // 文档 + 监控（基础设施端点）
    "/doc/**",
    "/swagger-ui/**",
    "/v3/api-docs/**",
    "/actuator/**",
    // WebSocket
    "/ws/**",
    // favicon
    "/favicon.ico"
  };

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth
                    // 1. 公开白名单（登录入口、静态资源、SPA index、文档、监控、WebSocket）
                    .requestMatchers(PUBLIC_PATHS)
                    .permitAll()
                    // 2. 所有 /api/** 内部 API 需认证（公开 API 已在 PUBLIC_PATHS 中先匹配）
                    .requestMatchers("/api/**")
                    .authenticated()
                    // 3. 其余路径（SPA 页面路由如 /login、/dashboard、/sys/user）一律放行 ——
                    //    SPA 自身在客户端做路由守卫（无 token 跳 /login）。
                    //    后端仅保护 API 命名空间，不保护 SPA 页面 HTML。
                    //    /openapi/** 和 /oauth2/** 由独立的 SecurityFilterChain（Order=1/2）处理，不会走到这里。
                    .anyRequest()
                    .permitAll())
        .addFilterBefore(
            new JwtAuthFilter(jwtUtil, permissionLoader, redisCacheService),
            UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}

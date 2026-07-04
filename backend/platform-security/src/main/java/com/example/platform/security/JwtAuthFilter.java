package com.example.platform.security;

import com.example.common.security.CurrentUser;
import com.example.common.security.JwtUtil;
import com.example.common.security.PermissionLoader;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 认证过滤器。解析 token，注入当前用户上下文和权限。
 *
 * <p>由 {@link SecurityConfig} 注册到过滤器链，位于
 * {@code UsernamePasswordAuthenticationFilter} 之前。
 */
@RequiredArgsConstructor
class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final PermissionLoader permissionLoader;

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
            Long unitId = claims.get("unitId", Long.class);

            @SuppressWarnings("unchecked")
            List<String> roleList = claims.get("roles", List.class);
            Set<String> roles = roleList != null ? new HashSet<>(roleList) : new HashSet<>();

            Set<String> permissions = permissionLoader.loadPermissions(userId);

            CurrentUser.set(new CurrentUser.UserInfo(userId, username, unitId, roles, permissions));

            var authToken = new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList());
            var context = new SecurityContextImpl();
            context.setAuthentication(authToken);
            SecurityContextHolder.setContext(context);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            CurrentUser.clear();
        }
    }
}

package com.example.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.cache.RedisCacheService;
import com.example.common.security.CurrentUser;
import com.example.common.security.JwtUtil;
import com.example.common.security.PermissionLoader;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JwtAuthFilter 黑名单校验")
class JwtAuthFilterTest {

    private static final String SECRET = "012345678901234567890123456789012345678901234567890123456789";
    private static final long EXPIRATION_MS = 3600_000L;

    private JwtUtil jwtUtil;

    @Mock private PermissionLoader permissionLoader;
    @Mock private RedisCacheService redisCacheService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain chain;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, EXPIRATION_MS);
        SecurityContextHolder.clearContext();
        CurrentUser.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        CurrentUser.clear();
    }

    @Test
    @DisplayName("有效且未拉黑 token → 注入认证上下文")
    void validToken_notBlacklisted_setsContext() throws Exception {
        String token = jwtUtil.generate(1L, "admin", 10L, List.of("ADMIN"));
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(redisCacheService.exists(eq(JwtAuthFilter.BLACKLIST_KEY_PREFIX + jwtUtil.parse(token).getId())))
                .thenReturn(false);
        when(permissionLoader.loadPermissions(eq(1L))).thenReturn(Set.of("sys:user:list"));

        // 在 chain 执行期间捕获 CurrentUser（过滤器 finally 会清空 ThreadLocal）
        final Long[] capturedUserId = new Long[1];
        final String[] capturedUsername = new String[1];
        org.mockito.Mockito.doAnswer(inv -> {
            capturedUserId[0] = CurrentUser.getUserId();
            capturedUsername[0] = CurrentUser.getUsername();
            return null;
        }).when(chain).doFilter(request, response);

        new JwtAuthFilter(jwtUtil, permissionLoader, redisCacheService)
                .doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(capturedUserId[0]).isEqualTo(1L);
        assertThat(capturedUsername[0]).isEqualTo("admin");
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("已拉黑 token → 不注入认证上下文（拒绝）")
    void blacklistedToken_doesNotSetContext() throws Exception {
        String token = jwtUtil.generate(1L, "admin", null, List.of("ADMIN"));
        String jti = jwtUtil.parse(token).getId();
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(redisCacheService.exists(JwtAuthFilter.BLACKLIST_KEY_PREFIX + jti)).thenReturn(true);

        new JwtAuthFilter(jwtUtil, permissionLoader, redisCacheService)
                .doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(CurrentUser.getUserId()).isNull();
        // permissionLoader 不应被调用（黑名单命中前即短路）
        verify(permissionLoader, never()).loadPermissions(anyLong());
        // 请求仍继续走过滤链（由 SecurityFilterChain 后续判定 401）
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("无 token → 不查 Redis、不注入上下文")
    void noToken_doesNothing() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        new JwtAuthFilter(jwtUtil, permissionLoader, redisCacheService)
                .doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(redisCacheService, never()).exists(org.mockito.ArgumentMatchers.anyString());
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("无效 token → 不查 Redis、不注入上下文")
    void invalidToken_doesNothing() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer not.a.valid.token");

        new JwtAuthFilter(jwtUtil, permissionLoader, redisCacheService)
                .doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(redisCacheService, never()).exists(org.mockito.ArgumentMatchers.anyString());
    }
}

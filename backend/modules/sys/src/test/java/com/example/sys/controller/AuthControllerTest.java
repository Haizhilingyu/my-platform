package com.example.sys.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.common.cache.RedisCacheService;
import com.example.common.result.Result;
import com.example.common.security.JwtUtil;
import com.example.sys.service.MenuService;
import com.example.sys.service.PermissionService;
import com.example.sys.service.UserService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

@DisplayName("AuthController 登出黑名单")
class AuthControllerTest {

    private static final String SECRET = "012345678901234567890123456789012345678901234567890123456789";
    private static final long EXPIRATION_MS = 3600_000L;

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, EXPIRATION_MS);
    private final UserService userService = mock(UserService.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final MenuService menuService = mock(MenuService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final RedisCacheService redisCacheService = mock(RedisCacheService.class);

    private final AuthController controller = new AuthController(
            userService, permissionService, menuService, passwordEncoder, jwtUtil, redisCacheService);

    @Test
    @DisplayName("登出：将 jti 写入黑名单，TTL = token 剩余有效期")
    void logout_putsJtiIntoBlacklist_withRemainingTtl() {
        String token = jwtUtil.generate(1L, "admin", 10L, List.of("ADMIN"));
        Claims claims = jwtUtil.parse(token);
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        Result<Void> result = controller.logout(request);

        assertThat(result.isSuccess()).isTrue();
        Duration expectedTtl = Duration.between(
                java.time.Instant.now(), claims.getExpiration().toInstant());
        verify(redisCacheService)
                .set(eq(AuthController.BLACKLIST_KEY_PREFIX + claims.getId()), eq("1"),
                        Mockito.argThat(d -> d != null && Math.abs(d.toSeconds() - expectedTtl.toSeconds()) <= 2));
    }

    @Test
    @DisplayName("登出：无 token → 不操作 Redis（幂等）")
    void logout_withoutToken_isNoop() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getHeader("Authorization")).thenReturn(null);

        Result<Void> result = controller.logout(request);

        assertThat(result.isSuccess()).isTrue();
        verifyNoInteractions(redisCacheService);
    }

    @Test
    @DisplayName("登出：无效 token → 不操作 Redis（不抛异常）")
    void logout_invalidToken_isNoop() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getHeader("Authorization")).thenReturn("Bearer garbage");

        Result<Void> result = controller.logout(request);

        assertThat(result.isSuccess()).isTrue();
        verifyNoInteractions(redisCacheService);
    }
}

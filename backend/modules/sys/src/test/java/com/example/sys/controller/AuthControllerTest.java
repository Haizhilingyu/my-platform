package com.example.sys.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.common.cache.RedisCacheService;
import com.example.common.exception.BizException;
import com.example.common.login.LoginMethodDescriptor;
import com.example.common.login.LoginMethodProvider;
import com.example.common.login.LoginMethodRegistry;
import com.example.common.login.LoginRequest;
import com.example.common.result.Result;
import com.example.common.security.JwtUtil;
import com.example.sys.dto.LoginVO;
import com.example.sys.dto.UserVO;
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

@DisplayName("AuthController 登出黑名单 + 登录路由")
class AuthControllerTest {

    private static final String SECRET = "012345678901234567890123456789012345678901234567890123456789";
    private static final long EXPIRATION_MS = 3600_000L;

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, EXPIRATION_MS);
    private final UserService userService = mock(UserService.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final MenuService menuService = mock(MenuService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final RedisCacheService redisCacheService = mock(RedisCacheService.class);
    private final LoginMethodRegistry loginMethodRegistry = mock(LoginMethodRegistry.class);

    private final AuthController controller =
            new AuthController(
                    userService, permissionService, menuService, jwtUtil, redisCacheService, loginMethodRegistry);

    @Test
    @DisplayName("登出：将 jti 写入黑名单，TTL = token 剩余有效期")
    void logout_putsJtiIntoBlacklist_withRemainingTtl() {
        String token = jwtUtil.generate(1L, "admin", 10L, List.of("ADMIN"));
        Claims claims = jwtUtil.parse(token);
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        Result<Void> result = controller.logout(request);

        assertThat(result.isSuccess()).isTrue();
        Duration expectedTtl =
                Duration.between(java.time.Instant.now(), claims.getExpiration().toInstant());
        verify(redisCacheService)
                .set(
                        eq(AuthController.BLACKLIST_KEY_PREFIX + claims.getId()),
                        eq("1"),
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

    @Test
    @DisplayName("登录：method=null 默认路由到 password provider，返回 LoginVO")
    void login_whenMethodNull_defaultsToPassword() {
        LoginMethodProvider provider = mock(LoginMethodProvider.class);
        LoginVO vo = new LoginVO("tok", "Bearer", new UserVO());
        when(provider.authenticate(any(LoginRequest.class))).thenReturn(vo);
        when(loginMethodRegistry.getProvider("password")).thenReturn(provider);

        Result<LoginVO> result =
                controller.login(new LoginRequest(null, "admin", "pw", null));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.data()).isSameAs(vo);
        verify(loginMethodRegistry).getProvider("password");
    }

    @Test
    @DisplayName("登录：method 显式指定为 password 时正确路由")
    void login_whenMethodPassword_routesToPasswordProvider() {
        LoginMethodProvider provider = mock(LoginMethodProvider.class);
        LoginVO vo = new LoginVO("tok", "Bearer", new UserVO());
        when(provider.authenticate(any(LoginRequest.class))).thenReturn(vo);
        when(loginMethodRegistry.getProvider("password")).thenReturn(provider);

        Result<LoginVO> result = controller.login(LoginRequest.password("admin", "pw"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.data().getToken()).isEqualTo("tok");
    }

    @Test
    @DisplayName("登录：method=ldap 路由到 ldap provider（多 provider 验证）")
    void login_whenMethodLdap_routesToLdapProvider() {
        LoginMethodProvider ldapProvider = mock(LoginMethodProvider.class);
        LoginVO ldapLoginVo = new LoginVO("ldap-tok", "Bearer", new UserVO());
        when(ldapProvider.authenticate(any(LoginRequest.class))).thenReturn(ldapLoginVo);
        when(loginMethodRegistry.getProvider("ldap")).thenReturn(ldapProvider);
        LoginRequest request =
                new LoginRequest("ldap", "alice", null, java.util.Map.of("domain", "corp"));

        Result<LoginVO> result = controller.login(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.data().getToken()).isEqualTo("ldap-tok");
        verify(loginMethodRegistry).getProvider("ldap");
        verify(ldapProvider).authenticate(any(LoginRequest.class));
    }

    @Test
    @DisplayName("登录：method 未知 → 400 BizException")
    void login_whenMethodUnknown_throws400() {
        when(loginMethodRegistry.getProvider("unknown")).thenReturn(null);

        assertThatThrownBy(
                        () -> controller.login(new LoginRequest("unknown", "alice", "pw", null)))
                .isInstanceOf(BizException.class)
                .satisfies(
                        ex -> {
                            BizException biz = (BizException) ex;
                            assertThat(biz.getCode()).isEqualTo(400);
                            assertThat(biz.getMessage()).contains("unknown");
                        });
    }

    @Test
    @DisplayName("登录：provider 已禁用 → getProvider 返回 null → 400")
    void login_whenProviderDisabled_throws400() {
        when(loginMethodRegistry.getProvider("ldap")).thenReturn(null);

        assertThatThrownBy(() -> controller.login(new LoginRequest("ldap", "alice", null, null)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo(400));
    }

    @Test
    @DisplayName("login-methods 端点：返回 registry 的已启用方法列表")
    void loginMethods_returnsRegistryEnabledMethods() {
        List<LoginMethodDescriptor> descriptors =
                List.of(
                        new LoginMethodDescriptor("ldap", "LDAP", "ldap-icon", 50),
                        new LoginMethodDescriptor("password", "密码", "pwd-icon", 100));
        when(loginMethodRegistry.getEnabledMethods()).thenReturn(descriptors);

        Result<List<LoginMethodDescriptor>> result = controller.loginMethods();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.data()).hasSize(2);
        assertThat(result.data().get(0).method()).isEqualTo("ldap");
    }
}

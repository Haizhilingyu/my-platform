package com.example.loginldap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.exception.BizException;
import com.example.common.login.LoginMethodDescriptor;
import com.example.common.login.LoginRequest;
import com.example.common.login.LoginResult;
import com.example.common.login.LoginSuccessEvent;
import com.example.common.security.JwtUtil;
import com.example.sys.domain.SysRole;
import com.example.sys.domain.SysUser;
import com.example.sys.dto.LoginVO;
import com.example.sys.repository.SysRoleRepository;
import com.example.sys.repository.SysUserRepository;
import com.example.sys.repository.SysUserRoleRepository;
import com.example.sys.service.PermissionService;
import java.util.List;
import java.util.Optional;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.ldap.AuthenticationException;
import org.springframework.ldap.core.ContextSource;

/**
 * LdapLoginProvider 单元测试。mock ContextSource + DirContext，覆盖认证成功/失败、自动建号、
 * 禁用用户、空凭据等场景。无需真实 LDAP 服务器。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LDAP 登录提供者")
class LdapLoginProviderTest {

    private static final String SECRET =
            "012345678901234567890123456789012345678901234567890123456789";
    private static final String DN_PATTERN = "uid={0},dc=devenv,dc=local";

    @Mock private ContextSource contextSource;
    @Mock private SysUserRepository userRepository;
    @Mock private SysRoleRepository roleRepository;
    @Mock private SysUserRoleRepository userRoleRepository;
    @Mock private PermissionService permissionService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 3600_000L);
    private final LdapLoginProperties properties = new LdapLoginProperties();

    private LdapLoginProvider provider;

    @BeforeEach
    void setUp() {
        properties.setEnabled(true);
        properties.setUserDnPattern(DN_PATTERN);
        properties.setAutoCreateUser(true);
        properties.setDefaultRoleCode("user");
        provider =
                new LdapLoginProvider(
                        properties,
                        contextSource,
                        userRepository,
                        roleRepository,
                        userRoleRepository,
                        permissionService,
                        jwtUtil,
                        eventPublisher);
    }

    // ==================== 描述符 / 启用状态 ====================

    @Test
    @DisplayName("describe：返回 ldap 方式 / label / icon / order=20")
    void describe_returnsLdapDescriptor() {
        LoginMethodDescriptor d = provider.describe();
        assertThat(d.method()).isEqualTo("ldap");
        assertThat(d.label()).isEqualTo("LDAP 登录");
        assertThat(d.icon()).isEqualTo("ldap");
        assertThat(d.order()).isEqualTo(20);
    }

    @Test
    @DisplayName("isEnabled：跟随 properties.enabled")
    void isEnabled_reflectsProperties() {
        assertThat(provider.isEnabled()).isTrue();
        properties.setEnabled(false);
        assertThat(provider.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("getMethod：返回 ldap")
    void getMethod_returnsLdap() {
        assertThat(provider.getMethod()).isEqualTo("ldap");
    }

    // ==================== 认证成功（已存在用户） ====================

    @Nested
    @DisplayName("认证成功 - 本地已有用户")
    class AuthSuccessExistingUser {

        @Test
        @DisplayName("LDAP bind 成功 + 本地用户存在 → 返回 LoginVO（含 JWT、Bearer）")
        void should_returnLoginVo_when_bindSuccessAndUserExists() throws Exception {
            // Given — LDAP bind 成功，返回带 mail/cn 属性的 DirContext
            DirContext ctx = mockDirContext("hai@devenv.local", "Hai");
            when(contextSource.getContext("uid=hai,dc=devenv,dc=local", "secret"))
                    .thenReturn(ctx);
            // Given — 本地用户已存在
            SysUser user =
                    SysUser.builder()
                            .id(7L)
                            .username("hai")
                            .status(1)
                            .email("hai@devenv.local")
                            .realName("Hai")
                            .build();
            user.setUnitId(10L);
            when(userRepository.findByUsername("hai")).thenReturn(Optional.of(user));
            when(permissionService.getUserRoleCodes(7L)).thenReturn(java.util.Set.of("user"));

            // When
            LoginResult result =
                    provider.authenticate(new LoginRequest("ldap", "hai", "secret", null));

            // Then
            assertThat(result).isInstanceOf(LoginVO.class);
            LoginVO vo = (LoginVO) result;
            assertThat(vo.getToken()).isNotBlank();
            assertThat(vo.getTokenType()).isEqualTo("Bearer");
            assertThat(vo.getUser().getUsername()).isEqualTo("hai");
            // 不自动建号
            verify(userRepository, never()).save(any());
            verify(userRoleRepository, never()).save(any());
            // 发布登录成功事件
            ArgumentCaptor<LoginSuccessEvent> eventCaptor =
                    ArgumentCaptor.forClass(LoginSuccessEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().userId()).isEqualTo(7L);
            assertThat(eventCaptor.getValue().username()).isEqualTo("hai");
        }
    }

    // ==================== 认证成功（自动建号） ====================

    @Nested
    @DisplayName("认证成功 - 自动建号")
    class AuthSuccessAutoCreate {

        @Test
        @DisplayName("本地无用户 + auto-create=true → 用 LDAP 属性建号 + 分配默认角色")
        void should_createLocalUser_when_notFoundAndAutoCreateEnabled() throws Exception {
            // Given
            DirContext ctx = mockDirContext("newldap@devenv.local", "New Ldap");
            when(contextSource.getContext("uid=newldap,dc=devenv,dc=local", "pw"))
                    .thenReturn(ctx);
            when(userRepository.findByUsername("newldap")).thenReturn(Optional.empty());
            SysUser saved =
                    SysUser.builder()
                            .id(99L)
                            .username("newldap")
                            .status(1)
                            .email("newldap@devenv.local")
                            .realName("New Ldap")
                            .build();
            when(userRepository.save(any(SysUser.class))).thenReturn(saved);
            SysRole defaultRole =
                    SysRole.builder().id(5L).roleCode("user").roleName("普通用户").status(1).build();
            when(roleRepository.findByStatus(1)).thenReturn(List.of(defaultRole));
            when(permissionService.getUserRoleCodes(99L)).thenReturn(java.util.Set.of("user"));

            // When
            LoginVO vo =
                    (LoginVO)
                            provider.authenticate(
                                    new LoginRequest("ldap", "newldap", "pw", null));

            // Then — 建号信息来自 LDAP
            ArgumentCaptor<SysUser> userCaptor = ArgumentCaptor.forClass(SysUser.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getUsername()).isEqualTo("newldap");
            assertThat(userCaptor.getValue().getEmail()).isEqualTo("newldap@devenv.local");
            assertThat(userCaptor.getValue().getRealName()).isEqualTo("New Ldap");
            assertThat(userCaptor.getValue().getStatus()).isEqualTo(1);
            // 密码为随机占位值（非空、非明文）
            assertThat(userCaptor.getValue().getPassword()).isNotBlank().isNotEqualTo("pw");

            // Then — 分配默认角色 user
            ArgumentCaptor<com.example.sys.domain.SysUserRole> roleCaptor =
                    ArgumentCaptor.forClass(com.example.sys.domain.SysUserRole.class);
            verify(userRoleRepository).save(roleCaptor.capture());
            assertThat(roleCaptor.getValue().getUserId()).isEqualTo(99L);
            assertThat(roleCaptor.getValue().getRoleId()).isEqualTo(5L);

            // Then — 返回有效 token
            assertThat(vo.getToken()).isNotBlank();
        }

        @Test
        @DisplayName("LDAP 无 cn 属性 → realName 回退为用户名")
        void should_fallbackRealNameToUsername_when_cnMissing() throws Exception {
            DirContext ctx = mockDirContext("x@devenv.local", null);
            when(contextSource.getContext("uid=x,dc=devenv,dc=local", "pw")).thenReturn(ctx);
            when(userRepository.findByUsername("x")).thenReturn(Optional.empty());
            SysUser saved = SysUser.builder().id(1L).username("x").status(1).build();
            when(userRepository.save(any(SysUser.class))).thenReturn(saved);
            when(roleRepository.findByStatus(1)).thenReturn(List.of());
            when(permissionService.getUserRoleCodes(1L)).thenReturn(java.util.Set.of());

            provider.authenticate(new LoginRequest("ldap", "x", "pw", null));

            ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getRealName()).isEqualTo("x");
        }
    }

    // ==================== 认证失败 ====================

    @Nested
    @DisplayName("认证失败")
    class AuthFailure {

        @Test
        @DisplayName("LDAP bind 抛 AuthenticationException → BizException 401")
        void should_throw401_when_ldapBindFails() {
            when(contextSource.getContext(anyString(), anyString()))
                    .thenThrow(
                            new AuthenticationException(
                                    new javax.naming.AuthenticationException(
                                            "invalid credentials")));

            assertThatThrownBy(
                            () ->
                                    provider.authenticate(
                                            new LoginRequest("ldap", "hai", "wrong", null)))
                    .isInstanceOf(BizException.class)
                    .satisfies(
                            ex -> {
                                BizException biz = (BizException) ex;
                                assertThat(biz.getCode()).isEqualTo(401);
                                assertThat(biz.getMessage()).contains("LDAP 认证失败");
                            });
            // 不应建号 / 不应发布事件
            verify(userRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("本地无用户 + auto-create=false → BizException 401")
        void should_throw401_when_userNotFoundAndAutoCreateDisabled() throws Exception {
            properties.setAutoCreateUser(false);
            DirContext ctx = mockDirContext(null, null);
            when(contextSource.getContext("uid=ghost,dc=devenv,dc=local", "pw")).thenReturn(ctx);
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(
                            () ->
                                    provider.authenticate(
                                            new LoginRequest("ldap", "ghost", "pw", null)))
                    .isInstanceOf(BizException.class)
                    .satisfies(
                            ex ->
                                    assertThat(((BizException) ex).getCode())
                                            .isEqualTo(401));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("本地用户已禁用 → BizException 403")
        void should_throw403_when_localUserDisabled() throws Exception {
            DirContext ctx = mockDirContext(null, null);
            when(contextSource.getContext("uid=blocked,dc=devenv,dc=local", "pw"))
                    .thenReturn(ctx);
            SysUser disabled =
                    SysUser.builder().id(3L).username("blocked").status(0).build();
            when(userRepository.findByUsername("blocked")).thenReturn(Optional.of(disabled));

            assertThatThrownBy(
                            () ->
                                    provider.authenticate(
                                            new LoginRequest("ldap", "blocked", "pw", null)))
                    .isInstanceOf(BizException.class)
                    .satisfies(
                            ex ->
                                    assertThat(((BizException) ex).getCode())
                                            .isEqualTo(403));
        }

        @Test
        @DisplayName("用户名或密码为空 → BizException 400（不触达 LDAP）")
        void should_throw400_when_credentialsBlank() {
            assertThatThrownBy(
                            () ->
                                    provider.authenticate(
                                            new LoginRequest("ldap", "", "pw", null)))
                    .isInstanceOf(BizException.class)
                    .satisfies(
                            ex ->
                                    assertThat(((BizException) ex).getCode())
                                            .isEqualTo(400));
            assertThatThrownBy(
                            () ->
                                    provider.authenticate(
                                            new LoginRequest("ldap", "hai", null, null)))
                    .isInstanceOf(BizException.class);
            verify(contextSource, never()).getContext(anyString(), anyString());
        }
    }

    // ==================== 辅助 ====================

    /** 构造一个 mock DirContext，其 getAttributes("") 返回含 mail/cn 的 Attributes。 */
    private DirContext mockDirContext(String mail, String cn) throws Exception {
        DirContext ctx = org.mockito.Mockito.mock(DirContext.class);
        Attributes attrs = new BasicAttributes();
        if (mail != null) {
            attrs.put(new BasicAttribute("mail", mail));
        }
        if (cn != null) {
            attrs.put(new BasicAttribute("cn", cn));
        }
        when(ctx.getAttributes("")).thenReturn(attrs);
        return ctx;
    }
}

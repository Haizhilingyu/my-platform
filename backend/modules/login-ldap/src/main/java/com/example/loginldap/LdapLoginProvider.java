package com.example.loginldap;

import com.example.common.exception.BizException;
import com.example.common.login.LoginMethodDescriptor;
import com.example.common.login.LoginMethodProvider;
import com.example.common.login.LoginRequest;
import com.example.common.login.LoginResult;
import com.example.common.login.LoginSuccessEvent;
import com.example.common.security.JwtUtil;
import com.example.sys.domain.SysRole;
import com.example.sys.domain.SysUser;
import com.example.sys.domain.SysUserRole;
import com.example.sys.dto.LoginVO;
import com.example.sys.dto.UserVO;
import com.example.sys.repository.SysRoleRepository;
import com.example.sys.repository.SysUserRepository;
import com.example.sys.repository.SysUserRoleRepository;
import com.example.sys.service.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.ldap.AuthenticationException;
import org.springframework.ldap.core.ContextSource;
import org.springframework.ldap.support.LdapUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * LDAP 登录方式提供者。
 *
 * <p>认证流程：
 * <ol>
 *   <li>用 {@code user-dn-pattern} 拼出用户完整 DN（{@code {0}} 替换为用户名）。</li>
 *   <li>通过 {@link ContextSource#getContext(String, String)} 执行 LDAP bind。
 *       成功即认证通过，{@link AuthenticationException} 即凭据错误（→ 401）。</li>
 *   <li>从认证上下文读取 {@code mail}/{@code cn} 属性，作为自动建号信息。</li>
 *   <li>查本地 SysUser：存在则校验状态；不存在且 {@code auto-create-user=true} 则自动建号
 *       并分配 {@code default-role-code} 角色。</li>
 *   <li>加载角色、生成 JWT（与 PasswordLoginProvider 同构）、发布 LoginSuccessEvent、
 *       返回 LoginVO（结构同密码登录）。</li>
 * </ol>
 *
 * <p>本类只做读认证 + 本地用户 provision，不向 LDAP 写数据。仅当
 * {@code platform.login.ldap.enabled=true} 时由 {@link LdapLoginAutoConfiguration} 注册。
 */
@Slf4j
@RequiredArgsConstructor
public class LdapLoginProvider implements LoginMethodProvider {

    public static final String METHOD = "ldap";
    static final String DN_PLACEHOLDER = "{0}";

    private final LdapLoginProperties properties;
    private final ContextSource contextSource;
    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final SysUserRoleRepository userRoleRepository;
    private final PermissionService permissionService;
    private final JwtUtil jwtUtil;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public String getMethod() {
        return METHOD;
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    @Override
    public int getOrder() {
        return 20;
    }

    @Override
    public LoginMethodDescriptor describe() {
        return new LoginMethodDescriptor(METHOD, "LDAP 登录", "ldap", getOrder());
    }

    @Override
    public LoginResult authenticate(LoginRequest request) {
        String username = request.username();
        String password = request.password();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new BizException(400, "用户名和密码不能为空");
        }

        LdapUserInfo ldapUser = bind(username, password);
        SysUser user = resolveOrCreateUser(ldapUser);
        if (user.getStatus() != 1) {
            throw new BizException(403, "用户已被禁用");
        }

        List<String> roles = List.copyOf(permissionService.getUserRoleCodes(user.getId()));
        String token = jwtUtil.generate(user.getId(), user.getUsername(), user.getUnitId(), roles);

        String jti = jwtUtil.parse(token).getId();
        eventPublisher.publishEvent(
                new LoginSuccessEvent(
                        user.getId(),
                        user.getUsername(),
                        jti,
                        extractIp(),
                        extractUserAgent(),
                        LocalDateTime.now()));

        return new LoginVO(token, "Bearer", UserVO.of(user));
    }

    /** LDAP bind 认证并提取用户属性。认证失败抛 {@link BizException}(401)。 */
    LdapUserInfo bind(String username, String password) {
        String userDn = properties.getUserDnPattern().replace(DN_PLACEHOLDER, username);
        DirContext ctx = null;
        try {
            ctx = contextSource.getContext(userDn, password);
            Attributes attrs = ctx.getAttributes("");
            return new LdapUserInfo(username, attrValue(attrs, "mail"), attrValue(attrs, "cn"));
        } catch (AuthenticationException e) {
            log.warn("LDAP 认证失败: user={}, msg={}", username, e.getMessage());
            throw new BizException(401, "LDAP 认证失败：用户名或密码错误");
        } catch (org.springframework.ldap.NamingException e) {
            log.error("LDAP 连接异常: user={}", username, e);
            throw new BizException(500, "LDAP 服务不可用: " + e.getMessage());
        } catch (NamingException e) {
            log.error("LDAP 读取用户属性失败: user={}", username, e);
            throw new BizException(500, "LDAP 读取用户属性失败");
        } finally {
            LdapUtils.closeContext(ctx);
        }
    }

    private SysUser resolveOrCreateUser(LdapUserInfo ldapUser) {
        Optional<SysUser> existing = userRepository.findByUsername(ldapUser.username());
        if (existing.isPresent()) {
            return existing.get();
        }
        if (!properties.isAutoCreateUser()) {
            throw new BizException(401, "用户不存在且未开启自动创建: " + ldapUser.username());
        }
        return createLocalUser(ldapUser);
    }

    private SysUser createLocalUser(LdapUserInfo ldapUser) {
        SysUser user =
                SysUser.builder()
                        .username(ldapUser.username())
                        // LDAP 用户密码由 LDAP 管理，本地置随机占位值（不可用于本地登录）。
                        .password(UUID.randomUUID().toString())
                        .realName(
                                ldapUser.realName() != null
                                        ? ldapUser.realName()
                                        : ldapUser.username())
                        .email(ldapUser.email())
                        .status(1)
                        .build();
        user = userRepository.save(user);
        assignDefaultRole(user.getId());
        log.info(
                "LDAP 首次登录自动创建本地用户: id={}, username={}", user.getId(), user.getUsername());
        return user;
    }

    private void assignDefaultRole(Long userId) {
        String roleCode = properties.getDefaultRoleCode();
        if (roleCode == null || roleCode.isBlank()) {
            return;
        }
        // SysRoleRepository 未提供按 code 单查，这里从启用角色中过滤（自动建号低频，开销可接受）。
        roleRepository.findByStatus(1).stream()
                .filter(r -> roleCode.equals(r.getRoleCode()))
                .findFirst()
                .map(SysRole::getId)
                .ifPresent(roleId -> userRoleRepository.save(new SysUserRole(userId, roleId)));
    }

    private String attrValue(Attributes attrs, String name) {
        try {
            if (attrs == null || attrs.get(name) == null) {
                return null;
            }
            Object val = attrs.get(name).get();
            return val != null ? String.valueOf(val) : null;
        } catch (NamingException e) {
            return null;
        }
    }

    private String extractIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String real = request.getHeader("X-Real-IP");
        return (real != null && !real.isBlank()) ? real.trim() : request.getRemoteAddr();
    }

    private String extractUserAgent() {
        HttpServletRequest request = currentRequest();
        return request != null ? request.getHeader("User-Agent") : null;
    }

    private HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }
}

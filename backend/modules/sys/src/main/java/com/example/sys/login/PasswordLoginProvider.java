package com.example.sys.login;

import com.example.common.exception.BizException;
import com.example.common.i18n.Messages;
import com.example.common.login.LoginMethodDescriptor;
import com.example.common.login.LoginMethodProvider;
import com.example.common.login.LoginRequest;
import com.example.common.login.LoginResult;
import com.example.common.login.LoginSuccessEvent;
import com.example.common.security.JwtUtil;
import com.example.sys.domain.SysUser;
import com.example.sys.dto.LoginVO;
import com.example.sys.dto.UserVO;
import com.example.sys.service.LoginSecurityService;
import com.example.sys.service.PermissionService;
import com.example.sys.service.UserService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 账号密码登录提供者。
 *
 * <p>从 {@code AuthController.login} 提取的原始密码认证逻辑：查库校验用户状态 → 比对密码哈希 → 加载角色 → 生成 JWT（含 unitId/jti）→ 组装
 * {@link LoginVO} → 发布 {@link LoginSuccessEvent}。
 *
 * <p>{@code order=100} 排在常见扩展方式（如 LDAP=50、SSO=30）之后，作为默认 Tab 展示在最后。
 */
@Component
@RequiredArgsConstructor
public class PasswordLoginProvider implements LoginMethodProvider {

  public static final String METHOD = "password";

  private final UserService userService;
  private final PermissionService permissionService;
  private final PasswordEncoder passwordEncoder;
  private final JwtUtil jwtUtil;
  private final ApplicationEventPublisher eventPublisher;
  private final LoginSecurityService loginSecurityService;

  @Override
  public String getMethod() {
    return METHOD;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public int getOrder() {
    return 100;
  }

  @Override
  public LoginMethodDescriptor describe() {
    return new LoginMethodDescriptor(
        METHOD, Messages.get("login.method.password"), "password-icon", getOrder());
  }

  @Override
  public LoginResult authenticate(LoginRequest request) {
    loginSecurityService.checkLockStatus(request.username());
    SysUser user = userService.getEntityByUsername(request.username());
    if (user.getStatus() != 1) {
      throw new BizException(403, Messages.get("error.user.disabled"));
    }
    if (!passwordEncoder.matches(request.password(), user.getPassword())) {
      loginSecurityService.recordFailedAttempt(request.username());
      throw new BizException(401, Messages.get("error.auth.bad.credentials"));
    }
    loginSecurityService.recordSuccessfulLogin(user.getUsername());

    List<String> roles = List.copyOf(permissionService.getUserRoleCodes(user.getId()));
    String token =
        jwtUtil.generate(
            user.getId(), user.getUsername(), user.getUnitId(), roles, user.getLocale());

    String jti = extractJti(token);
    eventPublisher.publishEvent(
        new LoginSuccessEvent(
            user.getId(),
            user.getUsername(),
            jti,
            extractIp(),
            extractUserAgent(),
            LocalDateTime.now()));

    UserVO vo = UserVO.of(user);
    return new LoginVO(token, "Bearer", vo);
  }

  private String extractJti(String token) {
    Claims claims = jwtUtil.parse(token);
    return claims.getId();
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
    if (real != null && !real.isBlank()) {
      return real.trim();
    }
    return request.getRemoteAddr();
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

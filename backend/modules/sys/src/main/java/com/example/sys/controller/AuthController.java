package com.example.sys.controller;

import com.example.common.audit.Auditable;
import com.example.common.cache.RedisCacheService;
import com.example.common.exception.BizException;
import com.example.common.i18n.Messages;
import com.example.common.login.LoginMethodDescriptor;
import com.example.common.login.LoginMethodProvider;
import com.example.common.login.LoginMethodRegistry;
import com.example.common.login.LoginRequest;
import com.example.common.login.LoginResult;
import com.example.common.result.Result;
import com.example.common.security.CurrentUser;
import com.example.common.security.JwtUtil;
import com.example.sys.domain.SysMenu;
import com.example.sys.dto.CaptchaResult;
import com.example.sys.dto.LoginVO;
import com.example.sys.dto.MenuTreeNode;
import com.example.sys.dto.UserVO;
import com.example.sys.service.CaptchaService;
import com.example.sys.service.ConfigService;
import com.example.sys.service.MenuService;
import com.example.sys.service.PermissionService;
import com.example.sys.service.UserService;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 认证 Controller。负责登录、登出、获取当前用户信息（权限、菜单）。 */
@Tag(name = "认证", description = "登录、登出、获取当前用户权限和菜单")
@RestController
@RequestMapping("/api/sys/auth")
@RequiredArgsConstructor
public class AuthController {

  static final String BLACKLIST_KEY_PREFIX = "jwt:blacklist:";
  static final String DEFAULT_LOGIN_METHOD = "password";
  static final String CAPTCHA_ENABLED_KEY = "sys.security.captcha.enabled";

  private final UserService userService;
  private final PermissionService permissionService;
  private final MenuService menuService;
  private final JwtUtil jwtUtil;
  private final RedisCacheService redisCacheService;
  private final LoginMethodRegistry loginMethodRegistry;
  private final CaptchaService captchaService;
  private final ConfigService configService;

  @Operation(summary = "登录", description = "根据 method 路由到对应 LoginMethodProvider，默认 password")
  @Auditable(action = "LOGIN")
  @PostMapping("/login")
  public Result<LoginVO> login(@RequestBody @Valid LoginRequest request) {
    if (captchaEnabled() && !captchaService.validate(request.captchaId(), request.captchaCode())) {
      if (request.captchaId() == null || request.captchaCode() == null) {
        throw new BizException(400, Messages.get("error.auth.captcha.required"));
      }
      throw new BizException(400, Messages.get("error.auth.captcha.invalid"));
    }
    String method = request.method() != null ? request.method() : DEFAULT_LOGIN_METHOD;
    LoginMethodProvider provider = loginMethodRegistry.getProvider(method);
    if (provider == null) {
      throw new BizException(400, Messages.get("error.auth.method.unsupported", method));
    }
    LoginResult result = provider.authenticate(request);
    return Result.ok((LoginVO) result);
  }

  @Operation(
      summary = "获取图形验证码",
      description = "返回 captchaId 和带 data URI 前缀的 base64 图片，TTL 5 分钟，单次使用")
  @GetMapping("/captcha")
  public Result<CaptchaResult> captcha() {
    return Result.ok(captchaService.generate());
  }

  private boolean captchaEnabled() {
    return "true".equalsIgnoreCase(configService.getValue(CAPTCHA_ENABLED_KEY, "true"));
  }

  @Operation(summary = "获取可用登录方式", description = "返回所有已启用登录方式的描述符，按 order 升序")
  @GetMapping("/login-methods")
  public Result<List<LoginMethodDescriptor>> loginMethods() {
    return Result.ok(loginMethodRegistry.getEnabledMethods());
  }

  @Operation(summary = "登出", description = "将当前 token 的 jti 加入 Redis 黑名单，使其立即失效")
  @PostMapping("/logout")
  public Result<Void> logout(HttpServletRequest request) {
    String token = jwtUtil.extractToken(request.getHeader("Authorization"));
    if (token != null && jwtUtil.isValid(token)) {
      Claims claims = jwtUtil.parse(token);
      String jti = claims.getId();
      if (jti != null) {
        Instant expiry = claims.getExpiration().toInstant();
        long remainingSeconds = Duration.between(Instant.now(), expiry).toSeconds();
        if (remainingSeconds > 0) {
          redisCacheService.set(
              BLACKLIST_KEY_PREFIX + jti, "1", Duration.ofSeconds(remainingSeconds));
        }
      }
    }
    return Result.ok();
  }

  @Operation(summary = "获取当前用户信息")
  @GetMapping("/me")
  public Result<UserVO> me() {
    Long userId = CurrentUser.getUserId();
    if (userId == null) {
      throw new BizException(401, Messages.get("error.auth.not.login"));
    }
    return Result.ok(userService.getById(userId));
  }

  @Operation(summary = "获取当前用户权限列表")
  @GetMapping("/permissions")
  public Result<Set<String>> permissions() {
    return Result.ok(CurrentUser.getPermissions());
  }

  @Operation(summary = "获取当前用户菜单树")
  @GetMapping("/menus")
  public Result<List<MenuTreeNode>> menus() {
    Long userId = CurrentUser.getUserId();
    if (userId == null) {
      throw new BizException(401, Messages.get("error.auth.not.login"));
    }
    List<SysMenu> menus = permissionService.getUserMenus(userId);
    return Result.ok(MenuService.buildTree(menus));
  }
}

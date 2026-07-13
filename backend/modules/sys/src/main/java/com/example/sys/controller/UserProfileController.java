package com.example.sys.controller;

import com.example.common.exception.BizException;
import com.example.common.result.Result;
import com.example.common.security.CurrentUser;
import com.example.common.security.JwtUtil;
import com.example.sys.domain.SysUser;
import com.example.sys.dto.LocaleUpdateDTO;
import com.example.sys.service.PermissionService;
import com.example.sys.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户个人资料 Controller，独立于 {@link UserController}（管理员维度的用户 CRUD）。仅处理「当前登录用户」自身的资料项，无需 {@code
 * sys:user:*} 权限。前端 {@code stores/locale.ts} 通过 {@code http.put('/sys/user/profile/locale',
 * {locale})} 调用。
 */
@Tag(name = "用户资料", description = "当前用户自身的资料管理")
@RestController
@RequestMapping("/api/sys/user/profile")
@RequiredArgsConstructor
public class UserProfileController {

  private final UserService userService;
  private final PermissionService permissionService;
  private final JwtUtil jwtUtil;

  @Operation(summary = "获取当前用户语言偏好")
  @GetMapping("/locale")
  public Result<Map<String, String>> getLocale() {
    SysUser user = userService.getEntityById(requireCurrentUserId());
    return Result.ok(Map.of("locale", user.getLocale()));
  }

  /**
   * 更新当前用户语言偏好，持久化后签发携带新 locale claim 的 JWT 一并返回。前端替换本地 token 后，后续请求的 {@code LocaleContextHolder} 由
   * {@code JwtAuthFilter} 从新 JWT 的 locale claim 解析。
   */
  @Operation(summary = "更新当前用户语言偏好，返回包含新 locale claim 的 JWT")
  @PutMapping("/locale")
  public Result<Map<String, Object>> updateLocale(@Valid @RequestBody LocaleUpdateDTO dto) {
    SysUser user = userService.updateLocale(requireCurrentUserId(), dto.locale());
    List<String> roles = List.copyOf(permissionService.getUserRoleCodes(user.getId()));
    String token =
        jwtUtil.generate(
            user.getId(), user.getUsername(), user.getUnitId(), roles, user.getLocale());
    return Result.ok(Map.of("locale", user.getLocale(), "token", token));
  }

  private Long requireCurrentUserId() {
    Long userId = CurrentUser.getUserId();
    if (userId == null) {
      throw BizException.i18n(401, "error.auth.not.login");
    }
    return userId;
  }
}

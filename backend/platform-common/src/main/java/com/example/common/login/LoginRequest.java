package com.example.common.login;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * 登录请求。统一的登录入参载体，兼容多种登录方式。
 *
 * <p>前端 POST {@code /sys/auth/login} 时，请求体反序列化为此记录。
 *
 * <p>向后兼容：旧请求体 {@code {"username":"...","password":"..."}} 不含 {@code method} 字段， 反序列化后 {@code
 * method() == null}，由 {@code AuthController} 默认为 {@code "password"}。 同理 {@code captchaId}/{@code
 * captchaCode} 缺失时为 null，由 AuthController 按配置决定是否放行。
 *
 * @param method 登录方式标识，如 "password"、"ldap"。为 null 时默认 "password"。
 * @param username 用户名（密码登录必填）。
 * @param password 密码明文（密码登录必填，HTTPS 传输）。
 * @param captchaId 图形验证码标识（{@code GET /sys/auth/captcha} 返回）。开启验证码时必填。
 * @param captchaCode 用户识别图形验证码后的答案。开启验证码时必填。
 * @param attributes 扩展属性映射，承载非通用字段：LDAP 域、SSO token 等。 由各 {@link LoginMethodProvider} 自行解析所需的键。
 */
public record LoginRequest(
    @Size(max = 32, message = "登录方式长度不能超过32") String method,
    @NotBlank(message = "用户名不能为空") @Size(max = 32, message = "用户名长度不能超过32") String username,
    @NotBlank(message = "密码不能为空") @Size(max = 32, message = "密码长度不能超过32") String password,
    @Size(max = 64, message = "验证码标识长度不能超过64") String captchaId,
    @Size(max = 6, message = "验证码长度不能超过6") String captchaCode,
    Map<String, Object> attributes) {

  /** 便捷工厂：构造密码登录请求（method="password"，无验证码，无扩展属性）。 */
  public static LoginRequest password(String username, String password) {
    return new LoginRequest("password", username, password, null, null, Map.of());
  }

  /**
   * 向后兼容的 4 参构造器。captchaId/captchaCode 默认 null。
   *
   * <p>保留此构造器是为了让历史调用点（如 {@code new LoginRequest(method, user, pw, attrs)}） 无需改动；record 的规范构造器已扩展为
   * 6 参。
   */
  public LoginRequest(
      String method, String username, String password, Map<String, Object> attributes) {
    this(method, username, password, null, null, attributes);
  }

  /** 读取扩展属性的字符串值，键不存在时返回 null。 */
  public String attribute(String key) {
    if (attributes == null) {
      return null;
    }
    Object val = attributes.get(key);
    return val != null ? String.valueOf(val) : null;
  }
}

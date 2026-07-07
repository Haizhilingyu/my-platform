package com.example.common.login;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("LoginRequest Bean Validation 边界值校验")
class LoginRequestBoundaryTest {

  private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
  private static final Validator VALIDATOR = FACTORY.getValidator();

  private static final String USERNAME_REQUIRED = "用户名不能为空";
  private static final String USERNAME_TOO_LONG = "用户名长度不能超过32";
  private static final String PASSWORD_REQUIRED = "密码不能为空";
  private static final String PASSWORD_TOO_LONG = "密码长度不能超过32";
  private static final String CAPTCHA_TOO_LONG = "验证码长度不能超过6";

  private static String repeat(String s, int n) {
    StringBuilder sb = new StringBuilder(n * s.length());
    for (int i = 0; i < n; i++) {
      sb.append(s);
    }
    return sb.toString();
  }

  private static LoginRequest withUsername(String username) {
    return new LoginRequest("password", username, "admin123", null, null, Map.of());
  }

  private static LoginRequest withPassword(String password) {
    return new LoginRequest("password", "admin", password, null, null, Map.of());
  }

  private static LoginRequest withCaptchaCode(String captchaCode) {
    return new LoginRequest("password", "admin", "admin123", "cap-id", captchaCode, Map.of());
  }

  private void assertViolations(LoginRequest request, int expectedCount, String expectedMessage) {
    var violations = VALIDATOR.validate(request);
    assertThat(violations).hasSize(expectedCount);
    if (expectedMessage != null) {
      assertThat(violations)
          .withFailMessage("期望包含消息: %s，实际: %s", expectedMessage, violationMessages(violations))
          .anySatisfy(v -> assertThat(v.getMessage()).isEqualTo(expectedMessage));
    }
  }

  private static java.util.Collection<String> violationMessages(
      java.util.Set<jakarta.validation.ConstraintViolation<LoginRequest>> violations) {
    return violations.stream().map(jakarta.validation.ConstraintViolation::getMessage).toList();
  }

  @AfterAll
  static void closeFactory() {
    FACTORY.close();
  }

  @Nested
  @DisplayName("username 边界")
  class UsernameBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("username=null → 1违例(用户名不能为空)", withUsername(null), 1, USERNAME_REQUIRED),
          Arguments.of("username=\"\" → 1违例", withUsername(""), 1, USERNAME_REQUIRED),
          Arguments.of(
              "username=空白 → 1违例(@NotBlank 拒绝纯空白)", withUsername("   "), 1, USERNAME_REQUIRED),
          Arguments.of("username=2字符 → 0违例(登录不校验最小长度)", withUsername("ab"), 0, null),
          Arguments.of("username=32字符 → 0违例(刚好上限)", withUsername(repeat("a", 32)), 0, null),
          Arguments.of(
              "username=33字符 → 1违例(超出上限)", withUsername(repeat("a", 33)), 1, USERNAME_TOO_LONG));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void username_boundary(String label, LoginRequest request, int count, String expectedMessage) {
      assertViolations(request, count, expectedMessage);
    }
  }

  @Nested
  @DisplayName("password 边界")
  class PasswordBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("password=null → 1违例(密码不能为空)", withPassword(null), 1, PASSWORD_REQUIRED),
          Arguments.of("password=\"\" → 1违例", withPassword(""), 1, PASSWORD_REQUIRED),
          Arguments.of("password=1字符 → 0违例(登录不校验最小长度)", withPassword("x"), 0, null),
          Arguments.of("password=32字符 → 0违例(刚好上限)", withPassword(repeat("p", 32)), 0, null),
          Arguments.of(
              "password=33字符 → 1违例(超出上限)", withPassword(repeat("p", 33)), 1, PASSWORD_TOO_LONG));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void password_boundary(String label, LoginRequest request, int count, String expectedMessage) {
      assertViolations(request, count, expectedMessage);
    }
  }

  @Nested
  @DisplayName("captchaCode 边界")
  class CaptchaCodeBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("captchaCode=6字符 → 0违例(刚好上限)", withCaptchaCode(repeat("a", 6)), 0, null),
          Arguments.of(
              "captchaCode=7字符 → 1违例(超出上限)", withCaptchaCode(repeat("a", 7)), 1, CAPTCHA_TOO_LONG),
          Arguments.of("captchaCode=null → 0违例(可选)", withCaptchaCode(null), 0, null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void captchaCode_boundary(
        String label, LoginRequest request, int count, String expectedMessage) {
      assertViolations(request, count, expectedMessage);
    }
  }

  @Nested
  @DisplayName("合法请求")
  class ValidRequest {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of(
              "合法请求(admin/admin123) → 0违例",
              new LoginRequest("password", "admin", "admin123", null, null, Map.of()),
              0,
              null),
          Arguments.of("便捷工厂构造的合法请求 → 0违例", LoginRequest.password("admin", "admin123"), 0, null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void valid_request(String label, LoginRequest request, int count, String expectedMessage) {
      assertViolations(request, count, expectedMessage);
    }
  }
}

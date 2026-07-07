package com.example.sys.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("UserCreateDTO Bean Validation 边界值校验")
class UserCreateDTOBoundaryTest {

  private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
  private static final Validator VALIDATOR = FACTORY.getValidator();

  private static final String USERNAME_REQUIRED = "用户名不能为空";
  private static final String USERNAME_SIZE = "用户名长度需在3-32之间";
  private static final String USERNAME_PATTERN = "用户名只能包含字母、数字、下划线";
  private static final String PASSWORD_REQUIRED = "密码不能为空";
  private static final String PASSWORD_SIZE = "密码长度需在6-32之间";
  private static final String REALNAME_SIZE = "姓名长度不能超过50";
  private static final String EMAIL_FORMAT = "邮箱格式不正确";
  private static final String EMAIL_SIZE = "邮箱长度不能超过100";
  private static final String PHONE_FORMAT = "手机号格式不正确";

  private static String repeat(String s, int n) {
    StringBuilder sb = new StringBuilder(n * s.length());
    for (int i = 0; i < n; i++) {
      sb.append(s);
    }
    return sb.toString();
  }

  private static UserCreateDTO create() {
    UserCreateDTO dto = new UserCreateDTO();
    dto.setUsername("validuser");
    dto.setPassword("password123");
    return dto;
  }

  private static Set<String> messages(
      Set<jakarta.validation.ConstraintViolation<UserCreateDTO>> v) {
    return v.stream()
        .map(jakarta.validation.ConstraintViolation::getMessage)
        .collect(java.util.stream.Collectors.toSet());
  }

  private void assertViolations(UserCreateDTO dto, int expectedCount, String expectedMessage) {
    var violations = VALIDATOR.validate(dto);
    assertThat(violations)
        .withFailMessage(
            "期望 %d 个违例，实际 %d 个: %s", expectedCount, violations.size(), messages(violations))
        .hasSize(expectedCount);
    if (expectedMessage != null) {
      assertThat(messages(violations)).contains(expectedMessage);
    }
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
          Arguments.of("username=null → 1违例(@NotBlank)", withUsername(null), 1, USERNAME_REQUIRED),
          Arguments.of(
              "username=\"\" → 3违例(NotBlank+Size+Pattern)", withUsername(""), 3, USERNAME_REQUIRED),
          Arguments.of("username=2字符 → 1违例(低于最小长度3)", withUsername("ab"), 1, USERNAME_SIZE),
          Arguments.of("username=3字符 → 0违例(最小长度)", withUsername("abc"), 0, null),
          Arguments.of("username=32字符 → 0违例(最大长度)", withUsername(repeat("a", 32)), 0, null),
          Arguments.of(
              "username=33字符 → 1违例(超出最大长度)", withUsername(repeat("a", 33)), 1, USERNAME_SIZE),
          Arguments.of("username=含下划线 → 0违例", withUsername("abc_def"), 0, null),
          Arguments.of(
              "username=含连字符 → 1违例(Pattern)", withUsername("abc-def"), 1, USERNAME_PATTERN),
          Arguments.of("username=中文 → 2违例(Size+Pattern)", withUsername("用户"), 2, USERNAME_PATTERN),
          Arguments.of("username=字母数字 → 0违例", withUsername("abc123"), 0, null));
    }

    private static UserCreateDTO withUsername(String username) {
      UserCreateDTO dto = create();
      dto.setUsername(username);
      return dto;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void username_boundary(String label, UserCreateDTO dto, int count, String expectedMessage) {
      assertViolations(dto, count, expectedMessage);
    }
  }

  @Nested
  @DisplayName("password 边界")
  class PasswordBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("password=null → 1违例(@NotBlank)", withPassword(null), 1, PASSWORD_REQUIRED),
          Arguments.of(
              "password=\"\" → 2违例(NotBlank+Size)", withPassword(""), 2, PASSWORD_REQUIRED),
          Arguments.of("password=5字符 → 1违例(低于最小长度6)", withPassword("12345"), 1, PASSWORD_SIZE),
          Arguments.of("password=6字符 → 0违例(最小长度)", withPassword("123456"), 0, null),
          Arguments.of("password=32字符 → 0违例(最大长度)", withPassword(repeat("p", 32)), 0, null),
          Arguments.of(
              "password=33字符 → 1违例(超出最大长度)", withPassword(repeat("p", 33)), 1, PASSWORD_SIZE));
    }

    private static UserCreateDTO withPassword(String password) {
      UserCreateDTO dto = create();
      dto.setPassword(password);
      return dto;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void password_boundary(String label, UserCreateDTO dto, int count, String expectedMessage) {
      assertViolations(dto, count, expectedMessage);
    }
  }

  @Nested
  @DisplayName("realName 边界（可选）")
  class RealNameBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("realName=null → 0违例(可选)", withRealName(null), 0, null),
          Arguments.of("realName=50字符 → 0违例(最大长度)", withRealName(repeat("张", 50)), 0, null),
          Arguments.of(
              "realName=51字符 → 1违例(超出最大长度)", withRealName(repeat("张", 51)), 1, REALNAME_SIZE));
    }

    private static UserCreateDTO withRealName(String realName) {
      UserCreateDTO dto = create();
      dto.setRealName(realName);
      return dto;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void realName_boundary(String label, UserCreateDTO dto, int count, String expectedMessage) {
      assertViolations(dto, count, expectedMessage);
    }
  }

  @Nested
  @DisplayName("email 边界（可选）")
  class EmailBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("email=null → 0违例(可选)", withEmail(null), 0, null),
          Arguments.of("email=合法格式 → 0违例", withEmail("a@b.com"), 0, null),
          Arguments.of("email=非法格式 → 1违例(@Email)", withEmail("invalid"), 1, EMAIL_FORMAT),
          Arguments.of(
              "email=101字符(格式合法) → 1违例(@Size)", withEmail(longValidEmail()), 1, EMAIL_SIZE));
    }

    private static UserCreateDTO withEmail(String email) {
      UserCreateDTO dto = create();
      dto.setEmail(email);
      return dto;
    }

    private static String longValidEmail() {
      return repeat("a", 50) + "@" + repeat("b", 30) + "." + repeat("c", 15) + ".com";
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void email_boundary(String label, UserCreateDTO dto, int count, String expectedMessage) {
      assertViolations(dto, count, expectedMessage);
    }
  }

  @Nested
  @DisplayName("phone 边界（可选）")
  class PhoneBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("phone=null → 0违例(可选)", withPhone(null), 0, null),
          Arguments.of("phone=13800138000 → 0违例(合法)", withPhone("13800138000"), 0, null),
          Arguments.of(
              "phone=12345678901(非1[3-9]开头) → 1违例", withPhone("12345678901"), 1, PHONE_FORMAT),
          Arguments.of("phone=10位数字 → 1违例(位数不符)", withPhone("1380013800"), 1, PHONE_FORMAT),
          Arguments.of("phone=非数字 → 1违例", withPhone("abc"), 1, PHONE_FORMAT));
    }

    private static UserCreateDTO withPhone(String phone) {
      UserCreateDTO dto = create();
      dto.setPhone(phone);
      return dto;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void phone_boundary(String label, UserCreateDTO dto, int count, String expectedMessage) {
      assertViolations(dto, count, expectedMessage);
    }
  }
}

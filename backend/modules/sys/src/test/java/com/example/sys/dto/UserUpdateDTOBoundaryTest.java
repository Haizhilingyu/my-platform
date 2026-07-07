package com.example.sys.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("UserUpdateDTO Bean Validation 边界值校验（字段全可选）")
class UserUpdateDTOBoundaryTest {

  private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
  private static final Validator VALIDATOR = FACTORY.getValidator();

  private static final String REALNAME_SIZE = "姓名长度不能超过50";
  private static final String EMAIL_FORMAT = "邮箱格式不正确";
  private static final String PHONE_FORMAT = "手机号格式不正确";
  private static final String STATUS_INVALID = "状态值非法";
  private static final String REMARK_SIZE = "备注长度不能超过200";

  private static String repeat(String s, int n) {
    StringBuilder sb = new StringBuilder(n * s.length());
    for (int i = 0; i < n; i++) {
      sb.append(s);
    }
    return sb.toString();
  }

  private static Set<String> messages(
      Set<jakarta.validation.ConstraintViolation<UserUpdateDTO>> v) {
    return v.stream()
        .map(jakarta.validation.ConstraintViolation::getMessage)
        .collect(Collectors.toSet());
  }

  private void assertViolations(UserUpdateDTO dto, int expectedCount, String expectedMessage) {
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
  @DisplayName("空 DTO 与单字段场景")
  class SingleField {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("空 DTO(全 null) → 0违例", new UserUpdateDTO(), 0, null),
          Arguments.of("email=非法 → 1违例(@Email)", emailDto("invalid"), 1, EMAIL_FORMAT),
          Arguments.of("email=合法 → 0违例", emailDto("a@b.com"), 0, null),
          Arguments.of("phone=12345(格式错误) → 1违例", phoneDto("12345"), 1, PHONE_FORMAT),
          Arguments.of("phone=13800138000 → 0违例", phoneDto("13800138000"), 0, null),
          Arguments.of("realName=51字符 → 1违例", realNameDto(repeat("张", 51)), 1, REALNAME_SIZE),
          Arguments.of("remark=201字符 → 1违例", remarkDto(repeat("a", 201)), 1, REMARK_SIZE));
    }

    private static UserUpdateDTO emailDto(String email) {
      UserUpdateDTO dto = new UserUpdateDTO();
      dto.setEmail(email);
      return dto;
    }

    private static UserUpdateDTO phoneDto(String phone) {
      UserUpdateDTO dto = new UserUpdateDTO();
      dto.setPhone(phone);
      return dto;
    }

    private static UserUpdateDTO realNameDto(String realName) {
      UserUpdateDTO dto = new UserUpdateDTO();
      dto.setRealName(realName);
      return dto;
    }

    private static UserUpdateDTO remarkDto(String remark) {
      UserUpdateDTO dto = new UserUpdateDTO();
      dto.setRemark(remark);
      return dto;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void single_field(String label, UserUpdateDTO dto, int count, String expectedMessage) {
      assertViolations(dto, count, expectedMessage);
    }
  }

  @Nested
  @DisplayName("status 边界（0/1 合法）")
  class StatusBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("status=-1 → 1违例(@Min)", statusDto(-1), 1, STATUS_INVALID),
          Arguments.of("status=0 → 0违例", statusDto(0), 0, null),
          Arguments.of("status=1 → 0违例", statusDto(1), 0, null),
          Arguments.of("status=2 → 1违例(@Max)", statusDto(2), 1, STATUS_INVALID),
          Arguments.of("status=null → 0违例(可选)", statusDto(null), 0, null));
    }

    private static UserUpdateDTO statusDto(Integer status) {
      UserUpdateDTO dto = new UserUpdateDTO();
      dto.setStatus(status);
      return dto;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void status_boundary(String label, UserUpdateDTO dto, int count, String expectedMessage) {
      assertViolations(dto, count, expectedMessage);
    }
  }

  @Nested
  @DisplayName("组合校验")
  class Combination {

    static Stream<Arguments> cases() {
      UserUpdateDTO bothInvalid = new UserUpdateDTO();
      bothInvalid.setEmail("invalid");
      bothInvalid.setPhone("12345");
      return Stream.of(Arguments.of("email+phone 同时非法 → 2违例", bothInvalid, 2, null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void combination(String label, UserUpdateDTO dto, int count, String expectedMessage) {
      assertViolations(dto, count, expectedMessage);
      if (count == 2) {
        assertThat(messages(VALIDATOR.validate(dto))).contains(EMAIL_FORMAT, PHONE_FORMAT);
      }
    }
  }
}

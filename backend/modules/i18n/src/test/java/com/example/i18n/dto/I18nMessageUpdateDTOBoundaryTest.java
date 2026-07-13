package com.example.i18n.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("I18nMessageUpdateDTO Bean Validation 边界值校验")
class I18nMessageUpdateDTOBoundaryTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUp() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void tearDown() {
    if (factory != null) {
      factory.close();
    }
  }

  private static final String VALUE_NOT_BLANK = "翻译值不能为空";
  private static final String VALUE_SIZE = "翻译值长度不能超过5000";

  private static String repeat(String s, int n) {
    StringBuilder sb = new StringBuilder(n * s.length());
    for (int i = 0; i < n; i++) {
      sb.append(s);
    }
    return sb.toString();
  }

  private static Set<String> messages(Set<ConstraintViolation<I18nMessageUpdateDTO>> v) {
    return v.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
  }

  private void assertViolations(
      I18nMessageUpdateDTO dto, int expectedCount, String expectedMessage) {
    var violations = validator.validate(dto);
    assertThat(violations)
        .withFailMessage(
            "期望 %d 个违例，实际 %d 个: %s", expectedCount, violations.size(), messages(violations))
        .hasSize(expectedCount);
    if (expectedMessage != null) {
      assertThat(messages(violations)).contains(expectedMessage);
    }
  }

  @Nested
  @DisplayName("value 边界")
  class ValueBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("value=null → 1违例(@NotBlank)", withValue(null), 1, VALUE_NOT_BLANK),
          Arguments.of("value=\"\" → 1违例(@NotBlank)", withValue(""), 1, VALUE_NOT_BLANK),
          Arguments.of("value=全空白 → 1违例(@NotBlank)", withValue("   "), 1, VALUE_NOT_BLANK),
          Arguments.of("value=5000字符 → 0违例(最大长度)", withValue(repeat("a", 5000)), 0, null),
          Arguments.of("value=5001字符 → 1违例(超出最大长度)", withValue(repeat("a", 5001)), 1, VALUE_SIZE));
    }

    private static I18nMessageUpdateDTO withValue(String value) {
      I18nMessageUpdateDTO dto = new I18nMessageUpdateDTO();
      dto.setValue(value);
      return dto;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void value_boundary(String label, I18nMessageUpdateDTO dto, int count, String expectedMessage) {
      assertViolations(dto, count, expectedMessage);
    }
  }

  @Test
  @DisplayName("合法值 → 0违例")
  void fullyValid() {
    I18nMessageUpdateDTO dto = new I18nMessageUpdateDTO();
    dto.setValue("用户管理");
    assertThat(validator.validate(dto)).isEmpty();
  }
}

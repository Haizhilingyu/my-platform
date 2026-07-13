package com.example.i18n.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.common.i18n.ValidationTestHelper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("I18nMessageImportDTO Bean Validation 边界值校验")
class I18nMessageImportDTOBoundaryTest {

  private static final Validator VALIDATOR = ValidationTestHelper.validatorWithMessages();

  private static final String KEY_NOT_BLANK = "消息 key 不能为空";
  private static final String KEY_SIZE = "消息 key 长度不能超过200";
  private static final String VALUE_NOT_BLANK = "翻译值不能为空";

  private static String repeat(String s, int n) {
    StringBuilder sb = new StringBuilder(n * s.length());
    for (int i = 0; i < n; i++) {
      sb.append(s);
    }
    return sb.toString();
  }

  private static I18nMessageImportDTO.Item validItem() {
    I18nMessageImportDTO.Item item = new I18nMessageImportDTO.Item();
    item.setMessageKey("sys.menu.1.name");
    item.setValue("系统管理");
    return item;
  }

  private static I18nMessageImportDTO validDto() {
    I18nMessageImportDTO dto = new I18nMessageImportDTO();
    dto.setLocale("zh-CN");
    dto.setItems(List.of(validItem()));
    return dto;
  }

  private static Set<String> messages(Set<ConstraintViolation<I18nMessageImportDTO>> v) {
    return v.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
  }

  @Nested
  @DisplayName("locale 边界")
  class LocaleBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("locale=null → 1违例(@NotBlank)", withLocale(null), 1),
          Arguments.of("locale=空白 → 2违例(@NotBlank+@Pattern)", withLocale("  "), 2),
          Arguments.of("locale=fr → 1违例(@Pattern)", withLocale("fr"), 1),
          Arguments.of("locale=en-US → 1违例(@Pattern 仅支持 zh-CN/en)", withLocale("en-US"), 1),
          Arguments.of("locale=zh-CN → 0违例", withLocale("zh-CN"), 0),
          Arguments.of("locale=en → 0违例", withLocale("en"), 0));
    }

    private static I18nMessageImportDTO withLocale(String locale) {
      I18nMessageImportDTO dto = validDto();
      dto.setLocale(locale);
      return dto;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void locale_boundary(String label, I18nMessageImportDTO dto, int expectedCount) {
      var violations = VALIDATOR.validate(dto);
      assertThat(violations)
          .withFailMessage(
              "期望 %d 个违例，实际 %d 个: %s", expectedCount, violations.size(), messages(violations))
          .hasSize(expectedCount);
    }
  }

  @Nested
  @DisplayName("items 边界")
  class ItemsBoundary {

    @Test
    @DisplayName("items=null → 1违例(@NotNull)")
    void items_null() {
      I18nMessageImportDTO dto = validDto();
      dto.setItems(null);
      assertThat(VALIDATOR.validate(dto)).hasSize(1);
    }

    @Test
    @DisplayName("items=空列表 → 1违例(@NotEmpty)")
    void items_empty() {
      I18nMessageImportDTO dto = validDto();
      dto.setItems(Collections.emptyList());
      assertThat(VALIDATOR.validate(dto)).hasSize(1);
    }
  }

  @Nested
  @DisplayName("Item.messageKey 边界")
  class ItemMessageKeyBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("messageKey=null → 1违例(@NotBlank)", withKey(null), 1, KEY_NOT_BLANK),
          Arguments.of("messageKey=\"\" → 1违例(@NotBlank)", withKey(""), 1, KEY_NOT_BLANK),
          Arguments.of("messageKey=200字符 → 0违例", withKey(repeat("a", 200)), 0, null),
          Arguments.of("messageKey=201字符 → 1违例(超出最大长度)", withKey(repeat("a", 201)), 1, KEY_SIZE));
    }

    private static I18nMessageImportDTO withKey(String key) {
      I18nMessageImportDTO.Item item = new I18nMessageImportDTO.Item();
      item.setMessageKey(key);
      item.setValue("v");
      I18nMessageImportDTO dto = validDto();
      dto.setItems(List.of(item));
      return dto;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void messageKey_boundary(String label, I18nMessageImportDTO dto, int count, String expected) {
      var violations = VALIDATOR.validate(dto);
      assertThat(violations)
          .withFailMessage("期望 %d 个违例，实际 %d 个: %s", count, violations.size(), messages(violations))
          .hasSize(count);
      if (expected != null) {
        assertThat(messages(violations)).contains(expected);
      }
    }
  }

  @Nested
  @DisplayName("Item.value 边界")
  class ItemValueBoundary {

    @Test
    @DisplayName("value=null → 1违例(@NotBlank)")
    void value_null() {
      I18nMessageImportDTO.Item item = new I18nMessageImportDTO.Item();
      item.setMessageKey("k");
      item.setValue(null);
      I18nMessageImportDTO dto = validDto();
      dto.setItems(List.of(item));
      assertThat(VALIDATOR.validate(dto)).hasSize(1);
    }

    @Test
    @DisplayName("value=空白 → 1违例(@NotBlank)")
    void value_blank() {
      I18nMessageImportDTO.Item item = new I18nMessageImportDTO.Item();
      item.setMessageKey("k");
      item.setValue("   ");
      I18nMessageImportDTO dto = validDto();
      dto.setItems(List.of(item));
      var violations = VALIDATOR.validate(dto);
      assertThat(violations).hasSize(1);
      assertThat(messages(violations)).contains(VALUE_NOT_BLANK);
    }
  }

  @Test
  @DisplayName("全部字段合法 → 0违例")
  void fullyValid() {
    assertThat(VALIDATOR.validate(validDto())).isEmpty();
  }
}

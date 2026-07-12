package com.example.sys.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.common.i18n.ValidationTestHelper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("ConfigDTO Bean Validation 边界值校验")
class ConfigDTOBoundaryTest {

  private static final Validator VALIDATOR = ValidationTestHelper.validatorWithMessages();

  private static final String CONFIGKEY_REQUIRED = "配置键不能为空";
  private static final String CONFIGKEY_SIZE = "配置键长度不能超过100";
  private static final String CONFIGKEY_PATTERN = "配置键只能包含字母、数字、点、下划线、连字符";
  private static final String CONFIGVALUE_SIZE = "配置值长度不能超过2000";
  private static final String CONFIGTYPE_SIZE = "配置类型长度不能超过50";
  private static final String DESCRIPTION_SIZE = "描述长度不能超过500";
  private static final String CATEGORY_SIZE = "分类长度不能超过50";

  private static String repeat(String s, int n) {
    StringBuilder sb = new StringBuilder(n * s.length());
    for (int i = 0; i < n; i++) {
      sb.append(s);
    }
    return sb.toString();
  }

  private static ConfigDTO create() {
    ConfigDTO dto = new ConfigDTO();
    dto.setConfigKey("sys.config.key");
    return dto;
  }

  private static Set<String> messages(Set<ConstraintViolation<ConfigDTO>> v) {
    return v.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
  }

  private void assertViolations(ConfigDTO dto, int expectedCount, String expectedMessage) {
    var violations = VALIDATOR.validate(dto);
    assertThat(violations)
        .withFailMessage(
            "期望 %d 个违例，实际 %d 个: %s", expectedCount, violations.size(), messages(violations))
        .hasSize(expectedCount);
    if (expectedMessage != null) {
      assertThat(messages(violations)).contains(expectedMessage);
    }
  }

  @Nested
  @DisplayName("configKey 边界")
  class ConfigKeyBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of(
              "configKey=null → 1违例(@NotBlank)", withConfigKey(null), 1, CONFIGKEY_REQUIRED),
          Arguments.of(
              "configKey=\"\" → 2违例(NotBlank+Pattern)", withConfigKey(""), 2, CONFIGKEY_REQUIRED),
          Arguments.of("configKey=含点 → 0违例", withConfigKey("sys.config"), 0, null),
          Arguments.of("configKey=含下划线 → 0违例", withConfigKey("config_key"), 0, null),
          Arguments.of("configKey=含连字符 → 0违例", withConfigKey("config-key"), 0, null),
          Arguments.of(
              "configKey=含空格 → 1违例(Pattern)", withConfigKey("config key"), 1, CONFIGKEY_PATTERN),
          Arguments.of("configKey=100字符 → 0违例(最大长度)", withConfigKey(repeat("a", 100)), 0, null),
          Arguments.of(
              "configKey=101字符 → 1违例(超出最大长度)", withConfigKey(repeat("a", 101)), 1, CONFIGKEY_SIZE),
          Arguments.of(
              "configKey=含@ → 1违例(Pattern)", withConfigKey("config@key"), 1, CONFIGKEY_PATTERN));
    }

    private static ConfigDTO withConfigKey(String configKey) {
      ConfigDTO dto = create();
      dto.setConfigKey(configKey);
      return dto;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void configKey_boundary(String label, ConfigDTO dto, int count, String expectedMessage) {
      assertViolations(dto, count, expectedMessage);
    }
  }

  @Nested
  @DisplayName("configValue 边界（可选）")
  class ConfigValueBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("configValue=null → 0违例(可选)", withConfigValue(null), 0, null),
          Arguments.of(
              "configValue=2000字符 → 0违例(最大长度)", withConfigValue(repeat("a", 2000)), 0, null),
          Arguments.of(
              "configValue=2001字符 → 1违例(超出最大长度)",
              withConfigValue(repeat("a", 2001)),
              1,
              CONFIGVALUE_SIZE));
    }

    private static ConfigDTO withConfigValue(String configValue) {
      ConfigDTO dto = create();
      dto.setConfigValue(configValue);
      return dto;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void configValue_boundary(String label, ConfigDTO dto, int count, String expectedMessage) {
      assertViolations(dto, count, expectedMessage);
    }
  }

  @Nested
  @DisplayName("configType 边界（可选）")
  class ConfigTypeBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("configType=null → 0违例(可选)", withConfigType(null), 0, null),
          Arguments.of("configType=50字符 → 0违例(最大长度)", withConfigType(repeat("a", 50)), 0, null),
          Arguments.of(
              "configType=51字符 → 1违例(超出最大长度)",
              withConfigType(repeat("a", 51)),
              1,
              CONFIGTYPE_SIZE));
    }

    private static ConfigDTO withConfigType(String configType) {
      ConfigDTO dto = create();
      dto.setConfigType(configType);
      return dto;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void configType_boundary(String label, ConfigDTO dto, int count, String expectedMessage) {
      assertViolations(dto, count, expectedMessage);
    }
  }

  @Nested
  @DisplayName("description 边界（可选）")
  class DescriptionBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("description=null → 0违例(可选)", withDescription(null), 0, null),
          Arguments.of("description=500字符 → 0违例(最大长度)", withDescription(repeat("a", 500)), 0, null),
          Arguments.of(
              "description=501字符 → 1违例(超出最大长度)",
              withDescription(repeat("a", 501)),
              1,
              DESCRIPTION_SIZE));
    }

    private static ConfigDTO withDescription(String description) {
      ConfigDTO dto = create();
      dto.setDescription(description);
      return dto;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void description_boundary(String label, ConfigDTO dto, int count, String expectedMessage) {
      assertViolations(dto, count, expectedMessage);
    }
  }

  @Nested
  @DisplayName("category 边界（可选）")
  class CategoryBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("category=null → 0违例(可选)", withCategory(null), 0, null),
          Arguments.of("category=50字符 → 0违例(最大长度)", withCategory(repeat("a", 50)), 0, null),
          Arguments.of(
              "category=51字符 → 1违例(超出最大长度)", withCategory(repeat("a", 51)), 1, CATEGORY_SIZE));
    }

    private static ConfigDTO withCategory(String category) {
      ConfigDTO dto = create();
      dto.setCategory(category);
      return dto;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void category_boundary(String label, ConfigDTO dto, int count, String expectedMessage) {
      assertViolations(dto, count, expectedMessage);
    }
  }

  @Test
  @DisplayName("全部字段合法 → 0违例")
  void fullyValid() {
    ConfigDTO dto = new ConfigDTO();
    dto.setConfigKey("sys.config.key");
    dto.setConfigValue("value");
    dto.setConfigType("STRING");
    dto.setDescription("描述");
    dto.setCategory("system");
    assertThat(VALIDATOR.validate(dto)).isEmpty();
  }
}

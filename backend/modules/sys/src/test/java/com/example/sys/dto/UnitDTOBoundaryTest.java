package com.example.sys.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("UnitDTO Bean Validation 边界值校验")
class UnitDTOBoundaryTest {

  private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
  private static final Validator VALIDATOR = FACTORY.getValidator();

  private static final String UNITCODE_REQUIRED = "单位编码不能为空";
  private static final String UNITCODE_SIZE = "单位编码长度需在3-50之间";
  private static final String UNITCODE_PATTERN = "单位编码只能包含字母、数字、下划线";
  private static final String UNITNAME_REQUIRED = "单位名称不能为空";
  private static final String UNITNAME_SIZE = "单位名称长度不能超过100";
  private static final String SORT_MIN = "排序值不能为负数";
  private static final String STATUS_INVALID = "状态值非法";
  private static final String REMARK_SIZE = "备注长度不能超过200";

  private static String repeat(String s, int n) {
    StringBuilder sb = new StringBuilder(n * s.length());
    for (int i = 0; i < n; i++) {
      sb.append(s);
    }
    return sb.toString();
  }

  private static UnitDTO create() {
    UnitDTO dto = new UnitDTO();
    dto.setUnitCode("valid_unit");
    dto.setUnitName("测试单位");
    return dto;
  }

  private static Set<String> messages(Set<ConstraintViolation<UnitDTO>> v) {
    return v.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
  }

  private void assertViolations(UnitDTO dto, int expectedCount, String expectedMessage) {
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
  @DisplayName("unitCode 边界")
  class UnitCodeBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("unitCode=null → 1违例(@NotBlank)", withUnitCode(null), 1, UNITCODE_REQUIRED),
          Arguments.of(
              "unitCode=\"\" → 3违例(NotBlank+Size+Pattern)", withUnitCode(""), 3, UNITCODE_REQUIRED),
          Arguments.of("unitCode=2字符 → 1违例(低于最小长度3)", withUnitCode("ab"), 1, UNITCODE_SIZE),
          Arguments.of("unitCode=3字符 → 0违例(最小长度)", withUnitCode("abc"), 0, null),
          Arguments.of("unitCode=50字符 → 0违例(最大长度)", withUnitCode(repeat("a", 50)), 0, null),
          Arguments.of(
              "unitCode=51字符 → 1违例(超出最大长度)", withUnitCode(repeat("a", 51)), 1, UNITCODE_SIZE),
          Arguments.of(
              "unitCode=含连字符 → 1违例(Pattern)", withUnitCode("abc-def"), 1, UNITCODE_PATTERN),
          Arguments.of("unitCode=含下划线 → 0违例", withUnitCode("code_1"), 0, null));
    }

    private static UnitDTO withUnitCode(String unitCode) {
      UnitDTO dto = create();
      dto.setUnitCode(unitCode);
      return dto;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void unitCode_boundary(String label, UnitDTO dto, int count, String expectedMessage) {
      assertViolations(dto, count, expectedMessage);
    }
  }

  @Nested
  @DisplayName("unitName 边界")
  class UnitNameBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("unitName=null → 1违例(@NotBlank)", withUnitName(null), 1, UNITNAME_REQUIRED),
          Arguments.of("unitName=\"\" → 1违例(@NotBlank)", withUnitName(""), 1, UNITNAME_REQUIRED),
          Arguments.of("unitName=100字符 → 0违例(最大长度)", withUnitName(repeat("张", 100)), 0, null),
          Arguments.of(
              "unitName=101字符 → 1违例(超出最大长度)", withUnitName(repeat("张", 101)), 1, UNITNAME_SIZE));
    }

    private static UnitDTO withUnitName(String unitName) {
      UnitDTO dto = create();
      dto.setUnitName(unitName);
      return dto;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void unitName_boundary(String label, UnitDTO dto, int count, String expectedMessage) {
      assertViolations(dto, count, expectedMessage);
    }
  }

  @Nested
  @DisplayName("sort 边界（可选）")
  class SortBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("sort=-1 → 1违例(@Min)", withSort(-1), 1, SORT_MIN),
          Arguments.of("sort=0 → 0违例", withSort(0), 0, null),
          Arguments.of("sort=1 → 0违例", withSort(1), 0, null),
          Arguments.of("sort=null → 0违例(可选)", withSort(null), 0, null));
    }

    private static UnitDTO withSort(Integer sort) {
      UnitDTO dto = create();
      dto.setSort(sort);
      return dto;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void sort_boundary(String label, UnitDTO dto, int count, String expectedMessage) {
      assertViolations(dto, count, expectedMessage);
    }
  }

  @Nested
  @DisplayName("status 边界（可选，0/1 合法）")
  class StatusBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("status=-1 → 1违例(@Min)", withStatus(-1), 1, STATUS_INVALID),
          Arguments.of("status=0 → 0违例", withStatus(0), 0, null),
          Arguments.of("status=1 → 0违例", withStatus(1), 0, null),
          Arguments.of("status=2 → 1违例(@Max)", withStatus(2), 1, STATUS_INVALID),
          Arguments.of("status=null → 0违例(可选)", withStatus(null), 0, null));
    }

    private static UnitDTO withStatus(Integer status) {
      UnitDTO dto = create();
      dto.setStatus(status);
      return dto;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void status_boundary(String label, UnitDTO dto, int count, String expectedMessage) {
      assertViolations(dto, count, expectedMessage);
    }
  }

  @Nested
  @DisplayName("remark 边界（可选）")
  class RemarkBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("remark=null → 0违例(可选)", withRemark(null), 0, null),
          Arguments.of("remark=200字符 → 0违例(最大长度)", withRemark(repeat("a", 200)), 0, null),
          Arguments.of("remark=201字符 → 1违例(超出最大长度)", withRemark(repeat("a", 201)), 1, REMARK_SIZE));
    }

    private static UnitDTO withRemark(String remark) {
      UnitDTO dto = create();
      dto.setRemark(remark);
      return dto;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void remark_boundary(String label, UnitDTO dto, int count, String expectedMessage) {
      assertViolations(dto, count, expectedMessage);
    }
  }

  @Test
  @DisplayName("全部字段合法 → 0违例")
  void fullyValid() {
    UnitDTO dto = new UnitDTO();
    dto.setParentId(0L);
    dto.setUnitCode("valid_unit");
    dto.setUnitName("测试单位");
    dto.setSort(0);
    dto.setStatus(1);
    dto.setRemark("备注");
    assertThat(VALIDATOR.validate(dto)).isEmpty();
  }
}

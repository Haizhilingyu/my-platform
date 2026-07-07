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

@DisplayName("RoleDTO Bean Validation 边界值校验")
class RoleDTOBoundaryTest {

  private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
  private static final Validator VALIDATOR = FACTORY.getValidator();

  private static final String ROLECODE_REQUIRED = "角色编码不能为空";
  private static final String ROLECODE_SIZE = "角色编码长度需在3-50之间";
  private static final String ROLECODE_PATTERN = "角色编码只能包含字母、数字、下划线";
  private static final String ROLENAME_REQUIRED = "角色名称不能为空";
  private static final String ROLENAME_SIZE = "角色名称长度不能超过100";
  private static final String DATASCOPE_REQUIRED = "数据范围不能为空";
  private static final String DATASCOPE_SIZE = "数据范围标识长度不能超过20";
  private static final String STATUS_INVALID = "状态值非法";
  private static final String REMARK_SIZE = "备注长度不能超过200";

  private static String repeat(String s, int n) {
    StringBuilder sb = new StringBuilder(n * s.length());
    for (int i = 0; i < n; i++) {
      sb.append(s);
    }
    return sb.toString();
  }

  private static RoleDTO create() {
    RoleDTO dto = new RoleDTO();
    dto.setRoleCode("ADMIN");
    dto.setRoleName("管理员");
    dto.setDataScope("ALL");
    return dto;
  }

  private static Set<String> messages(Set<ConstraintViolation<RoleDTO>> v) {
    return v.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
  }

  private void assertViolations(RoleDTO dto, int expectedCount, String expectedMessage) {
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
  @DisplayName("roleCode 边界")
  class RoleCodeBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("roleCode=null → 1违例(@NotBlank)", withRoleCode(null), 1, ROLECODE_REQUIRED),
          Arguments.of(
              "roleCode=\"\" → 3违例(NotBlank+Size+Pattern)", withRoleCode(""), 3, ROLECODE_REQUIRED),
          Arguments.of("roleCode=2字符 → 1违例(低于最小长度3)", withRoleCode("ab"), 1, ROLECODE_SIZE),
          Arguments.of("roleCode=3字符 → 0违例(最小长度)", withRoleCode("abc"), 0, null),
          Arguments.of("roleCode=50字符 → 0违例(最大长度)", withRoleCode(repeat("a", 50)), 0, null),
          Arguments.of(
              "roleCode=51字符 → 1违例(超出最大长度)", withRoleCode(repeat("a", 51)), 1, ROLECODE_SIZE),
          Arguments.of("roleCode=含连字符 → 1违例(Pattern)", withRoleCode("role-1"), 1, ROLECODE_PATTERN),
          Arguments.of("roleCode=含下划线 → 0违例", withRoleCode("ADMIN_CODE"), 0, null));
    }

    private static RoleDTO withRoleCode(String roleCode) {
      RoleDTO dto = create();
      dto.setRoleCode(roleCode);
      return dto;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void roleCode_boundary(String label, RoleDTO dto, int count, String expectedMessage) {
      assertViolations(dto, count, expectedMessage);
    }
  }

  @Nested
  @DisplayName("roleName 边界")
  class RoleNameBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("roleName=null → 1违例(@NotBlank)", withRoleName(null), 1, ROLENAME_REQUIRED),
          Arguments.of("roleName=\"\" → 1违例(@NotBlank)", withRoleName(""), 1, ROLENAME_REQUIRED),
          Arguments.of("roleName=100字符 → 0违例(最大长度)", withRoleName(repeat("张", 100)), 0, null),
          Arguments.of(
              "roleName=101字符 → 1违例(超出最大长度)", withRoleName(repeat("张", 101)), 1, ROLENAME_SIZE));
    }

    private static RoleDTO withRoleName(String roleName) {
      RoleDTO dto = create();
      dto.setRoleName(roleName);
      return dto;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void roleName_boundary(String label, RoleDTO dto, int count, String expectedMessage) {
      assertViolations(dto, count, expectedMessage);
    }
  }

  @Nested
  @DisplayName("dataScope 边界")
  class DataScopeBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of(
              "dataScope=null → 1违例(@NotBlank)", withDataScope(null), 1, DATASCOPE_REQUIRED),
          Arguments.of("dataScope=\"\" → 1违例(@NotBlank)", withDataScope(""), 1, DATASCOPE_REQUIRED),
          Arguments.of("dataScope=20字符 → 0违例(最大长度)", withDataScope(repeat("a", 20)), 0, null),
          Arguments.of(
              "dataScope=21字符 → 1违例(超出最大长度)", withDataScope(repeat("a", 21)), 1, DATASCOPE_SIZE));
    }

    private static RoleDTO withDataScope(String dataScope) {
      RoleDTO dto = create();
      dto.setDataScope(dataScope);
      return dto;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void dataScope_boundary(String label, RoleDTO dto, int count, String expectedMessage) {
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

    private static RoleDTO withStatus(Integer status) {
      RoleDTO dto = create();
      dto.setStatus(status);
      return dto;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void status_boundary(String label, RoleDTO dto, int count, String expectedMessage) {
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

    private static RoleDTO withRemark(String remark) {
      RoleDTO dto = create();
      dto.setRemark(remark);
      return dto;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void remark_boundary(String label, RoleDTO dto, int count, String expectedMessage) {
      assertViolations(dto, count, expectedMessage);
    }
  }

  @Test
  @DisplayName("全部字段合法 → 0违例")
  void fullyValid() {
    RoleDTO dto = new RoleDTO();
    dto.setRoleCode("ADMIN_CODE");
    dto.setRoleName("管理员");
    dto.setDataScope("ALL");
    dto.setStatus(1);
    dto.setRemark("备注");
    assertThat(VALIDATOR.validate(dto)).isEmpty();
  }
}

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

@DisplayName("MenuDTO Bean Validation 边界值校验")
class MenuDTOBoundaryTest {

  private static final Validator VALIDATOR = ValidationTestHelper.validatorWithMessages();

  private static final String MENUNAME_REQUIRED = "菜单名称不能为空";
  private static final String MENUNAME_SIZE = "菜单名称长度不能超过50";
  private static final String MENUTYPE_REQUIRED = "菜单类型不能为空";
  private static final String MENUTYPE_SIZE = "菜单类型长度不能超过20";
  private static final String PATH_SIZE = "路由路径长度不能超过200";
  private static final String COMPONENT_SIZE = "组件路径长度不能超过200";
  private static final String PERMISSION_SIZE = "权限标识长度不能超过100";
  private static final String ICON_SIZE = "图标长度不能超过100";
  private static final String SORT_MIN = "排序值不能为负数";
  private static final String VISIBLE_INVALID = "可见性值非法";
  private static final String STATUS_INVALID = "状态值非法";

  private static String repeat(String s, int n) {
    StringBuilder sb = new StringBuilder(n * s.length());
    for (int i = 0; i < n; i++) {
      sb.append(s);
    }
    return sb.toString();
  }

  private static MenuDTO create() {
    MenuDTO dto = new MenuDTO();
    dto.setMenuName("用户管理");
    dto.setMenuType("MENU");
    return dto;
  }

  private static Set<String> messages(Set<ConstraintViolation<MenuDTO>> v) {
    return v.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
  }

  private void assertViolations(MenuDTO dto, int expectedCount, String expectedMessage) {
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
  @DisplayName("menuName 边界")
  class MenuNameBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("menuName=null → 1违例(@NotBlank)", withMenuName(null), 1, MENUNAME_REQUIRED),
          Arguments.of("menuName=\"\" → 1违例(@NotBlank)", withMenuName(""), 1, MENUNAME_REQUIRED),
          Arguments.of("menuName=50字符 → 0违例(最大长度)", withMenuName(repeat("张", 50)), 0, null),
          Arguments.of(
              "menuName=51字符 → 1违例(超出最大长度)", withMenuName(repeat("张", 51)), 1, MENUNAME_SIZE));
    }

    private static MenuDTO withMenuName(String menuName) {
      MenuDTO dto = create();
      dto.setMenuName(menuName);
      return dto;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void menuName_boundary(String label, MenuDTO dto, int count, String expectedMessage) {
      assertViolations(dto, count, expectedMessage);
    }
  }

  @Nested
  @DisplayName("menuType 边界")
  class MenuTypeBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("menuType=null → 1违例(@NotBlank)", withMenuType(null), 1, MENUTYPE_REQUIRED),
          Arguments.of("menuType=\"\" → 1违例(@NotBlank)", withMenuType(""), 1, MENUTYPE_REQUIRED),
          Arguments.of("menuType=20字符 → 0违例(最大长度)", withMenuType(repeat("a", 20)), 0, null),
          Arguments.of(
              "menuType=21字符 → 1违例(超出最大长度)", withMenuType(repeat("a", 21)), 1, MENUTYPE_SIZE));
    }

    private static MenuDTO withMenuType(String menuType) {
      MenuDTO dto = create();
      dto.setMenuType(menuType);
      return dto;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void menuType_boundary(String label, MenuDTO dto, int count, String expectedMessage) {
      assertViolations(dto, count, expectedMessage);
    }
  }

  @Nested
  @DisplayName("path 边界（可选）")
  class PathBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("path=null → 0违例(可选)", withPath(null), 0, null),
          Arguments.of("path=200字符 → 0违例(最大长度)", withPath(repeat("a", 200)), 0, null),
          Arguments.of("path=201字符 → 1违例(超出最大长度)", withPath(repeat("a", 201)), 1, PATH_SIZE));
    }

    private static MenuDTO withPath(String path) {
      MenuDTO dto = create();
      dto.setPath(path);
      return dto;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void path_boundary(String label, MenuDTO dto, int count, String expectedMessage) {
      assertViolations(dto, count, expectedMessage);
    }
  }

  @Nested
  @DisplayName("component 边界（可选）")
  class ComponentBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("component=null → 0违例(可选)", withComponent(null), 0, null),
          Arguments.of("component=200字符 → 0违例(最大长度)", withComponent(repeat("a", 200)), 0, null),
          Arguments.of(
              "component=201字符 → 1违例(超出最大长度)", withComponent(repeat("a", 201)), 1, COMPONENT_SIZE));
    }

    private static MenuDTO withComponent(String component) {
      MenuDTO dto = create();
      dto.setComponent(component);
      return dto;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void component_boundary(String label, MenuDTO dto, int count, String expectedMessage) {
      assertViolations(dto, count, expectedMessage);
    }
  }

  @Nested
  @DisplayName("permission 边界（可选）")
  class PermissionBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("permission=null → 0违例(可选)", withPermission(null), 0, null),
          Arguments.of("permission=100字符 → 0违例(最大长度)", withPermission(repeat("a", 100)), 0, null),
          Arguments.of(
              "permission=101字符 → 1违例(超出最大长度)",
              withPermission(repeat("a", 101)),
              1,
              PERMISSION_SIZE));
    }

    private static MenuDTO withPermission(String permission) {
      MenuDTO dto = create();
      dto.setPermission(permission);
      return dto;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void permission_boundary(String label, MenuDTO dto, int count, String expectedMessage) {
      assertViolations(dto, count, expectedMessage);
    }
  }

  @Nested
  @DisplayName("icon 边界（可选）")
  class IconBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("icon=null → 0违例(可选)", withIcon(null), 0, null),
          Arguments.of("icon=100字符 → 0违例(最大长度)", withIcon(repeat("a", 100)), 0, null),
          Arguments.of("icon=101字符 → 1违例(超出最大长度)", withIcon(repeat("a", 101)), 1, ICON_SIZE));
    }

    private static MenuDTO withIcon(String icon) {
      MenuDTO dto = create();
      dto.setIcon(icon);
      return dto;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void icon_boundary(String label, MenuDTO dto, int count, String expectedMessage) {
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
          Arguments.of("sort=null → 0违例(可选)", withSort(null), 0, null));
    }

    private static MenuDTO withSort(Integer sort) {
      MenuDTO dto = create();
      dto.setSort(sort);
      return dto;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void sort_boundary(String label, MenuDTO dto, int count, String expectedMessage) {
      assertViolations(dto, count, expectedMessage);
    }
  }

  @Nested
  @DisplayName("visible 边界（可选，0/1 合法）")
  class VisibleBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("visible=-1 → 1违例(@Min)", withVisible(-1), 1, VISIBLE_INVALID),
          Arguments.of("visible=0 → 0违例", withVisible(0), 0, null),
          Arguments.of("visible=1 → 0违例", withVisible(1), 0, null),
          Arguments.of("visible=2 → 1违例(@Max)", withVisible(2), 1, VISIBLE_INVALID),
          Arguments.of("visible=null → 0违例(可选)", withVisible(null), 0, null));
    }

    private static MenuDTO withVisible(Integer visible) {
      MenuDTO dto = create();
      dto.setVisible(visible);
      return dto;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void visible_boundary(String label, MenuDTO dto, int count, String expectedMessage) {
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

    private static MenuDTO withStatus(Integer status) {
      MenuDTO dto = create();
      dto.setStatus(status);
      return dto;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void status_boundary(String label, MenuDTO dto, int count, String expectedMessage) {
      assertViolations(dto, count, expectedMessage);
    }
  }

  @Test
  @DisplayName("全部字段合法 → 0违例")
  void fullyValid() {
    MenuDTO dto = new MenuDTO();
    dto.setParentId(0L);
    dto.setMenuName("用户管理");
    dto.setMenuType("MENU");
    dto.setPath("/sys/user");
    dto.setComponent("sys/user/index");
    dto.setPermission("sys:user:list");
    dto.setIcon("user");
    dto.setSort(0);
    dto.setVisible(1);
    dto.setStatus(1);
    assertThat(VALIDATOR.validate(dto)).isEmpty();
  }
}

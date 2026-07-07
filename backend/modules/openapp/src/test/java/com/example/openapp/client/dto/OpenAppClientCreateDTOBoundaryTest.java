package com.example.openapp.client.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
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

@DisplayName("OpenAppClientCreateDTO Bean Validation 边界值校验")
class OpenAppClientCreateDTOBoundaryTest {

  private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
  private static final Validator VALIDATOR = FACTORY.getValidator();

  private static final String CLIENTNAME_REQUIRED = "应用名称不能为空";
  private static final String CLIENTNAME_SIZE = "应用名称长度不能超过100";
  private static final String REDIRECT_URIS_REQUIRED = "redirectUris 不能为空";
  private static final String SCOPES_REQUIRED = "scopes 不能为空";
  private static final String GRANT_TYPES_REQUIRED = "grantTypes 不能为空";

  private static final List<String> URIS = List.of("https://example.com/cb");
  private static final List<String> SCOPES = List.of("openid");
  private static final List<String> GRANTS = List.of("client_credentials");

  private static String repeat(String s, int n) {
    StringBuilder sb = new StringBuilder(n * s.length());
    for (int i = 0; i < n; i++) {
      sb.append(s);
    }
    return sb.toString();
  }

  private static OpenAppClientCreateDTO create() {
    return new OpenAppClientCreateDTO("demo-app", URIS, null, SCOPES, GRANTS);
  }

  private static Set<String> messages(Set<ConstraintViolation<OpenAppClientCreateDTO>> v) {
    return v.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
  }

  private void assertViolations(
      OpenAppClientCreateDTO dto, int expectedCount, String expectedMessage) {
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
  @DisplayName("clientName 边界")
  class ClientNameBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of(
              "clientName=null → 1违例(@NotBlank)", withClientName(null), 1, CLIENTNAME_REQUIRED),
          Arguments.of(
              "clientName=\"\" → 1违例(@NotBlank)", withClientName(""), 1, CLIENTNAME_REQUIRED),
          Arguments.of("clientName=100字符 → 0违例(最大长度)", withClientName(repeat("a", 100)), 0, null),
          Arguments.of(
              "clientName=101字符 → 1违例(超出最大长度)",
              withClientName(repeat("a", 101)),
              1,
              CLIENTNAME_SIZE));
    }

    private static OpenAppClientCreateDTO withClientName(String clientName) {
      return new OpenAppClientCreateDTO(clientName, URIS, null, SCOPES, GRANTS);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void clientName_boundary(
        String label, OpenAppClientCreateDTO dto, int count, String expectedMessage) {
      assertViolations(dto, count, expectedMessage);
    }
  }

  @Nested
  @DisplayName("redirectUris 边界")
  class RedirectUrisBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of(
              "redirectUris=null → 1违例(@NotEmpty)",
              withRedirectUris(null),
              1,
              REDIRECT_URIS_REQUIRED),
          Arguments.of(
              "redirectUris=空列表 → 1违例(@NotEmpty)",
              withRedirectUris(List.of()),
              1,
              REDIRECT_URIS_REQUIRED),
          Arguments.of("redirectUris=非空 → 0违例", withRedirectUris(URIS), 0, null));
    }

    private static OpenAppClientCreateDTO withRedirectUris(List<String> redirectUris) {
      return new OpenAppClientCreateDTO("demo-app", redirectUris, null, SCOPES, GRANTS);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void redirectUris_boundary(
        String label, OpenAppClientCreateDTO dto, int count, String expectedMessage) {
      assertViolations(dto, count, expectedMessage);
    }
  }

  @Nested
  @DisplayName("scopes 边界")
  class ScopesBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of("scopes=null → 1违例(@NotEmpty)", withScopes(null), 1, SCOPES_REQUIRED),
          Arguments.of("scopes=空列表 → 1违例(@NotEmpty)", withScopes(List.of()), 1, SCOPES_REQUIRED),
          Arguments.of("scopes=非空 → 0违例", withScopes(SCOPES), 0, null));
    }

    private static OpenAppClientCreateDTO withScopes(List<String> scopes) {
      return new OpenAppClientCreateDTO("demo-app", URIS, null, scopes, GRANTS);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void scopes_boundary(
        String label, OpenAppClientCreateDTO dto, int count, String expectedMessage) {
      assertViolations(dto, count, expectedMessage);
    }
  }

  @Nested
  @DisplayName("grantTypes 边界")
  class GrantTypesBoundary {

    static Stream<Arguments> cases() {
      return Stream.of(
          Arguments.of(
              "grantTypes=null → 1违例(@NotEmpty)", withGrantTypes(null), 1, GRANT_TYPES_REQUIRED),
          Arguments.of(
              "grantTypes=空列表 → 1违例(@NotEmpty)",
              withGrantTypes(List.of()),
              1,
              GRANT_TYPES_REQUIRED),
          Arguments.of("grantTypes=非空 → 0违例", withGrantTypes(GRANTS), 0, null));
    }

    private static OpenAppClientCreateDTO withGrantTypes(List<String> grantTypes) {
      return new OpenAppClientCreateDTO("demo-app", URIS, null, SCOPES, grantTypes);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void grantTypes_boundary(
        String label, OpenAppClientCreateDTO dto, int count, String expectedMessage) {
      assertViolations(dto, count, expectedMessage);
    }
  }

  @Test
  @DisplayName("全部字段合法 → 0违例")
  void fullyValid() {
    assertThat(VALIDATOR.validate(create())).isEmpty();
  }
}

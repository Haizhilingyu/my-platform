package com.example.notify.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** BatchReadDTO Bean Validation 边界值校验。 */
@DisplayName("BatchReadDTO Bean Validation 边界值校验")
class BatchReadDTOBoundaryTest {

  private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
  private static final Validator VALIDATOR = FACTORY.getValidator();

  @AfterAll
  static void close() {
    FACTORY.close();
  }

  @SuppressWarnings("unchecked")
  private static Set<String> paths(Set<?> violations) {
    return violations.stream()
        .map(v -> ((jakarta.validation.ConstraintViolation<?>) v).getPropertyPath().toString())
        .collect(Collectors.toSet());
  }

  @Test
  @DisplayName("合法：非空的 ids → 0 违例")
  void valid_noViolations() {
    BatchReadDTO dto = new BatchReadDTO();
    dto.setIds(List.of(1L, 2L));
    assertThat(VALIDATOR.validate(dto)).isEmpty();
  }

  @Test
  @DisplayName("ids 为 null → 违例路径 ids")
  void idsNull() {
    BatchReadDTO dto = new BatchReadDTO();
    dto.setIds(null);
    assertThat(paths(VALIDATOR.validate(dto))).contains("ids");
  }

  @Test
  @DisplayName("ids 为空 → 违例路径 ids")
  void idsEmpty() {
    BatchReadDTO dto = new BatchReadDTO();
    dto.setIds(List.of());
    assertThat(paths(VALIDATOR.validate(dto))).contains("ids");
  }
}

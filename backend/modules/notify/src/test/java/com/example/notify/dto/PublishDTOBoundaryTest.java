package com.example.notify.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.notify.enums.MessageLevel;
import com.example.notify.enums.RecipientType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** PublishDTO Bean Validation 边界值校验（含嵌套 RecipientSpec 级联校验）。 */
@DisplayName("PublishDTO Bean Validation 边界值校验")
class PublishDTOBoundaryTest {

  private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
  private static final Validator VALIDATOR = FACTORY.getValidator();

  @AfterAll
  static void close() {
    FACTORY.close();
  }

  private static PublishDTO valid() {
    PublishDTO dto = new PublishDTO();
    dto.setTitle("标题");
    dto.setContent("内容");
    dto.setLevel(MessageLevel.NORMAL);
    PublishDTO.RecipientSpec spec = new PublishDTO.RecipientSpec();
    spec.setType(RecipientType.USER);
    spec.setId(1L);
    dto.setRecipients(List.of(spec));
    return dto;
  }

  @SuppressWarnings("unchecked")
  private static Set<String> paths(Set<?> violations) {
    return violations.stream()
        .map(v -> ((jakarta.validation.ConstraintViolation<?>) v).getPropertyPath().toString())
        .collect(Collectors.toSet());
  }

  @Test
  @DisplayName("合法 DTO：0 违例")
  void valid_hasNoViolations() {
    assertThat(VALIDATOR.validate(valid())).isEmpty();
  }

  @Nested
  @DisplayName("必填字段")
  class Required {

    @Test
    @DisplayName("title 为 null → 违例路径 title")
    void titleNull() {
      PublishDTO dto = valid();
      dto.setTitle(null);
      assertThat(paths(VALIDATOR.validate(dto))).contains("title");
    }

    @Test
    @DisplayName("content 为 null → 违例路径 content")
    void contentNull() {
      PublishDTO dto = valid();
      dto.setContent(null);
      assertThat(paths(VALIDATOR.validate(dto))).contains("content");
    }

    @Test
    @DisplayName("level 为 null → 违例路径 level")
    void levelNull() {
      PublishDTO dto = valid();
      dto.setLevel(null);
      assertThat(paths(VALIDATOR.validate(dto))).contains("level");
    }

    @Test
    @DisplayName("recipients 为 null → 违例路径 recipients")
    void recipientsNull() {
      PublishDTO dto = valid();
      dto.setRecipients(null);
      assertThat(paths(VALIDATOR.validate(dto))).contains("recipients");
    }

    @Test
    @DisplayName("recipients 为空 → 违例路径 recipients")
    void recipientsEmpty() {
      PublishDTO dto = valid();
      dto.setRecipients(List.of());
      assertThat(paths(VALIDATOR.validate(dto))).contains("recipients");
    }
  }

  @Nested
  @DisplayName("嵌套 RecipientSpec 级联校验")
  class Cascade {

    @Test
    @DisplayName("spec.type 为 null → 路径 recipients[0].type")
    void typeNull() {
      PublishDTO dto = valid();
      dto.getRecipients().get(0).setType(null);
      assertThat(paths(VALIDATOR.validate(dto))).contains("recipients[0].type");
    }

    @Test
    @DisplayName("spec.id 为 null → 路径 recipients[0].id")
    void idNull() {
      PublishDTO dto = valid();
      dto.getRecipients().get(0).setId(null);
      assertThat(paths(VALIDATOR.validate(dto))).contains("recipients[0].id");
    }
  }
}

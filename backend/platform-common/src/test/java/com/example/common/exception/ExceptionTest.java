package com.example.common.exception;

import static org.assertj.core.api.Assertions.*;

import com.example.common.i18n.Messages;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.test.util.ReflectionTestUtils;

/** 异常类测试。 */
@DisplayName("异常体系")
class ExceptionTest {

  @BeforeEach
  void setUp() {
    ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
    ms.setBasename("classpath:i18n/messages");
    ms.setDefaultEncoding("UTF-8");
    ms.setFallbackToSystemLocale(false);
    ms.setUseCodeAsDefaultMessage(true);
    ReflectionTestUtils.invokeMethod(new Messages(ms), "init");
  }

  @AfterEach
  void tearDown() {
    LocaleContextHolder.resetLocaleContext();
  }

  @Test
  @DisplayName("BizException：默认 code=500")
  void should_defaultTo500_when_noCode() {
    BizException ex = new BizException("业务错误");

    assertThat(ex.getCode()).isEqualTo(500);
    assertThat(ex.getMessage()).isEqualTo("业务错误");
  }

  @Test
  @DisplayName("BizException：自定义 code")
  void should_useCustomCode_when_specified() {
    BizException ex = new BizException(400, "参数错误");

    assertThat(ex.getCode()).isEqualTo(400);
  }

  @Test
  @DisplayName("NotFoundException(2-arg)：code=404，向后兼容")
  void should_be404_when_notFound_twoArg() {
    NotFoundException ex = new NotFoundException("用户", 123L);

    assertThat(ex.getCode()).isEqualTo(404);
    assertThat(ex.getMessage()).contains("用户", "123");
  }

  @Test
  @DisplayName("NotFoundException(1-arg)：code=404，消息原样透传")
  void should_be404_when_notFound_singleArg() {
    NotFoundException ex = new NotFoundException("whatever pre-translated message");

    assertThat(ex.getCode()).isEqualTo(404);
    assertThat(ex.getMessage()).isEqualTo("whatever pre-translated message");
  }

  @Test
  @DisplayName("NotFoundException(1-arg)：配合 Messages.get() 解析 i18n")
  void should_resolveI18n_when_notFoundSingleArgWithMessages() {
    LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);

    NotFoundException ex =
        new NotFoundException(
            Messages.get("error.resource.not.found", Messages.get("resource.user"), 42L));

    assertThat(ex.getCode()).isEqualTo(404);
    assertThat(ex.getMessage()).isEqualTo("用户 不存在: 42");
  }

  @Test
  @DisplayName("ForbiddenException：code=403")
  void should_be403_when_forbidden() {
    ForbiddenException ex = new ForbiddenException("无权限");

    assertThat(ex.getCode()).isEqualTo(403);
  }

  @Test
  @DisplayName("AccountLockedException：code=423")
  void should_be423_when_accountLocked() {
    AccountLockedException ex = new AccountLockedException("locked");

    assertThat(ex.getCode()).isEqualTo(423);
  }

  @Test
  @DisplayName("AccountLockedException.defaultMessage()：中文 locale 解析 i18n")
  void should_resolveZh_when_defaultMessage_zh() {
    LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);

    AccountLockedException ex = AccountLockedException.defaultMessage();

    assertThat(ex.getCode()).isEqualTo(423);
    assertThat(ex.getMessage()).isEqualTo("账号已锁定，请联系管理员或稍后重试");
  }

  @Test
  @DisplayName("AccountLockedException.defaultMessage()：英文 locale 解析 i18n")
  void should_resolveEn_when_defaultMessage_en() {
    LocaleContextHolder.setLocale(Locale.ENGLISH);

    AccountLockedException ex = AccountLockedException.defaultMessage();

    assertThat(ex.getCode()).isEqualTo(423);
    assertThat(ex.getMessage())
        .isEqualTo("Account is locked, please contact the administrator or try again later");
  }
}

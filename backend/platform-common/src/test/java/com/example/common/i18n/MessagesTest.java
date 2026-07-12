package com.example.common.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

/**
 * {@link Messages} 静态工具类单元测试。
 *
 * <p>不启动 Spring 上下文——直接构造 {@link ReloadableResourceBundleMessageSource}， 通过创建 {@link Messages}
 * 实例并调用其 {@code @PostConstruct} 方法将消息源注入静态字段， 匹配项目中 boundary test 的"无 Spring 上下文"模式。
 */
@DisplayName("Messages 国际化工具")
class MessagesTest {

  private ReloadableResourceBundleMessageSource messageSource;

  @BeforeEach
  void setUp() {
    messageSource = new ReloadableResourceBundleMessageSource();
    messageSource.setBasename("classpath:i18n/messages");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setFallbackToSystemLocale(false);
    messageSource.setUseCodeAsDefaultMessage(true);

    new Messages(messageSource).init();
  }

  @AfterEach
  void tearDown() {
    LocaleContextHolder.resetLocaleContext();
  }

  @Test
  @DisplayName("中文 locale：返回中文文案")
  void should_returnChinese_when_localeZh() {
    assertThat(Messages.get(Locale.SIMPLIFIED_CHINESE, "error.access.denied")).isEqualTo("无权限访问");
  }

  @Test
  @DisplayName("英文 locale：返回英文文案")
  void should_returnEnglish_when_localeEn() {
    assertThat(Messages.get(Locale.ENGLISH, "error.access.denied")).isEqualTo("Access denied");
  }

  @Test
  @DisplayName("参数插值：单参数替换")
  void should_interpolateArg_when_singleArg() {
    assertThat(Messages.get(Locale.ENGLISH, "user.username.exists", "admin"))
        .isEqualTo("Username already exists: admin");
  }

  @Test
  @DisplayName("getOrDefault：key 不存在时返回默认值")
  void should_returnDefault_when_keyMissing() {
    assertThat(Messages.getOrDefault("nonexistent.key", "fallback")).isEqualTo("fallback");
  }

  @Test
  @DisplayName("参数插值：多参数替换")
  void should_interpolateArgs_when_multiArgs() {
    assertThat(Messages.get(Locale.ENGLISH, "error.resource.not.found", "User", 42))
        .isEqualTo("User does not exist: 42");
  }

  @Test
  @DisplayName("getOrDefault：key 存在时返回实际文案（非默认值）")
  void should_returnMessage_when_keyExists() {
    assertThat(Messages.getOrDefault("error.access.denied", "should-not-use")).isEqualTo("无权限访问");
  }

  @Test
  @DisplayName("无显式 locale 的 get：使用 LocaleContextHolder 中的 locale")
  void should_useContextLocale_when_noExplicitLocale() {
    LocaleContextHolder.setLocale(Locale.ENGLISH);

    assertThat(Messages.get("error.auth.not.login")).isEqualTo("Not logged in");
  }

  @Test
  @DisplayName("currentLocale：返回 LocaleContextHolder 中的 locale")
  void should_returnContextLocale_when_currentLocaleCalled() {
    LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);

    assertThat(Messages.currentLocale()).isEqualTo(Locale.SIMPLIFIED_CHINESE);
  }

  @Test
  @DisplayName("useCodeAsDefaultMessage：未命中的 key 返回 key 自身")
  void should_returnKey_when_keyMissingAndNoDefault() {
    assertThat(Messages.get(Locale.ENGLISH, "totally.missing.key"))
        .isEqualTo("totally.missing.key");
  }
}

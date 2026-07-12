package com.example.common.i18n;

import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Locale 解析自动配置。
 *
 * <p>基于 HTTP {@code Accept-Language} 头解析请求 locale，支持 zh-CN（默认）和 en。 当前线程 locale 通过 {@code
 * LocaleContextHolder} 暴露给 {@link Messages} 等工具类。
 *
 * <p>{@link ConditionalOnClass} 守卫：仅当 classpath 存在 Spring MVC 时才激活（非 Web 环境跳过）。
 */
@AutoConfiguration
@ConditionalOnClass(LocaleResolver.class)
public class LocaleConfig {

  /**
   * 创建 LocaleResolver bean。
   *
   * <p>默认 locale 为 {@link Locale#SIMPLIFIED_CHINESE}，支持列表包含 zh-CN 和 en。 当请求头不匹配任何 supported locale
   * 时，回退到默认 locale。
   *
   * @return 已配置的 {@link AcceptHeaderLocaleResolver}
   */
  @Bean
  @ConditionalOnMissingBean(LocaleResolver.class)
  public LocaleResolver localeResolver() {
    AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
    resolver.setSupportedLocales(
        List.of(Locale.SIMPLIFIED_CHINESE, Locale.ENGLISH, Locale.forLanguageTag("zh-CN")));
    resolver.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
    return resolver;
  }
}

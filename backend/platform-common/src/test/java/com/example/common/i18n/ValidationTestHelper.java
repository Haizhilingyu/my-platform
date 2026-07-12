package com.example.common.i18n;

import jakarta.validation.Validator;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * 测试专用：构造一个能够解析 {@code {validation.*}} 消息键的 {@link Validator}。
 *
 * <p>用于 boundary test。默认的 {@code Validation.buildDefaultValidatorFactory()} 不会经过 Spring 的 {@link
 * org.springframework.context.MessageSource}，因而无法解析 {@code messages.properties} 中定义的键； 本助手通过 {@link
 * LocalValidatorFactoryBean#setMessageSource} 将 {@code classpath:i18n/messages} 注入 Hibernate
 * Validator，使 {@link jakarta.validation.ConstraintViolation#getMessage()} 返回已解析的文案。
 *
 * <p>JVM 默认 locale 决定返回中文或英文文案。若以中文 locale 运行，断言可直接比对中文文案。
 */
public final class ValidationTestHelper {

  private ValidationTestHelper() {}

  /** 返回一个绑定 {@code messages.properties} 的 {@link Validator}，使 boundary test 能够断言已解析的中文/英文文案。 */
  public static Validator validatorWithMessages() {
    ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
    ms.setBasename("classpath:i18n/messages");
    ms.setDefaultEncoding("UTF-8");
    ms.setFallbackToSystemLocale(false);
    ms.setUseCodeAsDefaultMessage(true);
    LocalValidatorFactoryBean factory = new LocalValidatorFactoryBean();
    factory.setValidationMessageSource(ms);
    factory.afterPropertiesSet();
    return factory;
  }
}

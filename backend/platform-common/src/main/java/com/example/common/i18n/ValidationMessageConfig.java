package com.example.common.i18n;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Jakarta Bean Validation 自动配置。
 *
 * <p>将 {@link MessageSource} 桥接到 Hibernate Validator，使 DTO 字段上的 {@code @NotBlank(message =
 * "{validation.user.username.notBlank}")} 等 {key} 插值从同一份 i18n properties 解析。
 *
 * <p>不如此配置时，Hibernate Validator 会直接把 {key} 当作 literal 文本输出。
 */
@AutoConfiguration
@ConditionalOnClass(LocalValidatorFactoryBean.class)
public class ValidationMessageConfig {

  /**
   * 创建 Validator bean，其消息插值委托给全局 {@link MessageSource}。
   *
   * @param messageSource i18n 消息源（由 {@link MessageSourceConfig} 提供）
   * @return 已桥接 MessageSource 的 {@link LocalValidatorFactoryBean}
   */
  @Bean
  @ConditionalOnMissingBean(jakarta.validation.Validator.class)
  public LocalValidatorFactoryBean validator(MessageSource messageSource) {
    LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
    bean.setValidationMessageSource(messageSource);
    return bean;
  }
}

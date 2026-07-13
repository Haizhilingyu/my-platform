package com.example.i18n.config;

import com.example.i18n.i18n.HybridMessageSource;
import com.example.i18n.spi.DBMessageProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 装配 {@link HybridMessageSource} 为 {@code @Primary} 的 {@link MessageSource}， 以 properties 消息源为父级兜底。
 */
@Configuration
public class I18nMessageSourceConfig {

  @Bean
  @Primary
  public MessageSource messageSource(
      @Qualifier("propertiesMessageSource") MessageSource propertiesMessageSource,
      ObjectProvider<DBMessageProvider> dbProvider) {
    HybridMessageSource hybrid = new HybridMessageSource(propertiesMessageSource, dbProvider);
    hybrid.setParentMessageSource(propertiesMessageSource);
    return hybrid;
  }
}

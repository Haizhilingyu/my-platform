package com.example.common.i18n;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

/**
 * 国际化消息源自动配置。
 *
 * <p>提供基于 {@code classpath:i18n/messages} 的 {@link MessageSource} bean，所有业务模块的 i18n 消息 解析均依赖此 bean：
 *
 * <ul>
 *   <li>{@link Messages} 静态工具类通过此 bean 解析消息 key。
 *   <li>Jakarta Bean Validation 的 {@code {key}} 插值通过此 bean 解析（见 {@link ValidationMessageConfig}）。
 *   <li>异常消息、GlobalExceptionHandler 等下游消费者均从此处获取文案。
 * </ul>
 *
 * <p>设计要点：
 *
 * <ul>
 *   <li>使用 {@link ReloadableResourceBundleMessageSource}（而非 {@code
 *       ResourceBundleMessageSource}），支持未来按需热加载。
 *   <li>{@code setFallbackToSystemLocale(false)} —— JVM 默认 locale 不影响解析路径，避免在非中文 JVM 上 回退到系统
 *       locale。
 *   <li>{@code setUseCodeAsDefaultMessage(true)} —— 未解析的 key 返回 key 自身而非抛异常，便于排障时 快速定位缺失的 key。
 * </ul>
 */
@AutoConfiguration
public class MessageSourceConfig {

  /**
   * 创建 MessageSource bean。
   *
   * <p>basename 为 {@code classpath:i18n/messages}，UTF-8 编码，不回退到系统 locale， 未命中的 key 返回 key 本身。
   *
   * @return 已配置的 {@link ReloadableResourceBundleMessageSource}
   */
  @Bean
  @ConditionalOnMissingBean(MessageSource.class)
  public MessageSource messageSource() {
    ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
    ms.setBasename("classpath:i18n/messages");
    ms.setDefaultEncoding("UTF-8");
    ms.setFallbackToSystemLocale(false);
    ms.setUseCodeAsDefaultMessage(true);
    return ms;
  }
}

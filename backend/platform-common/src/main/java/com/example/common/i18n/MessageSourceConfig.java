package com.example.common.i18n;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

/**
 * properties 消息源自动配置。
 *
 * <p>提供基于 {@code classpath:i18n/messages} 的 {@link MessageSource} bean（名为 {@code
 * propertiesMessageSource}）， 作为 {@code HybridMessageSource}（i18n 模块，{@code @Primary}）的父级兜底消息源。未安装
 * i18n 模块时，本 bean 仍可作为唯一 MessageSource 使用。
 *
 * <ul>
 *   <li>{@link Messages} 静态工具类通过最终注入的 {@code @Primary} MessageSource 解析消息 key。
 *   <li>Jakarta Bean Validation 的 {@code {key}} 插值通过最终 MessageSource 解析（见 {@link
 *       ValidationMessageConfig}）。
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
   * 创建基于 properties 的 MessageSource bean。
   *
   * <p>basename 为 {@code classpath:i18n/messages}，UTF-8 编码，不回退到系统 locale， 未命中的 key 返回 key 本身。
   *
   * @return 已配置的 {@link ReloadableResourceBundleMessageSource}
   */
  @Bean("propertiesMessageSource")
  public MessageSource propertiesMessageSource() {
    ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
    ms.setBasename("classpath:i18n/messages");
    ms.setDefaultEncoding("UTF-8");
    ms.setFallbackToSystemLocale(false);
    ms.setUseCodeAsDefaultMessage(true);
    return ms;
  }
}

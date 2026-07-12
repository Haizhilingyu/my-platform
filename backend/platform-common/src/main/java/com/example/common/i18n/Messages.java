package com.example.common.i18n;

import jakarta.annotation.PostConstruct;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * 国际化消息静态工具类。
 *
 * <p>通过静态方法 {@code Messages.get("key", args...)} 在任意层（Service / Aspect / Exception） 获取 i18n 消息，无需注入
 * {@link MessageSource}。
 *
 * <p>实现原理：本类作为 Spring {@code @Component} 被实例化时，构造器注入 {@link MessageSource}， {@link PostConstruct}
 * 阶段将其复制到 {@link #MESSAGE_SOURCE} 静态字段。 之后所有静态方法通过该静态字段解析消息。
 *
 * <p>线程安全：{@code LocaleContextHolder} 基于 {@code ThreadLocal}，每个请求线程绑定各自的 locale（ 由 {@code
 * LocaleResolver} 从 Accept-Language 头解析），因此静态字段虽为共享状态但无竞态。
 *
 * <p>消息解析语义：
 *
 * <ul>
 *   <li>{@link #get(String, Object...)} —— 使用当前线程 locale，未命中的 key 返回 key 自身（ 因 {@code
 *       useCodeAsDefaultMessage=true}），不抛异常。
 *   <li>{@link #get(Locale, String, Object...)} —— 使用显式 locale。
 *   <li>{@link #getOrDefault(String, String, Object...)} —— 未命中返回 defaultValue，安全调用。
 * </ul>
 */
@Component
public class Messages {

  private static MessageSource MESSAGE_SOURCE;

  private final MessageSource messageSource;

  public Messages(MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  @PostConstruct
  public void init() {
    MESSAGE_SOURCE = messageSource;
  }

  /**
   * 使用当前线程 locale（{@link LocaleContextHolder#getLocale()}）解析消息。
   *
   * @param key 消息 key
   * @param args 插值参数（对应 properties 中的 {0}, {1}...）
   * @return 解析后的消息文案；若 key 未定义则返回 key 自身（因 useCodeAsDefaultMessage=true）
   */
  public static String get(String key, Object... args) {
    return MESSAGE_SOURCE.getMessage(key, args, LocaleContextHolder.getLocale());
  }

  /**
   * 使用显式 locale 解析消息。
   *
   * @param locale 目标 locale
   * @param key 消息 key
   * @param args 插值参数
   * @return 解析后的消息文案；若 key 未定义则返回 key 自身
   */
  public static String get(Locale locale, String key, Object... args) {
    return MESSAGE_SOURCE.getMessage(key, args, locale);
  }

  /**
   * 解析消息，未命中时返回 defaultValue（不抛异常）。
   *
   * <p>使用当前线程 locale。
   *
   * @param key 消息 key
   * @param defaultValue 未命中时的默认值（支持 {0},{1} 插值）
   * @param args 插值参数
   * @return 解析后的消息文案，或 defaultValue
   */
  public static String getOrDefault(String key, String defaultValue, Object... args) {
    return MESSAGE_SOURCE.getMessage(key, args, defaultValue, LocaleContextHolder.getLocale());
  }

  /**
   * 获取当前线程绑定的 locale。
   *
   * @return 当前 locale（由 LocaleResolver 从请求头解析后写入 LocaleContextHolder）
   */
  public static Locale currentLocale() {
    return LocaleContextHolder.getLocale();
  }
}

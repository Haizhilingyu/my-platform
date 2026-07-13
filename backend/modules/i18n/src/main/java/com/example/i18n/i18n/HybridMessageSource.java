package com.example.i18n.i18n;

import com.example.i18n.event.I18nMessageUpdatedEvent;
import com.example.i18n.spi.DBMessageProvider;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;
import org.springframework.context.event.EventListener;
import org.springframework.context.support.AbstractMessageSource;

/**
 * 混合消息源：DB（i18n_message）主 + properties 兜底。
 *
 * <p>解析优先查 DB 缓存，命中返回 DB 值；未命中返回 null，由父 MessageSource（properties）兜底。 监听 {@link
 * I18nMessageUpdatedEvent} 失效对应 locale 缓存。由 {@code I18nMessageSourceConfig} 装配为 {@code @Primary}
 * bean。
 */
public class HybridMessageSource extends AbstractMessageSource {

  private final ObjectProvider<DBMessageProvider> dbProviderOpt;
  private final Map<String, Map<String, String>> cache = new ConcurrentHashMap<>();

  public HybridMessageSource(
      MessageSource propertiesDelegate, ObjectProvider<DBMessageProvider> dbProviderOpt) {
    this.dbProviderOpt = dbProviderOpt;
    setParentMessageSource(propertiesDelegate);
  }

  @Override
  protected MessageFormat resolveCode(String code, Locale locale) {
    String localeTag = locale.toLanguageTag();
    Map<String, String> dict =
        cache.computeIfAbsent(
            localeTag,
            loc -> {
              DBMessageProvider provider = dbProviderOpt.getIfAvailable();
              return provider != null ? provider.loadByLocale(loc) : Collections.emptyMap();
            });
    if (dict.containsKey(code)) {
      return new MessageFormat(escape(dict.get(code)), locale);
    }
    return null;
  }

  @EventListener(I18nMessageUpdatedEvent.class)
  public void onMessageUpdated(I18nMessageUpdatedEvent event) {
    cache.remove(event.getLocale());
  }

  private static String escape(String value) {
    return value != null ? value.replace("'", "''") : "";
  }
}

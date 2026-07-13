package com.example.i18n.event;

import org.springframework.context.ApplicationEvent;

/** 翻译更新事件。{@code locale} 标识被修改的语言，监听方据此失效对应缓存。 */
public class I18nMessageUpdatedEvent extends ApplicationEvent {

  private final String locale;

  public I18nMessageUpdatedEvent(Object source, String locale) {
    super(source);
    this.locale = locale;
  }

  public String getLocale() {
    return locale;
  }
}

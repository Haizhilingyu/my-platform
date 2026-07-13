package com.example.i18n.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.i18n.event.I18nMessageUpdatedEvent;
import com.example.i18n.spi.DBMessageProvider;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.support.StaticMessageSource;

@DisplayName("HybridMessageSource 解析与缓存失效")
class HybridMessageSourceTest {

  private static ObjectProvider<DBMessageProvider> provider(DBMessageProvider impl) {
    return new ObjectProvider<>() {
      @Override
      public DBMessageProvider getIfAvailable() {
        return impl;
      }

      @Override
      public DBMessageProvider getIfUnique() {
        return impl;
      }

      @Override
      public DBMessageProvider getObject(Object... args) {
        return impl;
      }

      @Override
      public DBMessageProvider getObject() {
        return impl;
      }

      @Override
      public Iterator<DBMessageProvider> iterator() {
        return Collections.singleton(impl).iterator();
      }
    };
  }

  private static class MutableDbProvider implements DBMessageProvider {
    Map<String, String> data = new HashMap<>();

    @Override
    public Map<String, String> loadByLocale(String localeTag) {
      return new HashMap<>(data);
    }
  }

  @Test
  @DisplayName("DB 有 key → 返回 DB 值")
  void dbHit_returnsDbValue() {
    MutableDbProvider db = new MutableDbProvider();
    db.data.put("greeting", "你好");
    StaticMessageSource parent = new StaticMessageSource();
    HybridMessageSource hybrid = new HybridMessageSource(parent, provider(db));

    assertThat(hybrid.getMessage("greeting", null, Locale.forLanguageTag("zh-CN"))).isEqualTo("你好");
  }

  @Test
  @DisplayName("DB 无 key → 回退到 properties 父级值")
  void dbMiss_fallsBackToProperties() {
    MutableDbProvider db = new MutableDbProvider();
    StaticMessageSource parent = new StaticMessageSource();
    parent.addMessage("only.in.properties", Locale.ENGLISH, "properties-value");
    HybridMessageSource hybrid = new HybridMessageSource(parent, provider(db));

    assertThat(hybrid.getMessage("only.in.properties", null, Locale.ENGLISH))
        .isEqualTo("properties-value");
  }

  @Test
  @DisplayName("收到事件后缓存失效，下次查询拿到新值")
  void cacheInvalidation_picksUpNewValue() {
    MutableDbProvider db = new MutableDbProvider();
    db.data.put("k", "v1");
    StaticMessageSource parent = new StaticMessageSource();
    HybridMessageSource hybrid = new HybridMessageSource(parent, provider(db));
    Locale locale = Locale.forLanguageTag("zh-CN");

    assertThat(hybrid.getMessage("k", null, locale)).isEqualTo("v1");

    db.data.put("k", "v2");
    assertThat(hybrid.getMessage("k", null, locale)).isEqualTo("v1");

    hybrid.onMessageUpdated(new I18nMessageUpdatedEvent(this, "zh-CN"));
    assertThat(hybrid.getMessage("k", null, locale)).isEqualTo("v2");
  }
}

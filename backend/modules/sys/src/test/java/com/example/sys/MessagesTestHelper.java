package com.example.sys;

import com.example.common.i18n.Messages;
import java.lang.reflect.Field;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * 为纯 Mockito 单测预初始化 {@link Messages#MESSAGE_SOURCE}，避免 B5 引入的 {@code Messages.get(...)} 在无 Spring
 * 环境下 NPE。 幂等：仅当 MESSAGE_SOURCE 为 null 时填充。
 */
public final class MessagesTestHelper {

  private MessagesTestHelper() {}

  public static void init() {
    try {
      Field field = Messages.class.getDeclaredField("MESSAGE_SOURCE");
      field.setAccessible(true);
      if (field.get(null) != null) {
        return;
      }
      ResourceBundleMessageSource source = new ResourceBundleMessageSource();
      source.setBasenames("i18n/messages");
      source.setUseCodeAsDefaultMessage(true);
      source.setDefaultEncoding("UTF-8");
      field.set(null, source);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException("Failed to initialize Messages.MESSAGE_SOURCE for tests", e);
    }
  }
}

package com.example.i18n.spi;

import java.util.Map;

/** DB 消息提供者 SPI。实现方按 locale 加载扁平 {@code {messageKey: value}} 映射。 */
public interface DBMessageProvider {

  Map<String, String> loadByLocale(String localeTag);
}

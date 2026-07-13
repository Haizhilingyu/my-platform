package com.example.i18n.i18n;

import com.example.i18n.domain.I18nMessage;
import com.example.i18n.repository.I18nMessageRepository;
import com.example.i18n.spi.DBMessageProvider;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaDBMessageProvider implements DBMessageProvider {

  private final I18nMessageRepository repository;

  @Override
  public Map<String, String> loadByLocale(String localeTag) {
    return repository.findByLocale(localeTag).stream()
        .collect(
            Collectors.toMap(
                I18nMessage::getMessageKey,
                I18nMessage::getValue,
                (a, b) -> a,
                LinkedHashMap::new));
  }
}

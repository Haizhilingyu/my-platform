package com.example.i18n.repository;

import com.example.i18n.domain.I18nMessage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface I18nMessageRepository
    extends JpaRepository<I18nMessage, Long>, JpaSpecificationExecutor<I18nMessage> {

  List<I18nMessage> findByLocale(String locale);

  List<I18nMessage> findByLocaleAndModule(String locale, String module);

  Optional<I18nMessage> findByMessageKeyAndLocale(String messageKey, String locale);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM I18nMessage m WHERE m.locale = :locale")
  int deleteByLocale(@Param("locale") String locale);
}

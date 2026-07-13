package com.example.i18n.dto;

import com.example.i18n.domain.I18nMessage;
import java.time.LocalDateTime;
import lombok.Data;

/** 国际化消息视图对象。 */
@Data
public class I18nMessageVO {

  private Long id;
  private String messageKey;
  private String locale;
  private String module;
  private String value;
  private String description;
  private LocalDateTime updatedAt;

  public static I18nMessageVO of(I18nMessage m) {
    I18nMessageVO vo = new I18nMessageVO();
    vo.setId(m.getId());
    vo.setMessageKey(m.getMessageKey());
    vo.setLocale(m.getLocale());
    vo.setModule(m.getModule());
    vo.setValue(m.getValue());
    vo.setDescription(m.getDescription());
    vo.setUpdatedAt(m.getUpdatedAt());
    return vo;
  }
}

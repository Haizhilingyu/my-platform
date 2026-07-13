package com.example.common.exception;

import com.example.common.i18n.Messages;

public class NotFoundException extends BizException {

  public NotFoundException(String message) {
    super(404, message);
  }

  public NotFoundException(String message, String messageKey) {
    super(404, message, messageKey);
  }

  public static NotFoundException i18n(String key, Object... args) {
    return new NotFoundException(Messages.get(key, args), key);
  }

  @Deprecated
  public NotFoundException(String resource, Object id) {
    super(404, resource + " 不存在: " + id);
  }
}

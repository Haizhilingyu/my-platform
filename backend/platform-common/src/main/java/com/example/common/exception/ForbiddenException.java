package com.example.common.exception;

import com.example.common.i18n.Messages;

public class ForbiddenException extends BizException {

  public ForbiddenException(String message) {
    super(403, message);
  }

  public static ForbiddenException i18n(String key, Object... args) {
    return new ForbiddenException(Messages.get(key, args));
  }
}

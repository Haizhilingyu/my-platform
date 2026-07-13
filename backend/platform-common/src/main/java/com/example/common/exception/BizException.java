package com.example.common.exception;

import com.example.common.i18n.Messages;

public class BizException extends RuntimeException {

  private final int code;
  private final String messageKey;

  public BizException(int code, String message) {
    this(code, message, null);
  }

  public BizException(int code, String message, String messageKey) {
    super(message);
    this.code = code;
    this.messageKey = messageKey;
  }

  public BizException(String message) {
    this(500, message, null);
  }

  public static BizException i18n(String key, Object... args) {
    return i18n(500, key, args);
  }

  public static BizException i18n(int code, String key, Object... args) {
    return new BizException(code, Messages.get(key, args), key);
  }

  public int getCode() {
    return code;
  }

  public String getMessageKey() {
    return messageKey;
  }
}

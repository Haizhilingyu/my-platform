package com.example.common.exception;

/** 权限不足异常 (403)。 */
public class ForbiddenException extends BizException {

  public ForbiddenException(String message) {
    super(403, message);
  }
}

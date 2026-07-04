package com.example.common.exception;

/** 业务异常基类。所有模块的业务异常都继承此类。 */
public class BizException extends RuntimeException {

  private final int code;

  public BizException(int code, String message) {
    super(message);
    this.code = code;
  }

  public BizException(String message) {
    this(500, message);
  }

  public int getCode() {
    return code;
  }
}

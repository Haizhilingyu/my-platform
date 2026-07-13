package com.example.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一响应体。
 *
 * @param <T> 数据类型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Result<T>(int code, String message, T data, String messageKey) {

  private static final int SUCCESS_CODE = 200;
  private static final String SUCCESS_MSG = "success";

  /** 成功响应（带数据）。 */
  public static <T> Result<T> ok(T data) {
    return new Result<>(SUCCESS_CODE, SUCCESS_MSG, data, null);
  }

  /** 成功响应（无数据）。 */
  public static <T> Result<T> ok() {
    return new Result<>(SUCCESS_CODE, SUCCESS_MSG, null, null);
  }

  /** 失败响应。 */
  public static <T> Result<T> fail(int code, String message) {
    return new Result<>(code, message, null, null);
  }

  /** 失败响应（带数据，例如校验错误明细）。 */
  public static <T> Result<T> fail(int code, String message, T data) {
    return new Result<>(code, message, data, null);
  }

  /** 失败响应（带 i18n messageKey，前端按 key 翻译，无匹配时回退到 message）。 */
  public static <T> Result<T> fail(int code, String message, T data, String messageKey) {
    return new Result<>(code, message, data, messageKey);
  }

  public boolean isSuccess() {
    return this.code == SUCCESS_CODE;
  }
}

package com.example.common.exception;

/** 资源不存在异常 (404)。 */
public class NotFoundException extends BizException {

  public NotFoundException(String resource, Object id) {
    super(404, resource + " 不存在: " + id);
  }
}

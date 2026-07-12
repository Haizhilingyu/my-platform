package com.example.common.exception;

import com.example.common.i18n.Messages;

/** 资源不存在异常 (404)。推荐用 {@link Messages#get(String, Object...)} 预翻译后走单参构造器。 */
public class NotFoundException extends BizException {

  /**
   * @param message 已翻译的异常消息（例如 {@code Messages.get("error.resource.not.found", "用户", id)}）
   */
  public NotFoundException(String message) {
    super(404, message);
  }

  /**
   * @param resource 资源类型名称（如「用户」）
   * @param id 资源标识
   * @deprecated 使用 {@link #NotFoundException(String)} 配合 {@code
   *     Messages.get("error.resource.not.found", resource, id)} 代替，以支持 i18n。
   */
  @Deprecated
  public NotFoundException(String resource, Object id) {
    super(404, resource + " 不存在: " + id);
  }
}

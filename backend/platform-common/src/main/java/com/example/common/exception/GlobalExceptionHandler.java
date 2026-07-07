package com.example.common.exception;

import com.example.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 全局异常处理器。统一包装所有异常为标准 Result 格式。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(BizException.class)
  public ResponseEntity<Result<Void>> handleBiz(BizException ex, HttpServletRequest req) {
    log.warn("业务异常: {} {}", req.getRequestURI(), ex.getMessage());
    return ResponseEntity.status(ex.getCode()).body(Result.fail(ex.getCode(), ex.getMessage()));
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<Result<Void>> handleForbidden(ForbiddenException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Result.fail(403, ex.getMessage()));
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<Result<Void>> handleNotFound(NotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Result.fail(404, ex.getMessage()));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Result.fail(403, "无权限访问"));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Result<Map<String, String>>> handleValidation(
      MethodArgumentNotValidException ex) {
    Map<String, String> errors = new HashMap<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      errors.put(error.getField(), error.getDefaultMessage());
    }
    return ResponseEntity.badRequest().body(Result.fail(400, "参数校验失败", errors));
  }

  /**
   * 处理 {@code @Validated} 方法参数级约束校验失败（如 {@code @RequestParam @Min}）。
   *
   * <p>返回 400 与 {@link #handleValidation} 完全一致的 Result 信封结构。
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<Result<Map<String, String>>> handleConstraintViolation(
      ConstraintViolationException ex) {
    Map<String, String> errors = new HashMap<>();
    for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
      errors.put(violation.getPropertyPath().toString(), violation.getMessage());
    }
    return ResponseEntity.badRequest().body(Result.fail(400, "参数校验失败", errors));
  }

  /**
   * 处理请求体 JSON 不可读 / 无法解析的情况。
   *
   * <p>返回 400 + 通用「请求体格式错误」提示，不泄露内部类名等敏感信息，可安全用于生产环境。
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Result<Void>> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex) {
    return ResponseEntity.badRequest().body(Result.fail(400, "请求体格式错误"));
  }

  /**
   * 处理缺少必需的 {@code @RequestParam} 参数的情况。
   *
   * <p>返回 400 + 包含缺失参数名的提示信息。
   */
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<Result<Void>> handleMissingServletRequestParameter(
      MissingServletRequestParameterException ex) {
    return ResponseEntity.badRequest()
        .body(Result.fail(400, "缺少必需的请求参数: " + ex.getParameterName()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Result<Void>> handleGeneric(Exception ex, HttpServletRequest req) {
    log.error("系统异常: {} {}", req.getRequestURI(), ex.getMessage(), ex);
    return ResponseEntity.status(500).body(Result.fail(500, "系统内部错误"));
  }
}

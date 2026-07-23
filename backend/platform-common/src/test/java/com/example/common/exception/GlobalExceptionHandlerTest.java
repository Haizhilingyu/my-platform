package com.example.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;

@DisplayName("GlobalExceptionHandler i18n 解析")
class GlobalExceptionHandlerTest {

  private GlobalExceptionHandler handler;

  @BeforeEach
  void setUp() {
    ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
    ms.setBasename("classpath:i18n/messages");
    ms.setDefaultEncoding("UTF-8");
    ms.setFallbackToSystemLocale(false);
    ms.setUseCodeAsDefaultMessage(true);
    handler = new GlobalExceptionHandler(ms);
  }

  @AfterEach
  void tearDown() {
    LocaleContextHolder.resetLocaleContext();
  }

  @Test
  @DisplayName("AccessDeniedException：中文 locale 解析 error.access.denied")
  void should_resolveZh_when_accessDenied() {
    LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);

    ResponseEntity<Result<Void>> resp =
        handler.handleAccessDenied(new AccessDeniedException("denied"));

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(resp.getBody().code()).isEqualTo(403);
    assertThat(resp.getBody().message()).isEqualTo("无权限访问");
  }

  @Test
  @DisplayName("AccessDeniedException：英文 locale 解析 error.access.denied")
  void should_resolveEn_when_accessDenied() {
    LocaleContextHolder.setLocale(Locale.ENGLISH);

    ResponseEntity<Result<Void>> resp =
        handler.handleAccessDenied(new AccessDeniedException("denied"));

    assertThat(resp.getBody().message()).isEqualTo("Access denied");
  }

  @Test
  @DisplayName("HttpMessageNotReadableException：解析 error.body.malformed")
  void should_resolveI18n_when_bodyMalformed() {
    LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);

    ResponseEntity<Result<Void>> resp =
        handler.handleHttpMessageNotReadable(
            new HttpMessageNotReadableException("bad", mock(HttpInputMessage.class)));

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(resp.getBody().code()).isEqualTo(400);
    assertThat(resp.getBody().message()).isEqualTo("请求体格式错误");
  }

  @Test
  @DisplayName("MissingServletRequestParameterException：解析 error.param.missing 含参数名")
  void should_resolveI18n_when_missingParam() {
    LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);

    ResponseEntity<Result<Void>> resp =
        handler.handleMissingServletRequestParameter(
            new MissingServletRequestParameterException("username", "String"));

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(resp.getBody().message()).isEqualTo("缺少必需的请求参数: username");
  }

  @Test
  @DisplayName("MissingServletRequestParameterException：英文 locale 插值参数名")
  void should_resolveEn_when_missingParam() {
    LocaleContextHolder.setLocale(Locale.ENGLISH);

    ResponseEntity<Result<Void>> resp =
        handler.handleMissingServletRequestParameter(
            new MissingServletRequestParameterException("id", "Long"));

    assertThat(resp.getBody().message()).isEqualTo("Missing required request parameter: id");
  }

  @Test
  @DisplayName("generic Exception：解析 error.system")
  void should_resolveI18n_when_genericException() {
    LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getRequestURI()).thenReturn("/api/test");

    ResponseEntity<Result<Void>> resp = handler.handleGeneric(new RuntimeException("boom"), req);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(resp.getBody().code()).isEqualTo(500);
    assertThat(resp.getBody().message()).isEqualTo("系统内部错误");
  }

  @Test
  @DisplayName("generic Exception：英文 locale 解析 error.system")
  void should_resolveEn_when_genericException() {
    LocaleContextHolder.setLocale(Locale.ENGLISH);
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getRequestURI()).thenReturn("/api/test");

    ResponseEntity<Result<Void>> resp = handler.handleGeneric(new RuntimeException("boom"), req);

    assertThat(resp.getBody().message()).isEqualTo("Internal server error");
  }

  @Test
  @DisplayName("NotFoundException：透传预翻译消息")
  void should_passThrough_when_notFound() {
    ResponseEntity<Result<Void>> resp = handler.handleNotFound(new NotFoundException("用户 不存在: 42"));

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(resp.getBody().code()).isEqualTo(404);
    assertThat(resp.getBody().message()).isEqualTo("用户 不存在: 42");
  }

  @Test
  @DisplayName("ForbiddenException：透传预翻译消息")
  void should_passThrough_when_forbidden() {
    ResponseEntity<Result<Void>> resp = handler.handleForbidden(new ForbiddenException("nope"));

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(resp.getBody().message()).isEqualTo("nope");
  }
}

package com.example.app.web;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * SPA 路由 fallback：拦截 {@link NoResourceFoundException}，将非 API/基础设施路径转发到
 * {@code /index.html}（由 Spring Boot 静态资源处理器服务，Vue Router 接管客户端路由）。
 *
 * <p><b>为什么需要这个类</b>：Spring Boot 3.2+ 的静态资源处理器在找不到文件时抛出
 * {@code NoResourceFoundException}。{@code GlobalExceptionHandler}（{@code @RestControllerAdvice}）
 * 有一个 catch-all {@code @ExceptionHandler(Exception.class)} 会捕获它并返回 500 JSON，
 * 导致 SPA 深链接（如 {@code /sys/user}、{@code /dashboard}）返回 500 而非 index.html。
 *
 * <p>本类以 {@link Ordered#HIGHEST_PRECEDENCE} 运行，优先于 {@code GlobalExceptionHandler}。
 * 注意：必须是 {@code @ControllerAdvice}（非 {@code @RestControllerAdvice}），因为
 * {@code @RestControllerAdvice} 会把返回的 String 当作 JSON body 而非视图名。
 *
 * <p>对于 API/基础设施路径的 {@code NoResourceFoundException}，返回 404 JSON（不转发到 SPA）。
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SpaForwardHandler {

  /**
   * 处理静态资源未找到异常。
   *
   * <p>SPA 路由（如 {@code /login}、{@code /dashboard}、{@code /sys/user}）→ {@code forward:/index.html}
   * （HTTP 200，Vue Router 接管）。 API/基础设施路径 → 404 JSON（避免把 HTML 当作 API 响应）。
   */
  @ExceptionHandler(NoResourceFoundException.class)
  public Object handleNoResourceFound(HttpServletRequest request, NoResourceFoundException ex) {
    String uri = request.getRequestURI();
    if (uri != null && !isApiOrInfrastructurePath(uri)) {
      // SPA 路由 → 转发到 index.html（返回视图名，@ControllerAdvice 支持）
      return "forward:/index.html";
    }
    // API/基础设施路径 → 404 JSON
    String body =
        "{\"timestamp\":\""
            + Instant.now()
            + "\",\"status\":404,\"error\":\"Not Found\",\"message\":\""
            + (ex.getMessage() != null ? ex.getMessage().replace("\"", "'") : "resource not found")
            + "\",\"path\":\""
            + (uri != null ? uri : "")
            + "\"}";
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .header("Content-Type", "application/json;charset=UTF-8")
        .body(body);
  }

  /**
   * 判断是否为 API/基础设施路径（不应 fallback 到 SPA）。
   *
   * <p>这些前缀的请求 404 应返回 JSON 404，而非 index.html。
   */
  private boolean isApiOrInfrastructurePath(String uri) {
    return uri.startsWith("/api/")
        || uri.startsWith("/openapi/")
        || uri.startsWith("/oauth2/")
        || uri.startsWith("/.well-known/")
        || uri.startsWith("/ws/")
        || uri.startsWith("/v3/api-docs")
        || uri.startsWith("/swagger-ui")
        || uri.startsWith("/actuator/")
        || uri.startsWith("/doc/");
  }
}

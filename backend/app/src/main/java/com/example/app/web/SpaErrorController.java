package com.example.app.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.error.ErrorAttributeOptions.Include;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

/**
 * SPA fallback + API 错误处理。
 *
 * <p>当请求未匹配任何 controller 且静态资源也不存在时，Spring Boot 转发到 {@code /error}。 本类区分两类请求：
 *
 * <ol>
 *   <li>SPA 页面路由（如 {@code /login}、{@code /dashboard}、{@code /sys/user}）→ forward 到 {@code
 *       /index.html}，返回 200，让 Vue Router 接管渲染。
 *   <li>API/基础设施路径（{@code /api/**}、{@code /openapi/**}、{@code /oauth2/**}、{@code /ws/**} 等）→ 返回
 *       JSON 错误（{@link DefaultErrorAttributes} 格式），避免把 SPA HTML 当作 API 响应。
 * </ol>
 *
 * <p>必须配合 {@code application.yml} 中 {@code spring.mvc.throw-exception-if-no-handler-found=true}
 * 使用；否则未知路径会被静态资源处理器吞掉而非转发到 {@code /error}。
 *
 * <p>本 {@code @Controller} + {@code @RequestMapping("/error")} 会覆盖 Spring Boot 默认的 {@code
 * BasicErrorController}（后者由 {@code @ConditionalOnMissingBean(ErrorController.class)} 守卫）。
 *
 * <p><b>循环守卫</b>：当 {@code /index.html} 本身缺失（前端未构建 / static 目录空）时，SPA 路由 404 → forward 到 {@code
 * /index.html} → 又 404 → 重新进入 {@code /error} → 无限递归。 本类在方法入口检测 {@code ERROR_REQUEST_URI ==
 * "/index.html"}，命中则直接返回 404 JSON，打破循环。
 */
@Controller
public class SpaErrorController implements ErrorController {

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(SpaErrorController.class);

  private final DefaultErrorAttributes errorAttributes;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public SpaErrorController(DefaultErrorAttributes errorAttributes) {
    this.errorAttributes = errorAttributes;
  }

  @RequestMapping("/error")
  public void handleError(HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    Object statusObj = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
    int status = statusObj != null ? Integer.parseInt(statusObj.toString()) : 500;
    String requestUri = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);

    // 循环守卫：如果 /index.html 本身触发了错误（前端未构建 / static 目录空），
    // 直接返回 404 JSON，避免 forward(/index.html) → 404 → /error → forward(/index.html) 无限递归。
    if ("/index.html".equals(requestUri)) {
      response.setStatus(404);
      response.setContentType("application/json;charset=UTF-8");
      response
          .getWriter()
          .write(
              "{\"timestamp\":\""
                  + java.time.Instant.now()
                  + "\",\"status\":404,\"error\":\"Not Found\","
                  + "\"message\":\"index.html not found (frontend not built)\",\"path\":\"/index.html\"}");
      return;
    }

    // 404 on SPA route → forward to index.html (return 200 so browser loads SPA)
    if (status == 404 && !isApiOrInfrastructurePath(requestUri)) {
      response.setStatus(200);
      request.getRequestDispatcher("/index.html").forward(request, response);
      return;
    }

    // API / infrastructure errors → JSON error response (DefaultErrorAttributes format)
    WebRequest webRequest = new ServletWebRequest(request);
    Map<String, Object> body =
        errorAttributes.getErrorAttributes(webRequest, ErrorAttributeOptions.of(Include.MESSAGE));
    if (log.isDebugEnabled()) {
      log.debug("error-handler status={} uri={} body={}", status, requestUri, body);
    }
    response.setStatus(status);
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write(objectMapper.writeValueAsString(body));
  }

  /**
   * 判断是否为 API/基础设施路径（不应 fallback 到 SPA）。
   *
   * <p>这些前缀的请求 404 应返回 JSON 404，而非 index.html。
   */
  private boolean isApiOrInfrastructurePath(String uri) {
    if (uri == null) {
      return false;
    }
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

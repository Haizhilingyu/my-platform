package com.example.common.security;

import com.example.common.exception.ForbiddenException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.stereotype.Component;

/**
 * OAuth2 scope 校验切面。拦截所有标注了 {@link RequiresAppScope} 的方法。
 *
 * <p>从 {@link SecurityContextHolder} 取出当前认证主体，要求其为 {@link OAuth2AuthenticatedPrincipal}（OAuth2
 * Resource Server JWT 认证后的标准形态），再从 attributes 读取 {@code scope}。Spring 会将 access_token 中的 {@code
 * scope} claim（无论原是空格分隔字符串还是 JSON 数组）统一以 {@code Collection<String>} 形式放入 attributes。
 */
@Aspect
@Component
public class AppScopeAspect {

  @Around("@annotation(requiresAppScope)")
  public Object checkScope(ProceedingJoinPoint joinPoint, RequiresAppScope requiresAppScope)
      throws Throwable {
    String required = requiresAppScope.value();
    if (required == null || required.isBlank()) {
      return joinPoint.proceed();
    }

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
      throw ForbiddenException.i18n("error.scope.no.token");
    }

    Object principal = auth.getPrincipal();
    if (!(principal instanceof OAuth2AuthenticatedPrincipal oauthPrincipal)) {
      throw ForbiddenException.i18n("error.scope.not.oauth2");
    }

    List<String> scopes = extractScopes(oauthPrincipal);
    if (!scopes.contains(required)) {
      throw ForbiddenException.i18n("error.scope.missing", required);
    }

    return joinPoint.proceed();
  }

  private static List<String> extractScopes(OAuth2AuthenticatedPrincipal principal) {
    Object scope = principal.getAttributes().get("scope");
    if (scope == null) {
      return List.of();
    }
    if (scope instanceof Collection<?> col) {
      return col.stream().filter(Objects::nonNull).map(String::valueOf).toList();
    }
    String str = String.valueOf(scope);
    if (str.isBlank()) {
      return List.of();
    }
    return List.of(str.split("\\s+"));
  }
}

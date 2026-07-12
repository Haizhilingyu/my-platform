package com.example.common.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.common.exception.ForbiddenException;
import com.example.common.i18n.Messages;
import java.util.List;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth2 scope 切面")
class AppScopeAspectTest {

  @Mock private ProceedingJoinPoint joinPoint;
  @InjectMocks private AppScopeAspect aspect;

  @BeforeEach
  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
    ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
    ms.setBasename("classpath:i18n/messages");
    ms.setDefaultEncoding("UTF-8");
    ms.setFallbackToSystemLocale(false);
    ms.setUseCodeAsDefaultMessage(true);
    new Messages(ms).init();
  }

  private void withOAuthPrincipal(Object scopeValue) {
    Map<String, Object> attrs =
        scopeValue == null
            ? Map.of("sub", "test-client")
            : Map.of("scope", scopeValue, "sub", "test-client");
    OAuth2AuthenticatedPrincipal principal =
        new DefaultOAuth2AuthenticatedPrincipal(
            "test-client", attrs, AuthorityUtils.createAuthorityList("ROLE_CLIENT"));
    Authentication auth =
        new org.springframework.security.authentication.TestingAuthenticationToken(
            principal, null, "ROLE_CLIENT");
    auth.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @Test
  @DisplayName("scope 命中（Collection 形式）：放行执行")
  void should_proceed_when_scopeCollectionContainsRequired() throws Throwable {
    withOAuthPrincipal(List.of("notify:publish", "openid"));
    var annotation = mock(RequiresAppScope.class);
    when(annotation.value()).thenReturn("notify:publish");
    when(joinPoint.proceed()).thenReturn("OK");

    Object result = aspect.checkScope(joinPoint, annotation);

    assertThat(result).isEqualTo("OK");
    verify(joinPoint).proceed();
  }

  @Test
  @DisplayName("scope 命中（空格分隔字符串形式）：放行执行")
  void should_proceed_when_scopeStringContainsRequired() throws Throwable {
    withOAuthPrincipal("openid notify:publish");
    var annotation = mock(RequiresAppScope.class);
    when(annotation.value()).thenReturn("notify:publish");
    when(joinPoint.proceed()).thenReturn("OK");

    Object result = aspect.checkScope(joinPoint, annotation);

    assertThat(result).isEqualTo("OK");
    verify(joinPoint).proceed();
  }

  @Test
  @DisplayName("scope 缺失：抛 ForbiddenException")
  void should_throwForbidden_when_scopeMissing() {
    withOAuthPrincipal(List.of("openid"));
    var annotation = mock(RequiresAppScope.class);
    when(annotation.value()).thenReturn("notify:publish");

    assertThatThrownBy(() -> aspect.checkScope(joinPoint, annotation))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("notify:publish");
  }

  @Test
  @DisplayName("无 scope claim：抛 ForbiddenException")
  void should_throwForbidden_when_noScopeClaim() {
    withOAuthPrincipal(null);
    var annotation = mock(RequiresAppScope.class);
    when(annotation.value()).thenReturn("notify:publish");

    assertThatThrownBy(() -> aspect.checkScope(joinPoint, annotation))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  @DisplayName("principal 非 OAuth2AuthenticatedPrincipal：抛 ForbiddenException")
  void should_throwForbidden_when_notOAuthPrincipal() {
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            "admin", "creds", AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
    SecurityContextHolder.getContext().setAuthentication(auth);
    var annotation = mock(RequiresAppScope.class);
    when(annotation.value()).thenReturn("notify:publish");

    assertThatThrownBy(() -> aspect.checkScope(joinPoint, annotation))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("OAuth2");
  }

  @Test
  @DisplayName("匿名 token：抛 ForbiddenException")
  void should_throwForbidden_when_anonymous() {
    AnonymousAuthenticationToken anon =
        new AnonymousAuthenticationToken(
            "k", "anon", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
    SecurityContextHolder.getContext().setAuthentication(anon);
    var annotation = mock(RequiresAppScope.class);
    when(annotation.value()).thenReturn("notify:publish");

    assertThatThrownBy(() -> aspect.checkScope(joinPoint, annotation))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  @DisplayName("注解 value 为空字符串：直接放行（避免误用导致全锁死）")
  void should_proceed_when_annotationValueBlank() throws Throwable {
    withOAuthPrincipal(List.of("openid"));
    var annotation = mock(RequiresAppScope.class);
    when(annotation.value()).thenReturn("");
    when(joinPoint.proceed()).thenReturn("OK");

    Object result = aspect.checkScope(joinPoint, annotation);

    assertThat(result).isEqualTo("OK");
    verify(joinPoint).proceed();
  }
}

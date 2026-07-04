package com.example.common.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link LoginMethodRegistry} 单元测试。
 *
 * <p>覆盖：多提供者注册、按 order 升序、disabled 过滤、method 查找（命中/未知/禁用/null）。
 */
@DisplayName("LoginMethodRegistry 注册表")
class LoginMethodRegistryTest {

    private LoginMethodProvider provider(String method, boolean enabled, int order) {
        LoginMethodProvider p = mock(LoginMethodProvider.class);
        when(p.getMethod()).thenReturn(method);
        when(p.isEnabled()).thenReturn(enabled);
        when(p.getOrder()).thenReturn(order);
        when(p.describe())
                .thenReturn(new LoginMethodDescriptor(method, method + "-label", method + "-icon", order));
        return p;
    }

    @Test
    @DisplayName("多提供者注册：getEnabledMethods 按 order 升序排列")
    void getEnabledMethods_shouldSortByOrderAscending() {
        LoginMethodRegistry registry =
                new LoginMethodRegistry(
                        List.of(
                                provider("password", true, 100),
                                provider("ldap", true, 50),
                                provider("sso", true, 30)));

        List<LoginMethodDescriptor> methods = registry.getEnabledMethods();

        assertThat(methods)
                .extracting(LoginMethodDescriptor::method)
                .containsExactly("sso", "ldap", "password");
    }

    @Test
    @DisplayName("disabled 提供者不出现在 getEnabledMethods 中")
    void getEnabledMethods_shouldExcludeDisabled() {
        LoginMethodRegistry registry =
                new LoginMethodRegistry(
                        List.of(
                                provider("password", true, 100),
                                provider("ldap", false, 50)));

        List<LoginMethodDescriptor> methods = registry.getEnabledMethods();

        assertThat(methods).extracting(LoginMethodDescriptor::method).containsExactly("password");
    }

    @Test
    @DisplayName("getProvider 按 method 命中已启用提供者")
    void getProvider_shouldReturnEnabledProviderByMethod() {
        LoginMethodProvider password = provider("password", true, 100);
        LoginMethodRegistry registry = new LoginMethodRegistry(List.of(password));

        LoginMethodProvider resolved = registry.getProvider("password");

        assertThat(resolved).isSameAs(password);
    }

    @Test
    @DisplayName("getProvider 对未知 method 返回 null")
    void getProvider_shouldReturnNullForUnknownMethod() {
        LoginMethodRegistry registry =
                new LoginMethodRegistry(List.of(provider("password", true, 100)));

        assertThat(registry.getProvider("ldap")).isNull();
    }

    @Test
    @DisplayName("getProvider 对禁用 method 返回 null")
    void getProvider_shouldReturnNullForDisabledMethod() {
        LoginMethodRegistry registry =
                new LoginMethodRegistry(List.of(provider("ldap", false, 50)));

        assertThat(registry.getProvider("ldap")).isNull();
    }

    @Test
    @DisplayName("getProvider 对 null method 返回 null")
    void getProvider_shouldReturnNullForNullMethod() {
        LoginMethodRegistry registry =
                new LoginMethodRegistry(List.of(provider("password", true, 100)));

        assertThat(registry.getProvider(null)).isNull();
    }

    @Test
    @DisplayName("getEnabledMethodNames 返回启用方法集合，按 order 升序保序")
    void getEnabledMethodNames_shouldReturnOrderedSetOfEnabledMethods() {
        LoginMethodRegistry registry =
                new LoginMethodRegistry(
                        List.of(
                                provider("password", true, 100),
                                provider("ldap", true, 50),
                                provider("disabled", false, 10)));

        Set<String> names = registry.getEnabledMethodNames();

        assertThat(names).containsExactly("ldap", "password");
    }

    @Test
    @DisplayName("空注册表：所有查询返回空/null")
    void emptyRegistry_shouldReturnEmptyResults() {
        LoginMethodRegistry registry = new LoginMethodRegistry(List.of());

        assertThat(registry.getEnabledMethods()).isEmpty();
        assertThat(registry.getEnabledMethodNames()).isEmpty();
        assertThat(registry.getProvider("password")).isNull();
    }
}

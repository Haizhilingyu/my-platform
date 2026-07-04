package com.example.loginldap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.ldap.core.ContextSource;
import org.springframework.ldap.core.LdapTemplate;

/**
 * LdapLoginAutoConfiguration 条件装配测试。
 *
 * <p>验证 {@code @ConditionalOnProperty(enabled=true)} 语义：
 *
 * <ul>
 *   <li>enabled 未设置（默认 false）→ 不注册任何 LDAP bean（provider/contextSource/ldapTemplate）。
 *   <li>用 ApplicationContextRunner（轻量，无需启动完整应用上下文）。
 * </ul>
 *
 * <p>enabled=true 的实际装配由 app 模块的 ApplicationContextLoadsTest（profile=test， ldap 默认关闭）间接覆盖；provider
 * 业务正确性由 {@link LdapLoginProviderTest} 覆盖。
 */
@DisplayName("LDAP 自动配置条件装配")
class LdapLoginAutoConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner();

  @Test
  @DisplayName("enabled 未设置（默认 false）→ 无任何 LDAP bean")
  void whenDisabled_noLdapBeansRegistered() {
    runner
        .withConfiguration(AutoConfigurations.of(LdapLoginAutoConfiguration.class))
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(LdapLoginProvider.class);
              assertThat(context).doesNotHaveBean(ContextSource.class);
              assertThat(context).doesNotHaveBean(LdapTemplate.class);
            });
  }

  @Test
  @DisplayName("enabled=false 显式关闭 → 无任何 LDAP bean")
  void whenEnabledFalse_noLdapBeansRegistered() {
    runner
        .withConfiguration(AutoConfigurations.of(LdapLoginAutoConfiguration.class))
        .withPropertyValues("platform.login.ldap.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(LdapLoginProvider.class);
              assertThat(context).doesNotHaveBean(ContextSource.class);
            });
  }
}

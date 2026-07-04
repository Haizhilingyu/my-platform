package com.example.loginldap;

import com.example.common.security.JwtUtil;
import com.example.sys.repository.SysRoleRepository;
import com.example.sys.repository.SysUserRepository;
import com.example.sys.repository.SysUserRoleRepository;
import com.example.sys.service.PermissionService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.ldap.core.ContextSource;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;

/**
 * LDAP 登录自动配置。
 *
 * <p>仅当 {@code platform.login.ldap.enabled=true} 时激活（{@link ConditionalOnProperty}）。 激活时注册：
 *
 * <ul>
 *   <li>{@link ContextSource}（{@link LdapContextSource}）：无 base、无 manager 凭证， 仅用于用户 bind（{@code
 *       getContext(userDn, password)}）。
 *   <li>{@link LdapTemplate}：基于上述 ContextSource，供后续扩展（高阶 LDAP 操作）。
 *   <li>{@link LdapLoginProvider}：登录方式提供者，自动收集到 LoginMethodRegistry。
 * </ul>
 *
 * <p>默认 disabled，未开启时不创建任何 LDAP bean，{@code /login-methods} 不含 ldap 方式。
 */
@AutoConfiguration
@EnableConfigurationProperties(LdapLoginProperties.class)
@ConditionalOnProperty(prefix = "platform.login.ldap", name = "enabled", havingValue = "true")
public class LdapLoginAutoConfiguration {

  @Bean
  public ContextSource ldapContextSource(LdapLoginProperties properties) {
    LdapContextSource source = new LdapContextSource();
    source.setUrl(properties.getUrl());
    // 不设置 base / manager 凭证：仅用于 getContext(userDn, password) 的用户 bind。
    source.afterPropertiesSet();
    return source;
  }

  @Bean
  public LdapTemplate ldapTemplate(ContextSource ldapContextSource) {
    return new LdapTemplate(ldapContextSource);
  }

  @Bean
  public LdapLoginProvider ldapLoginProvider(
      LdapLoginProperties properties,
      ContextSource ldapContextSource,
      SysUserRepository userRepository,
      SysRoleRepository roleRepository,
      SysUserRoleRepository userRoleRepository,
      PermissionService permissionService,
      JwtUtil jwtUtil,
      ApplicationEventPublisher eventPublisher) {
    return new LdapLoginProvider(
        properties,
        ldapContextSource,
        userRepository,
        roleRepository,
        userRoleRepository,
        permissionService,
        jwtUtil,
        eventPublisher);
  }
}

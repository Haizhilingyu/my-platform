package com.example.loginldap;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LDAP 登录配置属性。
 *
 * <p>前缀 {@code platform.login.ldap}，对应 application.yml：
 *
 * <pre>{@code
 * platform:
 *   login:
 *     ldap:
 *       enabled: false              # 总开关，默认关闭，需显式开启
 *       url: ldap://<NAS_IP>:389
 *       user-dn-pattern: uid={0},dc=devenv,dc=local
 *       auto-create-user: true       # 首次 LDAP 登录自动创建本地 SysUser
 *       default-role-code: user      # 自动创建用户的默认角色编码
 * }</pre>
 *
 * <p>{@code user-dn-pattern} 中的 {@code {0}} 占位符由用户名替换，语义对齐 Spring Security LDAP 的 {@code
 * userDnPatterns}。{@code enabled} 默认 false，确保未配置环境不激活 LDAP。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "platform.login.ldap")
public class LdapLoginProperties {

  /** LDAP 服务器地址（含端口），如 {@code ldap://<NAS_IP>:389}。 */
  private String url = "ldap://localhost:389";

  /**
   * 用户 DN 模板，{@code {0}} 为用户名占位符。 例：{@code uid={0},dc=devenv,dc=local} → {@code
   * uid=hai,dc=devenv,dc=local}。
   */
  private String userDnPattern = "uid={0},dc=devenv,dc=local";

  /** 是否启用 LDAP 登录方式。默认 false —— 必须显式置 true 才激活。 */
  private boolean enabled = false;

  /** 首次 LDAP 登录且本地无对应用户时，是否自动创建本地 SysUser。默认 true。 */
  private boolean autoCreateUser = true;

  /** 自动创建用户分配的默认角色编码（需 sys_role 中存在且启用）。默认 "user"。 */
  private String defaultRoleCode = "user";
}

# Login LDAP 模块 (login-ldap)

## 功能概述
提供基于 **OpenLDAP** 用户绑定认证的登录方式（`LoginMethodProvider` SPI 实现）。
用户在 LDAP 服务器（如 `ldap://192.168.1.2:389`）中维护账号密码，登录时通过 LDAP bind 校验凭据；
认证通过后查/建本地 `sys_user`，生成与账号密码登录完全一致的 JWT + `LoginVO`。

**只读认证**：本模块只做 LDAP bind 校验和属性读取，不向 LDAP 写任何数据。

## 依赖
- platform-common（`LoginMethodProvider` SPI、`JwtUtil`、`LoginSuccessEvent`）
- sys-module（`SysUser`/`SysRole`/`SysUserRole` 实体与 Repository、`PermissionService`、`LoginVO`）
- spring-ldap-core（版本 3.2.7，由 spring-boot-dependencies BOM 管理）
- OpenLDAP 服务器（运行时外部依赖，默认 `192.168.1.2:389`）

## Maven 依赖
```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>login-ldap-module</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

> 通常通过 `platform-starter` 传递引入，无需单独声明。

## 自动装配
- 入口：`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` → `LdapLoginAutoConfiguration`
- 激活条件：`@ConditionalOnProperty(prefix="platform.login.ldap", name="enabled", havingValue="true")`
- **默认 disabled**（`enabled=false`）。未开启时不注册任何 LDAP bean，`/sys/auth/login-methods` 不含 ldap 方式。
- 激活时注册：`ContextSource`（`LdapContextSource`，无 manager 凭证）、`LdapTemplate`、`LdapLoginProvider`。

## 配置项
| 属性 | 默认值 | 说明 |
|---|---|---|
| `platform.login.ldap.enabled` | `false` | **总开关**，必须显式置 `true` 才启用 |
| `platform.login.ldap.url` | `ldap://localhost:389` | LDAP 服务器地址（建议 `ldap://192.168.1.2:389`） |
| `platform.login.ldap.user-dn-pattern` | `uid={0},dc=devenv,dc=local` | 用户 DN 模板，`{0}` 替换为用户名 |
| `platform.login.ldap.auto-create-user` | `true` | 首次 LDAP 登录且本地无用户时自动创建本地 SysUser |
| `platform.login.ldap.default-role-code` | `user` | 自动建号分配的默认角色编码（需 sys_role 中存在且启用） |

application.yml（环境变量注入）：
```yaml
platform:
  login:
    ldap:
      enabled: ${PLATFORM_LOGIN_LDAP_ENABLED:false}
      url: ${PLATFORM_LOGIN_LDAP_URL:ldap://192.168.1.2:389}
      user-dn-pattern: ${PLATFORM_LOGIN_LDAP_USER_DN_PATTERN:uid={0},dc=devenv,dc=local}
      auto-create-user: ${PLATFORM_LOGIN_LDAP_AUTO_CREATE_USER:true}
      default-role-code: ${PLATFORM_LOGIN_LDAP_DEFAULT_ROLE_CODE:user}
```

## OpenLDAP 连接信息（开发环境）
- 地址：`192.168.1.2:389`
- Base DN：`dc=devenv,dc=local`
- 管理员 DN：`cn=admin,dc=devenv,dc=local`（密码 `LDAP@2025`，仅 LDAP 运维用，本模块不使用）
- 测试用户：`uid=hai`（即 `uid=hai,dc=devenv,dc=local`）

## 认证流程
1. 前端 POST `/sys/auth/login`，body `{"method":"ldap","username":"hai","password":"..."}`。
2. `AuthController` 路由到 `LdapLoginProvider.authenticate`。
3. 拼用户 DN：`uid=hai,dc=devenv,dc=local`（`{0}` 替换）。
4. LDAP bind：`contextSource.getContext(userDn, password)`。
   - 成功 → 认证通过，读取 `mail`/`cn` 属性。
   - `AuthenticationException` → 401「LDAP 认证失败：用户名或密码错误」。
5. 查本地 `sys_user`：
   - 存在 → 校验 status；禁用 → 403。
   - 不存在 + `auto-create-user=true` → 自动建号（username=uid, email=mail, realName=cn, status=1, 随机占位密码），分配 `default-role-code` 角色。
   - 不存在 + `auto-create-user=false` → 401「用户不存在且未开启自动创建」。
6. 加载角色 → 生成 JWT（含 unitId/jti）→ 发布 `LoginSuccessEvent` → 返回 `LoginVO`（结构同密码登录）。

## 自动创建用户行为
- LDAP 用户的密码由 LDAP 管理，本地 `sys_user.password` 置随机 UUID 占位值，**不可用于本地密码登录**。
- `realName` 取 LDAP `cn`，缺失则回退为用户名；`email` 取 LDAP `mail`，缺失为 null。
- 仅首次登录建号；后续登录复用本地记录。

## REST 交互
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /sys/auth/login | body 指定 `"method":"ldap"` |
| GET | /sys/auth/login-methods | enabled=true 时返回含 ldap 描述符 |

## 测试
- 单元测试（`LdapLoginProviderTest`）：mock `ContextSource`，覆盖认证成功/失败、自动建号、禁用用户、空凭据。
- 条件装配测试（`LdapLoginAutoConfigurationTest`）：`ApplicationContextRunner` 验证 `enabled=false` 时不注册任何 LDAP bean。
- `mvn test -Dspring.profiles.active=test`：LDAP 默认 disabled，无需运行 LDAP 服务器。

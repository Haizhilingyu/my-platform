# 外部应用/开放平台模块 (openapp)

## 功能概述
基于 Spring Authorization Server 的 OAuth2 授权服务器，为外部应用提供开放 API 接入能力：
- OAuth2 授权码（authorization_code）、刷新令牌（refresh_token）、客户端凭据（client_credentials）
- OpenID Connect 1.0（UserInfo、RP-Initiated Logout、Discovery）
- **持久化 JWK**（数据库存储，非内存）+ 定时密钥轮转（active → grace → expired）
- `/openapi/**` 资源服务器（校验外部应用 access_token）
- `@RequiresAppScope` 注解校验外部应用 scope
- 外部应用 CRUD 管理 API

## 关键设计：持久化 JWK（生产强制要求）
JWK 私钥以 AES 加密后存入 `openapp_jwk` 表。多副本从同一数据库加载相同密钥，
避免内存 JWK 在 HA 环境下各副本密钥不一致导致 token 校验失败（生产定时炸弹）。
加密密钥来自环境变量 `JWK_ENCRYPTION_KEY`。定时任务每周一 3:00 生成新密钥，
旧密钥进入 30 天宽限期（grace，仍可校验旧 token），过期后标记 expired。

## 依赖
- platform-common（异常、CurrentUser）
- Spring Authorization Server 1.3.x（对齐 Spring Boot 3.3 / Spring Security 6.3）
- PostgreSQL + Flyway

## 三条安全过滤链（与 platform-security 共存）
| Order | 链 | 匹配路径 | 认证方式 |
|---|---|---|---|
| 1 | 授权服务器 | `/oauth2/**`、`/.well-known/**`、`/userinfo`、`/connect/logout` | OAuth2 AS |
| 2 | 资源服务器 | `/openapi/**` | OAuth2 access_token (JWT/JWK) |
| 兜底 | platform-security | 其余（`/sys/**` 等） | 内部用户 JWT |

## Maven 依赖
```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>openapp-module</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## REST API

### 授权服务器端点（自动暴露）
| 端点 | 说明 |
|---|---|
| GET `/.well-known/openid-configuration` | OIDC Discovery |
| GET `/oauth2/jwks` | JWK Set 公钥 |
| POST `/oauth2/token` | 令牌端点 |
| GET `/oauth2/authorize` | 授权端点 |
| GET `/userinfo` | OIDC UserInfo |
| GET `/connect/logout` | RP-Initiated Logout |

### 开放 API（外部应用，`/openapi/**`）
标注 `@RequiresAppScope("xxx")` 的方法校验 access_token 的 scope。

### 外部应用管理（管理员，`/sys/openapp/clients`）
| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | /sys/openapp/clients | sys:openapp:list | 列表 |
| GET | /sys/openapp/clients/{id} | sys:openapp:list | 详情 |
| POST | /sys/openapp/clients | sys:openapp:add | 新增（返回明文 client_secret 仅一次） |
| PUT | /sys/openapp/clients/{id} | sys:openapp:edit | 修改 |
| DELETE | /sys/openapp/clients/{id} | sys:openapp:delete | 删除 |
| POST | /sys/openapp/clients/{id}/reset-secret | sys:openapp:edit | 重置密钥 |

## 数据库表
- `openapp_client` — 外部应用（client_secret 存 BCrypt 哈希）
- `oauth_authorization` — OAuth2 授权记录
- `openapp_jwk` — 持久化 JWK 密钥（AES 加密）

## 可配置项
| 属性 | 默认值 | 说明 |
|---|---|---|
| app.openapp.issuer | http://localhost:8090 | 授权服务器 issuer |
| app.openapp.jwk-encryption-key | (env JWK_ENCRYPTION_KEY) | JWK 私钥 AES 加密密钥 |
| app.openapp.jwk-grace-days | 30 | 旧密钥宽限期天数 |

## 集成步骤
1. 引入 `openapp-module`（platform-starter 已聚合）
2. 配置 `JWK_ENCRYPTION_KEY` 环境变量（生产必填，否则用内置默认值）
3. Flyway 自动建表
4. 管理员通过 `/sys/openapp/clients` 创建外部应用，获取 client_id + client_secret
5. 外部应用凭 client_id/secret 调用 `/oauth2/token`（client_credentials）换取 access_token
6. 携带 `Authorization: Bearer {access_token}` 调用 `/openapi/**`

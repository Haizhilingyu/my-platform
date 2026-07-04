# Platform Security

## 功能概述
安全基础设施模块，提供 Spring Security 配置和 JWT 认证过滤器。
通过 Spring Boot AutoConfiguration SPI 自动装配，应用引入 `platform-starter` 即获得完整的安全链路。

## 依赖
- platform-common（JwtUtil、CurrentUser、PermissionLoader SPI）
- spring-boot-starter-security
- jjwt-api

## 提供的核心能力

### 自动装配
- `SecurityAutoConfiguration` — AutoConfiguration 入口，classpath 存在 Spring Security + JwtUtil 时激活
- `SecurityConfig` — SecurityFilterChain（JWT 无状态认证 + 方法级权限 + PUBLIC_PATHS 白名单）
- `JwtAuthFilter` — 解析 JWT token，填充 CurrentUser 上下文（userId/username/unitId/roles/permissions）

### 安全链路
1. 请求到达 → JwtAuthFilter 解析 `Authorization: Bearer {token}`
2. JwtUtil 校验 + 解析 claims（subject=userId, username, unitId, roles）
3. PermissionLoader.loadPermissions(userId) 加载权限集合
4. CurrentUser.set(UserInfo) 填充 ThreadLocal 上下文
5. SecurityContextHolder 设置认证态
6. 请求处理完毕 → CurrentUser.clear() 清理 ThreadLocal

## 公开路径白名单（PUBLIC_PATHS）
- `/sys/auth/login` — 登录接口
- `/doc/**`、`/swagger-ui/**`、`/v3/api-docs/**` — API 文档
- `/actuator/**` — 健康检查
- `/favicon.ico`

## 集成步骤
1. 引入 `platform-starter`（已传递依赖 platform-security）
2. 提供 `PermissionLoader` 实现（sys 模块的 PermissionService 已实现）
3. 提供 `JwtUtil` bean（sys 模块的 SysAutoConfiguration 已注册）
4. 启动应用，安全配置自动生效

## 可配置项
安全相关配置由 sys 模块管理：
| 属性 | 默认值 | 说明 |
|---|---|---|
| app.security.jwt.secret | my-platform-secret-key-... | JWT 签名密钥 |
| app.security.jwt.expiration | 86400000 | JWT 过期时间（毫秒），默认 24h |

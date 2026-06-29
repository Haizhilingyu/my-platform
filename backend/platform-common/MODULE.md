# Platform Common

## 功能概述
公共基础模块，被所有业务模块和应用依赖。提供统一响应体、异常处理、安全（JWT + 权限注解）、持久化基类。

## 依赖
- Spring Boot Web / Security / Data JPA / Validation
- JJWT (JWT 库)
- springdoc-openapi

## 提供的核心能力

### 统一响应
- `Result<T>` — 统一响应体 `{code, message, data}`
- `PageResult<T>` — 分页响应体 `{list, total, pageNum, pageSize}`

### 异常体系
- `BizException` — 业务异常基类
- `NotFoundException` — 404
- `ForbiddenException` — 403
- `GlobalExceptionHandler` — 全局异常处理器（自动注册）

### 安全
- `@RequiresPermission("模块:资源:操作")` — 方法级权限注解
- `CurrentUser` — ThreadLocal 当前用户上下文
- `JwtUtil` — JWT 生成/解析/校验
- `PermissionAspect` — 权限切面（自动注册）

### 持久化
- `BaseEntity` — 实体基类（createdAt/updatedAt/createdBy/updatedBy 自动填充）

## 集成步骤
1. 引入 Maven 依赖：`com.example:platform-common`
2. 主应用标注 `@EnableJpaAuditing`
3. 主应用标注 `@EnableAspectJAutoProxy`（Spring Boot 默认已启用）
4. 配置 JWT secret 和过期时间（`app.security.jwt.secret` / `app.security.jwt.expiration`）

## 权限标识规范
格式：`模块:资源:操作`，如 `sys:user:add`、`sys:role:assign`

## 使用示例
```java
@RestController
public class MyController {
    @RequiresPermission("sys:user:list")
    @GetMapping("/users")
    public Result<PageResult<UserVO>> list() { ... }
}
```

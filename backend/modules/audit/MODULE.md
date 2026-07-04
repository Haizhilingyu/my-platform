# 审计日志模块 (audit)

## 功能概述
提供声明式审计日志能力：在方法上标注 `@Auditable` 注解，AOP 切面自动记录操作人、IP、
User-Agent、方法参数（敏感字段已脱敏）、执行结果/异常，通过异步线程池落库，对主请求
线程开销 < 5ms。并提供分页查询 API。

## 依赖
- platform-common（提供 `@Auditable` / `AuditAspect` / `AuditRecorder` SPI）
- PostgreSQL + Flyway（建表和菜单种子随模块自动执行）
- sys 模块（V2 菜单种子依赖 sys_menu / sys_role_menu 表已存在）

## Maven 依赖
```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>audit-module</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## 架构
采用依赖倒置（DIP）保持模块边界：
- `platform-common` 定义 `@Auditable` 注解、`AuditAspect` 切面、`AuditRecorder` 接口、`AuditEvent` 数据载体
- `audit-module` 实现 `AuditRecorder`（`AuditLogService`），由 Spring 按类型自动装配

切面在请求线程同步采集上下文（内存操作 + 一次 JSON 序列化），组装 `AuditEvent` 后调用
`AuditRecorder.record(event)`。该方法标注 `@Async`，在独立线程池执行真正的 DB 写入。
当 classpath 上无 audit 模块时，切面通过 `ObjectProvider` 优雅降级（仅记调试日志，不落库）。

## 对外 API 门面
- `AuditRecorder.record(AuditEvent)` — SPI，由 AuditAspect 调用
- `AuditLogService.query(filter, pageable)` — 多条件分页查询

## REST API 列表

### 审计日志
| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | /sys/audit/logs | sys:audit:list | 分页查询审计日志（支持 actor/action/result/target/date-range 过滤） |

## 使用示例
```java
@Auditable(action = "LOGIN")
@PostMapping("/login")
public Result<LoginVO> login(@RequestBody LoginDTO dto) { ... }

@Auditable(action = "USER_UPDATE", targetType = "USER", targetIdParam = "id")
@PutMapping("/{id}")
public Result<Void> update(@PathVariable Long id, @RequestBody UserUpdateDTO dto) { ... }
```

## 敏感字段脱敏
方法参数序列化时，参数名（或 DTO 的 getter 字段名）匹配以下前缀的值会被替换为 `"***"`：
`password` / `passwd` / `oldPassword` / `newPassword` / `confirmPassword` /
`secret` / `token` / `accessToken` / `refreshToken` / `captchaCode` / `captcha` /
`credential` / `apiKey`

## 权限
- `sys:audit:list` — 查询审计日志（admin 角色在 V2 菜单种子中已绑定）

## 数据库表
`audit_log`（append-only，Flyway V1 自动建表；V2 播种菜单 + admin 绑定）

## 可配置项
| 属性 | 默认值 | 说明 |
|---|---|---|
| `spring.task.execution.*` | Spring Boot 默认 | `@Async` 线程池参数（可按需自定义） |

## 集成步骤
1. 引入 `audit-module` Maven 依赖（`platform-starter` 已聚合）
2. 确保已引入 `sys-module`（V2 依赖 sys_menu 表）
3. 启动应用，Flyway 自动建 audit_log 表 + 播种菜单
4. 在需要审计的方法上标注 `@Auditable(action = "...")`

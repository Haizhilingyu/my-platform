# AGENT.md — 后端代码规范

> 本文档为 AI Agent 提供项目结构、编码规范和开发约定。遵循本规范可确保代码一致性。

## 项目基本信息

- **语言**: Java 21
- **框架**: Spring Boot 3.3.5 + Spring Modulith 1.3
- **构建工具**: Maven 3.9+
- **ORM**: Spring Data JPA + Hibernate 6
- **数据库**: PostgreSQL 16（Flyway 管理迁移）
- **缓存**: Redis
- **安全**: Spring Security + JWT（jjwt 0.12）

## 模块化架构

```
backend/
├── pom.xml                    # 根 POM（parent），统一 dependencyManagement + pluginManagement
├── checkstyle.xml             # Checkstyle 规则
├── platform-common/           # 公共基础（被所有模块依赖）
├── platform-starter/           # 聚合 starter（引入后自动装配所有业务模块）
├── modules/                   # 业务模块目录
│   └── sys/                   # 系统设置模块（示例）
└── app/                       # 主应用启动入口
```

### Spring Modulith 模块规则

1. **一个文件夹 = 一个模块**。模块间通过公开 API 通信，禁止访问对方的 `internal/` 子包。
2. 模块的公开入口是根目录下的 `*Api.java` 接口类（如 `SysApi.java`）。
3. 模块间使用**领域事件**解耦：`ApplicationEventPublisher` 发布，`@ApplicationModuleListener` 监听。
4. 每个模块可以有自己的 `@Configuration` / `AutoConfiguration`，放在 `autoconfig/` 子包。
5. 依赖关系必须通过 CI 中的 Modulith Verify 测试检查。

### 模块目录约定

```
modules/<module-name>/
├── pom.xml                         # 子模块 POM（继承根 POM）
├── MODULE.md                       # 模块说明文档（Agent 优先读这个）
├── src/main/java/com/example/<name>/
│   ├── <Module>Api.java            # 对外 API 门面
│   ├── domain/                     # 实体（JPA Entity）
│   ├── repository/                 # Spring Data Repository
│   ├── service/                    # 业务逻辑
│   ├── controller/                 # REST Controller
│   ├── dto/                        # 请求/响应 DTO
│   ├── events/                     # 领域事件（record）
│   └── autoconfig/                 # 该模块的 AutoConfiguration
└── src/main/resources/
    ├── META-INF/spring/
    │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
    └── db/migration/               # Flyway SQL 脚本（模块自带）
```

### MODULE.md 文档规范

每个业务模块必须包含 `MODULE.md`，结构如下：

```markdown
# <模块名> 模块

## 功能概述
（一句话 + 功能列表）

## 依赖
- platform-common
- PostgreSQL + Flyway
- Redis（如需要）

## 对外 API
- XxxApi.method() — 说明

## REST API
| 方法 | 路径 | 权限 | 说明 |
（完整接口清单）

## 权限标识
- module:resource:action

## 领域事件
- EventName — 说明

## 集成步骤
1. 引入 Maven 依赖
2. 配置项说明
3. 可选步骤

## 可配置项
| 属性 | 默认值 | 说明 |
```

## 代码规范

### 命名规范

| 内容 | 规范 | 示例 |
|---|---|---|
| Package | 全小写 | `com.example.sys.domain` |
| Entity | `Sys` 前缀 + 名词 | `SysUser`, `SysRole` |
| Repository | `Sys` 前缀 + 名词 + `Repository` | `SysUserRepository` |
| Service | 名词 + `Service` | `UserService`, `PermissionService` |
| Controller | 名词 + `Controller` | `UserController` |
| DTO（请求） | 名词 + 操作 + `DTO` | `UserCreateDTO`, `RoleDTO` |
| VO（响应） | 名词 + `VO` | `UserVO`, `LoginVO` |
| 对外 API | `*Api` | `SysApi` |
| 领域事件 | 名词 + 过去式动词 | `UserCreated`, `RolePermissionChanged` |
| REST 路径 | `/<模块>/<资源>` | `/sys/user`, `/sys/menu/tree` |

### 类设计规范

**Entity**（`domain/`）:
- 继承 `com.example.common.persistence.BaseEntity`（自动填充 createdAt/updatedAt/createdBy/updatedBy）
- 使用 JPA 注解 (`@Entity`, `@Table`, `@Column`)
- 关联表用 `@IdClass` 实现复合主键（不用 `@EmbeddedId`）
- **不要**在 Entity 中写业务逻辑（业务逻辑放 Service）
- 数据库列名用 `snake_case`，Java 字段用 `camelCase`
- 状态字段用 `Integer`（0/1），不用 `boolean`

```java
@Entity
@Table(name = "sys_user")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SysUser extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 64)
    private String username;
    // ...
}
```

**Repository**（`repository/`）:
- 继承 `JpaRepository<Entity, Long>`
- 复杂查询用 `@Query`，不用 Criteria API
- 批量删除用 `@Modifying` + `@Query`

**Service**（`service/`）:
- 使用 `@Service` + `@RequiredArgsConstructor`（字段注入 `private final`）
- **所有读写方法必须标注** `@Transactional(readOnly = true)` 或 `@Transactional`
- 不要返回 Entity 给 Controller（用 VO/DTO 转换）
- 异常统一抛 `BizException` / `NotFoundException` / `ForbiddenException`

**Controller**（`controller/`）:
- 使用 `@RestController` + `@RequiredArgsConstructor`
- 统一返回 `Result<T>`（`Result.ok(data)` / `Result.fail(code, msg)`）
- 方法上标注 `@RequiresPermission("权限标识")`
- 文件上传用 `@RequestParam MultipartFile`
- 参数校验用 `@Valid` + DTO 字段注解；`@RequestParam`/`@PathVariable` 约束需在类级加 `@Validated`
- `List<Long>` 参数加 `@NotEmpty`；`@RequestBody List<DTO>` 加 `@Valid` 级联校验
- 所有 PUT 端点必须加 `@Valid`（历史代码曾遗漏，导致 DTO 注解被静默忽略）

**DTO**（`dto/`）:
- 使用 `@Data`（Lombok）
- 校验注解放在 DTO 字段上，不在 Controller 中手动校验
- **边界值测试**（`*BoundaryTest.java`）：每个带校验注解的 DTO 必须有对应的参数化边界测试，使用 Jakarta `Validator` API 直接校验（无需 Spring 上下文），覆盖 null/空串/超长/非法格式等边界值
- 校验注解必须与前端 `validation.ts` 中的 pattern 常量保持一致（同源约束）
- VO 提供静态工厂方法 `of(Entity)`

**领域事件**（`events/`）:
- 使用 Java `record` 类型
- 只包含必要字段 + 发生时间
- 示例：`public record UserCreated(Long userId, String username, LocalDateTime occurredAt) {}`

### 权限体系

**权限标识格式**: `<模块>:<资源>:<操作>`
- `sys:user:list` — 用户列表查看
- `sys:user:add` — 用户新增
- `sys:role:perm` — 角色权限分配

**Controller 中标注权限**:
```java
@RequiresPermission("sys:user:list")
@GetMapping
public Result<PageResult<UserVO>> list(...) { }

@RequiresPermission(value = {"sys:user:add", "sys:user:edit"}, logical = Logical.OR)
@PostMapping
public Result<Long> create(...) { }
```

### 统一响应体

```java
// 成功
Result.ok(data)        // 有数据
Result.ok()            // 无数据

// 失败
throw new BizException("业务错误");
throw new NotFoundException("资源", id);   // 自动生成 "资源 不存在: id"
throw new ForbiddenException("无权限");

// 校验失败（GlobalExceptionHandler 自动处理）
// 返回: Result.fail(400, "参数校验失败", errorsMap)
// errorsMap: { "username": "用户名不能为空", "email": "邮箱格式不正确" }
```

**不要**自己在 Controller 中构造 `Result.fail()`，统一抛异常，由 `GlobalExceptionHandler` 处理。

### 安全

- 当前用户信息通过 `CurrentUser.get()` 获取
- JWT Token 在 `Authorization: Bearer <token>` 头中
- 白名单路径在 `SecurityConfig.PUBLIC_PATHS` 中定义
- 登录使用 `/sys/auth/login`（POST），TOKEN 24 小时有效期

## 数据库

### Flyway 迁移规范

- 版本号用 `V<序号>__<描述>.sql`（双下划线）
- V1: 表结构
- V2: 初始数据
- 后续 V3, V4...: 增量修改
- 所有 DDL 必须 `IF NOT EXISTS`
- 所有 DML 必须 `ON CONFLICT DO NOTHING`
- `flyway.baseline-on-migrate=true` 允许在非空库中启用

### 表命名

| 类型 | 规范 | 示例 |
|---|---|---|
| 实体表 | `sys_<noun>` | `sys_user`, `sys_role` |
| 关联表 | `sys_<noun1>_<noun2>` | `sys_user_role` |
| 配置表 | `sys_config` | |
| 列名 | `snake_case` | `created_at`, `unit_id` |

### 索引规范

```sql
CREATE INDEX IF NOT EXISTS idx_<table>_<column> ON <table>(<column>);
```

## 代码质量

### 强制检查（pre-commit + CI）

| 工具 | 作用 | 跳过参数 |
|---|---|---|
| **Spotless** | Google Java Format | `-Dspotless.check.skip=true` |
| **Checkstyle** | 编码规范（命名/导入/空白） | `-Dcheckstyle.skip=true` |

### CI 检查（不阻塞本地开发）

| 工具 | 作用 | 跳过参数 |
|---|---|---|
| **SpotBugs** | 静态分析（空指针等 bug） | `-Dspotbugs.skip=true` |
| **JaCoCo** | 测试覆盖率（≥80% LINE，CI 强制） | `-Djacoco.skip=true` |

### Pre-commit Hook

```bash
# 手动启用
git config core.hooksPath .githooks
```

自动运行：
- 后端：`mvn spotless:check` + `checkstyle:check`
- 前端：`lint-staged`

## 测试规范

### TDD 开发流程（强制）

开发任何功能必须遵循 Red-Green-Refactor 循环：

```
1. Red    — 先写测试，此时测试必须失败（功能未实现）
2. Green  — 写最少量的代码让测试通过
3. Refactor — 重构代码，保持测试绿色
```

**不允许先写实现再补测试。**

### 测试命名

用 `should_<期望> _when_<条件>` 句式，用下划线分隔（可读性优先）：

```java
@Test
@DisplayName("密码加密：创建用户时密码被 BCrypt 加密")
void should_encryptPassword_when_createUser() { ... }

@Test
@DisplayName("用户名重复：抛出 BizException")
void should_throwBizException_when_usernameExists() { ... }
```

`@DisplayName` 用中文描述业务语义，方法名用英文。

### 测试结构

每个测试方法遵循 Given / When / Then 三段式：

```java
@Test
@DisplayName("正常创建：返回用户 ID")
void should_returnUserId_when_createValidUser() {
    // Given — 准备数据和 mock
    var dto = new UserCreateDTO();
    dto.setUsername("newuser");
    when(userRepository.save(any())).thenReturn(savedUser);

    // When — 执行被测方法
    Long id = userService.create(dto);

    // Then — 断言结果
    assertThat(id).isEqualTo(100L);
    verify(eventPublisher).publishEvent(any(UserCreated.class));
}
```

### 测试层级

| 层级 | 工具 | 测试什么 | 文件命名 |
|---|---|---|---|
| 单元测试 | JUnit5 + Mockito | Service/工具类的业务逻辑，不启动 Spring | `*Test.java` |
| 集成测试 | Spring Boot Test + H2 | Repository 查询、事务行为 | `*IT.java` |
| 模块验证 | Spring Modulith | 模块边界、依赖关系 | `ModulithVerificationTest.java` |
| 容器集成 | Testcontainers | 真实数据库下的行为 | `*ContainerIT.java` |

### 断言库

**统一使用 AssertJ**（`assertThat`），不使用 JUnit 原生 `assertEquals`：

```java
// ✅ AssertJ
assertThat(result).isEqualTo(expected);
assertThatThrownBy(() -> service.create(dto))
    .isInstanceOf(BizException.class)
    .hasMessageContaining("已存在");

// ❌ JUnit 原生
assertEquals(expected, result);
```

### Mock 规范

- 使用 `@ExtendWith(MockitoExtension.class)` + `@Mock` + `@InjectMocks`
- 只 mock 外部依赖（Repository、EventPublisher），不 mock 被测类
- 用 `ArgumentCaptor` 验证传入参数
- 用 `verify(mock, never())` 验证未被调用

### 覆盖率要求

| 指标 | 门禁 |
|---|---|
| 行覆盖率 (LINE) | ≥ 80% |
| 分支覆盖率 (BRANCH) | ≥ 70% |

覆盖率不达标 CI 构建失败。本地运行：

```bash
mvn test jacoco:report    # 生成报告
mvn jacoco:check           # 检查门禁
# 报告位置: target/site/jacoco/index.html
```

### 测试目录结构

```
src/test/java/<package>/
├── ModulithVerificationTest.java      # 模块边界验证
├── service/
│   ├── UserServiceTest.java           # 单元测试
│   ├── RoleServiceTest.java
│   └── PermissionServiceTest.java
├── controller/
│   └── UserControllerIT.java          # 集成测试
└── architecture/
    └── ArchitectureTest.java          # ArchUnit 架构约束
```

### 边界值测试（Bean Validation）

每个带 `@NotBlank`/`@Size`/`@Pattern` 等注解的 DTO，必须有对应的 `*BoundaryTest.java`：
- 使用 `Validation.buildDefaultValidatorFactory().getValidator()` 直接校验
- `@ParameterizedTest + @MethodSource` 提供边界值用例
- 覆盖：null、空串、超长、非法 pattern、空列表
- 参考：`UserCreateDTOBoundaryTest.java`

### 新增功能时的 TDD 操作清单

1. 创建测试文件（如 `XxxServiceTest.java`）
2. 写测试方法（应该失败，因为功能未实现）
3. 运行 `mvn test -Dtest=XxxServiceTest` 确认 Red
4. 实现最少代码让测试通过
5. 运行确认 Green
6. 重构，保持绿色
7. `git add` 时 pre-commit 会自动运行测试

## 常见任务（Agent 操作指南）

### 新增业务模块

1. 在 `backend/pom.xml` 的 `<modules>` 中添加
2. 在 `backend/modules/` 创建目录，复制 sys 模块结构
3. 编写 `pom.xml`（继承根 POM，依赖 `platform-common`）
4. 创建 domain → repository → service → controller → dto → events
5. 如果模块有独立配置，创建 `autoconfig/SysXxxAutoConfiguration.java`
6. 创建 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
7. 编写 `MODULE.md`
8. 编写 Flyway SQL 迁移脚本
9. 在 `platform-starter/pom.xml` 中添加依赖
10. 运行 `mvn clean compile -DskipTests` 验证

### 新增 REST API

1. 在 `dto/` 创建请求/响应 DTO（带 `@Valid` 注解）
2. 在 `service/` 中实现业务逻辑（注意事务注解）
3. 在 `controller/` 中添加接口方法，标注 `@RequiresPermission`
4. 如需新权限标识，在菜单表中添加 BUTTON 类型记录

### 新增数据库表

1. 创建新的 Flyway SQL 脚本：`V3__<description>.sql`
2. 放在 `modules/<name>/src/main/resources/db/migration/`
3. 所有 DDL 加 `IF NOT EXISTS`
4. 如需初始数据，另建 V4 脚本加 `ON CONFLICT DO NOTHING`

### 本地运行

```bash
cd backend
mvn clean compile -DskipTests -Dspotless.check.skip=true -Dcheckstyle.skip=true
cd app
mvn spring-boot:run

# 访问 Swagger
http://localhost:8090/swagger-ui.html
```

### 打包

```bash
cd backend
# 跳过代码检查
mvn package -DskipTests -Dspotless.check.skip=true -Dcheckstyle.skip=true -Dspotbugs.skip=true -Djacoco.skip=true
```

## 注意事项

1. **不要**在 Controller 中写业务逻辑
2. **不要**直接返回 Entity 给前端，通过 VO/DTO 转换
3. **不要**在 Service 中使用 `@Autowired` 字段注入，用 `@RequiredArgsConstructor` + `final`
4. **不要**吞异常，统一抛 BizException 子类
5. **不要**使用 `@EmbeddedId`，用 `@IdClass`
6. Lombok 需要每个模块都显式声明依赖（scope=optional）
7. Flyway 脚本中的 `COMMENT ON TABLE` 是独立语句，不在 CREATE TABLE 内部
8. JWT 密钥必须 ≥ 32 字节（256 bits），短密钥会导致 `WeakKeyException`

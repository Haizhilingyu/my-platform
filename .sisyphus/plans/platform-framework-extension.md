# 平台基础框架完善与扩展功能

## TL;DR

> **Quick Summary**: 在现有 Java21/Spring Boot 3.3 + Vue3 平台上，修复单位/菜单空白页 bug，落地 H2 测试配置与前端规范（Tailwind/响应式），并新建 6 个核心模块：登录安全增强、模块化外部登录（LDAP 示例）、消息中心（WebSocket 三级）、外部应用/SSO（OAuth2+OIDC）、操作审计日志、数据权限落地，外加 C/Java/Python/Go 四语言 SDK。
>
> **Deliverables**:
> - 修复 unit/menu 空白页 + v-permission 注册 + 前端规范（响应式/Tailwind-only）
> - H2 测试 profile（application-test.yml + V2 脚本 H2 兼容）
> - platform-security 模块（SecurityConfig 下沉 + PermissionLoader 接口）
> - 登录安全：3次锁定 + 管理员解锁 + 图形验证码 + Token 黑名单 + 在线会话管理
> - 模块化登录 SPI 框架 + LDAP 真实 provider（利用 OpenLDAP 192.168.1.2:389）
> - 消息中心（modules/notify）：WebSocket + 三级差异化弹窗 + 多范围发布 + 对外 HTTP 接口
> - 外部应用/开放平台（modules/openapp）：spring-authorization-server + OIDC + 登出回调 + 外部应用管理
> - 操作审计日志（modules/audit）：表 + AOP 切面 + 查询界面
> - 数据权限落地：DataScope 框架 + SysUser 参考实现 + ArchUnit 强制
> - SDK 四语言（Java/Python/Go/C）→ Nexus 私服 + 集成文档
>
> **Estimated Effort**: XL（30 个任务，5 个 Wave）
> **Parallel Execution**: YES - 5 Waves，Wave 2/3 各 8-10 并行任务
> **Critical Path**: Wave 1 基础设施（Security 下沉 + Redis 接线 + 数据权限框架）→ 阻塞 70% 下游

---

## Context

### Original Request
完善平台基础框架：补齐单位/菜单管理显示、H2 测试配置、前端 Tailwind/响应式规范；新增登录安全（锁定/解锁/验证码/会话）、模块化外部登录、消息中心（WebSocket 三级）、外部应用 SSO（OAuth2+OIDC）+ 四语言 SDK、操作审计日志、数据权限落地。

### Interview Summary
**Key Discussions**:
- 消息通道：**WebSocket**；弹出形式：**三级差异化**（紧急=Modal/重要=Toast/一般=徽标）
- SSO 协议：**OAuth2 + OIDC**；SDK 分发：**统一 Nexus 私服**
- 登录扩展粒度：**Starter 依赖装配**（符合 Spring Modulith 架构）
- 多租户：**否，单租户**
- 附加范围（全选）：**图形验证码 + 操作审计日志 + 数据权限落地 + 在线会话管理**

**Research Findings**（3 个探索代理 + Metis 5 个验证代理）:
- **后端**：sys 模块（user/role/menu/unit/config）已完整；JWT 无状态认证在但 SecurityConfig 硬耦合 sys 模块（`SecurityConfig.java:26` 直接 import PermissionService）；Redis 引入但零使用（PermissionService Javadoc **虚假声明**已用 Redis 缓存）；JaCoCo LINE≥80% TDD 强制
- **前端**：Naive UI + Tailwind 双层主题；无响应式（无 sm:/md:/lg:）；v-permission **未在 main.ts 注册**（bug）；unit/menu 页面树渲染用 `{{ node.label() }}` 插值 VNode（**根因：应改用 NTree 组件**）
- **H2 兼容性**：V1 完全兼容；V2 仅 7 处 `ON CONFLICT DO NOTHING`（行 8/13/18/22/49/54/62）需改为 `MERGE INTO ... WHEN NOT MATCHED`，无 JSONB/enums/arrays，**低风险**
- **OAuth2**：spring-authorization-server 1.5.8 稳定；RP-Initiated Logout **原生支持**；应用静态管理（非动态注册）；**JWK 轮转/持久化是高被低估风险**（in-memory 单 key 在 HA 下会跨副本验证失败）
- **数据权限**：dataScope 是 String 字段但从未用于查询；SysUnit 仅邻接表（parent_id），UNIT_BELOW 需递归 CTE；纯 JPA → **JPA Specification + ThreadLocal** 是正确方案（非 MyBatis Interceptor/Hibernate @Filter）

### Metis Review
**Identified Gaps**（addressed）:
- SecurityConfig 硬耦合 sys 模块 → 抽象 PermissionLoader 接口 + 提取 platform-security 模块（Wave 1 关键路径门）
- CurrentUser.unitId 被 JwtAuthFilter 写死 null → 数据权限无法工作 → 必须修复
- Redis 零使用但 Javadoc 谎称已缓存 → Wave 1 修复文档谎言 + 真实接线
- JWK in-memory 单 key → 生产定时炸弹 → 含 JWK 轮转/持久化任务（即使单实例也纳入）
- H2 仅 V2 的 7 处 ON CONFLICT 阻塞 → 低风险，MERGE INTO 改写
- 12 个边界场景（锁定竞态/captcha 重放/WebSocket 重连缺口/授权码重放/递归单位树环路等）→ 已在任务 QA 场景覆盖

---

## Work Objectives

### Core Objective
在保持现有 sys 模块契约（LoginVO 结构已被 E2E 锁定）不破坏的前提下，通过模块化扩展补齐企业级平台能力，所有新功能作为独立 Spring Modulith 模块交付，可独立复用。

### Concrete Deliverables
- 后端新模块：`platform-security`、`modules/notify`、`modules/openapp`、`modules/audit`、`platform-login-ldap`
- 后端增强：sys 模块（登录安全/数据权限）、platform-common（PermissionLoader/Redis/DataScope 框架）
- 前端新页面：消息中心、外部应用管理、审计日志、在线会话、登录页重构
- 前端改造：unit/menu 修复、响应式 Layout、Tailwind-only 规范
- SDK：Java/Python/Go/C 四语言 + Nexus 发布 CI + 集成文档
- 测试：application-test.yml H2 profile + ArchUnit 边界测试 + E2E 全链路

### Definition of Done
- [ ] `mvn test -Dspring.profiles.active=test` 全绿（H2，不连远端 DB）
- [ ] `npm run test:coverage` ≥80% lines
- [ ] 现有 E2E（e2e/api-e2e.sh + e2e/tests/login.spec.ts）仍通过
- [ ] ArchUnit 模块边界 + ScopedRepository 强制测试通过
- [ ] 四语言 SDK 各自能完成 OAuth2 登录 + 消息发布 demo

### Must Have
- 单一 plan，所有任务纳入（不分多 plan）
- 每个新模块：独立 pom + MODULE.md + AutoConfiguration + META-INF/spring imports + Flyway 脚本（模块前缀表名）
- TDD 强制（失败测试→实现→通过），JaCoCo LINE≥80%
- 所有新 Flyway 脚本 H2 兼容（用 MERGE INTO，禁用 ON CONFLICT）
- 所有新前端代码零行内样式（Tailwind-only），ESLint 强制
- 所有 QA 场景 agent 可执行（无"人工验证"）

### Must NOT Have（Guardrails from Metis）
- **不得**用 Hibernate @Filter 或字符串拼接 AOP 实现数据权限 → 必须 JPA Specification
- **不得**用 OAuth2 动态客户端注册 → 静态管理（admin UI 写 RegisteredClientRepository）
- **不得**创建"Redis 以后再接"占位 → 必须真实接线 + 测试
- **不得**在不接入 Spring Session + Redis backplane 的情况下声称支持 OIDC 登出
- **不得**用 `{{ node.label() }}` 插值 VNode → 改用 NTree 组件
- **不得**破坏现有 LoginVO 契约（E2E Layer1 已锁定）
- **不得**把多个模块塞进一个任务
- 范围排除：多租户、i18n、MFA、SAML、WebAuthn、邮件/短信/推送渠道、设计系统重写、运行时热插拔登录、SIEM/合规报表、设备指纹/异常检测

---

## Verification Strategy

> **ZERO HUMAN INTERVENTION** - ALL verification is agent-executed. No exceptions.

### Test Decision
- **Infrastructure exists**: YES（JUnit5+Mockito+AssertJ+Testcontainers+H2 / Vitest+jsdom+Playwright 已装）
- **Automated tests**: TDD（后端）/ Tests-after（前端页面）
- **Framework**: 后端 JUnit5（H2 profile）/ 前端 Vitest + Playwright

### QA Policy
每个任务含 agent 执行 QA 场景（具体命令/选择器/断言/证据路径）。证据存 `.sisyphus/evidence/task-{N}-{slug}.{ext}`。
- **后端 API**: Bash(curl) - 请求 + 断言 status + JSON 字段
- **前端 UI**: Playwright - 导航 + 交互 + DOM 断言 + 截图（375px/768px/1280px 三断点）
- **WebSocket**: Playwright + Bash(ws client) - 连接 + 推送 + 接收断言
- **SDK**: Bash(各语言运行时) - 编译 + 运行 demo + 断言输出

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 0 (前置快赢 - 顺序，半天):
└── T1: H2 测试 profile + V2 兼容修复 + 基线测试验证 [deep]

Wave 1 (基础设施 - 关键路径门，2 条并行链):
├── T2: PermissionLoader 接口抽象 + 修复 Redis Javadoc 谎言 [quick] ← 阻塞 T3/T8/T14
├── T3: platform-security 模块提取 + CurrentUser.unitId 修复 (blocked: T2) [deep] ← 阻塞 T8-T11/T16
├── T4: RedisConfig + RedisTemplate 真实接线 [quick] ← 阻塞 T8-T11/T15
├── T5: 数据权限框架 + ScopedEntity + sys_role_data_scope + 递归 CTE [deep] ← 阻塞 T17
├── T6: 前端基础设施修复 (v-permission 注册 + NNotificationProvider + useBreakpoint + 清理行内样式 + ESLint 规则) [visual-engineering]
└── T7: 修复 unit/menu 空白页 (NTree 重构) [visual-engineering]

Wave 2 (核心功能模块 - 最大并行，10 任务):
├── T8: 登录安全-锁定+解锁 (blocked: T3,T4) [deep]
├── T9: 登录安全-图形验证码 (blocked: T4) [quick]
├── T10: Token 黑名单 + 登出端点 (blocked: T3,T4) [deep]
├── T11: 在线会话管理 (blocked: T10) [unspecified-high]
├── T12: 模块化登录 SPI 框架 + /login-methods API (blocked: T3) [deep]
├── T13: LDAP 登录 provider starter (blocked: T12) [deep]
├── T14: 审计日志后端 (blocked: T2) [unspecified-high]
├── T15: 消息中心后端-WebSocket+三级+多范围 (blocked: T4) [deep]
├── T16: 外部应用/开放平台后端-OAuth2+OIDC (blocked: T3) [ultrabrain]
└── T17: 数据权限 SysUser 参考实现 (blocked: T5) [deep]

Wave 3 (前端页面 + 集成 - 并行，8 任务):
├── T18: 响应式 Layout 改造 (blocked: T6) [visual-engineering]
├── T19: 登录页重构-动态登录方式+验证码 (blocked: T6,T9,T12) [visual-engineering]
├── T20: 消息中心前端 (blocked: T6,T15) [visual-engineering]
├── T21: 外部应用管理前端 (blocked: T6,T16) [visual-engineering]
├── T22: 审计日志查询前端 (blocked: T6,T14) [visual-engineering]
├── T23: 在线会话管理前端 (blocked: T6,T11) [visual-engineering]
├── T24: 用户管理增强-锁定状态+解锁+数据权限配置 (blocked: T6,T8,T17) [visual-engineering]
└── T25: OIDC 单点登出 + Spring Session Redis backplane + 登出回调 (blocked: T16) [deep]

Wave 4 (SDK + 文档 + 集成验证 - 并行，5 任务):
├── T26: Java SDK + Python SDK (blocked: T16,T25) [unspecified-high]
├── T27: Go SDK + C SDK (blocked: T16,T25) [unspecified-high]
├── T28: 集成文档 (4 语言快速开始 + API 参考 + 示例) (blocked: T26,T27) [writing]
├── T29: ArchUnit 边界测试 + E2E 全链路集成测试 (blocked: all impl) [deep]
└── T30: 部署 Runbook + Nexus SDK 发布 CI (blocked: T26,T27) [writing]

Wave FINAL (4 并行评审，全部 APPROVE 后待用户确认):
├── F1: Plan 合规审计 (oracle)
├── F2: 代码质量审查 (unspecified-high)
├── F3: 真实手动 QA (unspecified-high + playwright)
└── F4: 范围保真检查 (deep)

Critical Path: T1 → T2 → T3 → T8/T10/T12/T16 → T19/T20/T21/T25 → T26 → T29 → F1-F4
Parallel Speedup: ~75% faster than sequential
Max Concurrent: 10 (Wave 2)
```

### Dependency Matrix

| Task | Blocked By | Blocks |
|---|---|---|
| T1 | - | T2-T30 (基线) |
| T2 | T1 | T3, T8, T14 |
| T3 | T2 | T8, T10, T12, T16 |
| T4 | T1 | T8, T9, T10, T15 |
| T5 | T1 | T17 |
| T6 | T1 | T18-T24 |
| T7 | T1 | - |
| T8 | T3, T4 | T24 |
| T9 | T4 | T19 |
| T10 | T3, T4 | T11 |
| T11 | T10 | T23 |
| T12 | T3 | T13, T19 |
| T13 | T12 | T19 |
| T14 | T2 | T22 |
| T15 | T4 | T20 |
| T16 | T3 | T21, T25, T26, T27 |
| T17 | T5 | T24 |
| T18 | T6 | - |
| T19 | T6, T9, T12 | - |
| T20 | T6, T15 | - |
| T21 | T6, T16 | - |
| T22 | T6, T14 | - |
| T23 | T6, T11 | - |
| T24 | T6, T8, T17 | - |
| T25 | T16 | T26, T27 |
| T26 | T16, T25 | T28, T30 |
| T27 | T16, T25 | T28, T30 |
| T28 | T26, T27 | - |
| T29 | all impl | F1-F4 |
| T30 | T26, T27 | - |

### Agent Dispatch Summary

- **Wave 0**: 1 - T1 → `deep`
- **Wave 1**: 6 - T2/T4 → `quick`, T3/T5 → `deep`, T6/T7 → `visual-engineering`
- **Wave 2**: 10 - T8/T10/T12/T13/T15/T17 → `deep`, T9 → `quick`, T11/T14 → `unspecified-high`, T16 → `ultrabrain`
- **Wave 3**: 8 - T18-T24 → `visual-engineering`, T25 → `deep`
- **Wave 4**: 5 - T26/T27 → `unspecified-high`, T28/T30 → `writing`, T29 → `deep`
- **FINAL**: 4 - F1 → `oracle`, F2/F3 → `unspecified-high`, F4 → `deep`

---

## TODOs

> 详见下方各任务。每个任务含：实现步骤 + Must NOT do + Agent Profile + 并行化 + References（模式/API/测试/外部）+ 验收标准 + QA 场景 + 证据 + Commit。

- [x] 1. **H2 测试 profile + V2 兼容修复 + 基线验证** [Wave 0]

  **What to do**:
  - 创建 `backend/app/src/test/resources/application-test.yml`：激活 H2 PostgreSQL 兼容模式（`jdbc:h2:mem:test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1`），`spring.jpa.ddl-auto=validate`，Flyway 指向同迁移目录
  - 创建 `backend/app/src/test/resources/db/migration/` 或修改 V2：将 `backend/modules/sys/src/main/resources/db/migration/V2__sys_init_data.sql` 的 7 处 `INSERT ... ON CONFLICT (key) DO NOTHING` 改为 `MERGE INTO ... WHEN NOT MATCHED THEN INSERT ...`（H2+PG15+ 通用语法）
  - 验证现有 `UserServiceTest` 等单测在 H2 profile 下通过（`mvn test -Dspring.profiles.active=test`）
  - 添加 Testcontainers PostgreSQL 集成测试：断言 V2 改写后 PG 与 H2 播种行数一致（per-table golden file）
  - 运行基线 `mvn test` 确认现有测试全绿（如有红色先修复）

  **Must NOT do**:
  - 不改 V1（已完全 H2 兼容）
  - 不引入 H2 专用独立 DDL（维护成本高）
  - 不破坏 PostgreSQL 真实部署的播种

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 涉及 Flyway/H2 兼容性分析 + Testcontainers 双 DB 验证，需深度调试
  - **Skills**: [`git-master`]
    - `git-master`: 多文件改动需精确 commit 拆分

  **Parallelization**:
  - **Can Run In Parallel**: NO（Wave 0 顺序，所有下游依赖基线）
  - **Parallel Group**: Wave 0（独立）
  - **Blocks**: T2-T30（基线门）
  - **Blocked By**: None

  **References**:
  - **Pattern**: `backend/modules/sys/src/test/java/com/example/sys/service/UserServiceTest.java` — JUnit5+Mockito 测试范式（Given/When/Then + @Nested）
  - **Pattern**: `backend/app/src/test/java/com/example/app/ModulithVerificationTest.java` — Spring Modulith 边界测试
  - **Target file**: `backend/modules/sys/src/main/resources/db/migration/V2__sys_init_data.sql` 行 8/13/18/22/49/54/62 的 7 处 ON CONFLICT
  - **Config**: `backend/app/src/main/resources/application.yml` — 现有 PG 配置参考（H2 profile 需镜像结构）
  - **External**: H2 PostgreSQL 模式文档 `https://h2database.com/html/features.html#compatibility` — MODE=PostgreSQL 支持范围
  - **WHY**: V2 是唯一阻塞 H2 的文件；改写后必须保证 PG 播种一致性（Testcontainers 双 DB 测试是唯一可靠验证）

  **Acceptance Criteria**:
  - [ ] `application-test.yml` 创建，含 H2 PostgreSQL 模式配置
  - [ ] V2 的 7 处 ON CONFLICT 改为 MERGE INTO
  - [ ] `mvn test -Dspring.profiles.active=test` → BUILD SUCCESS（不连远端 DB）
  - [ ] Testcontainers PG 测试：per-table 行数与 H2 一致

  **QA Scenarios**:
  ```
  Scenario: H2 profile 本地测试不连远端 DB
    Tool: Bash
    Preconditions: 关闭 VPN/断开 192.168.1.2 网络
    Steps:
      1. cd backend && mvn test -Dspring.profiles.active=test -pl modules/sys,app
      2. 检查日志无 "Connection refused 192.168.1.2:5532"
      3. 检查日志含 "H2 Console" 或 "H2 /mem:test"
    Expected Result: BUILD SUCCESS，零远端 DB 连接尝试
    Failure Indicators: 出现 PostgreSQL 连接错误，或 Flyway 报语法错误
    Evidence: .sisyphus/evidence/task-1-h2-local-test.txt（mvn 输出）

  Scenario: PG/H2 播种一致性（Testcontainers）
    Tool: Bash
    Preconditions: Docker 运行中
    Steps:
      1. mvn test -Dtest=FlywayConsistencyIT -Dspring.profiles.active=test
      2. 测试内部：Testcontainers 启动 PG16，分别对 PG 和 H2 跑 Flyway
      3. 断言 sys_user/sys_role/sys_menu/sys_unit/sys_config 行数一致
    Expected Result: per-table row count match，0 差异
    Failure Indicators: 任一表行数不一致（MERGE 改写引入语义偏差）
    Evidence: .sisyphus/evidence/task-1-flyway-consistency.txt
  ```

  **Commit**: YES
  - Message: `test(infra): add H2 test profile with PostgreSQL compatibility`
  - Files: application-test.yml, V2__sys_init_data.sql, FlywayConsistencyIT.java
  - Pre-commit: `mvn test -Dspring.profiles.active=test`

- [x] 2. **PermissionLoader 接口抽象 + 修复 Redis Javadoc 谎言** [Wave 1]

  **What to do**:
  - 在 `platform-common` 新增 `PermissionLoader` 接口（方法：`Set<String> loadPermissions(userId)` / `Set<String> loadRoles(userId)` / `UserInfo loadUserInfo(userId)` / `boolean hasPermission(userId, perm)`）
  - `sys` 模块 `PermissionService` 实现该接口（`implements PermissionLoader`），保留原有方法签名兼容
  - 修改 `app/SecurityConfig.java:26,38,71`：将 `import com.example.sys.service.PermissionService` 改为 `import com.example.common.security.PermissionLoader`，通过 Spring 自动注入（sys 模块作为唯一实现）
  - 修复 `PermissionService.java:26-27` Javadoc：删除"权限缓存通过 Redis 实现"虚假声明，改为"直接查库（Redis 缓存待 T4 实现）"
  - 添加 ArchUnit 测试断言 `platform-common` 不依赖任何 `com.example.sys.*`

  **Must NOT do**:
  - 不在此任务实现 Redis 缓存（T4 做）
  - 不改变 PermissionService 现有方法签名（保持 sys 模块契约）
  - 不破坏现有登录流程

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 接口提取 + import 替换，机械性重构，范围明确
  - **Skills**: [`git-master`]
    - `git-master`: 跨模块重构 commit 拆分

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T4/T5/T6/T7 同 Wave）
  - **Parallel Group**: Wave 1
  - **Blocks**: T3, T8, T14
  - **Blocked By**: T1

  **References**:
  - **Target**: `backend/app/src/main/java/com/example/app/config/SecurityConfig.java:26,38,71` — 硬耦合点（PermissionService 直接 import）
  - **Target**: `backend/modules/sys/src/main/java/com/example/sys/service/PermissionService.java:26-27` — Javadoc 谎言
  - **Pattern**: `backend/platform-common/src/main/java/com/example/common/security/RequiresPermission.java` — 现有注解如何在 common 定义并被 sys 消费的范例
  - **WHY**: SecurityConfig 硬耦合 sys 是所有新模块复用安全基础设施的阻塞点；接口提取是 platform-security 模块（T3）的前置

  **Acceptance Criteria**:
  - [ ] `PermissionLoader` 接口在 platform-common 创建
  - [ ] PermissionService implements PermissionLoader
  - [ ] SecurityConfig 依赖 PermissionLoader（非 PermissionService）
  - [ ] Javadoc 修复
  - [ ] ArchUnit: platform-common 不依赖 com.example.sys
  - [ ] 现有登录 E2E 通过

  **QA Scenarios**:
  ```
  Scenario: 重构后登录仍工作
    Tool: Bash (curl)
    Preconditions: 后端启动（dev profile）
    Steps:
      1. curl -s -X POST http://localhost:8090/sys/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}'
      2. 解析响应 JSON，提取 token
      3. curl -s http://localhost:8090/sys/auth/me -H "Authorization: Bearer $token"
    Expected Result: 登录返回 200 + {token, tokenType:"Bearer", user:{username:"admin"}}；/me 返回 admin 用户信息
    Failure Indicators: 登录 500 或 token 为空（接口提取破坏注入）
    Evidence: .sisyphus/evidence/task-2-login-after-refactor.txt

  Scenario: ArchUnit 边界强制
    Tool: Bash
    Steps:
      1. mvn test -Dtest=ArchUnitTest -pl platform-common -Dspring.profiles.active=test
      2. 测试断言 com.example.common 不依赖 com.example.sys
    Expected Result: PASS
    Failure Indicators: ArchUnit 报告 platform-common 某类 import com.example.sys
    Evidence: .sisyphus/evidence/task-2-archunit.txt
  ```

  **Commit**: YES
  - Message: `refactor(common): extract PermissionLoader interface, decouple SecurityConfig from sys`
  - Files: PermissionLoader.java, PermissionService.java, SecurityConfig.java, ArchUnitTest.java
  - Pre-commit: `mvn test -Dspring.profiles.active=test`

- [x] 3. **platform-security 模块提取 + CurrentUser.unitId 修复** [Wave 1]

  **What to do**:
  - 新建 `backend/platform-security/` 模块（pom 继承根，依赖 platform-common + spring-security + jjwt）
  - 将 `app/SecurityConfig.java` + `JwtAuthFilter`（内部类）迁移到 `platform-security/src/main/java/com/example/platform/security/`
  - 在 platform-security 创建 `SecurityAutoConfiguration` + `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  - 修复 `JwtAuthFilter`：JWT claims 增加含 `unitId`，过滤时从 token 解析并填充 `CurrentUser.UserInfo.unitId`（需修改 `JwtUtil` 生成 token 时注入 unitId）
  - 修改 `AuthController.login`：登录成功后从 SysUser 查 unitId，写入 JWT claims
  - 根 `pom.xml` `<modules>` 加 platform-security；`platform-starter/pom.xml` 加依赖
  - `app` 模块删除原 SecurityConfig，依赖 platform-security 自动装配
  - 更新 `Application.java` scanBasePackages 加 `com.example.platform.security`
  - 创建 `platform-security/MODULE.md`

  **Must NOT do**:
  - 不改变 JWT 签名算法（保持 HS256）
  - 不改变 LoginVO 响应结构
  - 不在此任务加 token 黑名单（T10 做）
  - 不破坏现有 PUBLIC_PATHS 白名单

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 跨模块迁移 + JWT claims 变更 + AutoConfiguration，需深度理解 Spring Boot 装配
  - **Skills**: [`git-master`]
    - `git-master`: 大范围文件移动需精确 git mv + commit

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T4/T5/T6/T7 同 Wave，但依赖 T2 完成）
  - **Parallel Group**: Wave 1（blocked by T2）
  - **Blocks**: T8, T10, T12, T16
  - **Blocked By**: T2

  **References**:
  - **Source**: `backend/app/src/main/java/com/example/app/config/SecurityConfig.java` — 待迁移（含 JwtAuthFilter 内部类）
  - **Pattern**: `backend/modules/sys/src/main/java/com/example/sys/autoconfig/SysAutoConfiguration.java` — AutoConfiguration 编写范例
  - **Pattern**: `backend/modules/sys/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` — SPI 注册范例
  - **Target**: `backend/platform-common/src/main/java/com/example/common/security/CurrentUser.java` — UserInfo record 含 unitId 字段（待填充）
  - **Target**: `backend/platform-common/src/main/java/com/example/common/security/JwtUtil.java` — 需增加 unitId claim
  - **WHY**: SecurityConfig 在 app 限制复用；下沉后所有新模块经 platform-starter 自动获得安全能力；unitId 注入是数据权限（T5/T17）的前置

  **Acceptance Criteria**:
  - [ ] platform-security 模块创建（pom + AutoConfiguration + imports + MODULE.md）
  - [ ] SecurityConfig + JwtAuthFilter 迁移完成
  - [ ] app 模块无 SecurityConfig，依赖自动装配
  - [ ] JWT claims 含 unitId
  - [ ] CurrentUser.get().unitId() 非 null（登录后）
  - [ ] ModulithVerificationTest 通过
  - [ ] 现有 E2E 通过

  **QA Scenarios**:
  ```
  Scenario: 提取后契约测试 - 新 app 依赖 platform-starter 获得安全链
    Tool: Bash
    Steps:
      1. mvn test -Dtest=SecurityAutoConfigContractTest -Dspring.profiles.active=test
      2. 测试：new 一个最小 app（仅依赖 platform-starter），启动后 GET /sys/auth/me 返回 401（未授权）
    Expected Result: SecurityFilterChain 自动装配，未带 token 返回 401
    Failure Indicators: 返回 404（过滤器未装配）或 500
    Evidence: .sisyphus/evidence/task-3-security-autoconfig.txt

  Scenario: unitId 注入验证
    Tool: Bash (curl)
    Steps:
      1. 登录 admin，获取 token
      2. 在 controller 加临时端点 GET /test/unit-id 返回 CurrentUser.get().unitId()（或用 /sys/auth/me）
      3. curl 该端点，断言 unitId 非 null
    Expected Result: unitId 字段有值（admin 默认单位）
    Failure Indicators: unitId 为 null（filter 未填充）
    Evidence: .sisyphus/evidence/task-3-unitid-injection.txt
  ```

  **Commit**: YES（分 2 commit）
  - C1: `refactor(security): extract platform-security module from app`
  - C2: `feat(security): inject unitId into JWT claims and CurrentUser`
  - Pre-commit: `mvn test -Dspring.profiles.active=test`

- [x] 4. **RedisConfig + RedisTemplate 真实接线** [Wave 1]

  **What to do**:
  - 在 `platform-common` 新增 `cache/RedisConfig.java`：配置 `RedisConnectionFactory`（Lettuce）+ `RedisTemplate<String, Object>`（Jackson2JsonRedisSerializer）+ `StringRedisTemplate`
  - `application.yml` 已有 Redis 配置（192.168.1.2:6380），补充 `spring.data.redis.password=Redis@2025`（从环境变量 `REDIS_PASSWORD` 注入，.env.example 补充）
  - 在 platform-common 加 `RedisCacheService`（封装常用操作：get/set/delete/incr/expire/exists）
  - 添加 Redis 连接集成测试（Testcontainers Redis 或真实 192.168.1.2:6380）
  - 为后续任务（T8 锁定/T9 验证码/T10 黑名单/T15 WebSocket）提供缓存抽象

  **Must NOT do**:
  - 不在此任务实现具体业务缓存（如权限缓存）— 仅基础设施
  - 不用 Jedis（项目已选 Lettuce via spring-boot-starter）
  - 不硬编码密码（环境变量注入）

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 标准 Spring Boot Redis 配置，模式成熟
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T2/T3/T5/T6/T7 同 Wave，T2 完成后即可）
  - **Parallel Group**: Wave 1
  - **Blocks**: T8, T9, T10, T15
  - **Blocked By**: T1

  **References**:
  - **Existing config**: `backend/app/src/main/resources/application.yml:30-35` — Redis 连接配置已存在
  - **Existing dep**: `backend/modules/sys/pom.xml` — spring-boot-starter-data-redis 已声明
  - **Env**: 用户提供 Redis 192.168.1.2:6380 密码 Redis@2025
  - **WHY**: Redis 引入但零使用；T8-T11/T15 都依赖它；必须先建可复用基础设施

  **Acceptance Criteria**:
  - [ ] RedisConfig 创建，RedisTemplate Bean 可注入
  - [ ] RedisCacheService 封装完成（含 TTL/incr 操作）
  - [ ] .env.example 补充 REDIS_PASSWORD
  - [ ] 连接测试通过（set/get 断言）
  - [ ] 密码未硬编码（从 env 注入）

  **QA Scenarios**:
  ```
  Scenario: Redis 连接与基本操作
    Tool: Bash
    Preconditions: Redis 192.168.1.2:6380 可达（或 Testcontainers Redis）
    Steps:
      1. mvn test -Dtest=RedisCacheServiceTest -Dspring.profiles.active=test
      2. 测试 set("test:key","value",10s) → get → 断言 "value" → delete → exists 返回 false
      3. 测试 incr("counter") 3 次返回 1,2,3
    Expected Result: 所有断言通过
    Failure Indicators: 连接超时或序列化错误
    Evidence: .sisyphus/evidence/task-4-redis-basic.txt

  Scenario: 密码未泄露
    Tool: Bash
    Steps:
      1. grep -rn "Redis@2025" backend/ --include="*.java" --include="*.yml"
      2. 仅 .env.example 或 docker-compose 可含（作为示例），源码零硬编码
    Expected Result: 源码零命中
    Evidence: .sisyphus/evidence/task-4-no-hardcoded-secret.txt
  ```

  **Commit**: YES
  - Message: `feat(common): wire RedisConfig with RedisTemplate and cache service`
  - Files: RedisConfig.java, RedisCacheService.java, application.yml, .env.example
  - Pre-commit: `mvn test -Dspring.profiles.active=test`

- [x] 5. **数据权限框架 + ScopedEntity + 递归 CTE** [Wave 1]

  **What to do**:
  - 在 platform-common 新增 `datapolicy/` 包：
    - `DataScope` enum（ALL/UNIT/UNIT_BELOW/SELF/CUSTOM）
    - `DataScopeContext`（ThreadLocal 持当前用户 scope + unitId + customUnitIds）
    - `DataScopeSpecification<T>`（JPA Specification 工厂，按 scope 生成 Predicate：ALL→无限制 / SELF→createdBy=? / UNIT→unitId=? / UNIT_BELOW→unitId IN (subtree) / CUSTOM→unitId IN (customUnitIds)）
    - `ScopedEntity` @MappedSuperclass（含 unitId + createdBy 字段）
    - `ScopedRepository<T,ID>` extends JpaRepository + JpaSpecificationExecutor
  - Flyway：`backend/modules/sys/src/main/resources/db/migration/V3__sys_data_scope.sql` 创建 `sys_role_data_scope`（roleId, unitId 复合主键）支持 CUSTOM
  - SysUnit 增加递归 CTE 查询方法（`@Query(nativeQuery=true)` 查子孙单位 IDs）：`WITH RECURSIVE unit_tree AS (...)`
  - SysUser 改 extends ScopedEntity（加 unitId 已有，加 createdBy）+ SysUserRepository extends ScopedRepository
  - ArchUnit 测试：所有 `com.example..repository` 中的 Repository extends ScopedRepository（强制）
  - 注意：BaseEntity 的 createdBy 需 @CreatedDate 或手动在 service 填充（当前未填充，需补 @EnableJpaAuditing 的 AuditorAware）

  **Must NOT do**:
  - 不用 Hibernate @Filter（Metis 护栏）
  - 不用字符串拼接 SQL（注入风险）
  - 不在此任务对 SysUser 查询实际应用 DataScope（T17 做，本任务只建框架）
  - 不改 SysRole.dataScope 字段类型（保持 String 兼容现有数据）

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 横切关注点 + JPA Specification + 递归 SQL + ArchUnit，需深度设计
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T2/T3/T4/T6/T7 同 Wave）
  - **Parallel Group**: Wave 1
  - **Blocks**: T17
  - **Blocked By**: T1（需 H2 profile 跑测试）

  **References**:
  - **Existing field**: `backend/modules/sys/src/main/java/com/example/sys/domain/SysRole.java` — dataScope String 字段已存在（5 种值）
  - **Existing entity**: `backend/modules/sys/src/main/java/com/example/sys/domain/SysUnit.java` — parentId 邻接表，需递归 CTE
  - **Pattern**: `backend/platform-common/src/main/java/com/example/common/persistence/BaseEntity.java` — @MappedSuperclass 范例（ScopedEntity 仿此）
  - **Pattern**: `backend/platform-common/src/main/java/com/example/common/security/PermissionAspect.java` — ThreadLocal + AOP 范例（DataScopeContext 仿此模式）
  - **External**: yudao `DeptDataPermissionRule` 架构（ThreadLocal context + pluggable rule）→ 端口到 JPA Specification
  - **WHY**: dataScope 字段在但从未用于查询；纯 JPA → Specification 是唯一正确方案；UNIT_BELOW 需递归 CTE 因 SysUnit 仅邻接表

  **Acceptance Criteria**:
  - [ ] DataScope/DataScopeContext/DataScopeSpecification/ScopedEntity/ScopedRepository 创建
  - [ ] V3__sys_data_scope.sql 创建 sys_role_data_scope 表
  - [ ] SysUnit 递归 CTE 方法 + 测试（3 级单位树验证）
  - [ ] SysUser extends ScopedEntity，SysUserRepository extends ScopedRepository
  - [ ] AuditorAware 配置（createdBy 填充）
  - [ ] ArchUnit: 业务 Repository extends ScopedRepository
  - [ ] DataScopeSpecification 5 种 scope 单元测试（断言生成的 Predicate SQL）

  **QA Scenarios**:
  ```
  Scenario: 递归 CTE 查子孙单位
    Tool: Bash
    Preconditions: H2 profile，种子数据：总部→分公司A→部门1（3级）
    Steps:
      1. mvn test -Dtest=SysUnitTest -Dspring.profiles.active=test
      2. 测试 findDescendantUnitIds(总部ID) 返回 [分公司A, 部门1]
    Expected Result: 返回所有子孙单位 ID
    Failure Indicators: 仅返回直接子级或递归报错
    Evidence: .sisyphus/evidence/task-5-recursive-cte.txt

  Scenario: DataScopeSpecification 各 scope 生成正确 Predicate
    Tool: Bash
    Steps:
      1. mvn test -Dtest=DataScopeSpecificationTest -Dspring.profiles.active=test
      2. 对每种 scope 断言 Specification 生成的 where 子句（ALL→无 / SELF→createdBy=? / UNIT→unitId=? 等）
    Expected Result: 5 种 scope 各生成预期 Predicate
    Evidence: .sisyphus/evidence/task-5-datascope-spec.txt

  Scenario: ArchUnit 强制 ScopedRepository
    Tool: Bash
    Steps:
      1. mvn test -Dtest=ArchUnitTest -Dspring.profiles.active=test
      2. 断言所有 Repository extends ScopedRepository
    Expected Result: PASS（现有 SysUserRepository 已改）
    Failure Indicators: 任一 Repository 未继承
    Evidence: .sisyphus/evidence/task-5-archunit-scoped.txt
  ```

  **Commit**: YES（分 3 commit）
  - C1: `flyway(sys): V3 add sys_role_data_scope table`
  - C2: `feat(common): add DataScope framework with ScopedEntity and Specification`
  - C3: `feat(sys): SysUser implements ScopedEntity, recursive CTE for unit tree`
  - Pre-commit: `mvn test -Dspring.profiles.active=test`

- [x] 6. **前端基础设施修复（v-permission + NNotificationProvider + useBreakpoint + 清理行内样式 + ESLint）** [Wave 1]

  **What to do**:
  - `main.ts` 注册全局指令：`app.directive('permission', vPermission)`（导入 `shared/directives/permission.ts`）
  - `App.vue` 在 Provider 链增加 `<NNotificationProvider>`（为消息中心实时弹窗准备）
  - 新增 `shared/composables/useBreakpoint.ts`：响应式断点（mobile <768 / tablet 768-1024 / desktop >1024），基于 `window.matchMedia`
  - 清理现有约 10 处行内 `style="..."`（unit/menu/user/role/config/Login/Dashboard 等），改为 Tailwind class（如 `style="width:500px"` → `class="w-[500px]"`）
  - `.eslintrc` 添加规则禁止 `style` 属性（`vue/no-restricted-html-elements` 或自定义规则检测 `style="`）
  - 新增 `shared/composables/useWebSocket.ts` 占位骨架（T20/T15 填充实现）
  - 单元测试：v-permission 注册测试、useBreakpoint 测试

  **Must NOT do**:
  - 不在此任务实现 WebSocket 逻辑（仅骨架）
  - 不重构页面布局（T18 响应式改造做）
  - 不引入新依赖（用现有 Naive UI + Tailwind）

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: 前端基础设施 + 样式规范，属 UI/前端工程
  - **Skills**: [`frontend-design`]
    - `frontend-design`: Tailwind class 替换需保持视觉一致

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T2-T5/T7 同 Wave）
  - **Parallel Group**: Wave 1
  - **Blocks**: T18-T24
  - **Blocked By**: T1

  **References**:
  - **Target**: `frontend/src/main.ts` — 缺少 app.directive 注册
  - **Target**: `frontend/src/shared/directives/permission.ts` — vPermission 定义已存在，未注册
  - **Target**: `frontend/src/App.vue` — Provider 链需加 NNotificationProvider
  - **Pattern**: `frontend/src/stores/__tests__/auth.test.ts` — Vitest 测试范式
  - **Existing**: `frontend/src/styles/index.css` — Tailwind + CSS 变量 token 入口
  - **WHY**: v-permission 未注册是现有 bug（按钮权限失效）；NNotificationProvider 是消息中心前置；ESLint 规则从源头禁止行内样式

  **Acceptance Criteria**:
  - [ ] main.ts 注册 v-permission
  - [ ] App.vue 含 NNotificationProvider
  - [ ] useBreakpoint.ts 创建 + 测试
  - [ ] 现有行内 style 全部清理（grep `style="` 在 src/ 零命中，NModal width 等例外需记录）
  - [ ] ESLint 规则禁止行内 style
  - [ ] vitest 全绿

  **QA Scenarios**:
  ```
  Scenario: v-permission 生效
    Tool: Playwright
    Preconditions: 前端 dev server 运行，登录 admin
    Steps:
      1. 导航 /sys/user
      2. 断言无 sys:user:add 权限的用户看不到"新增"按钮（用低权账号或 mock）
      3. admin 看到"新增"按钮
    Expected Result: v-permission 正确隐藏/显示按钮
    Evidence: .sisyphus/evidence/task-6-vpermission.png

  Scenario: 零行内样式
    Tool: Bash (grep)
    Steps:
      1. grep -rn 'style="' frontend/src/ --include="*.vue" | grep -v 'NModal.*width' | grep -v 'NSpin'
      2. 仅记录的例外（NModal width 等组件必需）允许
    Expected Result: 业务行内样式零命中
    Evidence: .sisyphus/evidence/task-6-no-inline-style.txt
  ```

  **Commit**: YES
  - Message: `fix(frontend): register v-permission, add NNotificationProvider, ban inline styles`
  - Pre-commit: `npm run lint:check && npm run test`

- [x] 7. **修复 unit/menu 空白页（NTree 重构）** [Wave 1]

  **What to do**:
  - `frontend/src/modules/sys/views/unit/index.vue`：删除手动 `v-for + {{ node.label() }}` 渲染（VNode 插值 bug），改用 Naive UI `<NTree :data="treeData" key-field="id" label-field="..." expand-on-click :render-label="...">` + 自定义节点（用 `render-prefix`/`render-suffix` 放新增/编辑/删除按钮）
  - 同样修复 `menu/index.vue`
  - 利用 T6 注册的 v-permission（按钮权限）
  - NTree 配置：默认展开第一级、支持拖拽排序（可选）、空状态提示
  - 响应式：窄屏时树占满宽度（T18 统一改，本任务先保证桌面正常）

  **Must NOT do**:
  - 不改 API 层（unitApi/menuApi 接口不变）
  - 不改后端（问题在前端渲染）
  - 不引入新组件库

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: Vue 组件重构 + Naive UI NTree 使用
  - **Skills**: [`frontend-design`]

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T2-T6 同 Wave，独立前端任务）
  - **Parallel Group**: Wave 1
  - **Blocks**: None（独立修复）
  - **Blocked By**: T1（基线绿）；建议依赖 T6（v-permission 注册后按钮才正常）

  **References**:
  - **Target**: `frontend/src/modules/sys/views/unit/index.vue:113-122` — VNode 插值 bug
  - **Target**: `frontend/src/modules/sys/views/menu/index.vue:138-147` — 同样 bug
  - **Pattern**: `frontend/src/modules/sys/views/user/index.vue` — NDataTable + render h 函数按钮范式（NTree 仿此按钮渲染）
  - **External**: Naive UI NTree 文档 `https://www.naiveui.com/zh-CN/os-theme/components/tree` — key-field/label-field/render-label API
  - **WHY**: `{{ node.label() }}` 返回 VNode 但插值渲染成 [object Object]；NTree 是 Naive UI 标准树组件，支持自定义渲染

  **Acceptance Criteria**:
  - [ ] unit/index.vue 用 NTree 渲染
  - [ ] menu/index.vue 用 NTree 渲染
  - [ ] 树节点含新增/编辑/删除按钮（v-permission 控制）
  - [ ] CRUD 操作正常（新增/编辑/删除后树刷新）
  - [ ] 空数据状态有提示

  **QA Scenarios**:
  ```
  Scenario: unit 页面渲染树（非空白）
    Tool: Playwright
    Preconditions: 登录 admin，后端有种子单位数据
    Steps:
      1. 导航 /sys/unit
      2. 等待 .n-tree 选择器可见（timeout 10s）
      3. 截图
      4. 断言页面含至少 1 个树节点（.n-tree-node）
      5. 点击"新增"按钮，断言 NModal 出现
    Expected Result: 树正常渲染，操作按钮工作
    Failure Indicators: 页面空白或 .n-tree 不存在
    Evidence: .sisyphus/evidence/task-7-unit-tree.png

  Scenario: menu 页面渲染树 + CRUD
    Tool: Playwright
    Steps:
      1. 导航 /sys/menu
      2. 断言 NTree 渲染含"系统管理"目录节点
      3. 点击某节点的"新增"，填表保存，断言树刷新出现新节点
      4. 删除新节点，断言消失
    Expected Result: 完整 CRUD 工作
    Evidence: .sisyphus/evidence/task-7-menu-tree-crud.png
  ```

  **Commit**: YES
  - Message: `fix(sys): rebuild unit/menu pages with NTree, fix blank rendering`
  - Pre-commit: `npm run lint:check && npm run build`

- [x] 8. **登录安全 - 锁定（3 次错误）+ 管理员解锁** [Wave 2]

  **What to do**:
  - 新建 `backend/modules/sys/src/main/java/com/example/sys/service/LoginSecurityService.java`
  - 登录失败：`RedisCacheService.incr("login:fail:" + username)` + TTL（如 30min）；当计数达 3，在 SysUser.status 或单独 Redis 标记 `user:lock:userId` 锁定
  - `AuthController.login`：登录前检查锁定状态 → 返回 423 LOCKED + 提示联系管理员；登录成功清除失败计数
  - 管理员解锁接口 `POST /sys/user/{id}/unlock`（@RequiresPermission("sys:user:unlock")）：清除 Redis 锁标记 + 失败计数
  - 前端用户管理增加"解锁"按钮（T24 做，本任务仅 API）
  - 单元测试：3 次失败后锁定、解锁后可登录、成功登录清零、并发安全（Redis INCR 原子）
  - 锁定阈值通过 SysConfig 可配（`sys.security.login.max-fail-count`）

  **Must NOT do**:
  - 不用数据库计数器（Redis 原子操作，性能 + 并发安全）
  - 不锁定 admin 账号导致系统无法管理（admin 锁定仍可在控制台/SQL 解锁，或豁免）
  - 不在此任务加验证码（T9 做）

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 安全逻辑 + Redis 原子操作 + 并发边界 + 可配置
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T9-T17 同 Wave，但依赖 T3/T4）
  - **Parallel Group**: Wave 2（blocked by T3, T4）
  - **Blocks**: T24
  - **Blocked By**: T3（SecurityConfig）, T4（Redis）

  **References**:
  - **Target**: `backend/modules/sys/src/main/java/com/example/sys/controller/AuthController.java` — login 方法待加锁定检查
  - **Target**: `backend/modules/sys/src/main/java/com/example/sys/controller/UserController.java` — 加 unlock 端点
  - **Dependency**: T4 RedisCacheService.incr（原子计数）
  - **Pattern**: `backend/modules/sys/src/main/java/com/example/sys/service/UserService.java` — Service 范式 + 事件发布
  - **WHY**: 用户明确需求 3 次锁定 + 管理员解锁；Redis INCR 解决并发竞态（Metis 边界场景 1）

  **Acceptance Criteria**:
  - [ ] LoginSecurityService 创建
  - [ ] login 失败 3 次后第 4 次返回 423
  - [ ] unlock 接口清除锁定
  - [ ] 成功登录清零失败计数
  - [ ] 并发测试：5 个并发失败请求，计数精确为 5（非 3 或 4）
  - [ ] 阈值可通过 SysConfig 配置
  - [ ] 单元测试覆盖率 ≥80%

  **QA Scenarios**:
  ```
  Scenario: 3 次错误后锁定
    Tool: Bash (curl)
    Steps:
      1. curl POST /sys/auth/login 错误密码，重复 3 次
      2. 第 4 次（正确密码）请求
    Expected Result: 前 3 次返回 401，第 4 次返回 423 + message 含"锁定"
    Failure Indicators: 第 4 次返回 200（锁定未生效）或 401（未区分锁定）
    Evidence: .sisyphus/evidence/task-8-lockout.txt

  Scenario: 管理员解锁
    Tool: Bash (curl)
    Steps:
      1. 锁定 admin 之外的用户 X（如 testuser）
      2. admin 登录获 token
      3. curl POST /sys/user/{X_id}/unlock -H "Authorization: Bearer admin_token"
      4. testuser 用正确密码登录
    Expected Result: unlock 返回 200，testuser 登录返回 200
    Evidence: .sisyphus/evidence/task-8-unlock.txt

  Scenario: 并发安全（Metis 边界 1）
    Tool: Bash
    Steps:
      1. 用 5 个并发请求错误登录 testuser
      2. 查 Redis login:fail:testuser 计数
    Expected Result: 计数精确为 5（INCR 原子）
    Failure Indicators: 计数 < 5（竞态丢更新）
    Evidence: .sisyphus/evidence/task-8-concurrent.txt
  ```

  **Commit**: YES（TDD）
  - C1: `test(sys): add failing tests for login lockout and unlock`
  - C2: `feat(sys): implement 3-attempt lockout with Redis atomic counter`
  - Pre-commit: `mvn test -Dspring.profiles.active=test`

- [x] 9. **登录安全 - 图形验证码（Hutool 本地 + Redis 单次）** [Wave 2]

  **What to do**:
  - 引入 `cn.hutool:hutool-captcha` 依赖（父 pom dependencyManagement）
  - 新接口 `GET /sys/auth/captcha`：生成验证码图片（base64）+ captchaId（UUID），存 Redis（key=`captcha:` + captchaId，TTL 5min）
  - 修改 LoginDTO 增加 `captchaId` + `captchaCode` 字段
  - `AuthController.login`：校验 captchaCode == Redis 存储值；校验成功后**立即删除**（单次使用，防重放）；校验失败返回 400
  - 验证码可配开关（SysConfig `sys.security.captcha.enabled`，默认 true）
  - 前端登录页加验证码输入（T19 做，本任务仅 API）
  - 测试：正确验证码通过、错误验证码 400、重放攻击拒绝（同一 captchaId 二次使用失败）、过期验证码失败

  **Must NOT do**:
  - 不用云验证码（reCAPTCHA/Turnstile，需外网 + 隐私审查）
  - 不把验证码答案返回前端
  - 不允许多次使用同一 captchaId（Metis 边界 3）

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 成熟库 + 标准 Redis 模式
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T8/T10-T17 同 Wave）
  - **Parallel Group**: Wave 2（blocked by T4）
  - **Blocks**: T19
  - **Blocked By**: T4（Redis）

  **References**:
  - **Target**: `backend/modules/sys/src/main/java/com/example/sys/controller/AuthController.java` — login 加校验
  - **Target**: `backend/modules/sys/src/main/java/com/example/sys/dto/LoginDTO.java` — 加字段
  - **Dependency**: T4 RedisCacheService
  - **External**: Hutool Captcha 文档 `https://hutool.cn/docs/#/captcha`
  - **PUBLIC_PATHS**: 需加 `/sys/auth/captcha` 到白名单（platform-security SecurityConfig）
  - **WHY**: 防暴力破解（与锁定互补）；Redis 单次使用防重放（Metis 边界 3）

  **Acceptance Criteria**:
  - [ ] /sys/auth/captcha 返回 base64 图片 + captchaId
  - [ ] login 校验验证码，失败 400
  - [ ] 同一 captchaId 二次使用拒绝
  - [ ] 过期验证码（TTL 后）失败
  - [ ] SysConfig 开关生效

  **QA Scenarios**:
  ```
  Scenario: 验证码生成与校验
    Tool: Bash (curl)
    Steps:
      1. curl GET /sys/auth/captcha → {captchaId, image: "data:image/png;base64,..."}
      2. 提取 captchaId（图片答案需测试中 mock 或可读模式获取，Hutool 可配置返回文本）
    Expected Result: 返回结构正确，图片非空
    Evidence: .sisyphus/evidence/task-9-captcha-gen.txt

  Scenario: 重放攻击拒绝
    Tool: Bash
    Steps:
      1. 获取 captchaId + 正确 code
      2. 用该 code 登录一次（成功）
      3. 再次用同一 captchaId 登录
    Expected Result: 第二次返回 400（captcha 已删除）
    Evidence: .sisyphus/evidence/task-9-replay-attack.txt
  ```

  **Commit**: YES
  - Message: `feat(sys): add Hutool captcha with Redis single-use enforcement`
  - Pre-commit: `mvn test -Dspring.profiles.active=test`

- [x] 10. **Token 黑名单（jti + Redis）+ 登出端点** [Wave 2]

  **What to do**:
  - JWT 增加 `jti`（JWT ID）claim（修改 JwtUtil.generate）
  - 新接口 `POST /sys/auth/logout`：将当前 token 的 jti 加入 Redis 黑名单 `jwt:blacklist:` + jti，TTL = token 剩余有效期
  - 修改 `JwtAuthFilter`（platform-security）：每次请求校验 jti 是否在黑名单（RedisCacheService.exists），是则返回 401
  - 管理员强制踢出（T11 会话管理用）：将目标 jti 加入黑名单
  - 测试：登出后 token 失效（401）、黑名单 TTL 过期后 token 自然失效（不残留）、伪造 jti 无法黑名单他人（jti 从 token 解析）

  **Must NOT do**:
  - 不把整个 token 存 Redis（仅 jti 字符串，省内存）
  - 不改变 token 签名/结构（仅加 claim）
  - 黑名单查询需 < 5ms（Redis 单次 GET）

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: JWT 结构变更 + 过滤器逻辑 + 安全边界
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T8/T9/T11-T17 同 Wave，但 T11 依赖本任务）
  - **Parallel Group**: Wave 2（blocked by T3, T4）
  - **Blocks**: T11
  - **Blocked By**: T3（JwtAuthFilter 在 platform-security）, T4（Redis）

  **References**:
  - **Target**: `backend/platform-common/src/main/java/com/example/common/security/JwtUtil.java` — 加 jti claim
  - **Target**: platform-security JwtAuthFilter（T3 迁移后）— 加黑名单检查
  - **Dependency**: T3, T4
  - **WHY**: 无状态 JWT 无法主动失效（Metis Finding 2）；jti + Redis 黑名单是标准方案；每次请求 1 次 Redis GET 延迟可控（<5ms）

  **Acceptance Criteria**:
  - [ ] JWT 含 jti claim
  - [ ] logout 接口将 jti 加入 Redis 黑名单
  - [ ] 登出后原 token 返回 401
  - [ ] 黑名单 TTL = token 剩余寿命
  - [ ] JwtAuthFilter 每请求校验黑名单
  - [ ] 延迟测试：黑名单查询增加 < 5ms p99

  **QA Scenarios**:
  ```
  Scenario: 登出后 token 失效
    Tool: Bash (curl)
    Steps:
      1. admin 登录获 token
      2. curl /sys/auth/me -H token → 200
      3. curl POST /sys/auth/logout -H token → 200
      4. curl /sys/auth/me -H 同 token → 401
    Expected Result: 第 4 步 401
    Evidence: .sisyphus/evidence/task-10-logout-blacklist.txt

  Scenario: 性能 - 黑名单查询延迟
    Tool: Bash
    Steps:
      1. 登录获 token
      2. 用 ab/wrk 对 /sys/auth/me 压测 1000 请求
      3. 对比 T3 完成时（无黑名单）的 p99 延迟
    Expected Result: p99 增量 < 5ms
    Evidence: .sisyphus/evidence/task-10-latency.txt
  ```

  **Commit**: YES
  - Message: `feat(security): add jti-based token blacklist with Redis, logout endpoint`

- [x] 11. **在线会话管理（会话列表 + 强制踢出）** [Wave 2]

  **What to do**:
  - 登录成功时在 Redis 记录会话：`session:active:` + jti → {userId, username, ip, userAgent, loginAt, deviceType}，TTL = token 有效期
  - 新接口 `GET /sys/auth/sessions`（当前用户）和 `GET /sys/user/{id}/sessions`（管理员，@RequiresPermission("sys:user:session")）：列出活跃会话
  - `POST /sys/auth/sessions/{jti}/revoke`（自己）/ `POST /sys/user/{id}/sessions/{jti}/revoke`（管理员）：将 jti 加入黑名单（T10）+ 删除会话记录
  - deviceType 从 User-Agent 解析（浏览器/OS）
  - 测试：登录后会话列表 +1、踢出后目标 token 401、过期会话自动消失（TTL）

  **Must NOT do**:
  - 不做设备指纹/geo-IP/异常检测（Metis 范围排除）
  - 不限制单点登录（允许多设备同时登录）

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: 标准 CRUD + Redis 模式，中等复杂度
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T8/T9/T12-T17 同 Wave，但依赖 T10）
  - **Parallel Group**: Wave 2（blocked by T10）
  - **Blocks**: T23
  - **Blocked By**: T10（黑名单）

  **References**:
  - **Target**: AuthController 加 sessions/revoke 端点
  - **Dependency**: T10 黑名单（强制踢出 = 加黑名单）
  - **WHY**: 用户明确需求"在线会话/设备管理"；强制踢出复用 T10 黑名单机制

  **Acceptance Criteria**:
  - [ ] 登录后 Redis 会话记录
  - [ ] GET 会话列表返回活跃会话
  - [ ] revoke 后目标 token 401
  - [ ] deviceType 从 UA 解析

  **QA Scenarios**:
  ```
  Scenario: 会话列表与强制踢出
    Tool: Bash (curl)
    Steps:
      1. testuser 在"设备 A"登录获 tokenA
      2. admin 登录获 adminToken
      3. curl GET /sys/user/{testuser_id}/sessions -H adminToken → 含 tokenA 的会话
      4. curl POST /sys/user/{testuser_id}/sessions/{jtiA}/revoke -H adminToken → 200
      5. curl /sys/auth/me -H tokenA → 401
    Expected Result: 第 5 步 401（被强制踢出）
    Evidence: .sisyphus/evidence/task-11-session-kickout.txt
  ```

  **Commit**: YES
  - Message: `feat(sys): add active session management with force-kickout via blacklist`

- [x] 12. **模块化登录 SPI 框架 + /login-methods API** [Wave 2]

  **What to do**:
  - 在 platform-common 新增 `login/` 包：
    - `LoginMethodProvider` 接口：`String getMethod()`（如 "password"/"ldap"/"wechat"）、`LoginVO authenticate(LoginRequest)`、`boolean isEnabled()`、`int getOrder()`、`LoginMethodDescriptor describe()`（含图标/显示名/排序，供前端渲染）
    - `LoginMethodRegistry`：Spring 自动收集所有 LoginMethodProvider Bean，按 order 排序
    - `LoginRequest` record（含 method + 通用字段 username/password + 扩展属性 Map）
  - 新接口 `GET /sys/auth/login-methods`（PUBLIC）：返回已启用的登录方式列表 `[{method, label, icon, order}]`
  - `AuthController.login` 改为：按 LoginRequest.method 路由到对应 Provider（默认 "password"）
  - 现有账密登录重构为 `PasswordLoginProvider implements LoginMethodProvider`
  - 测试：多 provider 注册、路由正确、禁用的 provider 不返回

  **Must NOT do**:
  - 不做运行时热插拔（Metis 护栏：Starter 装配粒度）
  - 不破坏 LoginVO 响应结构（E2E 锁定）

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: SPI 框架设计 + 接口契约 + 多 provider 路由
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T8-T11/T13-T17 同 Wave，依赖 T3）
  - **Parallel Group**: Wave 2（blocked by T3）
  - **Blocks**: T13（LDAP provider）, T19（前端动态渲染）
  - **Blocked By**: T3（AuthController 在 sys，依赖 platform-security）

  **References**:
  - **Target**: `backend/modules/sys/src/main/java/com/example/sys/controller/AuthController.java` — login 重构为路由
  - **Pattern**: `backend/modules/sys/src/main/java/com/example/sys/SysApi.java` — 门面模式范例
  - **Pattern**: Spring Security `AuthenticationProvider` 链设计思想（多 provider 按 order）
  - **WHY**: 用户明确"模块化加载"，加 starter 自动注册 provider；前端动态获取方式列表

  **Acceptance Criteria**:
  - [ ] LoginMethodProvider/Registry/Descriptor 创建
  - [ ] PasswordLoginProvider 重构现有登录
  - [ ] /sys/auth/login-methods 返回 [{method:"password",...}]
  - [ ] login 按 method 路由
  - [ ] 多 provider 测试（注册 2 个 mock，断言返回 2 个 + 按 order 排序）
  - [ ] 现有 E2E 登录测试仍通过（LoginVO 不变）

  **QA Scenarios**:
  ```
  Scenario: 登录方式动态返回
    Tool: Bash (curl)
    Steps:
      1. curl GET /sys/auth/login-methods（无需 token）
      2. 断言返回数组含 {method:"password", label:"账号密码登录"}
    Expected Result: 至少返回 password 方式
    Evidence: .sisyphus/evidence/task-12-login-methods.txt

  Scenario: 按 method 路由
    Tool: Bash
    Steps:
      1. curl POST /sys/auth/login -d '{"method":"password","username":"admin","password":"admin123"}'
      2. curl POST /sys/auth/login -d '{"method":"nonexist",...}'
    Expected Result: 第 1 次 200 + token，第 2 次 400（不支持的 method）
    Evidence: .sisyphus/evidence/task-12-method-routing.txt
  ```

  **Commit**: YES
  - Message: `feat(common): add LoginMethodProvider SPI framework with dynamic discovery`

- [x] 13. **LDAP 登录 provider starter（利用 OpenLDAP 192.168.1.2:389）** [Wave 2]

  **What to do**:
  - 新建 `backend/modules/login-ldap/` 模块（pom 继承根，依赖 platform-common + spring-ldap-core 或 unboundid-ldapsdk）
  - 实现 `LdapLoginProvider implements LoginMethodProvider`：getMethod()="ldap"，authenticate 用 LDAP bind（用户 DN 模板 `uid={username},dc=devenv,dc=local`）校验
  - 配置（application.yml）：`platform.login.ldap.url=ldap://192.168.1.2:389`、`user-dn-pattern`、`enabled`（从 env 注入，默认 false）
  - AutoConfiguration：当 enabled=true 时注册 LdapLoginProvider Bean
  - 登录成功后：若本地无该用户，可选自动创建 SysUser（uid → username, mail → email, cn → realName），分配默认角色
  - 在 platform-starter/pom.xml 加 login-ldap 依赖（默认引入但 enabled=false）
  - 测试：用 Testcontainers OpenLDAP 或 mock LDAP（uid=hai 密码验证）
  - 创建 MODULE.md

  **Must NOT do**:
  - 不硬编码 LDAP 凭证（env 注入）
  - 不默认启用（enabled=false，需显式配置）
  - 不实现 LDAP 写操作（仅认证读）

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 新模块 + LDAP 协议 + 自动建户逻辑
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T8-T11/T14-T17 同 Wave，依赖 T12）
  - **Parallel Group**: Wave 2（blocked by T12）
  - **Blocks**: T19（前端 LDAP 登录入口）
  - **Blocked By**: T12（SPI 框架）

  **References**:
  - **Target env**: 用户提供 OpenLDAP 192.168.1.2:389，Admin DN cn=admin,dc=devenv,dc=local，密码 LDAP@2025，测试用户 uid=hai
  - **Pattern**: `backend/modules/sys/` 完整模块结构（pom + autoconfig + imports + MODULE.md）
  - **Dependency**: T12 LoginMethodProvider
  - **External**: Spring LDAP 文档 `https://docs.spring.io/spring-ldap/reference/` — LdapTemplate bind 认证
  - **WHY**: 用户提供了 OpenLDAP 基础设施；LDAP 是企业最常见外部登录；作为 SPI 框架的真实 provider 验证（非 mock）

  **Acceptance Criteria**:
  - [ ] login-ldap 模块创建（pom + autoconfig + imports + MODULE.md）
  - [ ] LdapLoginProvider 实现
  - [ ] enabled=true 时 /login-methods 返回 ldap 方式
  - [ ] uid=hai + 正确密码认证成功，返回 LoginVO
  - [ ] 错误密码认证失败
  - [ ] 自动建户逻辑（可选，默认开启）

  **QA Scenarios**:
  ```
  Scenario: LDAP 登录（真实 OpenLDAP）
    Tool: Bash (curl)
    Preconditions: application-dev.yml 配置 platform.login.ldap.enabled=true，url=ldap://192.168.1.2:389
    Steps:
      1. curl GET /sys/auth/login-methods → 含 {method:"ldap"}
      2. curl POST /sys/auth/login -d '{"method":"ldap","username":"hai","password":"<hai密码>"}'
      3. 断言返回 LoginVO（含 token + user.realName 含中文）
    Expected Result: LDAP 认证成功，返回有效 token
    Failure Indicators: 认证失败（bind 拒绝）或 500
    Evidence: .sisyphus/evidence/task-13-ldap-login.txt

  Scenario: LDAP 不可达时降级
    Tool: Bash
    Preconditions: ldap.enabled=true 但 url 错误
    Steps:
      1. curl POST /sys/auth/login -d '{"method":"ldap",...}'
    Expected Result: 返回 503 或 500 + 明确错误（不影响 password 方式）
    Evidence: .sisyphus/evidence/task-13-ldap-unavailable.txt
  ```

  **Commit**: YES
  - Message: `feat(login-ldap): add LDAP login provider starter using OpenLDAP`

- [x] 14. **审计日志后端（表 + @Auditable AOP 切面 + 查询 service）** [Wave 2]

  **What to do**:
  - 新建 `backend/modules/audit/` 模块（pom + autoconfig + imports + MODULE.md）
  - Flyway `V1__audit_init.sql`（模块独立版本段，避免与 sys V3 冲突）：`audit_log` 表（id, actor, actor_type, action, target_type, target_id, ip, user_agent, params TEXT, result, error_msg, created_at），按月分区（PostgreSQL native partitioning）或预留分区字段
  - `platform-common` 新增 `@Auditable(action="...")` 注解 + `AuditAspect`（@Around，仿 PermissionAspect）
  - 切面记录：actor=CurrentUser（或 "system"）/ action=注解值 / ip / UA / 方法参数 / 结果/异常
  - `AuditLogService.query(filter, pageable)`：按 actor/action/date-range/target 过滤分页
  - 在关键端点标注 @Auditable：login（成功/失败）、unlock、revoke session、message publish、external app token 签发
  - 异步写入（@Async + 队列，避免阻塞主流程 + AOP 延迟 < 5ms）
  - 测试：标注方法产生审计行、参数记录、异常记录、查询过滤

  **Must NOT do**:
  - 不做 SIEM/合规报表（Metis 范围排除）
  - 不同步写库（性能影响）
  - 不记录敏感参数明文（密码字段脱敏）

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: AOP 切面 + 异步 + 分区表，中等复杂度
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T8-T13/T15-T17 同 Wave，依赖 T2）
  - **Parallel Group**: Wave 2（blocked by T2，CurrentUser 在 common）
  - **Blocks**: T22（前端查询）
  - **Blocked By**: T2（PermissionLoader/CurrentUser 可用）

  **References**:
  - **Pattern**: `backend/platform-common/src/main/java/com/example/common/security/PermissionAspect.java` — AOP @Around 切面范例
  - **Pattern**: `backend/platform-common/src/main/java/com/example/common/security/RequiresPermission.java` — 注解定义范例
  - **Target**: sys 模块 AuthController/UserController — 加 @Auditable
  - **WHY**: 用户明确需求"操作审计日志"；PermissionAspect 是现成 AOP 范式；异步写避免 p99 延迟（Metis 验收）

  **Acceptance Criteria**:
  - [ ] audit 模块创建
  - [ ] audit_log 表（含分区或预留）
  - [ ] @Auditable 注解 + AuditAspect
  - [ ] login 成功/失败产生审计行
  - [ ] 异步写入（主线程不阻塞）
  - [ ] 查询 service 按多条件过滤
  - [ ] 密码参数脱敏
  - [ ] AOP 延迟 < 5ms

  **QA Scenarios**:
  ```
  Scenario: 登录产生审计
    Tool: Bash (curl)
    Steps:
      1. curl POST /sys/auth/login（成功）
      2. curl POST /sys/auth/login（错误密码，失败）
      3. admin 查询 audit_log：GET /sys/audit/logs?action=LOGIN
      4. 断言 2 行（success + fail），actor 含 admin/未知
    Expected Result: 2 行审计，result 字段区分 success/fail
    Evidence: .sisyphus/evidence/task-14-audit-login.txt

  Scenario: AOP 延迟
    Tool: Bash
    Steps:
      1. 对比 @Auditable 标注端点 vs 未标注的 p99 延迟
    Expected Result: 增量 < 5ms（异步写不阻塞）
    Evidence: .sisyphus/evidence/task-14-aop-latency.txt

  Scenario: 密码脱敏
    Tool: Bash
    Steps:
      1. 查询 login 审计的 params 字段
      2. 断言 password 字段为 "***" 而非明文
    Expected Result: 密码脱敏
    Evidence: .sisyphus/evidence/task-14-password-mask.txt
  ```

  **Commit**: YES
  - C1: `flyway(audit): V1 add audit_log table`
  - C2: `feat(audit): add @Auditable AOP aspect with async write`
  - Pre-commit: `mvn test -Dspring.profiles.active=test`

- [x] 15. **消息中心后端（notify 模块：WebSocket + 三级 + 多范围 + 对外接口）** [Wave 2]

  **What to do**:
  - 新建 `backend/modules/notify/` 模块（pom + autoconfig + imports + MODULE.md）
  - 父 pom dependencyManagement 加 `spring-boot-starter-websocket`
  - Flyway `V1__notify_init.sql`：
    - `notify_message`（id, title, content, level ENUM(URGENT/IMPORTANT/NORMAL), sender_id, send_time, expire_time, business_type, created_at）
    - `notify_recipient`（id, message_id, recipient_type ENUM(USER/ROLE/UNIT), recipient_id, read_status, read_time）
    - `notify_user_inbox`（id, user_id, message_id, seq, delivered, delivered_at）— seq 用于重连重放
  - `MessageService.publish(PublishDTO)`：按 recipientType 展开（USER→直接 / ROLE→查用户 / UNIT→递归查单位+子单位用户），批量插入 inbox，level=URGENT 时实时推送
  - WebSocket：`@ServerEndpoint` 或 Spring WebSocketHandler，连接时绑定 userId，维护 `Map<userId, Set<Session>>`；客户端连接发 lastSeqReceived，服务端重放未送达消息（TTL 24h）
  - 推送格式：{type:"message", level, title, content, messageId}
  - 对外 HTTP 接口 `POST /openapi/notify/publish`（@RequiresAppScope("notify:publish")，T16 外部应用鉴权）：供外部系统/SDK 调用
  - 内部 HTTP `POST /sys/notify/publish`（@RequiresPermission("sys:notify:publish")）：管理员手动发布
  - 三级差异化由前端实现（T20），后端仅返回 level
  - 测试：单用户/角色/单位范围发布、WebSocket 实时推送、离线重连重放、TTL 过期清理

  **Must NOT do**:
  - 不做邮件/短信/推送渠道（Metis 范围排除，预留 MessageChannel SPI）
  - 不做接收者偏好覆盖（发布者决定级别，Metis default）
  - 不破坏 sys 模块

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: WebSocket + 复杂范围展开 + 重放 + 对外接口，高复杂度
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T8-T14/T16/T17 同 Wave，依赖 T4）
  - **Parallel Group**: Wave 2（blocked by T4 Redis 可选用于 session）
  - **Blocks**: T20（前端）, T26/T27（SDK 发布）
  - **Blocked By**: T4

  **References**:
  - **Existing event**: `backend/modules/sys/src/main/java/com/example/sys/events/UserCreated.java` — 领域事件范例（消息中心可监听）
  - **Pattern**: `backend/modules/sys/src/main/java/com/example/sys/service/MenuService.java` — buildTree 递归（单位展开仿此）
  - **Dependency**: T3 unitId（单位范围查询用）, T16 外部应用鉴权（/openapi 接口）
  - **External**: Spring WebSocket 文档 `https://docs.spring.io/spring-boot/reference/web/websocket.html`
  - **WHY**: 用户明确消息中心需求；WebSocket 双向；seq+重放解决重连丢消息（Metis 边界 4）；三级差异化前端实现

  **Acceptance Criteria**:
  - [ ] notify 模块创建
  - [ ] 3 张表 + 索引
  - [ ] MessageService.publish 多范围展开
  - [ ] WebSocket 连接 + 推送 + 重放
  - [ ] /sys/notify/publish + /openapi/notify/publish 接口
  - [ ] 离线消息重连重放（24h TTL）
  - [ ] 单元 + 集成测试 ≥80%

  **QA Scenarios**:
  ```
  Scenario: WebSocket 实时推送（单用户）
    Tool: Bash (ws client + curl)
    Steps:
      1. admin 登录获 token
      2. ws 连接 ws://localhost:8090/ws/notify?token=... 发 {lastSeqReceived:0}
      3. curl POST /sys/notify/publish -d '{"title":"测试","content":"...","level":"URGENT","recipientType":"USER","recipientId":1}'
      4. ws 客户端断言 1s 内收到 {type:"message", level:"URGENT"}
    Expected Result: 实时推送，延迟 < 1s
    Evidence: .sisyphus/evidence/task-15-ws-push.txt

  Scenario: 离线重连重放（Metis 边界 4）
    Tool: Bash
    Steps:
      1. admin 离线时发布 3 条消息
      2. admin ws 连接，发 lastSeqReceived=0
      3. 断言收到 3 条消息（按 seq 顺序）
    Expected Result: 重放完整
    Evidence: .sisyphus/evidence/task-15-reconnect-replay.txt

  Scenario: 角色范围发布
    Tool: Bash
    Steps:
      1. 发布 recipientType=ROLE, recipientId=adminRoleId
      2. 查 notify_user_inbox，断言所有 admin 角色用户各 1 行
    Expected Result: 范围正确展开
    Evidence: .sisyphus/evidence/task-15-role-publish.txt
  ```

  **Commit**: YES（分 3 commit）
  - C1: `flyway(notify): V1 add message tables`
  - C2: `feat(notify): add WebSocket push with seq replay`
  - C3: `feat(notify): add multi-scope publish API`
  - Pre-commit: `mvn test -Dspring.profiles.active=test`

- [x] 16. **外部应用/开放平台后端（spring-authorization-server + OIDC + JWK 轮转）** [Wave 2]

  **What to do**:
  - 新建 `backend/modules/openapp/` 模块（pom + autoconfig + imports + MODULE.md）
  - 父 pom dependencyManagement 加 `spring-authorization-server 1.5.x`
  - Flyway `V1__openapp_init.sql`：
    - `openapp_client`（id, client_id UNIQUE, client_secret(BCrypt), client_name, redirect_uris TEXT[], post_logout_redirect_uris TEXT[], scopes TEXT[], grant_types TEXT[], enabled, created_at）
    - `oauth_authorization`（id, registered_client_id, principal_name, access_token, refresh_token, ...）— spring-authorization-server JDBC service
  - 实现 `JdbcRegisteredClientRepository`（基于 openapp_client 表，CRUD by admin）
  - `AuthorizationServerConfig`：配置 AuthorizationServerConfigurer（JWK source → **持久化到 DB 加密存储**，非 in-memory；含轮转调度，kid 标识）
  - 支持 grants：authorization_code + refresh_token + client_credentials
  - OIDC：UserInfo endpoint + RP-Initiated Logout（end_session_endpoint，回调 post_logout_redirect_uri）
  - `@RequiresAppScope("xxx")` 注解 + AppScopeAspect（外部应用 access_token 鉴权，类似 @RequiresPermission 但用 client scope）
  - `/openapi/**` 路径走 OAuth2 resource server 鉴权（非 JWT 用户 token）
  - 外部应用管理 API（@RequiresPermission("sys:openapp:*")）：CRUD client、生成 client_secret、配置 scope/redirect_uri
  - **JWK 轮转**：定时任务（如每周）生成新 key，保留旧 key grace period（如 30 天），kid 切换
  - 测试：discovery endpoint、authorization code flow、client credentials flow、RP-Initiated Logout、JWK 轮转后旧 token grace 验证

  **Must NOT do**:
  - 不用动态客户端注册（Metis 护栏：静态管理）
  - 不用 in-memory JWK（Metis：HA 定时炸弹）
  - 不做 MFA/SAML/WebAuthn（范围排除）
  - 不破坏现有 /sys/auth/** 用户登录（两套并存）

  **Recommended Agent Profile**:
  - **Category**: `ultrabrain`
    - Reason: OAuth2/OIDC 协议 + JWK 加密 + 多 grant + 最复杂模块，需高逻辑推理
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T8-T15/T17 同 Wave，依赖 T3）
  - **Parallel Group**: Wave 2（blocked by T3）
  - **Blocks**: T21（前端）, T25（OIDC 登出 backplane）, T26/T27（SDK）
  - **Blocked By**: T3（SecurityConfig）

  **References**:
  - **Dependency**: T3 platform-security（/openapi 走 resource server）
  - **External**: spring-authorization-server 文档 `https://docs.spring.io/spring-authorization-server/reference/`
  - **External**: OIDC RP-Initiated Logout `https://openid.net/specs/openid-connect-rpinitiated-1_0.html`
  - **Pattern**: `backend/modules/sys/` 模块结构
  - **WHY**: 用户明确 OAuth2+OIDC；spring-authorization-server 是 Spring 官方实现（Metis Finding 4）；JWK 必须持久化（in-memory 在 HA 下跨副本验证失败）；静态客户端管理（非动态注册）

  **Acceptance Criteria**:
  - [ ] openapp 模块创建
  - [ ] openapp_client + oauth_authorization 表
  - [ ] JdbcRegisteredClientRepository
  - [ ] AuthorizationServerConfig + 持久化 JWK
  - [ ] /.well-known/openid-configuration 返回完整发现文档
  - [ ] authorization_code flow 端到端
  - [ ] client_credentials flow
  - [ ] RP-Initiated Logout 回调
  - [ ] JWK 轮转：旧 token grace period 内仍可用
  - [ ] @RequiresAppScope 鉴权 /openapi/**
  - [ ] 外部应用 CRUD API

  **QA Scenarios**:
  ```
  Scenario: OIDC 发现端点
    Tool: Bash (curl)
    Steps:
      1. curl http://localhost:8090/.well-known/openid-configuration
      2. 断言 JSON 含 issuer/authorization_endpoint/token_endpoint/userinfo_endpoint/end_session_endpoint/jwks_uri
    Expected Result: 完整发现文档
    Evidence: .sisyphus/evidence/task-16-oidc-discovery.txt

  Scenario: Authorization Code Flow
    Tool: Bash (curl + 测试客户端)
    Steps:
      1. admin 创建外部应用 client（redirect_uri=http://localhost:9999/callback, scope=notify:publish）
      2. 浏览器/curl 访问 /oauth2/authorize?client_id=...&redirect_uri=...&response_type=code&scope=notify:publish
      3. 登录 admin 同意，获 code
      4. curl POST /oauth2/token 换 access_token
      5. curl POST /openapi/notify/publish -H "Authorization: Bearer app_token"
    Expected Result: 完整流程，外部应用能调 /openapi
    Evidence: .sisyphus/evidence/task-16-auth-code-flow.txt

  Scenario: RP-Initiated Logout 回调
    Tool: Bash
    Steps:
      1. 获取 id_token
      2. curl GET /oauth2/logout?id_token_hint=...&post_logout_redirect_uri=http://localhost:9999
      3. 断言 302 重定向到 post_logout_redirect_uri
    Expected Result: 重定向正确
    Evidence: .sisyphus/evidence/task-16-rp-logout.txt

  Scenario: JWK 轮转 grace period
    Tool: Bash
    Steps:
      1. 获取 token（旧 key 签名）
      2. 触发 JWK 轮转（新 key）
      3. 旧 token 在 grace 期内仍可调 /openapi
    Expected Result: 旧 token grace 期内可用
    Evidence: .sisyphus/evidence/task-16-jwk-rotation.txt
  ```

  **Commit**: YES（分 4 commit）
  - C1: `flyway(openapp): V1 add client and authorization tables`
  - C2: `feat(openapp): configure spring-authorization-server with persistent JWK`
  - C3: `feat(openapp): add OIDC and RP-Initiated Logout`
  - C4: `feat(openapp): add @RequiresAppScope and external app management API`

- [x] 17. **数据权限 SysUser 参考实现** [Wave 2]

  **What to do**:
  - 在 `UserService.list` / `UserController.list` 实际应用 DataScopeSpecification
  - 查询时从 CurrentUser 获取当前用户角色 dataScope + unitId + customUnitIds，构建 Specification 注入查询
  - admin 角色（dataScope=ALL）不受限
  - 测试 5 种 scope（ALL/UNIT/UNIT_BELOW/SELF/CUSTOM）：预置 3 级单位树 + 多用户，断言各 scope 返回正确行数
  - 自定义 scope（CUSTOM）的 sys_role_data_scope 配置 UI（角色管理页加单位选择树，T24 整合）
  - bulk 操作保护：override ScopedRepository.deleteAll 禁用或按 scope 过滤（Metis 边界 6）

  **Must NOT do**:
  - 不对其他实体（SysRole/SysMenu 等）应用 dataScope（v1 仅 SysUser 参考）
  - 不改 SysRole.dataScope 字段类型

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: 5 种 scope 逻辑 + 递归 CTE 集成 + bulk 保护
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T8-T16 同 Wave，依赖 T5）
  - **Parallel Group**: Wave 2（blocked by T5）
  - **Blocks**: T24（前端配置）
  - **Blocked By**: T5（DataScope 框架）

  **References**:
  - **Dependency**: T5 DataScopeSpecification/ScopedRepository
  - **Target**: `backend/modules/sys/src/main/java/com/example/sys/service/UserService.java` — list 方法加 scope
  - **WHY**: dataScope 字段在但未实现；SysUser 是参考实现（Metis Q3 default）

  **Acceptance Criteria**:
  - [ ] UserService.list 应用 DataScopeSpecification
  - [ ] ALL → 返回全部
  - [ ] SELF → 仅自己
  - [ ] UNIT → 同单位
  - [ ] UNIT_BELOW → 本单位+子孙单位
  - [ ] CUSTOM → sys_role_data_scope 配置的单位
  - [ ] bulk 操作保护
  - [ ] 单元测试 5 scope 全覆盖

  **QA Scenarios**:
  ```
  Scenario: 5 种 dataScope 过滤
    Tool: Bash (curl)
    Preconditions: 种子 3 级单位（总部→分公司A→部门1）+ 每单位 2 用户 + 各 scope 的测试账号
    Steps:
      1. 用 scope=SELF 的账号 GET /sys/user → 断言行数=1（自己）
      2. scope=UNIT（分公司A）→ 断言 2 行（同单位）
      3. scope=UNIT_BELOW（总部）→ 断言 6 行（总部+分公司A+部门1 各 2）
      4. scope=CUSTOM（配置仅分公司A）→ 断言 2 行
      5. scope=ALL（admin）→ 断言全部
    Expected Result: 各 scope 行数匹配预期
    Evidence: .sisyphus/evidence/task-17-datascope-5-levels.txt

  Scenario: bulk 操作保护（Metis 边界 6）
    Tool: Bash
    Steps:
      1. 用 scope=UNIT 的账号尝试 DELETE /sys/user/batch [其他单位用户ID]
    Expected Result: 403 或 0 行删除（跨单位批量操作被拦截）
    Evidence: .sisyphus/evidence/task-17-bulk-protection.txt
  ```

  **Commit**: YES
  - Message: `feat(sys): apply DataScope to SysUser queries with 5 scope levels`

- [x] 18. **响应式 Layout 改造（断点驱动 sider 抽屉 + NGrid 响应式）** [Wave 3]

  **What to do**:
  - 改造 `frontend/src/shared/components/Layout.vue`：
    - desktop（≥1024）：现有 NLayoutSider 固定 240px/折叠 64px（保持）
    - tablet（768-1024）：sider 可折叠，默认折叠
    - mobile（<768）：sider 改 NDrawer 抽屉模式，汉堡按钮触发
  - 使用 T6 的 useBreakpoint composable 驱动切换
  - Dashboard 的 `NGrid :cols="4"` 改为响应式 `:cols="{ xs:1, s:2, m:4 }"`
  - NDataTable 在窄屏开启横向滚动（scroll-x）或卡片切换（可选）
  - 全局审计：所有现有页面（user/role/menu/unit/config）在 375px/768px/1280px 三断点截图验证无破版
  - 顶栏在 mobile 隐藏部分次要信息

  **Must NOT do**:
  - 不重写设计系统（Metis 范围排除）
  - 不引入新 UI 库
  - 不破坏桌面端现有体验

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: 响应式 UI 改造
  - **Skills**: [`frontend-design`]

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T19-T25 同 Wave，依赖 T6）
  - **Parallel Group**: Wave 3（blocked by T6）
  - **Blocks**: None
  - **Blocked By**: T6（useBreakpoint）

  **References**:
  - **Target**: `frontend/src/shared/components/Layout.vue` — 唯一布局壳
  - **Target**: `frontend/src/modules/sys/views/Dashboard.vue` — NGrid 固定 4 列
  - **Dependency**: T6 useBreakpoint
  - **External**: Naive UI NGrid responsive `https://www.naiveui.com/zh-CN/os-theme/components/grid` — cols 响应式对象
  - **WHY**: 用户硬约束"手机/pad/电脑都支持"；当前零响应式

  **Acceptance Criteria**:
  - [ ] Layout 三断点行为正确（sider 固定/折叠/抽屉）
  - [ ] Dashboard NGrid 响应式
  - [ ] 表格窄屏横向滚动
  - [ ] 所有现有页面三断点截图无破版

  **QA Scenarios**:
  ```
  Scenario: 三断点响应式截图
    Tool: Playwright
    Preconditions: 登录 admin
    Steps:
      1. 对每个页面（dashboard/user/role/menu/unit/config）分别截 375px/768px/1280px 宽度截图
      2. mobile（375）：断言 sider 为抽屉，汉堡按钮可见
      3. tablet（768）：断言 sider 折叠（64px）
      4. desktop（1280）：断言 sider 展开（240px）
    Expected Result: 三断点布局正确，无破版
    Evidence: .sisyphus/evidence/task-18-responsive-{page}-{breakpoint}.png（共 ~18 张）
  ```

  **Commit**: YES
  - Message: `feat(frontend): responsive Layout with breakpoint-driven sider drawer`

- [x] 19. **登录页重构（动态登录方式 tabs + 验证码 + LDAP 入口）** [Wave 3]

  **What to do**:
  - 重构 `frontend/src/modules/sys/views/Login.vue`：
    - onMounted 调 `GET /sys/auth/login-methods` 获取已启用方式
    - 渲染 NTabs（每个 method 一个 tab，按 order 排序，含图标/label）
    - 账号密码 tab：username/password + 验证码输入（图片从 `/sys/auth/captcha` 获取，点击刷新）
    - LDAP tab：username/password（LDAP 认证，复用同一表单但 method="ldap"）
    - tab 切换时切换 LoginRequest.method
  - 新建 `frontend/src/modules/sys/components/login-methods/PasswordTab.vue`、`LdapTab.vue`（按方式组织）
  - 验证码组件：显示 base64 图片 + 输入框 + 刷新按钮
  - 登录失败：显示错误（账号锁定提示联系管理员、验证码错误、密码错误）
  - 响应式：窄屏 tab 横向滚动，表单单列

  **Must NOT do**:
  - 不破坏现有 LoginVO 处理（authStore.login 不变）
  - 不硬编码登录方式（动态获取）
  - 不引入新依赖

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: 登录页 UI 重构 + 动态渲染
  - **Skills**: [`frontend-design`]

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T20-T25 同 Wave，依赖 T6/T9/T12）
  - **Parallel Group**: Wave 3（blocked by T6, T9, T12）
  - **Blocks**: None
  - **Blocked By**: T6（基础设施）, T9（验证码 API）, T12（login-methods API）

  **References**:
  - **Target**: `frontend/src/modules/sys/views/Login.vue` — 现有硬编码账密表单
  - **Dependency**: T9 /sys/auth/captcha, T12 /sys/auth/login-methods
  - **Pattern**: `frontend/src/stores/auth.ts` — login() 方法不变
  - **External**: Naive UI NTabs/NCaptcha（如可用）
  - **WHY**: 用户明确"前端动态获取登录方式 + 微信按钮"；验证码集成；LDAP 入口

  **Acceptance Criteria**:
  - [ ] Login.vue 动态渲染登录方式 tabs
  - [ ] 验证码图片显示 + 刷新
  - [ ] 账号密码登录工作（含验证码）
  - [ ] LDAP tab（若启用）工作
  - [ ] 锁定/错误提示正确
  - [ ] 响应式窄屏正常

  **QA Scenarios**:
  ```
  Scenario: 动态登录方式渲染
    Tool: Playwright
    Preconditions: LDAP enabled=true（dev profile）
    Steps:
      1. 导航 /login
      2. 断言 NTabs 含"账号密码"和"LDAP"两个 tab
      3. 点击 LDAP tab，表单切换
    Expected Result: 动态渲染正确
    Evidence: .sisyphus/evidence/task-19-login-tabs.png

  Scenario: 验证码登录流程
    Tool: Playwright
    Steps:
      1. 导航 /login，断言验证码图片可见
      2. 填 admin/admin123 + 正确验证码 → 登录成功
      3. 填错误验证码 → 提示"验证码错误"
    Expected Result: 验证码校验生效
    Evidence: .sisyphus/evidence/task-19-captcha-login.png

  Scenario: 锁定提示
    Tool: Playwright
    Steps:
      1. 错误密码登录 3 次
      2. 第 4 次提示含"锁定"/"联系管理员"
    Expected Result: 锁定提示正确
    Evidence: .sisyphus/evidence/task-19-lockout-hint.png
  ```

  **Commit**: YES
  - Message: `feat(frontend): rebuild Login page with dynamic methods, captcha, LDAP`

- [x] 20. **消息中心前端（通知列表 + 三级弹窗 + WebSocket composable + 未读徽标）** [Wave 3]

  **What to do**:
  - 实现 `frontend/src/shared/composables/useWebSocket.ts`（T6 骨架）：connect(url, token)、自动重连（指数退避）、message 事件分发、lastSeqReceived 管理
  - 新建 `frontend/src/modules/sys/views/message/index.vue`：
    - 通知列表（NDataTable/List）：标题/级别 tag/发送时间/已读状态，按时间倒序
    - 筛选：级别（紧急/重要/一般）+ 已读/未读
    - 点击标记已读、批量已读
  - 三级差异化弹窗（App.vue 全局监听 WebSocket message）：
    - URGENT → NModal 全屏遮罩，必须手动确认关闭
    - IMPORTANT → useMessage Toast 右上角，自动消失
    - NORMAL → 仅更新未读徽标，不主动弹
  - 顶栏铃铛图标 + NBadge 未读数，点击下拉最近 5 条
  - 响应式：窄屏列表单列、Modal 全屏

  **Must NOT do**:
  - 不破坏 App.vue 现有 Provider 链
  - 不轮询（用 WebSocket）

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: 复杂前端交互 + WebSocket 集成
  - **Skills**: [`frontend-design`]

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T18/T19/T21-T25 同 Wave，依赖 T6/T15）
  - **Parallel Group**: Wave 3（blocked by T6, T15）
  - **Blocks**: None
  - **Blocked By**: T6（NNotificationProvider/useWebSocket 骨架）, T15（消息中心 API）

  **References**:
  - **Dependency**: T6 useWebSocket 骨架, T15 后端 WebSocket + API
  - **Target**: `frontend/src/App.vue` — 加全局 message 监听
  - **Pattern**: `frontend/src/shared/components/Layout.vue` — 顶栏加铃铛
  - **External**: Naive UI NModal/useMessage/NBadge
  - **WHY**: 用户明确三级差异化弹窗 + 登录后通知 + 实时弹出

  **Acceptance Criteria**:
  - [ ] useWebSocket 实现含自动重连
  - [ ] 消息列表页 + 筛选 + 标记已读
  - [ ] URGENT → NModal 弹窗
  - [ ] IMPORTANT → Toast
  - [ ] NORMAL → 仅徽标
  - [ ] 顶栏铃铛 + 未读数
  - [ ] 响应式

  **QA Scenarios**:
  ```
  Scenario: 紧急消息 Modal 弹窗
    Tool: Playwright + Bash
    Preconditions: admin 登录，前端 WebSocket 已连接
    Steps:
      1. curl 发布 URGENT 消息给 admin
      2. 前端断言 1s 内出现 NModal（.n-modal）含消息标题
      3. 点击关闭按钮，Modal 消失
    Expected Result: URGENT 弹 Modal
    Evidence: .sisyphus/evidence/task-20-urgent-modal.png

  Scenario: 重要消息 Toast
    Tool: Playwright + Bash
    Steps:
      1. 发布 IMPORTANT 消息
      2. 断言右上角出现 NMessage（.n-message）
    Expected Result: Toast 显示
    Evidence: .sisyphus/evidence/task-20-important-toast.png

  Scenario: WebSocket 重连
    Tool: Playwright
    Steps:
      1. 后端重启（断开 WS）
      2. 前端断言自动重连（指数退避）
      3. 重连后发布消息，仍能收到
    Expected Result: 重连后消息不丢
    Evidence: .sisyphus/evidence/task-20-ws-reconnect.txt
  ```

  **Commit**: YES
  - Message: `feat(frontend): message center with WebSocket, three-tier popups, unread badge`

- [x] 21. **外部应用管理前端（CRUD + 授权配置 + Scope 管理）** [Wave 3]

  **What to do**:
  - 新建 `frontend/src/modules/sys/views/app/index.vue`：
    - 外部应用列表（NDataTable）：client_id/client_name/启用状态/创建时间
    - 新增/编辑应用（NModal 表单）：client_name、redirect_uris（多输入动态添加）、post_logout_redirect_uris、scopes（多选，如 notify:publish/sys:user:read）、grant_types
    - 生成 client_secret（仅创建时显示一次，复制按钮）
    - 启用/禁用开关
    - 查看 client 的 access_token 调试（可选，开发模式）
  - 路由注册：`sys/app` + 后端菜单种子插入
  - 响应式：窄屏表单单列
  - 复用 user/index.vue 的 NDataTable + NModal 范式

  **Must NOT do**:
  - 不显示 client_secret 明文（仅创建时一次性）
  - 不在前端做 OAuth2 流程（仅管理）

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: 标准 CRUD 管理页
  - **Skills**: [`frontend-design`]

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T18-T20/T22-T25 同 Wave，依赖 T6/T16）
  - **Parallel Group**: Wave 3（blocked by T6, T16）
  - **Blocks**: None
  - **Blocked By**: T6（基础设施）, T16（外部应用 API）

  **References**:
  - **Pattern**: `frontend/src/modules/sys/views/user/index.vue` — CRUD 范式标杆
  - **Dependency**: T16 外部应用 API
  - **WHY**: 用户明确"外部应用管理，单独界面"

  **Acceptance Criteria**:
  - [ ] 外部应用列表 + CRUD
  - [ ] redirect_uris/scopes 动态多输入
  - [ ] client_secret 一次性显示
  - [ ] 启用/禁用
  - [ ] 响应式

  **QA Scenarios**:
  ```
  Scenario: 创建外部应用
    Tool: Playwright
    Steps:
      1. 导航 /sys/app，点"新增"
      2. 填 client_name="测试应用"，redirect_uris=["http://localhost:9999/callback"]，scopes=["notify:publish"]
      3. 保存，断言 client_secret 弹窗显示一次
      4. 列表出现新应用
    Expected Result: CRUD 完整，secret 一次性
    Evidence: .sisyphus/evidence/task-21-create-app.png
  ```

  **Commit**: YES
  - Message: `feat(frontend): external app management page with OAuth2 client CRUD`

- [x] 22. **审计日志查询前端** [Wave 3]

  **What to do**:
  - 新建 `frontend/src/modules/sys/views/audit/index.vue`：
    - 筛选栏：actor（用户名）/ action（下拉多选：LOGIN/UNLOCK/PUBLISH 等）/ date-range / target_type
    - NDataTable：actor/action/target/ip/time/result（成功/失败 tag）/详情（点击展开 params JSON）
    - 分页
  - 路由 `sys/audit` + 后端菜单种子
  - 响应式：窄屏筛选栏折叠

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
  - **Skills**: [`frontend-design`]

  **Parallelization**: Wave 3（blocked by T6, T14）. Blocks: None. Blocked By: T6, T14.

  **References**:
  - **Pattern**: `frontend/src/modules/sys/views/user/index.vue` — 筛选+表格范式
  - **Dependency**: T14 审计日志 API

  **Acceptance Criteria**:
  - [ ] 筛选栏多条件
  - [ ] 表格 + 分页 + 详情展开
  - [ ] result 成功/失败 tag 区分
  - [ ] 响应式

  **QA Scenarios**:
  ```
  Scenario: 审计日志查询
    Tool: Playwright
    Preconditions: 有登录审计数据
    Steps:
      1. 导航 /sys/audit
      2. action 筛选选 LOGIN，查询
      3. 断言表格含 LOGIN 行
      4. 点击详情展开 params JSON
    Expected Result: 筛选+详情正常
    Evidence: .sisyphus/evidence/task-22-audit-query.png
  ```

  **Commit**: YES - `feat(frontend): audit log query page with filters`

- [x] 23. **在线会话管理前端** [Wave 3]

  **What to do**:
  - 新建 `frontend/src/modules/sys/views/session/index.vue`（或嵌入用户管理）：
    - 用户选择（搜索）→ 显示该用户活跃会话列表（IP/设备/登录时间/UA）
    - 每行"强制下线"按钮（confirm 后调 revoke）
    - 当前用户可在个人中心查看自己会话
  - 路由 `sys/session` + 菜单种子
  - 响应式

  **Recommended Agent Profile**: `visual-engineering` + [`frontend-design`]

  **Parallelization**: Wave 3（blocked by T6, T11）. Blocks: None. Blocked By: T6, T11.

  **References**:
  - **Dependency**: T11 会话管理 API
  - **Pattern**: user/index.vue 表格范式

  **Acceptance Criteria**:
  - [ ] 用户会话列表
  - [ ] 强制下线按钮 + confirm
  - [ ] 设备类型显示
  - [ ] 响应式

  **QA Scenarios**:
  ```
  Scenario: 强制下线
    Tool: Playwright + Bash
    Steps:
      1. testuser 登录（保持会话）
      2. admin 导航 /sys/session，选 testuser
      3. 点"强制下线"，confirm
      4. testuser 端刷新页面 → 跳转登录
    Expected Result: 强制下线生效
    Evidence: .sisyphus/evidence/task-23-force-kickout.png
  ```

  **Commit**: YES - `feat(frontend): session management page with force-kickout`

- [x] 24. **用户管理增强（锁定状态 + 解锁按钮 + 数据权限配置入口）** [Wave 3]

  **What to do**:
  - 增强 `frontend/src/modules/sys/views/user/index.vue`：
    - 列加"状态"列（正常/锁定 tag）
    - 锁定用户行显示"解锁"按钮（@click 调 /sys/user/{id}/unlock）
    - 角色管理页（role/index.vue）加 dataScope 配置：
      - 选择 dataScope（下拉：ALL/UNIT/UNIT_BELOW/SELF/CUSTOM）
      - CUSTOM 时显示单位选择树（NTree 多选），保存到 sys_role_data_scope
  - 响应式

  **Recommended Agent Profile**: `visual-engineering` + [`frontend-design`]

  **Parallelization**: Wave 3（blocked by T6, T8, T17）. Blocks: None. Blocked By: T6, T8（解锁 API）, T17（数据权限 API）.

  **References**:
  - **Target**: `frontend/src/modules/sys/views/user/index.vue`、`role/index.vue`
  - **Dependency**: T8 unlock API, T17 dataScope 配置

  **Acceptance Criteria**:
  - [ ] 用户列表显示锁定状态
  - [ ] 解锁按钮工作
  - [ ] 角色 dataScope 配置 UI
  - [ ] CUSTOM 时单位树多选
  - [ ] 响应式

  **QA Scenarios**:
  ```
  Scenario: 解锁用户
    Tool: Playwright + Bash
    Steps:
      1. 锁定 testuser（curl 错误登录 3 次）
      2. admin 导航 /sys/user，断言 testuser 行显示锁定 tag + 解锁按钮
      3. 点击解锁，按钮消失，状态变正常
      4. testuser 可正常登录
    Expected Result: 解锁工作
    Evidence: .sisyphus/evidence/task-24-unlock.png

  Scenario: 角色 dataScope CUSTOM 配置
    Tool: Playwright
    Steps:
      1. 导航 /sys/role，编辑某角色
      2. dataScope 选 CUSTOM，单位树勾选"分公司A"
      3. 保存
      4. 该角色用户 GET /sys/user 断言仅见分公司A 用户
    Expected Result: CUSTOM 配置生效
    Evidence: .sisyphus/evidence/task-24-datascope-custom.png
  ```

  **Commit**: YES - `feat(frontend): enhance user/role pages with lock status, unlock, dataScope config`

- [x] 25. **OIDC 单点登出 + Spring Session Redis backplane + 登出回调** [Wave 3]

  **What to do**:
  - 引入 `spring-session-data-redis`：配置 `@EnableRedisHttpSession`，让 OAuth2 授权服务器的 session 存 Redis（多副本共享）
  - 配置 spring-authorization-server 的 `OidcLogoutAuthenticationProvider`（已原生支持 RP-Initiated Logout）
  - end_session_endpoint：接收 id_token_hint + post_logout_redirect_uri + state，验证后销毁服务器端 session，302 重定向到 post_logout_redirect_uri
  - 用户登出事件通知外部应用：自定义 `LogoutEvent` 监听 → 回调所有该用户授权过的外部应用的 logout webhook（若配置）
  - 测试：OIDC 登出重定向、session 销毁、webhook 回调

  **Must NOT do**:
  - 不在未配 Spring Session 的情况下声称支持 HA OIDC Logout（Metis 护栏）
  - 不破坏现有 /sys/auth/logout（用户 token 登出）

  **Recommended Agent Profile**: `deep`

  **Parallelization**: Wave 3（blocked by T16）. Blocks: T26, T27. Blocked By: T16.

  **References**:
  - **Dependency**: T16 spring-authorization-server, T4 Redis
  - **External**: Spring Session Redis `https://docs.spring.io/spring-session/reference/`
  - **External**: OIDC RP-Initiated Logout spec

  **Acceptance Criteria**:
  - [ ] Spring Session Redis 配置
  - [ ] end_session_endpoint 工作
  - [ ] session 销毁验证
  - [ ] post_logout_redirect_uri 重定向 + state 保留
  - [ ] 登出 webhook 回调外部应用

  **QA Scenarios**:
  ```
  Scenario: OIDC 单点登出
    Tool: Bash (curl)
    Steps:
      1. 完成 authorization code flow，获 id_token
      2. curl GET /oauth2/logout?id_token_hint=...&post_logout_redirect_uri=http://localhost:9999&state=xyz
      3. 断言 302 Location 含 state=xyz
      4. 同一 id_token 再次使用 → 失败（session 销毁）
    Expected Result: 登出+重定向正确
    Evidence: .sisyphus/evidence/task-25-oidc-logout.txt

  Scenario: 登出 webhook 回调
    Tool: Bash
    Preconditions: 外部应用配置 logout_webhook_url
    Steps:
      1. 用户登出
      2. 断言外部应用 webhook 端点收到 POST {event:"logout", userId:...}
    Expected Result: webhook 触发
    Evidence: .sisyphus/evidence/task-25-logout-webhook.txt
  ```

  **Commit**: YES - `feat(openapp): OIDC RP-Initiated Logout with Redis session backplane`

- [x] 26. **Java SDK + Python SDK（OAuth2 客户端 + 消息发布 + token 刷新 + Nexus 发布）** [Wave 4]

  **What to do**:
  - **Java SDK** `backend/client-sdk-java/`（独立 Maven 模块）：
    - `PlatformClient`：配置 clientId/clientSecret/issuerUrl
    - OAuth2：authorizationUrl() 生成授权 URL、exchangeCode(code) 换 token、refreshToken()、clientCredentials()（机器对机器）
    - 消息发布：publishMessage(accessToken, PublishRequest)
    - token 自动刷新：拦截 401 → refresh → 重试（Metis 边界 10）
    - JUnit5 测试（mock server）+ 示例 Main.java
    - Nexus 发布配置（distributionManagement 指向 192.168.1.2:8081）
  - **Python SDK** `client-sdk-python/`：
    - `platform_client/__init__.py`：PlatformClient 类（同 Java 功能）
    - requests 库 + OAuth2 流程 + token 刷新
    - pytest 测试 + 示例 example.py
    - setup.py + Nexus PyPI 仓库发布配置
  - CI（.gitea/workflows/sdk.yml）：mvn deploy + twine upload

  **Must NOT do**:
  - 不硬编码 client_secret（用户传入）
  - 不阻塞式轮询 token（用刷新机制）
  - 不绑定特定框架（SDK 应轻量无依赖或最小依赖）

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: 多语言 SDK + OAuth2 流程，中等复杂度
  - **Skills**: []

  **Parallelization**: Wave 4（blocked by T16, T25）. Blocks: T28, T30. Blocked By: T16, T25.

  **References**:
  - **Dependency**: T16 OAuth2 端点, T15 消息发布 API, T25 OIDC 登出
  - **External**: OAuth2 客户端流程规范
  - **WHY**: 用户明确 SDK 需求；Java 覆盖 JVM 生态，Python 覆盖脚本/ML；token 刷新是关键（边界 10）

  **Acceptance Criteria**:
  - [ ] Java SDK：OAuth2 flow + publish + refresh + 示例 + 测试
  - [ ] Python SDK：同上
  - [ ] 两者能对运行中的后端完成完整 demo（登录→发布消息）
  - [ ] Nexus 发布 CI 配置

  **QA Scenarios**:
  ```
  Scenario: Java SDK 完整 demo
    Tool: Bash
    Preconditions: 后端运行，已创建测试外部应用
    Steps:
      1. cd backend/client-sdk-java && mvn test（mock server）
      2. 运行示例：java -jar demo.jar（真实后端），完成 client_credentials → publish URGENT 消息
      3. admin 端断言收到消息
    Expected Result: SDK 完整流程工作
    Evidence: .sisyphus/evidence/task-26-java-sdk-demo.txt

  Scenario: Python SDK 完整 demo
    Tool: Bash
    Steps:
      1. cd client-sdk-python && pytest
      2. python example.py（真实后端），完成 flow + publish
    Expected Result: demo 成功
    Evidence: .sisyphus/evidence/task-26-python-sdk-demo.txt

  Scenario: token 过期自动刷新（边界 10）
    Tool: Bash
    Steps:
      1. SDK 持有即将过期的 access_token + refresh_token
      2. 调用 publish 触发 401
      3. 断言 SDK 自动 refresh + 重试成功
    Expected Result: 无感刷新
    Evidence: .sisyphus/evidence/task-26-token-refresh.txt
  ```

  **Commit**: YES（分 2 commit）
  - C1: `feat(sdk): Java SDK with OAuth2 and message publish`
  - C2: `feat(sdk): Python SDK with OAuth2 and message publish`

- [x] 27. **Go SDK + C SDK** [Wave 4]

  **What to do**:
  - **Go SDK** `client-sdk-go/`：
    - `platformclient/` package：Client struct（clientId/clientSecret/issuerUrl）
    - OAuth2 流程（net/http + golang.org/x/oauth2）+ publish + refresh
    - go test + example/main.go
    - go.mod + Nexus Go 仓库发布（或 Git module + tag）
  - **C SDK** `client-sdk-c/`：
    - `platform_client.h` + `platform_client.c`：HTTP 客户端（libcurl）+ OAuth2 flow + publish
    - 依赖：libcurl + jansson（JSON）或单文件 JSON 解析
    - Makefile + 示例 example.c
    - GitHub Release 静态库 + 头文件（C 无标准包管理器）
  - CI：Go module tag + C 静态库构建 release

  **Must NOT do**:
  - C SDK 不引入重型依赖（libcurl 是合理最小）
  - Go SDK 不用反射（保持简洁）
  - 两者不破坏与 Java/Python SDK 的 API 一致性

  **Recommended Agent Profile**: `unspecified-high`

  **Parallelization**: Wave 4（blocked by T16, T25）. Blocks: T28, T30. Blocked By: T16, T25.

  **References**: 同 T26 + libcurl 文档 + Go oauth2 包

  **Acceptance Criteria**:
  - [ ] Go SDK：完整 flow + 测试 + 示例
  - [ ] C SDK：完整 flow + Makefile + 示例 + 静态库
  - [ ] 两者 demo 对真实后端工作
  - [ ] API 与 Java/Python 一致

  **QA Scenarios**:
  ```
  Scenario: Go SDK demo
    Tool: Bash
    Steps:
      1. cd client-sdk-go && go test ./...
      2. go run example/main.go（真实后端）
    Expected Result: demo 成功
    Evidence: .sisyphus/evidence/task-27-go-sdk-demo.txt

  Scenario: C SDK demo
    Tool: Bash
    Preconditions: libcurl + jansson 已装
    Steps:
      1. cd client-sdk-c && make
      2. ./example（真实后端）
    Expected Result: demo 成功
    Evidence: .sisyphus/evidence/task-27-c-sdk-demo.txt
  ```

  **Commit**: YES（分 2 commit）
  - C1: `feat(sdk): Go SDK with OAuth2 and message publish`
  - C2: `feat(sdk): C SDK with libcurl OAuth2 and message publish`

- [x] 28. **集成文档（4 语言快速开始 + API 参考 + 示例）** [Wave 4]

  **What to do**:
  - 创建 `docs/integration/` 目录：
    - `README.md`：集成总览（认证体系/消息中心/SDK 选择指南）
    - `quickstart-java.md` / `quickstart-python.md` / `quickstart-go.md` / `quickstart-c.md`：5 分钟快速开始（创建外部应用→配置 SDK→登录→发布消息，含可复制代码）
    - `api-reference.md`：所有 /openapi/** 端点（路径/方法/请求/响应/错误码），与 Swagger 一致
    - `oauth2-flow.md`：OAuth2/OIDC 流程详解（authorization code / client credentials / RP-Initiated Logout 时序图）
    - `examples/`：4 语言完整示例项目（可 clone 运行）
  - 在 `docs/api-contracts/` 补充 notify/openapp/audit 的 API 契约
  - 在 `docs/design/` 补充模块设计文档

  **Must NOT do**:
  - 不写空泛文档（每篇含可运行代码）
  - 不与 Swagger 重复（文档重集成视角，Swagger 重参考）

  **Recommended Agent Profile**: `writing`

  **Parallelization**: Wave 4（blocked by T26, T27）. Blocks: None. Blocked By: T26, T27.

  **References**:
  - **Existing**: `docs/README.md` — 文档分区约定（requirements/design/api-contracts）
  - **Dependency**: T26/T27 SDK 实现

  **Acceptance Criteria**:
  - [ ] 4 语言 quickstart 含可运行代码
  - [ ] API 参考完整
  - [ ] OAuth2 流程时序图
  - [ ] 4 语言示例项目可运行

  **QA Scenarios**:
  ```
  Scenario: 文档可运行性
    Tool: Bash
    Steps:
      1. 按 quickstart-java.md 步骤，新开发者能在 10 分钟内跑通 demo
    Expected Result: 文档步骤无缺失
    Evidence: .sisyphus/evidence/task-28-doc-runnable.txt
  ```

  **Commit**: YES - `docs(integration): add SDK quickstart, API reference, OAuth2 flow guides`

- [x] 29. **ArchUnit 边界测试 + E2E 全链路集成测试** [Wave 4]

  **What to do**:
  - **ArchUnit 测试套件**（backend/app/src/test）：
    - 模块边界：platform-common 不依赖任何业务模块；platform-security 不依赖 sys；业务模块间不互相依赖（经事件/API 门面）
    - ScopedRepository 强制：所有 Repository extends ScopedRepository
    - 命名规范：Controller/Service/Repository 后缀
    - 依赖方向：controller→service→repository（不反向）
  - **E2E 全链路**（e2e/ 目录）：
    - 扩展 api-e2e.sh：登录→验证码→锁定→解锁→会话→数据权限→消息发布→审计查询 全链路 curl 脚本
    - 扩展 Playwright：登录页动态方式→LDAP 登录→消息弹窗→外部应用管理→三断点响应式截图
    - 新增 OAuth2 E2E：authorization code flow 端到端（含 RP-Initiated Logout）
  - 集成 Testcontainers：PG + Redis + OpenLDAP 容器化测试环境

  **Must NOT do**:
  - 不降低覆盖率门禁（保持 80%）
  - 不跳过 E2E（CI 必须运行）

  **Recommended Agent Profile**: `deep`

  **Parallelization**: Wave 4（blocked by all impl）. Blocks: F1-F4. Blocked By: T1-T27.

  **References**:
  - **Existing**: `backend/app/src/test/java/com/example/app/ModulithVerificationTest.java`
  - **Existing**: `e2e/run-e2e.sh` + `e2e/api-e2e.sh` + `e2e/tests/login.spec.ts`
  - **External**: ArchUnit `https://www.archunit.org/`

  **Acceptance Criteria**:
  - [ ] ArchUnit 套件：模块边界 + ScopedRepository + 命名 + 依赖方向
  - [ ] E2E 全链路 curl 脚本通过
  - [ ] Playwright 扩展覆盖新功能
  - [ ] OAuth2 E2E 通过
  - [ ] CI 集成

  **QA Scenarios**:
  ```
  Scenario: ArchUnit 全绿
    Tool: Bash
    Steps:
      1. mvn test -Dtest='ArchUnit*,ModulithVerificationTest' -Dspring.profiles.active=test
    Expected Result: 全绿
    Evidence: .sisyphus/evidence/task-29-archunit.txt

  Scenario: E2E 全链路
    Tool: Bash
    Steps:
      1. cd e2e && ./run-e2e.sh
      2. Layer1（curl）+ Layer2（Playwright）全过
    Expected Result: 全绿
    Evidence: .sisyphus/evidence/task-29-e2e-full.txt
  ```

  **Commit**: YES - `test(e2e): add ArchUnit boundary tests and full-chain E2E`

- [x] 30. **部署 Runbook + Nexus SDK 发布 CI** [Wave 4]

  **What to do**:
  - **部署 Runbook** `docs/deployment.md`：
    - 环境变量清单（DB/Redis/JWT/LDAP/Nexus 凭证，含用户提供值）
    - JWK 轮转运维（如何手动触发、密钥备份）
    - Flyway 多实例启动注意事项（flyway.lock）
    - Redis 连接排查
    - OAuth2 客户端密钥轮转流程
    - 消息中心 WebSocket 负载均衡（sticky session 或共享 session store）
    - 审计日志分区维护与归档
  - **SDK 发布 CI** `.gitea/workflows/sdk-release.yml`：
    - 触发：tag 推送（sdk-java-v*、sdk-python-v* 等）
    - Java：mvn deploy 到 Nexus Maven 仓库（凭证 NEXUS_USER/NEXUS_PASS）
    - Python：twine upload 到 Nexus PyPI 仓库
    - Go：git tag + module proxy
    - C：GitHub Release 上传静态库 + 头文件
  - **清理**：删除版本库中的 `docker/app.jar`（探索发现的反面案例）

  **Must NOT do**:
  - 不在 Runbook 写明文密码
  - 不破坏现有 deploy.yml

  **Recommended Agent Profile**: `writing`

  **Parallelization**: Wave 4（blocked by T26, T27）. Blocks: None. Blocked By: T26, T27.

  **References**:
    - **Existing**: `.gitea/workflows/deploy.yml`、`docker/.env.example`、`docker/docker-compose.yml`
    - **User infra**: Nexus 192.168.1.2:8081 密码 Nexus@2025

  **Acceptance Criteria**:
    - [ ] Runbook 含完整运维章节
    - [ ] SDK 发布 CI 4 语言全覆盖
    - [ ] docker/app.jar 已从版本库删除（.gitignore 加 app.jar）
    - [ ] 凭证从 env 注入，无明文

  **QA Scenarios**:
  ```
  Scenario: SDK 发布 CI
    Tool: Bash
    Steps:
      1. git tag sdk-java-v1.0.0 && git push --tags
      2. 触发 sdk-release.yml
      3. 验证 Nexus 192.168.1.2:8081 出现 platform-client-java 1.0.0
    Expected Result: 发布成功
    Evidence: .sisyphus/evidence/task-30-sdk-release.txt

  Scenario: docker/app.jar 清理
    Tool: Bash
    Steps:
      1. git ls-files docker/app.jar → 应无输出
      2. grep app.jar docker/.gitignore → 命中
    Expected Result: 已清理
    Evidence: .sisyphus/evidence/task-30-jar-cleanup.txt
  ```

  **Commit**: YES - `docs(deploy): add runbook, SDK release CI, remove committed jar`

- [x] 31. **前后端合并：REST 控制器加 `/api` 前缀 + SPA fallback + 安全白名单** [Wave 5 - 部署合并]

  **背景**：用户要求将前后端打包到同一 Docker 镜像，Spring Boot 直接服务前端静态资源（`classpath:/static/index.html`）。现状：后端 REST 路径无 `/api` 前缀（`/sys/**`、`/openapi/**`），前端 `baseURL='/api'`，由 nginx 反代时剥离 `/api`。**关键冲突**：`UserController`/`RoleController`/`ConfigController` 存在 bare `@GetMapping`，与前端 Vue Router 路由 `/sys/user`、`/sys/role`、`/sys/config` 直接冲突 —— 浏览器访问页面会被 controller 返回 JSON 而非 index.html。**必须**给所有 REST 控制器加 `/api` 前缀以彻底分离 API 命名空间与 SPA 页面命名空间。

  **What to do**:
  - **后端控制器加 `/api` 前缀**（仅 `@RestController`，不动 OAuth2 AS 默认端点 `/oauth2/**`、不动 WebSocket `/ws/**`、不动 actuator/swagger）：
    - `AuthController`: `@RequestMapping("/sys/auth")` → `@RequestMapping("/api/sys/auth")`
    - `UserController`: `/sys/user` → `/api/sys/user`
    - `RoleController`: `/sys/role` → `/api/sys/role`
    - `MenuController`: `/sys/menu` → `/api/sys/menu`
    - `UnitController`: `/sys/unit` → `/api/sys/unit`
    - `ConfigController`: `/sys/config` → `/api/sys/config`
    - `SessionController`: 方法级路径 `/sys/auth/sessions`、`/sys/user/{id}/sessions` → 加 `/api` 前缀
    - `AuditLogController`: `/sys/audit` → `/api/sys/audit`
    - `InternalNotifyController`: `/sys/notify` → `/api/sys/notify`
    - `OpenAppClientController`: `/sys/openapp/clients` → `/api/sys/openapp/clients`
    - **`ExternalNotifyController` 保持 `/openapi/notify` 不变**（外部第三方 API，独立 OAuth2 资源服务器链 `OpenApiResourceServerConfig`，SDK 已发布此路径，不改以避免 SDK 破坏）
  - **更新 `SecurityConfig.PUBLIC_PATHS`**（`backend/platform-security/.../SecurityConfig.java`）：
    - 已有 API 公开路径前加 `/api`：`/api/sys/auth/login`、`/api/sys/auth/login-methods`、`/api/sys/auth/captcha`
    - **新增**静态/SPA 公开路径：`/`、`/index.html`、`/*.html`、`/*.js`、`/*.css`、`/assets/**`、`/error`
    - 保留 `/doc/**`、`/swagger-ui/**`、`/v3/api-docs/**`、`/actuator/**`、`/favicon.ico`、`/ws/**`
  - **更新 `OpenApiResourceServerConfig`**：`securityMatcher` 保持 `/openapi/**`（ExternalNotifyController 路径不变，SDK 已发布）
  - **创建 SPA fallback**：在 `backend/app/src/main/java/com/example/app/web/` 新增 `SpaErrorController.java`，实现 `ErrorController`，处理 `/error`：
    - 404 且请求 URI 非 `/api/**`、`/ws/**`、`/v3/api-docs/**`、`/swagger-ui/**`、`/actuator/**`、`/oauth2/**`、`/openapi/**`、`/.well-known/**` 开头 → `forward:/index.html`（HTTP 200）
    - 其他错误（含 API 404、5xx）→ 返回 JSON 错误体（沿用 `DefaultErrorAttributes` 格式：`timestamp`、`status`、`error`、`path`、`message`）
    - 实现：继承 `AbstractErrorController` 或直接 `@Component @Controller` + `@RequestMapping("/error")`（覆盖默认 `BasicErrorController`）
  - **更新所有控制器测试** MockMvc 路径加 `/api` 前缀（搜索所有 `MockMvcRequestBuilders.*("/sys/`、`@WebMvcTest`、`.perform(get("/sys/`、`.perform(post("/sys/` 等）
  - **更新 `application.yml`**：明确配置 `spring.web.resources.static-locations: classpath:/static/,file:/app/static/optional/`（同时支持 jar 内嵌和容器外挂卷），`spring.mvc.throw-exception-if-no-handler-found: true`（让未知路径触发 ErrorController）
  - **更新 E2E 脚本** `e2e/api-e2e.sh` 中硬编码的 `/sys/`、`/openapi/` 路径加 `/api` 前缀
  - **更新集成文档路径**（如果 SDK 文档 / docs/ 引用了无前缀路径）

  **Must NOT do**:
  - 不修改前端代码（`baseURL='/api'` 保持不变 —— 已与 nginx 一致）
  - 不修改 OAuth2 AuthorizationServerConfig 的端点（`/oauth2/**` 保持 Spring AS 默认）
  - 不修改 WebSocket 路径（`/ws/notify` 保持不变 —— 前端直连）
  - 不删除现有 `docker/nginx.conf`、`Dockerfile.frontend`（Task 32 处理 Docker 合并）
  - 不引入新依赖（`AbstractErrorController` 在 spring-boot-autoconfigure 已有）
  - 不破坏 `SPRING_PROFILES_ACTIVE=prod`（无 prod yml，回落 base，保持现状）

  **Acceptance Criteria**:
    - [ ] 10 个 `@RestController`（所有 `/sys/**` 内部 API）带 `/api` 前缀；`ExternalNotifyController`（`/openapi/**`）保持不变
    - [ ] `SecurityConfig.PUBLIC_PATHS` 含静态/SPA 公开路径
    - [ ] `SpaErrorController` 正确处理 SPA 404 → index.html，API 404 → JSON
    - [ ] `OpenApiResourceServerConfig.securityMatcher` 保持 `/openapi/**`（SDK 兼容）
    - [ ] E2E 脚本 `/sys/**` 路径同步加 `/api`；SDK 测试无改动（`/openapi/**` 不变）
    - [ ] `mvn test -Dspring.profiles.active=test` 全绿
    - [ ] 前端 `npm run build` 不受影响（无前端改动）

  **QA Scenarios**:
  ```
  Scenario: 后端单测全绿
    Tool: Bash
    Steps:
      1. cd backend && mvn test -Dspring.profiles.active=test
    Expected Result: BUILD SUCCESS, 0 failures
    Evidence: .sisyphus/evidence/task-31-backend-tests.txt

  Scenario: SPA fallback 验证
    Tool: Bash
    Steps:
      1. 本地启动后端（SPRING_PROFILES_ACTIVE=test 或 dev）
      2. curl -i http://localhost:8090/sys/user → 200 + index.html 内容（非 JSON）
      3. curl -i http://localhost:8090/api/sys/auth/login-methods → 200 JSON
      4. curl -i http://localhost:8090/api/sys/user → 401 JSON（未认证）
      5. curl -i http://localhost:8090/nonexistent/deep/link → 200 + index.html
      6. curl -i http://localhost:8090/api/nonexistent → 404 JSON（非 index.html）
    Expected Result: SPA 路径返回 HTML，API 路径返回 JSON
    Evidence: .sisyphus/evidence/task-31-spa-fallback.txt
  ```

  **Commit**: YES - `refactor(api): add /api prefix to REST controllers + SPA fallback for single-image deploy`

- [x] 32. **前后端 Docker 镜像合并 + 单服务 compose** [Wave 5 - 部署合并]

  **依赖**：Task 31 完成后执行（Dockerfile 内 `mvn package` 会编译 Task 31 改动后的代码）。

  **What to do**:
  - **新增 `docker/Dockerfile`**（统一入口，替换 `Dockerfile.backend` + `Dockerfile.frontend`）三阶段构建：
    ```dockerfile
    # Stage 1: frontend build
    FROM node:20-alpine AS frontend-builder
    WORKDIR /build
    COPY frontend/package.json frontend/package-lock.json* ./
    RUN npm ci --registry=https://registry.npmmirror.com
    COPY frontend/ .
    RUN npm run build
    # → /build/dist/

    # Stage 2: backend build (embed frontend assets in classpath:/static/)
    FROM maven:3.9-eclipse-temurin-21 AS backend-builder
    COPY docker/maven-settings.xml /root/.m2/settings.xml
    WORKDIR /build
    COPY backend/pom.xml backend/checkstyle.xml ./
    COPY backend/platform-common backend/platform-starter backend/modules backend/app ./
    COPY --from=frontend-builder /build/dist/ ./app/src/main/resources/static/
    RUN mvn package -DskipTests -B -Dspotless.check.skip=true -Dcheckstyle.skip=true -Dspotbugs.skip=true -Djacoco.skip=true

    # Stage 3: runtime
    FROM eclipse-temurin:21-jre
    WORKDIR /app
    COPY --from=backend-builder /build/app/target/*.jar app.jar
    ENV JAVA_OPTS="-Xms256m -Xmx512m"
    ENV SPRING_PROFILES_ACTIVE=prod
    EXPOSE 8090
    ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
    ```
  - **更新 `docker/docker-compose.yml`**：合并为单 `app` 服务（移除 `frontend` 服务，`backend` 改名 `app`），端口 `8095:8090` 暴露 HTTP（前端 + API + WebSocket 共用）
  - **删除/归档旧文件**：
    - `docker/Dockerfile.backend`（合并入新 `Dockerfile`）
    - `docker/Dockerfile.frontend`、`docker/Dockerfile.frontend-local`、`docker/Dockerfile.local`（不再需要）
    - `docker/Dockerfile.runtime`（合并入新 `Dockerfile` 第三阶段）
    - `docker/nginx.conf`（不再需要反向代理 —— 单一源）
    - `docker/app.jar`（75MB 预构建 jar —— 已在 Task 30 计划清理，本任务执行 `git rm`）
    - `docker/docker-compose.local.yml`（如已被新单服务 compose 取代）
  - **更新 `.gitignore`** 确保 `docker/app.jar` 已忽略（避免再次提交）
  - **更新 `docs/deployment.md`**（如 Task 30 已创建）：将"双服务部署"改为"单镜像部署"

  **Must NOT do**:
  - 不在 Dockerfile 写明文密码
  - 不破坏 `.env.example` 凭证外部化约定
  - 不删除 `docker/maven-settings.xml`、`docker/settings.xml`（构建依赖）
  - 不改变端口对外映射 `8095:8090`（保持向后兼容）

  **Acceptance Criteria**:
    - [ ] `docker/Dockerfile` 三阶段构建成功
    - [ ] `docker build -t my-platform:latest -f docker/Dockerfile .` 成功
    - [ ] 镜像内 `app.jar` 包含 `BOOT-INF/classes/static/index.html`
    - [ ] `docker-compose up` 单服务启动，`http://localhost:8095` 返回前端 + `http://localhost:8095/api/sys/auth/login-methods` 返回 JSON
    - [ ] WebSocket `ws://localhost:8095/ws/notify?token=...` 可连接
    - [ ] 旧 Dockerfile/nginx.conf 已删除
    - [ ] `docker/app.jar` 已从版本库移除

  **QA Scenarios**:
  ```
  Scenario: 镜像构建
    Tool: Bash
    Steps:
      1. docker build -t my-platform:single -f docker/Dockerfile .
      2. docker image inspect my-platform:single --format '{{.Size}}'
    Expected Result: 构建成功，镜像 < 500MB
    Evidence: .sisyphus/evidence/task-32-docker-build.txt

  Scenario: 容器一体化验证
    Tool: Bash + docker-compose
    Steps:
      1. cd docker && docker compose up -d
      2. curl -i http://localhost:8095/ → 200 + index.html
      3. curl -i http://localhost:8095/api/sys/auth/login-methods → 200 JSON
      4. curl -i http://localhost:8095/sys/user → 200 + index.html（SPA fallback）
    Expected Result: 单容器同时服务前端+API
    Evidence: .sisyphus/evidence/task-32-single-container.txt
  ```

  **Commit**: YES - `build(docker): merge frontend+backend into single multi-stage image`

---

## Final Verification Wave（所有实现任务完成后 - 必做）

> 4 个评审代理并行运行，全部 APPROVE 后向用户呈现结果，等待用户明确确认。
> **不得在用户确认前标记 F1-F4 完成。**

- [x] F1. **Plan 合规审计** — `oracle`
  通读 plan，对每条 Must Have：验证实现存在（读文件/curl/运行命令）。对每条 Must NOT Have：搜索代码库禁用模式 — 发现则拒绝（file:line）。检查 .sisyphus/evidence/ 证据文件存在。对比交付物与 plan。
  Output: `Must Have [N/N] | Must NOT Have [N/N] | Tasks [N/N] | VERDICT: APPROVE/REJECT`

- [x] F2. **代码质量审查** — `unspecified-high`
  运行 `mvn spotless:check checkstyle:check spotbugs:check test jacoco:check` + 前端 `npm run type-check lint:check test:coverage build`。审查所有变更文件：`as any`/`@ts-ignore`、空 catch、console.log、注释代码、未用 import。检查 AI slop：过度注释、过度抽象、泛名（data/result/item）。
  Output: `Build [PASS/FAIL] | Lint [PASS/FAIL] | Tests [N pass/N fail] | Files [N clean/N issues] | VERDICT`

- [x] F3. **真实手动 QA** — `unspecified-high`（+ `playwright` skill）
  从干净状态启动。执行每个任务的每个 QA 场景 — 跟随精确步骤，捕获证据。测试跨任务集成（功能协同，非孤立）。测试边界：空状态、无效输入、快速操作。存 `.sisyphus/evidence/final-qa/`。前端三断点截图（375/768/1280）。
  Output: `Scenarios [N/N pass] | Integration [N/N] | Edge Cases [N tested] | VERDICT`

- [x] F4. **范围保真检查** — `deep`
  对每个任务：读"What to do"，读实际 diff（git log/diff）。验证 1:1 — 规格中的都建了（无遗漏），未建超出规格的（无蔓延）。检查"Must NOT do"合规。检测跨任务污染。标记未解释变更。
  Output: `Tasks [N/N compliant] | Contamination [CLEAN/N issues] | Unaccounted [CLEAN/N files] | VERDICT`

---

## Commit Strategy

每个任务遵循 TDD 原子提交（Metis 建议）：

```
Commit 1 (test): test(<scope>): add failing test for <behavior>
  - 仅测试文件，RED
Commit 2 (impl): feat(<scope>): <behavior>
  - 最小实现使测试 GREEN + Flyway 迁移 + MODULE.md（新模块）
Commit 3 (refactor/docs): 可选
```

- 每个 commit 独立编译通过
- Commit 2+ 必须 GREEN
- Flyway 迁移独立 commit：`flyway(<module>): V<n>__<desc>.sql`
- type ∈ {test, feat, fix, refactor, docs, flyway, chore}；scope = 模块名

---

## Success Criteria

### Verification Commands
```bash
# 后端（H2，不连远端 DB）
cd backend && mvn test -Dspring.profiles.active=test
# 期望: BUILD SUCCESS, JaCoCo LINE >= 80%

# 后端模块边界
cd backend && mvn test -Dtest=ModulithVerificationTest,ArchUnit* -Dspring.profiles.active=test
# 期望: 所有模块边界 + ScopedRepository 强制通过

# 前端
cd frontend && npm run type-check && npm run lint:check && npm run test:coverage && npm run build
# 期望: 全绿, coverage >= 80%

# E2E 现有契约不破坏
cd e2e && ./api-e2e.sh && npx playwright test
# 期望: Layer1 8 用例全过 + Layer2 登录流程全过

# 登录安全全链路（curl 示例）
# 3 次错误 → 锁定 → 管理员解锁 → 成功
# 期望: 第4次返回 423, 解锁后返回 200

# OAuth2 发现端点
curl http://localhost:8090/.well-known/openid-configuration
# 期望: 返回含 authorization_endpoint/token_endpoint/end_session_endpoint 的 JSON

# WebSocket 消息推送（ws client）
# 期望: 发布后 1s 内连接客户端收到消息
```

### Final Checklist
- [ ] 所有 Must Have 存在
- [ ] 所有 Must NOT Have 缺失
- [ ] 所有测试通过（H2 profile）
- [ ] 现有 E2E 契约不破坏
- [ ] 四语言 SDK 各自能完成 OAuth2 + 消息发布 demo
- [ ] 前端三断点（375/768/1280）截图无破版

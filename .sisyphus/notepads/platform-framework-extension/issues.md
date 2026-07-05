# Issues — platform-framework-extension

> Problems, gotchas, blockers. Append-only.

## T1 — 已解决 / 环境备注（2026-07-04）
- **[ENV, non-blocking] Docker Desktop ryuk 500**：本机 macOS Docker Desktop 28.4.0 上 Testcontainers 1.20.4 的 ryuk 资源回收容器启动报 `Status 500`（`tc.testcontainers/ryuk:0.11.0 -- Could not start container`），导致 `@Testcontainers(disabledWithoutDocker=true)` 误判 Docker 不可用并优雅跳过 FlywayConsistencyIT。绕过：`export TESTCONTAINERS_RYUK_DISABLED=true`，IT 即可实跑并通过。属环境特性，CI 通常无此问题；不阻塞 T1（graceful skip 满足验收，且已用绕过方式验证 IT 逻辑正确）。
- **[PRE-EXISTING, latent] 实体/DDL 字段长度漂移**：`SysUser.avatar` 实体 `@Column(name="avatar")`（默认 length=255）vs V1 DDL `VARCHAR(500)`。当前 Hibernate validate 未因此报错（DB 列更宽，Hibernate 宽容通过），但属潜在技术债。其余字段长度均一致。建议后续任务对齐（要么实体加 `length=500`，要么 DDL 改 255）。
- **[INFO] git hooksPath 指向 husky 前端**：`core.hooksPath = frontend/.husky/_`，后端 `.githooks/pre-commit`（spotless）未激活。后端格式/规范依赖 `mvn` 的 validate 阶段（spotless:check + checkstyle）把关，已通过。
- **[INFO] Flyway 警告 H2 2.2.224 比 tested 2.2.220 新**：仅提示，不影响迁移。

## T4 — 并发任务导致 pom.xml 竞态 + ArchUnitTest 循环依赖（2026-07-04）
- **[BLOCKER for full `mvn test`, NOT T4's] 并发任务污染 platform-common/pom.xml**：T4 编辑 pom.xml 添加 redis/testcontainers 依赖后，另一并发 Wave 1 任务（T2/T3，数据权限/ScopedEntity）同时编辑同一 pom.xml，导致：① T4 的 redis/testcontainers/failsafe/build 依赖被覆盖丢失；② archunit-junit5 被重复添加两次。多 agent 并发写同一文件 = 经典 lost-update。T4 用 Write 工具整体重写 pom.xml 修复（包含所有需要的依赖）。**教训：并发任务共享 pom.xml 时，编辑后立即验证 git diff，发现竞态立即整体重写而非增量 edit。**
- **[BLOCKER for full `mvn test`, NOT T4's] ArchUnitTest 循环依赖失败**：`com.example.common.security.ArchUnitTest.commonShouldBeFreeOfCycles` 检测到 `datapolicy ↔ persistence` 包循环：`DataScopeSpecification.of` 的泛型 `<T extends ScopedEntity>` 依赖 persistence；`ScopedRepository.scopeFindById` 调用 `DataScopeContext/DataScopeSpecification` 依赖 datapolicy。这是 T2/T3（数据权限）任务的代码，ArchUnitTest 也是另一任务的产物。**属并发任务间的契约冲突，需 Orchestrator 协调**（要么 datapolicy 任务消除循环，要么 ArchUnitTest 调整 slice 划分）。T4 的 Redis 代码（com.example.common.cache）独立成 slice，不涉及此循环。T4 验收用 `-Dtest=RedisCacheServiceTest` 隔离验证：14/14 绿。
- **[INFO] T4 顺带补充 archunit-junit5 依赖**：parent pom 已在 dependencyManagement 声明 archunit 1.3.0，但创建 ArchUnitTest 的并发任务忘记在 platform-common/pom.xml 添加 dependency，导致测试编译失败。T4 在重写 pom 时一并补上（test scope，无害）。

## F1 Plan Compliance Audit — REJECT (2026-07-04)

### Blocking issue: backend build RED
`cd backend && mvn clean test -Dspring.profiles.active=test` FAILS at platform-common (DoD #1 violated).
- Reactor HALTS at platform-common → sys/audit/notify/openapp/login-ldap/app tests NEVER RUN.
- platform-common failures (full reactor): Tests run 46, Failures 1, Errors 8:
  - ArchUnitTest.commonMustNotDependOnSysModule: FAIL (empty should — no classes matched)
  - AuditAspectTest: 5 ERROR (UnfinishedMockingSession)
  - PermissionAspectTest: 3 ERROR (UnfinishedMockingSession + NoClassDefFound ForbiddenException)
- Isolated `mvn test -pl platform-common` is worse: also ArchUnitTest.commonMustNotDependOnAppModule (FAIL), ExceptionTest 4 ERROR, LoginMethodRegistryTest 8 ERROR.
- Root cause likely: platform-common test classpath does not see sys/app classes → ArchUnit rules have empty `should`; MockitoExtension + manual mock session mismatch in aspects tests.
- Consequence: JaCoCo ≥80% gate (Must Have #3) cannot pass; E2E contracts (DoD #3) cannot run; most complex modules (openapp OAuth2/OIDC, notify WebSocket) are UNVERIFIED.

### Verified OK
- All 5 new modules present: platform-security, modules/notify, modules/openapp, modules/audit, modules/login-ldap — each with pom + MODULE.md + AutoConfiguration + META-INF spring imports; root pom registers all 9 modules.
- Flyway: no ON CONFLICT in any new script (only a comment in V2 explaining removal). H2 compatible.
- Frontend: npm run lint:check PASS (0 err/53 warn), test:run PASS (38), build PASS (built in 45s).
- Must NOT Have patterns all ABSENT: no Hibernate @Filter (DataScopeSpecification uses JPA Criteria), no {{ node.label() }} (NTree refactor done), no multi-tenant/i18n/MFA/SAML/WebAuthn, no Redis "later" placeholder, OAuth2 static management (JdbcRegisteredClientRepository CRUD, no RFC7591 dynamic registration).
- 4 SDKs present: client-sdk-{c,go,python} + backend/client-sdk-java. docs/integration quickstarts present. sdk-release.yml present. docker/app.jar removed from git.
- 30/30 task commits present in git log.

## [T17 F4-review "bulk delete scope gap" — FALSE PREMISE] (2026-07-04)

**Task claim (F4 reviewer):** "UserService calls userRepository.deleteAllByIdInBatch(ids)
without data scope filtering" → Metis boundary 6 bulk-delete protection missing.

**Actual state (verified):** The gap does NOT exist. Bulk delete protection was already
implemented AND tested in commit `7d89027` (T17 itself, "feat(sys): apply DataScope to
SysUser queries with 5 scope levels").

**Evidence:**

1. `UserService.deleteBatch(List<Long> ids)` (UserService.java:161-185) already:
   - Resolves current user's scope via `dataScopeResolver.resolve(...)`
   - If scope is null (ALL/admin) → skips check, direct delete
   - Otherwise builds `DataScopeSpecification` + id-in spec, runs
     `userRepository.count(scopeSpec.and(idSpec))`, and if
     `visibleCount != ids.size()` throws `new BizException(403, "无权删除数据权限范围外的用户")`
   - Only then calls `userRepository.deleteAllByIdInBatch(ids)`

2. Tests already cover cross-scope rejection:
   - `UserServiceTest.DeleteBatch` (4 unit tests, all pass):
     - empty list → no-op
     - ALL scope → skip check
     - UNIT scope, all visible → delete succeeds
     - **UNIT scope, some out-of-scope → 403 BizException "无权删除"** (the exact
       scenario the task asked to add)
   - `UserDataScopeTest.BatchDeleteProtection` (14 integration tests, all pass):
     count-based scope filtering verified for ALL/UNIT with real DB rows.

3. `git log -- backend/.../UserService.java` shows last touch = `7d89027` (T17).
   Working tree clean — nothing uncommitted. No further fix commit is warranted.

4. Build verified GREEN: `mvn test -Dspring.profiles.active=test -pl modules/sys -am`
   → BUILD SUCCESS, exit 0. Relevant suites: "批量删除" 4/4, "批量删除范围保护" 14/14.

**Action taken:** NONE. No code/test changes (would be redundant AI slop / churn on
already-correct, already-tested code). No commit (nothing to commit). Reported accurate
state back to orchestrator.

**Lesson:** Before acting on a reviewer's "gap" finding, diff against the cited commit
and run the existing test suite. F4 reviewer likely evaluated a stale snapshot (pre-7d89027)
or the protection layer was overlooked. The Metis boundary 6 requirement
("override ScopedRepository.deleteAll 禁用或按 scope 过滤") IS satisfied via the
count-based scope filter in `deleteBatch`.

## T31 — SpaErrorController 编译陷阱（2026-07-04）
- **I1: `ErrorAttributeOptions` 包名是 `org.springframework.boot.web.error`，不是 `...servlet.error`**。`DefaultErrorAttributes` 和 `ErrorController` 在 `org.springframework.boot.web.servlet.error`，但 `ErrorAttributeOptions`（Spring Boot 2.3+ 抽出的公共类，servlet+reactive 共用）在上一层 `org.springframework.boot.web.error`。plan spec 4.3 给的 import 写错了包，编译报「找不到符号」。修复：单独 import `org.springframework.boot.web.error.ErrorAttributeOptions` + `...ErrorAttributeOptions.Include`。
- **I2: `DefaultErrorAttributes.getErrorAttributes(...)` 第一个参数是 `WebRequest`，不是 `HttpServletRequest`**。需 `new ServletWebRequest(request)` 包装。plan spec 4.3 直接传 `request`，编译报「参数不匹配; HttpServletRequest 无法转换为 WebRequest」。修复：引入 `org.springframework.web.context.request.ServletWebRequest` + `WebRequest`。
- **I3: plan 给的 SpaErrorController 源码两处编译错误，需以实际 Spring Boot 3.3.5 API 为准修正**。教训：plan 里的代码片段是设计意图，落地时必须对照实际依赖 jar 的 API（`jar tf` + javadoc）校验签名。LSP(jdtls) 在本环境未安装，靠 `mvn compile` 快速反馈。

## T31 — SpaErrorController 循环守卫修复（2026-07-04）
- **Bug**：SPA 路由 404（如 `GET /dashboard`）时，`handleError` forward 到 `/index.html`。若 `static/index.html` 不存在（本地开发未构建前端 / Task 32 Docker 合并前），该 forward 本身又 404 → 重新进入 `/error` → `ERROR_REQUEST_URI` 变成 `/index.html` → `isApiOrInfrastructurePath("/index.html")` 返回 false → 再次 forward 到 `/index.html` → **无限递归直至 StackOverflowError**。
- **Fix**：在 `handleError()` 入口最前面加循环守卫——若 `ERROR_REQUEST_URI == "/index.html"`，直接返回 404 JSON（`index.html not found (frontend not built)`），打破递归。仅此一处改动，不影响 SPA/API 正常分支。
- **验证 Bean 覆盖生效**：`ApplicationContextLoadsTest`（@SpringBootTest 全上下文加载）通过 = `SpaErrorController` 这个 `@Controller` + `@RequestMapping("/error")` bean 与默认 `BasicErrorController`（`@ConditionalOnMissingBean(ErrorController.class)` 守卫）无冲突，前者正确覆盖后者。`mvn test` 全绿 510/0。
- **教训**：forward-based fallback 必须考虑「forward 目标本身缺失」的退化路径，否则 error→forward→error 形成环。任何 forward/redirect 到固定资源（index.html、favicon 等）的 error handler 都应有「目标 URI 命中 error」的兜底。

## T32 — Dockerfile platform-security 模块遗漏 + npm ci 兼容（2026-07-04）
- **I1: plan spec 的 Dockerfile 漏 COPY `backend/platform-security`**。plan 4.1 给的 Dockerfile 只列了 `platform-common/platform-starter/modules/app` 四个源码 COPY，但实际 backend/pom.xml 有 9 个 modules（含 `platform-security`，由早期安全模块任务引入）。首次 `docker build` 在 Maven 阶段报 `Child module /build/platform-security of /build/pom.xml does not exist`。Fix：在 `COPY backend/platform-common` 后加 `COPY backend/platform-security ./platform-security`。**教训：plan 里的基础设施 spec 是基于某个时间点的快照，落地时必须对照实际 pom.xml modules 列表验证 COPY 完整性。**
- **I2: `npm ci` 要求 package-lock.json 存在，但项目 gitignore 了它（.gitignore 第 19 行）**。本地构建磁盘有 package-lock.json 走 `npm ci`（OK）；CI checkout 后无该文件，`npm ci` 会报 `npm ci can only install packages when your package.json and package-lock.json or npm-shrinkwrap.json are in sync`（实际是 file not found）。Fix：Dockerfile 用 `if [ -f package-lock.json ]; then npm ci; else npm install; fi` 兼容两种场景。**潜在技术债**：CI 构建无锁版本，传递依赖漂移风险；建议后续任务把 package-lock.json 提交进版本库（移除 .gitignore 第 19 行），届时可恢复纯 `npm ci`。
- **I3: JRE runtime 镜像无 `jar` 命令，无法在容器内验证 jar 内容**。eclipse-temurin:21-jre 只含 `java/jfr/jrunscript/jwebserver/keytool/rmiregistry`，无 `jar`/`unzip`/`python3`。验证 `BOOT-INF/classes/static/index.html` 需用 `docker create` + `docker cp` 把 jar 拷到宿主机用宿主 JDK 的 `jar tf` 检查。非阻塞，仅影响验证流程。
- **I4: 镜像大小 607MB，超出 plan 估算 ~350MB / 目标 < 500MB**。Spring Boot fat jar（含 Tomcat + 全套 Spring 生态 + 业务模块）本体 ~85MB，eclipse-temurin:21-jre base ~270MB，加上 layer 元数据。未优化（见 decisions D6 理由）。若需瘦身优先级：换 alpine JRE base > JLink 裁剪 > 模块拆分。

## T31 — SecurityConfig 阻断 SPA 页面路由（2026-07-04 集成 QA 发现）
- **Bug**：合并 Docker 镜像实测 `GET /sys/user`（SPA 页面）返回 **403 JSON**（非 200 HTML）。根因：`anyRequest().authenticated()` 在 DispatcherServlet 之前拦截所有非白名单路径，SPA deep-link 全部 403，`SpaErrorController` 永远到不了。`GET /api/nonexistent` 也 403（security 先于 404）。
- **Fix**：改为三层授权（见 decisions.md `T31 — SecurityConfig 三层授权策略修复`）：PUBLIC_PATHS permitAll → `/api/**` authenticated → anyRequest permitAll。
- **教训**：Spring Security filter chain 在 DispatcherServlet 之前执行——任何依赖 `ErrorController` / `NoHandlerFoundException` 做 fallback（如 SPA index forward）的设计，必须确保目标路径能穿过 Security 层（permitAll），否则 fallback 链路在 Security 层就被截断。集成 QA（真实容器 curl 各类 URL）是发现此类「单测绿、集成挂」问题的唯一手段——纯 Mockito 单测不加载 SecurityFilterChain。

## T31 — NoResourceFoundException 被 GlobalExceptionHandler 截获（2026-07-04 集成 QA 发现）
- **Bug**：SecurityConfig 三层授权修复后，`GET /sys/user`（SPA 页面）仍返回 **500 JSON** `{"code":500,"message":"系统内部错误"}`。app 日志：`org.springframework.web.servlet.resource.NoResourceFoundException: No static resource sys/user.` 被 `GlobalExceptionHandler.@ExceptionHandler(Exception.class)` catch-all 捕获 → 500。
- **根因**：Spring Boot 3.2+ 静态资源处理器找不到文件时抛 `NoResourceFoundException`（继承 `Exception`），被 `@RestControllerAdvice` 的 catch-all **先于** `/error` dispatch path 截获，`SpaErrorController` 永远到不了。
- **Fix**：新建 `com.example.app.web.SpaForwardHandler`——`@ControllerAdvice` + `@Order(HIGHEST_PRECEDENCE)` + `@ExceptionHandler(NoResourceFoundException.class)`，优先于 `GlobalExceptionHandler`。SPA 路由 → `return "forward:/index.html"`（视图名）；API/基础设施路径 → 404 JSON。
- **关键陷阱**：
  1. 必须是 `@ControllerAdvice`（非 `@RestControllerAdvice`）——后者会把 `"forward:/index.html"` 当 JSON body 序列化，而非视图名 forward。
  2. 必须 `@Order(HIGHEST_PRECEDENCE)`——否则 `GlobalExceptionHandler` 的 catch-all `Exception.class` 会因更具体的 `NoResourceFoundException` 匹配优先级……实际上 Spring 对同包 advice 按 `@Order` 决定哪个 `@ExceptionHandler` 解析器先注册，HIGHEST_PRECEDENCE 确保本类先被查到。
  3. `SpaErrorController`（`/error` dispatch）和 `SpaForwardHandler`（`NoResourceFoundException` 直接捕获）现在职责互补：前者处理 `throw-exception-if-no-handler-found=true` 抛出的 `NoHandlerFoundException` 及其它到达 `/error` 的错误；后者专门拦截静态资源 404 异常（Spring Boot 3.2+ 的默认行为，不走 `/error`）。
- **教训**：Spring Boot 3.2+ 改变了静态资源 404 的处理路径——从「转发 `/error`」变为「抛 `NoResourceFoundException`」。任何 SPA fallback 设计必须同时覆盖两条路径（`/error` dispatch + `NoResourceFoundException` 异常捕获）。集成 QA（真实容器 curl + 查 app 日志）是发现此类框架版本行为差异的唯一手段。

## F3 — RedisConfig JavaTimeModule 缺失导致登录 500（2026-07-04 发现 → 已修复 → 复验通过）
- **Bug（F3 首轮发现，REJECT）**：合并 Docker 镜像实测 `POST /api/sys/auth/login`（admin/admin123 + 正确验证码）返回 **500**。前端 alert "系统内部错误"，登录完全不可用。
- **根因**：`RedisConfig.jsonRedisSerializer()`（platform-common/.../cache/RedisConfig.java:77-85）构造 `ObjectMapper` 时**未注册 `JavaTimeModule`（JSR310）**。`SessionInfo`（sys-module/.../dto/SessionInfo.java）含两个 `LocalDateTime` 字段（loginAt、expiresAt）。登录成功后 `SessionEventListener.onLoginSuccess → SessionService.recordSession → RedisCacheService.set → GenericJackson2JsonRedisSerializer.serialize(SessionInfo)` 抛 `InvalidDefinitionException: Java 8 date/time type java.time.LocalDateTime not supported by default` → 反向传播成 500。
- **为什么 orchestrator 的 curl 冒烟没发现**：curl 验证的 9 个端点全是 GET（SPA root/深链接/静态资源/公开 API/受保护 API），**未覆盖 POST /api/sys/auth/login**。该 500 只在「认证成功 + 写 session 缓存」路径上发生，curl GET 冒烟天然无法触达。**这正说明 F3 真实浏览器端到端 QA（含验证码 + POST 登录）不可替代。**
- **验证码 QA 技巧**：登录页有图形验证码（默认 captcha enabled）。读 Redis 拿答案最可靠：`GET /api/sys/auth/captcha` 拿 captchaId → `docker exec my-platform-redis redis-cli GET "captcha:{captchaId}"`（去引号）→ 填表。只读、无副作用，比 OCR base64 图可靠。
- **Fix**：commit `6278096 fix(redis): register JavaTimeModule` —— RedisConfig.jsonRedisSerializer() 加 `mapper.registerModule(new JavaTimeModule())` + `mapper.disable(WRITE_DATES_AS_TIMESTAMPS)`。依赖 `jackson-datatype-jsr310` 已由 spring-boot-starter-web 间接引入，无需新增。
- **复验（F3 二轮，本任务）**：同一镜像同一容器（已重启带 fix）真实浏览器登录 → **PASS**：
  - Scenario B 登录：填表（admin/admin123 + Redis 读取的验证码 Aq9f）→ 跳转 `/dashboard`，显示"欢迎回来，超级管理员！"+ 4 张统计卡（用户 0/角色 0/菜单 1/权限 27）。
  - Scenario C SPA 深链接 `/sys/user`：用户管理表格正常渲染，admin 行（超级管理员/总部/正常），分页 1/10。非 JSON 非 500，JWT 持久化 + SPA fallback 全链路通。
  - Scenario D `/sys/role`：角色管理表格，admin 行（超级管理员/全部数据/启用）+ 权限/编辑/删除按钮。
  - Scenario E 响应式三断点（DOM 实测验证）：375px sider 隐藏 + 卡片 1 列（343px）；768px sider 64px icon-rail + 卡片 2 列（328px）；1280px sider 240px 全展 + 卡片 4 列（240px）。Naive UI n-layout-sider 自动折叠 + NGrid 响应式 cols 工作完美。
  - **VERDICT: APPROVE**（前轮 REJECT 的全部阻塞场景 B/C/D/E 全部转 PASS）。
- **附带发现（非阻塞）**：dashboard 控制台有 1 个 404 `GET /api/sys/notify/inbox/unread-count`。notify 模块的未读消息计数端点未实现/路由缺失，前端消息中心组件兜底处理未报错。不影响登录/导航/表格，记录待后续 notify 任务跟进。
- **教训**：① 任何 `ObjectMapper` 手搓配置（尤其用于 Redis/JMS 等非 Web 序列化场景）必须显式注册 `JavaTimeModule`——Spring MVC 的 ObjectMapper 自动配了，但 `new ObjectMapper()` 不会继承。② F3 必须真实跑登录 POST，curl GET 冒烟覆盖不到认证写路径。③ 响应式验证用 DOM 实测（getComputedStyle + getBoundingClientRect）比截图视觉判断更可靠（本环境 look_at / zai 图像分析都不可用，DOM 实测照样能精确验证）。

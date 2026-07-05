# Decisions — platform-framework-extension

> Architectural choices made during execution. Append-only.

## T1 — H2 测试 profile + V2 兼容修复（2026-07-04）
- **D1: V2 跨库兼容语法选 `INSERT...SELECT...WHERE NOT EXISTS`**（而非 `MERGE INTO`）。理由：MERGE INTO 在 H2 与 PG 间列清单/语义有细微差异且更啰嗦；`SELECT...WHERE NOT EXISTS` 是标准 SQL，在 PG15+ 与 H2(PostgreSQL 模式) 上与 `ON CONFLICT DO NOTHING` 语义完全等价（幂等、不重复插入、不报错），最可移植。生产 PG 播种行为保持不变。
- **D2: 单源 Flyway 脚本，不引入 H2 专用 DDL**。坚持 V1/V2 同时跑在 PG 和 H2 上，避免维护两套迁移。V1 原生 H2 兼容（BIGSERIAL/COMMENT ON/NOW()/IF NOT EXISTS 均支持），无需改动。
- **D3: test profile 用 `ddl-auto=validate`**（非 none）。让 Hibernate validate 实体↔Flyway 表结构一致成为基线的一部分；Flyway 仍是 schema 唯一来源。avatar 长度漂移见 issues（非阻塞）。
- **D4: 新增 `ApplicationContextLoadsTest`** 作为 Wave 0 基线冒烟。原因：现有测试全是纯单元测试，不加载 Spring 上下文、不连 DB；只有 `@SpringBootTest` 才能让 Flyway 迁移 + JPA validate 在 `mvn test` 中真正执行，产出 "Successfully applied 2 migrations" 日志证据。
- **D5: IT 用 `@Testcontainers(disabledWithoutDocker=true)` + `*IT.java` 命名**。`mvn test`(Surefire) 不执行 IT（保持快速、无 Docker 依赖）；`mvn verify`(Failsafe) 才执行。无 Docker 环境自动跳过不阻塞构建。
- **D6: app 模块独立声明 H2 + testcontainers + failsafe-plugin**。test scope 不传递，每个需要它们的模块必须显式声明。

## T31 — /api 前缀分离 + SPA fallback（2026-07-04）
- **D1: 选 `/api` 前缀方案而非 URL rewrite filter / content-based routing**。理由：前缀方案是声明式、编译期可见、对 SecurityFilterChain `requestMatchers` 友好；rewrite filter 会把路由复杂性藏进运行时规则，调试困难且易与 Spring Security matcher 错配。前缀方案与现有 nginx 反代（`/api/sys/user` API vs `/sys/user` 页面）一一对应，合并到 Spring Boot 后等效语义零 surprises。
- **D2: `/openapi/**` 保持不变（SDK 兼容）**。Java/Go/C SDK 已发布 `POST /openapi/notify/publish` 路径，加 `/api` 前缀会破坏已发布 SDK。`OpenApiResourceServerConfig.securityMatcher("/openapi/**")` 独立安全链（Order=2），与 `/api/**` default 链解耦，无需改动。前后端命名空间分离只针对内部管理 API。
- **D3: `SpaErrorController` 用 `@Controller` + `@RequestMapping("/error")` 覆盖默认 `BasicErrorController`**（后者 `@ConditionalOnMissingBean(ErrorController.class)` 守卫）。两分支：404 + 非 API/基础设施路径 → forward `/index.html` 返回 200（让 Vue Router 接管）；其余 → `DefaultErrorAttributes` JSON。返回 200 on 404 反直觉但必要（浏览器刷新 SPA deep link 时不能给 404）。靠 `isApiOrInfrastructurePath` 白名单前缀（`/api/`、`/openapi/`、`/oauth2/`、`/.well-known/`、`/ws/`、`/v3/api-docs`、`/swagger-ui`、`/actuator/`、`/doc/`）防止 API 404 误 fallback 成 HTML。
- **D4: 配 `spring.mvc.throw-exception-if-no-handler-found=true` + `spring.web.resources.static-locations=classpath:/static/,file:/app/static/optional/`**。前者让未知路径抛 `NoHandlerFoundException` 触发 `/error` 路由（否则 ResourceHttpRequestHandler 会吞掉）；后者支持 Docker 挂载卷 `file:/app/static/optional/` 覆盖前端 dist 而无需重新打包 jar。`add-mappings` 保持默认 true 不关闭。
- **D5: 菜单表 `sys_menu.path`（如 `/sys/audit`、`/sys/user`）属前端路由，不动**。这些是 Vue Router 浏览器 URL，不是 API 路径；与 controller `@RequestMapping` 路径属不同命名空间。`V21__audit_menu.sql` 的 `/sys/audit` 是 PAGE 类型菜单的 frontend route，保持不变。

## T32 — 单镜像合并（2026-07-04）
- **D1: 三阶段 multi-stage 构建（node → maven → JRE）而非两阶段（合并 node 到 maven 镜像）**。理由：① 阶段职责单一，每个 FROM 一个工具链，BuildKit 可并行拉取基础镜像；② node:20-alpine 和 maven:3.9-eclipse-temurin-21 互不污染，避免在 maven 镜像里装 node 的冗余层；③ 最终 runtime 镜像只含 eclipse-temurin:21-jre（无 node、无 maven、无 src），最小攻击面。三阶段是 Spring Boot + SPA 合并镜像的社区标准模式。
- **D2: 前端 dist 通过 `COPY --from=frontend-builder /build/dist/ ./app/src/main/resources/static/` 注入到 Maven 源码树，而非构建后 `jar uf` 注入**。理由：源码树注入让 Maven 正常打包，dist 文件进入 `BOOT-INF/classes/static/` 的路径由 spring-boot-maven-plugin 标准 repackaging 处理，不依赖手动 jar 操作；`jar uf` 需要额外 JDK 工具且破坏 layer cache（每次前端变更都要重打 jar 层）。代价：前端变更会触发 Maven 全量 package（~2min），但 layer cache 仍能复用依赖下载层（pom.xml 独立 COPY 在前）。
- **D3: 保留 `file:/app/static/optional/` 外挂静态目录（Dockerfile 第 51 行 `mkdir -p /app/static/optional`）**。理由：支持"不重新打 jar 热更新前端"——运维可挂载只读卷覆盖 `docker run -v /new/dist:/app/static/optional/`，Spring Boot 的 `ResourceChain` 按 `static-locations` 顺序查找（classpath 在前、file 在后），file 覆盖优先。代价：空目录在镜像里多占 ~0 字节（目录本身无成本），且避免了 application.yml 配置了但目录不存在时的启动 WARN。这是 Task 31 D4 的运行时配套。
- **D4: deploy.yml 单镜像化，移除 NEXUS build-args**。理由：合并 Dockerfile 内部用 `docker/maven-settings.xml`（阿里云 mirror，无需认证）下载依赖，不再需要 NEXUS_USER/NEXUS_PASS build-args 透传到 Maven 阶段。原双 Dockerfile 的 backend 镜像需要 build-args 是因为它的 settings.xml 可能引用 Nexus 私有仓库；合并后的 settings.xml 只配 public mirror，公开依赖即可。代价：若未来引入私有依赖（com.example:xxx 仅在 Nexus），需恢复 build-args + settings.xml server 段。当前所有依赖（spring-boot、naive-ui 等）均公开可拉，无阻塞。
- **D5: `npm ci` 退化为 `npm install` 的兼容分支（`if [ -f package-lock.json ]; then npm ci; else npm install; fi`）**。理由：项目 `.gitignore` 第 19 行 gitignore 了 `frontend/package-lock.json`（pre-existing decision），CI checkout 后无 package-lock.json，`npm ci` 会失败。本地构建（磁盘有 package-lock.json）走 `npm ci`（严格锁版本），CI 构建走 `npm install`（宽松）。这是对 pre-existing gitignore 决策的兼容，不改变它。**潜在技术债**：CI 构建无锁版本，可能因传递依赖漂移导致构建不一致；建议后续任务把 package-lock.json 提交进版本库（移除 .gitignore 第 19 行）。
- **D6: 镜像大小 607MB（实测），超出 plan 估算的 ~350MB / 目标 < 500MB**。原因：Spring Boot fat jar 内嵌 Tomcat + Spring Security + Spring Data JPA + Flyway + 所有业务模块依赖，jar 本体 ~85MB；eclipse-temurin:21-jre base ~270MB；JRE 内含 JLink 未裁剪（含 jfr、jwebserver 等非必要工具）。**不优化**：① 607MB 在 NAS 内网拉取 < 5s，非瓶颈；② JLink 裁剪需重写 base 镜像，ROI 低；③ 若未来需要瘦身，可换用 `eclipse-temurin:21-jre-alpine`（~180MB base）但需测试 glibc/musl 兼容性（Netty/Native 库可能踩坑）。

## T31 — SecurityConfig 三层授权策略修复（2026-07-04）
- **D1: 从「deny-all-except-whitelist」改为「protect-API-permit-SPA」**。原 `requestMatchers(PUBLIC_PATHS).permitAll().anyRequest().authenticated()` 会拦截所有不在白名单的路径（含 SPA 页面路由 `/sys/user`、`/dashboard`、`/login`、`/random/deep/link`），在 DispatcherServlet 之前返回 403，`SpaErrorController` 永远到不了，SPA deep-linking 完全失效。
- **D2: 三层顺序匹配（顺序不可调换）**：① `PUBLIC_PATHS` permitAll（含公开 API `/api/sys/auth/login` 等 + 静态资源 + SPA index）；② `/api/**` authenticated（所有内部管理 API 需登录）；③ `anyRequest` permitAll（SPA 页面路由放行 → 穿过 Security → 404 → SpaErrorController forward `/index.html` → 200 HTML）。**顺序是 load-bearing**：公开 API 必须在第①层先命中，否则被第②层 `/api/**` 要求认证。
- **D3: 不把 SPA 路由逐条加进 PUBLIC_PATHS**（脆弱、易漏），而是用 `anyRequest().permitAll()` 兜底。SPA 自身在客户端做路由守卫（无 token 跳 /login），后端只保护 API 命名空间。
- **D4: `/openapi/**` 和 `/oauth2/**` 不受影响**——它们由独立的 SecurityFilterChain（Order=1 AuthorizationServerConfig、Order=2 OpenApiResourceServerConfig）处理，`securityMatcher` 限定范围，本 default 链的 `anyRequest().permitAll()` 对它们无作用。

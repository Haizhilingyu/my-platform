# PROJECT KNOWLEDGE BASE

**Updated:** 2026-07-21
**Commit:** 4347349
**Branch:** main

## OVERVIEW

Polyglot monorepo: modular platform on Spring Boot 3.3 + Spring Modulith (Java 21) backend, Vue 3 + Naive UI frontend, 4 hand-written client SDKs (Java/Python/Go/C), Playwright E2E, Docker deploy to home NAS via Gitea Actions.

## STRUCTURE

```
my-platform/
├── backend/                 # Maven multi-module (Java 21 + Spring Boot 3.3.5)
│   ├── AGENT.md             # ⭐ AUTHORITATIVE backend guide (437 lines) — READ FIRST
│   ├── platform-common/     # AGENTS.md present (foundational package map)
│   ├── platform-security/   # AGENTS.md present (3 SecurityFilterChains)
│   ├── platform-starter/    # Aggregator POM (no source)
│   ├── modules/             # Business modules (Spring Modulith)
│   │   ├── sys/             # Users/roles/menus/units/config/permissions (MODULE.md)
│   │   ├── openapp/         # OAuth2 Authorization Server + OpenAPI resource server (MODULE.md)
│   │   ├── audit/           # @Auditable consumer → audit_log (MODULE.md)
│   │   ├── notify/          # WebSocket in-app notifications + publish API (MODULE.md)
│   │   ├── login-ldap/      # LDAP LoginMethodProvider SPI impl (MODULE.md)
│   │   ├── i18n/            # DB 驱动 MessageSource + 翻译 CRUD + Excel/JSON 导入导出 (MODULE.md, autoconfig)
│   │   └── ai-agent/        # In-app AI copilot: SSE 对话 + 工具调用 + 破坏性操作二次确认 (MODULE.md)
│   ├── client-sdk-java/     # Java SDK (also released as separate artifact)
│   └── app/                 # Bootstrap entry: Application.java, port 8090
├── frontend/                # Vue 3 + TypeScript + Vite 6 + Naive UI + Tailwind
│   └── AGENT.md             # ⭐ AUTHORITATIVE frontend guide (569 lines) — READ FIRST
├── client-sdk-python/       # Python ≥3.8 (requests)
├── client-sdk-go/           # Go 1.21 (stdlib net/http, zero deps)
├── client-sdk-c/            # C11 (libcurl, bundled JSON parser, Makefile)
├── e2e/                     # Playwright + shell E2E — AGENTS.md present
├── docker/                  # 3-stage Dockerfile + compose (covered by docs/deployment.md)
├── docs/                    # requirements/, design/, api-contracts/, integration/, deployment.md
├── .gitea/workflows/        # CI: ci.yml, deploy.yml, sdk-release.yml (NOT .github/)
└── .githooks/               # pre-commit: Spotless+Checkstyle+mvn test / lint-staged+vitest
```

## WHERE TO LOOK

| Task | Location | Notes |
|---|---|---|
| Add backend feature | `backend/AGENT.md` → "新增业务模块" / "新增 REST API" | Per-module `MODULE.md` first |
| Add frontend page | `frontend/AGENT.md` → "新增业务模块的前端页面" | Pattern: `modules/<name>/{api,views}/` |
| Add new SDK language | Mirror `client-sdk-go/` (cleanest reference) | Same OAuth2 contract, see SDK section below |
| Add E2E test | `e2e/AGENTS.md` + numbered `NN-topic.spec.ts` | Serial execution, fixtures required |
| Deploy / ops | `docs/deployment.md` (412 lines) | Topology, JWK rotation, rollback |
| CI pipeline | `.gitea/workflows/ci.yml` | Gitea Actions (GitHub Actions compatible) |
| DB migration | `backend/modules/<name>/src/main/resources/db/migration/V<N>__*.sql` | Per-module Flyway |
| Module integration docs | `docs/integration/` | API ref, OAuth2 flow, quickstarts per SDK |
| API contracts (truth) | `docs/api-contracts/` | Authoritative spec |
| Add boundary test (backend) | `backend/modules/<name>/src/test/java/.../dto/*BoundaryTest.java` | `@ParameterizedTest + @MethodSource`, direct `Validator` API |
| Add form validation test (frontend) | `frontend/src/modules/sys/views/<name>/__tests__/index.test.ts` | Vitest + @vue/test-utils, mock useMessage |
| Shared validation rules | `frontend/src/shared/utils/validation.ts` | FormItemRule factories + pattern constants |

## CONVENTIONS (PROJECT-SPECIFIC)

### Cross-stack contracts (do not break)
- **Result envelope**: `{ code, message, data }` — both backend (`Result.ok/fail`) and frontend (`Result<T>`).
- **PageResult**: `{ list, total, pageNum, pageSize }` — paginated list contract.
- **Permission identifiers**: `<module>:<resource>:<action>` (e.g. `sys:user:add`). Admin role has wildcard `*`.
- **Internal vs external API**: internal `/api/...` (JWT, web UI), external `/openapi/...` (OAuth2 resource server, machine-to-machine).
- **OAuth2 endpoints**: token at `POST /oauth2/token` (Basic Auth), publish at `POST /openapi/notify/publish` (Bearer).

### Input validation (frontend ↔ backend aligned)
- **Backend**: Jakarta Bean Validation on DTO fields (`@NotBlank`, `@Size`, `@Pattern`, `@Email`, `@Min`/`@Max`). All `@RequestBody` params must have `@Valid`. Controllers with `@RequestParam` validation use class-level `@Validated`.
- **Frontend**: Naive UI `FormItemRule` factories in `src/shared/utils/validation.ts` (`requiredRule`, `lengthRule`, `maxLengthRule`, `patternRule`, `emailRule`). All forms use `formRef.value?.validate()` before API call.
- **Pattern constants** (shared between frontend regex and backend `@Pattern`): `USERNAME_PATTERN = /^[a-zA-Z0-9_]+$/`, `PHONE_PATTERN = /^1[3-9]\d{9}$/`, `CONFIG_KEY_PATTERN = /^[a-zA-Z0-9._-]+$/`.
- **GlobalExceptionHandler** returns field-level errors: `Result.fail(400, "参数校验失败", errorsMap)` where `errorsMap` is `{field: message}`.
- **Boundary tests**: `*BoundaryTest.java` files use `@ParameterizedTest + @MethodSource` with Jakarta `Validator` API (no Spring context). Frontend: `*.test.ts` in `__tests__/` dirs test form validation rejection.

### SDK contract (all 4 SDKs identical)
- 3 OAuth2 grants: `client_credentials`, `authorization_code`, `refresh_token`.
- Auto-refresh on 401: refresh once → retry once (no infinite loop).
- `PublishRequest` fluent builder: `title/content/level/recipients/businessType/expireTime`.
- `MessageLevel`: `URGENT / IMPORTANT / NORMAL`. `RecipientType`: `USER / ROLE / UNIT`.
- Env vars everywhere: `PLATFORM_CLIENT_ID`, `PLATFORM_CLIENT_SECRET`, `PLATFORM_ISSUER`, `PLATFORM_RECIPIENT_ID`.

### Commit hygiene
- Conventional Commits enforced via commitlint (frontend) + commit-msg hook.
- Pre-commit runs `mvn test -pl platform-common,modules/sys` (backend) + `vitest run` (frontend). Heavy; use `--no-verify` only for WIP commits that don't touch test scope.
- First-time setup: `git config core.hooksPath .githooks`.

## TDD 铁律 (TEST-FIRST)

**先写测试，再写实现。这不是建议，是强制流程。**

1. **RED**：为新功能/修复先写失败测试（后端 `*Test.java` / `*BoundaryTest.java`，前端 `__tests__/*.test.ts`），运行确认它**因预期原因失败**。
2. **GREEN**：写最小实现让测试通过。
3. **REFACTOR**：在测试保护下重构，不改外部行为。

强制执行点：
- **任何 `feat:`/`fix:` 提交必须同提交包含测试变更**（新测试或更新的既有测试）。实现与测试禁止拆成两个提交。
- **提交前本地全绿**：后端至少跑变更模块 `mvn test`，前端 `vitest run`。红测试禁止提交；`--no-verify` 仅限不碰测试范围的 WIP。
- **修改既有行为时先改测试**：测试即规格说明。行为变更必须先体现为测试断言变更。（2026-07 教训：AI copilot 提交给 user 视图新增 `route.query.highlight` 依赖却没更新测试，留下 8 个红用例——功能代码与测试脱节。）
- **覆盖率是硬门禁不是目标**：后端 JaCoCo ≥80% 行、前端 ≥80% 行/函数/语句 + ≥70% 分支，CI 红灯即阻塞合并。
- **测试暴露实现 bug 时，先保留 failing test 再修实现**。（2026-07 实证：ChatPanel 测试暴露 `confirmState` 从未初始化，破坏性操作二次确认卡片线上从不渲染——纯靠人工 review 没发现。）

## E2E 与部署门禁

**部署前必须跑通 E2E，无例外。**

- 触发部署（merge 到 main / 手动 dispatch `deploy.yml`）前，必须在目标提交上执行 `bash e2e/run-e2e.sh`，**13 个 Playwright spec + 3 个 shell API E2E 全绿**才允许部署。
- E2E 串行执行（`workers: 1`），自动拉起完整本地栈（Postgres + Redis + 后端 + 前端构建产物），与生产单端口架构同构。
- `deploy.yml` 当前 `needs: []`（见 ANTI-PATTERNS），CI 不替你挡——流程必须挡：部署前在部署单/PR 中粘贴最近一次 E2E 全绿证据（`run-e2e.sh` 末尾 summary）。
- 后端接口变更（增删 endpoint、改契约字段）必须同步新增/更新对应 E2E spec，否则视为功能未完成。

## ANTI-PATTERNS (THIS PROJECT)

- **DO NOT** put `client-sdk-java/` outside `backend/` — Maven multi-module requires it there. Other 3 SDKs are at root.
- **`<NAS_IP>` placeholder**: appears in `docker-compose.yml`, `deploy.yml`, `sdk-release.yml` as `&lt;NAS_IP&gt;` (XML-escaped in pom.xml). Replace before deploy.
- **DO NOT** commit to `docker-compose.local.yml` secrets — `Postgres@2025` already leaked there; rotate.
- **DO NOT** skip the Gitea CI gate — `deploy.yml` has `needs: []` and can deploy untested code if pushed rapidly. Always wait for `ci.yml` green.
- **DO NOT** assume GitHub Actions — workflows live in `.gitea/workflows/`. Uses `actions/checkout@v4` and `GITHUB_TOKEN` via Gitea's compatibility shim.
- **DO NOT** add E2E tests as parallel — Playwright is `workers: 1` because backend state is shared. New tests must be serial-safe.
- **DO NOT** trust `client-sdk-c/*.o`, `*.a`, `test_platform_client`, `platform_demo` — compiled artifacts currently committed. Should be in `.gitignore`.
- **DO NOT** omit `@Valid` on `@RequestBody` parameters — all PUT/POST endpoints must trigger bean validation. PUT endpoints were previously missing `@Valid` (fixed in commit c68a6e3).
- **DO NOT** define validation rules independently in frontend and backend — use the shared pattern constants from `validation.ts` to keep them aligned.

## COMMANDS

```bash
# Backend
cd backend && mvn clean install -DskipTests
cd backend/app && mvn spring-boot:run          # http://localhost:8090

# Frontend
cd frontend && npm install && npm run dev       # http://localhost:5173

# Full local stack (postgres + redis + app)
cd docker && docker compose -f docker-compose.local.yml up

# E2E (spins local stack automatically) — 部署前必跑 (mandatory pre-deploy gate)
bash e2e/run-e2e.sh

# Type check / lint
cd frontend && npm run type-check && npm run lint:check
cd backend && mvn spotless:check checkstyle:check

# Coverage gates
cd backend && mvn test jacoco:check             # ≥80% line (CI gate)
cd frontend && npm run test:coverage            # ≥80% line/func, ≥70% branch

# Build (skip all quality gates)
cd backend && mvn package -DskipTests -Dspotless.check.skip=true -Dcheckstyle.skip=true -Dspotbugs.skip=true -Djacoco.skip=true

# Enable git hooks (one-time)
git config core.hooksPath .githooks
```

## NOTES

- **Admin login**: `admin / admin123` (default). Seed in Flyway V2.
- **Single-port architecture**: production Docker bakes Vue `dist/` into Spring Boot's `static/` classpath. Dev runs two ports (5173 → 8090 via Vite proxy).
- **Coverage gate mismatch**: README says ≥60%, `ci.yml` enforces ≥80%. Trust the CI.
- **`<NAS_IP>` placeholder**: appears in `docker-compose.yml`, `deploy.yml`, `sdk-release.yml`. Replace before deploy.
- **Module reuse**: external projects can pull `sys-module` via Maven (`com.example:sys-module:1.0.0-SNAPSHOT`) — see each module's `MODULE.md`.
- **Test slice bootstraps**: `SysTestApplication.java`, `AuditTestApplication.java` exist per module for fast Spring Boot test context.
- **Frontend dist override**: runtime mount `/app/static/optional/` lets ops swap Vue bundle without rebuilding jar.
- **No root `package.json`/workspaces**: each npm project (frontend, e2e) is independent.
- **Boundary test pattern**: `*BoundaryTest.java` files test DTO validation at edge values (null, empty, oversize, invalid pattern) using Jakarta `Validator` API directly (no Spring context needed). 8 files across sys + openapp + platform-common modules.
- **Form validation pattern**: All 7 CRUD forms use `formRef` + `:rules` + `validate()`. Rules are composed from shared factories in `src/shared/utils/validation.ts`.

# platform-common/ — Foundational Cross-Cutting Module

Base library depended on by **every** business module. Defines the contract layer: Result envelope, JPA auditing, permission/audit/scope aspects, exception hierarchy, JWT util, login SPI, Redis cache wrapper. No business logic lives here.

> **Read parent first**: `backend/AGENT.md` for backend-wide conventions (naming, layering, quality gates). This file documents platform-common internals only.

## STRUCTURE

```
src/main/java/com/example/common/
├── result/                     # Result<T> + PageResult<T> — DO NOT break contract (frontend depends)
├── exception/                  # BizException + 4 subclasses + GlobalExceptionHandler
├── persistence/                # BaseEntity (JPA auditing) + ScopedEntity + ScopedRepository
├── security/                   # @RequiresPermission + @RequiresAppScope + aspects + CurrentUser + JwtUtil
├── audit/                      # @Auditable + AuditAspect (async) + AuditEvent
├── datapolicy/                 # DataScope enum + DataScopeSpecification (row-level filter)
├── login/                      # LoginMethodProvider SPI + LoginMethodRegistry
├── cache/                      # RedisCacheService wrapper + RedisConfig
└── web/                        # PageUtils (pagination helper)
```

## WHERE TO LOOK

| Need | Class |
|---|---|
| Return data to frontend | `result/Result.ok(data)` / throw exception (never `Result.fail` in controller) |
| Paginated list | `result/PageResult<T>` + `web/PageUtils.toPage(page, size)` |
| Throw business error | `exception/BizException` (generic), `NotFoundException`, `ForbiddenException`, `AccountLockedException` |
| Get current user | `security/CurrentUser.get()` (ThreadLocal, set by `JwtAuthFilter`) |
| Declare permission | `security/@RequiresPermission("sys:user:add")` (with `Logical.AND/OR` for multi) |
| Declare OAuth2 scope | `security/@RequiresAppScope("notify:publish")` |
| Mark auditable action | `audit/@Auditable(action = "LOGIN", targetType = "USER")` |
| JPA entity base | Extend `persistence/BaseEntity` (auto-fills createdAt/updatedAt/createdBy/updatedBy) |
| Row-level data scope | Implement `persistence/ScopedEntity` + `ScopedRepository`; set `datapolicy/DataScopeContext` |
| Add login method | Implement `login/LoginMethodProvider` SPI → auto-registered via `LoginMethodRegistry` |
| Cache wrapper | `cache/RedisCacheService.get/set/evict` (prefer over raw RedisTemplate) |

## KEY CONTRACTS (DO NOT BREAK)

### Result envelope
```java
Result.ok(data)                       // { code: 200, message: "success", data: ... }
Result.ok()                           // no data
Result.fail(code, message)            // no data (used by BizException handler)
Result.fail(code, message, data)      // with data (used by validation handler — data = errors map)
// Result.fail() is internal only — GlobalExceptionHandler builds it from exceptions
```
Frontend's `Result<T>` interface and axios interceptors depend on this exact shape.

### Exception → HTTP mapping (GlobalExceptionHandler)
| Exception | HTTP | Result.code |
|---|---|---|
| `BizException` | 400 | custom |
| `NotFoundException` | 404 | 404 |
| `ForbiddenException` | 403 | 403 |
| `AccountLockedException` | 423 | 423 |
| MethodArgumentNotValid | 400 | 400 + errors map in data |
| ConstraintViolation | 400 | 400 + errors map in data |

### Permission aspect flow
1. `@RequiresPermission("sys:user:add")` on controller method
2. `PermissionAspect` intercepts → reads `CurrentUser.get()` permissions
3. Calls `PermissionLoader` (implemented by sys module) to load user permissions
4. Admin wildcard `*` short-circuits to true
5. Multi-permission: `Logical.AND` (default) / `Logical.OR`

### JPA auditing chain
`BaseEntity` (@CreatedBy/@CreatedDate/@LastModifiedBy/@LastModifiedDate) ← `@EnableJpaAuditing` in `app/Application.java` ← `AuditorAwareImpl` resolves `CurrentUser.get().getId()`.

## CONVENTIONS (platform-common-specific)

- **Package**: `com.example.common.<concern>` — flat, no nested `internal/`.
- **Aspects** (`PermissionAspect`, `AppScopeAspect`, `AuditAspect`) use Spring AOP `@Around`. Pointcuts match the corresponding annotation.
- **Audit is async**: `AuditAspect` publishes `AuditEvent` via `ApplicationEventPublisher`. `AuditRecorder` (consumer in audit module) writes to DB asynchronously. Don't await audit completion in business flow.
- **Login SPI**: `LoginMethodProvider` returns `LoginResult` with `LoginStatus` enum (`SUCCESS`/`INVALID_CREDENTIALS`/`DISABLED`/`LOCKED`). Registered via `LoginMethodRegistry.register(provider)` — modules call this in their autoconfig.
- **LoginRequest validation**: `LoginRequest` record has `@NotBlank` on username/password, `@Size` on all string fields. `AuthController.login()` uses `@Valid` to trigger validation. Login-specific validation (captcha) remains in controller logic.

## ANTI-PATTERNS

- **DO NOT** put business logic here — this is contract + infrastructure only. Move business code to the relevant `modules/<name>/`.
- **DO NOT** add new exceptions without adding a handler case in `GlobalExceptionHandler`.
- **DO NOT** change `Result<T>` field names or `PageResult<T>` shape — frontend type definitions mirror this exactly.
- **DO NOT** use `RedisTemplate` directly in modules — go through `RedisCacheService` (consistent serialization + key prefixing).
- **DO NOT** skip `BaseEntity` for entities that need audit fields — manual `createdAt` columns break JPA auditing.
- **DO NOT** change `LoginMethodProvider` SPI signature without updating all impls (currently: `login-ldap` module + sys password provider).

## NOTES

- `ArchUnitTest.java` enforces dependency rules (e.g., common cannot depend on business modules).
- Test pattern: `*Test.java` (unit, Mockito) vs `*IT.java` (integration, requires Redis/DB). See `cache/RedisCacheServiceIT.java`.
- `RedisConfig.java` uses `GenericJackson2JsonRedisSerializer` — all cached objects must be serializable.
- `DataScope` enum: `ALL`, `UNIT`, `UNIT_BELOW`, `SELF`, `CUSTOM`. Specification builder composes JPA Predicate.
- **Boundary tests**: `LoginRequestBoundaryTest.java` tests LoginRequest validation at boundary values using direct Validator API.

# platform-security/ — Spring Security Configuration

Owns the **default SecurityFilterChain** (`/api/**` + SPA + static resources). Loaded via `SecurityAutoConfiguration` when `platform-starter` is on classpath.

> **OAuth2 chains live elsewhere**: `/openapi/**` (resource server) and `/oauth2/**` (authorization server) are owned by `modules/openapp/`, registered at `@Order(1)` and `@Order(2)`. This module's default chain is `@Order(100)` (implicit).

## STRUCTURE

```
src/main/java/com/example/platform/security/
├── SecurityConfig.java            # Default SecurityFilterChain (PUBLIC_PATHS + /api/** authenticated)
├── SecurityAutoConfiguration.java # @Import(SecurityConfig) — auto-loads via spring.factories
└── JwtAuthFilter.java             # Token parse + Redis blacklist check + CurrentUser set
```

## WHERE TO LOOK

| Need | Location |
|---|---|
| Whitelist a public endpoint | `SecurityConfig.PUBLIC_PATHS` (add path, then run E2E — frontend route guard may also need update) |
| Change session policy | `SecurityConfig.filterChain()` — currently `STATELESS` |
| Add custom JWT validation | `JwtAuthFilter` (insert logic before `chain.doFilter`) |
| Understand OAuth2 chains | `backend/modules/openapp/` (not this module) |

## THREE-LAYER AUTHORIZATION POLICY (filter chain order)

```
1. PUBLIC_PATHS  → permitAll   (login, static, SPA index, docs, /ws/**, /actuator/**)
2. /api/**       → authenticated  (all internal API require JWT)
3. anyRequest    → permitAll   (SPA page routes → forwarded by SpaErrorController to /index.html)
```

`/openapi/**` and `/oauth2/**` never reach this chain (separate `@Order(1)`/`@Order(2)` chains in openapp).

## JWT AUTH FILTER FLOW

```
Request → JwtAuthFilter:
  1. Extract Bearer token from Authorization header
  2. If absent → continue (anonymous, may hit permitAll or 401)
  3. Parse + verify via JwtUtil (HMAC SHA-256, secret ≥32 bytes)
  4. Check Redis blacklist (key: jwt:blacklist:<jti>) — present = logged out
  5. Load permissions via PermissionLoader (implemented by sys module)
  6. Set SecurityContext authentication + CurrentUser (ThreadLocal)
  7. Continue chain
```

Logout writes `<jti>` to Redis blacklist with TTL = remaining token lifetime (24h default).

## CONVENTIONS

- **Stateless**: `SessionCreationPolicy.STATELESS` — no HTTP session, ever. JWT is the only session.
- **CSRF disabled**: stateless API, no cookies → CSRF not applicable.
- **Method security**: `@EnableMethodSecurity` activates `@PreAuthorize` / `@RequiresPermission` annotations globally.
- **Filter order**: `JwtAuthFilter` inserted before `UsernamePasswordAuthenticationFilter`.

## ANTI-PATTERNS

- **DO NOT** add new public API paths without checking frontend route guards — backend permit ≠ frontend allow.
- **DO NOT** enable sessions — entire auth flow assumes stateless JWT. Adding session state breaks horizontal scaling.
- **DO NOT** modify JWT secret at runtime — tokens issued before change become invalid (no rotation support yet).
- **DO NOT** put `/openapi/**` or `/oauth2/**` rules in `SecurityConfig` — they belong to openapp's chains. Mixing breaks filter ordering.
- **DO NOT** skip Redis blacklist check — required for logout enforcement.

## NOTES

- **Filter chain count**: 3 total (`/oauth2/**` @Order(1), `/openapi/**` @Order(2), default @Order(100)). See `backend/modules/openapp/` for the other two.
- **Secret requirement**: JWT HMAC key ≥ 32 bytes (256 bits). Shorter keys throw `WeakKeyException` at startup.
- **Token lifetime**: 24 hours (configurable via `app.security.jwt.expiration`).
- **Blacklist key TTL**: matches remaining token lifetime — auto-eviction after expiry.
- **Test coverage**: `JwtAuthFilterTest.java` covers parse/expire/blacklist/missing-header paths.

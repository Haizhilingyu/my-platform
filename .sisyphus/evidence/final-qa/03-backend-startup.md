# Backend Runtime Startup Attempt

**Date**: 2026-07-04

## Attempted Approaches & Results

### Approach 1: `spring-boot:run` with `-Dspring-boot.run.profiles=test`
- **Result**: ❌ FAILED
- **Cause**: `application-test.yml` lives in `src/test/resources/`, NOT on `spring-boot:run` runtime classpath. Spring reports: "Config data resource 'class path resource [application-test.yml]' does not exist".
- **Behavior**: Fell back to default profile, attempted connection to `192.168.1.2:5532` PostgreSQL with password "postgres" → `FATAL: password authentication failed for user "postgres"`.

### Approach 2: Env vars override datasource to H2 in-memory
- **Result**: ❌ FAILED
- **Cause**: `Cannot load driver class: org.h2.Driver`. H2 is declared `<scope>test</scope>` in `app/pom.xml` and is not on the `spring-boot:run` runtime classpath.

### Approach 3: Connect to real PG (192.168.1.2:5532)
- **PG reachable**: ✅ Yes (`nc -z` succeeded).
- **Auth**: ❌ Default password "postgres" rejected. Real password is in `docker/.env` (gitignored, not available to QA).
- **Redis (192.168.1.2:6380)**: ❌ Not reachable from this host (`nc -z` timed out).
- **Local Redis (localhost:6379)**: ❌ Not running.

## Conclusion
**Cannot start backend for live curl testing** — confirmed limitation noted in task brief:
> "may fail if DB/Redis unavailable — that's OK, document it"

## What WAS Verified
1. ✅ **H2 test-profile integration verified via test suite** (226 tests pass): `SysUnitRecursiveCteTest`, `DataScopeSpecificationTest`, `ApplicationContextLoadsTest` all boot the full Spring context against H2 + Flyway migrations successfully. This proves the JPA entities, Flyway scripts, repository queries, data-scope specifications, recursive CTEs all work end-to-end at the integration-test level.
2. ✅ **Flyway migrations apply cleanly** on H2 in PostgreSQL-compat mode (7 migrations: sys init/data/data-scope, notify init, audit init/menu, openapp init).
3. ✅ **LDAP conditional wiring** verified by `login-ldap-module` 12 tests.

## What Requires Real PG/Redis
- JWT issuance against persistent Redis-backed token store
- Captcha rate-limit / lockout (Redis INCR)
- WebSocket push / online session registry (Redis pub/sub)
- OAuth2 JWK rotation against PostgreSQL `oauth_jwk` table (JdbcJwkSource tests use H2)

## Path Forward
For full runtime QA, would need either:
- `docker/.env` with real PG credentials + reachable Redis, or
- Temporarily add H2 + redis-mock as compile-scope deps (out of scope: must-not-modify-code rule).

Integration verification proceeds at **code + test level** (see subsequent evidence files).

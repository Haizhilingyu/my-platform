# Deployment Runbook

Operations guide for deploying and running **My Platform** in production. Covers environment configuration, key rotation, database migrations, Redis, OAuth2, WebSocket load balancing, audit retention, and LDAP.

> All secrets below are shown as placeholders. Never commit real credentials. Inject them through the host `.env` file (gitignored) or your secrets manager.

---

## 1. Topology at a Glance

| Component | Host | Port | Notes |
| --- | --- | --- | --- |
| Backend (Spring Boot) | container `my-platform-backend` | 8090 (host 8095) | Java 21, port from `server.port` |
| Frontend (nginx) | container `my-platform-frontend` | 80 (host 8088) | Serves built Vue 3 dist |
| PostgreSQL | NAS `192.168.1.2` | 5532 | Database `platform` |
| Redis | NAS `192.168.1.2` | 6380 | Auth enabled on NAS |
| Nexus | NAS `192.168.1.2` | 8081 | Maven, PyPI, Docker registry |
| Docker registry | NAS `192.168.1.2` | 8082 | `platform-backend`, `platform-frontend` images |
| OpenLDAP | NAS `192.168.1.2` | 389 | Optional login method |
| Gitea + Actions | self-hosted | — | Runs `ci.yml`, `deploy.yml`, `sdk-release.yml` |

Deploy target lives at `/volume1/docker/my-platform` on the NAS. The `deploy.yml` workflow SSHes in, runs `docker compose pull && docker compose up -d`, and prints `docker compose ps`.

---

## 2. Environment Variables

Copy `docker/.env.example` to `docker/.env` and fill in real values. The compose file reads from there. Variables marked **required** will fail startup with a clear message if missing.

### Database

| Variable | Description | Default | Source |
| --- | --- | --- | --- |
| `SPRING_DATASOURCE_URL` | JDBC URL | `jdbc:postgresql://192.168.1.2:5532/platform` | `application.yml` |
| `SPRING_DATASOURCE_USERNAME` | DB user | `postgres` | `application.yml` |
| `SPRING_DATASOURCE_PASSWORD` | **Required.** DB password | `postgres` (dev only) | `.env` |

The compose file enforces `SPRING_DATASOURCE_PASSWORD` and `APP_SECURITY_JWT_SECRET` with `${VAR:?message}` so the container refuses to start without them.

### Redis

| Variable | Description | Default | Notes |
| --- | --- | --- | --- |
| `SPRING_DATA_REDIS_HOST` | Redis host | `192.168.1.2` | |
| `SPRING_DATA_REDIS_PORT` | Redis port | `6380` | |
| `REDIS_PASSWORD` | **Recommended.** Redis password | (empty) | Takes priority over the legacy var |
| `SPRING_DATA_REDIS_PASSWORD` | Legacy password var | (empty) | Kept for backward compat; `REDIS_PASSWORD` wins |

### JWT and JWK

| Variable | Description | Default | Notes |
| --- | --- | --- | --- |
| `APP_SECURITY_JWT_SECRET` | **Required.** HS256 signing key for internal `/sys/**` JWTs | built-in dev key | Must be at least 32 bytes. Use a random value in production |
| `JWK_ENCRYPTION_KEY` | AES key that encrypts RSA JWK material at rest in `openapp_jwk` | `my-platform-default-jwk-encryption-key-32b` | Override with a strong random key. Rotating it requires re-encrypting all stored keys (see section 4) |

### OAuth2 issuer

| Property | Description | Default |
| --- | --- | --- |
| `APP_OPENAPP_ISSUER` | OAuth2 Authorization Server issuer | `http://localhost:8090` |
| `APP_OPENAPP_JWK_GRACE_DAYS` | Days a retired JWK stays valid before expiry | `30` |

### LDAP (optional login method)

Off by default. Flip `PLATFORM_LOGIN_LDAP_ENABLED=true` to activate against the NAS OpenLDAP.

| Variable | Description | Default |
| --- | --- | --- |
| `PLATFORM_LOGIN_LDAP_ENABLED` | Master switch | `false` |
| `PLATFORM_LOGIN_LDAP_URL` | LDAP endpoint | `ldap://192.168.1.2:389` |
| `PLATFORM_LOGIN_LDAP_USER_DN_PATTERN` | DN template, `{0}` is the username | `uid={0},dc=devenv,dc=local` |
| `PLATFORM_LOGIN_LDAP_AUTO_CREATE_USER` | Create a local `SysUser` on first LDAP login | `true` |
| `PLATFORM_LOGIN_LDAP_DEFAULT_ROLE_CODE` | Role assigned to auto-created users | `user` |

See section 9 for full LDAP setup.

### CI and deploy credentials

These live as Gitea Action secrets, not in `.env`. They never touch the app container at runtime.

| Secret | Used by | Purpose |
| --- | --- | --- |
| `NEXUS_USER` / `NEXUS_PASS` | `deploy.yml`, `sdk-release.yml`, image build | Nexus Maven, PyPI, Docker push |
| `DEPLOY_USER` / `DEPLOY_PASS` | `deploy.yml` | SSH to the NAS for `docker compose` rollout |

---

## 3. First-Time Deploy

1. Provision PostgreSQL with an empty `platform` database and a dedicated user.
2. Provision Redis (password recommended).
3. On the deploy host, clone the repo to `/volume1/docker/my-platform`.
4. Create `docker/.env` from `docker/.env.example`. Set at minimum:
   ```
   SPRING_DATASOURCE_PASSWORD=<random>
   APP_SECURITY_JWT_SECRET=<random, 32+ bytes>
   REDIS_PASSWORD=<random>
   ```
5. Configure Gitea secrets `NEXUS_USER`, `NEXUS_PASS`, `DEPLOY_USER`, `DEPLOY_PASS`.
6. Push to `main`. The `deploy.yml` workflow builds both images, pushes them to the NAS registry at `192.168.1.2:8082`, then rolls the compose stack.

Flyway runs on first boot and creates all tables (see section 5).

---

## 4. JWK Rotation Operations

OAuth2 tokens are signed with RSA keys held in the `openapp_jwk` table. Keys are encrypted at rest with `JWK_ENCRYPTION_KEY` and cycled on a schedule by `JwkRotationService`.

### Rotation schedule

| When | What happens |
| --- | --- |
| Every Monday 03:00 (`0 0 3 ? * MON`) | A new 2048-bit RSA key is generated, stored encrypted, and marked `active`. The previous active key drops to `grace`. |
| Daily 03:30 (`0 30 3 * * *`) | Grace keys older than `APP_OPENAPP_JWK_GRACE_DAYS` are marked `expired` and reloaded out of the active set. |

The `kid` (key ID) is a random UUID per key. The Authorization Server publishes all non-expired keys at the JWK set endpoint, so existing tokens stay valid through the grace window.

### Manual rotation

Trigger an out-of-band rotation through the management endpoint or by calling the service directly. The service exposes two manual hooks:

- `rotateNow()` generates a fresh active key and demotes the current one to grace.
- `expireGraceNow()` forces expiry of overdue grace keys and returns the count purged.

Use manual rotation if you suspect a key compromise. The grace window keeps already-issued tokens working while clients refetch the JWK set.

### Key backup

The single source of truth is the `openapp_jwk` table. Back it up with a normal Postgres dump. Because `key_data` is AES-encrypted with `JWK_ENCRYPTION_KEY`, a dump alone is not enough to use the keys. You also need to back up `JWK_ENCRYPTION_KEY` itself, stored in your secrets manager, not next to the dump.

```
# example: logical backup of the JWK table
pg_dump -h 192.168.1.2 -p 5532 -U postgres -t openapp_jwk platform > openapp_jwk_$(date +%F).sql
```

### Rotating `JWK_ENCRYPTION_KEY`

This is the at-rest encryption key, not the JWT signing key. Rotating it means decrypting every `openapp_jwk.key_data` row with the old key and re-encrypting with the new one. There is no scheduled rotation for it. Plan a maintenance window, run the re-encryption against all rows, swap the env var, and restart. Keep both the old and new key during the transition in case a rollback is needed.

### kid switching and client impact

Clients fetch the JWK set by HTTP and cache it. As long as the old key stays in `grace`, it remains in the published set and tokens signed with it still validate. Clients do not need a redeploy. Only after a key moves to `expired` is it dropped from the set, and by then any token it signed is past its own lifetime.

---

## 5. Flyway and Multi-Instance Startup

Flyway is on by default (`spring.flyway.enabled=true`) with `baseline-on-migrate=true` and scripts under `classpath:db/migration`. Migrations are co-located with each module, for example `V20__audit_init.sql` and `V30__openapp_init.sql`.

### Single instance

Nothing to do. On boot, Flyway reads `flyway_schema_history`, applies pending scripts in order, then the app starts. `ddl-auto=validate` means Hibernate only checks entities against the migrated schema and never alters tables itself.

### Multiple backend instances

When more than one backend replica starts at the same time, they all try to migrate. Flyway protects the history table with a database-level advisory lock (`flyway_schema_history` row lock), so only one instance runs migrations while the others wait, retry, then proceed once the lock is free.

Recommendations for a clean multi-replica boot:

- **Stagger startup.** Bring up the first replica, wait for the health endpoint to return `UP`, then scale out. This avoids all replicas racing the lock on a cold deploy.
- **Keep `baseline-on-migrate=true`.** It lets Flyway adopt an existing schema without choking on a pre-Flyway database.
- **Do not disable Flyway on individual replicas.** All instances should run the same code path. The advisory lock makes it safe.
- **Watch for lock timeouts.** A long-running script blocks the others. If a migration takes minutes, raise the Flyway lock timeout rather than killing instances.
- **Never edit a shipped migration.** Add a new versioned file instead. Flyway checksums each script and will fail boot on a mismatch.

---

## 6. Redis Connection Troubleshooting

Redis backs the Spring Session store (T25) that keeps user sessions alive across backend instances. Losing Redis means forced logouts.

### Symptom checklist

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| Users logged out after every request | Session store unreachable | Check `REDIS_PASSWORD` and host/port |
| `NOAUTH Authentication required` in logs | Password not set or wrong var | Use `REDIS_PASSWORD`, not just `SPRING_DATA_REDIS_PASSWORD` |
| Long hangs on login | Redis connection pool exhausted | Raise pool size, check for leaked connections |
| Works locally, fails on NAS | NAS Redis requires auth, local does not | Set `REDIS_PASSWORD` in `docker/.env` |

### Quick checks

From the backend host:

```
redis-cli -h 192.168.1.2 -p 6380 -a "$REDIS_PASSWORD" PING
# expect: PONG
```

From inside the container:

```
docker compose exec backend sh -c 'echo PING | redis-cli -h $SPRING_DATA_REDIS_HOST -p $SPRING_DATA_REDIS_PORT -a "$REDIS_PASSWORD"'
```

### Variable precedence

`REDIS_PASSWORD` wins over `SPRING_DATA_REDIS_PASSWORD`. The `application.yml` line is:

```yaml
password: ${REDIS_PASSWORD:${SPRING_DATA_REDIS_PASSWORD:}}
```

If you set both, only `REDIS_PASSWORD` is used. If neither is set, the client connects without auth, which fails on the NAS Redis.

---

## 7. OAuth2 Client Secret Rotation

External applications register as OAuth2 clients in the `openapp_client` table. The `client_secret` column stores a BCrypt hash, never the plaintext. The Authorization Server (`AuthorizationServerConfig`) reads clients through `JdbcRegisteredClientRepository`.

### Rotation procedure

Rotating a client secret is a row update with a freshly BCrypt-hashed value. There is no scheduled rotation. Rotate on demand, for example on staff turnover or a suspected leak.

1. Generate a new strong secret and give it to the client application owner out of band.
2. Compute the BCrypt hash. The hash is what gets stored:
   ```
   # example using htpasswd
   htpasswd -bnBC 10 "" "$NEW_SECRET" | tr -d ':\n' | sed 's/^\$2y/\$2a/'
   ```
3. Update the row, keeping `enabled=true` so the client keeps working:
   ```sql
   UPDATE openapp_client
   SET client_secret = '$2a$10$...new bcrypt hash...',
       updated_at = NOW()
   WHERE client_id = 'the-client-id';
   ```
4. Confirm with a token request from the client using the new secret.

Because the stored value is already a hash, there is no "old and new overlap" at the database level. Coordinate the cutover with the client owner so they swap their config at the same time you write the row. If you need zero-downtime rotation, register a second client row with the new secret, let the client switch over, then disable the old one.

### Revoking a client

Set `enabled=false` instead of deleting the row. This preserves audit history and can be reversed. Active access tokens already issued to the client remain valid until they expire. To revoke them immediately, delete the matching rows from `oauth_authorization`.

---

## 8. WebSocket Load Balancing

The notify module serves a WebSocket endpoint at `/ws/notify` (configurable through `app.notify.websocket-path`). `NotifyWebSocketHandler` holds open sessions in a `WebSocketSessionRegistry` and pushes URGENT messages in real time.

The problem with more than one backend replica is that a WebSocket session is pinned to the instance that accepted it. A push sent from a different instance will not reach that session unless sessions are shared.

### Option A: Sticky sessions (simplest)

Pin each client to one backend instance at the load balancer. With nginx:

```nginx
upstream platform_backend {
    ip_hash;
    server backend1:8090;
    server backend2:8090;
}

server {
    location /ws/notify {
        proxy_pass http://platform_backend;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 3600s;
    }
}
```

`ip_hash` keeps a given client on the same instance. The downside is uneven load if your client IPs cluster behind a NAT. The upside is no extra moving parts.

### Option B: Shared session store (true HA)

For real horizontal scaling, back the `WebSocketSessionRegistry` with Redis so any instance can look up which instance holds a session and forward the push. This pairs with the Spring Session Redis backplane (T25) used for HTTP sessions. Choose this when you need a push to land no matter which replica received the WebSocket connection, or when sticky routing is unreliable.

The handshake path stays the same. The registry is what changes from an in-memory map to a Redis-backed structure.

### Health and timeouts

- Set `proxy_read_timeout` high (3600s or more) so idle connections are not killed mid-session.
- Put `/ws/notify` behind the same reverse proxy that terminates TLS for the rest of the app.
- Monitor active session count through the metrics endpoint. A sudden drop signals a load balancer or backend restart that severed connections.

---

## 9. LDAP Connection (OpenLDAP)

The `login-ldap` module adds LDAP as a password login method against the NAS OpenLDAP at `192.168.1.2:389`. It is disabled by default and must be turned on explicitly.

### Enabling LDAP

Set these in `docker/.env`:

```
PLATFORM_LOGIN_LDAP_ENABLED=true
PLATFORM_LOGIN_LDAP_URL=ldap://192.168.1.2:389
PLATFORM_LOGIN_LDAP_USER_DN_PATTERN=uid={0},dc=devenv,dc=local
PLATFORM_LOGIN_LDAP_AUTO_CREATE_USER=true
PLATFORM_LOGIN_LDAP_DEFAULT_ROLE_CODE=user
```

Restart the backend. The login page will then offer LDAP alongside the local password method.

### How binding works

The `{0}` in the user DN pattern is replaced with the submitted username, then the module attempts an LDAP bind with that DN and the submitted password. A successful bind authenticates the user. On first login, if `auto-create-user` is true and no local `SysUser` exists, one is created and assigned the `default-role-code` role. That role must already exist and be enabled in `sys_role`.

### Verifying the connection

From the backend host:

```
ldapwhoami -x -H ldap://192.168.1.2:389 \
  -D "uid=<testuser>,dc=devenv,dc=local" -W
# enter the testuser password; expect: dns:<dn>
```

If `ldapwhoami` is not available, `ldapsearch -x -H ldap://192.168.1.2:389 -b "dc=devenv,dc=local" "(uid=<testuser>)"` confirms the tree is reachable and the user DN resolves.

### Common failures

| Symptom | Cause | Fix |
| --- | --- | --- |
| Login falls back to local method silently | `PLATFORM_LOGIN_LDAP_ENABLED` not `true` | Check the env var and that the module is on the classpath |
| `unable to bind` for every user | Wrong DN pattern | Confirm the `{0}` expansion matches your tree, for example `uid={0}` vs `cn={0}` |
| First login creates user without role | `default-role-code` missing or disabled | Create or enable the role in `sys_role` |
| TLS or cert errors | LDAPS endpoint or CA not trusted | Prefer `ldap://` inside the trusted LAN, or import the CA into the JVM truststore |

LDAP is a login method only. It does not manage roles, menus, or permissions. Those stay in the local `sys_*` tables.

---

## 10. Audit Log Partitioning and Archival

The `audit_log` table (created by `V20__audit_init.sql`) is append-only. It grows without bound, so it needs a retention plan.

### Current shape

```sql
CREATE TABLE audit_log (
    id           BIGSERIAL PRIMARY KEY,
    actor        VARCHAR(64),
    action       VARCHAR(64) NOT NULL,
    target_type  VARCHAR(64),
    target_id    VARCHAR(64),
    result       VARCHAR(20) NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    ...
);
```

Indexes cover the common queries: `(actor, action, created_at)`, `(created_at)`, and `(target_type, target_id)`.

### Partitioning strategy

Partition by range on `created_at`, one partition per month. This lets you drop old partitions instantly instead of running a slow `DELETE` that bloats the table.

To convert in place on PostgreSQL:

1. Create a new partitioned parent table with the same columns and `PARTITION BY RANGE (created_at)`.
2. Create monthly partitions ahead of time, for example `audit_log_2026_07` for the current month and the next couple of months.
3. Migrate existing rows into the new table, then swap names so the parent becomes `audit_log`.
4. Add a Flyway migration that creates future partitions. A common pattern is a scheduled job (cron, pg_cron, or a small app task) that runs near month-end and creates next month's partition.

Keep the existing indexes. Recreate them on the partitioned parent, and PostgreSQL will apply them to each partition automatically.

### Archival and retention

Decide how long you need live audit data. A typical policy is 12 to 24 months online, older rows archived to cold storage.

- **Archive.** Before dropping a partition, dump it to a compressed file or object storage:
  ```
  pg_dump -h 192.168.1.2 -p 5532 -U postgres -t audit_log_2025_06 platform | gzip > audit_log_2025_06.sql.gz
  ```
- **Drop.** Once archived and verified, detach and drop the partition:
  ```sql
  ALTER TABLE audit_log DETACH PARTITION audit_log_2025_06;
  DROP TABLE audit_log_2025_06;
  ```
- **Verify the archive** before dropping. Restoring one row from the dump into a scratch schema is a cheap sanity check.

Never `DELETE` in bulk from the live table if you partition. Dropping a partition is an O(1) metadata operation. A big `DELETE` is slow, locks rows, and leaves dead tuples for autovacuum to clean up.

---

## 11. Routine Checklist

| Cadence | Task |
| --- | --- |
| Daily | Check `docker compose ps` and health endpoint on the NAS |
| Weekly | Confirm the Monday 03:00 JWK rotation ran by checking `openapp_jwk` for a new active row |
| Weekly | Verify Postgres and Redis backups succeeded |
| Monthly | Create next month's `audit_log` partition if not automated |
| Quarterly | Review `openapp_client` for stale or disabled clients |
| On demand | Rotate OAuth2 client secrets, rotate `JWK_ENCRYPTION_KEY`, archive and drop old audit partitions |

---

## 12. Rollback

To roll back a bad deploy:

1. On the NAS, set the previous image tag and pull it:
   ```
   cd /volume1/docker/my-platform
   export TAG=<previous-sha-or-tag>
   docker compose pull
   docker compose up -d --remove-orphans
   ```
2. Confirm `docker compose ps` shows the older image running.
3. If a Flyway migration shipped with the bad deploy, the database is already at the new schema. Flyway does not auto-rollback. Write a forward migration that undoes the change, or restore from a pre-deploy backup. Never edit the shipped migration file.

The `deploy.yml` workflow tags every push as both `${GIT_SHA}` and `latest`, so any prior commit is recoverable by its seven-character SHA.

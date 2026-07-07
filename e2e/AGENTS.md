# e2e/ — End-to-End Test Infrastructure

Playwright + shell-based E2E for the full stack. Spins local docker-compose, exercises UI + API + WebSocket + OAuth2 flows.

## STRUCTURE

```
e2e/
├── playwright.config.ts        # Chromium only, workers=1, baseURL :8090, retries on CI
├── global-setup.ts             # Pre-cleans E2E-* residual data from DB
├── global-teardown.ts
├── tests/                      # Numbered specs (execution order matters)
│   ├── 01-auth.spec.ts         # Login, captcha, permission denial, menu render
│   ├── 02-user.spec.ts         # User CRUD + role assignment
│   ├── 03-role.spec.ts
│   ├── 04-unit.spec.ts
│   ├── 05-menu.spec.ts
│   ├── 06-session.spec.ts      # JWT blacklist / force logout
│   ├── 07-config.spec.ts
│   ├── 08-audit.spec.ts
│   ├── 09-notify.spec.ts       # WebSocket push
│   ├── 10-openapp.spec.ts      # OAuth2 client_credentials + publish
│   └── 11-responsive.spec.ts
├── fixtures/
│   ├── auth.ts                 # uiLogin(), extractJti() helpers
│   ├── db.ts                   # Direct PostgreSQL (bypass app) — seed/assert state
│   ├── redis.ts                # Direct Redis — extract captcha answers
│   └── helpers.ts              # Shared test utilities
├── run-e2e.sh                  # Orchestrator: docker compose up → wait health → playwright
├── api-e2e.sh                  # Layer 1: curl+jq smoke (8 auth/CRUD cases)
├── api-e2e-extended.sh         # Extended API smoke
├── oauth2-e2e.sh               # OAuth2 flow shell test
└── package.json                # Playwright ^1.61, pg, ioredis
```

## WHERE TO LOOK

| Task | Location |
|---|---|
| Add new E2E test | `tests/NN-<topic>.spec.ts` (next number, serial-safe) |
| Add fixture | `fixtures/<domain>.ts` |
| Bypass app for state assert | `fixtures/db.ts` (raw SQL via `pg`) |
| Solve captcha in test | `fixtures/redis.ts` (read key, extract answer) |
| Run full suite | `bash run-e2e.sh` (auto-starts docker stack) |
| Run shell-only smoke | `bash api-e2e.sh` (requires app running) |

## CONVENTIONS (E2E-specific)

- **Numbered spec files** (`01-`, `02-`, …) — enforce execution order. Workers=1 because backend state is shared; parallel runs pollute.
- **Direct DB/Redis fixtures** — Playwright bypasses app to seed/assert state. Used for: captcha solving (Redis), permission reset (DB), residual data cleanup (`global-setup.ts`).
- **E2E-* prefix** for all test-created entities (users, roles, units) — `global-setup.ts` cleans these before each full run.
- **Dual test tooling**: Playwright (UI flows) + shell scripts (pure API smoke). Both valid; shell scripts are faster for regression.
- **Timeouts**: 15-20s per test. Captcha appearance, WebSocket message, OAuth2 token issuance all need slack.
- **Trace/screenshot/video**: captured on failure only (CI cost control).

## ANTI-PATTERNS

- **DO NOT** add parallel tests — `workers: 1` is mandatory. Backend is single-instance with shared DB.
- **DO NOT** rely solely on UI for state verification — use `fixtures/db.ts` for direct SQL assertions (faster, more reliable).
- **DO NOT** hardcode captcha values — read from Redis via `fixtures/redis.ts` (`captcha:<sessionId>`).
- **DO NOT** create users/roles without `E2E-` prefix — `global-setup.ts` cleanup is prefix-based.
- **DO NOT** skip `global-setup.ts` — residual state from prior runs will cause flaky failures.

## COMMANDS

```bash
# Full local E2E (spins postgres + redis + app automatically)
bash run-e2e.sh

# Just Playwright (assumes app already running on :8090)
cd e2e && npx playwright test

# Single spec file
cd e2e && npx playwright test tests/02-user.spec.ts

# Headed mode (debugging)
cd e2e && npm run test:headed

# View last HTML report
cd e2e && npm run report

# Shell-only API smoke (app must be running)
bash api-e2e.sh
bash oauth2-e2e.sh
```

## NOTES

- **CI gap**: `ci.yml` does NOT run Playwright. E2E is local-only via `run-e2e.sh`. Adding to CI requires docker-compose service in runner.
- **Base image**: `mcr.microsoft.com/playwright:v1.61.1-jammy` if containerizing.
- **Postgres port** (local stack): 5533 (NOT 5432). Redis: 6381. App: 8090.
- **Test artifacts** (`test-results/`, `playwright-report/`, `logs/`) are gitignored — do not commit.

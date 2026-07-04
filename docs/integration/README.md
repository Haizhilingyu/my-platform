# Integration Guide

Everything an external team needs to call the My Platform open API: get an
access token, then publish messages into the platform notification center.

The platform exposes two halves that work together:

- **Authorization server** (OAuth2 / OIDC). Issues short-lived access tokens
  that identify your application (and optionally an end user). Endpoints live
  under `/oauth2/**` and `/.well-known/**`.
- **Open API resource server**. Accepts your token and does the work. Today
  this is `POST /openapi/notify/publish`. More endpoints will land here as new
  modules expose them.

You do not have to talk to raw HTTP. We ship four SDKs that wrap the token
dance, handle refresh, and give you a typed publish call. Pick the one that
matches your stack and skip the rest.

## Where things live

| Thing | URL |
| --- | --- |
| Backend (dev) | `http://localhost:8090` |
| OIDC discovery | `http://localhost:8090/.well-known/openid-configuration` |
| OAuth2 authorization server metadata | `http://localhost:8090/.well-known/oauth-authorization-server` |
| Token endpoint | `POST http://localhost:8090/oauth2/token` |
| Authorize endpoint | `GET  http://localhost:8090/oauth2/authorize` |
| JWKS (token signing keys) | `GET  http://localhost:8090/oauth2/jwks` |
| RP-Initiated Logout | `GET  http://localhost:8090/oauth2/logout` |
| Publish message | `POST http://localhost:8090/openapi/notify/publish` |
| Nexus (private artifacts) | `http://192.168.1.2:8081` |

## Authentication overview

The platform is a standard OAuth2 / OIDC provider (Spring Authorization
Server). Two grant types cover almost every integration:

- **Client credentials**. For machine-to-machine callers: CI bots, cron jobs,
  microservices, batch importers. Your app authenticates with a
  `client_id` + `client_secret` and gets a token scoped to the permissions it
  needs. No human in the loop. See [oauth2-flow.md](oauth2-flow.md#client-credentials-flow).
- **Authorization code**. For web apps that act on behalf of a human user.
  The user logs in at the platform, consents, and your callback receives a
  code you swap for `access_token` + `refresh_token` + `id_token`. See
  [oauth2-flow.md](oauth2-flow.md#authorization-code-flow).

Both flows use `CLIENT_SECRET_BASIC` authentication (HTTP Basic with
`client_id:client_secret`). Public clients (single-page apps, mobile) should
ask the platform admin for a `client_secret`-less setup before going live.

Scopes are per-client and configured at registration time. The one most
integrations need is `notify:publish`. OIDC scopes (`openid`, `profile`,
`email`) are available for the authorization-code flow.

### Tokens are JWTs

Access tokens are signed JWTs validated by the resource server against the
public key at `/oauth2/jwks`. You do not need to validate them client side.
Just send them in the `Authorization: Bearer <token>` header. Keys are
rotated on a schedule (see `JwkRotationService`), so do not cache the JWKS
forever: respect the cache headers.

## Message center overview

`POST /openapi/notify/publish` is the entry point. You send a title, body,
urgency level, and a list of recipients. The platform resolves the recipients
to concrete users, writes a row to each user's inbox, and, for `URGENT`
messages, pushes over WebSocket to anyone online.

Recipient types:

| `type` | Meaning | `id` is |
| --- | --- | --- |
| `USER` | One specific user | `sys_user.id` |
| `ROLE` | Every user that has this role | `sys_role.id` |
| `UNIT` | Every user in this org unit, including sub-units | `sys_unit.id` |

Message levels:

| `level` | Behaviour |
| --- | --- |
| `URGENT` | Inbox write **plus** immediate WebSocket push to online users |
| `IMPORTANT` | Inbox write only. Surfaced prominently in the UI. |
| `NORMAL` | Inbox write only. |

A successful publish returns `messageId` (the created message row) and
`recipientCount` (how many inboxes were written). The call is transactional:
either every recipient gets the message, or no one does.

Full request/response details: [api-reference.md](api-reference.md).

## SDK selection guide

| SDK | Use it when | Install | Artifact |
| --- | --- | --- | --- |
| **Java** | Your service is on the JVM (Spring Boot, Quarkus, plain Java 17+). First-class Maven artifact, minimal deps (just `jackson-databind`). | Maven from Nexus | `com.example:client-sdk-java:1.0.0-SNAPSHOT` |
| **Python** | Scripts, data pipelines, ML services, anything Python 3.8+. Single runtime dep: `requests`. | pip from Nexus PyPI | `my-platform-client` |
| **Go** | Cloud-native services, CLIs, sidecars. Zero external deps, stdlib `net/http`. | `go get` | `github.com/my-platform/client-sdk-go` |
| **C** | Embedded agents, legacy systems, anything that can link a static `.a` and libcurl. Ships a bundled JSON parser. | `make lib` + link | `libplatformclient.a` |

All four SDKs expose the same shape:

1. Build a client with `issuer URL + client_id + client_secret`.
2. Get a token (`client_credentials` for machines, `exchange_code` for web).
3. Call publish. The SDK caches the token, refreshes it before expiry, and
   retries once on `401`.

If your language is not on this list, you can still integrate against raw
HTTP. The [api-reference.md](api-reference.md) and [oauth2-flow.md](oauth2-flow.md)
docs give you every URL, header, and body you need.

## Quickstart guides

Five-minute walk-throughs, end to end, with copy-pasteable code:

- [Java quickstart](quickstart-java.md)
- [Python quickstart](quickstart-python.md)
- [Go quickstart](quickstart-go.md)
- [C quickstart](quickstart-c.md)

## Reference

- [API reference](api-reference.md). Every `/openapi/**` endpoint, with
  request bodies, response shapes, and error codes.
- [OAuth2 / OIDC flows](oauth2-flow.md). Step-by-step sequences for
  authorization-code, client-credentials, and RP-Initiated Logout, with
  sequence diagrams.

## Common steps (all SDKs)

No matter which SDK you use, you do this once per integration:

1. **Register an external application.** Ask the platform admin to create a
   client for you. You will receive a `client_id` and `client_secret`. For
   the authorization-code flow you also give the admin a `redirect_uri` and
   (optionally) a `post_logout_redirect_uri`.
2. **Pick the scopes you need.** `notify:publish` for message publishing.
   Add `openid profile email` if you also want user identity via the
   authorization-code flow.
3. **Choose a grant type.** Machine caller? `client_credentials`. Acting for
   a user? `authorization_code`.
4. **Store the secret safely.** Put it in a secret manager or environment
   variable. Never check it into source control. The examples in this guide
   use `PLATFORM_CLIENT_ID` and `PLATFORM_CLIENT_SECRET` env vars.
5. **Try the quickstart for your stack.** It should print a `messageId` in
   under a minute against a running backend.

## Conventions used in these docs

- `http://localhost:8090` is the dev backend. Replace with your real issuer
  URL in production.
- `<your-client-id>` and `<your-client-secret>` are placeholders. So is
  `<your-redirect-uri>`.
- Every code sample reads secrets from environment variables. Do the same.
- HTTP examples use `curl` so you can verify behaviour without writing code.

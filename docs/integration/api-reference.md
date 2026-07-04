# API reference

This is the integration-focused reference. For exhaustive field-by-field
schemas (including internal endpoints not exposed to external apps), see the
Swagger UI at `http://localhost:8090/swagger-ui.html`.

Base URL for everything below is the platform issuer: `http://localhost:8090`
in dev. All paths are relative to the base URL.

## Conventions

- All request and response bodies are JSON (`application/json; charset=UTF-8`),
  except the token and revocation endpoints which take
  `application/x-www-form-urlencoded`.
- Dates are ISO-8601 in UTC, for example `2026-07-04T12:34:56`.
- Identifiers are 64-bit integers serialized as JSON numbers.
- Every open API response is wrapped in the standard `Result<T>` envelope
  (see below). OAuth2 token / revocation / introspection responses are raw
  per RFC and are not wrapped.

### The `Result<T>` envelope

```json
{
  "code": 200,
  "message": "success",
  "data": { /* the actual payload, type T */ }
}
```

| Field | Type | Meaning |
| --- | --- | --- |
| `code` | `int` | `200` on success. Anything else is an error. |
| `message` | `string` | `"success"` on success, or a human-readable error message. |
| `data` | `T \| null` | The payload. Omitted (or `null`) when there is nothing to return. |

## Authentication

Every `/openapi/**` request must carry a bearer access token issued by this
authorization server:

```
Authorization: Bearer <access_token>
```

Tokens are JWTs signed with RS256 / ES256 (whichever the current JWK set
uses). The resource server validates them against `/oauth2/jwks`. Tokens are
short-lived (5 minutes by default) and scoped. The scope `notify:publish` is
required to call `/openapi/notify/publish`.

How you get a token is covered in detail in [oauth2-flow.md](oauth2-flow.md).
The short version: `POST /oauth2/token` with the `client_credentials` grant
for machine callers, or with the `authorization_code` grant for web apps.

---

## Open API endpoints

### POST /openapi/notify/publish

Publish a message into the platform notification center. Resolves the
recipient specs to concrete users, writes one inbox row per user, and, when
`level` is `URGENT`, pushes the message to online users over WebSocket.

This is a transactional operation: either every resolved recipient gets the
message, or the call rolls back and no one does.

**Scope required:** `notify:publish`

**Headers:**

| Header | Required | Value |
| --- | --- | --- |
| `Authorization` | yes | `Bearer <access_token>` |
| `Content-Type` | yes | `application/json` |
| `Accept` | optional | `application/json` (default) |

**Request body:** `PublishDTO`

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `title` | `string` | yes | Non-blank. |
| `content` | `string` | yes | Non-blank. |
| `level` | `enum` | yes | One of `URGENT`, `IMPORTANT`, `NORMAL`. `URGENT` triggers WebSocket push. |
| `businessType` | `string` | no | Free-form tag for grouping and filtering in the UI. |
| `expireTime` | `string` (ISO-8601) | no | When the message should disappear from inboxes. |
| `recipients` | `array[RecipientSpec]` | yes | Non-empty. Each entry resolves to one or more users. |
| `recipients[].type` | `enum` | yes | One of `USER`, `ROLE`, `UNIT`. |
| `recipients[].id` | `int64` | yes | `sys_user.id`, `sys_role.id`, or `sys_unit.id`. `UNIT` includes sub-units. |

**Example request:**

```bash
curl -X POST http://localhost:8090/openapi/notify/publish \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Deploy failed",
    "content": "Pipeline #42 broke on the test stage.",
    "level": "URGENT",
    "businessType": "ci.pipeline",
    "recipients": [
      { "type": "USER", "id": 1 },
      { "type": "ROLE", "id": 2 }
    ]
  }'
```

**Example response (200):**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "messageId": 42,
    "recipientCount": 3
  }
}
```

**Response payload:** `PublishResult`

| Field | Type | Meaning |
| --- | --- | --- |
| `messageId` | `int64` | The id of the created `notify_message` row. |
| `recipientCount` | `int` | Number of user inboxes written. Driven by recipient expansion. |

**Error codes:**

| HTTP | `code` | When |
| --- | --- | --- |
| `200` | `200` | Success. |
| `400` | `400` | Validation failure: blank `title` / `content`, null `level`, empty `recipients`, unknown enum value, malformed JSON. `message` names the field. |
| `401` | n/a | Missing, expired, or invalid bearer token. Body is empty; the resource server rejects before reaching the controller. |
| `403` | n/a | Token is valid but lacks the `notify:publish` scope. |
| `500` | `500` | Unexpected server error. Check the backend logs. |

> The `X-Api-Key` request header is accepted but currently optional and
> ignored. Scope enforcement via OAuth2 is the production path. Treat the
> header as reserved.

---

## OAuth2 / OIDC endpoints

These are the building blocks the SDKs call on your behalf. You rarely need
to hit them by hand, but they are documented here for integrations that
cannot use an SDK, and for debugging.

### Discovery

#### GET /.well-known/openid-configuration

OIDC discovery document. Returns every other endpoint URL, the supported
grant types, scopes, and the JWKS URI.

**Example:**

```bash
curl http://localhost:8090/.well-known/openid-configuration
```

**Response (abbreviated):**

```json
{
  "issuer": "http://localhost:8090",
  "authorization_endpoint": "http://localhost:8090/oauth2/authorize",
  "token_endpoint": "http://localhost:8090/oauth2/token",
  "jwks_uri": "http://localhost:8090/oauth2/jwks",
  "userinfo_endpoint": "http://localhost:8090/userinfo",
  "end_session_endpoint": "http://localhost:8090/oauth2/logout",
  "revocation_endpoint": "http://localhost:8090/oauth2/revoke",
  "introspection_endpoint": "http://localhost:8090/oauth2/introspect",
  "grant_types_supported": [
    "authorization_code",
    "refresh_token",
    "client_credentials"
  ],
  "scopes_supported": ["openid", "profile", "email", "notify:publish"]
}
```

#### GET /.well-known/oauth-authorization-server

RFC 8414 OAuth2 server metadata. Same shape as OIDC discovery, minus the
OIDC-specific fields.

### Token

#### POST /oauth2/token

Exchange credentials for tokens. Which grant you use depends on the caller.

**Headers:**

| Header | Required | Value |
| --- | --- | --- |
| `Authorization` | yes | `Basic base64(client_id:client_secret)` |
| `Content-Type` | yes | `application/x-www-form-urlencoded` |
| `Accept` | optional | `application/json` |

**Form parameters by grant:**

| `grant_type` | Extra params | Returns `refresh_token`? |
| --- | --- | --- |
| `client_credentials` | `scope` (optional) | No |
| `authorization_code` | `code`, `redirect_uri` | Yes |
| `refresh_token` | `refresh_token`, `scope` (optional, must be subset of original) | Yes |

**Example (client_credentials):**

```bash
curl -X POST http://localhost:8090/oauth2/token \
  -u "$PLATFORM_CLIENT_ID:$PLATFORM_CLIENT_SECRET" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&scope=notify:publish"
```

**Response (200):**

```json
{
  "access_token": "eyJraWQiOi... truncated",
  "token_type": "Bearer",
  "expires_in": 300,
  "scope": "notify:publish"
}
```

For the `authorization_code` grant the response also includes `refresh_token`
and (when `openid` was requested) an `id_token`.

**Error codes:**

| HTTP | OAuth2 `error` | When |
| --- | --- | --- |
| `401` | `invalid_client` | Wrong `client_id` / `client_secret`, or Basic header malformed. |
| `400` | `unsupported_grant_type` | Unknown `grant_type`. |
| `400` | `invalid_grant` | Expired or already-used `code` / `refresh_token`. |
| `400` | `invalid_scope` | Requested scope not granted to this client. |

### Authorize

#### GET /oauth2/authorize

Browser endpoint. Starts the authorization-code flow. The user must visit
this in their browser (the platform shows the login and consent pages).
Never call this from the server side.

**Query parameters:**

| Param | Required | Value |
| --- | --- | --- |
| `response_type` | yes | `code` |
| `client_id` | yes | Your `client_id`. |
| `redirect_uri` | yes | Must match a URI registered for this client. |
| `scope` | recommended | Space-separated, for example `openid profile notify:publish`. |
| `state` | recommended | CSRF token you generate. Echoed back to the redirect. |

After login and consent, the user is redirected to `redirect_uri` with
`?code=...&state=...`. Hand the `code` to `POST /oauth2/token` to get tokens.

### JWKS

#### GET /oauth2/jwks

Public signing keys, in standard JWK Set format. Used by resource servers to
verify JWT signatures. Clients normally do not need this (the SDK handles
verification server side), but it is useful for debugging signature issues.

Keys are rotated on a schedule. Respect cache headers and refetch when a
token carries an unknown `kid`.

### UserInfo (OIDC)

#### GET /userinfo

Returns claims about the authenticated user. Requires an access token issued
via the authorization-code flow with the `openid` scope.

**Headers:** `Authorization: Bearer <access_token>`

**Response (200):** standard OIDC UserInfo claims (`sub`, `name`, `email`,
etc., depending on scopes granted).

### Revocation

#### POST /oauth2/revoke

Revoke an access or refresh token (RFC 7009). Useful when a user logs out of
your app or a token is leaked.

**Headers:** `Authorization: Basic base64(client_id:client_secret)`

**Form parameters:** `token`, `token_type_hint` (optional: `access_token` or
`refresh_token`).

Returns `200 OK` with an empty body regardless of whether the token existed.

### Introspection

#### POST /oauth2/introspect

RFC 7662 token introspection. Returns whether a token is active and its
claims.

**Headers:** `Authorization: Basic base64(client_id:client_secret)`

**Form parameters:** `token`, `token_type_hint` (optional).

**Response (200):**

```json
{
  "active": true,
  "scope": "notify:publish",
  "client_id": "demo-client",
  "exp": 1783218896,
  "iat": 1783218596,
  "sub": "demo-client",
  "token_type": "Bearer"
}
```

### RP-Initiated Logout

#### GET /oauth2/logout

OIDC RP-Initiated Logout. Ends the user's platform SSO session, then
optionally redirects back to your app.

Detailed flow including parameters, redirect rules, and the back-channel
logout webhook: see [oauth2-flow.md](oauth2-flow.md#rp-initiated-logout).

---

## Rate limiting and quotas

The platform does not currently rate-limit `/openapi/**` calls per client.
That will change before production exposure. Recommended client-side hygiene
while the policy is being finalized:

- Reuse the cached access token until 60 seconds before `expires_in`. All
  four SDKs already do this.
- Avoid tight polling loops. If you need periodic notifications, publish on
  event, not on a timer.
- Batch recipients into a single publish call rather than calling publish
  once per user.

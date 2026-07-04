# OAuth2 / OIDC flows

Step-by-step walkthrough of every flow the platform authorization server
supports, with sequence diagrams. Use this when you cannot use an SDK, when
you are wiring a custom client (e.g. another language, an API gateway), or
when you are debugging a token problem.

The server is a standard Spring Authorization Server deployment. Endpoints
follow the OAuth2 and OIDC standards, so any compliant client library will
work. The platform-specific details are scopes (`notify:publish`), the
logout endpoint (`/oauth2/logout`), and the back-channel logout webhook.

Issuer in all examples: `http://localhost:8090`. Replace with your real
issuer in production. Confirm any time by hitting discovery:

```bash
curl -s http://localhost:8090/.well-known/openid-configuration | jq .issuer
```

## Choosing a flow

| Caller | Flow | Why |
| --- | --- | --- |
| CI bot, cron job, microservice, batch importer | **Client credentials** | No human involved. The token represents the app itself. |
| Web app (server-rendered or backend-for-frontend) acting for a logged-in user | **Authorization code** | The token represents the user. You get profile info via OIDC. |
| Single-page app, mobile app | **Authorization code with PKCE** | Same as above, but the client secret is replaced by a code challenge. Ask the platform admin to register a public client. |
| Existing token about to expire | **Refresh token** | Get a new access token without re-prompting the user. Only works for grants that issue a refresh token. |
| User logs out of your app | **RP-Initiated Logout** | End the platform SSO session cleanly. |

---

## Client credentials flow

For machine-to-machine calls. Your app authenticates with a
`client_id` + `client_secret` and gets a token scoped to what the client is
allowed to do. No refresh token is issued: when the access token expires,
ask for a new one.

### Sequence

```
+----------+           +----------+                 +-------------------+
|  Client  |           | Browser  |                 | Authorization Srv |
 |  (app)  |           | (none)   |                 |  /oauth2/token    |
+----------+           +----------+                 +-------------------+
     |                                                   |
     |  POST /oauth2/token                               |
     |    grant_type=client_credentials                  |
     |    scope=notify:publish                           |
     |    Authorization: Basic base64(cid:secret)        |
     |-------------------------------------------------->|
     |                                                   |
     |                  200 OK                           |
     |    { access_token, expires_in, scope }            |
     |<--------------------------------------------------|
     |                                                   |
     |  POST /openapi/notify/publish                     |
     |    Authorization: Bearer <access_token>           |
     |-------------------------------------------------->|  (resource server)
     |                                                   |
     |                  200 OK                           |
     |    { code:200, data:{ messageId, recipientCount}} |
     |<--------------------------------------------------|
```

### Step by step

1. Build the Basic auth header. It is
   `base64("<client_id>:<client_secret>")`:

   ```bash
   BASIC=$(printf "%s:%s" "$PLATFORM_CLIENT_ID" "$PLATFORM_CLIENT_SECRET" | base64)
   ```

2. POST to the token endpoint with `grant_type=client_credentials` and the
   scope you need:

   ```bash
   TOKEN=$(curl -s -X POST http://localhost:8090/oauth2/token \
     -H "Authorization: Basic $BASIC" \
     -H "Content-Type: application/x-www-form-urlencoded" \
     -d "grant_type=client_credentials&scope=notify:publish" \
     | jq -r .access_token)
   ```

3. Use the token to call `/openapi/notify/publish`:

   ```bash
   curl -X POST http://localhost:8090/openapi/notify/publish \
     -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"title":"Hi","content":"from curl","level":"URGENT",
          "recipients":[{"type":"USER","id":1}]}'
   ```

### Notes

- The scope requested here must be a subset of the scopes the admin granted
  to your client. Asking for `notify:publish` on a client that only has
  `openid` returns HTTP 400 `invalid_scope`.
- `expires_in` is in seconds (typically 300). Refresh proactively before
  expiry. All four SDKs do this for you.
- The response has no `refresh_token`. Do not look for one.

---

## Authorization code flow

For web apps that act on behalf of a human user. The user logs in at the
platform, consents to your scopes, and your callback receives a one-time
`code` that you swap for tokens. Because the code is short-lived and bound
to your `redirect_uri`, this is the safest flow for user-facing apps.

This flow also issues a `refresh_token` and, when the `openid` scope is
requested, an `id_token` carrying the user's claims.

### Sequence

```
+--------+                                +-------------------+
|  User  |                                | Authorization Srv |
|        |                                |  /oauth2/authorize|
+--------+                                +-------------------+
     ^                                            ^
     | browser                                    |
     |                                            |  2. login + consent
     |                                            |<--------+
     |  1. redirect to /oauth2/authorize?...      |         |
     +--------------------------------------------|---------|--------+
                                                  |         |        |
                                                  |  3. 302 to redirect_uri
                                                  |         |        |  ?code=...&state=...
                                                  |---------|------->|
                                                                          |
+----------+                                 +-------------------+      |
|  Client  |                                 | Authorization Srv |      |
|  (app)   |                                 |  /oauth2/token    |      |
+----------+                                 +-------------------+      |
     |                                            ^                    |
     |  4. POST /oauth2/token                     |                    |
     |    grant_type=authorization_code           |                    |
     |    code=<from callback>                    |                    |
     |    redirect_uri=<same as step 1>           |                    |
     |    Authorization: Basic base64(cid:secret) |                    |
     |------------------------------------------->|                    |
     |                                            |                    |
     |  5. 200 OK                                 |                    |
     |    { access_token, refresh_token,          |                    |
     |      id_token, expires_in, scope }         |                    |
     |<-------------------------------------------|                    |
     |                                                                 |
     |  6. use access_token against /openapi/**                        |
     +--------------> (resource server) <------------------------------+
```

### Step by step

1. **Build the authorize URL and redirect the user's browser to it.** The
   user must visit this in their browser. Do not fetch it from the server.

   ```
   http://localhost:8090/oauth2/authorize?
     response_type=code
     &client_id=<your-client-id>
     &redirect_uri=<your-redirect-uri>
     &scope=openid%20profile%20notify:publish
     &state=<random-csrf-token>
   ```

   `state` must be an unguessable value you generate and store in the user's
   session. You will check it in step 3.

2. **User authenticates and consents at the platform.** This is handled by
   the platform login and consent pages. Your app is not involved.

3. **Platform redirects to your `redirect_uri` with `code` and `state`.** On
   your callback handler, verify that `state` matches what you stored in
   step 1. If it does not, reject the request. Then extract `code`.

4. **Exchange the code for tokens.** This is a server-to-server POST.

   ```bash
   curl -s -X POST http://localhost:8090/oauth2/token \
     -u "$PLATFORM_CLIENT_ID:$PLATFORM_CLIENT_SECRET" \
     -H "Content-Type: application/x-www-form-urlencoded" \
     -d "grant_type=authorization_code" \
     -d "code=$CODE" \
     -d "redirect_uri=$PLATFORM_REDIRECT_URI"
   ```

   The response includes `access_token`, `refresh_token`, `expires_in`,
   `scope`, and (if you asked for `openid`) an `id_token` in JWT form.

5. **Validate the id_token (OIDC only).** Verify its signature against
   `/oauth2/jwks`, check `iss` equals the issuer, check `aud` matches your
   `client_id`, check `exp` is in the future. Most OIDC libraries do this in
   one call.

6. **Call the open API.** Send `access_token` as a bearer token.

### Notes

- The `code` is single-use and expires in about 30 seconds. Exchange it
  immediately. A second attempt returns `400 invalid_grant`.
- `redirect_uri` in step 4 must match the value in step 1 exactly
  (including trailing slash and query string).
- The refresh token from this flow is long-lived. Store it securely
  (encrypted at rest). Use it to get new access tokens without re-prompting
  the user.

### With PKCE (public clients)

For single-page apps and mobile apps that cannot keep a client secret, use
PKCE. The flow is identical, with two additions:

- Before step 1, generate a random `code_verifier` (43 to 128 chars). Compute
  `code_challenge = base64url(sha256(code_verifier))` and
  `code_challenge_method = S256`.
- Add `&code_challenge=<challenge>&code_challenge_method=S256` to the
  authorize URL.
- In step 4, add `&code_verifier=<verifier>` to the token POST. The server
  hashes it and compares to the challenge.

Ask the platform admin to register your client with
`client_authentication_method=none` for PKCE.

---

## Refresh token flow

Access tokens are short-lived. When one expires, swap a refresh token for a
new access token without re-prompting the user. Only the authorization-code
flow issues refresh tokens; `client_credentials` does not.

### Sequence

```
+----------+                 +-------------------+
|  Client  |                 | Authorization Srv |
|  (app)   |                 |  /oauth2/token    |
+----------+                 +-------------------+
     |                               |
     |  POST /oauth2/token           |
     |    grant_type=refresh_token   |
     |    refresh_token=<rt>         |
     |    Authorization: Basic ...   |
     |------------------------------>|
     |                               |
     |  200 OK                       |
     |   { access_token,             |
     |     refresh_token (rotated),  |
     |     expires_in }              |
     |<------------------------------|
```

### Step by step

```bash
curl -s -X POST http://localhost:8090/oauth2/token \
  -u "$PLATFORM_CLIENT_ID:$PLATFORM_CLIENT_SECRET" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=refresh_token" \
  -d "refresh_token=$REFRESH_TOKEN" \
  -d "scope=notify:publish"
```

### Notes

- The new `refresh_token` (if returned) supersedes the old one. Replace what
  you have stored. All four SDKs handle this for you.
- The `scope` parameter is optional. If present, it must be a subset of the
  scopes granted originally. You cannot escalate.
- A `400 invalid_grant` means the refresh token was revoked, expired, or
  already used. Prompt the user to log in again via the authorization-code
  flow.

---

## RP-Initiated Logout

Ends the user's platform SSO session, then optionally redirects back to your
app. Implements OIDC RP-Initiated Logout 1.0. The endpoint is
`GET /oauth2/logout`.

### Sequence

```
+--------+                 +----------+                  +-------------------+
|  User  |                 | Client   |                  | Authorization Srv |
+--------+                 | (app)    |                  |   /oauth2/logout  |
     ^                     +----------+                  +-------------------+
     |                          |                                 ^
     |  1. click "Sign out"     |                                 |
     |------------------------->|                                 |
     |                          |                                 |
     |                          |  2. redirect to /oauth2/logout  |
     |                          |     ?id_token_hint=<id_token>   |
     |                          |     &post_logout_redirect_uri=  |
     |                          |          <your-logout-url>      |
     |                          |     (&state=<csrf>)             |
     |                          |-------------------------------->|
     |                                                            |
     |  3. platform destroys SSO session                          |
     |     (and fires back-channel logout webhooks to all apps    |
     |      where this user has an active authorization)           |
     |                                                            |
     |  4. 302 to post_logout_redirect_uri                        |
     |<-----------------------------------------------------------|
     |                                                            |
     |  5. your app shows "You are signed out"                    |
     |<-------------------------+                                 |
```

### Step by step

1. Capture the user's `id_token` during the authorization-code flow. You
   will need it for `id_token_hint`.

2. When the user clicks sign out, redirect their browser to:

   ```
   http://localhost:8090/oauth2/logout?
     id_token_hint=<id_token-from-login>
     &post_logout_redirect_uri=<your-registered-logout-url>
     &state=<random-csrf-token>
   ```

   `post_logout_redirect_uri` must exactly match a URI the admin registered
   for your client. If you omit it, the platform shows a generic
   "You are signed out" page.

3. The platform destroys the SSO session. With Spring Session backed by
   Redis, the destruction propagates to every backend replica immediately,
   so there is no risk of one replica keeping a live session after another
   replica processed the logout.

4. The platform fires back-channel logout webhooks (HTTP POST to the URL the
   admin configured per client) so other apps where this user has an active
   session can clean up too. This is automatic; you do not drive it from
   your logout call.

5. The user is redirected to your `post_logout_redirect_uri`.

### Notes

- Always pass `id_token_hint`. Without it the platform may prompt the user
  to confirm logout, which breaks headless redirects.
- `state` is echoed back to your `post_logout_redirect_uri` as a query
  parameter. Use it to prevent logout CSRF.
- This flow ends the platform SSO session, not your app's local session.
  Clear your app's cookies and tokens in addition to calling logout.
- For clients where the admin configured a logout webhook URL, the platform
  delivers a back-channel logout notification. That notification is a POST
  to the configured URL with a `logout_token` (a JWT) in the body. Validate
  its signature against `/oauth2/jwks` and invalidate the user's session in
  your app.

---

## Token lifecycle and security checklist

- Access tokens live ~5 minutes. Treat them as ephemeral. Do not store them
  in localStorage in a browser (use an HttpOnly cookie or in-memory).
- Refresh tokens are bearer tokens. Store them encrypted at rest, bound to
  the user record that owns them.
- Always use HTTPS in production. The dev URLs in this guide are HTTP only
  because the dev backend is local.
- Validate `state` on every authorization-code callback. Generate it with a
  CSPRNG, at least 16 bytes.
- For browser-based apps, prefer authorization-code with PKCE over implicit
  flow. The implicit flow is deprecated and not recommended.
- Rotate the client secret if it leaks. Ask the platform admin; the old
  secret stops working immediately.

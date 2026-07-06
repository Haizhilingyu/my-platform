# Go quickstart

Goal: from zero to a published platform message in under five minutes, using
the Go SDK.

You will register an external app, add the SDK to a module, get a token via
the `client_credentials` grant, and publish an `URGENT` message to one user.
Works on Go 1.21 and newer. No external dependencies: it uses only the
standard library.

## Prerequisites

- Go 1.21 or newer.
- A running platform backend at `http://localhost:8090` (default dev URL).
- A user id to send to. The default admin is `1`.

## Step 1. Register an external application

Ask the platform admin to create a client for you. They will return:

- `client_id`, for example `demo-client`
- `client_secret`, for example `demo-secret`
- the list of scopes granted, which must include `notify:publish`

For this quickstart you do not need a `redirect_uri`. That is only required
for the authorization-code flow (web apps acting for a user).

## Step 2. Add the SDK to your module

The module path is `github.com/my-platform/client-sdk-go`. In your own
module:

```bash
go mod init your.org/quickstart
go get github.com/my-platform/client-sdk-go
```

Behind the scenes the platform runs a private Athens / Nexus Go proxy at
`<NAS_IP>:8081`. If your environment is not already pointed at it, set:

```bash
export GOPROXY="http://<NAS_IP>:8081/repository/go-proxy/,https://proxy.golang.org,direct"
```

No proxy access? Build from source:

```bash
cd client-sdk-go
go install ./...
```

## Step 3. Set credentials in the environment

Never hard-code the secret. Export it in the shell you will run the program
from:

```bash
export PLATFORM_ISSUER="http://localhost:8090"
export PLATFORM_CLIENT_ID="<your-client-id>"
export PLATFORM_CLIENT_SECRET="<your-client-secret>"
export PLATFORM_RECIPIENT_ID="1"
```

## Step 4. Write the code

`main.go`:

```go
package main

import (
	"fmt"
	"log"
	"os"
	"strconv"

	"github.com/my-platform/client-sdk-go/platformclient"
)

func main() {
	recipientID, _ := strconv.ParseInt(os.Getenv("PLATFORM_RECIPIENT_ID"), 10, 64)
	if recipientID == 0 {
		recipientID = 1
	}

	// 1. Build the client. Reads credentials from env vars.
	clientID := os.Getenv("PLATFORM_CLIENT_ID")       // never hard-code secrets
	clientSecret := os.Getenv("PLATFORM_CLIENT_SECRET")
	issuerURL := os.Getenv("PLATFORM_ISSUER")         // empty falls back to DefaultIssuerURL

	client := platformclient.NewClient(clientID, clientSecret, issuerURL)

	// 2. Get a token using client_credentials. Scope matches what the admin granted.
	token, err := client.ClientCredentials("notify:publish")
	if err != nil {
		log.Fatalf("client_credentials: %v", err)
	}
	fmt.Printf("Got token, expires in %ds\n", token.ExpiresIn)

	// 3. Publish an URGENT message to one user.
	tm := platformclient.NewTokenManager(client, token, "notify:publish")

	req := platformclient.NewSinglePublishRequest(
		"Hello from Go SDK",
		"Sent via client_credentials + /openapi/notify/publish",
		platformclient.LevelUrgent,
		platformclient.RecipientUser,
		recipientID,
	)

	resp, err := tm.Publish(req)
	if err != nil {
		log.Fatalf("publish: %v", err)
	}
	fmt.Printf("Published: messageId=%d recipientCount=%d\n",
		resp.Result.MessageID, resp.Result.RecipientCount)
}
```

Run it:

```bash
go run main.go
```

You should see:

```
Got token, expires in 300s
Published: messageId=42 recipientCount=1
```

That is the whole integration. Log in to the platform web UI as user `1` and
the message will be in the inbox. Because the level is `URGENT`, it was also
pushed over WebSocket if the user was online.

## What the SDK does for you

The snippet above is deceptively small. The SDK is doing real work:

- Sends `POST /oauth2/token` with `grant_type=client_credentials`, client
  credentials in HTTP Basic, and `scope=notify:publish` in the form body.
- Wraps the client and token in a `TokenManager` that refreshes proactively
  before expiry, and retries once on `401` (using `refresh_token` if one is
  present, otherwise re-issuing `client_credentials`).
- Returns `ErrUnauthorized` when the server keeps refusing the token after
  the retry, so you can tell auth failures apart from network errors.

For the authorization-code flow (web apps acting for a user), swap step 2 for
`AuthorizationURL(...)` plus `ExchangeCode(...)`. Full pattern is in
[oauth2-flow.md](oauth2-flow.md#authorization-code-flow) and the SDK README.

## Going further

- Fan out to a role or unit instead of one user. Set `Recipients` directly:

  ```go
  req := &platformclient.PublishRequest{
      Title:   "Deploy",
      Content: "v1.2 out",
      Level:   platformclient.LevelUrgent,
      Recipients: []platformclient.Recipient{
          {Type: platformclient.RecipientRole, ID: 2},
          {Type: platformclient.RecipientUnit, ID: 10}, // includes sub-units
      },
  }
  ```

- Send a lower-priority message that only writes to the inbox (no push):

  ```go
  req := platformclient.NewSinglePublishRequest(
      "Weekly digest", "Nothing urgent, just FYI",
      platformclient.LevelNormal,
      platformclient.RecipientUser, recipientID,
  )
  ```

- Attach a business type so the UI can group and filter:

  ```go
  req.BusinessType = "ci.pipeline"
  ```

- Set an expiry so stale messages disappear from inboxes after a deadline:

  ```go
  req.ExpireTime = "2026-12-31T23:59:59"
  ```

## Troubleshooting

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| `client_credentials: ... 401` | Wrong `client_id` / `client_secret`, or wrong issuer URL. | Check env vars. Hit `/.well-known/oauth-authorization-server` to confirm the issuer. |
| `client_credentials: ... 400 invalid_scope` | The client was not granted `notify:publish`. | Ask the platform admin to add the scope. |
| `publish: platformclient: unauthorized (HTTP 401)` | Token was valid at issue but the client lost the scope, or clock skew. | Re-run. If it persists, the `TokenManager` already retried once; check the admin did not revoke the client. |
| `publish: ... 400` with a validation message | Missing `title`, `content`, `level`, or `recipients`. | All four fields are required. See [api-reference.md](api-reference.md#post-openapinotifypublish). |
| `dial tcp ... connect: connection refused` | Backend is not running, or wrong port. | Default is `http://localhost:8090`. |

## Full example

A ready-to-run demo lives in the SDK source tree:

```bash
cd client-sdk-go
PLATFORM_CLIENT_ID=... PLATFORM_CLIENT_SECRET=... go run example/main.go
```

Source: `client-sdk-go/example/main.go`.

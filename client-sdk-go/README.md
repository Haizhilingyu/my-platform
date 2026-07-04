# my-platform Client SDK for Go

OAuth2 + message-publish client for the my-platform open API.

## Install

```bash
go get github.com/my-platform/client-sdk-go
```

## Quick start (client_credentials)

```go
package main

import (
	"fmt"
	"log"

	"github.com/my-platform/client-sdk-go/platformclient"
)

func main() {
	c := platformclient.NewClient("client-id", "client-secret", "http://localhost:8090")

	tok, err := c.ClientCredentials("notify:publish")
	if err != nil {
		log.Fatal(err)
	}

	tm := platformclient.NewTokenManager(c, tok, "notify:publish")
	resp, err := tm.Publish(platformclient.NewSinglePublishRequest(
		"Title", "Body", platformclient.LevelUrgent,
		platformclient.RecipientUser, 1,
	))
	if err != nil {
		log.Fatal(err)
	}
	fmt.Printf("messageId=%d recipientCount=%d\n",
		resp.Result.MessageID, resp.Result.RecipientCount)
}
```

## OAuth2 flows

| Method | Grant | Notes |
|---|---|---|
| `ClientCredentials(scopes...)` | `client_credentials` | Basic-auth client credentials |
| `AuthorizationURL(redirectURI, state, scopes...)` | `authorization_code` | Build browser redirect URL |
| `ExchangeCode(code, redirectURI, scopes...)` | `authorization_code` | Swap code for token |
| `RefreshToken(refreshToken, scopes...)` | `refresh_token` | Rotate token |

## Auto-refresh

`TokenManager` wraps a `Client` + current token. On HTTP 401 from publish it
refreshes once (via `refresh_token`, falling back to `client_credentials`) and
retries. Refresh failure is returned without retry.

## Run the demo

```bash
cd client-sdk-go
PLATFORM_CLIENT_ID=... PLATFORM_CLIENT_SECRET=... go run example/main.go
```

## Tests

```bash
cd client-sdk-go && go test ./...
```

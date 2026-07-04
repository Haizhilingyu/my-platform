// Command platform-demo exercises the Go SDK end-to-end against a running
// platform backend using the client_credentials grant.
package main

import (
	"context"
	"fmt"
	"log"
	"os"
	"time"

	"github.com/my-platform/client-sdk-go/platformclient"
)

func main() {
	clientID := envOrDefault("PLATFORM_CLIENT_ID", "demo-client")
	clientSecret := envOrDefault("PLATFORM_CLIENT_SECRET", "demo-secret")
	issuerURL := envOrDefault("PLATFORM_ISSUER", platformclient.DefaultIssuerURL)
	recipientID := envOrDefaultInt("PLATFORM_RECIPIENT_ID", 1)

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	_ = ctx

	client := platformclient.NewClient(clientID, clientSecret, issuerURL)

	token, err := client.ClientCredentials("notify:publish")
	if err != nil {
		log.Fatalf("client_credentials: %v", err)
	}
	fmt.Printf("obtained access token (type=%s, expires_in=%ds)\n", token.TokenType, token.ExpiresIn)

	tm := platformclient.NewTokenManager(client, token, "notify:publish")

	req := platformclient.NewSinglePublishRequest(
		"Hello from Go SDK",
		"Sent via client_credentials + /openapi/notify/publish",
		platformclient.LevelImportant,
		platformclient.RecipientUser,
		recipientID,
	)

	resp, err := tm.Publish(req)
	if err != nil {
		log.Fatalf("publish: %v", err)
	}
	fmt.Printf("published: messageId=%d recipientCount=%d\n", resp.Result.MessageID, resp.Result.RecipientCount)
}

func envOrDefault(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

func envOrDefaultInt(key string, def int64) int64 {
	if v := os.Getenv(key); v != "" {
		var n int64
		if _, err := fmt.Sscanf(v, "%d", &n); err == nil {
			return n
		}
	}
	return def
}

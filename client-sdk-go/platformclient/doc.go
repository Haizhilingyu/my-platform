// Package platformclient provides a client SDK for the my-platform open API.
//
// It implements the OAuth2 flows (client_credentials, authorization_code,
// refresh_token) exposed by the platform's authorization server and a thin
// wrapper around the message-publish open API (POST /openapi/notify/publish).
//
// The API surface intentionally mirrors the Java and Python SDKs so that
// external integrations can switch languages with minimal friction:
//
//   - Client with clientId/clientSecret/issuerUrl
//   - ClientCredentials / ExchangeCode / RefreshToken / AuthorizationURL
//   - PublishMessage
//   - Auto-refresh on HTTP 401 with a single retry
//
// Only the Go standard library is used (net/http + encoding/json) so the SDK
// stays free of external dependencies.
package platformclient

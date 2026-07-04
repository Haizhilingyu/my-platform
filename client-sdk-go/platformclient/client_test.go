package platformclient

import (
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"sync/atomic"
	"testing"
)

type fakeBackend struct {
	mux *http.ServeMux

	tokenCalls         atomic.Int32
	publishCalls       atomic.Int32
	refreshTokenSeen   string
	lastAuthHeader     string
	lastPublishBody    string
	stagedUnauthorized atomic.Bool
	staged401Returned  atomic.Bool
}

func newFakeBackend(t *testing.T) (*fakeBackend, *httptest.Server) {
	t.Helper()
	fb := &fakeBackend{mux: http.NewServeMux()}
	fb.mux.HandleFunc("/oauth2/token", fb.handleToken)
	fb.mux.HandleFunc("/openapi/notify/publish", fb.handlePublish)
	srv := httptest.NewServer(fb.mux)
	t.Cleanup(srv.Close)
	return fb, srv
}

func (f *fakeBackend) handleToken(w http.ResponseWriter, r *http.Request) {
	f.tokenCalls.Add(1)
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	user, pass, ok := r.BasicAuth()
	if !ok || user != "cid" || pass != "secret" {
		writeJSON(nil, w, http.StatusUnauthorized, map[string]any{
			"error": "invalid_client",
		})
		return
	}
	if err := r.ParseForm(); err != nil {
		http.Error(w, "bad form", http.StatusBadRequest)
		return
	}
	grant := r.FormValue("grant_type")
	resp := map[string]any{
		"access_token": "access-" + grant,
		"token_type":   "Bearer",
		"expires_in":   3600,
		"scope":        r.FormValue("scope"),
	}
	switch grant {
	case "client_credentials":
	case "authorization_code":
		if r.FormValue("code") == "" {
			http.Error(w, "missing code", http.StatusBadRequest)
			return
		}
	case "refresh_token":
		f.refreshTokenSeen = r.FormValue("refresh_token")
		if f.refreshTokenSeen == "" {
			http.Error(w, "missing refresh_token", http.StatusBadRequest)
			return
		}
		resp["access_token"] = "access-refreshed"
	default:
		http.Error(w, "unsupported grant", http.StatusBadRequest)
		return
	}
	resp["refresh_token"] = "refresh-" + grant
	writeJSON(nil, w, http.StatusOK, resp)
}

func (f *fakeBackend) handlePublish(w http.ResponseWriter, r *http.Request) {
	f.publishCalls.Add(1)
	f.lastAuthHeader = r.Header.Get("Authorization")
	body, _ := io.ReadAll(r.Body)
	f.lastPublishBody = string(body)

	status := http.StatusOK
	if f.stagedUnauthorized.Load() && !f.staged401Returned.Swap(true) {
		status = http.StatusUnauthorized
	}

	if status == http.StatusUnauthorized {
		w.WriteHeader(http.StatusUnauthorized)
		_, _ = w.Write([]byte(`{"error":"invalid_token"}`))
		return
	}
	writeJSON(nil, w, status, map[string]any{
		"code":    200,
		"message": "success",
		"data":    map[string]any{"messageId": 42, "recipientCount": 1},
	})
}

func writeJSON(_ *testing.T, w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func TestClientCredentials(t *testing.T) {
	fb, srv := newFakeBackend(t)
	c := NewClient("cid", "secret", srv.URL)

	tok, err := c.ClientCredentials("notify:publish")
	if err != nil {
		t.Fatalf("ClientCredentials: %v", err)
	}
	if tok.AccessToken != "access-client_credentials" {
		t.Fatalf("access_token = %q, want access-client_credentials", tok.AccessToken)
	}
	if tok.TokenType != "Bearer" {
		t.Fatalf("token_type = %q", tok.TokenType)
	}
	if tok.RefreshToken == "" {
		t.Fatal("expected non-empty refresh_token")
	}
	if fb.tokenCalls.Load() != 1 {
		t.Fatalf("token calls = %d", fb.tokenCalls.Load())
	}
}

func TestAuthorizationURL(t *testing.T) {
	c := NewClient("cid", "secret", "http://issuer.example")
	got := c.AuthorizationURL("http://cb/cb", "xyz", "notify:publish", "openid")
	u, err := url.Parse(got)
	if err != nil {
		t.Fatalf("parse: %v", err)
	}
	if u.Path != "/oauth2/authorize" {
		t.Fatalf("path = %s", u.Path)
	}
	if u.Query().Get("client_id") != "cid" {
		t.Fatalf("client_id = %s", u.Query().Get("client_id"))
	}
	if u.Query().Get("state") != "xyz" {
		t.Fatalf("state = %s", u.Query().Get("state"))
	}
	if u.Query().Get("scope") != "notify:publish openid" {
		t.Fatalf("scope = %s", u.Query().Get("scope"))
	}
}

func TestExchangeCode(t *testing.T) {
	_, srv := newFakeBackend(t)
	c := NewClient("cid", "secret", srv.URL)
	tok, err := c.ExchangeCode("the-code", "http://cb/cb", "notify:publish")
	if err != nil {
		t.Fatalf("ExchangeCode: %v", err)
	}
	if tok.AccessToken != "access-authorization_code" {
		t.Fatalf("access_token = %s", tok.AccessToken)
	}
}

func TestRefreshToken(t *testing.T) {
	fb, srv := newFakeBackend(t)
	c := NewClient("cid", "secret", srv.URL)
	tok, err := c.RefreshToken("rt-123")
	if err != nil {
		t.Fatalf("RefreshToken: %v", err)
	}
	if tok.AccessToken != "access-refreshed" {
		t.Fatalf("access_token = %s", tok.AccessToken)
	}
	if fb.refreshTokenSeen != "rt-123" {
		t.Fatalf("refresh_token seen = %s", fb.refreshTokenSeen)
	}
}

func TestRefreshTokenRequiresValue(t *testing.T) {
	c := NewClient("cid", "secret", "http://x")
	if _, err := c.RefreshToken(""); err == nil {
		t.Fatal("expected error on empty refresh token")
	}
}

func TestClientCredentialsMissingCreds(t *testing.T) {
	c := NewClient("", "", "http://x")
	if _, err := c.ClientCredentials(); err == nil {
		t.Fatal("expected error on missing credentials")
	}
}

func TestPublishMessage(t *testing.T) {
	fb, srv := newFakeBackend(t)
	c := NewClient("cid", "secret", srv.URL)

	req := NewSinglePublishRequest("hi", "body", LevelUrgent, RecipientUser, 7)
	resp, err := c.PublishMessage("access-xyz", req)
	if err != nil {
		t.Fatalf("PublishMessage: %v", err)
	}
	if resp.Code != 200 {
		t.Fatalf("code = %d", resp.Code)
	}
	if resp.Result == nil || resp.Result.MessageID != 42 {
		t.Fatalf("result = %+v", resp.Result)
	}
	if fb.lastAuthHeader != "Bearer access-xyz" {
		t.Fatalf("auth header = %s", fb.lastAuthHeader)
	}
	if !strings.Contains(fb.lastPublishBody, `"recipients"`) {
		t.Fatalf("body missing recipients: %s", fb.lastPublishBody)
	}
	if !strings.Contains(fb.lastPublishBody, `"type":"USER"`) {
		t.Fatalf("body missing recipient type: %s", fb.lastPublishBody)
	}
}

func TestPublishValidation(t *testing.T) {
	c := NewClient("cid", "secret", "http://x")
	cases := []struct {
		name string
		req  *PublishRequest
	}{
		{"empty title", &PublishRequest{Content: "c", Level: LevelNormal, Recipients: []Recipient{{Type: RecipientUser, ID: 1}}}},
		{"empty content", &PublishRequest{Title: "t", Level: LevelNormal, Recipients: []Recipient{{Type: RecipientUser, ID: 1}}}},
		{"no recipients", &PublishRequest{Title: "t", Content: "c", Level: LevelNormal}},
		{"nil req", nil},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if _, err := c.PublishMessage("tok", tc.req); err == nil {
				t.Fatal("expected validation error")
			}
		})
	}
}

func TestPublishUnauthorizedReturnsSentinel(t *testing.T) {
	fb, srv := newFakeBackend(t)
	fb.stagedUnauthorized.Store(true)
	c := NewClient("cid", "secret", srv.URL)

	req := NewSinglePublishRequest("hi", "body", LevelUrgent, RecipientUser, 7)
	_, err := c.PublishMessage("access-xyz", req)
	if err == nil {
		t.Fatal("expected error")
	}
	if err != ErrUnauthorized {
		t.Fatalf("err = %v, want ErrUnauthorized", err)
	}
}

func TestTokenManagerPublishAutoRefresh(t *testing.T) {
	fb, srv := newFakeBackend(t)
	fb.stagedUnauthorized.Store(true)
	c := NewClient("cid", "secret", srv.URL)

	initial := &TokenResponse{
		AccessToken:  "access-expired",
		TokenType:    "Bearer",
		ExpiresIn:    3600,
		RefreshToken: "rt-seed",
	}
	tm := NewTokenManager(c, initial, "notify:publish")

	req := NewSinglePublishRequest("hi", "body", LevelUrgent, RecipientUser, 7)
	resp, err := tm.Publish(req)
	if err != nil {
		t.Fatalf("Publish: %v", err)
	}
	if resp.Code != 200 {
		t.Fatalf("code = %d", resp.Code)
	}
	if fb.publishCalls.Load() != 2 {
		t.Fatalf("expected 2 publish calls (initial 401 + retry), got %d", fb.publishCalls.Load())
	}
	if fb.tokenCalls.Load() != 1 {
		t.Fatalf("expected 1 token refresh call, got %d", fb.tokenCalls.Load())
	}
	if fb.refreshTokenSeen != "rt-seed" {
		t.Fatalf("refresh_token sent = %s", fb.refreshTokenSeen)
	}
	if tm.CurrentToken().AccessToken != "access-refreshed" {
		t.Fatalf("current token = %s", tm.CurrentToken().AccessToken)
	}
}

func TestTokenManagerFallsBackToClientCredentials(t *testing.T) {
	fb, srv := newFakeBackend(t)
	fb.stagedUnauthorized.Store(true)
	c := NewClient("cid", "secret", srv.URL)

	initial := &TokenResponse{
		AccessToken: "access-expired",
		TokenType:   "Bearer",
		ExpiresIn:   3600,
	}
	tm := NewTokenManager(c, initial, "notify:publish")

	req := NewSinglePublishRequest("hi", "body", LevelUrgent, RecipientUser, 7)
	if _, err := tm.Publish(req); err != nil {
		t.Fatalf("Publish: %v", err)
	}
	if fb.refreshTokenSeen != "" {
		t.Fatalf("should not have used refresh_token, got %s", fb.refreshTokenSeen)
	}
}

func TestTokenManagerRefreshFailureIsReturned(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if strings.HasSuffix(r.URL.Path, "/oauth2/token") {
			w.WriteHeader(http.StatusUnauthorized)
			_, _ = w.Write([]byte(`{"error":"invalid_client"}`))
			return
		}
		w.WriteHeader(http.StatusUnauthorized)
	}))
	t.Cleanup(srv.Close)

	c := NewClient("cid", "secret", srv.URL)
	tm := NewTokenManager(c, &TokenResponse{AccessToken: "dead", ExpiresIn: 3600}, "notify:publish")
	req := NewSinglePublishRequest("hi", "body", LevelUrgent, RecipientUser, 7)
	_, err := tm.Publish(req)
	if err == nil {
		t.Fatal("expected error after refresh failure")
	}
	if !strings.Contains(err.Error(), "auto-refresh failed") {
		t.Fatalf("err = %v", err)
	}
}

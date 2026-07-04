package platformclient

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
)

// TokenResponse mirrors the OAuth2 token-endpoint payload (RFC 6749 §5.1).
type TokenResponse struct {
	AccessToken  string `json:"access_token"`
	TokenType    string `json:"token_type"`
	ExpiresIn    int64  `json:"expires_in"`
	RefreshToken string `json:"refresh_token,omitempty"`
	Scope        string `json:"scope,omitempty"`
}

// Expiry returns the absolute expiry time of the access token, computed at
// the moment the token was received. Returns the zero time if unknown.
func (t *TokenResponse) Expiry(receivedAt time.Time) time.Time {
	if t == nil || t.ExpiresIn <= 0 {
		return time.Time{}
	}
	return receivedAt.Add(time.Duration(t.ExpiresIn) * time.Second)
}

const (
	grantClientCredentials = "client_credentials"
	grantAuthorizationCode = "authorization_code"
	grantRefreshToken      = "refresh_token"
)

// AuthorizationURL builds the browser-facing authorization-endpoint URL for
// the authorization_code flow. The user agent is redirected here, then back
// to redirectURI with an authorization `code` that ExchangeCode consumes.
func (c *Client) AuthorizationURL(redirectURI, state string, scopes ...string) string {
	q := url.Values{}
	q.Set("response_type", "code")
	q.Set("client_id", c.ClientID)
	q.Set("redirect_uri", redirectURI)
	if state != "" {
		q.Set("state", state)
	}
	if len(scopes) > 0 {
		q.Set("scope", strings.Join(scopes, " "))
	}
	return c.issuer() + "/oauth2/authorize?" + q.Encode()
}

// ExchangeCode swaps an authorization `code` for a token set.
func (c *Client) ExchangeCode(code, redirectURI string, scopes ...string) (*TokenResponse, error) {
	if code == "" {
		return nil, fmt.Errorf("platformclient: code is required")
	}
	form := url.Values{}
	form.Set("grant_type", grantAuthorizationCode)
	form.Set("code", code)
	form.Set("redirect_uri", redirectURI)
	if len(scopes) > 0 {
		form.Set("scope", strings.Join(scopes, " "))
	}
	return c.doToken(form)
}

// RefreshToken exchanges a refresh_token for a new token set.
func (c *Client) RefreshToken(refreshToken string, scopes ...string) (*TokenResponse, error) {
	if refreshToken == "" {
		return nil, fmt.Errorf("platformclient: refreshToken is required")
	}
	form := url.Values{}
	form.Set("grant_type", grantRefreshToken)
	form.Set("refresh_token", refreshToken)
	if len(scopes) > 0 {
		form.Set("scope", strings.Join(scopes, " "))
	}
	return c.doToken(form)
}

// ClientCredentials obtains a token using the client_credentials grant.
func (c *Client) ClientCredentials(scopes ...string) (*TokenResponse, error) {
	form := url.Values{}
	form.Set("grant_type", grantClientCredentials)
	if len(scopes) > 0 {
		form.Set("scope", strings.Join(scopes, " "))
	}
	return c.doToken(form)
}

func (c *Client) doToken(form url.Values) (*TokenResponse, error) {
	if c.ClientID == "" || c.ClientSecret == "" {
		return nil, fmt.Errorf("platformclient: clientId and clientSecret are required")
	}
	endpoint := c.issuer() + "/oauth2/token"

	req, err := http.NewRequest(http.MethodPost, endpoint, strings.NewReader(form.Encode()))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	req.SetBasicAuth(c.ClientID, c.ClientSecret)

	resp, err := c.httpClient().Do(req)
	if err != nil {
		return nil, fmt.Errorf("platformclient: token request failed: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("platformclient: reading token response: %w", err)
	}

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, parseTokenError(resp.StatusCode, body)
	}

	var tok TokenResponse
	if err := json.Unmarshal(body, &tok); err != nil {
		return nil, fmt.Errorf("platformclient: decoding token response: %w", err)
	}
	if tok.AccessToken == "" {
		return nil, fmt.Errorf("platformclient: token response missing access_token")
	}
	return &tok, nil
}

func parseTokenError(status int, body []byte) error {
	var raw map[string]any
	if json.Unmarshal(body, &raw); len(raw) > 0 {
		if desc, ok := raw["error_description"].(string); ok && desc != "" {
			return fmt.Errorf("platformclient: token endpoint returned %d: %s", status, desc)
		}
		if e, ok := raw["error"].(string); ok && e != "" {
			return fmt.Errorf("platformclient: token endpoint returned %d: %s", status, e)
		}
	}
	return fmt.Errorf("platformclient: token endpoint returned %d: %s", status, truncateBody(body))
}

func truncateBody(b []byte) string {
	const max = 256
	s := string(b)
	if len(s) > max {
		return s[:max] + "..."
	}
	return s
}

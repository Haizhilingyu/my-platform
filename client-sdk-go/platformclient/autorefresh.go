package platformclient

import (
	"errors"
	"fmt"
	"sync"
	"time"
)

// ErrUnauthorized is returned by PublishMessage when the server responds
// HTTP 401. TokenManager uses it to trigger a refresh + retry.
var ErrUnauthorized = errors.New("platformclient: unauthorized (HTTP 401)")

// tokenMargin avoids publishing with a token that is about to expire.
const tokenMargin = 30 * time.Second

// TokenManager owns a TokenResponse and transparently refreshes it.
//
// It implements the auto-refresh contract required by the SDK spec: on a 401
// from PublishMessage, refresh once via the stored refresh_token (falling back
// to client_credentials when no refresh_token is available) and retry. A
// refresh failure is returned to the caller without retry.
//
// TokenManager is safe for concurrent use.
type TokenManager struct {
	client *Client
	scopes []string

	mu         sync.Mutex
	token      *TokenResponse
	receivedAt time.Time
	refreshing bool
}

// NewTokenManager seeds a TokenManager with an initial token set and the
// scopes to request on refresh / client_credentials fallback.
func NewTokenManager(client *Client, initial *TokenResponse, scopes ...string) *TokenManager {
	return &TokenManager{
		client:     client,
		scopes:     scopes,
		token:      initial,
		receivedAt: time.Now(),
	}
}

// Token returns the current access token, refreshing proactively if it is
// within tokenMargin of expiry.
func (m *TokenManager) Token() (string, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.shouldRefresh() {
		if err := m.refreshLocked(); err != nil {
			return "", err
		}
	}
	return m.token.AccessToken, nil
}

// Publish posts a message, refreshing the token on HTTP 401 and retrying once.
func (m *TokenManager) Publish(req *PublishRequest) (*PublishResponse, error) {
	accessToken, err := m.Token()
	if err != nil {
		return nil, fmt.Errorf("platformclient: obtain token: %w", err)
	}

	resp, err := m.client.PublishMessage(accessToken, req)
	if err == nil {
		return resp, nil
	}
	if !errors.Is(err, ErrUnauthorized) {
		return resp, err
	}

	if rerr := m.forceRefresh(); rerr != nil {
		return resp, fmt.Errorf("platformclient: auto-refresh failed: %w", rerr)
	}

	newToken, terr := m.Token()
	if terr != nil {
		return resp, terr
	}
	return m.client.PublishMessage(newToken, req)
}

func (m *TokenManager) shouldRefresh() bool {
	if m.token == nil {
		return true
	}
	exp := m.token.Expiry(m.receivedAt)
	if exp.IsZero() {
		return false
	}
	return time.Now().Add(tokenMargin).After(exp)
}

func (m *TokenManager) forceRefresh() error {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.refreshLocked()
}

func (m *TokenManager) refreshLocked() error {
	var tok *TokenResponse
	var err error
	if m.token != nil && m.token.RefreshToken != "" {
		tok, err = m.client.RefreshToken(m.token.RefreshToken, m.scopes...)
	} else {
		tok, err = m.client.ClientCredentials(m.scopes...)
	}
	if err != nil {
		return err
	}
	m.token = tok
	m.receivedAt = time.Now()
	return nil
}

// CurrentToken returns a copy of the stored token for inspection/logging.
func (m *TokenManager) CurrentToken() *TokenResponse {
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.token == nil {
		return nil
	}
	cp := *m.token
	return &cp
}

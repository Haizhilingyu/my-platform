package platformclient

import (
	"net/http"
	"time"
)

// DefaultIssuerURL is the platform's default authorization-server issuer.
const DefaultIssuerURL = "http://localhost:8090"

const defaultHTTPTimeout = 30 * time.Second

// Client is the entry point for talking to the platform open API.
//
// Fields are exported so callers can construct it as a struct literal,
// matching the Java/Python SDK style. Use NewClient for the common case.
type Client struct {
	ClientID     string
	ClientSecret string
	IssuerURL    string
	HTTPClient   *http.Client
}

// NewClient builds a Client with sensible defaults (default issuer, default
// HTTP timeout). Pass an empty issuer to use DefaultIssuerURL.
func NewClient(clientID, clientSecret, issuerURL string) *Client {
	if issuerURL == "" {
		issuerURL = DefaultIssuerURL
	}
	return &Client{
		ClientID:     clientID,
		ClientSecret: clientSecret,
		IssuerURL:    issuerURL,
		HTTPClient:   &http.Client{Timeout: defaultHTTPTimeout},
	}
}

func (c *Client) httpClient() *http.Client {
	if c.HTTPClient == nil {
		c.HTTPClient = &http.Client{Timeout: defaultHTTPTimeout}
	}
	return c.HTTPClient
}

func (c *Client) issuer() string {
	if c.IssuerURL == "" {
		return DefaultIssuerURL
	}
	return c.IssuerURL
}

package platformclient

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
)

// MessageLevel mirrors com.example.notify.enums.MessageLevel.
type MessageLevel string

const (
	LevelUrgent    MessageLevel = "URGENT"
	LevelImportant MessageLevel = "IMPORTANT"
	LevelNormal    MessageLevel = "NORMAL"
)

// RecipientType mirrors com.example.notify.enums.RecipientType.
type RecipientType string

const (
	RecipientUser RecipientType = "USER"
	RecipientRole RecipientType = "ROLE"
	RecipientUnit RecipientType = "UNIT"
)

// Recipient is one recipient specification inside a publish request.
type Recipient struct {
	Type RecipientType `json:"type"`
	ID   int64         `json:"id"`
}

// PublishRequest is the body for POST /openapi/notify/publish.
//
// Title/Content/Level/Recipients are required by the backend. BusinessType
// and ExpireTime are optional. Recipients is an array; for the single-recipient
// convenience see NewSinglePublishRequest.
type PublishRequest struct {
	Title        string       `json:"title"`
	Content      string       `json:"content"`
	Level        MessageLevel `json:"level"`
	BusinessType string       `json:"businessType,omitempty"`
	ExpireTime   string       `json:"expireTime,omitempty"`
	Recipients   []Recipient  `json:"recipients"`
}

// NewSinglePublishRequest is a convenience constructor that maps the
// single-recipient shape (recipientType, recipientId) used by the Java/Python
// SDKs onto the backend's recipients array.
func NewSinglePublishRequest(title, content string, level MessageLevel, recipientType RecipientType, recipientID int64) *PublishRequest {
	return &PublishRequest{
		Title:      title,
		Content:    content,
		Level:      level,
		Recipients: []Recipient{{Type: recipientType, ID: recipientID}},
	}
}

// PublishResult is the `data` field of a successful publish response.
type PublishResult struct {
	MessageID      int64 `json:"messageId"`
	RecipientCount int   `json:"recipientCount"`
}

// publishEnvelope mirrors the platform's Result<T> wrapper:
// {"code":200,"message":"success","data":{...}}.
type publishEnvelope struct {
	Code    int            `json:"code"`
	Message string         `json:"message"`
	Data    *PublishResult `json:"data,omitempty"`
}

// PublishResponse is what PublishMessage returns to callers.
type PublishResponse struct {
	Code      int
	Message   string
	Result    *PublishResult
	RawStatus int
	RawBody   []byte
}

// PublishMessage posts a message to /openapi/notify/publish with the given
// Bearer access token. It does NOT auto-refresh; use TokenManager.Publish for
// transparent refresh-on-401.
func (c *Client) PublishMessage(accessToken string, req *PublishRequest) (*PublishResponse, error) {
	if accessToken == "" {
		return nil, fmt.Errorf("platformclient: accessToken is required")
	}
	if req == nil {
		return nil, fmt.Errorf("platformclient: request is required")
	}
	if err := req.validate(); err != nil {
		return nil, err
	}

	body, err := json.Marshal(req)
	if err != nil {
		return nil, fmt.Errorf("platformclient: encoding publish request: %w", err)
	}

	httpReq, err := http.NewRequest(http.MethodPost, c.issuer()+"/openapi/notify/publish", bytes.NewReader(body))
	if err != nil {
		return nil, err
	}
	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("Accept", "application/json")
	httpReq.Header.Set("Authorization", "Bearer "+accessToken)

	resp, err := c.httpClient().Do(httpReq)
	if err != nil {
		return nil, fmt.Errorf("platformclient: publish request failed: %w", err)
	}
	defer resp.Body.Close()

	rawBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("platformclient: reading publish response: %w", err)
	}

	out := &PublishResponse{RawStatus: resp.StatusCode, RawBody: rawBody}

	if resp.StatusCode == http.StatusUnauthorized {
		return out, ErrUnauthorized
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return out, fmt.Errorf("platformclient: publish returned HTTP %d: %s", resp.StatusCode, truncateBody(rawBody))
	}

	var env publishEnvelope
	if err := json.Unmarshal(rawBody, &env); err != nil {
		return out, fmt.Errorf("platformclient: decoding publish envelope: %w", err)
	}
	out.Code = env.Code
	out.Message = env.Message
	out.Result = env.Data

	if env.Code != 200 {
		return out, fmt.Errorf("platformclient: publish business code %d: %s", env.Code, env.Message)
	}
	return out, nil
}

func (r *PublishRequest) validate() error {
	if r.Title == "" {
		return fmt.Errorf("platformclient: title is required")
	}
	if r.Content == "" {
		return fmt.Errorf("platformclient: content is required")
	}
	if r.Level == "" {
		return fmt.Errorf("platformclient: level is required")
	}
	if len(r.Recipients) == 0 {
		return fmt.Errorf("platformclient: at least one recipient is required")
	}
	return nil
}

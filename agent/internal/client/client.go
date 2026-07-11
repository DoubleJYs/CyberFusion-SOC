package client

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"time"
)

const agentTokenHeader = "X-CyberFusion-Agent-Token"

type Client struct {
	BaseURL    string
	AgentToken string
	HTTP       *http.Client
}

type APIEnvelope struct {
	Code    string          `json:"code"`
	Message string          `json:"message"`
	Data    json.RawMessage `json:"data"`
}

type FIMWatchPath struct {
	ID          int64  `json:"id"`
	DisplayName string `json:"displayName"`
	WatchPath   string `json:"watchPath"`
	Purpose     string `json:"purpose"`
	Recursive   bool   `json:"recursive"`
	MaxEntries  int    `json:"maxEntries"`
	Version     int    `json:"version"`
}

// HTTPStatusError lets the durable queue distinguish a schema rejection from a
// transient transport failure without exposing request contents in logs.
type HTTPStatusError struct {
	Path       string
	StatusCode int
}

func (e HTTPStatusError) Error() string {
	return fmt.Sprintf("%s returned HTTP %d", e.Path, e.StatusCode)
}

func (e HTTPStatusError) Permanent() bool {
	return e.StatusCode >= http.StatusBadRequest && e.StatusCode < http.StatusInternalServerError &&
		e.StatusCode != http.StatusUnauthorized &&
		e.StatusCode != http.StatusForbidden &&
		e.StatusCode != http.StatusRequestTimeout &&
		e.StatusCode != http.StatusConflict &&
		e.StatusCode != http.StatusTooManyRequests
}

func New(baseURL string, agentToken string) Client {
	return Client{
		BaseURL:    baseURL,
		AgentToken: agentToken,
		HTTP: &http.Client{
			Timeout: 20 * time.Second,
		},
	}
}

func (c Client) Post(path string, payload any) error {
	body, err := json.Marshal(payload)
	if err != nil {
		return err
	}
	return c.PostRaw(path, body)
}

func (c Client) PostRaw(path string, body []byte) error {
	req, err := http.NewRequest(http.MethodPost, c.BaseURL+path, bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set(agentTokenHeader, c.AgentToken)
	resp, err := c.HTTP.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	respBody, err := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if err != nil {
		return err
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return HTTPStatusError{Path: path, StatusCode: resp.StatusCode}
	}
	var envelope APIEnvelope
	if err := json.Unmarshal(respBody, &envelope); err != nil {
		return fmt.Errorf("%s returned non-json response: %w", path, err)
	}
	if envelope.Code != "SUCCESS" {
		return fmt.Errorf("%s returned code %s: %s", path, envelope.Code, envelope.Message)
	}
	return nil
}

// FIMWatchPaths reads only directories that the platform has already published
// for this authenticated Agent. It does not give the platform a remote shell or
// arbitrary filesystem access.
func (c Client) FIMWatchPaths(agentID, osType string) ([]FIMWatchPath, error) {
	query := url.Values{"agentId": {agentID}, "osType": {osType}}
	req, err := http.NewRequest(http.MethodGet, c.BaseURL+"/soc/ingest/host/fim-watch-paths?"+query.Encode(), nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set(agentTokenHeader, c.AgentToken)
	resp, err := c.HTTP.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if err != nil {
		return nil, err
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, HTTPStatusError{Path: "/soc/ingest/host/fim-watch-paths", StatusCode: resp.StatusCode}
	}
	var envelope APIEnvelope
	if err := json.Unmarshal(body, &envelope); err != nil {
		return nil, err
	}
	if envelope.Code != "SUCCESS" {
		return nil, fmt.Errorf("fim watch path request returned code %s: %s", envelope.Code, envelope.Message)
	}
	var paths []FIMWatchPath
	if err := json.Unmarshal(envelope.Data, &paths); err != nil {
		return nil, err
	}
	return paths, nil
}

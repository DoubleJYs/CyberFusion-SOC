package client

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
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
		return fmt.Errorf("%s returned HTTP %d", path, resp.StatusCode)
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

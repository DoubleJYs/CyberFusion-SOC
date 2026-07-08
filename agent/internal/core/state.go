package core

import (
	"encoding/json"
	"os"
	"path/filepath"
	"time"
)

type State struct {
	path string
	Seen map[string]string `json:"seen"`
}

func LoadState(runtimeDir string) (State, error) {
	stateDir := filepath.Join(runtimeDir, "state")
	if err := os.MkdirAll(stateDir, 0o700); err != nil {
		return State{}, err
	}
	state := State{
		path: filepath.Join(stateDir, "dedupe.json"),
		Seen: map[string]string{},
	}
	content, err := os.ReadFile(state.path)
	if err != nil {
		if os.IsNotExist(err) {
			return state, nil
		}
		return state, err
	}
	if len(content) == 0 {
		return state, nil
	}
	if err := json.Unmarshal(content, &state); err != nil {
		return state, err
	}
	if state.Seen == nil {
		state.Seen = map[string]string{}
	}
	state.path = filepath.Join(stateDir, "dedupe.json")
	return state, nil
}

func (s State) SeenKey(key string) bool {
	if key == "" {
		return false
	}
	_, ok := s.Seen[key]
	return ok
}

func (s State) Mark(keys []string) {
	now := time.Now().UTC().Format(time.RFC3339)
	for _, key := range keys {
		if key != "" {
			s.Seen[key] = now
		}
	}
}

func (s State) Save() error {
	content, err := json.MarshalIndent(s, "", "  ")
	if err != nil {
		return err
	}
	tmp := s.path + ".tmp"
	if err := os.WriteFile(tmp, content, 0o600); err != nil {
		return err
	}
	return os.Rename(tmp, s.path)
}

package core

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"
)

type Operation struct {
	ID         string          `json:"id"`
	Path       string          `json:"path"`
	Kind       string          `json:"kind"`
	CreatedAt  string          `json:"createdAt"`
	DedupeKeys []string        `json:"dedupeKeys,omitempty"`
	Payload    json.RawMessage `json:"payload"`
}

type Queue struct {
	pendingDir  string
	rejectedDir string
	maxBytes    int64
}

type QueueStats struct {
	Depth int
	Bytes int64
}

type FlushResult struct {
	Sent     int64
	Failed   int64
	Rejected int64
}

type RawPoster interface {
	PostRaw(path string, body []byte) error
}

type permanentUploadError interface {
	Permanent() bool
}

func NewQueue(runtimeDir string, maxBytes int64) (Queue, error) {
	pending := filepath.Join(runtimeDir, "queue", "pending")
	if err := os.MkdirAll(pending, 0o700); err != nil {
		return Queue{}, err
	}
	rejected := filepath.Join(runtimeDir, "queue", "rejected")
	if err := os.MkdirAll(rejected, 0o700); err != nil {
		return Queue{}, err
	}
	return Queue{pendingDir: pending, rejectedDir: rejected, maxBytes: maxBytes}, nil
}

func (q Queue) Enqueue(kind string, path string, payload any, dedupeKeys []string) error {
	stats, err := q.Stats()
	if err != nil {
		return err
	}
	duplicatePending, err := q.hasAllPendingDedupeKeys(dedupeKeys)
	if err != nil {
		return err
	}
	if duplicatePending {
		return nil
	}
	body, err := json.Marshal(payload)
	if err != nil {
		return err
	}
	if q.maxBytes > 0 && stats.Bytes+int64(len(body)) > q.maxBytes {
		return fmt.Errorf("queue soft limit exceeded: current=%d payload=%d max=%d", stats.Bytes, len(body), q.maxBytes)
	}
	op := Operation{
		ID:         operationID(kind),
		Path:       path,
		Kind:       kind,
		CreatedAt:  time.Now().UTC().Format(time.RFC3339Nano),
		DedupeKeys: dedupeKeys,
		Payload:    body,
	}
	content, err := json.MarshalIndent(op, "", "  ")
	if err != nil {
		return err
	}
	tmp := filepath.Join(q.pendingDir, op.ID+".json.tmp")
	final := filepath.Join(q.pendingDir, op.ID+".json")
	if err := os.WriteFile(tmp, content, 0o600); err != nil {
		return err
	}
	return os.Rename(tmp, final)
}

func (q Queue) hasAllPendingDedupeKeys(keys []string) (bool, error) {
	needed := make(map[string]struct{}, len(keys))
	for _, key := range keys {
		if key != "" {
			needed[key] = struct{}{}
		}
	}
	if len(needed) == 0 {
		return false, nil
	}
	files, err := filepath.Glob(filepath.Join(q.pendingDir, "*.json"))
	if err != nil {
		return false, err
	}
	if len(files) == 0 {
		return false, nil
	}
	for _, file := range files {
		op, err := readOperation(file)
		if err != nil {
			continue
		}
		for _, key := range op.DedupeKeys {
			delete(needed, key)
			if len(needed) == 0 {
				return true, nil
			}
		}
	}
	return false, nil
}

func (q Queue) Stats() (QueueStats, error) {
	files, err := filepath.Glob(filepath.Join(q.pendingDir, "*.json"))
	if err != nil {
		return QueueStats{}, err
	}
	var stats QueueStats
	for _, file := range files {
		info, err := os.Stat(file)
		if err != nil {
			continue
		}
		stats.Depth++
		stats.Bytes += info.Size()
	}
	return stats, nil
}

func (q Queue) Flush(c RawPoster, limiter RateLimiter, state State, logger Logger) (FlushResult, State, error) {
	files, err := filepath.Glob(filepath.Join(q.pendingDir, "*.json"))
	if err != nil {
		return FlushResult{}, state, err
	}
	sort.Strings(files)
	var result FlushResult
	for _, file := range files {
		op, err := readOperation(file)
		if err != nil {
			result.Failed++
			logger.Error("queue operation decode failed", "file", file, "error", err)
			continue
		}
		if limiter != nil {
			limiter.Wait()
		}
		if err := c.PostRaw(op.Path, op.Payload); err != nil {
			if isPermanentUploadError(err) {
				if rejectErr := q.reject(file, op); rejectErr == nil {
					result.Rejected++
					logger.Error("queue operation rejected permanently", "kind", op.Kind, "path", op.Path, "error", err)
					continue
				} else {
					result.Failed++
					logger.Error("queue operation reject move failed", "file", file, "error", rejectErr)
					continue
				}
			}
			result.Failed++
			logger.Error("queue operation upload failed", "kind", op.Kind, "path", op.Path, "error", err)
			continue
		}
		state.Mark(op.DedupeKeys)
		if err := state.Save(); err != nil {
			result.Failed++
			logger.Error("state save failed after upload", "kind", op.Kind, "error", err)
			continue
		}
		if err := os.Remove(file); err != nil && !errors.Is(err, os.ErrNotExist) {
			result.Failed++
			logger.Error("queue operation remove failed", "file", file, "error", err)
			continue
		}
		result.Sent++
		logger.Info("queue operation uploaded", "kind", op.Kind, "path", op.Path)
	}
	return result, state, nil
}

func (q Queue) reject(file string, op Operation) error {
	if err := os.MkdirAll(q.rejectedDir, 0o700); err != nil {
		return err
	}
	target := filepath.Join(q.rejectedDir, op.ID+".json")
	return os.Rename(file, target)
}

func isPermanentUploadError(err error) bool {
	var permanent permanentUploadError
	return errors.As(err, &permanent) && permanent.Permanent()
}

func readOperation(file string) (Operation, error) {
	content, err := os.ReadFile(file)
	if err != nil {
		return Operation{}, err
	}
	var op Operation
	if err := json.Unmarshal(content, &op); err != nil {
		return Operation{}, err
	}
	if !strings.HasPrefix(op.Path, "/soc/") {
		return Operation{}, fmt.Errorf("unexpected API path %q", op.Path)
	}
	return op, nil
}

func operationID(kind string) string {
	safeKind := strings.Map(func(r rune) rune {
		if (r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z') || (r >= '0' && r <= '9') || r == '-' || r == '_' {
			return r
		}
		return '-'
	}, kind)
	return fmt.Sprintf("%s-%s", time.Now().UTC().Format("20060102T150405.000000000"), safeKind)
}

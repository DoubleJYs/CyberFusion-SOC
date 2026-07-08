package core

import (
	"errors"
	"testing"
)

func TestQueueSkipsOperationWhenAllDedupeKeysAreAlreadyPending(t *testing.T) {
	queue, err := NewQueue(t.TempDir(), 200<<20)
	if err != nil {
		t.Fatalf("NewQueue() error = %v", err)
	}

	payload := map[string]any{"events": []string{"evt-1"}}
	if err := queue.Enqueue("event", "/soc/ingest/host/events", payload, []string{"evt-1"}); err != nil {
		t.Fatalf("first Enqueue() error = %v", err)
	}
	if err := queue.Enqueue("event", "/soc/ingest/host/events", payload, []string{"evt-1"}); err != nil {
		t.Fatalf("duplicate Enqueue() error = %v", err)
	}

	stats, err := queue.Stats()
	if err != nil {
		t.Fatalf("Stats() error = %v", err)
	}
	if stats.Depth != 1 {
		t.Fatalf("Stats().Depth = %d, want 1", stats.Depth)
	}
}

func TestQueueKeepsOperationWhenAnyDedupeKeyIsNew(t *testing.T) {
	queue, err := NewQueue(t.TempDir(), 200<<20)
	if err != nil {
		t.Fatalf("NewQueue() error = %v", err)
	}

	if err := queue.Enqueue("event", "/soc/ingest/host/events", map[string]any{"events": []string{"evt-1"}}, []string{"evt-1"}); err != nil {
		t.Fatalf("first Enqueue() error = %v", err)
	}
	if err := queue.Enqueue("event", "/soc/ingest/host/events", map[string]any{"events": []string{"evt-1", "evt-2"}}, []string{"evt-1", "evt-2"}); err != nil {
		t.Fatalf("mixed Enqueue() error = %v", err)
	}

	stats, err := queue.Stats()
	if err != nil {
		t.Fatalf("Stats() error = %v", err)
	}
	if stats.Depth != 2 {
		t.Fatalf("Stats().Depth = %d, want 2", stats.Depth)
	}
}

func TestQueueFlushRetainsFailedOperationsAndRemovesAfterRecovery(t *testing.T) {
	runtimeDir := t.TempDir()
	queue, err := NewQueue(runtimeDir, 200<<20)
	if err != nil {
		t.Fatalf("NewQueue() error = %v", err)
	}
	state, err := LoadState(runtimeDir)
	if err != nil {
		t.Fatalf("LoadState() error = %v", err)
	}
	logger, err := NewLogger(runtimeDir)
	if err != nil {
		t.Fatalf("NewLogger() error = %v", err)
	}
	if err := queue.Enqueue("event", "/soc/ingest/host/events", map[string]any{"events": []string{"evt-1"}}, []string{"evt-1"}); err != nil {
		t.Fatalf("Enqueue(event) error = %v", err)
	}
	if err := queue.Enqueue("fim", "/soc/ingest/host/fim", map[string]any{"events": []string{"fim-1"}}, []string{"fim-1"}); err != nil {
		t.Fatalf("Enqueue(fim) error = %v", err)
	}

	failedResult, state, err := queue.Flush(fakePoster{err: errors.New("platform unavailable")}, nil, state, logger)
	if err != nil {
		t.Fatalf("Flush(failing) returned unexpected error = %v", err)
	}
	if failedResult.Sent != 0 || failedResult.Failed != 2 {
		t.Fatalf("Flush(failing) result = sent %d failed %d, want sent 0 failed 2", failedResult.Sent, failedResult.Failed)
	}
	statsAfterFailure, err := queue.Stats()
	if err != nil {
		t.Fatalf("Stats() after failing flush error = %v", err)
	}
	if statsAfterFailure.Depth != 2 {
		t.Fatalf("queue depth after failing flush = %d, want 2", statsAfterFailure.Depth)
	}

	successPoster := &recordingPoster{}
	successResult, state, err := queue.Flush(successPoster, nil, state, logger)
	if err != nil {
		t.Fatalf("Flush(success) returned unexpected error = %v", err)
	}
	if successResult.Sent != 2 || successResult.Failed != 0 {
		t.Fatalf("Flush(success) result = sent %d failed %d, want sent 2 failed 0", successResult.Sent, successResult.Failed)
	}
	statsAfterSuccess, err := queue.Stats()
	if err != nil {
		t.Fatalf("Stats() after successful flush error = %v", err)
	}
	if statsAfterSuccess.Depth != 0 {
		t.Fatalf("queue depth after successful flush = %d, want 0", statsAfterSuccess.Depth)
	}
	if !state.SeenKey("evt-1") || !state.SeenKey("fim-1") {
		t.Fatalf("state did not mark flushed dedupe keys: %#v", state.Seen)
	}
	if len(successPoster.paths) != 2 {
		t.Fatalf("uploaded paths = %d, want 2", len(successPoster.paths))
	}
}

type fakePoster struct {
	err error
}

func (p fakePoster) PostRaw(string, []byte) error {
	return p.err
}

type recordingPoster struct {
	paths []string
}

func (p *recordingPoster) PostRaw(path string, _ []byte) error {
	p.paths = append(p.paths, path)
	return nil
}

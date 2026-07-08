package main

import (
	"testing"

	"cyberfusion-agent/internal/collector"
	"cyberfusion-agent/internal/core"
	"cyberfusion-agent/internal/schema"
)

func TestEnqueueSnapshotsDoesNotGrowPendingQueueForRepeatedSnapshot(t *testing.T) {
	snapshot, err := collector.Fixture("queue-dedupe-agent", "windows")
	if err != nil {
		t.Fatalf("collector.Fixture() error = %v", err)
	}
	runtimeDir := t.TempDir()
	queue, err := core.NewQueue(runtimeDir, 200<<20)
	if err != nil {
		t.Fatalf("NewQueue() error = %v", err)
	}
	state, err := core.LoadState(runtimeDir)
	if err != nil {
		t.Fatalf("LoadState() error = %v", err)
	}

	if err := enqueueSnapshots(queue, state, []schema.Snapshot{snapshot}); err != nil {
		t.Fatalf("first enqueueSnapshots() error = %v", err)
	}
	firstStats, err := queue.Stats()
	if err != nil {
		t.Fatalf("first Stats() error = %v", err)
	}
	if firstStats.Depth == 0 {
		t.Fatal("first enqueueSnapshots() produced empty queue")
	}

	if err := enqueueSnapshots(queue, state, []schema.Snapshot{snapshot}); err != nil {
		t.Fatalf("second enqueueSnapshots() error = %v", err)
	}
	secondStats, err := queue.Stats()
	if err != nil {
		t.Fatalf("second Stats() error = %v", err)
	}
	if secondStats.Depth != firstStats.Depth {
		t.Fatalf("queue depth grew after repeated snapshot: got %d, want %d", secondStats.Depth, firstStats.Depth)
	}
}

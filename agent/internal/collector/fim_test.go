package collector

import (
	"os"
	"path/filepath"
	"testing"
	"time"
)

func TestFIMDirectoryProducesSnapshotWithoutReadingDirectoryAsFile(t *testing.T) {
	watchDir := t.TempDir()
	if err := os.WriteFile(filepath.Join(watchDir, "sample.log"), []byte("safe test content"), 0o600); err != nil {
		t.Fatalf("WriteFile() error = %v", err)
	}

	event, err := fimEvent(Options{AgentID: "directory-fim-agent", FIMPath: watchDir}, "test-host", "127.0.0.1", time.Now())
	if err != nil {
		t.Fatalf("fimEvent() error = %v", err)
	}
	if event.Action != "hash" {
		t.Fatalf("action = %q, want hash", event.Action)
	}
	if event.AfterHash == "" {
		t.Fatal("directory snapshot hash is empty")
	}
	if got := event.Attributes["contentTransferred"]; got != false {
		t.Fatalf("contentTransferred = %v, want false", got)
	}
	if got := event.Attributes["snapshotType"]; got != "directory_metadata" {
		t.Fatalf("snapshotType = %v, want directory_metadata", got)
	}
}

func TestFIMReadFailureDoesNotPreventSnapshotCollection(t *testing.T) {
	missingPath := filepath.Join(t.TempDir(), "not-present")
	snapshot, err := CollectOnce(Options{
		AgentID: "fim-failure-agent",
		OSType:  "macos",
		Profile: "full",
		FIMPath: missingPath,
	})
	if err != nil {
		t.Fatalf("CollectOnce() error = %v", err)
	}
	if len(snapshot.Assets.Assets) == 0 {
		t.Fatal("asset snapshot is missing after FIM failure")
	}
	if snapshot.Heartbeat.CollectedCount == 0 {
		t.Fatal("heartbeat collection count is empty after FIM failure")
	}
	for _, event := range snapshot.Events.Events {
		if event.EventType == "fim_collect_failed" {
			return
		}
	}
	t.Fatal("FIM failure was not retained as collector evidence")
}

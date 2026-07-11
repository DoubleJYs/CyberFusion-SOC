package collector

import (
	"os"
	"path/filepath"
	"testing"
	"time"
)

func TestAuthorizedWatchRecordsOnlyChangesAfterBaseline(t *testing.T) {
	watchDir := t.TempDir()
	stateFile := filepath.Join(t.TempDir(), "fim-state.json")
	file := filepath.Join(watchDir, "application.log")
	if err := os.WriteFile(file, []byte("first"), 0o600); err != nil {
		t.Fatal(err)
	}
	opts := Options{
		AgentID: "watch-test", OSType: "macos", FIMStateFile: stateFile,
		FIMWatchPaths: []FIMWatchPath{{ID: 1, Name: "app log", Path: watchDir, Purpose: "application_log", Recursive: true, MaxEntries: 100}},
	}
	baseline, err := collectAuthorizedFIMChanges(opts, "host", "127.0.0.1", time.Now())
	if err != nil {
		t.Fatalf("baseline error: %v", err)
	}
	if len(baseline) != 1 || baseline[0].Action != "hash" || baseline[0].Attributes["contentTransferred"] != false {
		t.Fatalf("unexpected baseline: %#v", baseline)
	}
	if err := os.WriteFile(file, []byte("second"), 0o600); err != nil {
		t.Fatal(err)
	}
	changed, err := collectAuthorizedFIMChanges(opts, "host", "127.0.0.1", time.Now())
	if err != nil {
		t.Fatalf("change scan error: %v", err)
	}
	if len(changed) != 1 || changed[0].Action != "modified" || changed[0].FilePath != file {
		t.Fatalf("expected one metadata-only modification, got %#v", changed)
	}
	if changed[0].Attributes["contentTransferred"] != false {
		t.Fatalf("contentTransferred = %v, want false", changed[0].Attributes["contentTransferred"])
	}
}

func TestAuthorizedWatchSkipsSymlinks(t *testing.T) {
	watchDir := t.TempDir()
	target := filepath.Join(t.TempDir(), "secret.txt")
	if err := os.WriteFile(target, []byte("not monitored"), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.Symlink(target, filepath.Join(watchDir, "outside-link")); err != nil {
		t.Skipf("symlink unsupported: %v", err)
	}
	entries, err := scanAuthorizedPath(FIMWatchPath{Path: watchDir, Recursive: true, MaxEntries: 20})
	if err != nil {
		t.Fatal(err)
	}
	if len(entries) != 1 {
		t.Fatalf("symlink must not be scanned, entries=%v", entries)
	}
}

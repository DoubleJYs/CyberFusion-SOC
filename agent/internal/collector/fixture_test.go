package collector

import "testing"

func TestWindowsFixtureIncludesMVPEventTypes(t *testing.T) {
	snapshot, err := Fixture("windows-fixture-test-agent", "windows")
	if err != nil {
		t.Fatalf("Fixture() error = %v", err)
	}
	eventTypes := make(map[string]bool, len(snapshot.Events.Events))
	for _, event := range snapshot.Events.Events {
		eventTypes[event.EventType] = true
	}
	for _, eventType := range []string{
		"windows_logon_failure",
		"windows_defender_detection_activity",
		"new_service_installed",
		"windows_patch_summary_observed",
		"windows_startup_summary_observed",
	} {
		if !eventTypes[eventType] {
			t.Fatalf("Windows fixture missing event type %q", eventType)
		}
	}
	if len(snapshot.FIM.Events) == 0 {
		t.Fatal("Windows fixture missing FIM event")
	}
	if len(snapshot.Baseline.Checks) == 0 {
		t.Fatal("Windows fixture missing baseline check")
	}
}

func TestMacOSFixtureIncludesMVPEventTypes(t *testing.T) {
	snapshot, err := Fixture("macos-fixture-test-agent", "macos")
	if err != nil {
		t.Fatalf("Fixture() error = %v", err)
	}
	eventTypes := make(map[string]bool, len(snapshot.Events.Events))
	for _, event := range snapshot.Events.Events {
		eventTypes[event.EventType] = true
	}
	for _, eventType := range []string{
		"macos_login_observed",
		"macos_startup_items_observed",
		"macos_system_log_summary_observed",
	} {
		if !eventTypes[eventType] {
			t.Fatalf("macOS fixture missing event type %q", eventType)
		}
	}
	if len(snapshot.FIM.Events) == 0 {
		t.Fatal("macOS fixture missing FIM event")
	}
	if len(snapshot.Baseline.Checks) == 0 {
		t.Fatal("macOS fixture missing baseline check")
	}
}

func TestFixtureRunIDCreatesDistinctQueuePayloadsForSameAgent(t *testing.T) {
	first, err := FixtureWithRunID("pressure-agent", "macos", "cycle-001")
	if err != nil {
		t.Fatalf("FixtureWithRunID(first) error = %v", err)
	}
	second, err := FixtureWithRunID("pressure-agent", "macos", "cycle-002")
	if err != nil {
		t.Fatalf("FixtureWithRunID(second) error = %v", err)
	}

	if first.Events.BatchID == second.Events.BatchID {
		t.Fatalf("event batch id did not change: %s", first.Events.BatchID)
	}
	if first.Events.Events[0].EventUID == second.Events.Events[0].EventUID {
		t.Fatalf("event uid did not change: %s", first.Events.Events[0].EventUID)
	}
	if first.FIM.Events[0].EventUID == second.FIM.Events[0].EventUID {
		t.Fatalf("fim uid did not change: %s", first.FIM.Events[0].EventUID)
	}
	if first.Baseline.Checks[0].CheckCode == second.Baseline.Checks[0].CheckCode {
		t.Fatalf("baseline check code did not change: %s", first.Baseline.Checks[0].CheckCode)
	}
}

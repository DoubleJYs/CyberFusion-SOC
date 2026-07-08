package collector

import (
	"strings"
	"time"

	"cyberfusion-agent/internal/schema"
)

func Fixture(agentID string, osType string) (schema.Snapshot, error) {
	return FixtureWithRunID(agentID, osType, "")
}

func FixtureWithRunID(agentID string, osType string, runID string) (schema.Snapshot, error) {
	if err := RequireSupportedOS(osType); err != nil {
		return schema.Snapshot{}, err
	}
	now := time.Now()
	if agentID == "" {
		agentID = osType + "-fixture-agent"
	}
	runID = sanitizeFixtureRunID(runID)
	hostname := "mac-dev-host"
	primaryIP := "192.0.2.10"
	eventUID := fixtureUID(agentID)
	if runID != "" {
		eventUID = fixtureUID(agentID + "-" + runID)
	}
	fimPath := "/tmp/cyberfusion-fixture.conf"
	if osType == "windows" {
		hostname = "win-docker-host"
		primaryIP = "192.0.2.20"
		fimPath = "C:\\ProgramData\\CyberFusion\\fixture.conf"
	}
	snapshot := baseSnapshot(agentID, osType, hostname, primaryIP, []string{primaryIP}, []string{"00:00:5e:00:53:01"}, now)
	snapshot.Registration.OSVersion = "fixture"
	snapshot.Registration.Architecture = "arm64/x64"
	snapshot.Registration.Labels = map[string]string{"fixture": "true", "platform": osType}
	if runID != "" {
		snapshot.Registration.Labels["fixtureRunId"] = runID
		applyFixtureRunID(&snapshot, runID)
	}
	snapshot.Assets.Assets[0].OSVersion = "fixture"
	snapshot.Assets.Assets[0].Facts["fixture"] = "true"
	if runID != "" {
		snapshot.Assets.Assets[0].Facts["fixtureRunId"] = runID
	}
	snapshot.Events.Events = fixtureEvents(agentID, osType, hostname, primaryIP, eventUID, now)
	snapshot.FIM.Events = []schema.FIMEvent{{
		EventUID:  eventUID + "-fim",
		Action:    "modified",
		Severity:  "medium",
		Hostname:  hostname,
		AssetIP:   primaryIP,
		FilePath:  fimPath,
		RuleName:  "Fixture FIM change",
		AfterHash: "fixture-hash",
		EventTime: schema.LocalTime(now),
		Attributes: map[string]any{
			"fixture": true,
		},
	}}
	baselineSuffix := "baseline"
	if runID != "" {
		baselineSuffix += "-" + runID
	}
	snapshot.Baseline.Checks = []schema.BaselineCheck{{
		CheckCode:   checkCode(agentID, baselineSuffix),
		Category:    "host-hardening",
		CheckItem:   "Fixture baseline check",
		AssetName:   hostname,
		AssetIP:     primaryIP,
		Result:      "failed",
		Severity:    "medium",
		PassRate:    0,
		Remediation: "Review fixture baseline finding.",
		Status:      "failed",
		CheckedAt:   schema.LocalTime(now),
		Evidence: map[string]any{
			"fixture": true,
		},
	}}
	return snapshot, nil
}

func applyFixtureRunID(snapshot *schema.Snapshot, runID string) {
	suffix := "-" + runID
	snapshot.Assets.BatchID += suffix
	snapshot.Events.BatchID += suffix
	snapshot.FIM.BatchID += suffix
	snapshot.Baseline.BatchID += suffix
}

func fixtureEvents(agentID string, osType string, hostname string, primaryIP string, eventUID string, now time.Time) []schema.HostEvent {
	if osType == "windows" {
		return []schema.HostEvent{
			fixtureEvent(agentID, osType, eventUID+"-login", "eventlog", "windows_logon_failure", "high", "WIN-4625", "Windows fixture failed logon", hostname, primaryIP, now, map[string]any{
				"channel": "Security",
				"eventId": 4625,
			}),
			fixtureEvent(agentID, osType, eventUID+"-defender", "defender", "windows_defender_detection_activity", "high", "WIN-DEFENDER-1116", "Windows fixture Defender detection", hostname, primaryIP, now, map[string]any{
				"channel": "Microsoft-Windows-Windows Defender/Operational",
				"eventId": 1116,
			}),
			fixtureEvent(agentID, osType, eventUID+"-service", "eventlog", "new_service_installed", "high", "WIN-7045", "Windows fixture service install", hostname, primaryIP, now, map[string]any{
				"channel": "System",
				"eventId": 7045,
			}),
			fixtureEvent(agentID, osType, eventUID+"-patch", "windows-agent", "windows_patch_summary_observed", "info", "WIN-PATCH-SUMMARY", "Windows fixture patch summary", hostname, primaryIP, now, map[string]any{
				"hotFixIds": []string{"KB5039299", "KB5039212"},
			}),
			fixtureEvent(agentID, osType, eventUID+"-startup", "windows-agent", "windows_startup_summary_observed", "info", "WIN-STARTUP-SUMMARY", "Windows fixture startup summary", hostname, primaryIP, now, map[string]any{
				"sampleSize": 1,
			}),
		}
	}
	return []schema.HostEvent{
		fixtureEvent(agentID, osType, eventUID+"-login", "auditlog", "macos_login_observed", "medium", "MAC-AUDITLOG", "macOS fixture login activity", hostname, primaryIP, now, map[string]any{
			"sampleSize": 1,
		}),
		fixtureEvent(agentID, osType, eventUID+"-startup", "macos-agent", "macos_startup_items_observed", "info", "MAC-LAUNCHD-STARTUP", "macOS fixture launchd startup summary", hostname, primaryIP, now, map[string]any{
			"sampleSize": 1,
			"items":      []string{"/Library/LaunchDaemons/com.cyberfusion.fixture.plist"},
		}),
		fixtureEvent(agentID, osType, eventUID+"-system-log", "macos-agent", "macos_system_log_summary_observed", "info", "MAC-SYSTEM-LOG-SUMMARY", "macOS fixture system log summary", hostname, primaryIP, now, map[string]any{
			"window":        "10m",
			"sampleExcerpt": "fixture authentication summary",
		}),
	}
}

func fixtureEvent(agentID string, osType string, eventUID string, sourceModule string, eventType string, severity string, ruleID string, ruleName string, hostname string, primaryIP string, now time.Time, raw map[string]any) schema.HostEvent {
	if raw == nil {
		raw = map[string]any{}
	}
	raw["fixture"] = true
	raw["osType"] = osType
	return schema.HostEvent{
		EventUID:     eventUID,
		SourceModule: sourceModule,
		EventType:    eventType,
		Severity:     severity,
		RuleID:       ruleID,
		RuleName:     ruleName,
		AssetName:    hostname,
		AssetIP:      primaryIP,
		Action:       "review",
		EventTime:    schema.LocalTime(now),
		Raw:          raw,
		Normalized: map[string]any{
			"fixture": true,
			"agentId": agentID,
		},
	}
}

func fixtureUID(agentID string) string {
	value := strings.ToUpper(strings.ReplaceAll(agentID, "_", "-"))
	if len(value) > 96 {
		value = value[:96]
	}
	return value + "-EVENT-0001"
}

func sanitizeFixtureRunID(value string) string {
	value = strings.TrimSpace(value)
	if value == "" {
		return ""
	}
	var builder strings.Builder
	for _, r := range value {
		if (r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z') || (r >= '0' && r <= '9') || r == '-' || r == '_' {
			builder.WriteRune(r)
		} else {
			builder.WriteByte('-')
		}
	}
	out := strings.Trim(builder.String(), "-_")
	if len(out) > 32 {
		out = out[:32]
	}
	return out
}

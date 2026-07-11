package collector

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
	"net"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"time"

	"cyberfusion-agent/internal/schema"
)

type Options struct {
	AgentID       string
	AgentVersion  string
	OSType        string
	Profile       string
	FIMPath       string
	FIMWatchPaths []FIMWatchPath
	FIMStateFile  string
}

func CollectOnce(opts Options) (schema.Snapshot, error) {
	now := time.Now()
	hostname, _ := os.Hostname()
	if hostname == "" {
		hostname = "unknown-host"
	}
	ips, macs := interfaceFacts()
	primaryIP := firstOr(ips, "127.0.0.1")
	snapshot := baseSnapshot(opts, hostname, primaryIP, ips, macs, now)
	snapshot.Registration.OSVersion = runtime.GOOS
	snapshot.Registration.Architecture = runtime.GOARCH
	snapshot.Registration.Labels = map[string]string{"collector": "go-agent", "mode": "collect", "profile": collectionProfile(opts.Profile)}
	snapshot.Assets.Assets[0].Facts["runtimeGoos"] = runtime.GOOS
	snapshot.Assets.Assets[0].Facts["runtimeArch"] = runtime.GOARCH
	snapshot.Assets.Assets[0].Facts["collectionProfile"] = collectionProfile(opts.Profile)
	if userName := currentUsername(); userName != "" {
		snapshot.Assets.Assets[0].Facts["currentUser"] = userName
	}
	snapshot.Events.Events = append(snapshot.Events.Events, schema.HostEvent{
		EventUID:     stableUID(opts.AgentID, "inventory", hostname),
		SourceModule: opts.OSType + "-agent",
		EventType:    "host_inventory_observed",
		Severity:     "info",
		RuleID:       "HOST-INVENTORY",
		RuleName:     "Host inventory observed by CyberFusion Agent",
		AssetName:    hostname,
		AssetIP:      primaryIP,
		Action:       "record",
		EventTime:    schema.LocalTime(now),
		Raw: map[string]any{
			"osType":        opts.OSType,
			"ipCount":       len(ips),
			"macCount":      len(macs),
			"collectorMode": "collect",
		},
		Normalized: map[string]any{
			"agentId": opts.AgentID,
			"source":  "cyberfusion-agent",
		},
	})
	snapshot.Events.Events = append(snapshot.Events.Events, listeningPortEvents(opts, hostname, primaryIP, now)...)
	snapshot.Events.Events = append(snapshot.Events.Events, macOSCollectorEvents(opts, hostname, primaryIP, now)...)
	snapshot.Events.Events = append(snapshot.Events.Events, windowsCollectorEvents(opts, hostname, primaryIP, now)...)
	if processEvent, ok := processSummaryEvent(opts, hostname, primaryIP, now); ok {
		snapshot.Events.Events = append(snapshot.Events.Events, processEvent)
	}
	if len(opts.FIMWatchPaths) > 0 {
		fimEvents, err := collectAuthorizedFIMChanges(opts, hostname, primaryIP, now)
		if err != nil {
			snapshot.Events.Events = append(snapshot.Events.Events,
				collectorErrorEvent(opts, hostname, primaryIP, now, "fim_collect_failed", "authorized_watch_paths", err))
		} else {
			snapshot.FIM.Events = append(snapshot.FIM.Events, fimEvents...)
		}
	} else if opts.FIMPath != "" {
		fim, err := fimEvent(opts, hostname, primaryIP, now)
		if err != nil {
			// FIM permission and file-system errors are evidence, not a reason to suppress
			// the Agent heartbeat, asset inventory, and other independent collectors.
			snapshot.Events.Events = append(snapshot.Events.Events,
				collectorErrorEvent(opts, hostname, primaryIP, now, "fim_collect_failed", opts.FIMPath, err))
		} else {
			snapshot.FIM.Events = append(snapshot.FIM.Events, fim)
		}
	}
	snapshot.Events.Events = uniqueHostEvents(snapshot.Events.Events)
	snapshot.Baseline.Checks = append(snapshot.Baseline.Checks, schema.BaselineCheck{
		CheckCode:   checkCode(opts.AgentID, "agent-runtime"),
		Category:    "agent-health",
		CheckItem:   "CyberFusion Agent runtime collector available",
		AssetName:   hostname,
		AssetIP:     primaryIP,
		Result:      "pass",
		Severity:    "info",
		PassRate:    100,
		Remediation: "Keep the Agent running and monitor heartbeat.",
		Status:      "passed",
		CheckedAt:   schema.LocalTime(now),
		Evidence: map[string]any{
			"goos":   runtime.GOOS,
			"goarch": runtime.GOARCH,
		},
	})
	snapshot.Baseline.Checks = append(snapshot.Baseline.Checks, baselineChecks(opts, hostname, primaryIP, now)...)
	applyCollectionProfile(&snapshot, collectionProfile(opts.Profile))
	snapshot.Heartbeat.CollectedCount = int64(len(snapshot.Assets.Assets) + len(snapshot.Events.Events) + len(snapshot.FIM.Events) + len(snapshot.Baseline.Checks))
	return snapshot, nil
}

func uniqueHostEvents(events []schema.HostEvent) []schema.HostEvent {
	seen := make(map[string]struct{}, len(events))
	out := make([]schema.HostEvent, 0, len(events))
	for _, event := range events {
		if event.EventUID == "" {
			out = append(out, event)
			continue
		}
		if _, ok := seen[event.EventUID]; ok {
			continue
		}
		seen[event.EventUID] = struct{}{}
		out = append(out, event)
	}
	return out
}

func baseSnapshot(opts Options, hostname string, primaryIP string, ips []string, macs []string, now time.Time) schema.Snapshot {
	collectedAt := schema.LocalTime(now)
	agentVersion := opts.AgentVersion
	if agentVersion == "" {
		agentVersion = schema.Version
	}
	return schema.Snapshot{
		Registration: schema.AgentRegistration{
			AgentID:      opts.AgentID,
			AgentName:    hostname,
			Hostname:     hostname,
			OSType:       opts.OSType,
			AgentVersion: agentVersion,
			IPAddresses:  ips,
			MACAddresses: macs,
		},
		Heartbeat: schema.Heartbeat{
			AgentID:        opts.AgentID,
			Hostname:       hostname,
			OSType:         opts.OSType,
			AgentVersion:   agentVersion,
			IPAddresses:    ips,
			QueueDepth:     0,
			QueueBytes:     0,
			CollectedCount: 0,
			SentCount:      0,
			FailedCount:    0,
			UptimeSeconds:  0,
			ObservedAt:     collectedAt,
		},
		Assets: schema.AssetIngest{
			AgentID:     opts.AgentID,
			BatchID:     "HOST-" + opts.AgentID + "-ASSET",
			OSType:      opts.OSType,
			CollectedAt: collectedAt,
			Assets: []schema.Asset{{
				Hostname:     hostname,
				PrimaryIP:    primaryIP,
				OSType:       opts.OSType,
				IPAddresses:  ips,
				MACAddresses: macs,
				Facts:        map[string]string{"source": "cyberfusion-agent"},
				ObservedAt:   collectedAt,
			}},
		},
		Events: schema.EventIngest{
			AgentID:     opts.AgentID,
			BatchID:     "HOST-" + opts.AgentID + "-EVENT",
			OSType:      opts.OSType,
			CollectedAt: collectedAt,
		},
		FIM: schema.FIMIngest{
			AgentID:     opts.AgentID,
			BatchID:     "HOST-" + opts.AgentID + "-FIM",
			OSType:      opts.OSType,
			CollectedAt: collectedAt,
		},
		Baseline: schema.BaselineIngest{
			AgentID:     opts.AgentID,
			BatchID:     "HOST-" + opts.AgentID + "-BASELINE",
			OSType:      opts.OSType,
			CollectedAt: collectedAt,
		},
	}
}

func collectionProfile(profile string) string {
	switch profile {
	case "host-log", "patrol-audit", "file-integrity", "baseline-audit":
		return profile
	default:
		return "full"
	}
}

func applyCollectionProfile(snapshot *schema.Snapshot, profile string) {
	switch profile {
	case "host-log":
		snapshot.FIM.Events = nil
		snapshot.Baseline.Checks = nil
	case "patrol-audit":
		snapshot.FIM.Events = nil
	case "file-integrity":
		snapshot.Events.Events = nil
	case "baseline-audit":
		snapshot.Events.Events = nil
		snapshot.FIM.Events = nil
	}
}

func interfaceFacts() ([]string, []string) {
	var ips []string
	var macs []string
	ifaces, err := net.Interfaces()
	if err != nil {
		return nil, nil
	}
	for _, iface := range ifaces {
		if iface.Flags&net.FlagLoopback != 0 || iface.Flags&net.FlagUp == 0 {
			continue
		}
		if len(iface.HardwareAddr) > 0 {
			macs = append(macs, iface.HardwareAddr.String())
		}
		addrs, err := iface.Addrs()
		if err != nil {
			continue
		}
		for _, addr := range addrs {
			var ip net.IP
			switch v := addr.(type) {
			case *net.IPNet:
				ip = v.IP
			case *net.IPAddr:
				ip = v.IP
			}
			if ip == nil || ip.IsLoopback() {
				continue
			}
			if ip4 := ip.To4(); ip4 != nil {
				ips = append(ips, ip4.String())
			}
		}
	}
	return unique(ips), unique(macs)
}

func fimEvent(opts Options, hostname string, primaryIP string, now time.Time) (schema.FIMEvent, error) {
	info, err := os.Stat(opts.FIMPath)
	if err != nil {
		return schema.FIMEvent{}, err
	}
	if info.IsDir() {
		return fimDirectoryEvent(opts, hostname, primaryIP, now)
	}
	file, err := os.Open(opts.FIMPath)
	if err != nil {
		return schema.FIMEvent{}, err
	}
	defer file.Close()
	hash := sha256.New()
	if _, err := io.Copy(hash, io.LimitReader(file, 64<<20)); err != nil {
		return schema.FIMEvent{}, err
	}
	sum := hex.EncodeToString(hash.Sum(nil))
	return schema.FIMEvent{
		EventUID:  stableUID(opts.AgentID, "fim", opts.FIMPath, sum),
		Action:    "hash",
		Severity:  "info",
		Hostname:  hostname,
		AssetIP:   primaryIP,
		FilePath:  opts.FIMPath,
		RuleName:  "CyberFusion Agent watched file hash",
		AfterHash: sum,
		EventTime: schema.LocalTime(now),
		Attributes: map[string]any{
			"hashAlgorithm": "sha256",
			"collector":     "cyberfusion-agent",
		},
	}, nil
}

// fimDirectoryEvent hashes bounded immediate-entry metadata without uploading file names or contents.
// The installation UI accepts directories as FIM targets, so a directory must never prevent a cycle heartbeat.
func fimDirectoryEvent(opts Options, hostname string, primaryIP string, now time.Time) (schema.FIMEvent, error) {
	entries, err := os.ReadDir(opts.FIMPath)
	if err != nil {
		return schema.FIMEvent{}, err
	}
	const maxEntries = 256
	hash := sha256.New()
	_, _ = io.WriteString(hash, "cyberfusion-directory-snapshot\n")
	_, _ = io.WriteString(hash, opts.FIMPath+"\n")
	count := len(entries)
	if count > maxEntries {
		entries = entries[:maxEntries]
	}
	for _, entry := range entries {
		entryInfo, infoErr := entry.Info()
		if infoErr != nil {
			_, _ = io.WriteString(hash, entry.Name()+"|unreadable\n")
			continue
		}
		// Entry names are intentionally included only in the local hash, never in the payload.
		_, _ = fmt.Fprintf(hash, "%s|%t|%d|%d\n", entry.Name(), entry.IsDir(), entryInfo.Size(), entryInfo.ModTime().UnixNano())
	}
	sum := hex.EncodeToString(hash.Sum(nil))
	return schema.FIMEvent{
		EventUID: stableUID(opts.AgentID, "fim-directory", opts.FIMPath, sum),
		// The backend FIM contract accepts hash as the normalized action. The rule and
		// attributes retain that this is a directory metadata snapshot.
		Action:    "hash",
		Severity:  "info",
		Hostname:  hostname,
		AssetIP:   primaryIP,
		FilePath:  opts.FIMPath,
		RuleName:  "CyberFusion Agent watched directory snapshot",
		AfterHash: sum,
		EventTime: schema.LocalTime(now),
		Attributes: map[string]any{
			"snapshotType":       "directory_metadata",
			"hashAlgorithm":      "sha256",
			"collector":          "cyberfusion-agent",
			"entryCount":         count,
			"sampledEntryCount":  len(entries),
			"entriesTruncated":   count > maxEntries,
			"contentTransferred": false,
		},
	}, nil
}

func listeningPortEvents(opts Options, hostname string, primaryIP string, now time.Time) []schema.HostEvent {
	if opts.OSType != "macos" && opts.OSType != "linux" {
		return nil
	}
	output, err := runCommand(2*time.Second, "lsof", "-nP", "-iTCP", "-sTCP:LISTEN")
	if err != nil {
		return []schema.HostEvent{collectorErrorEvent(opts, hostname, primaryIP, now, "listening_port_collect_failed", "lsof", err)}
	}
	lines := strings.Split(output, "\n")
	events := make([]schema.HostEvent, 0, 16)
	for i, line := range lines {
		if i == 0 || strings.TrimSpace(line) == "" {
			continue
		}
		fields := strings.Fields(line)
		if len(fields) < 9 {
			continue
		}
		command := fields[0]
		pid := fields[1]
		runUser := fields[2]
		name := strings.Join(fields[8:], " ")
		events = append(events, schema.HostEvent{
			EventUID:     stableUID(opts.AgentID, "listen", pid, command, name),
			SourceModule: opts.OSType + "-agent",
			EventType:    "listening_port_observed",
			Severity:     "info",
			RuleID:       "HOST-LISTENING-PORT",
			RuleName:     "Listening port observed by CyberFusion Agent",
			AssetName:    hostname,
			AssetIP:      primaryIP,
			Action:       "record",
			EventTime:    schema.LocalTime(now),
			Raw: map[string]any{
				"process": command,
				"pid":     pid,
				"user":    runUser,
				"name":    name,
			},
			Normalized: map[string]any{
				"agentId": opts.AgentID,
				"source":  "cyberfusion-agent",
			},
		})
		if len(events) >= 50 {
			break
		}
	}
	return events
}

func macOSCollectorEvents(opts Options, hostname string, primaryIP string, now time.Time) []schema.HostEvent {
	if opts.OSType != "macos" {
		return nil
	}
	return []schema.HostEvent{
		macOSStartupSummary(opts, hostname, primaryIP, now),
		macOSSystemLogSummary(opts, hostname, primaryIP, now),
	}
}

func macOSStartupSummary(opts Options, hostname string, primaryIP string, now time.Time) schema.HostEvent {
	items, counts := collectLaunchdItems()
	return schema.HostEvent{
		EventUID:     stableUID(opts.AgentID, "macos-startup-summary", strings.Join(items, ",")),
		SourceModule: "macos-agent",
		EventType:    "macos_startup_items_observed",
		Severity:     "info",
		RuleID:       "MAC-LAUNCHD-STARTUP",
		RuleName:     "macOS launchd startup items observed by CyberFusion Agent",
		AssetName:    hostname,
		AssetIP:      primaryIP,
		Action:       "record",
		EventTime:    schema.LocalTime(now),
		Raw: map[string]any{
			"sampleSize": len(items),
			"items":      items,
			"counts":     counts,
		},
		Normalized: map[string]any{
			"agentId": opts.AgentID,
			"source":  "cyberfusion-agent",
		},
	}
}

func collectLaunchdItems() ([]string, map[string]int) {
	paths := []string{
		"/Library/LaunchDaemons",
		"/Library/LaunchAgents",
		"/System/Library/LaunchDaemons",
		"/System/Library/LaunchAgents",
	}
	if home, err := os.UserHomeDir(); err == nil && home != "" {
		paths = append(paths, filepath.Join(home, "Library", "LaunchAgents"))
	}
	items := make([]string, 0, 30)
	counts := make(map[string]int, len(paths))
	for _, dir := range paths {
		entries, err := os.ReadDir(dir)
		if err != nil {
			continue
		}
		for _, entry := range entries {
			if entry.IsDir() || !strings.HasSuffix(entry.Name(), ".plist") {
				continue
			}
			counts[dir]++
			if len(items) < 30 {
				items = append(items, filepath.Join(dir, entry.Name()))
			}
		}
	}
	return items, counts
}

func macOSSystemLogSummary(opts Options, hostname string, primaryIP string, now time.Time) schema.HostEvent {
	predicate := `process == "sshd" OR process == "loginwindow" OR process == "sudo" OR eventMessage CONTAINS[c] "authentication" OR eventMessage CONTAINS[c] "failed"`
	output, err := runCommand(4*time.Second, "log", "show", "--style", "compact", "--last", "10m", "--predicate", predicate, "--info")
	severity := "info"
	action := "record"
	if err != nil {
		severity = "low"
		action = "review"
	}
	return schema.HostEvent{
		EventUID:     stableUID(opts.AgentID, "macos-system-log-summary", trimText(output, 256), errString(err)),
		SourceModule: "macos-agent",
		EventType:    "macos_system_log_summary_observed",
		Severity:     severity,
		RuleID:       "MAC-SYSTEM-LOG-SUMMARY",
		RuleName:     "macOS security-relevant system log summary observed by CyberFusion Agent",
		AssetName:    hostname,
		AssetIP:      primaryIP,
		Action:       action,
		EventTime:    schema.LocalTime(now),
		Raw: map[string]any{
			"predicate":     predicate,
			"window":        "10m",
			"sampleExcerpt": trimText(output, 1600),
			"error":         errString(err),
		},
		Normalized: map[string]any{
			"agentId": opts.AgentID,
			"source":  "cyberfusion-agent",
		},
	}
}

func windowsCollectorEvents(opts Options, hostname string, primaryIP string, now time.Time) []schema.HostEvent {
	if opts.OSType != "windows" {
		return nil
	}
	events := make([]schema.HostEvent, 0, 8)
	events = append(events, windowsEventLogSummary(opts, hostname, primaryIP, now, "Security", "windows_logon_activity", "WIN-EVENTLOG-SECURITY", "Security EventLog login activity", "*[System[(EventID=4624 or EventID=4625)]]"))
	events = append(events, windowsEventLogSummary(opts, hostname, primaryIP, now, "System", "windows_system_service_activity", "WIN-EVENTLOG-SYSTEM", "System EventLog service activity", "*[System[(EventID=7036 or EventID=7045)]]"))
	events = append(events, windowsEventLogSummary(opts, hostname, primaryIP, now, "Application", "windows_application_error_activity", "WIN-EVENTLOG-APPLICATION", "Application EventLog error activity", "*[System[(Level=2)]]"))
	events = append(events, windowsEventLogSummary(opts, hostname, primaryIP, now, "Microsoft-Windows-PowerShell/Operational", "windows_powershell_operational_activity", "WIN-POWERSHELL-OPERATIONAL", "PowerShell Operational activity", "*[System[(EventID=4103 or EventID=4104)]]"))
	events = append(events, windowsEventLogSummary(opts, hostname, primaryIP, now, "Microsoft-Windows-Windows Defender/Operational", "windows_defender_detection_activity", "WIN-DEFENDER-OPERATIONAL", "Microsoft Defender detection activity", "*[System[(EventID=1116 or EventID=1117 or EventID=5007)]]"))
	events = append(events, windowsEventLogSummary(opts, hostname, primaryIP, now, "Microsoft-Windows-Sysmon/Operational", "windows_sysmon_activity", "WIN-SYSMON-OPERATIONAL", "Sysmon activity if installed", "*[System[(EventID=1 or EventID=3 or EventID=11)]]"))
	events = append(events, windowsPatchSummary(opts, hostname, primaryIP, now))
	events = append(events, windowsServiceSummary(opts, hostname, primaryIP, now))
	events = append(events, windowsListeningPortSummary(opts, hostname, primaryIP, now))
	events = append(events, windowsStartupSummary(opts, hostname, primaryIP, now))
	return events
}

func windowsEventLogSummary(opts Options, hostname string, primaryIP string, now time.Time, channel string, eventType string, ruleID string, ruleName string, query string) schema.HostEvent {
	output, err := runCommand(4*time.Second, "wevtutil", "qe", channel, "/c:5", "/rd:true", "/f:text", "/q:"+query)
	severity := "info"
	action := "record"
	if err != nil {
		severity = "low"
		action = "review"
	}
	return schema.HostEvent{
		EventUID:     stableUID(opts.AgentID, channel, eventType, trimText(output, 256), errString(err)),
		SourceModule: "windows-agent",
		EventType:    eventType,
		Severity:     severity,
		RuleID:       ruleID,
		RuleName:     ruleName,
		AssetName:    hostname,
		AssetIP:      primaryIP,
		Action:       action,
		EventTime:    schema.LocalTime(now),
		Raw: map[string]any{
			"channel":       channel,
			"query":         query,
			"sampleExcerpt": trimText(output, 1600),
			"error":         errString(err),
		},
		Normalized: map[string]any{
			"agentId": opts.AgentID,
			"source":  "cyberfusion-agent",
		},
	}
}

func windowsPatchSummary(opts Options, hostname string, primaryIP string, now time.Time) schema.HostEvent {
	command := `Get-HotFix | Sort-Object InstalledOn -Descending | Select-Object -First 10 HotFixID,InstalledOn,Description | ConvertTo-Json -Compress`
	output, err := runCommand(6*time.Second, "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", command)
	severity := "info"
	action := "record"
	if err != nil {
		severity = "low"
		action = "review"
	}
	return schema.HostEvent{
		EventUID:     stableUID(opts.AgentID, "windows-patch-summary", trimText(output, 256), errString(err)),
		SourceModule: "windows-agent",
		EventType:    "windows_patch_summary_observed",
		Severity:     severity,
		RuleID:       "WIN-PATCH-SUMMARY",
		RuleName:     "Windows patch summary observed by CyberFusion Agent",
		AssetName:    hostname,
		AssetIP:      primaryIP,
		Action:       action,
		EventTime:    schema.LocalTime(now),
		Raw: map[string]any{
			"command":       "Get-HotFix",
			"sampleExcerpt": trimText(output, 1600),
			"error":         errString(err),
		},
		Normalized: map[string]any{
			"agentId": opts.AgentID,
			"source":  "cyberfusion-agent",
		},
	}
}

func windowsServiceSummary(opts Options, hostname string, primaryIP string, now time.Time) schema.HostEvent {
	output, err := runCommand(4*time.Second, "sc", "query", "state=", "all")
	severity := "info"
	action := "record"
	if err != nil {
		severity = "low"
		action = "review"
	}
	return schema.HostEvent{
		EventUID:     stableUID(opts.AgentID, "windows-service-summary", trimText(output, 256), errString(err)),
		SourceModule: "windows-agent",
		EventType:    "windows_service_summary_observed",
		Severity:     severity,
		RuleID:       "WIN-SERVICE-SUMMARY",
		RuleName:     "Windows service summary observed by CyberFusion Agent",
		AssetName:    hostname,
		AssetIP:      primaryIP,
		Action:       action,
		EventTime:    schema.LocalTime(now),
		Raw: map[string]any{
			"sampleExcerpt": trimText(output, 1600),
			"error":         errString(err),
		},
		Normalized: map[string]any{
			"agentId": opts.AgentID,
			"source":  "cyberfusion-agent",
		},
	}
}

func windowsListeningPortSummary(opts Options, hostname string, primaryIP string, now time.Time) schema.HostEvent {
	output, err := runCommand(4*time.Second, "netstat", "-ano", "-p", "tcp")
	severity := "info"
	action := "record"
	if err != nil {
		severity = "low"
		action = "review"
	}
	return schema.HostEvent{
		EventUID:     stableUID(opts.AgentID, "windows-listening-ports", trimText(output, 256), errString(err)),
		SourceModule: "windows-agent",
		EventType:    "windows_listening_port_summary_observed",
		Severity:     severity,
		RuleID:       "WIN-LISTENING-PORTS",
		RuleName:     "Windows listening port summary observed by CyberFusion Agent",
		AssetName:    hostname,
		AssetIP:      primaryIP,
		Action:       action,
		EventTime:    schema.LocalTime(now),
		Raw: map[string]any{
			"sampleExcerpt": trimText(filterLines(output, "LISTENING"), 1600),
			"error":         errString(err),
		},
		Normalized: map[string]any{
			"agentId": opts.AgentID,
			"source":  "cyberfusion-agent",
		},
	}
}

func windowsStartupSummary(opts Options, hostname string, primaryIP string, now time.Time) schema.HostEvent {
	output, err := runCommand(4*time.Second, "reg", "query", "HKLM\\Software\\Microsoft\\Windows\\CurrentVersion\\Run")
	severity := "info"
	action := "record"
	if err != nil {
		severity = "low"
		action = "review"
	}
	return schema.HostEvent{
		EventUID:     stableUID(opts.AgentID, "windows-startup-summary", trimText(output, 256), errString(err)),
		SourceModule: "windows-agent",
		EventType:    "windows_startup_summary_observed",
		Severity:     severity,
		RuleID:       "WIN-STARTUP-SUMMARY",
		RuleName:     "Windows startup item summary observed by CyberFusion Agent",
		AssetName:    hostname,
		AssetIP:      primaryIP,
		Action:       action,
		EventTime:    schema.LocalTime(now),
		Raw: map[string]any{
			"registryPath":  "HKLM\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
			"sampleExcerpt": trimText(output, 1200),
			"error":         errString(err),
		},
		Normalized: map[string]any{
			"agentId": opts.AgentID,
			"source":  "cyberfusion-agent",
		},
	}
}

func processSummaryEvent(opts Options, hostname string, primaryIP string, now time.Time) (schema.HostEvent, bool) {
	if opts.OSType != "macos" && opts.OSType != "linux" {
		return schema.HostEvent{}, false
	}
	output, err := runCommand(2*time.Second, "ps", "-axo", "pid,user,comm")
	if err != nil {
		return collectorErrorEvent(opts, hostname, primaryIP, now, "process_collect_failed", "ps", err), true
	}
	lines := strings.Split(output, "\n")
	processes := make([]map[string]string, 0, 30)
	for i, line := range lines {
		if i == 0 || strings.TrimSpace(line) == "" {
			continue
		}
		fields := strings.Fields(line)
		if len(fields) < 3 {
			continue
		}
		processes = append(processes, map[string]string{
			"pid":     fields[0],
			"user":    fields[1],
			"command": strings.Join(fields[2:], " "),
		})
		if len(processes) >= 30 {
			break
		}
	}
	return schema.HostEvent{
		EventUID:     stableUID(opts.AgentID, "process-summary", hostname),
		SourceModule: opts.OSType + "-agent",
		EventType:    "process_summary_observed",
		Severity:     "info",
		RuleID:       "HOST-PROCESS-SUMMARY",
		RuleName:     "Process summary observed by CyberFusion Agent",
		AssetName:    hostname,
		AssetIP:      primaryIP,
		Action:       "record",
		EventTime:    schema.LocalTime(now),
		Raw: map[string]any{
			"sampleSize": len(processes),
			"processes":  processes,
		},
		Normalized: map[string]any{
			"agentId": opts.AgentID,
			"source":  "cyberfusion-agent",
		},
	}, true
}

func baselineChecks(opts Options, hostname string, primaryIP string, now time.Time) []schema.BaselineCheck {
	checks := make([]schema.BaselineCheck, 0, 4)
	if opts.OSType == "macos" {
		checks = append(checks, macOSFirewallCheck(opts, hostname, primaryIP, now))
		checks = append(checks, macOSRemoteLoginCheck(opts, hostname, primaryIP, now))
	}
	if opts.OSType == "windows" {
		checks = append(checks, windowsDefenderServiceCheck(opts, hostname, primaryIP, now))
		checks = append(checks, windowsFirewallCheck(opts, hostname, primaryIP, now))
	}
	if opts.FIMPath != "" {
		checks = append(checks, filePermissionCheck(opts, hostname, primaryIP, now))
	}
	return checks
}

func windowsDefenderServiceCheck(opts Options, hostname string, primaryIP string, now time.Time) schema.BaselineCheck {
	output, err := runCommand(3*time.Second, "sc", "query", "WinDefend")
	lower := strings.ToLower(output)
	result := "unknown"
	status := "reviewing"
	passRate := 0
	remediation := "Review Microsoft Defender service status manually."
	if err == nil && strings.Contains(lower, "running") {
		result = "pass"
		status = "passed"
		passRate = 100
		remediation = "Keep Microsoft Defender running or document the approved alternative EDR."
	} else if err == nil {
		result = "failed"
		status = "failed"
		remediation = "Start Microsoft Defender or document the approved alternative EDR."
	}
	return baselineCheck(opts, hostname, primaryIP, "windows-defender-service", "host-hardening", "Microsoft Defender service running", result, "high", passRate, remediation, status, now, map[string]any{
		"command": "sc query WinDefend",
		"output":  trimText(output, 1000),
		"error":   errString(err),
	})
}

func windowsFirewallCheck(opts Options, hostname string, primaryIP string, now time.Time) schema.BaselineCheck {
	output, err := runCommand(3*time.Second, "netsh", "advfirewall", "show", "allprofiles", "state")
	lower := strings.ToLower(output)
	result := "unknown"
	status := "reviewing"
	passRate := 0
	remediation := "Review Windows firewall profile states manually."
	if err == nil && !strings.Contains(lower, "off") && strings.Contains(lower, "on") {
		result = "pass"
		status = "passed"
		passRate = 100
		remediation = "Keep Windows firewall enabled for required profiles."
	} else if err == nil {
		result = "warning"
		status = "open"
		passRate = 50
		remediation = "Enable Windows firewall profiles or document approved exceptions."
	}
	return baselineCheck(opts, hostname, primaryIP, "windows-firewall", "host-hardening", "Windows firewall profiles enabled", result, "medium", passRate, remediation, status, now, map[string]any{
		"command": "netsh advfirewall show allprofiles state",
		"output":  trimText(output, 1000),
		"error":   errString(err),
	})
}

func macOSFirewallCheck(opts Options, hostname string, primaryIP string, now time.Time) schema.BaselineCheck {
	output, err := runCommand(2*time.Second, "/usr/libexec/ApplicationFirewall/socketfilterfw", "--getglobalstate")
	result := "unknown"
	status := "reviewing"
	passRate := 0
	remediation := "Review macOS firewall status manually."
	if err == nil && strings.Contains(strings.ToLower(output), "enabled") {
		result = "pass"
		status = "passed"
		passRate = 100
		remediation = "Keep macOS firewall enabled."
	} else if err == nil {
		result = "failed"
		status = "failed"
		remediation = "Enable macOS firewall or document an approved exception."
	}
	return baselineCheck(opts, hostname, primaryIP, "macos-firewall", "host-hardening", "macOS firewall enabled", result, "medium", passRate, remediation, status, now, map[string]any{
		"command": "socketfilterfw --getglobalstate",
		"output":  strings.TrimSpace(output),
		"error":   errString(err),
	})
}

func macOSRemoteLoginCheck(opts Options, hostname string, primaryIP string, now time.Time) schema.BaselineCheck {
	output, err := runCommand(2*time.Second, "systemsetup", "-getremotelogin")
	lower := strings.ToLower(output)
	result := "unknown"
	status := "reviewing"
	passRate := 0
	remediation := "Review macOS remote login status manually."
	if err == nil && strings.Contains(lower, "off") {
		result = "pass"
		status = "passed"
		passRate = 100
		remediation = "Keep remote login disabled unless explicitly required."
	} else if err == nil && strings.Contains(lower, "on") {
		result = "warning"
		status = "open"
		passRate = 50
		remediation = "Disable SSH remote login or restrict it to approved administrators."
	}
	return baselineCheck(opts, hostname, primaryIP, "macos-remote-login", "host-hardening", "macOS SSH remote login disabled", result, "medium", passRate, remediation, status, now, map[string]any{
		"command": "systemsetup -getremotelogin",
		"output":  strings.TrimSpace(output),
		"error":   errString(err),
	})
}

func filePermissionCheck(opts Options, hostname string, primaryIP string, now time.Time) schema.BaselineCheck {
	info, err := os.Stat(opts.FIMPath)
	result := "unknown"
	status := "reviewing"
	passRate := 0
	remediation := "Review watched file permissions manually."
	evidence := map[string]any{
		"path":  opts.FIMPath,
		"error": errString(err),
	}
	if err == nil {
		mode := info.Mode().Perm()
		evidence["mode"] = mode.String()
		if mode&0o002 != 0 {
			result = "failed"
			status = "failed"
			remediation = "Remove world-write permission from the watched file."
		} else {
			result = "pass"
			status = "passed"
			passRate = 100
			remediation = "Keep watched file permissions restricted."
		}
	}
	return baselineCheck(opts, hostname, primaryIP, "watched-file-permission", "file-integrity", "Watched file is not world writable", result, "medium", passRate, remediation, status, now, evidence)
}

func baselineCheck(opts Options, hostname string, primaryIP string, suffix string, category string, item string, result string, severity string, passRate int, remediation string, status string, now time.Time, evidence map[string]any) schema.BaselineCheck {
	return schema.BaselineCheck{
		CheckCode:   checkCode(opts.AgentID, suffix),
		Category:    category,
		CheckItem:   item,
		AssetName:   hostname,
		AssetIP:     primaryIP,
		Result:      result,
		Severity:    severity,
		PassRate:    passRate,
		Remediation: remediation,
		Status:      status,
		CheckedAt:   schema.LocalTime(now),
		Evidence:    evidence,
	}
}

func collectorErrorEvent(opts Options, hostname string, primaryIP string, now time.Time, eventType string, command string, err error) schema.HostEvent {
	return schema.HostEvent{
		EventUID:     stableUID(opts.AgentID, eventType, command),
		SourceModule: opts.OSType + "-agent",
		EventType:    eventType,
		Severity:     "low",
		RuleID:       "HOST-COLLECTOR-ERROR",
		RuleName:     "Host collector could not collect a source",
		AssetName:    hostname,
		AssetIP:      primaryIP,
		Action:       "review",
		EventTime:    schema.LocalTime(now),
		Raw: map[string]any{
			"command": command,
			"error":   errString(err),
		},
		Normalized: map[string]any{
			"agentId": opts.AgentID,
			"source":  "cyberfusion-agent",
		},
	}
}

func runCommand(timeout time.Duration, name string, args ...string) (string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()
	cmd := exec.CommandContext(ctx, name, args...)
	output, err := cmd.CombinedOutput()
	if ctx.Err() != nil {
		return string(output), ctx.Err()
	}
	return string(output), err
}

func currentUsername() string {
	value := os.Getenv("USER")
	if value == "" {
		value = os.Getenv("USERNAME")
	}
	return value
}

func errString(err error) string {
	if err == nil {
		return ""
	}
	return err.Error()
}

func filterLines(text string, contains string) string {
	if contains == "" {
		return text
	}
	var out []string
	for _, line := range strings.Split(text, "\n") {
		if strings.Contains(strings.ToUpper(line), strings.ToUpper(contains)) {
			out = append(out, strings.TrimSpace(line))
		}
		if len(out) >= 20 {
			break
		}
	}
	return strings.Join(out, "\n")
}

func trimText(text string, limit int) string {
	text = strings.TrimSpace(text)
	if limit <= 0 || len(text) <= limit {
		return text
	}
	return text[:limit] + "...[truncated]"
}

func checkCode(agentID string, suffix string) string {
	code := agentID + "-" + suffix
	if len(code) <= 64 {
		return code
	}
	return stableUID("baseline", agentID, suffix)
}

func stableUID(parts ...string) string {
	h := sha256.New()
	for _, part := range parts {
		_, _ = h.Write([]byte(part))
		_, _ = h.Write([]byte{0})
	}
	return "CF-HOST-" + strings.ToUpper(hex.EncodeToString(h.Sum(nil))[:24])
}

func firstOr(values []string, fallback string) string {
	if len(values) == 0 || values[0] == "" {
		return fallback
	}
	return values[0]
}

func unique(values []string) []string {
	seen := make(map[string]struct{}, len(values))
	out := make([]string, 0, len(values))
	for _, value := range values {
		if value == "" {
			continue
		}
		if _, ok := seen[value]; ok {
			continue
		}
		seen[value] = struct{}{}
		out = append(out, value)
	}
	return out
}

func RequireSupportedOS(osType string) error {
	switch osType {
	case "macos", "windows", "linux":
		return nil
	default:
		return fmt.Errorf("unsupported os type %q", osType)
	}
}

package config

import (
	"errors"
	"flag"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"time"
)

type Config struct {
	APIBaseURL         string
	AgentID            string
	AgentToken         string
	AgentVersion       string
	Profile            string
	ConfigFile         string
	OSType             string
	Mode               string
	FixtureOS          string
	FixtureRunID       string
	FIMPath            string
	RuntimeDir         string
	ResourceReportFile string
	QueueMaxMB         int
	RatePerSec         int
	Interval           time.Duration
	MaxCycles          int
	DryRun             bool
	Once               bool
}

func Parse(args []string) (Config, error) {
	var cfg Config
	fs := flag.NewFlagSet("cyberfusion-agent", flag.ContinueOnError)
	fs.StringVar(&cfg.APIBaseURL, "api-base-url", envOr("CYBERFUSION_API_BASE", "http://127.0.0.1:18080/api"), "CyberFusion API base URL")
	fs.StringVar(&cfg.AgentID, "agent-id", os.Getenv("CYBERFUSION_AGENT_ID"), "stable agent id")
	fs.StringVar(&cfg.AgentToken, "agent-token", os.Getenv("CYBERFUSION_AGENT_TOKEN"), "agent token; prefer environment or protected config")
	fs.StringVar(&cfg.AgentVersion, "agent-version", envOr("CYBERFUSION_AGENT_VERSION", "0.1.0-dev"), "deployed Agent software version")
	fs.StringVar(&cfg.Profile, "profile", envOr("CYBERFUSION_AGENT_PROFILE", "full"), "collection profile: full, host-log, patrol-audit, file-integrity, baseline-audit")
	fs.StringVar(&cfg.ConfigFile, "config-file", os.Getenv("CYBERFUSION_AGENT_CONFIG"), "optional KEY=VALUE config file")
	fs.StringVar(&cfg.OSType, "os-type", defaultOSType(), "collector OS type: macos, windows, linux")
	fs.StringVar(&cfg.Mode, "mode", "collect", "collect or fixture")
	fs.StringVar(&cfg.FixtureOS, "fixture-os", "current", "fixture target: current, macos, windows, all")
	fs.StringVar(&cfg.FixtureRunID, "fixture-run-id", os.Getenv("CYBERFUSION_FIXTURE_RUN_ID"), "optional fixture run id for queue pressure tests")
	fs.StringVar(&cfg.FIMPath, "fim-path", "", "optional file path for FIM hash metadata")
	fs.StringVar(&cfg.RuntimeDir, "runtime-dir", os.Getenv("CYBERFUSION_AGENT_RUNTIME_DIR"), "runtime directory for queue, state, and logs")
	fs.StringVar(&cfg.ResourceReportFile, "resource-report-file", os.Getenv("CYBERFUSION_AGENT_RESOURCE_REPORT_FILE"), "optional JSON file for smoke-test runtime memory stats")
	fs.IntVar(&cfg.QueueMaxMB, "queue-max-mb", intFromEnv("CYBERFUSION_AGENT_QUEUE_MAX_MB", 200), "local queue soft limit in MB")
	fs.IntVar(&cfg.RatePerSec, "rate-per-sec", intFromEnv("CYBERFUSION_AGENT_RATE_PER_SEC", 2), "max upload requests per second")
	fs.DurationVar(&cfg.Interval, "interval", durationFromEnv("CYBERFUSION_AGENT_INTERVAL", 60*time.Second), "daemon collection interval, for example 30s or 1m")
	fs.IntVar(&cfg.MaxCycles, "max-cycles", intFromEnv("CYBERFUSION_AGENT_MAX_CYCLES", 0), "daemon test limit; 0 means run until stopped")
	fs.BoolVar(&cfg.DryRun, "dry-run", false, "print payloads without uploading")
	fs.BoolVar(&cfg.Once, "once", true, "collect and upload once; set false for service/daemon mode")
	if err := fs.Parse(args); err != nil {
		return cfg, err
	}
	setFlags := map[string]bool{}
	fs.Visit(func(f *flag.Flag) {
		setFlags[f.Name] = true
	})
	if cfg.ConfigFile != "" {
		values, err := readConfigFile(cfg.ConfigFile)
		if err != nil {
			return cfg, err
		}
		applyConfigFile(&cfg, values, setFlags)
	}
	cfg.APIBaseURL = strings.TrimRight(cfg.APIBaseURL, "/")
	cfg.Mode = strings.ToLower(strings.TrimSpace(cfg.Mode))
	cfg.OSType = strings.ToLower(strings.TrimSpace(cfg.OSType))
	cfg.FixtureOS = strings.ToLower(strings.TrimSpace(cfg.FixtureOS))
	cfg.FixtureRunID = sanitizeID(cfg.FixtureRunID)
	cfg.AgentVersion = strings.TrimSpace(cfg.AgentVersion)
	cfg.Profile = strings.ToLower(strings.TrimSpace(cfg.Profile))
	if cfg.Mode != "collect" && cfg.Mode != "fixture" {
		return cfg, errors.New("mode must be collect or fixture")
	}
	if cfg.OSType != "macos" && cfg.OSType != "windows" && cfg.OSType != "linux" {
		return cfg, errors.New("os-type must be macos, windows, or linux")
	}
	if cfg.AgentVersion == "" {
		return cfg, errors.New("agent-version is required")
	}
	if cfg.Profile != "full" && cfg.Profile != "host-log" && cfg.Profile != "patrol-audit" && cfg.Profile != "file-integrity" && cfg.Profile != "baseline-audit" {
		return cfg, errors.New("profile must be full, host-log, patrol-audit, file-integrity, or baseline-audit")
	}
	if cfg.FixtureOS != "current" && cfg.FixtureOS != "macos" && cfg.FixtureOS != "windows" && cfg.FixtureOS != "all" {
		return cfg, errors.New("fixture-os must be current, macos, windows, or all")
	}
	if cfg.AgentID == "" && cfg.Mode == "collect" {
		host, _ := os.Hostname()
		if host == "" {
			host = "unknown-host"
		}
		cfg.AgentID = cfg.OSType + "-" + sanitizeID(host)
	}
	if cfg.AgentID == "" && cfg.Mode == "fixture" && cfg.FixtureOS != "all" {
		target := cfg.OSType
		if cfg.FixtureOS != "current" {
			target = cfg.FixtureOS
		}
		cfg.AgentID = target + "-fixture-agent"
	}
	if cfg.RuntimeDir == "" {
		cfg.RuntimeDir = defaultRuntimeDir(cfg.AgentID)
	}
	if cfg.QueueMaxMB <= 0 || cfg.QueueMaxMB > 2048 {
		return cfg, errors.New("queue-max-mb must be between 1 and 2048")
	}
	if cfg.RatePerSec <= 0 || cfg.RatePerSec > 20 {
		return cfg, errors.New("rate-per-sec must be between 1 and 20")
	}
	if cfg.Interval < time.Second || cfg.Interval > 24*time.Hour {
		return cfg, errors.New("interval must be between 1s and 24h")
	}
	if cfg.MaxCycles < 0 {
		return cfg, errors.New("max-cycles must be zero or positive")
	}
	if !cfg.DryRun && cfg.AgentToken == "" {
		return cfg, errors.New("agent-token is required unless --dry-run is set")
	}
	return cfg, nil
}

func readConfigFile(path string) (map[string]string, error) {
	content, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	values := map[string]string{}
	for _, line := range strings.Split(string(content), "\n") {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		key, value, ok := strings.Cut(line, "=")
		if !ok {
			continue
		}
		key = strings.TrimPrefix(strings.TrimSpace(key), "\ufeff")
		value = strings.Trim(strings.TrimSpace(value), "\"")
		values[key] = value
	}
	return values, nil
}

func applyConfigFile(cfg *Config, values map[string]string, setFlags map[string]bool) {
	if !setFlags["api-base-url"] && (cfg.APIBaseURL == "" || cfg.APIBaseURL == "http://127.0.0.1:18080/api") {
		cfg.APIBaseURL = firstConfig(values, "CYBERFUSION_API_BASE", cfg.APIBaseURL)
	}
	if !setFlags["agent-id"] && cfg.AgentID == "" {
		cfg.AgentID = firstConfig(values, "CYBERFUSION_AGENT_ID", cfg.AgentID)
	}
	if !setFlags["agent-token"] && cfg.AgentToken == "" {
		cfg.AgentToken = firstConfig(values, "CYBERFUSION_AGENT_TOKEN", cfg.AgentToken)
	}
	if !setFlags["agent-version"] {
		cfg.AgentVersion = firstConfig(values, "CYBERFUSION_AGENT_VERSION", cfg.AgentVersion)
	}
	if !setFlags["profile"] {
		cfg.Profile = firstConfig(values, "CYBERFUSION_AGENT_PROFILE", cfg.Profile)
	}
	if !setFlags["runtime-dir"] && cfg.RuntimeDir == "" {
		cfg.RuntimeDir = firstConfig(values, "CYBERFUSION_AGENT_RUNTIME_DIR", cfg.RuntimeDir)
	}
	if !setFlags["resource-report-file"] && cfg.ResourceReportFile == "" {
		cfg.ResourceReportFile = firstConfig(values, "CYBERFUSION_AGENT_RESOURCE_REPORT_FILE", cfg.ResourceReportFile)
	}
	if !setFlags["fim-path"] && cfg.FIMPath == "" {
		cfg.FIMPath = firstConfig(values, "CYBERFUSION_AGENT_FIM_PATH", cfg.FIMPath)
	}
	if !setFlags["interval"] {
		value := firstConfig(values, "CYBERFUSION_AGENT_INTERVAL", "")
		if parsed, ok := parseIntervalValue(value); ok {
			cfg.Interval = parsed
		}
	}
	if !setFlags["max-cycles"] {
		value := firstConfig(values, "CYBERFUSION_AGENT_MAX_CYCLES", "")
		if parsed := parsePositiveInt(value); parsed >= 0 {
			cfg.MaxCycles = parsed
		}
	}
	if !setFlags["once"] {
		value := firstConfig(values, "CYBERFUSION_AGENT_ONCE", "")
		cfg.Once = parseBoolValue(value, cfg.Once)
	}
}

func firstConfig(values map[string]string, key string, fallback string) string {
	if value := values[key]; value != "" {
		return value
	}
	return fallback
}

func intFromEnv(name string, fallback int) int {
	parsed := parsePositiveInt(os.Getenv(name))
	if parsed < 0 {
		return fallback
	}
	return parsed
}

func parsePositiveInt(value string) int {
	value = strings.TrimSpace(value)
	if value == "" {
		return -1
	}
	var parsed int
	for _, r := range value {
		if r < '0' || r > '9' {
			return -1
		}
		parsed = parsed*10 + int(r-'0')
	}
	return parsed
}

func durationFromEnv(name string, fallback time.Duration) time.Duration {
	value := strings.TrimSpace(os.Getenv(name))
	if value == "" {
		return fallback
	}
	if parsed, ok := parseIntervalValue(value); ok {
		return parsed
	}
	return fallback
}

func parseIntervalValue(value string) (time.Duration, bool) {
	parsed, err := time.ParseDuration(strings.TrimSpace(value))
	if err == nil {
		return parsed, true
	}
	seconds := parsePositiveInt(value)
	if seconds > 0 {
		return time.Duration(seconds) * time.Second, true
	}
	return 0, false
}

func parseBoolValue(value string, fallback bool) bool {
	switch strings.ToLower(strings.TrimSpace(value)) {
	case "1", "true", "yes", "y", "on":
		return true
	case "0", "false", "no", "n", "off":
		return false
	default:
		return fallback
	}
}

func envOr(name string, fallback string) string {
	value := os.Getenv(name)
	if value == "" {
		return fallback
	}
	return value
}

func defaultOSType() string {
	switch runtime.GOOS {
	case "darwin":
		return "macos"
	case "windows":
		return "windows"
	default:
		return "linux"
	}
}

func defaultRuntimeDir(agentID string) string {
	if root := os.Getenv("CYBERFUSION_ENV_ROOT"); root != "" {
		return filepath.Join(root, "agent", sanitizeID(agentID))
	}
	if runtime.GOOS == "windows" {
		if data := os.Getenv("ProgramData"); data != "" {
			return filepath.Join(data, "CyberFusion", "Agent", sanitizeID(agentID))
		}
	}
	return filepath.Join(os.TempDir(), "cyberfusion-agent", sanitizeID(agentID))
}

func sanitizeID(value string) string {
	var b strings.Builder
	for _, r := range strings.ToLower(value) {
		if (r >= 'a' && r <= 'z') || (r >= '0' && r <= '9') || r == '-' || r == '_' {
			b.WriteRune(r)
		} else {
			b.WriteByte('-')
		}
	}
	return strings.Trim(b.String(), "-")
}

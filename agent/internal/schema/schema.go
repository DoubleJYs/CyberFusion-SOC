package schema

import "time"

const Version = "0.1.0-dev"

type AgentRegistration struct {
	AgentID      string            `json:"agentId,omitempty"`
	AgentName    string            `json:"agentName,omitempty"`
	Hostname     string            `json:"hostname"`
	OSType       string            `json:"osType"`
	OSVersion    string            `json:"osVersion,omitempty"`
	Architecture string            `json:"architecture,omitempty"`
	AgentVersion string            `json:"agentVersion"`
	IPAddresses  []string          `json:"ipAddresses,omitempty"`
	MACAddresses []string          `json:"macAddresses,omitempty"`
	Labels       map[string]string `json:"labels,omitempty"`
}

type Heartbeat struct {
	AgentID        string   `json:"agentId"`
	Hostname       string   `json:"hostname,omitempty"`
	OSType         string   `json:"osType,omitempty"`
	AgentVersion   string   `json:"agentVersion,omitempty"`
	IPAddresses    []string `json:"ipAddresses,omitempty"`
	QueueDepth     int      `json:"queueDepth"`
	QueueBytes     int64    `json:"queueBytes"`
	CollectedCount int64    `json:"collectedCount"`
	SentCount      int64    `json:"sentCount"`
	FailedCount    int64    `json:"failedCount"`
	UptimeSeconds  int64    `json:"uptimeSeconds"`
	ObservedAt     string   `json:"observedAt,omitempty"`
}

type AssetIngest struct {
	AgentID     string  `json:"agentId"`
	BatchID     string  `json:"batchId,omitempty"`
	OSType      string  `json:"osType,omitempty"`
	CollectedAt string  `json:"collectedAt,omitempty"`
	Assets      []Asset `json:"assets"`
}

type Asset struct {
	Hostname     string            `json:"hostname"`
	PrimaryIP    string            `json:"primaryIp"`
	OSType       string            `json:"osType,omitempty"`
	OSVersion    string            `json:"osVersion,omitempty"`
	IPAddresses  []string          `json:"ipAddresses,omitempty"`
	MACAddresses []string          `json:"macAddresses,omitempty"`
	OwnerName    string            `json:"ownerName,omitempty"`
	DeptName     string            `json:"deptName,omitempty"`
	Facts        map[string]string `json:"facts,omitempty"`
	ObservedAt   string            `json:"observedAt,omitempty"`
}

type EventIngest struct {
	AgentID     string      `json:"agentId"`
	BatchID     string      `json:"batchId,omitempty"`
	OSType      string      `json:"osType,omitempty"`
	CollectedAt string      `json:"collectedAt,omitempty"`
	Events      []HostEvent `json:"events"`
}

type HostEvent struct {
	EventUID     string         `json:"eventUid"`
	SourceModule string         `json:"sourceModule,omitempty"`
	EventType    string         `json:"eventType"`
	Severity     string         `json:"severity"`
	RuleID       string         `json:"ruleId,omitempty"`
	RuleName     string         `json:"ruleName,omitempty"`
	SrcIP        string         `json:"srcIp,omitempty"`
	DestIP       string         `json:"destIp,omitempty"`
	AssetName    string         `json:"assetName,omitempty"`
	AssetIP      string         `json:"assetIp,omitempty"`
	TargetURL    string         `json:"targetUrl,omitempty"`
	Action       string         `json:"action,omitempty"`
	IOC          string         `json:"ioc,omitempty"`
	EventTime    string         `json:"eventTime,omitempty"`
	Raw          map[string]any `json:"raw,omitempty"`
	Normalized   map[string]any `json:"normalized,omitempty"`
}

type FIMIngest struct {
	AgentID     string     `json:"agentId"`
	BatchID     string     `json:"batchId,omitempty"`
	OSType      string     `json:"osType,omitempty"`
	CollectedAt string     `json:"collectedAt,omitempty"`
	Events      []FIMEvent `json:"events"`
}

type FIMEvent struct {
	EventUID   string         `json:"eventUid"`
	Action     string         `json:"action"`
	Severity   string         `json:"severity"`
	Hostname   string         `json:"hostname"`
	AssetIP    string         `json:"assetIp"`
	FilePath   string         `json:"filePath"`
	RuleName   string         `json:"ruleName,omitempty"`
	BeforeHash string         `json:"beforeHash,omitempty"`
	AfterHash  string         `json:"afterHash,omitempty"`
	EventTime  string         `json:"eventTime,omitempty"`
	Attributes map[string]any `json:"attributes,omitempty"`
}

type BaselineIngest struct {
	AgentID     string          `json:"agentId"`
	BatchID     string          `json:"batchId,omitempty"`
	OSType      string          `json:"osType,omitempty"`
	CollectedAt string          `json:"collectedAt,omitempty"`
	Checks      []BaselineCheck `json:"checks"`
}

type BaselineCheck struct {
	CheckCode   string         `json:"checkCode"`
	Category    string         `json:"category"`
	CheckItem   string         `json:"checkItem"`
	AssetName   string         `json:"assetName"`
	AssetIP     string         `json:"assetIp"`
	Result      string         `json:"result"`
	Severity    string         `json:"severity"`
	PassRate    int            `json:"passRate"`
	Remediation string         `json:"remediation,omitempty"`
	Status      string         `json:"status,omitempty"`
	CheckedAt   string         `json:"checkedAt,omitempty"`
	Evidence    map[string]any `json:"evidence,omitempty"`
}

type Snapshot struct {
	Registration AgentRegistration `json:"registration"`
	Heartbeat    Heartbeat         `json:"heartbeat"`
	Assets       AssetIngest       `json:"assets"`
	Events       EventIngest       `json:"events"`
	FIM          FIMIngest         `json:"fim"`
	Baseline     BaselineIngest    `json:"baseline"`
}

func LocalTime(t time.Time) string {
	return t.UTC().Format("2006-01-02T15:04:05")
}

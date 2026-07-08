package main

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"os/signal"
	"runtime"
	"syscall"
	"time"

	"cyberfusion-agent/internal/client"
	"cyberfusion-agent/internal/collector"
	"cyberfusion-agent/internal/config"
	"cyberfusion-agent/internal/core"
	"cyberfusion-agent/internal/schema"
)

func main() {
	cfg, err := config.Parse(os.Args[1:])
	if err != nil {
		fmt.Fprintf(os.Stderr, "config error: %v\n", err)
		os.Exit(2)
	}
	resourceProbe := newResourceProbe(cfg.ResourceReportFile)
	defer func() {
		if err := resourceProbe.Write(); err != nil {
			fmt.Fprintf(os.Stderr, "resource report failed: %v\n", err)
		}
	}()
	if cfg.DryRun {
		snapshots, err := snapshotsFor(cfg)
		if err != nil {
			fmt.Fprintf(os.Stderr, "collect error: %v\n", err)
			os.Exit(1)
		}
		if err := printSnapshots(snapshots); err != nil {
			fmt.Fprintf(os.Stderr, "dry-run error: %v\n", err)
			os.Exit(1)
		}
		resourceProbe.Sample()
		return
	}
	if cfg.Once {
		snapshots, err := snapshotsFor(cfg)
		if err != nil {
			fmt.Fprintf(os.Stderr, "collect error: %v\n", err)
			os.Exit(1)
		}
		if err := runUpload(cfg, snapshots); err != nil {
			fmt.Fprintf(os.Stderr, "upload failed: %v\n", err)
			os.Exit(1)
		}
		resourceProbe.Sample()
		return
	}
	if err := runDaemon(cfg, resourceProbe); err != nil {
		fmt.Fprintf(os.Stderr, "upload failed: %v\n", err)
		os.Exit(1)
	}
}

func snapshotsFor(cfg config.Config) ([]schema.Snapshot, error) {
	if cfg.Mode == "fixture" {
		targets := []string{cfg.OSType}
		if cfg.FixtureOS == "all" {
			targets = []string{"macos", "windows"}
		} else if cfg.FixtureOS != "current" {
			targets = []string{cfg.FixtureOS}
		}
		snapshots := make([]schema.Snapshot, 0, len(targets))
		for _, target := range targets {
			agentID := cfg.AgentID
			if cfg.FixtureOS == "all" || agentID == "" {
				agentID = target + "-fixture-agent"
			}
			snapshot, err := collector.FixtureWithRunID(agentID, target, cfg.FixtureRunID)
			if err != nil {
				return nil, err
			}
			snapshots = append(snapshots, snapshot)
		}
		return snapshots, nil
	}
	snapshot, err := collector.CollectOnce(collector.Options{
		AgentID: cfg.AgentID,
		OSType:  cfg.OSType,
		FIMPath: cfg.FIMPath,
	})
	if err != nil {
		return nil, err
	}
	return []schema.Snapshot{snapshot}, nil
}

func runDaemon(cfg config.Config, resourceProbe *resourceProbe) error {
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	logger, err := core.NewLogger(cfg.RuntimeDir)
	if err != nil {
		return err
	}
	logger.Info("agent daemon started", "agentId", cfg.AgentID, "osType", cfg.OSType, "interval", cfg.Interval.String())
	defer logger.Info("agent daemon stopped", "agentId", cfg.AgentID)

	cycle := 0
	for {
		cycle++
		if err := runOneCycle(cfg, logger, cycle); err != nil {
			logger.Error("agent daemon cycle failed", "cycle", cycle, "error", err)
		}
		resourceProbe.Sample()
		if cfg.MaxCycles > 0 && cycle >= cfg.MaxCycles {
			return nil
		}
		timer := time.NewTimer(cfg.Interval)
		select {
		case <-ctx.Done():
			if !timer.Stop() {
				select {
				case <-timer.C:
				default:
				}
			}
			return nil
		case <-timer.C:
		}
	}
}

type resourceProbe struct {
	Path               string    `json:"-"`
	ObservedAt         time.Time `json:"observedAt"`
	Samples            int       `json:"samples"`
	MaxAllocBytes      uint64    `json:"maxAllocBytes"`
	MaxSysBytes        uint64    `json:"maxSysBytes"`
	MaxHeapInuseBytes  uint64    `json:"maxHeapInuseBytes"`
	MaxStackInuseBytes uint64    `json:"maxStackInuseBytes"`
	MaxGoroutines      int       `json:"maxGoroutines"`
	Metric             string    `json:"metric"`
	Note               string    `json:"note"`
}

func newResourceProbe(path string) *resourceProbe {
	return &resourceProbe{
		Path:   path,
		Metric: "go_runtime_memstats",
		Note:   "Go runtime memory stats for smoke tests; measure OS RSS or Windows working set on real service hosts.",
	}
}

func (p *resourceProbe) Sample() {
	if p == nil || p.Path == "" {
		return
	}
	var stats runtime.MemStats
	runtime.ReadMemStats(&stats)
	p.ObservedAt = time.Now().UTC()
	p.Samples++
	if stats.Alloc > p.MaxAllocBytes {
		p.MaxAllocBytes = stats.Alloc
	}
	if stats.Sys > p.MaxSysBytes {
		p.MaxSysBytes = stats.Sys
	}
	if stats.HeapInuse > p.MaxHeapInuseBytes {
		p.MaxHeapInuseBytes = stats.HeapInuse
	}
	if stats.StackInuse > p.MaxStackInuseBytes {
		p.MaxStackInuseBytes = stats.StackInuse
	}
	if goroutines := runtime.NumGoroutine(); goroutines > p.MaxGoroutines {
		p.MaxGoroutines = goroutines
	}
}

func (p *resourceProbe) Write() error {
	if p == nil || p.Path == "" {
		return nil
	}
	if p.Samples == 0 {
		p.Sample()
	}
	content, err := json.MarshalIndent(p, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(p.Path, append(content, '\n'), 0o600)
}

func runOneCycle(cfg config.Config, logger core.Logger, cycle int) error {
	snapshots, err := snapshotsFor(cfg)
	if err != nil {
		return err
	}
	logger.Info("agent daemon cycle collected", "cycle", cycle, "snapshots", len(snapshots))
	return runUpload(cfg, snapshots)
}

func runUpload(cfg config.Config, snapshots []schema.Snapshot) error {
	logger, err := core.NewLogger(cfg.RuntimeDir)
	if err != nil {
		return err
	}
	state, err := core.LoadState(cfg.RuntimeDir)
	if err != nil {
		return err
	}
	queue, err := core.NewQueue(cfg.RuntimeDir, int64(cfg.QueueMaxMB)*1024*1024)
	if err != nil {
		return err
	}
	if err := enqueueSnapshots(queue, state, snapshots); err != nil {
		return err
	}
	stats, err := queue.Stats()
	if err != nil {
		return err
	}
	applyHeartbeatStats(snapshots, stats, 0, 0)
	c := client.New(cfg.APIBaseURL, cfg.AgentToken)
	for _, snapshot := range snapshots {
		if err := c.Post("/soc/agents/heartbeat", snapshot.Heartbeat); err != nil {
			logger.Error("heartbeat upload failed", "agentId", snapshot.Heartbeat.AgentID, "error", err)
			return err
		}
	}
	result, _, err := queue.Flush(c, core.NewRateLimiter(cfg.RatePerSec), state, logger)
	if err != nil {
		return err
	}
	logger.Info("flush completed", "sent", result.Sent, "failed", result.Failed)
	postStats, err := queue.Stats()
	if err != nil {
		return err
	}
	applyHeartbeatStats(snapshots, postStats, result.Sent, result.Failed)
	for _, snapshot := range snapshots {
		if err := c.Post("/soc/agents/heartbeat", snapshot.Heartbeat); err != nil {
			logger.Error("post-flush heartbeat upload failed", "agentId", snapshot.Heartbeat.AgentID, "error", err)
			return err
		}
	}
	if result.Failed > 0 {
		return fmt.Errorf("queue flush left %d failed operations pending", result.Failed)
	}
	for _, snapshot := range snapshots {
		fmt.Printf("uploaded host agent snapshot: agentId=%s osType=%s sent=%d\n", snapshot.Registration.AgentID, snapshot.Registration.OSType, result.Sent)
	}
	return nil
}

func applyHeartbeatStats(snapshots []schema.Snapshot, stats core.QueueStats, sent int64, failed int64) {
	for i := range snapshots {
		snapshots[i].Heartbeat.QueueDepth = stats.Depth
		snapshots[i].Heartbeat.QueueBytes = stats.Bytes
		snapshots[i].Heartbeat.SentCount = sent
		snapshots[i].Heartbeat.FailedCount = failed
	}
}

func enqueueSnapshots(queue core.Queue, state core.State, snapshots []schema.Snapshot) error {
	for _, snapshot := range snapshots {
		if len(snapshot.Assets.Assets) > 0 {
			if err := queue.Enqueue("asset", "/soc/ingest/host/assets", snapshot.Assets, assetKeys(snapshot.Assets.Assets)); err != nil {
				return err
			}
		}
		events := filterHostEvents(snapshot.Events.Events, state)
		if len(events) > 0 {
			eventPayload := snapshot.Events
			eventPayload.Events = events
			if err := queue.Enqueue("event", "/soc/ingest/host/events", eventPayload, hostEventKeys(events)); err != nil {
				return err
			}
		}
		fimEvents := filterFIMEvents(snapshot.FIM.Events, state)
		if len(fimEvents) > 0 {
			fimPayload := snapshot.FIM
			fimPayload.Events = fimEvents
			if err := queue.Enqueue("fim", "/soc/ingest/host/fim", fimPayload, fimEventKeys(fimEvents)); err != nil {
				return err
			}
		}
		if len(snapshot.Baseline.Checks) > 0 {
			if err := queue.Enqueue("baseline", "/soc/ingest/host/baseline", snapshot.Baseline, baselineCheckKeys(snapshot.Baseline.Checks)); err != nil {
				return err
			}
		}
	}
	return nil
}

func printSnapshots(snapshots []schema.Snapshot) error {
	encoder := json.NewEncoder(os.Stdout)
	encoder.SetIndent("", "  ")
	for _, snapshot := range snapshots {
		if err := encoder.Encode(snapshot); err != nil {
			return err
		}
	}
	return nil
}

func filterHostEvents(events []schema.HostEvent, state core.State) []schema.HostEvent {
	out := make([]schema.HostEvent, 0, len(events))
	for _, event := range events {
		if !state.SeenKey(event.EventUID) {
			out = append(out, event)
		}
	}
	return out
}

func filterFIMEvents(events []schema.FIMEvent, state core.State) []schema.FIMEvent {
	out := make([]schema.FIMEvent, 0, len(events))
	for _, event := range events {
		if !state.SeenKey(event.EventUID) {
			out = append(out, event)
		}
	}
	return out
}

func hostEventKeys(events []schema.HostEvent) []string {
	keys := make([]string, 0, len(events))
	for _, event := range events {
		keys = append(keys, event.EventUID)
	}
	return keys
}

func fimEventKeys(events []schema.FIMEvent) []string {
	keys := make([]string, 0, len(events))
	for _, event := range events {
		keys = append(keys, event.EventUID)
	}
	return keys
}

func assetKeys(assets []schema.Asset) []string {
	keys := make([]string, 0, len(assets))
	for _, asset := range assets {
		keys = append(keys, "asset:"+firstNonEmpty(asset.PrimaryIP, asset.Hostname))
	}
	return keys
}

func baselineCheckKeys(checks []schema.BaselineCheck) []string {
	keys := make([]string, 0, len(checks))
	for _, check := range checks {
		keys = append(keys, "baseline:"+check.CheckCode+":"+firstNonEmpty(check.AssetIP, check.AssetName))
	}
	return keys
}

func firstNonEmpty(values ...string) string {
	for _, value := range values {
		if value != "" {
			return value
		}
	}
	return ""
}

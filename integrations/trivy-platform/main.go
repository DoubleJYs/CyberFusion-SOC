package main

import (
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/aquasecurity/trivy/pkg/platform"
)

func main() {
	mode := flag.String("mode", "serve", "serve or scan")
	addr := flag.String("addr", "", "listen address for serve mode")
	trivyBin := flag.String("trivy-bin", "", "path to trivy binary")
	taskType := flag.String("type", "image", "scan type: image, fs, repo, config, or sbom")
	target := flag.String("target", "", "authorized image reference, repository URL, SBOM file, or filesystem path")
	name := flag.String("name", "", "task name")
	project := flag.String("project", "default", "project name")
	group := flag.String("group", "default", "project group")
	authorized := flag.Bool("authorized", false, "confirm the target is authorized for scanning")
	maxCritical := flag.Int("max-critical", 0, "CI gate: maximum critical vulnerabilities")
	maxHigh := flag.Int("max-high", 10, "CI gate: maximum high vulnerabilities")
	maxMedium := flag.Int("max-medium", -1, "CI gate: maximum medium vulnerabilities, -1 disables")
	maxTotal := flag.Int("max-total", -1, "CI gate: maximum total vulnerabilities, -1 disables")
	failOnNoFix := flag.Bool("fail-on-no-fix", false, "CI gate: fail when a vulnerability has no fixed version")
	deniedLicenses := flag.String("deny-license", "", "comma-separated denied licenses for CI gate")
	flag.Parse()

	cwd, err := os.Getwd()
	if err != nil {
		log.Fatal(err)
	}
	cfg := platform.ConfigFromEnv(filepath.Clean(cwd))
	if *addr != "" {
		cfg.Addr = *addr
	}
	if *trivyBin != "" {
		cfg.TrivyPath = *trivyBin
	}
	if err := cfg.EnsureRuntimeDirs(); err != nil {
		log.Fatal(err)
	}
	store, err := platform.OpenStore(cfg.DatabasePath)
	if err != nil {
		log.Fatal(err)
	}

	switch *mode {
	case "serve":
		server := platform.NewServer(cfg, store)
		log.Printf("trivy platform listening on http://%s", cfg.Addr)
		log.Printf("runtime database: %s", cfg.DatabasePath)
		if err := http.ListenAndServe(cfg.Addr, server.Handler()); err != nil {
			log.Fatal(err)
		}
	case "scan":
		scanner := platform.NewScanner(cfg, store)
		ctx, cancel := scanContext(cfg.TaskTimeout)
		defer cancel()
		task, err := scanner.RunNewTask(ctx, platform.CreateTaskRequest{
			Name:       *name,
			Project:    *project,
			Group:      *group,
			Type:       platform.TaskType(*taskType),
			Target:     *target,
			Authorized: *authorized,
			Policy: platform.ScanPolicy{
				MaxCritical:    *maxCritical,
				MaxHigh:        *maxHigh,
				MaxMedium:      *maxMedium,
				MaxTotal:       *maxTotal,
				FailOnNoFix:    *failOnNoFix,
				DeniedLicenses: splitCSV(*deniedLicenses),
			},
		})
		data, marshalErr := json.MarshalIndent(task, "", "  ")
		if marshalErr == nil {
			fmt.Println(string(data))
		}
		if err != nil {
			log.Fatal(err)
		}
		if !task.Decision.Passed {
			os.Exit(1)
		}
	default:
		log.Fatalf("unsupported mode %q", *mode)
	}
}

func scanContext(timeout time.Duration) (context.Context, context.CancelFunc) {
	if timeout <= 0 {
		return context.WithCancel(context.Background())
	}
	return context.WithTimeout(context.Background(), timeout)
}

func splitCSV(value string) []string {
	if value == "" {
		return nil
	}
	var values []string
	for _, item := range strings.Split(value, ",") {
		item = strings.TrimSpace(item)
		if item != "" {
			values = append(values, item)
		}
	}
	return values
}

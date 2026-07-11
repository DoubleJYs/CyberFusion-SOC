package collector

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"

	"cyberfusion-agent/internal/schema"
)

// FIMWatchPath is delivered by the platform only after an administrator has
// published a path for this host. It intentionally has no option for file
// content transfer: this collector records metadata and a local metadata hash.
type FIMWatchPath struct {
	ID         int64
	Name       string
	Path       string
	Purpose    string
	Recursive  bool
	MaxEntries int
	Version    int
}

type fimSnapshotState struct {
	Entries map[string]fimSnapshotEntry `json:"entries"`
}

type fimSnapshotEntry struct {
	Hash string `json:"hash"`
	Mode string `json:"mode"`
}

func collectAuthorizedFIMChanges(opts Options, hostname, primaryIP string, now time.Time) ([]schema.FIMEvent, error) {
	previous, err := loadFIMState(opts.FIMStateFile)
	if err != nil {
		return nil, err
	}
	current := fimSnapshotState{Entries: map[string]fimSnapshotEntry{}}
	events := make([]schema.FIMEvent, 0)
	for _, watch := range opts.FIMWatchPaths {
		entries, err := scanAuthorizedPath(watch)
		if err != nil {
			return nil, fmt.Errorf("%s: %w", watch.Path, err)
		}
		for path, entry := range entries {
			current.Entries[path] = entry
		}
		if !hasWatchEntries(previous, watch.Path) {
			events = append(events, baselineWatchEvent(opts, hostname, primaryIP, now, watch, entries))
			continue
		}
		events = append(events, diffWatchEntries(opts, hostname, primaryIP, now, watch, previous.Entries, entries)...)
	}
	if err := saveFIMState(opts.FIMStateFile, current); err != nil {
		return nil, err
	}
	return events, nil
}

func scanAuthorizedPath(watch FIMWatchPath) (map[string]fimSnapshotEntry, error) {
	if watch.Path == "" {
		return nil, fmt.Errorf("empty watch path")
	}
	info, err := os.Stat(watch.Path)
	if err != nil {
		return nil, err
	}
	maxEntries := watch.MaxEntries
	if maxEntries <= 0 || maxEntries > 2000 {
		maxEntries = 500
	}
	entries := map[string]fimSnapshotEntry{}
	add := func(path string, item fs.FileInfo) bool {
		if len(entries) >= maxEntries {
			return false
		}
		entries[path] = fimSnapshotEntry{Hash: metadataHash(path, item), Mode: item.Mode().String()}
		return true
	}
	if !info.IsDir() {
		add(watch.Path, info)
		return entries, nil
	}
	if !add(watch.Path, info) {
		return entries, nil
	}
	err = filepath.WalkDir(watch.Path, func(path string, entry fs.DirEntry, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		if path == watch.Path {
			return nil
		}
		if entry.Type()&os.ModeSymlink != 0 {
			if entry.IsDir() {
				return filepath.SkipDir
			}
			return nil
		}
		if !watch.Recursive && filepath.Dir(path) != watch.Path {
			if entry.IsDir() {
				return filepath.SkipDir
			}
			return nil
		}
		item, infoErr := entry.Info()
		if infoErr != nil {
			return infoErr
		}
		if !add(path, item) {
			return fs.SkipAll
		}
		return nil
	})
	return entries, err
}

func diffWatchEntries(opts Options, hostname, primaryIP string, now time.Time, watch FIMWatchPath,
	previous, current map[string]fimSnapshotEntry) []schema.FIMEvent {
	paths := make([]string, 0, len(current)+len(previous))
	seen := map[string]struct{}{}
	for path := range current {
		seen[path] = struct{}{}
		paths = append(paths, path)
	}
	for path := range previous {
		if _, ok := seen[path]; !ok && withinWatch(path, watch.Path) {
			paths = append(paths, path)
		}
	}
	sort.Strings(paths)
	events := make([]schema.FIMEvent, 0)
	for _, path := range paths {
		before, hadBefore := previous[path]
		after, hasAfter := current[path]
		if !hadBefore && hasAfter {
			events = append(events, fimChangeEvent(opts, hostname, primaryIP, now, watch, path, "created", fimSnapshotEntry{}, after))
			continue
		}
		if hadBefore && !hasAfter {
			events = append(events, fimChangeEvent(opts, hostname, primaryIP, now, watch, path, "deleted", before, fimSnapshotEntry{}))
			continue
		}
		if before.Hash == after.Hash {
			continue
		}
		action := "modified"
		if before.Mode != after.Mode {
			action = "permission"
		}
		events = append(events, fimChangeEvent(opts, hostname, primaryIP, now, watch, path, action, before, after))
	}
	return events
}

func baselineWatchEvent(opts Options, hostname, primaryIP string, now time.Time, watch FIMWatchPath, entries map[string]fimSnapshotEntry) schema.FIMEvent {
	keys := make([]string, 0, len(entries))
	for key := range entries {
		keys = append(keys, key)
	}
	sort.Strings(keys)
	combined := sha256.New()
	for _, key := range keys {
		_, _ = fmt.Fprintf(combined, "%s|%s\n", key, entries[key].Hash)
	}
	sum := hex.EncodeToString(combined.Sum(nil))
	return schema.FIMEvent{
		EventUID: stableUID(opts.AgentID, "fim-baseline", watch.Path, sum), Action: "hash", Severity: "info",
		Hostname: hostname, AssetIP: primaryIP, FilePath: watch.Path, RuleName: "CyberFusion authorized directory baseline",
		AfterHash: sum, EventTime: schema.LocalTime(now),
		Attributes: map[string]any{"watchName": watch.Name, "watchPurpose": watch.Purpose, "watchPathId": watch.ID,
			"watchVersion": watch.Version, "entryCount": len(entries), "contentTransferred": false, "baseline": true},
	}
}

func fimChangeEvent(opts Options, hostname, primaryIP string, now time.Time, watch FIMWatchPath, path, action string,
	before, after fimSnapshotEntry) schema.FIMEvent {
	identity := after.Hash
	if identity == "" {
		identity = before.Hash
	}
	return schema.FIMEvent{
		EventUID: stableUID(opts.AgentID, "fim-change", action, path, identity), Action: action, Severity: "info",
		Hostname: hostname, AssetIP: primaryIP, FilePath: path, RuleName: "CyberFusion authorized file metadata change",
		BeforeHash: before.Hash, AfterHash: after.Hash, EventTime: schema.LocalTime(now),
		Attributes: map[string]any{"watchName": watch.Name, "watchPurpose": watch.Purpose, "watchPathId": watch.ID,
			"watchVersion": watch.Version, "contentTransferred": false, "metadataOnly": true},
	}
}

func metadataHash(path string, item fs.FileInfo) string {
	value := strings.Join([]string{path, item.Mode().String(), fmt.Sprint(item.Size()), fmt.Sprint(item.ModTime().UnixNano())}, "|")
	sum := sha256.Sum256([]byte(value))
	return hex.EncodeToString(sum[:])
}

func hasWatchEntries(state fimSnapshotState, watchPath string) bool {
	for path := range state.Entries {
		if withinWatch(path, watchPath) {
			return true
		}
	}
	return false
}

func withinWatch(path, root string) bool {
	rel, err := filepath.Rel(root, path)
	return err == nil && rel != ".." && !strings.HasPrefix(rel, ".."+string(filepath.Separator))
}

func loadFIMState(path string) (fimSnapshotState, error) {
	state := fimSnapshotState{Entries: map[string]fimSnapshotEntry{}}
	if path == "" {
		return state, nil
	}
	content, err := os.ReadFile(path)
	if os.IsNotExist(err) {
		return state, nil
	}
	if err != nil {
		return state, err
	}
	if err := json.Unmarshal(content, &state); err != nil {
		return state, err
	}
	if state.Entries == nil {
		state.Entries = map[string]fimSnapshotEntry{}
	}
	return state, nil
}

func saveFIMState(path string, state fimSnapshotState) error {
	if path == "" {
		return nil
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		return err
	}
	content, err := json.Marshal(state)
	if err != nil {
		return err
	}
	return os.WriteFile(path, append(content, '\n'), 0o600)
}

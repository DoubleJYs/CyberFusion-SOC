package collector

import (
	"testing"

	"cyberfusion-agent/internal/schema"
)

func TestCollectionProfilesLimitExpectedEvidence(t *testing.T) {
	cases := []struct {
		profile       string
		wantEvents    int
		wantFIM       int
		wantBaselines int
	}{
		{profile: "full", wantEvents: 1, wantFIM: 1, wantBaselines: 1},
		{profile: "host-log", wantEvents: 1, wantFIM: 0, wantBaselines: 0},
		{profile: "patrol-audit", wantEvents: 1, wantFIM: 0, wantBaselines: 1},
		{profile: "file-integrity", wantEvents: 0, wantFIM: 1, wantBaselines: 1},
		{profile: "baseline-audit", wantEvents: 0, wantFIM: 0, wantBaselines: 1},
	}

	for _, tc := range cases {
		t.Run(tc.profile, func(t *testing.T) {
			snapshot := schema.Snapshot{
				Events:   schema.EventIngest{Events: []schema.HostEvent{{EventUID: "event"}}},
				FIM:      schema.FIMIngest{Events: []schema.FIMEvent{{EventUID: "fim"}}},
				Baseline: schema.BaselineIngest{Checks: []schema.BaselineCheck{{CheckCode: "baseline"}}},
			}

			applyCollectionProfile(&snapshot, tc.profile)

			if got := len(snapshot.Events.Events); got != tc.wantEvents {
				t.Fatalf("events = %d, want %d", got, tc.wantEvents)
			}
			if got := len(snapshot.FIM.Events); got != tc.wantFIM {
				t.Fatalf("fim = %d, want %d", got, tc.wantFIM)
			}
			if got := len(snapshot.Baseline.Checks); got != tc.wantBaselines {
				t.Fatalf("baselines = %d, want %d", got, tc.wantBaselines)
			}
		})
	}
}

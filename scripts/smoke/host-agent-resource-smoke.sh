#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
AGENT_DIR="$PROJECT_ROOT/agent"
MAX_RUNTIME_SYS_MB="${CYBERFUSION_AGENT_MAX_RUNTIME_SYS_MB:-${CYBERFUSION_AGENT_MAX_RSS_MB:-100}}"
export GOCACHE="${GOCACHE:-/private/tmp/cyberfusion-agent-go-build}"

tmp_parent="${CYBERFUSION_SMOKE_TMPDIR:-${TMPDIR:-/tmp}}"
tmp_dir="$(mktemp -d "${tmp_parent%/}/cyberfusion-host-agent-resource.XXXXXX")"
trap 'rm -rf "$tmp_dir"' EXIT

info() {
  printf '[INFO] %s\n' "$1"
}

pass() {
  printf '[PASS] %s\n' "$1"
}

fail() {
  printf '[FAIL] %s\n' "$1" >&2
  exit 1
}

require_positive_int() {
  local name="$1"
  local value="$2"
  if [[ ! "$value" =~ ^[1-9][0-9]*$ ]]; then
    fail "${name} must be a positive integer"
  fi
}

report_value() {
  local report_file="$1"
  local field="$2"
  python3 - "$report_file" "$field" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as f:
    data = json.load(f)
value = data.get(sys.argv[2])
if not isinstance(value, int):
    raise SystemExit(1)
print(value)
PY
}

assert_runtime_sys_under_limit() {
  local label="$1"
  local report_file="$2"
  local sys_bytes sys_mb limit_bytes samples

  [[ -s "$report_file" ]] || fail "${label} resource report was not created"
  sys_bytes="$(report_value "$report_file" maxSysBytes)" || fail "${label} resource report is missing maxSysBytes"
  samples="$(report_value "$report_file" samples)" || fail "${label} resource report is missing samples"
  require_positive_int "${label} maxSysBytes" "$sys_bytes"
  require_positive_int "${label} samples" "$samples"
  limit_bytes=$((MAX_RUNTIME_SYS_MB * 1024 * 1024))
  sys_mb=$(((sys_bytes + 1024 * 1024 - 1) / 1024 / 1024))

  if (( sys_bytes > limit_bytes )); then
    cat "$report_file" >&2 || true
    fail "${label} Go runtime Sys ${sys_mb} MB exceeded ${MAX_RUNTIME_SYS_MB} MB"
  fi

  pass "${label} Go runtime Sys ${sys_mb} MB <= ${MAX_RUNTIME_SYS_MB} MB across ${samples} samples"
}

run_with_resource_report() {
  local label="$1"
  local stdout_log="$2"
  local stderr_log="$3"
  local report_file="$4"
  shift 4

  info "Running ${label}"
  "$@" --resource-report-file "$report_file" >"$stdout_log" 2>"$stderr_log" || {
    cat "$stdout_log" >&2 || true
    cat "$stderr_log" >&2 || true
    fail "${label} command failed"
  }
  assert_runtime_sys_under_limit "$label" "$report_file"
}

main() {
  require_positive_int "CYBERFUSION_AGENT_MAX_RUNTIME_SYS_MB" "$MAX_RUNTIME_SYS_MB"
  [[ -d "$AGENT_DIR" ]] || fail "agent directory not found: $AGENT_DIR"
  command -v python3 >/dev/null 2>&1 || fail "python3 is required for parsing resource reports"

  local host_goos host_goarch binary
  host_goos="$(go env GOOS)"
  host_goarch="$(go env GOARCH)"
  binary="$tmp_dir/cyberfusion-agent-${host_goos}-${host_goarch}"

  info "Building Host Agent binary for ${host_goos}/${host_goarch}"
  (
    cd "$AGENT_DIR"
    GOOS="$host_goos" GOARCH="$host_goarch" go build -o "$binary" ./cmd/cyberfusion-agent
  )
  [[ -s "$binary" ]] || fail "Host Agent binary was not created"
  pass "Host Agent binary built"

  "$binary" --mode fixture --fixture-os all --dry-run >"$tmp_dir/dry-run.out"
  pass "fixture dry-run completed"

  run_with_resource_report \
    "bounded daemon outage loop" \
    "$tmp_dir/daemon.out" \
    "$tmp_dir/daemon.err" \
    "$tmp_dir/daemon-resource.json" \
    "$binary" \
      --mode fixture \
      --fixture-os all \
      --agent-token local-resource-smoke-token \
      --api-base-url http://127.0.0.1:9/api \
      --runtime-dir "$tmp_dir/runtime" \
      --once=false \
      --interval 3s \
      --max-cycles 3

  pass "Host Agent resource smoke completed"
  info "This checks Go runtime memory stats. OS RSS, Windows working set, and long-idle CPU must still be measured on real launchd/Windows Service hosts."
}

main "$@"

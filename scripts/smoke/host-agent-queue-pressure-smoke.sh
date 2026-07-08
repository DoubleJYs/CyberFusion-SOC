#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
API_BASE_URL="${CYBERFUSION_API_BASE:-http://127.0.0.1:18080/api}"
BAD_API_BASE_URL="${CYBERFUSION_QUEUE_PRESSURE_BAD_API_BASE:-http://127.0.0.1:9/api}"
ADMIN_USER="${CYBERFUSION_ADMIN_USER:-admin}"
DEMO_PASSWORD="${CYBERFUSION_DEMO_PASSWORD:-Admin@123456}"
ADMIN_PASSWORD="${CYBERFUSION_ADMIN_PASSWORD:-$DEMO_PASSWORD}"
AGENT_ID="${CYBERFUSION_QUEUE_PRESSURE_AGENT_ID:-queue-pressure-macos-agent-$(date +%Y%m%d%H%M%S)}"
PRESSURE_CYCLES="${CYBERFUSION_QUEUE_PRESSURE_CYCLES:-8}"
KEEP_SMOKE_DATA="${CYBERFUSION_KEEP_SMOKE_DATA:-0}"
export GOCACHE="${GOCACHE:-/private/tmp/cyberfusion-agent-go-build}"

tmp_parent="${CYBERFUSION_SMOKE_TMPDIR:-${TMPDIR:-/tmp}}"
tmp_dir="$(mktemp -d "${tmp_parent%/}/cyberfusion-host-agent-queue-pressure.XXXXXX")"
runtime_dir="$tmp_dir/runtime"
agent_bin="$tmp_dir/cyberfusion-agent"
trap 'rm -rf "$tmp_dir"' EXIT

LAST_BODY=""
LAST_STATUS=""

pass() {
  printf '[PASS] %s\n' "$1"
}

fail() {
  printf '[FAIL] %s\n' "$1" >&2
  exit 1
}

api_call() {
  local method="$1"
  local path="$2"
  local token="${3:-}"
  local data_file="${4:-}"
  local response
  local -a curl_args=(
    -sS
    --retry 5
    --retry-connrefused
    --retry-delay 1
    --connect-timeout 3
    --max-time 30
    -X "$method"
    "${API_BASE_URL%/}${path}"
  )
  if [[ -n "$token" ]]; then
    curl_args+=(-H "Authorization: Bearer ${token}")
  fi
  if [[ -n "$data_file" ]]; then
    curl_args+=(-H 'Content-Type: application/json' --data @"$data_file")
  fi
  response="$(curl "${curl_args[@]}" -w 'CYBERFUSION_HTTP_STATUS:%{http_code}' || true)"
  LAST_STATUS="${response##*CYBERFUSION_HTTP_STATUS:}"
  LAST_BODY="${response%CYBERFUSION_HTTP_STATUS:*}"
  if [[ "$response" == "$LAST_STATUS" ]]; then
    LAST_BODY=""
  fi
  [[ "$LAST_STATUS" != "000" ]]
}

json_get() {
  local expr="$1"
  printf '%s' "$LAST_BODY" | python3 -c '
import json, sys
d = json.load(sys.stdin)
safe = {"__builtins__": {}, "d": d, "len": len, "any": any, "all": all}
print(eval(sys.argv[1], safe, {}) or "")
' "$expr"
}

expect_success() {
  local label="$1"
  local code
  code="$(json_get 'd.get("code")')"
  if [[ "$LAST_STATUS" == "200" && "$code" == "SUCCESS" ]]; then
    pass "$label"
  else
    fail "$label returned HTTP ${LAST_STATUS}: ${LAST_BODY}"
  fi
}

login() {
  local candidate token
  for candidate in "$ADMIN_PASSWORD" admin123; do
    printf '{"username":"%s","password":"%s"}' "$ADMIN_USER" "$candidate" >"$tmp_dir/login.json"
    api_call POST /auth/login "" "$tmp_dir/login.json" || continue
    token="$(json_get '(d.get("data") or {}).get("accessToken")')"
    if [[ -n "$token" ]]; then
      printf '%s\n' "$token"
      return 0
    fi
  done
  return 1
}

queue_depth() {
  local pending_dir="$runtime_dir/queue/pending"
  if [[ ! -d "$pending_dir" ]]; then
    printf '0\n'
    return
  fi
  find "$pending_dir" -type f -name '*.json' | wc -l | tr -d ' '
}

queue_bytes() {
  local pending_dir="$runtime_dir/queue/pending"
  if [[ ! -d "$pending_dir" ]]; then
    printf '0\n'
    return
  fi
  find "$pending_dir" -type f -name '*.json' -print0 | xargs -0 stat -f '%z' 2>/dev/null | awk '{s += $1} END {print s + 0}'
}

run_fixture_cycle() {
  local api_base="$1"
  local agent_token="$2"
  local run_id="$3"
  CYBERFUSION_AGENT_TOKEN="$agent_token" "$agent_bin" \
    --mode fixture \
    --fixture-os macos \
    --os-type macos \
    --agent-id "$AGENT_ID" \
    --runtime-dir "$runtime_dir" \
    --api-base-url "$api_base" \
    --fixture-run-id "$run_id" \
    >"$tmp_dir/${run_id}.out" 2>"$tmp_dir/${run_id}.err"
}

assert_agent_post_flush_status() {
  local admin_token="$1"
  local min_sent="$2"
  api_call GET "/soc/agents?osType=macos" "$admin_token"
  expect_success "agent list lookup"
  printf '%s' "$LAST_BODY" >"$tmp_dir/agents.json"
  python3 - "$tmp_dir/agents.json" "$AGENT_ID" "$min_sent" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as f:
    payload = json.load(f)
agent_id = sys.argv[2]
min_sent = int(sys.argv[3])
agents = payload.get("data") or []
match = next((item for item in agents if item.get("agentId") == agent_id), None)
if not match:
    raise SystemExit(f"agent {agent_id} not found")
errors = []
if match.get("queueDepth") != 0:
    errors.append(f"queueDepth={match.get('queueDepth')}")
if match.get("queueBytes") != 0:
    errors.append(f"queueBytes={match.get('queueBytes')}")
if (match.get("sentCount") or 0) < min_sent:
    errors.append(f"sentCount={match.get('sentCount')} < {min_sent}")
if (match.get("failedCount") or 0) != 0:
    errors.append(f"failedCount={match.get('failedCount')}")
if errors:
    raise SystemExit("; ".join(errors))
print(f"[PASS] pressure replay heartbeat exposes sent={match.get('sentCount')} queueDepth={match.get('queueDepth')}")
PY
}

assert_total_at_least() {
  local label="$1"
  local path="$2"
  local min_total="$3"
  local admin_token="$4"
  api_call GET "$path" "$admin_token"
  expect_success "$label lookup"
  local total
  total="$(json_get '(d.get("data") or {}).get("total", 0)')"
  [[ "${total:-0}" -ge "$min_total" ]] || fail "$label total ${total:-0} < ${min_total}"
  pass "$label total ${total}"
}

main() {
  [[ "$PRESSURE_CYCLES" =~ ^[0-9]+$ ]] || fail "CYBERFUSION_QUEUE_PRESSURE_CYCLES must be numeric"
  [[ "$PRESSURE_CYCLES" -ge 2 ]] || fail "CYBERFUSION_QUEUE_PRESSURE_CYCLES must be >= 2"
  [[ "$PRESSURE_CYCLES" -le 50 ]] || fail "CYBERFUSION_QUEUE_PRESSURE_CYCLES must be <= 50"

  api_call GET /health
  expect_success "backend health"

  local admin_token
  admin_token="$(login || true)"
  [[ -n "$admin_token" ]] || fail "admin login failed"
  pass "admin login succeeded"

  (
    cd "$PROJECT_ROOT/agent"
    go build -o "$agent_bin" ./cmd/cyberfusion-agent
  )
  pass "Go Agent pressure binary built"

  printf '{"agentId":"%s","agentName":"%s","hostname":"mac-dev-host","osType":"macos","osVersion":"queue-pressure-smoke","architecture":"fixture","agentVersion":"0.1.0-dev","labels":{"smoke":"queue-pressure","agent":"go","fixture":"true"}}' \
    "$AGENT_ID" "$AGENT_ID" >"$tmp_dir/register.json"
  api_call POST /soc/agents/register "$admin_token" "$tmp_dir/register.json"
  expect_success "queue pressure agent registered"
  agent_token="$(json_get '(d.get("data") or {}).get("agentToken")')"
  [[ -n "$agent_token" ]] || fail "agent token missing"

  local cycle run_id depth_after_failure bytes_after_failure
  cycle=1
  while [[ "$cycle" -le "$PRESSURE_CYCLES" ]]; do
    run_id="$(printf 'cycle-%03d' "$cycle")"
    if run_fixture_cycle "$BAD_API_BASE_URL" "$agent_token" "$run_id"; then
      fail "agent upload unexpectedly succeeded against bad backend URL on ${run_id}"
    fi
    cycle=$((cycle + 1))
  done

  depth_after_failure="$(queue_depth)"
  bytes_after_failure="$(queue_bytes)"
  expected_min_depth=$((1 + PRESSURE_CYCLES * 3))
  [[ "$depth_after_failure" -ge "$expected_min_depth" ]] || fail "queue depth ${depth_after_failure} < expected ${expected_min_depth}"
  [[ "$bytes_after_failure" -gt 0 ]] || fail "queue bytes did not grow"
  pass "queue retained ${depth_after_failure} operations / ${bytes_after_failure} bytes after ${PRESSURE_CYCLES} outage cycles"

  run_fixture_cycle "$API_BASE_URL" "$agent_token" "recovery"
  depth_after_replay="$(queue_depth)"
  [[ "$depth_after_replay" -eq 0 ]] || fail "queue still has ${depth_after_replay} operations after replay"
  pass "queue flushed after backend recovery"

  assert_agent_post_flush_status "$admin_token" "$expected_min_depth"
  assert_total_at_least "pressure replay external events" "/soc/external-events?pageNum=1&pageSize=10&keyword=${AGENT_ID}" $((PRESSURE_CYCLES * 3)) "$admin_token"
  assert_total_at_least "pressure replay unified alerts" "/soc/alerts?pageNum=1&pageSize=10&keyword=${AGENT_ID}" $((PRESSURE_CYCLES * 2)) "$admin_token"

  api_call POST /soc/incidents/correlate "$admin_token"
  expect_success "pressure replay incident correlation"
  assert_total_at_least "pressure replay incident clusters" "/soc/incidents?pageNum=1&pageSize=10&keyword=${AGENT_ID}" 1 "$admin_token"

  if [[ "$KEEP_SMOKE_DATA" == "1" ]]; then
    printf '[INFO] keeping queue pressure fixture data for debugging: agentId=%s\n' "$AGENT_ID"
  else
    api_call DELETE /soc/demo-range/demo-data "$admin_token"
    expect_success "queue pressure fixture data cleaned"
  fi

  printf '[SUMMARY] queue pressure passed agent=%s cycles=%s queuedBeforeReplay=%s bytesBeforeReplay=%s queueAfterReplay=%s\n' \
    "$AGENT_ID" "$PRESSURE_CYCLES" "$depth_after_failure" "$bytes_after_failure" "$depth_after_replay"
}

main "$@"

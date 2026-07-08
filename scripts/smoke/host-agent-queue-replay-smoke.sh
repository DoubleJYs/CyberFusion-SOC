#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
API_BASE_URL="${CYBERFUSION_API_BASE:-http://127.0.0.1:18080/api}"
BAD_API_BASE_URL="${CYBERFUSION_QUEUE_REPLAY_BAD_API_BASE:-http://127.0.0.1:9/api}"
ADMIN_USER="${CYBERFUSION_ADMIN_USER:-admin}"
DEMO_PASSWORD="${CYBERFUSION_DEMO_PASSWORD:-Admin@123456}"
ADMIN_PASSWORD="${CYBERFUSION_ADMIN_PASSWORD:-$DEMO_PASSWORD}"
AGENT_ID="${CYBERFUSION_QUEUE_REPLAY_AGENT_ID:-queue-replay-macos-agent-$(date +%Y%m%d%H%M%S)}"
KEEP_SMOKE_DATA="${CYBERFUSION_KEEP_SMOKE_DATA:-0}"
export GOCACHE="${GOCACHE:-/private/tmp/cyberfusion-agent-go-build}"

tmp_parent="${CYBERFUSION_SMOKE_TMPDIR:-${TMPDIR:-/tmp}}"
tmp_dir="$(mktemp -d "${tmp_parent%/}/cyberfusion-host-agent-queue.XXXXXX")"
runtime_dir="$tmp_dir/runtime"
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
  local body_file="${tmp_dir}/api-response-body.json"
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
  : >"$body_file"
  LAST_STATUS="$(curl "${curl_args[@]}" -o "$body_file" -w '%{http_code}' || true)"
  LAST_BODY="$(cat "$body_file" 2>/dev/null || true)"
  [[ "$LAST_STATUS" != "000" ]]
}

json_get() {
  local expr="$1"
  printf '%s' "$LAST_BODY" | python3 -c '
import json, sys
d = json.load(sys.stdin)
print(eval(sys.argv[1], {"__builtins__": {}}, {"d": d, "len": len, "any": any, "all": all}) or "")
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

run_agent_fixture() {
  local api_base="$1"
  local agent_token="$2"
  (
    cd "$PROJECT_ROOT/agent"
    CYBERFUSION_AGENT_TOKEN="$agent_token" go run ./cmd/cyberfusion-agent \
      --mode fixture \
      --fixture-os macos \
      --os-type macos \
      --agent-id "$AGENT_ID" \
      --runtime-dir "$runtime_dir" \
      --api-base-url "$api_base" >/dev/null
  )
}

assert_agent_post_flush_status() {
  local admin_token="$1"
  api_call GET "/soc/agents?osType=macos" "$admin_token"
  expect_success "agent list lookup"
  printf '%s' "$LAST_BODY" >"$tmp_dir/agents.json"
  python3 - "$tmp_dir/agents.json" "$AGENT_ID" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as f:
    payload = json.load(f)
agent_id = sys.argv[2]
agents = payload.get("data") or []
match = next((item for item in agents if item.get("agentId") == agent_id), None)
if not match:
    raise SystemExit(f"agent {agent_id} not found")
errors = []
if match.get("queueDepth") != 0:
    errors.append(f"queueDepth={match.get('queueDepth')}")
if match.get("queueBytes") != 0:
    errors.append(f"queueBytes={match.get('queueBytes')}")
if (match.get("sentCount") or 0) < 4:
    errors.append(f"sentCount={match.get('sentCount')}")
if (match.get("failedCount") or 0) != 0:
    errors.append(f"failedCount={match.get('failedCount')}")
if errors:
    raise SystemExit("; ".join(errors))
print(f"[PASS] post-flush heartbeat exposes queue replay stats: sent={match.get('sentCount')} queueDepth={match.get('queueDepth')}")
PY
}

main() {
  api_call GET /health
  expect_success "backend health"

  local admin_token
  admin_token="$(login || true)"
  [[ -n "$admin_token" ]] || fail "admin login failed"
  pass "admin login succeeded"

  printf '{"agentId":"%s","agentName":"%s","hostname":"%s","osType":"macos","osVersion":"queue-replay-smoke","architecture":"fixture","agentVersion":"0.1.0-dev","labels":{"smoke":"queue-replay","agent":"go"}}' \
    "$AGENT_ID" "$AGENT_ID" "$AGENT_ID" >"$tmp_dir/register.json"
  api_call POST /soc/agents/register "$admin_token" "$tmp_dir/register.json"
  expect_success "queue replay agent registered"
  agent_token="$(json_get '(d.get("data") or {}).get("agentToken")')"
  [[ -n "$agent_token" ]] || fail "agent token missing"

  if run_agent_fixture "$BAD_API_BASE_URL" "$agent_token"; then
    fail "agent upload unexpectedly succeeded against bad backend URL"
  fi
  depth_after_failure="$(queue_depth)"
  [[ "$depth_after_failure" -gt 0 ]] || fail "queue did not retain operations after backend outage"
  pass "queue retained ${depth_after_failure} operations after backend outage"

  run_agent_fixture "$API_BASE_URL" "$agent_token"
  depth_after_replay="$(queue_depth)"
  [[ "$depth_after_replay" -eq 0 ]] || fail "queue still has ${depth_after_replay} operations after replay"
  pass "queue flushed after backend recovery"

  assert_agent_post_flush_status "$admin_token"

  api_call GET "/soc/external-events?pageNum=1&pageSize=10&keyword=${AGENT_ID}" "$admin_token"
  expect_success "replayed host events lookup"
  total="$(json_get '(d.get("data") or {}).get("total", 0)')"
  [[ "$total" -ge 1 ]] || fail "replayed host event not visible"
  pass "replayed host event is visible"

  if [[ "$KEEP_SMOKE_DATA" == "1" ]]; then
    printf '[INFO] keeping queue replay fixture data for debugging: agentId=%s\n' "$AGENT_ID"
  else
    api_call DELETE /soc/demo-range/demo-data "$admin_token"
    expect_success "queue replay fixture data cleaned"
  fi

  printf '[SUMMARY] agentId=%s queuedBeforeReplay=%s queueAfterReplay=%s\n' "$AGENT_ID" "$depth_after_failure" "$depth_after_replay"
}

main "$@"

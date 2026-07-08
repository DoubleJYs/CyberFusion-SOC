#!/usr/bin/env bash
set -euo pipefail

API_BASE_URL="${CYBERFUSION_API_BASE:-http://127.0.0.1:18080/api}"
ADMIN_USER="${CYBERFUSION_ADMIN_USER:-admin}"
DEMO_PASSWORD="${CYBERFUSION_DEMO_PASSWORD:-Admin@123456}"
ADMIN_PASSWORD="${CYBERFUSION_ADMIN_PASSWORD:-$DEMO_PASSWORD}"
KEEP_SMOKE_DATA="${CYBERFUSION_KEEP_SMOKE_DATA:-0}"
export GOCACHE="${GOCACHE:-/private/tmp/cyberfusion-agent-go-build}"
tmp_parent="${CYBERFUSION_SMOKE_TMPDIR:-${TMPDIR:-/tmp}}"
tmp_dir="$(mktemp -d "${tmp_parent%/}/cyberfusion-host-agent-go-smoke.XXXXXX")"
trap 'rm -rf "$tmp_dir"' EXIT

pass_count=0
fail_count=0
LAST_BODY=""
LAST_STATUS=""
REGISTERED_AGENT_TOKEN=""

pass() {
  pass_count=$((pass_count + 1))
  printf '[PASS] %s\n' "$1"
}

fail() {
  fail_count=$((fail_count + 1))
  printf '[FAIL] %s\n' "$1" >&2
}

api_call() {
  local method="$1"
  local path="$2"
  local token="${3:-}"
  local agent_token="${4:-}"
  local data_file="${5:-}"
  local response
  local -a curl_args=(
    -sS
    --retry 5
    --retry-connrefused
    --retry-delay 1
    --connect-timeout 3
    --max-time 20
    -X "$method"
    "${API_BASE_URL}${path}"
  )
  if [[ -n "$token" ]]; then
    curl_args+=(-H "Authorization: Bearer ${token}")
  fi
  if [[ -n "$agent_token" ]]; then
    curl_args+=(-H "X-CyberFusion-Agent-Token: ${agent_token}")
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
  python3 -c '
import json, sys
d = json.load(sys.stdin)
print(eval(sys.argv[1], {}, {"d": d}) or "")
' "$expr"
}

json_code() {
  printf '%s' "$LAST_BODY" | json_get 'd.get("code")'
}

json_data() {
  local expr="$1"
  printf '%s' "$LAST_BODY" | json_get '(d.get("data") or {}).get("'$expr'")'
}

expect_success() {
  local label="$1"
  if [[ "$LAST_STATUS" == "200" && "$(json_code)" == "SUCCESS" ]]; then
    pass "$label"
  else
    fail "$label returned HTTP ${LAST_STATUS}: ${LAST_BODY}"
  fi
}

login() {
  local candidate token
  for candidate in "$ADMIN_PASSWORD" admin123; do
    printf '{"username":"%s","password":"%s"}' "$ADMIN_USER" "$candidate" >"$tmp_dir/login.json"
    api_call POST /auth/login "" "" "$tmp_dir/login.json" || continue
    token="$(printf '%s' "$LAST_BODY" | json_get '(d.get("data") or {}).get("accessToken")')"
    if [[ -n "$token" ]]; then
      printf '%s\n' "$token"
      return 0
    fi
  done
  return 1
}

write_register_payload() {
  local file="$1" agent_id="$2" os_type="$3" hostname="$4" ip="$5"
  printf '{"agentId":"%s","agentName":"%s","hostname":"%s","osType":"%s","osVersion":"fixture","architecture":"arm64/x64","agentVersion":"0.1.0-dev","ipAddresses":["%s"],"macAddresses":["00:00:5e:00:53:01"],"labels":{"fixture":"true","agent":"go","platform":"%s"}}' \
    "$agent_id" "$hostname" "$hostname" "$os_type" "$ip" "$os_type" >"$file"
}

register_agent() {
  local admin_token="$1" agent_id="$2" os_type="$3" hostname="$4" ip="$5"
  write_register_payload "$tmp_dir/${agent_id}-register.json" "$agent_id" "$os_type" "$hostname" "$ip"
  api_call POST /soc/agents/register "$admin_token" "" "$tmp_dir/${agent_id}-register.json"
  expect_success "${os_type} Go agent registered"
  REGISTERED_AGENT_TOKEN="$(json_data agentToken)"
  [[ -n "$REGISTERED_AGENT_TOKEN" ]] || fail "${os_type} Go agent token missing"
}

run_go_agent() {
  local agent_id="$1" os_type="$2" agent_token="$3"
  (
    cd agent
    CYBERFUSION_AGENT_TOKEN="$agent_token" go run ./cmd/cyberfusion-agent \
      --mode fixture \
      --os-type "$os_type" \
      --agent-id "$agent_id" \
      --runtime-dir "$tmp_dir/${agent_id}-runtime" \
      --api-base-url "$API_BASE_URL" >/dev/null
  )
  pass "${os_type} Go agent fixture uploaded"
}

run_go_agent_daemon_limited() {
  local agent_id="$1" os_type="$2" agent_token="$3"
  (
    cd agent
    CYBERFUSION_AGENT_TOKEN="$agent_token" go run ./cmd/cyberfusion-agent \
      --mode fixture \
      --os-type "$os_type" \
      --agent-id "$agent_id" \
      --runtime-dir "$tmp_dir/${agent_id}-daemon-runtime" \
      --api-base-url "$API_BASE_URL" \
      --once=false \
      --interval 1s \
      --max-cycles 2 >/dev/null
  )
  pass "${os_type} Go agent daemon mode completed bounded cycles"
}

(
  cd agent
  go test ./...
  go run ./cmd/cyberfusion-agent --mode fixture --fixture-os all --dry-run >/dev/null
)
pass "Go agent builds and renders both fixtures"

ADMIN_TOKEN="$(login || true)"
if [[ -z "$ADMIN_TOKEN" ]]; then
  fail "admin login failed"
  exit 1
fi
pass "admin login succeeded"

register_agent "$ADMIN_TOKEN" "go-macos-fixture-agent" "macos" "mac-dev-host" "192.0.2.10"
MAC_TOKEN="$REGISTERED_AGENT_TOKEN"
register_agent "$ADMIN_TOKEN" "go-windows-fixture-agent" "windows" "win-docker-host" "192.0.2.20"
WIN_TOKEN="$REGISTERED_AGENT_TOKEN"

run_go_agent "go-macos-fixture-agent" "macos" "$MAC_TOKEN"
run_go_agent "go-windows-fixture-agent" "windows" "$WIN_TOKEN"
run_go_agent_daemon_limited "go-macos-fixture-agent" "macos" "$MAC_TOKEN"

if [[ "$KEEP_SMOKE_DATA" == "1" ]]; then
  printf '[INFO] keeping Go Host Agent fixture data for debugging\n'
else
  api_call DELETE /soc/demo-range/demo-data "$ADMIN_TOKEN"
  expect_success "Go Host Agent fixture data cleaned"
fi

printf '[SUMMARY] PASS=%s FAIL=%s\n' "$pass_count" "$fail_count"
[[ "$fail_count" -eq 0 ]]

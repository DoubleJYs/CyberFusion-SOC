#!/usr/bin/env bash
set -euo pipefail

API_BASE_URL="${CYBERFUSION_API_BASE:-http://127.0.0.1:18080/api}"
ADMIN_USER="${CYBERFUSION_ADMIN_USER:-admin}"
DEMO_PASSWORD="${CYBERFUSION_DEMO_PASSWORD:-Admin@123456}"
ADMIN_PASSWORD="${CYBERFUSION_ADMIN_PASSWORD:-$DEMO_PASSWORD}"
KEEP_SMOKE_DATA="${CYBERFUSION_KEEP_SMOKE_DATA:-0}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --api-base-url)
      API_BASE_URL="$2"
      shift 2
      ;;
    --help|-h)
      cat <<USAGE
Usage: scripts/smoke/host-agent-ingest-smoke.sh [--api-base-url URL]

Exercises Phase 0 Host Agent ingest APIs without clearing data:
- admin registers macOS and Windows fixture agents
- both fixtures use the same ingest API contract
- assets/events/FIM/baseline data are accepted
- duplicate eventUid is counted as duplicate, not inserted again
- invalid agent token is rejected

Environment:
  CYBERFUSION_API_BASE        Default: http://127.0.0.1:18080/api
  CYBERFUSION_ADMIN_USER      Default: admin
  CYBERFUSION_DEMO_PASSWORD   Shared local demo password fallback
  CYBERFUSION_ADMIN_PASSWORD  Admin password override
  CYBERFUSION_KEEP_SMOKE_DATA Set to 1 to keep fixture data for debugging
  CYBERFUSION_SMOKE_TMPDIR    Parent temp directory, useful for sandboxed runs
USAGE
      exit 0
      ;;
    *)
      printf 'Unknown argument: %s\n' "$1" >&2
      exit 2
      ;;
  esac
done

tmp_parent="${CYBERFUSION_SMOKE_TMPDIR:-${TMPDIR:-/tmp}}"
tmp_dir="$(mktemp -d "${tmp_parent%/}/cyberfusion-host-agent-smoke.XXXXXX")"
trap 'rm -rf "$tmp_dir"' EXIT

pass_count=0
fail_count=0
LAST_BODY=""
LAST_STATUS=""

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
  local body_file="${tmp_dir}/api-response-body.json"
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
  : >"$body_file"
  LAST_STATUS="$(curl "${curl_args[@]}" -o "$body_file" -w '%{http_code}' || true)"
  LAST_BODY="$(cat "$body_file" 2>/dev/null || true)"
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
  printf '{"agentId":"%s","agentName":"%s","hostname":"%s","osType":"%s","osVersion":"fixture","architecture":"arm64/x64","agentVersion":"0.1.0-dev","ipAddresses":["%s"],"macAddresses":["00:00:5e:00:53:01"],"labels":{"fixture":"true","platform":"%s"}}' \
    "$agent_id" "$hostname" "$hostname" "$os_type" "$ip" "$os_type" >"$file"
}

write_asset_payload() {
  local file="$1" agent_id="$2" os_type="$3" hostname="$4" ip="$5"
  printf '{"agentId":"%s","batchId":"HOST-%s-ASSET","osType":"%s","assets":[{"hostname":"%s","primaryIp":"%s","osType":"%s","osVersion":"fixture","ipAddresses":["%s"],"macAddresses":["00:00:5e:00:53:01"],"facts":{"source":"host-agent-smoke"}}]}' \
    "$agent_id" "$agent_id" "$os_type" "$hostname" "$ip" "$os_type" "$ip" >"$file"
}

write_event_payload() {
  local file="$1" agent_id="$2" os_type="$3" hostname="$4" ip="$5" event_uid="$6" module="$7" event_type="$8" rule_id="$9"
  printf '{"agentId":"%s","batchId":"HOST-%s-EVENT","osType":"%s","events":[{"eventUid":"%s","sourceModule":"%s","eventType":"%s","severity":"high","ruleId":"%s","ruleName":"Host fixture event","assetName":"%s","assetIp":"%s","action":"review","raw":{"fixture":true},"normalized":{"fixture":true,"agentId":"%s"}}]}' \
    "$agent_id" "$agent_id" "$os_type" "$event_uid" "$module" "$event_type" "$rule_id" "$hostname" "$ip" "$agent_id" >"$file"
}

write_fim_payload() {
  local file="$1" agent_id="$2" os_type="$3" hostname="$4" ip="$5" event_uid="$6"
  printf '{"agentId":"%s","batchId":"HOST-%s-FIM","osType":"%s","events":[{"eventUid":"%s","action":"modified","severity":"medium","hostname":"%s","assetIp":"%s","filePath":"/tmp/cyberfusion-fixture.conf","ruleName":"Fixture FIM change","afterHash":"fixture-hash"}]}' \
    "$agent_id" "$agent_id" "$os_type" "$event_uid" "$hostname" "$ip" >"$file"
}

write_baseline_payload() {
  local file="$1" agent_id="$2" os_type="$3" hostname="$4" ip="$5" code="$6"
  printf '{"agentId":"%s","batchId":"HOST-%s-BASELINE","osType":"%s","checks":[{"checkCode":"%s","category":"host-hardening","checkItem":"Fixture baseline check","assetName":"%s","assetIp":"%s","result":"failed","severity":"medium","passRate":0,"remediation":"Review fixture baseline finding.","status":"failed","evidence":{"fixture":true}}]}' \
    "$agent_id" "$agent_id" "$os_type" "$code" "$hostname" "$ip" >"$file"
}

run_fixture() {
  local admin_token="$1" agent_id="$2" os_type="$3" hostname="$4" ip="$5" event_uid="$6" module="$7" event_type="$8" rule_id="$9"
  local token
  write_register_payload "$tmp_dir/${agent_id}-register.json" "$agent_id" "$os_type" "$hostname" "$ip"
  api_call POST /soc/agents/register "$admin_token" "" "$tmp_dir/${agent_id}-register.json"
  expect_success "${os_type} agent registered"
  token="$(json_data agentToken)"
  [[ -n "$token" ]] || fail "${os_type} agent token missing"

  write_asset_payload "$tmp_dir/${agent_id}-asset.json" "$agent_id" "$os_type" "$hostname" "$ip"
  api_call POST /soc/ingest/host/assets "" "$token" "$tmp_dir/${agent_id}-asset.json"
  expect_success "${os_type} asset ingest"

  write_event_payload "$tmp_dir/${agent_id}-event.json" "$agent_id" "$os_type" "$hostname" "$ip" "$event_uid" "$module" "$event_type" "$rule_id"
  api_call POST /soc/ingest/host/events "" "$token" "$tmp_dir/${agent_id}-event.json"
  expect_success "${os_type} event ingest"

  api_call POST /soc/ingest/host/events "" "$token" "$tmp_dir/${agent_id}-event.json"
  expect_success "${os_type} duplicate event accepted as idempotent"
  local duplicates
  duplicates="$(json_data duplicates)"
  [[ "${duplicates:-0}" -ge 1 ]] && pass "${os_type} duplicate counted" || fail "${os_type} duplicate not counted"

  write_fim_payload "$tmp_dir/${agent_id}-fim.json" "$agent_id" "$os_type" "$hostname" "$ip" "${event_uid}-fim"
  api_call POST /soc/ingest/host/fim "" "$token" "$tmp_dir/${agent_id}-fim.json"
  expect_success "${os_type} fim ingest"

  write_baseline_payload "$tmp_dir/${agent_id}-baseline.json" "$agent_id" "$os_type" "$hostname" "$ip" "${agent_id}-baseline"
  api_call POST /soc/ingest/host/baseline "" "$token" "$tmp_dir/${agent_id}-baseline.json"
  expect_success "${os_type} baseline ingest"
}

ADMIN_TOKEN="$(login || true)"
if [[ -z "$ADMIN_TOKEN" ]]; then
  fail "admin login failed"
  exit 1
fi
pass "admin login succeeded"

run_fixture "$ADMIN_TOKEN" "macos-fixture-agent" "macos" "mac-dev-host" "192.0.2.10" "MAC-FIXTURE-EVENT-0001" "auditlog" "listening_port_opened" "MAC-PORT-OPEN"
run_fixture "$ADMIN_TOKEN" "windows-fixture-agent" "windows" "win-docker-host" "192.0.2.20" "WIN-FIXTURE-EVENT-0001" "eventlog" "windows_logon_failure" "WIN-4625"

write_asset_payload "$tmp_dir/bad-token.json" "macos-fixture-agent" "macos" "mac-dev-host" "192.0.2.10"
api_call POST /soc/ingest/host/assets "" "bad-token" "$tmp_dir/bad-token.json" || true
if [[ "$(json_code)" == "AUTH_UNAUTHORIZED" ]]; then
  pass "invalid agent token rejected"
else
  fail "invalid agent token should be rejected: ${LAST_BODY}"
fi

if [[ "$KEEP_SMOKE_DATA" == "1" ]]; then
  printf '[INFO] keeping Host Agent fixture data for debugging\n'
else
  api_call DELETE /soc/demo-range/demo-data "$ADMIN_TOKEN"
  expect_success "host agent fixture data cleaned"
fi

printf '[SUMMARY] PASS=%s FAIL=%s\n' "$pass_count" "$fail_count"
[[ "$fail_count" -eq 0 ]]

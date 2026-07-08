#!/usr/bin/env bash
set -euo pipefail

API_BASE_URL="${CYBERFUSION_API_BASE:-http://127.0.0.1:18080/api}"
ADMIN_USER="${CYBERFUSION_ADMIN_USER:-admin}"
DEMO_PASSWORD="${CYBERFUSION_DEMO_PASSWORD:-Admin@123456}"
ADMIN_PASSWORD="${CYBERFUSION_ADMIN_PASSWORD:-$DEMO_PASSWORD}"
BATCH_ID="${CYBERFUSION_HOST_AGENT_BATCH_ID:-HOST-AGENT-INCIDENT-SMOKE-$(date +%Y%m%d%H%M%S)}"
FIXTURE_NET_OCTET="${CYBERFUSION_HOST_AGENT_FIXTURE_OCTET:-$((RANDOM % 180 + 20))}"
MAC_FIXTURE_IP="${CYBERFUSION_HOST_AGENT_MAC_IP:-198.18.${FIXTURE_NET_OCTET}.31}"
WIN_FIXTURE_IP="${CYBERFUSION_HOST_AGENT_WIN_IP:-198.19.${FIXTURE_NET_OCTET}.32}"
KEEP_SMOKE_DATA="${CYBERFUSION_KEEP_SMOKE_DATA:-0}"

tmp_parent="${CYBERFUSION_SMOKE_TMPDIR:-${TMPDIR:-/tmp}}"
tmp_dir="$(mktemp -d "${tmp_parent%/}/cyberfusion-host-agent-incident.XXXXXX")"
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
  exit 1
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
    --max-time 30
    -X "$method"
    "${API_BASE_URL%/}${path}"
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
  printf '%s' "$LAST_BODY" | python3 -c '
import json, sys
d = json.load(sys.stdin)
print(eval(sys.argv[1], {"__builtins__": {}}, {"d": d, "len": len, "any": any, "all": all, "set": set}) or "")
' "$expr"
}

json_code() {
  json_get 'd.get("code")'
}

json_data() {
  local expr="$1"
  printf '%s' "$LAST_BODY" | python3 -c '
import json, sys
d = json.load(sys.stdin)
value = eval(sys.argv[1], {"__builtins__": {}}, {"d": d, "len": len, "any": any, "all": all, "set": set})
print(value if value is not None else "")
' "$expr"
}

expect_success() {
  local label="$1"
  if [[ "$LAST_STATUS" == "200" && "$(json_code)" == "SUCCESS" ]]; then
    pass "$label"
  else
    fail "$label returned HTTP ${LAST_STATUS}: ${LAST_BODY}"
  fi
}

assert_json() {
  local expr="$1" label="$2"
  printf '%s' "$LAST_BODY" | python3 -c '
import json, sys
d = json.load(sys.stdin)
expr = sys.argv[1]
label = sys.argv[2]
safe = {"__builtins__": {}, "d": d, "len": len, "any": any, "all": all, "set": set, "str": str}
if not bool(eval(expr, safe, {})):
    raise SystemExit(label)
' "$expr" "$label" || fail "$label"
  pass "$label"
}

login() {
  local candidate token
  for candidate in "$ADMIN_PASSWORD" admin123; do
    printf '{"username":"%s","password":"%s"}' "$ADMIN_USER" "$candidate" >"$tmp_dir/login.json"
    api_call POST /auth/login "" "" "$tmp_dir/login.json" || continue
    token="$(json_data '(d.get("data") or {}).get("accessToken")')"
    if [[ -n "$token" ]]; then
      printf '%s\n' "$token"
      return 0
    fi
  done
  return 1
}

write_register_payload() {
  local file="$1" agent_id="$2" os_type="$3" hostname="$4" ip="$5"
  printf '{"agentId":"%s","agentName":"%s","hostname":"%s","osType":"%s","osVersion":"incident-smoke","architecture":"fixture","agentVersion":"0.1.0-dev","ipAddresses":["%s"],"macAddresses":["00:00:5e:00:53:10"],"labels":{"fixture":"true","scope":"incident-chain"}}' \
    "$agent_id" "$hostname" "$hostname" "$os_type" "$ip" >"$file"
}

register_agent() {
  local admin_token="$1" agent_id="$2" os_type="$3" hostname="$4" ip="$5"
  write_register_payload "$tmp_dir/${agent_id}-register.json" "$agent_id" "$os_type" "$hostname" "$ip"
  api_call POST /soc/agents/register "$admin_token" "" "$tmp_dir/${agent_id}-register.json"
  expect_success "${os_type} incident-chain agent registered"
  REGISTERED_AGENT_TOKEN="$(json_data '(d.get("data") or {}).get("agentToken")')"
  [[ -n "$REGISTERED_AGENT_TOKEN" ]] || fail "${os_type} agent token missing"
}

write_asset_payload() {
  local file="$1" agent_id="$2" os_type="$3" hostname="$4" ip="$5"
  printf '{"agentId":"%s","batchId":"%s-ASSET-%s","osType":"%s","assets":[{"hostname":"%s","primaryIp":"%s","osType":"%s","osVersion":"incident-smoke","ipAddresses":["%s"],"macAddresses":["00:00:5e:00:53:10"],"facts":{"source":"host-agent-incident-smoke","batchId":"%s"}}]}' \
    "$agent_id" "$BATCH_ID" "$os_type" "$os_type" "$hostname" "$ip" "$os_type" "$ip" "$BATCH_ID" >"$file"
}

write_event_payload() {
  local file="$1" agent_id="$2" os_type="$3" hostname="$4" ip="$5" event_a="$6" type_a="$7" rule_a="$8" event_b="$9" type_b="${10}" rule_b="${11}"
  printf '{"agentId":"%s","batchId":"%s-EVENT-%s","osType":"%s","events":[{"eventUid":"%s","sourceModule":"host","eventType":"%s","severity":"high","ruleId":"%s","ruleName":"Host Agent incident smoke %s","assetName":"%s","assetIp":"%s","action":"review","raw":{"fixture":true,"batchId":"%s"},"normalized":{"fixture":true,"batchId":"%s","agentId":"%s"}},{"eventUid":"%s","sourceModule":"host","eventType":"%s","severity":"high","ruleId":"%s","ruleName":"Host Agent incident smoke %s","assetName":"%s","assetIp":"%s","action":"review","raw":{"fixture":true,"batchId":"%s"},"normalized":{"fixture":true,"batchId":"%s","agentId":"%s"}}]}' \
    "$agent_id" "$BATCH_ID" "$os_type" "$os_type" \
    "$event_a" "$type_a" "$rule_a" "$type_a" "$hostname" "$ip" "$BATCH_ID" "$BATCH_ID" "$agent_id" \
    "$event_b" "$type_b" "$rule_b" "$type_b" "$hostname" "$ip" "$BATCH_ID" "$BATCH_ID" "$agent_id" >"$file"
}

run_agent_fixture() {
  local admin_token="$1" agent_id="$2" os_type="$3" hostname="$4" ip="$5" type_a="$6" rule_a="$7" type_b="$8" rule_b="$9"
  local token event_a event_b
  register_agent "$admin_token" "$agent_id" "$os_type" "$hostname" "$ip"
  token="$REGISTERED_AGENT_TOKEN"

  write_asset_payload "$tmp_dir/${agent_id}-asset.json" "$agent_id" "$os_type" "$hostname" "$ip"
  api_call POST /soc/ingest/host/assets "" "$token" "$tmp_dir/${agent_id}-asset.json"
  expect_success "${os_type} incident-chain asset ingest"

  event_a="${BATCH_ID}-${agent_id}-A"
  event_b="${BATCH_ID}-${agent_id}-B"
  write_event_payload "$tmp_dir/${agent_id}-events.json" "$agent_id" "$os_type" "$hostname" "$ip" "$event_a" "$type_a" "$rule_a" "$event_b" "$type_b" "$rule_b"
  api_call POST /soc/ingest/host/events "" "$token" "$tmp_dir/${agent_id}-events.json"
  expect_success "${os_type} incident-chain event ingest"
  assert_json "(d.get('data') or {}).get('accepted', 0) == 2" "${os_type} accepted two host events"
}

ADMIN_TOKEN="$(login || true)"
[[ -n "$ADMIN_TOKEN" ]] || fail "admin login failed"
pass "admin login succeeded"

run_agent_fixture "$ADMIN_TOKEN" "incident-macos-agent" "macos" "mac-incident-host" "$MAC_FIXTURE_IP" "listening_port_opened" "MAC-NEW-PORT" "suspicious_powershell" "MAC-SHELL-SIGNAL"
run_agent_fixture "$ADMIN_TOKEN" "incident-windows-agent" "windows" "win-incident-host" "$WIN_FIXTURE_IP" "windows_logon_failure" "WIN-4625" "new_service_installed" "WIN-7045"

api_call GET "/soc/external-events?pageNum=1&pageSize=20&keyword=${BATCH_ID}" "$ADMIN_TOKEN"
expect_success "host external events lookup"
assert_json "(d.get('data') or {}).get('total', 0) >= 4" "host fixture external events are visible"
assert_json "set(['macos-agent','windows-agent']).issubset({r.get('sourceType') for r in (d.get('data') or {}).get('records') or []})" "external events include Mac and Windows Agent sources"

api_call GET "/soc/alerts?pageNum=1&pageSize=20&keyword=${BATCH_ID}" "$ADMIN_TOKEN"
expect_success "host alerts lookup"
assert_json "(d.get('data') or {}).get('total', 0) >= 4" "host fixture events generated unified alerts"
assert_json "set(['macos-agent','windows-agent']).issubset({r.get('sourceType') for r in (d.get('data') or {}).get('records') or []})" "alerts include Mac and Windows Agent sources"

api_call POST /soc/incidents/correlate "$ADMIN_TOKEN"
expect_success "host incident correlation"
assert_json "(d.get('data') or {}).get('activeRules', 0) >= 1" "correlation engine has active rules"
assert_json "(d.get('data') or {}).get('evidenceWritten', 0) >= 1" "correlation wrote host evidence"

api_call GET "/soc/incidents?pageNum=1&pageSize=20&keyword=${BATCH_ID}" "$ADMIN_TOKEN"
expect_success "host incident cluster lookup"
assert_json "(d.get('data') or {}).get('total', 0) >= 1" "host fixture generated incident cluster"
INCIDENT_ID="$(json_data '((d.get("data") or {}).get("records") or [{}])[0].get("id")')"
[[ -n "$INCIDENT_ID" ]] || fail "incident id missing"

api_call GET "/soc/incidents/${INCIDENT_ID}" "$ADMIN_TOKEN"
expect_success "host incident detail"
assert_json "len((d.get('data') or {}).get('evidence') or []) >= 2" "host incident detail includes evidence"
assert_json "any(e.get('sourceType') in ['macos-agent','windows-agent','host-agent'] for e in (d.get('data') or {}).get('evidence') or [])" "incident evidence keeps Host Agent source"

if [[ "$KEEP_SMOKE_DATA" == "1" ]]; then
  printf '[INFO] keeping Host Agent incident smoke data for debugging: batch=%s\n' "$BATCH_ID"
else
  api_call DELETE "/soc/demo-range/host-agent-smoke-data/${BATCH_ID}" "$ADMIN_TOKEN"
  expect_success "host incident smoke fixture cleaned"
fi

printf '[SUMMARY] batch=%s PASS=%s FAIL=%s\n' "$BATCH_ID" "$pass_count" "$fail_count"
[[ "$fail_count" -eq 0 ]]

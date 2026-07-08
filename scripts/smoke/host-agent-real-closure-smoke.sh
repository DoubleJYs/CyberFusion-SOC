#!/usr/bin/env bash
set -euo pipefail

API_BASE_URL="${CYBERFUSION_API_BASE:-http://127.0.0.1:18080/api}"
ADMIN_USER="${CYBERFUSION_ADMIN_USER:-admin}"
DEMO_PASSWORD="${CYBERFUSION_DEMO_PASSWORD:-Admin@123456}"
ADMIN_PASSWORD="${CYBERFUSION_ADMIN_PASSWORD:-$DEMO_PASSWORD}"
AGENT_ID="${CYBERFUSION_REAL_CLOSURE_AGENT_ID:-go-macos-real-closure-$(date +%Y%m%d%H%M%S)}"
EVENT_BATCH_ID="HOST-${AGENT_ID}-EVENT"
TODAY="$(date +%F)"

tmp_parent="${CYBERFUSION_SMOKE_TMPDIR:-${TMPDIR:-/tmp}}"
tmp_dir="$(mktemp -d "${tmp_parent%/}/cyberfusion-host-agent-real-closure.XXXXXX")"
trap 'rm -rf "$tmp_dir"' EXIT

pass_count=0
LAST_BODY=""
LAST_STATUS=""

pass() {
  pass_count=$((pass_count + 1))
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
safe = {"__builtins__": {}, "d": d, "len": len, "any": any, "all": all, "set": set, "str": str}
value = eval(sys.argv[1], safe, {})
print(value if value is not None else "")
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

assert_json() {
  local expr="$1" label="$2"
  printf '%s' "$LAST_BODY" | python3 -c '
import json, sys
d = json.load(sys.stdin)
safe = {"__builtins__": {}, "d": d, "len": len, "any": any, "all": all, "set": set, "str": str}
if not bool(eval(sys.argv[1], safe, {})):
    raise SystemExit(sys.argv[2])
' "$expr" "$label" || fail "$label"
  pass "$label"
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

ADMIN_TOKEN="$(login || true)"
[[ -n "$ADMIN_TOKEN" ]] || fail "admin login failed"
pass "admin login succeeded"

CYBERFUSION_API_BASE="$API_BASE_URL" \
CYBERFUSION_MAC_AGENT_ID="$AGENT_ID" \
  scripts/smoke/host-agent-mac-collect-smoke.sh
pass "real macOS Host Agent collection completed"

api_call GET "/soc/external-events?pageNum=1&pageSize=100&keyword=${AGENT_ID}" "$ADMIN_TOKEN"
expect_success "real host external events lookup"
assert_json "(d.get('data') or {}).get('total', 0) >= 4" "real host external events are visible"
assert_json "all(r.get('sourceType') == 'macos-agent' for r in ((d.get('data') or {}).get('records') or []))" "real host external events keep macOS source"

api_call GET "/soc/alerts?pageNum=1&pageSize=100&keyword=${EVENT_BATCH_ID}" "$ADMIN_TOKEN"
expect_success "real host unified alerts lookup"
assert_json "(d.get('data') or {}).get('total', 0) >= 2" "real host events generated unified alerts"
assert_json "all(r.get('sourceType') == 'macos-agent' for r in ((d.get('data') or {}).get('records') or []))" "real host alerts keep macOS source"
assert_json "any(r.get('severity') in ['medium','high','critical'] for r in ((d.get('data') or {}).get('records') or []))" "real host alerts have actionable severity"
ALERT_ID="$(json_get '((d.get("data") or {}).get("records") or [{}])[0].get("id")')"
[[ -n "$ALERT_ID" ]] || fail "real host alert id missing"

api_call POST /soc/incidents/correlate "$ADMIN_TOKEN"
expect_success "real host incident correlation"
assert_json "(d.get('data') or {}).get('activeRules', 0) >= 1" "correlation engine has active rules"
assert_json "(d.get('data') or {}).get('evidenceWritten', 0) >= 1" "correlation wrote real host evidence"

api_call GET "/soc/alerts/${ALERT_ID}/related-incidents" "$ADMIN_TOKEN"
expect_success "real host alert related incidents"
assert_json "len(d.get('data') or []) >= 1" "real host alert is linked to an incident cluster"
INCIDENT_ID="$(json_get '(d.get("data") or [{}])[0].get("id")')"
[[ -n "$INCIDENT_ID" ]] || fail "real host incident id missing"

api_call GET "/soc/incidents/${INCIDENT_ID}" "$ADMIN_TOKEN"
expect_success "real host incident detail"
assert_json "len((d.get('data') or {}).get('evidence') or []) >= 2" "real host incident contains multiple evidence records"
assert_json "any(e.get('sourceType') == 'macos-agent' and '${AGENT_ID}' in str(e.get('evidenceUid')) for e in ((d.get('data') or {}).get('evidence') or []))" "real host incident evidence keeps current Agent identity"

printf '{"remark":"Host Agent real-data closure smoke: %s"}' "$AGENT_ID" >"$tmp_dir/incident-ticket.json"
api_call POST "/soc/incidents/${INCIDENT_ID}/ticket" "$ADMIN_TOKEN" "$tmp_dir/incident-ticket.json"
expect_success "real host incident converted to ticket"
TICKET_ID="$(json_get '(d.get("data") or {}).get("id")')"
TICKET_NO="$(json_get '(d.get("data") or {}).get("ticketNo")')"
[[ -n "$TICKET_ID" && -n "$TICKET_NO" ]] || fail "real host ticket identity missing"

api_call GET "/soc/tickets/${TICKET_ID}" "$ADMIN_TOKEN"
expect_success "real host ticket detail"
assert_json "(d.get('data') or {}).get('ticket', {}).get('id') is not None" "real host ticket detail returns ticket"
assert_json "len((d.get('data') or {}).get('timeline') or []) >= 1" "real host ticket timeline is recorded"

printf '{"reportType":"daily","periodStart":"%s","periodEnd":"%s"}' "$TODAY" "$TODAY" >"$tmp_dir/report.json"
api_call POST /soc/reports/generate "$ADMIN_TOKEN" "$tmp_dir/report.json"
expect_success "real host daily report generated"
REPORT_ID="$(json_get '(d.get("data") or {}).get("id")')"
REPORT_NO="$(json_get '(d.get("data") or {}).get("reportNo")')"
[[ -n "$REPORT_ID" && -n "$REPORT_NO" ]] || fail "real host report identity missing"
assert_json "'本周期告警' in ((d.get('data') or {}).get('summary') or '')" "real host report summarizes alerts"
assert_json "'待处理工单' in ((d.get('data') or {}).get('summary') or '')" "real host report summarizes tickets"

api_call GET "/soc/reports?pageNum=1&pageSize=10&keyword=${REPORT_NO}" "$ADMIN_TOKEN"
expect_success "real host report lookup"
assert_json "(d.get('data') or {}).get('total', 0) >= 1" "real host report is queryable"

printf '[SUMMARY] Host Agent real closure passed agent=%s incident=%s ticket=%s report=%s PASS=%s\n' \
  "$AGENT_ID" "$INCIDENT_ID" "$TICKET_NO" "$REPORT_NO" "$pass_count"

#!/usr/bin/env bash
set -euo pipefail

API_BASE_URL="${CYBERFUSION_API_BASE:-http://127.0.0.1:18080/api}"
ADMIN_USER="${CYBERFUSION_ADMIN_USER:-admin}"
DEMO_PASSWORD="${CYBERFUSION_DEMO_PASSWORD:-Admin@123456}"
ADMIN_PASSWORD="${CYBERFUSION_ADMIN_PASSWORD:-$DEMO_PASSWORD}"
CLEAR_FIRST=0

usage() {
  cat <<USAGE
Usage: scripts/smoke/host-agent-fixture-residue-gate.sh [--api-base-url URL] [--clear-first]

Fails if Host Agent fixture/smoke data is still visible through SOC APIs.
This is an API-level gate: it does not read database passwords or query MySQL directly.

Environment:
  CYBERFUSION_API_BASE          Default: http://127.0.0.1:18080/api
  CYBERFUSION_ADMIN_USER        Default: admin
  CYBERFUSION_DEMO_PASSWORD     Shared local demo password fallback
  CYBERFUSION_ADMIN_PASSWORD    Admin password override
  CYBERFUSION_SMOKE_TMPDIR      Parent temp directory

Examples:
  scripts/smoke/host-agent-fixture-residue-gate.sh
  scripts/smoke/host-agent-fixture-residue-gate.sh --clear-first
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --api-base-url)
      API_BASE_URL="$2"
      shift 2
      ;;
    --clear-first)
      CLEAR_FIRST=1
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      printf 'Unknown argument: %s\n' "$1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

tmp_parent="${CYBERFUSION_SMOKE_TMPDIR:-${TMPDIR:-/tmp}}"
tmp_dir="$(mktemp -d "${tmp_parent%/}/cyberfusion-host-agent-fixture-gate.XXXXXX")"
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
    curl_args+=(-H 'Content-Type: application/json')
    if [[ -f "$data_file" ]]; then
      curl_args+=(--data @"$data_file")
    else
      curl_args+=(--data "$data_file")
    fi
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
import json
import sys
d = json.load(sys.stdin)
print(eval(sys.argv[1], {"__builtins__": {}}, {"d": d, "len": len}) or "")
' "$expr"
}

json_code() {
  json_get 'd.get("code")'
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
  local candidate token payload
  for candidate in "$ADMIN_PASSWORD" admin123; do
    payload="$(printf '{"username":"%s","password":"%s"}' "$ADMIN_USER" "$candidate")"
    api_call POST /auth/login "" "$payload" || continue
    token="$(json_get '(d.get("data") or {}).get("accessToken")')"
    if [[ -n "$token" ]]; then
      printf '%s\n' "$token"
      return 0
    fi
  done
  return 1
}

api_total() {
  printf '%s' "$LAST_BODY" | python3 -c '
import json
import sys
payload = json.load(sys.stdin)
data = payload.get("data")
if isinstance(data, dict):
    print(data.get("total", len(data.get("records") or [])) or 0)
elif isinstance(data, list):
    print(len(data))
else:
    print(0)
'
}

assert_no_keyword_rows() {
  local label="$1"
  local path="$2"
  api_call GET "$path" "$ADMIN_TOKEN"
  expect_success "$label lookup"
  local total
  total="$(api_total)"
  [[ "${total:-0}" -eq 0 ]] || fail "$label fixture residue is still visible: total=${total}"
  pass "$label has no fixture residue"
}

ADMIN_TOKEN="$(login || true)"
[[ -n "$ADMIN_TOKEN" ]] || fail "admin login failed"
pass "admin login succeeded"

if [[ "$CLEAR_FIRST" == "1" ]]; then
  api_call DELETE /soc/demo-range/demo-data "$ADMIN_TOKEN"
  expect_success "fixture/demo cleanup"
fi

api_call GET /soc/demo-range/demo-data/status "$ADMIN_TOKEN"
expect_success "demo data status"
printf '%s' "$LAST_BODY" >"$tmp_dir/demo-status.json"
python3 - "$tmp_dir/demo-status.json" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as f:
    payload = json.load(f)
data = payload.get("data") or {}
if data.get("hasDemoData"):
    raise SystemExit("[FAIL] demo-data status still reports fixture/demo rows: " + json.dumps(data, ensure_ascii=False))
print("[PASS] demo-data status reports no fixture/demo data")
PY

api_call GET "/soc/agents?pageNum=1&pageSize=500" "$ADMIN_TOKEN"
expect_success "agent list"
printf '%s' "$LAST_BODY" >"$tmp_dir/agents.json"
python3 - "$tmp_dir/agents.json" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as f:
    payload = json.load(f)
data = payload.get("data")
if isinstance(data, dict):
    records = data.get("records") or []
elif isinstance(data, list):
    records = data
else:
    records = []

fixture_hosts = {"mac-dev-host", "win-docker-host", "mac-incident-host", "win-incident-host"}
bad = []
for item in records:
    agent_id = item.get("agentId") or ""
    hostname = item.get("hostname") or ""
    blob = json.dumps(item, ensure_ascii=False)
    if (
        agent_id.startswith("incident-")
        or "fixture-agent" in agent_id
        or agent_id.startswith("queue-replay-macos-agent-")
        or agent_id.startswith("queue-pressure-macos-agent-")
        or hostname in fixture_hosts
        or "192.0.2." in blob
        or "198.18." in blob
        or "198.19." in blob
        or "incident-chain" in blob
        or "queue-replay" in blob
        or "queue-pressure" in blob
        or '"fixture": "true"' in blob
        or '"fixture":"true"' in blob
    ):
        bad.append({"agentId": agent_id, "hostname": hostname, "osType": item.get("osType")})

if bad:
    raise SystemExit("[FAIL] Host Agent fixture records remain: " + json.dumps(bad, ensure_ascii=False))
print("[PASS] agent list has no Host Agent fixture residue")
PY

for keyword in \
  win-incident-host \
  mac-incident-host \
  win-docker-host \
  mac-dev-host \
  HOST-AGENT-INCIDENT-SMOKE \
  queue-pressure-macos-agent \
  192.0.2 \
  198.18 \
  198.19; do
  assert_no_keyword_rows "incident keyword ${keyword}" "/soc/incidents?pageNum=1&pageSize=20&keyword=${keyword}"
  assert_no_keyword_rows "alert keyword ${keyword}" "/soc/alerts?pageNum=1&pageSize=20&keyword=${keyword}"
  assert_no_keyword_rows "external-event keyword ${keyword}" "/soc/external-events?pageNum=1&pageSize=20&keyword=${keyword}"
  assert_no_keyword_rows "asset keyword ${keyword}" "/soc/assets?pageNum=1&pageSize=20&keyword=${keyword}"
done

printf '[SUMMARY] Host Agent fixture residue gate passed\n'

#!/usr/bin/env bash
set -euo pipefail

API_BASE_URL="${CYBERFUSION_API_BASE:-http://127.0.0.1:18080/api}"
ADMIN_USER="${CYBERFUSION_ADMIN_USER:-admin}"
DEMO_PASSWORD="${CYBERFUSION_DEMO_PASSWORD:-Admin@123456}"
ADMIN_PASSWORD="${CYBERFUSION_ADMIN_PASSWORD:-$DEMO_PASSWORD}"
CLEAR_DEMO=1

usage() {
  cat <<USAGE
Usage: scripts/smoke/host-agent-real-data-gate.sh [--skip-clear-demo]

Validates the Host Agent real-data path after clearing demo data by default:
1. Login as admin.
2. DELETE /soc/demo-range/demo-data unless --skip-clear-demo is passed.
3. Run shared macOS/Windows Go fixture smoke as protocol preflight only.
4. Assert no Host Agent fixture residue is visible through SOC APIs.
5. Run macOS queue pressure replay smoke against an unreachable backend, then
   replay the queued records after recovery.
6. Run current macOS real closure smoke:
   real host event -> alert -> incident cluster -> ticket -> report.
7. Verify /soc/agents/overview still exposes real Host Agent assets/events.

Windows note:
  This gate does not validate real Windows EventLog, Defender, Sysmon, Windows
  Service, reboot recovery, or Windows Docker host queue replay. Windows remains
  a reserved real-host acceptance target until scripts/win/verify-agent.ps1 runs
  on a Windows host.

Environment:
  CYBERFUSION_API_BASE          Default: http://127.0.0.1:18080/api
  CYBERFUSION_ADMIN_USER        Default: admin
  CYBERFUSION_ADMIN_PASSWORD    Defaults to CYBERFUSION_DEMO_PASSWORD
  CYBERFUSION_QUEUE_PRESSURE_CYCLES
                                Default: 8
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-clear-demo)
      CLEAR_DEMO=0
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
tmp_dir="$(mktemp -d "${tmp_parent%/}/cyberfusion-host-agent-real-gate.XXXXXX")"
trap 'rm -rf "$tmp_dir"' EXIT

LAST_BODY=""
LAST_STATUS=""

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
print(eval(sys.argv[1], {}, {"d": d}) or "")
' "$expr"
}

expect_success() {
  local label="$1"
  local code
  code="$(json_get 'd.get("code")')"
  if [[ "$LAST_STATUS" == "200" && "$code" == "SUCCESS" ]]; then
    printf '[PASS] %s\n' "$label"
  else
    printf '[FAIL] %s returned HTTP %s: %s\n' "$label" "$LAST_STATUS" "$LAST_BODY" >&2
    exit 1
  fi
}

login() {
  printf '{"username":"%s","password":"%s"}' "$ADMIN_USER" "$ADMIN_PASSWORD" >"$tmp_dir/login.json"
  api_call POST /auth/login "" "$tmp_dir/login.json"
  local code
  code="$(json_get 'd.get("code")')"
  if [[ "$LAST_STATUS" != "200" || "$code" != "SUCCESS" ]]; then
    printf '[FAIL] admin login returned HTTP %s: %s\n' "$LAST_STATUS" "$LAST_BODY" >&2
    exit 1
  fi
  printf '[PASS] admin login\n' >&2
  json_get '(d.get("data") or {}).get("accessToken")'
}

ADMIN_TOKEN="$(login)"
if [[ -z "$ADMIN_TOKEN" ]]; then
  printf '[FAIL] admin access token is empty\n' >&2
  exit 1
fi

if [[ "$CLEAR_DEMO" == "1" ]]; then
  api_call DELETE /soc/demo-range/demo-data "$ADMIN_TOKEN"
  expect_success "demo data cleared"
else
  printf '[INFO] demo data clear skipped\n'
fi

CYBERFUSION_API_BASE="$API_BASE_URL" scripts/smoke/host-agent-go-smoke.sh
CYBERFUSION_API_BASE="$API_BASE_URL" scripts/smoke/host-agent-fixture-residue-gate.sh
CYBERFUSION_API_BASE="$API_BASE_URL" scripts/smoke/host-agent-queue-pressure-smoke.sh
CYBERFUSION_API_BASE="$API_BASE_URL" scripts/smoke/host-agent-fixture-residue-gate.sh
CYBERFUSION_API_BASE="$API_BASE_URL" scripts/smoke/host-agent-real-closure-smoke.sh
CYBERFUSION_API_BASE="$API_BASE_URL" scripts/smoke/host-agent-fixture-residue-gate.sh

api_call GET /soc/agents/overview "$ADMIN_TOKEN"
expect_success "host agent overview"
overview_file="$tmp_dir/host-agent-overview.json"
printf '%s' "$LAST_BODY" >"$overview_file"
python3 - "$overview_file" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as f:
    d = json.load(f)
data = d.get("data") or {}
sources = {item.get("sourceType") for item in data.get("sources") or []}
errors = []
if (data.get("realAssetCount") or 0) < 1:
    errors.append("realAssetCount < 1")
if (data.get("events24h") or 0) < 1:
    errors.append("events24h < 1")
if "macos-agent" not in sources:
    errors.append("macos-agent source missing")
if errors:
    print("[FAIL] " + "; ".join(errors), file=sys.stderr)
    sys.exit(1)
print(
    "[PASS] real host agent data visible: "
    f"assets={data.get('realAssetCount')} events24h={data.get('events24h')} "
    f"agents={data.get('totalAgents')} online={data.get('onlineAgents')}"
)
PY

printf '[SUMMARY] Host Agent real-data gate passed\n'

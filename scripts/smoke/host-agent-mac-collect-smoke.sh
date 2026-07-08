#!/usr/bin/env bash
set -euo pipefail

API_BASE_URL="${CYBERFUSION_API_BASE:-http://127.0.0.1:18080/api}"
ADMIN_USER="${CYBERFUSION_ADMIN_USER:-admin}"
DEMO_PASSWORD="${CYBERFUSION_DEMO_PASSWORD:-Admin@123456}"
ADMIN_PASSWORD="${CYBERFUSION_ADMIN_PASSWORD:-$DEMO_PASSWORD}"
AGENT_ID="${CYBERFUSION_MAC_AGENT_ID:-go-macos-real-agent}"
FIM_PATH="${CYBERFUSION_MAC_FIM_PATH:-README.md}"
export GOCACHE="${GOCACHE:-/private/tmp/cyberfusion-agent-go-build}"

tmp_parent="${CYBERFUSION_SMOKE_TMPDIR:-${TMPDIR:-/tmp}}"
tmp_dir="$(mktemp -d "${tmp_parent%/}/cyberfusion-host-agent-mac-smoke.XXXXXX")"
trap 'rm -rf "$tmp_dir"' EXIT

LAST_BODY=""
LAST_STATUS=""

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
    --max-time 20
    -X "$method"
    "${API_BASE_URL}${path}"
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
    printf '[PASS] %s\n' "$label"
  else
    printf '[FAIL] %s returned HTTP %s: %s\n' "$label" "$LAST_STATUS" "$LAST_BODY" >&2
    exit 1
  fi
}

login() {
  local candidate token
  for candidate in "$ADMIN_PASSWORD" admin123; do
    printf '{"username":"%s","password":"%s"}' "$ADMIN_USER" "$candidate" >"$tmp_dir/login.json"
    api_call POST /auth/login "" "$tmp_dir/login.json" || continue
    token="$(printf '%s' "$LAST_BODY" | json_get '(d.get("data") or {}).get("accessToken")')"
    if [[ -n "$token" ]]; then
      printf '%s\n' "$token"
      return 0
    fi
  done
  return 1
}

HOSTNAME_VALUE="$(hostname 2>/dev/null || printf 'macos-host')"
ADMIN_TOKEN="$(login || true)"
if [[ -z "$ADMIN_TOKEN" ]]; then
  printf '[FAIL] admin login failed\n' >&2
  exit 1
fi
printf '[PASS] admin login succeeded\n'

printf '{"agentId":"%s","agentName":"%s","hostname":"%s","osType":"macos","osVersion":"runtime","architecture":"auto","agentVersion":"0.1.0-dev","labels":{"mode":"collect","agent":"go"}}' \
  "$AGENT_ID" "$HOSTNAME_VALUE" "$HOSTNAME_VALUE" >"$tmp_dir/register.json"
api_call POST /soc/agents/register "$ADMIN_TOKEN" "$tmp_dir/register.json"
expect_success "macOS collect agent registered"
AGENT_TOKEN="$(json_data agentToken)"
if [[ -z "$AGENT_TOKEN" ]]; then
  printf '[FAIL] macOS collect agent token missing\n' >&2
  exit 1
fi

(
  cd agent
  CYBERFUSION_AGENT_TOKEN="$AGENT_TOKEN" go run ./cmd/cyberfusion-agent \
    --mode collect \
    --os-type macos \
    --agent-id "$AGENT_ID" \
    --runtime-dir "$tmp_dir/${AGENT_ID}-runtime" \
    --api-base-url "$API_BASE_URL" \
    --fim-path "$FIM_PATH" >/dev/null
)
printf '[PASS] macOS real collector uploaded asset/events/fim/baseline\n'

api_call GET "/soc/external-events?pageNum=1&pageSize=100&keyword=${AGENT_ID}" "$ADMIN_TOKEN"
expect_success "macOS collector external events lookup"
printf '%s' "$LAST_BODY" >"$tmp_dir/external-events.json"
python3 - "$tmp_dir/external-events.json" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as f:
    payload = json.load(f)

records = (payload.get("data") or {}).get("records") or []
event_types = {item.get("eventType") for item in records}
source_types = {item.get("sourceType") for item in records}
required = {
    "listening_port_observed",
    "process_summary_observed",
    "macos_startup_items_observed",
    "macos_system_log_summary_observed",
}
missing = sorted(required - event_types)
if missing:
    raise SystemExit("missing macOS collector event types: " + ", ".join(missing))
if "macos-agent" not in source_types:
    raise SystemExit("macos-agent source missing from external events")
print("[PASS] macOS collector events include ports, process, startup items, and system log summary")
PY

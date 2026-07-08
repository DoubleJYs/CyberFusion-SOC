#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
API_BASE_URL="${CYBERFUSION_API_BASE:-http://127.0.0.1:18080/api}"
ADMIN_USER="${CYBERFUSION_ADMIN_USER:-admin}"
DEMO_PASSWORD="${CYBERFUSION_DEMO_PASSWORD:-Admin@123456}"
ADMIN_PASSWORD="${CYBERFUSION_ADMIN_PASSWORD:-$DEMO_PASSWORD}"
export GOCACHE="${GOCACHE:-/private/tmp/cyberfusion-agent-go-build}"

tmp_parent="${CYBERFUSION_SMOKE_TMPDIR:-${TMPDIR:-/tmp}}"
tmp_dir="$(mktemp -d "${tmp_parent%/}/cyberfusion-host-agent-preflight.XXXXXX")"
trap 'rm -rf "$tmp_dir"' EXIT

LAST_BODY=""
LAST_STATUS=""

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
print(eval(sys.argv[1], {}, {"d": d}) or "")
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

build_agent_binaries() {
  local host_goos host_goarch
  host_goos="$(go env GOOS)"
  host_goarch="$(go env GOARCH)"
  (
    cd "$PROJECT_ROOT/agent"
    GOOS="$host_goos" GOARCH="$host_goarch" go build -o "$tmp_dir/cyberfusion-agent-${host_goos}-${host_goarch}" ./cmd/cyberfusion-agent
    GOOS=windows GOARCH=amd64 go build -o "$tmp_dir/cyberfusion-agent-windows-amd64.exe" ./cmd/cyberfusion-agent
  )
  [[ -s "$tmp_dir/cyberfusion-agent-${host_goos}-${host_goarch}" ]] || fail "host agent binary was not created"
  [[ -s "$tmp_dir/cyberfusion-agent-windows-amd64.exe" ]] || fail "Windows agent binary was not created"
  pass "Go Agent builds for ${host_goos}/${host_goarch} and windows/amd64"
}

verify_fixture_overview() {
  local admin_token="$1"
  local overview_file="$tmp_dir/host-agent-overview.json"
  api_call GET /soc/agents/overview "$admin_token"
  expect_success "Host Agent overview API"
  printf '%s' "$LAST_BODY" >"$overview_file"
  python3 - "$overview_file" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as f:
    payload = json.load(f)

data = payload.get("data") or {}
sources = set()
for item in data.get("sources") or []:
    if isinstance(item, dict):
        value = item.get("sourceType") or item.get("source")
    else:
        value = str(item)
    if value:
        sources.add(value)

errors = []
if (data.get("totalAgents") or 0) < 2:
    errors.append("totalAgents < 2")
if (data.get("realAssetCount") or 0) < 1:
    errors.append("realAssetCount < 1")
if (data.get("events24h") or 0) < 1:
    errors.append("events24h < 1")
if "macos-agent" not in sources:
    errors.append("macos-agent source missing")
if "windows-agent" not in sources:
    errors.append("windows-agent source missing")

if errors:
    print("[FAIL] " + "; ".join(errors), file=sys.stderr)
    sys.exit(1)

print(
    "[PASS] overview exposes Mac and Windows fixture data: "
    f"agents={data.get('totalAgents')} assets={data.get('realAssetCount')} "
    f"events24h={data.get('events24h')} sources={','.join(sorted(sources))}"
)
PY
}

verify_real_macos_overview() {
  local admin_token="$1"
  local overview_file="$tmp_dir/host-agent-real-overview.json"
  api_call GET /soc/agents/overview "$admin_token"
  expect_success "Host Agent real overview API"
  printf '%s' "$LAST_BODY" >"$overview_file"
  python3 - "$overview_file" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as f:
    payload = json.load(f)

data = payload.get("data") or {}
sources = set()
for item in data.get("sources") or []:
    if isinstance(item, dict):
        value = item.get("sourceType") or item.get("source")
    else:
        value = str(item)
    if value:
        sources.add(value)

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
    "[PASS] overview exposes real macOS Host Agent data after fixture cleanup: "
    f"assets={data.get('realAssetCount')} events24h={data.get('events24h')} "
    f"sources={','.join(sorted(sources))}"
)
PY
}

main() {
  cd "$PROJECT_ROOT"
  info "This preflight validates the Mac-verifiable dual-platform contract only."
  info "It does not validate real Windows EventLog, Defender, Sysmon, Windows Service, reboot recovery, or Windows queue recovery."
  info "It does validate bounded local Agent queue replay before the Windows 30-minute outage acceptance."

  api_call GET /health
  expect_success "backend health"

  build_agent_binaries

  CYBERFUSION_KEEP_SMOKE_DATA=1 CYBERFUSION_API_BASE="$API_BASE_URL" "$PROJECT_ROOT/scripts/smoke/host-agent-go-smoke.sh"
  local admin_token
  admin_token="$(login || true)"
  [[ -n "$admin_token" ]] || fail "admin login failed"
  pass "admin login succeeded"
  verify_fixture_overview "$admin_token"

  api_call DELETE /soc/demo-range/demo-data "$admin_token"
  expect_success "dual-platform fixture cleanup"
  CYBERFUSION_API_BASE="$API_BASE_URL" "$PROJECT_ROOT/scripts/smoke/host-agent-fixture-residue-gate.sh"

  "$PROJECT_ROOT/scripts/smoke/host-agent-queue-replay-smoke.sh"
  CYBERFUSION_API_BASE="$API_BASE_URL" "$PROJECT_ROOT/scripts/smoke/host-agent-fixture-residue-gate.sh"

  "$PROJECT_ROOT/scripts/smoke/host-agent-uninstall-smoke.sh"
  "$PROJECT_ROOT/scripts/smoke/host-agent-resource-smoke.sh"
  "$PROJECT_ROOT/scripts/smoke/host-agent-package-smoke.sh"
  CYBERFUSION_API_BASE="$API_BASE_URL" "$PROJECT_ROOT/scripts/smoke/host-agent-mac-collect-smoke.sh"
  CYBERFUSION_API_BASE="$API_BASE_URL" "$PROJECT_ROOT/scripts/smoke/host-agent-fixture-residue-gate.sh"

  verify_real_macos_overview "$admin_token"

  info "Windows real host acceptance still requires scripts/win/verify-agent.ps1 on a Windows host running the Docker platform."
  pass "Host Agent Mac/Windows preflight completed"
}

main "$@"

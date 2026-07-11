#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BASE_URL="${CYBERFUSION_FRONTEND_URL:-http://127.0.0.1:5174}"
API_BASE_URL="${CYBERFUSION_API_BASE:-http://127.0.0.1:18080/api}"
ADMIN_USER="${CYBERFUSION_ADMIN_USER:-admin}"
ANALYST_USER="${CYBERFUSION_ANALYST_USER:-analyst}"
EMPLOYEE_USER="${CYBERFUSION_EMPLOYEE_USER:-operator}"
DEMO_PASSWORD="${CYBERFUSION_DEMO_PASSWORD:-Admin@123456}"
ADMIN_PASSWORD="${CYBERFUSION_ADMIN_PASSWORD:-$DEMO_PASSWORD}"
ANALYST_PASSWORD="${CYBERFUSION_ANALYST_PASSWORD:-$DEMO_PASSWORD}"
EMPLOYEE_PASSWORD="${CYBERFUSION_EMPLOYEE_PASSWORD:-$DEMO_PASSWORD}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url)
      BASE_URL="$2"
      shift 2
      ;;
    --api-base-url)
      API_BASE_URL="$2"
      shift 2
      ;;
    --help|-h)
      cat <<USAGE
Usage: scripts/smoke/check-visibility.sh [--base-url URL] [--api-base-url URL]

Checks route, menu, permission, and key API visibility for the current
CyberFusion runtime. It does not import demo data, run scans, send
notifications, or modify database records.

Environment:
  CYBERFUSION_FRONTEND_URL     Default: http://127.0.0.1:5174
  CYBERFUSION_API_BASE         Default: http://127.0.0.1:18080/api
  CYBERFUSION_DEMO_PASSWORD    Shared local demo password fallback
  CYBERFUSION_ADMIN_PASSWORD   Admin password override
  CYBERFUSION_ANALYST_PASSWORD Analyst password override
  CYBERFUSION_EMPLOYEE_PASSWORD Employee password override
USAGE
      exit 0
      ;;
    *)
      printf 'Unknown argument: %s\n' "$1" >&2
      exit 2
      ;;
  esac
done

PASS_COUNT=0
FAIL_COUNT=0
LAST_BODY=""
LAST_STATUS=""
ADMIN_TOKEN=""
ANALYST_TOKEN=""
EMPLOYEE_TOKEN=""

require_tool() {
  command -v "$1" >/dev/null 2>&1 || record_fail "tool:$1" "missing required tool"
}

record_pass() {
  PASS_COUNT=$((PASS_COUNT + 1))
  printf 'PASS\t%s\t%s\n' "$1" "$2"
}

record_fail() {
  FAIL_COUNT=$((FAIL_COUNT + 1))
  printf 'FAIL\t%s\t%s\n' "$1" "$2"
}

api_call() {
  local method="$1"
  local path="$2"
  local token="${3:-}"
  local data="${4:-}"
  local -a curl_args=(-sS -w $'\n%{http_code}' -X "$method" "${API_BASE_URL}${path}")
  if [[ -n "$token" ]]; then
    curl_args+=(-H "Authorization: Bearer ${token}")
  fi
  if [[ -n "$data" ]]; then
    curl_args+=(-H 'Content-Type: application/json' --data "$data")
  fi
  local response
  response="$(curl "${curl_args[@]}")" || return 1
  LAST_STATUS="${response##*$'\n'}"
  LAST_BODY="${response%$'\n'"$LAST_STATUS"}"
}

json_query() {
  local expr="$1"
  python3 -c '
import json, sys
expr = sys.argv[1]
try:
    d = json.load(sys.stdin)
    value = eval(expr, {"__builtins__": {}}, {"d": d, "len": len, "any": any, "all": all, "str": str})
except Exception:
    raise SystemExit(1)
if isinstance(value, (dict, list)):
    print(json.dumps(value, ensure_ascii=False))
elif value is None:
    print("")
else:
    print(value)
' "$expr"
}

json_assert() {
  local expr="$1"
  python3 -c '
import json, sys
expr = sys.argv[1]
try:
    d = json.load(sys.stdin)
    ok = bool(eval(expr, {"__builtins__": {}}, {"d": d, "len": len, "any": any, "all": all, "str": str}))
except Exception:
    ok = False
raise SystemExit(0 if ok else 1)
' "$expr"
}

login() {
  local username="$1"
  local password="$2"
  local candidates=("$password")
  if [[ "$username" == "$ADMIN_USER" && -z "${CYBERFUSION_ADMIN_PASSWORD:-}" ]]; then
    candidates+=("admin123")
  fi
  local candidate token
  for candidate in "${candidates[@]}"; do
    api_call POST /auth/login "" "{\"username\":\"${username}\",\"password\":\"${candidate}\"}" || continue
    [[ "$LAST_STATUS" == "200" ]] || continue
    token="$(printf '%s' "$LAST_BODY" | json_query "(d.get('data') or {}).get('accessToken') or ''" || true)"
    if [[ -n "$token" ]]; then
      printf '%s\n' "$token"
      return 0
    fi
  done
  return 1
}

check_api_status() {
  local label="$1"
  local method="$2"
  local path="$3"
  local token="$4"
  local expected="$5"
  local data="${6:-}"
  if api_call "$method" "$path" "$token" "$data" && [[ "$LAST_STATUS" == "$expected" ]]; then
    record_pass "$label" "$method $path -> $expected"
  else
    record_fail "$label" "$method $path expected $expected got ${LAST_STATUS:-curl-error}"
  fi
}

check_frontend_route() {
  local path="$1"
  local label="$2"
  local status
  status="$(curl -sS -o /tmp/cyberfusion-visibility-page.html -w '%{http_code}' "${BASE_URL}${path}")" || status="curl-error"
  if [[ "$status" == "200" ]] && grep -q '<div id="app"' /tmp/cyberfusion-visibility-page.html; then
    record_pass "$label" "${BASE_URL}${path}"
  else
    record_fail "$label" "${BASE_URL}${path} returned $status"
  fi
}

check_source_route() {
  local path="$1"
  local label="$2"
  local bare_path="${path#/}"
  local tail_path="${bare_path##*/}"
  if rg -q "$path|$bare_path|$tail_path" "$ROOT_DIR/frontend/src/router" "$ROOT_DIR/frontend/src/layouts" "$ROOT_DIR/frontend/src/components/AppLayout"; then
    record_pass "$label" "$path registered in frontend source"
  else
    record_fail "$label" "$path not found in frontend route/menu source"
  fi
}

check_source_text() {
  local label="$1"
  local pattern="$2"
  shift 2
  if rg -q "$pattern" "$@"; then
    record_pass "$label" "$pattern present in source"
  else
    record_fail "$label" "$pattern missing from source"
  fi
}

check_menu_path() {
  local path="$1"
  local label="$2"
  if printf '%s' "$LAST_BODY" | python3 -c '
import json
import sys

target = sys.argv[1]
try:
    body = json.load(sys.stdin)
except Exception:
    raise SystemExit(1)

def walk(items):
    for item in items or []:
        if item.get("path") == target:
            return True
        if walk(item.get("children") or []):
            return True
    return False

raise SystemExit(0 if walk((body.get("data") or {}).get("menus") or []) else 1)
' "$path"
  then
    record_pass "$label" "$path present in /auth/me menus"
  else
    record_fail "$label" "$path missing from /auth/me menus"
  fi
}

printf 'CyberFusion visibility check\n'
printf 'Frontend URL: %s\n' "$BASE_URL"
printf 'Backend API: %s\n' "$API_BASE_URL"
printf 'Result\tCheck\tDetail\n'

require_tool curl
require_tool python3
require_tool rg

ADMIN_TOKEN="$(login "$ADMIN_USER" "$ADMIN_PASSWORD" || true)"
if [[ -n "$ADMIN_TOKEN" ]]; then
  record_pass "admin-login" "$ADMIN_USER"
else
  record_fail "admin-login" "cannot login as $ADMIN_USER"
fi

if [[ -n "$ADMIN_TOKEN" ]]; then
  check_api_status "admin-me" GET /auth/me "$ADMIN_TOKEN" 200
  ADMIN_ME="$LAST_BODY"
  printf '%s' "$ADMIN_ME" | python3 -c 'import json,sys; d=json.load(sys.stdin); print("Admin menu paths:"); 
def walk(items):
    for item in items:
        if item.get("path"):
            print(" - " + item["path"])
        walk(item.get("children") or [])
walk(d["data"]["menus"])'
  LAST_BODY="$ADMIN_ME"
  if printf '%s' "$ADMIN_ME" | json_assert "any(role in ['admin','super_admin'] for role in ((d.get('data') or {}).get('roles') or []))"; then
    record_pass "role:super-admin" "admin identity has admin/super_admin role"
  else
    record_fail "role:super-admin" "admin identity lacks admin/super_admin role"
  fi
  check_menu_path "/soc/policies" "menu:/soc/policies"
  check_menu_path "/soc/alerts" "menu:/soc/alerts"
  check_menu_path "/soc/incidents" "menu:/soc/incidents"
  check_menu_path "/soc/capabilities" "menu:/soc/capabilities"
  check_menu_path "/soc/rules" "menu:/soc/rules"
  check_menu_path "/soc/baselines" "menu:/soc/baselines"
  check_menu_path "/soc/fim" "menu:/soc/fim"
  check_menu_path "/soc/external-events" "menu:/soc/external-events"
  check_menu_path "/soc/settings" "menu:/soc/settings"
  check_menu_path "/soc/tickets" "menu:/soc/tickets"
  check_menu_path "/soc/reports" "menu:/soc/reports"
  check_menu_path "/system/user" "menu:/system/user"
fi

for route in /showcase /soc/policies /soc/alerts /soc/incidents /soc/tickets /soc/reports /client/workbench /client/tasks '/client/local-range?ip=10.20.1.15&host=prod-app-01&os=Linux'; do
  check_frontend_route "$route" "frontend:$route"
done

for route in /showcase /soc/policies /soc/incidents /client/tasks /client/local-range; do
  check_source_route "$route" "source-route:$route"
done
check_source_text "source-tab:correlation-rules" "事件关联规则" "$ROOT_DIR/frontend/src/views/soc/PolicyCenterView.vue"
check_source_text "source-api:correlation-rules" "/soc/correlation-rules" "$ROOT_DIR/frontend/src/api/soc.ts"
check_source_text "source-tab:algorithm-governance" "算法治理" "$ROOT_DIR/frontend/src/views/soc/PolicyCenterView.vue"
check_source_text "source-api:algorithm-center" "/soc/algorithm-center" "$ROOT_DIR/frontend/src/api/soc.ts"
check_source_text "source-view-mode" "viewMode" "$ROOT_DIR/frontend/src/stores/app.ts" "$ROOT_DIR/frontend/src/layouts/AdminLayout.vue"
check_source_text "source-role-experience" "super_admin" "$ROOT_DIR/frontend/src/utils/roleExperience.ts" "$ROOT_DIR/frontend/src/components/AppLayout/SidebarMenu.vue"

if [[ -n "$ADMIN_TOKEN" ]]; then
  check_api_status "policy-local-list" GET /soc/policies/local-check-commands "$ADMIN_TOKEN" 200
  check_api_status "policy-adapter-list" GET /soc/policies/event-adapters "$ADMIN_TOKEN" 200
  check_api_status "policy-playbook-list" GET /soc/policies/playbooks "$ADMIN_TOKEN" 200
  check_api_status "algorithm-overview" GET /soc/algorithm-center/overview "$ADMIN_TOKEN" 200
  check_api_status "integration-catalog" GET /soc/integrations/catalog "$ADMIN_TOKEN" 200
  check_api_status "correlation-rule-list" GET '/soc/correlation-rules?pageNum=1&pageSize=5' "$ADMIN_TOKEN" 200
  check_api_status "incident-list" GET '/soc/incidents?pageNum=1&pageSize=5' "$ADMIN_TOKEN" 200
  check_api_status "operations-overview" GET '/soc/operations/overview' "$ADMIN_TOKEN" 200
  check_api_status "client-commands" GET '/client/local-terminal/commands?os=Linux' "$ADMIN_TOKEN" 200
  check_api_status "demo-evidence-chain" GET '/soc/demo-range/batches/DEMO-RANGE-OFFLINE-V1/evidence-chain' "$ADMIN_TOKEN" 200
fi

if [[ -n "$ADMIN_TOKEN" ]]; then
  api_call GET '/soc/policies/event-adapters?pageNum=1&pageSize=1&status=active' "$ADMIN_TOKEN" "" || true
  ADAPTER_ID=""
  if [[ "$LAST_STATUS" == "200" ]]; then
    ADAPTER_ID="$(printf '%s' "$LAST_BODY" | json_query "d['data']['records'][0]['id']" || true)"
  fi
  if [[ -n "$ADAPTER_ID" ]]; then
    check_api_status "adapter-preview-no-write" POST "/soc/policies/event-adapters/${ADAPTER_ID}/preview" "$ADMIN_TOKEN" 200 '{"payload":"{\"sourceType\":\"waf\",\"eventType\":\"waf_block\",\"severity\":\"high\",\"assetIp\":\"10.20.1.15\",\"requestId\":\"visibility-preview\"}"}'
  else
    record_fail "adapter-preview-no-write" "no active adapter id available"
  fi
fi

ANALYST_TOKEN="$(login "$ANALYST_USER" "$ANALYST_PASSWORD" || true)"
if [[ -n "$ANALYST_TOKEN" ]]; then
  record_pass "analyst-login" "$ANALYST_USER"
  check_api_status "analyst-alerts" GET '/soc/alerts?pageNum=1&pageSize=1' "$ANALYST_TOKEN" 200
  check_api_status "analyst-incidents" GET '/soc/incidents?pageNum=1&pageSize=1' "$ANALYST_TOKEN" 200
  check_api_status "analyst-correlation-rules" GET '/soc/correlation-rules?pageNum=1&pageSize=1' "$ANALYST_TOKEN" 200
  check_api_status "analyst-algorithm-evaluations" GET '/soc/algorithm-center/evaluations?pageNum=1&pageSize=1' "$ANALYST_TOKEN" 200
  check_api_status "analyst-denied-algorithm-replay" POST /soc/algorithm-center/replay "$ANALYST_TOKEN" 403 '{"algorithmType":"all","saveEvaluation":false}'
  check_api_status "analyst-tickets" GET '/soc/tickets?pageNum=1&pageSize=1' "$ANALYST_TOKEN" 200
  check_api_status "analyst-reports" GET '/soc/reports?pageNum=1&pageSize=1' "$ANALYST_TOKEN" 200
else
  record_fail "analyst-login" "cannot login as $ANALYST_USER"
fi

EMPLOYEE_TOKEN="$(login "$EMPLOYEE_USER" "$EMPLOYEE_PASSWORD" || true)"
if [[ -n "$EMPLOYEE_TOKEN" ]]; then
  record_pass "employee-login" "$EMPLOYEE_USER"
  check_api_status "employee-denied-policy" GET /soc/policies/local-check-commands "$EMPLOYEE_TOKEN" 403
  check_api_status "employee-denied-assets" GET '/soc/assets?pageNum=1&pageSize=1' "$EMPLOYEE_TOKEN" 403
  check_api_status "employee-denied-incidents" GET '/soc/incidents?pageNum=1&pageSize=1' "$EMPLOYEE_TOKEN" 403
  check_api_status "employee-denied-correlation-rules" GET '/soc/correlation-rules?pageNum=1&pageSize=1' "$EMPLOYEE_TOKEN" 403
  check_api_status "employee-denied-system" GET '/system/users?pageNum=1&pageSize=1' "$EMPLOYEE_TOKEN" 403
  check_api_status "employee-denied-algorithm-overview" GET /soc/algorithm-center/overview "$EMPLOYEE_TOKEN" 403
  check_api_status "employee-denied-algorithm-replay" POST /soc/algorithm-center/replay "$EMPLOYEE_TOKEN" 403 '{"algorithmType":"all","saveEvaluation":false}'
  check_api_status "employee-denied-operations" GET '/soc/operations/overview' "$EMPLOYEE_TOKEN" 403
  check_api_status "employee-client-commands" GET '/client/local-terminal/commands?os=Linux' "$EMPLOYEE_TOKEN" 200
  check_api_status "employee-client-tasks" GET /client/tasks "$EMPLOYEE_TOKEN" 200
  check_api_status "employee-me" GET /auth/me "$EMPLOYEE_TOKEN" 200
  EMPLOYEE_ME="$LAST_BODY"
  if printf '%s' "$EMPLOYEE_ME" | python3 -c '
import json, sys
body = json.load(sys.stdin)
paths = []
def walk(items):
    for item in items or []:
        path = item.get("path")
        if path:
            paths.append(path)
        walk(item.get("children") or [])
walk((body.get("data") or {}).get("menus") or [])
bad = [path for path in paths if path in ("/dashboard", "/soc", "/system") or path.startswith("/soc/") or path.startswith("/system/")]
raise SystemExit(1 if bad else 0)
'
  then
    record_pass "employee-menu-simple" "employee /auth/me menus exclude SOC and system paths"
  else
    record_fail "employee-menu-simple" "employee /auth/me menus include SOC or system paths"
  fi
else
  record_fail "employee-login" "cannot login as $EMPLOYEE_USER"
fi

printf '\nSummary: PASS=%s FAIL=%s\n' "$PASS_COUNT" "$FAIL_COUNT"
if [[ "$FAIL_COUNT" -gt 0 ]]; then
  exit 1
fi

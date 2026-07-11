#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${CYBERFUSION_FRONTEND_URL:-http://127.0.0.1:5174}"
API_BASE_URL="${CYBERFUSION_API_BASE:-http://127.0.0.1:18080/api}"
MYSQL_CONTAINER="${CYBERFUSION_MYSQL_CONTAINER:-cyberfusion-platform-mysql-1}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-cyberfusion_soc}"
DB_USERNAME="${DB_USERNAME:-root}"
ADMIN_USER="${CYBERFUSION_ADMIN_USER:-admin}"
EMPLOYEE_USER="${CYBERFUSION_EMPLOYEE_USER:-operator}"
DEMO_PASSWORD="${CYBERFUSION_DEMO_PASSWORD:-Admin@123456}"
ADMIN_PASSWORD="${CYBERFUSION_ADMIN_PASSWORD:-$DEMO_PASSWORD}"
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
    --mysql-container)
      MYSQL_CONTAINER="$2"
      shift 2
      ;;
    --help|-h)
      cat <<USAGE
Usage: scripts/smoke/dev-doctor.sh [--base-url URL] [--api-base-url URL] [--mysql-container NAME]

Checks local CyberFusion development runtime without modifying data:
- frontend/backend ports
- frontend /api proxy health path
- backend Java process start time
- /api/health dependency, schema, and seed diagnostics
- Docker MySQL critical SOC tables and menu/permission seed rows
- live admin menu and employee 403 boundaries

Environment:
  CYBERFUSION_FRONTEND_URL   Default: http://127.0.0.1:5174
  CYBERFUSION_API_BASE       Default: http://127.0.0.1:18080/api
  CYBERFUSION_MYSQL_CONTAINER Default: cyberfusion-platform-mysql-1
  DB_HOST                    Default: 127.0.0.1
  DB_PORT                    Default: 3306
  DB_NAME                    Default: cyberfusion_soc
  DB_USERNAME                Default: root
  DB_PASSWORD                Preferred for SQL authentication checks; if absent,
                             Docker MySQL container MYSQL_ROOT_PASSWORD is used
                             as a local fallback without printing the password.
  CYBERFUSION_DEMO_PASSWORD  Shared local demo password fallback
  CYBERFUSION_ADMIN_PASSWORD Admin password override
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

pass_count=0
warn_count=0
fail_count=0

pass() {
  pass_count=$((pass_count + 1))
  printf '[PASS] %s\n' "$1"
}

warn() {
  warn_count=$((warn_count + 1))
  printf '[WARN] %s\n' "$1"
}

fail() {
  fail_count=$((fail_count + 1))
  printf '[FAIL] %s\n' "$1"
}

port_from_url() {
  python3 - "$1" <<'PY'
from urllib.parse import urlparse
import sys
url = urlparse(sys.argv[1])
print(url.port or (443 if url.scheme == "https" else 80))
PY
}

print_listener() {
  local label="$1"
  local port="$2"
  if command -v lsof >/dev/null 2>&1; then
    local line
    line="$(lsof -nP -iTCP:"$port" -sTCP:LISTEN 2>/dev/null | awk 'NR==2 {print $1, $2, $9}' || true)"
    if [[ -n "$line" ]]; then
      pass "$label listens on port $port ($line)"
      local pid
      pid="$(awk '{print $2}' <<<"$line")"
      if [[ "$label" == "backend" ]]; then
        ps -p "$pid" -o pid=,lstart=,comm= 2>/dev/null | sed 's/^/[INFO] backend process /' || true
      fi
      return
    fi
  fi
  fail "$label is not listening on port $port"
}

check_http() {
  local label="$1"
  local url="$2"
  local status
  status="$(curl -sS -o /tmp/cyberfusion-doctor-http.out -w '%{http_code}' "$url" 2>/tmp/cyberfusion-doctor-http.err || true)"
  if [[ "$status" == "200" ]]; then
    pass "$label returned HTTP 200"
  else
    fail "$label returned ${status:-curl-error}: $(cat /tmp/cyberfusion-doctor-http.err 2>/dev/null)"
  fi
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

check_frontend_proxy() {
  local backend_port expected_target proxied_url status body
  backend_port="$(port_from_url "$API_BASE_URL")"
  expected_target="http://127.0.0.1:${backend_port}"
  proxied_url="${BASE_URL%/}/api/health"
  body="$(mktemp /tmp/cyberfusion-proxy-health.XXXXXX.json)"
  status="$(curl -sS -o "$body" -w '%{http_code}' "$proxied_url" 2>/tmp/cyberfusion-proxy-health.err || true)"
  if [[ "$status" == "200" ]] && python3 - "$body" <<'PY'
import json
import sys
with open(sys.argv[1], encoding="utf-8") as fh:
    body = json.load(fh)
raise SystemExit(0 if (body.get("data") or {}).get("status") == "UP" else 1)
PY
  then
    pass "frontend proxy /api/health reaches backend on port $backend_port"
  else
    fail "frontend proxy /api/health did not reach backend $expected_target (HTTP ${status:-curl-error})"
  fi
  rm -f "$body"

  if command -v lsof >/dev/null 2>&1; then
    local frontend_port pid process_env
    frontend_port="$(port_from_url "$BASE_URL")"
    pid="$(lsof -nP -iTCP:"$frontend_port" -sTCP:LISTEN 2>/dev/null | awk 'NR==2 {print $2}' || true)"
    if [[ -n "$pid" ]]; then
      process_env="$(ps eww -p "$pid" 2>/dev/null || true)"
      if grep -q "VITE_API_PROXY_TARGET=${expected_target}" <<<"$process_env"; then
        pass "frontend process has VITE_API_PROXY_TARGET=$expected_target"
      else
        warn "frontend process env did not expose VITE_API_PROXY_TARGET=$expected_target; proxied /api/health is the runtime proof"
      fi
    fi
  fi
}

check_health() {
  local url="${API_BASE_URL%/}/health"
  local body status
  body="$(mktemp /tmp/cyberfusion-health.XXXXXX.json)"
  status="$(curl -sS -o "$body" -w '%{http_code}' "$url" 2>/tmp/cyberfusion-health.err || true)"
  if [[ "$status" != "200" ]]; then
    fail "/api/health returned ${status:-curl-error}: $(cat /tmp/cyberfusion-health.err 2>/dev/null)"
    rm -f "$body"
    return
  fi
  if python3 - "$body" <<'PY'
import json
import sys
path = sys.argv[1]
with open(path, encoding="utf-8") as fh:
    body = json.load(fh)
data = body.get("data") or {}
deps = data.get("dependencies") or {}
print(f"[INFO] health status={data.get('status')} version={data.get('version')} profile={data.get('profile')}")
for key in ("database", "schema", "seed", "redis"):
    item = deps.get(key) or {}
    print(f"[INFO] health {key}: {item.get('status')} {item.get('message') or ''}".rstrip())
raise SystemExit(0 if data.get("status") == "UP" else 1)
PY
  then
    pass "/api/health reports UP"
  else
    fail "/api/health reports DOWN"
  fi
  rm -f "$body"
}

mysql_query() {
  local query="$1"
  local err_file="$2"
  : >"$err_file"

  if [[ -n "${DB_PASSWORD:-}" ]] && command -v mysql >/dev/null 2>&1; then
    MYSQL_PWD="$DB_PASSWORD" mysql \
      --default-character-set=utf8mb4 \
      -h"$DB_HOST" \
      -P"$DB_PORT" \
      -u"$DB_USERNAME" \
      --batch \
      --skip-column-names \
      "$DB_NAME" <<<"$query" 2>"$err_file"
    return
  fi

  if ! command -v docker >/dev/null 2>&1; then
    printf 'Neither local mysql client nor docker CLI is available; cannot run SQL diagnostics.\n' >"$err_file"
    return 127
  fi

  if ! docker ps --format '{{.Names}}' 2>/tmp/cyberfusion-docker.err | grep -qx "$MYSQL_CONTAINER"; then
    printf 'MySQL container %s is not reachable: %s\n' "$MYSQL_CONTAINER" "$(cat /tmp/cyberfusion-docker.err 2>/dev/null)" >"$err_file"
    return 127
  fi

  local effective_password="${DB_PASSWORD:-}"
  if [[ -z "$effective_password" ]]; then
    effective_password="$(docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$MYSQL_CONTAINER" 2>/tmp/cyberfusion-docker-inspect.err \
      | awk -F= '$1=="MYSQL_ROOT_PASSWORD" {print $2; exit}')"
  fi
  if [[ -z "$effective_password" ]]; then
    printf 'DB_PASSWORD is not set and MYSQL_ROOT_PASSWORD is unavailable from Docker container metadata. Export DB_PASSWORD outside source before applying SQL or running live smoke.\n' >"$err_file"
    return 2
  fi

  MYSQL_PWD="$effective_password" docker exec -e MYSQL_PWD -i "$MYSQL_CONTAINER" \
    mysql \
      --default-character-set=utf8mb4 \
      -u"$DB_USERNAME" \
      --batch \
      --skip-column-names \
      "$DB_NAME" <<<"$query" 2>"$err_file"
}

check_mysql_seed() {
  local query
  query="
SELECT 'tables', COUNT(*) FROM information_schema.tables
 WHERE table_schema = DATABASE()
   AND table_name IN ('sys_user','sys_menu','sys_role_menu','soc_asset','soc_external_event','soc_alert','soc_ticket','soc_report','soc_local_check_command','soc_detection_rule_policy','soc_event_adapter_profile','soc_correlation_rule','soc_incident_cluster','soc_incident_evidence');
SELECT 'menus', COUNT(*) FROM sys_menu WHERE path IN ('/soc/incidents','/soc/policies','/soc/tickets','/soc/reports','/client/workbench') AND status = 1;
SELECT 'permissions', COUNT(*) FROM sys_menu WHERE permission IN ('soc:policy:list','soc:incident:list','soc:correlation-rule:list') AND status = 1;
SELECT 'admin', COUNT(*) FROM sys_user WHERE username = 'admin' AND status = 1;
"
  local output
  if output="$(mysql_query "$query" /tmp/cyberfusion-mysql.err)"; then
    printf '%s\n' "$output" | sed 's/^/[INFO] mysql /'
    if python3 - "$output" <<'PY'
import sys
rows = {}
for line in sys.argv[1].splitlines():
    parts = line.split("\t")
    if len(parts) == 2:
        rows[parts[0]] = int(parts[1])
ok = rows.get("tables", 0) >= 13 and rows.get("menus", 0) >= 5 and rows.get("permissions", 0) >= 3 and rows.get("admin", 0) >= 1
raise SystemExit(0 if ok else 1)
PY
    then
      pass "Docker MySQL key tables and seed rows are present"
    else
      fail "Docker MySQL key tables or seed rows are incomplete"
    fi
  else
    local mysql_error
    mysql_error="$(cat /tmp/cyberfusion-mysql.err 2>/dev/null)"
    fail "MySQL SQL query failed; local mysql is optional and Docker client fallback is supported. Container health is not SQL authentication proof. Verify DB_PASSWORD with a real SELECT 1 before applying SQL or running live smoke. ${mysql_error}"
  fi
}

check_runtime_permissions() {
  local admin_token employee_token admin_me
  admin_token="$(login "$ADMIN_USER" "$ADMIN_PASSWORD" || true)"
  if [[ -z "$admin_token" ]]; then
    fail "admin login failed; check current demo password and seed"
    return
  fi
  pass "admin login succeeded"
  if api_call GET /auth/me "$admin_token" "" && [[ "$LAST_STATUS" == "200" ]]; then
    admin_me="$LAST_BODY"
    if printf '%s' "$admin_me" | python3 -c '
import json
import sys
body = json.load(sys.stdin)
targets = {"/soc/policies", "/soc/incidents", "/soc/reports"}
seen = set()
def walk(items):
    for item in items or []:
        path = item.get("path")
        if path:
            seen.add(path)
        walk(item.get("children") or [])
walk((body.get("data") or {}).get("menus") or [])
missing = sorted(targets - seen)
if missing:
    print("missing admin menu paths: " + ", ".join(missing))
    raise SystemExit(1)
'; then
      pass "admin /auth/me includes latest SOC menus"
    else
      fail "admin /auth/me is missing latest SOC menus"
    fi
    if printf '%s' "$admin_me" | python3 -c '
import json
import sys
body = json.load(sys.stdin)
targets = {"soc:policy:list", "soc:incident:list", "soc:correlation-rule:list"}
permissions = set((body.get("data") or {}).get("permissions") or [])
missing = sorted(targets - permissions)
if missing:
    print("missing admin permissions: " + ", ".join(missing))
    raise SystemExit(1)
'; then
      pass "admin /auth/me includes latest policy and incident permissions"
    else
      fail "admin /auth/me is missing latest policy or incident permissions"
    fi
  else
    fail "admin /auth/me returned ${LAST_STATUS:-curl-error}"
  fi

  employee_token="$(login "$EMPLOYEE_USER" "$EMPLOYEE_PASSWORD" || true)"
  if [[ -z "$employee_token" ]]; then
    fail "employee login failed; check current demo password and seed"
    return
  fi
  pass "employee login succeeded"
  local denied_ok=1
  for path in "/soc/policies/local-check-commands" "/soc/incidents?pageNum=1&pageSize=1" "/soc/correlation-rules?pageNum=1&pageSize=1"; do
    if api_call GET "$path" "$employee_token" "" && [[ "$LAST_STATUS" == "403" ]]; then
      pass "employee denied $path"
    else
      denied_ok=0
      fail "employee access boundary failed for $path: got ${LAST_STATUS:-curl-error}"
    fi
  done
  if [[ "$denied_ok" == "1" ]] && api_call GET "/client/local-terminal/commands?os=Linux" "$employee_token" "" && [[ "$LAST_STATUS" == "200" ]]; then
    pass "employee can read active local-check commands only"
  else
    fail "employee local-check command catalog returned ${LAST_STATUS:-curl-error}"
  fi
}

printf 'CyberFusion dev doctor\n'
printf 'Frontend URL: %s\n' "$BASE_URL"
printf 'Backend API: %s\n' "$API_BASE_URL"
printf 'MySQL container: %s\n\n' "$MYSQL_CONTAINER"

print_listener "frontend" "$(port_from_url "$BASE_URL")"
print_listener "backend" "$(port_from_url "$API_BASE_URL")"
check_http "frontend shell" "$BASE_URL"
check_frontend_proxy
check_health
check_mysql_seed
check_runtime_permissions

printf '\nSummary: PASS=%s WARN=%s FAIL=%s\n' "$pass_count" "$warn_count" "$fail_count"
if [[ "$fail_count" -gt 0 ]]; then
  exit 1
fi

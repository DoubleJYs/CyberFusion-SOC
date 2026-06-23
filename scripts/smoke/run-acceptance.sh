#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
API_BASE="${CYBERFUSION_API_BASE:-http://localhost:18080/api}"
BATCH_ID="${CYBERFUSION_ACCEPTANCE_BATCH_ID:-DEMO-RANGE-ACCEPTANCE-SMOKE}"
DEMO_PASSWORD="${CYBERFUSION_DEMO_PASSWORD:-Admin@123456}"
ADMIN_PASSWORD="${CYBERFUSION_ADMIN_PASSWORD:-${DEMO_PASSWORD}}"
ANALYST_PASSWORD="${CYBERFUSION_ANALYST_PASSWORD:-${DEMO_PASSWORD}}"
EMPLOYEE_PASSWORD="${CYBERFUSION_EMPLOYEE_PASSWORD:-${DEMO_PASSWORD}}"
MODE="execute"

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  cat <<USAGE
Usage: scripts/smoke/run-acceptance.sh [--dry-run]

Runs the CyberFusion SOC release-candidate smoke test against a local backend.

Environment:
  CYBERFUSION_API_BASE             Default: http://localhost:18080/api
  CYBERFUSION_ACCEPTANCE_BATCH_ID  Default: DEMO-RANGE-ACCEPTANCE-SMOKE
  CYBERFUSION_DEMO_PASSWORD        Shared fallback demo account password
  CYBERFUSION_ADMIN_PASSWORD       Admin demo account password
  CYBERFUSION_ANALYST_PASSWORD     Analyst demo account password
  CYBERFUSION_EMPLOYEE_PASSWORD    Employee demo account password

--dry-run means this script uses only local demo data and dry-run notification
paths. It still calls the local API to prove the acceptance chain. It never
calls public targets, real WAF/IDS/SIEM, email, webhook, Feishu, WeCom,
DingTalk, Slack, or any external sender.
USAGE
  exit 0
fi

if [[ "${1:-}" == "--dry-run" ]]; then
  MODE="dry-run"
fi

pass_count=0
fail_count=0
admin_token=""
analyst_token=""
employee_token=""
last_body=""
last_status=""

section() {
  printf '\n== %s ==\n' "$1"
}

pass() {
  pass_count=$((pass_count + 1))
  printf '[PASS] %s\n' "$1"
}

fail() {
  fail_count=$((fail_count + 1))
  printf '[FAIL] %s\n' "$1" >&2
  exit 1
}

require_tool() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing required tool: $1"
}

json_query() {
  local expr="$1"
  python3 -c '
import json, sys
d = json.load(sys.stdin)
expr = sys.argv[1]
try:
    value = eval(expr, {"__builtins__": {}}, {"d": d})
except Exception as exc:
    raise SystemExit(f"json query failed: {expr}: {exc}")
if isinstance(value, (dict, list)):
    print(json.dumps(value, ensure_ascii=False))
elif value is None:
    print("")
else:
    print(value)
' "$expr"
}

assert_json() {
  local body="$1"
  local expr="$2"
  local message="$3"
  python3 -c '
import json, sys
d = json.loads(sys.argv[1])
expr = sys.argv[2]
message = sys.argv[3]
safe_names = {
    "__builtins__": {},
    "d": d,
    "all": all,
    "any": any,
    "json": json,
    "len": len,
    "set": set,
    "str": str,
}
ok = bool(eval(expr, safe_names, {}))
if not ok:
    raise SystemExit(message)
' "$body" "$expr" "$message" || fail "$message"
  pass "$message"
}

api_call() {
  local method="$1"
  local path="$2"
  local token="${3:-}"
  local data="${4:-}"
  local expected="${5:-200}"
  local response
  local url="${API_BASE}${path}"
  local -a curl_args=(-sS -w $'\n%{http_code}' -X "$method" "$url")
  if [[ -n "$token" ]]; then
    curl_args+=(-H "Authorization: Bearer ${token}")
  fi
  if [[ -n "$data" ]]; then
    curl_args+=(-H 'Content-Type: application/json' --data "$data")
  fi
  response="$(curl "${curl_args[@]}")"
  last_status="${response##*$'\n'}"
  last_body="${response%$'\n'"$last_status"}"
  [[ "$last_status" == "$expected" ]] || {
    printf 'Request failed: %s %s expected=%s actual=%s\n%s\n' "$method" "$path" "$expected" "$last_status" "$last_body" >&2
    return 1
  }
}

login_as() {
  local username="$1"
  local password="${2:-}"
  local candidates=()
  if [[ -n "$password" ]]; then
    candidates+=("$password")
  fi
  if [[ "$username" == "admin" && -z "${CYBERFUSION_ADMIN_PASSWORD:-}" ]]; then
    candidates+=("admin123")
  fi
  local candidate response status body token
  local -a curl_args
  for candidate in "${candidates[@]}"; do
    curl_args=(-sS -w $'\n%{http_code}' -X POST "${API_BASE}/auth/login" -H 'Content-Type: application/json' --data "{\"username\":\"${username}\",\"password\":\"${candidate}\"}")
    response="$(curl "${curl_args[@]}")" || continue
    status="${response##*$'\n'}"
    body="${response%$'\n'"$status"}"
    if [[ "$status" == "200" ]]; then
      token="$(printf '%s' "$body" | json_query "d['data']['accessToken']" || true)"
      if [[ -n "$token" ]]; then
        printf '%s' "$token"
        return 0
      fi
    fi
  done
  return 1
}

extract_first_id() {
  printf '%s' "$1" | json_query "d['data']['records'][0]['id']"
}

section "Prerequisites"
require_tool curl
require_tool python3
[[ -d "$ROOT_DIR" ]] || fail "project root not found"
pass "tools and project root are available"

section "Login and identity"
admin_token="$(login_as admin "$ADMIN_PASSWORD")"
[[ -n "$admin_token" ]] || fail "admin token is empty"
pass "admin login"
api_call GET /auth/me "$admin_token" "" 200 || fail "admin me"
assert_json "$last_body" "len(d['data']['permissions']) > 0" "admin permissions returned"
assert_json "$last_body" "len(d['data']['menus']) > 0" "admin menus returned"

section "Demo Range acceptance chain"
api_call GET /soc/dashboard/overview "$admin_token" "" 200 || fail "dashboard overview"
pass "dashboard overview reachable"
api_call POST /soc/demo-range/batches/import "$admin_token" "{\"batchId\":\"${BATCH_ID}\",\"linkAlerts\":true}" 200 || fail "demo batch import"
assert_json "$last_body" "d['data']['batchId'] == '${BATCH_ID}'" "batchId returned"
assert_json "$last_body" "d['data']['failedItems'] == 0" "demo batch imports without source failures"
assert_json "$last_body" "set(['waf','zap','trivy','wazuh','suricata','zeek']).issubset({s['sourceType'] for s in d['data']['sources']})" "demo batch source summary covers six demo sources"
assert_json "$last_body" "d['data']['importedEvents'] >= 5" "demo batch imported external events"
assert_json "$last_body" "d['data']['createdAlerts'] >= 5" "demo batch linked alerts"
assert_json "$last_body" "d['data']['createdVulnerabilities'] >= 1" "demo batch imported Trivy vulnerability"

api_call GET "/soc/demo-range/batches/${BATCH_ID}/evidence-chain" "$admin_token" "" 200 || fail "evidence chain"
assert_json "$last_body" "d['data']['summary']['eventCount'] >= 5" "evidence chain has events"
assert_json "$last_body" "d['data']['summary']['alertCount'] >= 5" "evidence chain has alerts"

api_call GET "/soc/external-events?pageNum=1&pageSize=50&keyword=${BATCH_ID}" "$admin_token" "" 200 || fail "external events"
assert_json "$last_body" "set(['waf','zap','wazuh','suricata','zeek']).issubset({r['sourceType'] for r in d['data']['records']})" "external events cover WAF/ZAP/Wazuh/Suricata/Zeek"
assert_json "$last_body" "set(['waf','zap','wazuh','suricata','zeek']).issubset({r['sourceType'] for r in d['data']['records'] if r.get('batchId') == '${BATCH_ID}' and r.get('demoCaseId') and r.get('correlationKey')})" "external events expose structured batch, demo case, and correlation fields"
assert_json "$last_body" "any(r.get('sourceType') == 'waf' and r.get('targetUrl') and r.get('action') and r.get('requestId') for r in d['data']['records'])" "WAF external event exposes targetUrl action and requestId"
api_call GET "/soc/vulnerabilities?pageNum=1&pageSize=20&keyword=${BATCH_ID}" "$admin_token" "" 200 || fail "trivy vulnerability lookup"
assert_json "$last_body" "d['data']['total'] >= 1" "Trivy evidence is visible in vulnerability center"

api_call GET "/soc/alerts?pageNum=1&pageSize=20&keyword=${BATCH_ID}&sourceType=waf" "$admin_token" "" 200 || fail "alerts lookup"
assert_json "$last_body" "d['data']['total'] >= 1" "linked alerts are searchable"
alert_id="$(extract_first_id "$last_body")"
api_call GET "/soc/alerts/${alert_id}" "$admin_token" "" 200 || fail "alert detail"
assert_json "$last_body" "d['data'].get('batchId') == '${BATCH_ID}'" "alert detail contains batchId"
assert_json "$last_body" "d['data'].get('eventType') and d['data'].get('demoCaseId') and d['data'].get('correlationKey')" "alert detail contains structured event linkage fields"
assert_json "$last_body" "d['data'].get('targetUrl') and d['data'].get('action') and d['data'].get('evidenceSummary')" "WAF linked alert contains targetUrl action and evidenceSummary"

api_call POST /soc/incidents/correlate "$admin_token" "" 200 || fail "correlate incidents"
assert_json "$last_body" "d['data']['activeRules'] >= 1" "correlation engine has active rules"
assert_json "$last_body" "d['data']['evidenceWritten'] >= 1" "correlation engine writes explainable evidence"
api_call GET "/soc/incidents?pageNum=1&pageSize=20&keyword=${BATCH_ID}" "$admin_token" "" 200 || fail "incident clusters"
assert_json "$last_body" "d['data']['total'] >= 1" "incident cluster is searchable by batch"
incident_id="$(extract_first_id "$last_body")"
api_call GET "/soc/incidents/${incident_id}" "$admin_token" "" 200 || fail "incident detail"
assert_json "$last_body" "len(d['data'].get('evidence') or []) >= 1" "incident detail includes evidence"
assert_json "$last_body" "all(e.get('relationReason') for e in d['data'].get('evidence') or [])" "incident evidence has relation reasons"
api_call POST "/soc/incidents/${incident_id}/ticket" "$admin_token" "{\"remark\":\"Acceptance incident ticket ${BATCH_ID}\"}" 200 || fail "incident cluster to ticket"
incident_ticket_id="$(printf '%s' "$last_body" | json_query "d['data']['id']")"
assert_json "$last_body" "str(d['data']['ticketNo']).startswith('INC-')" "incident cluster can be converted to ticket"
api_call GET "/soc/tickets/${incident_ticket_id}" "$admin_token" "" 200 || fail "incident ticket detail"
assert_json "$last_body" "len(d['data']['timeline']) >= 1" "incident ticket has timeline records"
api_call GET "/soc/alerts/${alert_id}/related-incidents" "$admin_token" "" 200 || fail "alert related incidents"
assert_json "$last_body" "len(d['data']) >= 1" "alert links to related incident clusters"

api_call GET "/soc/alerts/${alert_id}/playbook-suggestions" "$admin_token" "" 200 || fail "alert playbook suggestions"
assert_json "$last_body" "len(d['data']) >= 1" "alert has response playbook suggestion"
playbook_id="$(printf '%s' "$last_body" | json_query "d['data'][0]['playbook']['id']")"
api_call POST "/soc/alerts/${alert_id}/apply-playbook" "$admin_token" "{\"playbookId\":${playbook_id},\"remark\":\"Acceptance smoke ${BATCH_ID}\"}" 200 || fail "apply response playbook"
ticket_id="$(printf '%s' "$last_body" | json_query "d['data']['ticket']['id']")"
assert_json "$last_body" "str(d['data']['ticket']['ticketNo']).startswith('INC-')" "ticket created or reused by playbook"
assert_json "$last_body" "len(d['data']['tasks']) >= 1" "playbook creates ticket tasks"
api_call GET "/soc/tickets/${ticket_id}" "$admin_token" "" 200 || fail "ticket timeline"
assert_json "$last_body" "'Demo Range' in json.dumps(d['data']['timeline'], ensure_ascii=False)" "ticket timeline includes Demo Range source"
assert_json "$last_body" "len(d['data'].get('tasks') or []) >= 1" "ticket detail includes playbook tasks"
task_id="$(printf '%s' "$last_body" | json_query "d['data']['tasks'][0]['id']")"
api_call POST /soc/risk-scoring/recalculate "$admin_token" "" 200 || fail "asset risk recalculation"
assert_json "$last_body" "d['data']['recalculatedAssets'] >= 1" "asset risk recalculation covers demo assets"
api_call GET "/soc/risk-scoring/top-assets?limit=5" "$admin_token" "" 200 || fail "top risk assets"
assert_json "$last_body" "any(any(f.get('factorType') in ['incident_open','incident_high'] for f in item.get('factors') or []) for item in d['data'])" "risk profile includes incident cluster factors"
assert_json "$last_body" "any(any(f.get('factorType') in ['playbook_open','employee_pending'] for f in item.get('factors') or []) for item in d['data'])" "risk profile includes playbook or employee task factors"
asset_id="$(printf '%s' "$last_body" | json_query "d['data'][0]['asset']['id']")"
api_call GET "/soc/recommendations/top?limit=10" "$admin_token" "" 200 || fail "top recommendation actions"
assert_json "$last_body" "len(d['data']) >= 3" "recommendation API returns prioritized actions"
assert_json "$last_body" "any(r.get('relatedBizType') == 'incident' for r in d['data'])" "recommendations include incident cluster action"
assert_json "$last_body" "any(r.get('relatedBizType') == 'vulnerability' for r in d['data'])" "recommendations include vulnerability action"
assert_json "$last_body" "any(r.get('relatedBizType') in ['ticket','playbook_task','client_task'] for r in d['data'])" "recommendations include ticket or task action"
recommendation_key="$(printf '%s' "$last_body" | json_query "d['data'][0]['key']")"
recommendation_type="$(printf '%s' "$last_body" | json_query "d['data'][0]['relatedBizType']")"
recommendation_id="$(printf '%s' "$last_body" | json_query "d['data'][0]['relatedBizId']")"
api_call POST "/soc/recommendations/${recommendation_key}/record" "$admin_token" "{\"actionType\":\"view\",\"relatedBizType\":\"${recommendation_type}\",\"relatedBizId\":${recommendation_id},\"note\":\"Acceptance recommendation view ${BATCH_ID}\"}" 200 || fail "record recommendation action"
assert_json "$last_body" "d['data']['recommendationKey'] == '${recommendation_key}'" "recommendation adoption record is written"
api_call GET "/soc/assets/${asset_id}/recommendations?limit=10" "$admin_token" "" 200 || fail "asset recommendations"
assert_json "$last_body" "len(d['data']) >= 1 and all(r.get('reason') and r.get('recommendedAction') for r in d['data'])" "asset recommendations are explainable"
api_call GET "/soc/trends/anomalies/top?limit=5" "$admin_token" "" 200 || fail "trend anomaly top"
assert_json "$last_body" "len(d['data']) >= 1" "trend anomaly API returns demo spike"
assert_json "$last_body" "any(r.get('reason') and r.get('recommendation') and r.get('currentCount', 0) >= 1 for r in d['data'])" "trend anomalies include explainable reason and recommendation"
assert_json "$last_body" "any(r.get('eventType') in ['cross_source_rise','severity_ratio_rise'] or '突增' in r.get('title','') for r in d['data'])" "trend anomalies detect spike or cross-source rise"
api_call GET "/soc/trends/aggregations?assetIp=10.20.1.15&granularity=hour&limit=20" "$admin_token" "" 200 || fail "trend aggregations"
assert_json "$last_body" "len(d['data']) >= 1 and all(r.get('bucket') and r.get('count', 0) >= 1 for r in d['data'])" "trend aggregation API groups events by time window"

section "Algorithm governance dry-run replay"
api_call GET "/soc/algorithm-center/overview" "$admin_token" "" 200 || fail "algorithm overview"
assert_json "$last_body" "set(['correlation','risk_scoring','recommendation','trend_anomaly']).issubset({r['algorithmType'] for r in d['data']['cards']})" "algorithm overview covers four governance objects"
assert_json "$last_body" "all('recentHitCount' in r and 'sourceCoverage' in r and 'version' in r for r in d['data']['cards'])" "algorithm overview exposes hit count source coverage and version"
api_call GET "/soc/incidents?pageNum=1&pageSize=1&keyword=${BATCH_ID}" "$admin_token" "" 200 || fail "incident count before algorithm replay"
algorithm_incidents_before="$(printf '%s' "$last_body" | json_query "d['data']['total']")"
api_call GET "/soc/tickets?pageNum=1&pageSize=1&keyword=${BATCH_ID}" "$admin_token" "" 200 || fail "ticket count before algorithm replay"
algorithm_tickets_before="$(printf '%s' "$last_body" | json_query "d['data']['total']")"
api_call GET "/soc/reports?pageNum=1&pageSize=1&keyword=${BATCH_ID}" "$admin_token" "" 200 || fail "report count before algorithm replay"
algorithm_reports_before="$(printf '%s' "$last_body" | json_query "d['data']['total']")"
api_call POST "/soc/algorithm-center/replay" "$admin_token" "{\"algorithmType\":\"all\",\"policyMode\":\"active\",\"batchId\":\"${BATCH_ID}\",\"saveEvaluation\":true}" 200 || fail "algorithm replay dry-run"
assert_json "$last_body" "d['data']['dryRun'] is True and d['data']['realWrites'] is False" "algorithm replay is dry-run without real writes"
assert_json "$last_body" "d['data']['inputCount'] >= 1 and d['data']['outputCount'] >= 1" "algorithm replay returns preview inputs and outputs"
assert_json "$last_body" "len(d['data']['allItems']) >= 1 and all(i.get('reason') for i in d['data']['allItems'])" "algorithm replay preview items are explainable"
assert_json "$last_body" "d['data']['diffSummary'].get('realWrites') is False and d['data']['diffSummary'].get('dryRun') is True" "algorithm replay diff summary confirms no production writes"
algorithm_evaluation_id="$(printf '%s' "$last_body" | json_query "d['data']['evaluationId']")"
api_call GET "/soc/incidents?pageNum=1&pageSize=1&keyword=${BATCH_ID}" "$admin_token" "" 200 || fail "incident count after algorithm replay"
algorithm_incidents_after="$(printf '%s' "$last_body" | json_query "d['data']['total']")"
api_call GET "/soc/tickets?pageNum=1&pageSize=1&keyword=${BATCH_ID}" "$admin_token" "" 200 || fail "ticket count after algorithm replay"
algorithm_tickets_after="$(printf '%s' "$last_body" | json_query "d['data']['total']")"
api_call GET "/soc/reports?pageNum=1&pageSize=1&keyword=${BATCH_ID}" "$admin_token" "" 200 || fail "report count after algorithm replay"
algorithm_reports_after="$(printf '%s' "$last_body" | json_query "d['data']['total']")"
[[ "$algorithm_incidents_before" == "$algorithm_incidents_after" ]] || fail "algorithm replay wrote real incident clusters"
[[ "$algorithm_tickets_before" == "$algorithm_tickets_after" ]] || fail "algorithm replay wrote real tickets"
[[ "$algorithm_reports_before" == "$algorithm_reports_after" ]] || fail "algorithm replay wrote real reports"
pass "algorithm replay does not write incident ticket or report records"
api_call GET "/soc/algorithm-center/evaluations/${algorithm_evaluation_id}" "$admin_token" "" 200 || fail "algorithm evaluation detail"
assert_json "$last_body" "len(d['data']['items']) >= 1 and all(i.get('reason') for i in d['data']['items'])" "saved algorithm evaluation keeps explainable preview items"

api_call GET "/soc/operations/overview" "$admin_token" "" 200 || fail "operations overview"
assert_json "$last_body" "len(d['data']['metrics']) >= 10" "operations overview returns metric catalog"
assert_json "$last_body" "all(m.get('metricCode') and m.get('metricName') and m.get('explanation') and m.get('drilldownTarget') for m in d['data']['metrics'])" "operations metrics are explainable and drilldown-ready"
assert_json "$last_body" "any(m.get('metricCode','').startswith('risk.') for m in d['data']['metrics'])" "operations metrics include risk metrics"
assert_json "$last_body" "any(m.get('metricCode','').startswith('ticket.') for m in d['data']['metrics'])" "operations metrics include ticket metrics"
assert_json "$last_body" "any(m.get('metricCode','').startswith('recommendation.') for m in d['data']['metrics'])" "operations metrics include recommendation metrics"
assert_json "$last_body" "any(m.get('metricCode','').startswith('client_task.') for m in d['data']['metrics'])" "operations metrics include client task metrics"
assert_json "$last_body" "any(m.get('metricCode') == 'client_task.completion.rate' for m in d['data']['metrics'])" "operations metrics include client task completion rate"
assert_json "$last_body" "any(m.get('metricCode','').startswith('trend.') for m in d['data']['metrics'])" "operations metrics include trend metrics"
api_call GET "/soc/operations/sla" "$admin_token" "" 200 || fail "operations SLA"
assert_json "$last_body" "set(['totalTickets','pendingTickets','overdueTickets','closeRate','mttaHours','mttrHours','playbookApplications','playbookCompletionRate']).issubset(d['data'].keys())" "operations SLA exposes ticket and playbook efficiency"
api_call GET "/soc/operations/risk-trend" "$admin_token" "" 200 || fail "operations risk trend"
assert_json "$last_body" "len(d['data']['points']) == 7 and 'change24h' in d['data'] and 'change7d' in d['data']" "operations risk trend exposes 24h and 7d changes"
api_call GET "/soc/operations/recommendation-adoption" "$admin_token" "" 200 || fail "operations recommendation adoption"
assert_json "$last_body" "set(['totalRecommendations','adoptedRecommendations','adoptionRate']).issubset(d['data'].keys())" "operations recommendation adoption exposes rate"
api_call GET "/soc/operations/client-tasks" "$admin_token" "" 200 || fail "operations client tasks"
assert_json "$last_body" "set(['totalTasks','completedTasks','overdueTasks','completionRate','checkupCoverageRate']).issubset(d['data'].keys())" "operations client task metrics expose completion and coverage"
api_call POST "/soc/tickets/${ticket_id}/tasks/${task_id}/start" "$admin_token" "{\"remark\":\"Acceptance task start ${BATCH_ID}\"}" 200 || fail "start ticket task"
api_call POST "/soc/tickets/${ticket_id}/tasks/${task_id}/complete" "$admin_token" "{\"remark\":\"Acceptance task complete ${BATCH_ID}\",\"evidenceText\":\"dry-run acceptance evidence\"}" 200 || fail "complete ticket task"

api_call POST /soc/reports/generate "$admin_token" "{\"reportType\":\"security_validation\",\"batchId\":\"${BATCH_ID}\"}" 200 || fail "security validation report"
assert_json "$last_body" "d['data']['reportType'] == 'security_validation'" "security validation report generated"
assert_json "$last_body" "'事件簇' in d['data']['summary'] and '风险' in d['data']['summary'] and '推荐动作' in d['data']['summary']" "security validation report contains incident risk and recommendation summary"
assert_json "$last_body" "'运营指标' in d['data']['summary'] and '员工待办' in d['data']['summary'] and 'dry-run' in d['data']['summary']" "security validation report contains operation clientTask and dryRun summary"
assert_json "$last_body" "'未扫描公网' in d['data']['summary'] and '未发送真实通知' in d['data']['summary'] and '未执行攻击' in d['data']['summary']" "security validation report documents safety boundaries"

api_call POST /soc/external-events/shuffle/demo-notification "$admin_token" "" 200 || fail "shuffle dry-run"
assert_json "$last_body" "d['data']['status'] == 'DRY_RUN'" "Shuffle notification remains dry-run"
api_call GET "/soc/settings/notification-logs?pageNum=1&pageSize=20&keyword=${BATCH_ID}" "$admin_token" "" 200 || fail "notification logs"
assert_json "$last_body" "d['data']['total'] >= 1" "batch notification dry-run log found"

section "Policy and adapter smoke"
api_call GET "/client/local-terminal/commands?os=Linux" "$admin_token" "" 200 || fail "local commands"
assert_json "$last_body" "len(d['data']) >= 5 and all(item.get('builtInFallback') in [False, 'false', 0] for item in d['data'])" "active local check commands returned from DB"
api_call GET "/soc/policies/event-adapters?pageNum=1&pageSize=20&status=active" "$admin_token" "" 200 || fail "event adapters"
assert_json "$last_body" "set(['waf','zap','trivy','wazuh','suricata','zeek']).issubset({r['sourceType'] for r in d['data']['records']})" "active event adapters cover six demo sources"
adapter_id="$(extract_first_id "$last_body")"
api_call GET "/soc/policies/playbooks?pageNum=1&pageSize=20&status=active" "$admin_token" "" 200 || fail "response playbooks"
assert_json "$last_body" "set(['waf','zap','trivy','wazuh']).issubset({r['sourceType'] for r in d['data']['records']})" "active response playbooks cover core sources"
api_call GET "/soc/algorithm-center/overview" "$admin_token" "" 200 || fail "admin algorithm governance overview"
pass "admin can access algorithm governance overview"
api_call GET "/soc/correlation-rules?pageNum=1&pageSize=20&status=active" "$admin_token" "" 200 || fail "correlation rules"
assert_json "$last_body" "set(['cross_source_chain','event_count','value_count','frequency','temporal_ordered']).issubset({r['ruleType'] for r in d['data']['records']})" "active correlation rules cover v1 rule types"
assert_json "$last_body" "set(['same_asset_event_count','demo_batch_chain','waf_zap_wazuh_chain','network_ids_chain']).issubset({r.get('ruleCode') or r.get('ruleKey') for r in d['data']['records']})" "active correlation rules include required default seeds"
correlation_rule_id="$(extract_first_id "$last_body")"
api_call POST "/soc/correlation-rules/${correlation_rule_id}/validate" "$admin_token" "" 200 || fail "validate correlation rule"
assert_json "$last_body" "d['data']['passed'] is True" "correlation rule validation passes"
api_call GET "/soc/external-events?pageNum=1&pageSize=1&keyword=${BATCH_ID}" "$admin_token" "" 200 || fail "event count before preview"
event_count_before="$(printf '%s' "$last_body" | json_query "d['data']['total']")"
api_call POST "/soc/policies/event-adapters/${adapter_id}/preview" "$admin_token" '{"payload":"{\"eventType\":\"waf_block\",\"severity\":\"high\",\"ruleId\":\"WAF-PREVIEW\",\"assetIp\":\"10.20.1.15\",\"requestId\":\"preview-only\"}"}' 200 || fail "adapter preview"
assert_json "$last_body" "d['data']['normalizedEvent'] and d['data']['dedupKey']" "adapter preview returns normalized event and dedup key"
api_call GET "/soc/external-events?pageNum=1&pageSize=1&keyword=${BATCH_ID}" "$admin_token" "" 200 || fail "event count after preview"
event_count_after="$(printf '%s' "$last_body" | json_query "d['data']['total']")"
[[ "$event_count_before" == "$event_count_after" ]] || fail "adapter preview wrote database records"
pass "adapter preview does not write external events"

section "Permission smoke"
api_call GET /soc/policies/local-check-commands "$admin_token" "" 200 || fail "admin local policy list"
pass "admin can access local check policy list"
api_call GET /soc/policies/event-adapters "$admin_token" "" 200 || fail "admin adapter list"
pass "admin can access adapter list"
api_call GET /soc/policies/playbooks "$admin_token" "" 200 || fail "admin playbook list"
pass "admin can access response playbook list"

analyst_token="$(login_as analyst "$ANALYST_PASSWORD")"
api_call GET "/soc/alerts?pageNum=1&pageSize=10" "$analyst_token" "" 200 || fail "analyst alerts"
api_call GET "/soc/external-events?pageNum=1&pageSize=10" "$analyst_token" "" 200 || fail "analyst events"
api_call GET "/soc/tickets?pageNum=1&pageSize=10" "$analyst_token" "" 200 || fail "analyst tickets"
api_call GET "/soc/reports?pageNum=1&pageSize=10" "$analyst_token" "" 200 || fail "analyst reports"
api_call GET "/soc/incidents?pageNum=1&pageSize=10" "$analyst_token" "" 200 || fail "analyst incidents"
api_call GET "/soc/algorithm-center/evaluations?pageNum=1&pageSize=5" "$analyst_token" "" 200 || fail "analyst algorithm evaluations"
api_call POST "/soc/algorithm-center/replay" "$analyst_token" "{\"algorithmType\":\"all\",\"batchId\":\"${BATCH_ID}\",\"saveEvaluation\":false}" 403 || fail "analyst algorithm replay should be forbidden"
api_call POST "/soc/incidents/${incident_id}/close" "$analyst_token" '{"remark":"acceptance close permission check"}' 200 || fail "analyst incident close"
pass "analyst can read SOC handling surfaces"
api_call POST "/soc/policies/event-adapters/${adapter_id}/publish" "$analyst_token" "" 403 || fail "analyst policy publish should be forbidden"
pass "analyst cannot publish policy without permission"

employee_token="$(login_as operator "$EMPLOYEE_PASSWORD")"
api_call GET /soc/policies/local-check-commands "$employee_token" "" 403 || fail "employee policies should be forbidden"
pass "employee cannot access policy center APIs"
api_call GET "/soc/incidents?pageNum=1&pageSize=10" "$employee_token" "" 403 || fail "employee incidents should be forbidden"
pass "employee cannot access incident clusters"
api_call GET "/soc/trends/anomalies/top?limit=5" "$employee_token" "" 403 || fail "employee trend anomalies should be forbidden"
pass "employee cannot access SOC trend anomaly APIs"
api_call GET "/soc/operations/overview" "$employee_token" "" 403 || fail "employee operations overview should be forbidden"
pass "employee cannot access SOC operation metrics APIs"
api_call GET "/soc/reports?pageNum=1&pageSize=1" "$employee_token" "" 403 || fail "employee report management should be forbidden"
pass "employee cannot access SOC report management APIs"
api_call GET "/soc/algorithm-center/overview" "$employee_token" "" 403 || fail "employee algorithm overview should be forbidden"
pass "employee cannot access algorithm governance APIs"
api_call POST "/soc/algorithm-center/replay" "$employee_token" "{\"algorithmType\":\"all\",\"batchId\":\"${BATCH_ID}\"}" 403 || fail "employee algorithm replay should be forbidden"
pass "employee cannot execute algorithm replay"
api_call GET "/client/local-terminal/commands?os=Linux" "$employee_token" "" 200 || fail "employee local commands"
assert_json "$last_body" "len(d['data']) >= 5" "employee sees active local check commands"
api_call GET "/client/tasks" "$employee_token" "" 200 || fail "employee tasks"
assert_json "$last_body" "all(r.get('assigneeType') == 'employee' for r in d['data'])" "employee task API only returns employee tasks"
api_call GET "/client/devices?pageNum=1&pageSize=10" "$employee_token" "" 200 || fail "employee devices"
assert_json "$last_body" "all(r.get('ownerId') in [5, None] for r in d['data']['records'])" "employee client devices are scoped to current user"
employee_asset_ip="$(printf '%s' "$last_body" | json_query "d['data']['records'][0]['ip']")"
api_call GET "/client/security-keeper/next-actions?assetIp=${employee_asset_ip}&limit=5" "$employee_token" "" 200 || fail "employee next actions"
assert_json "$last_body" "all(r.get('title') and r.get('recommendedAction') and r.get('relatedBizType') for r in d['data'])" "employee next actions use ordinary actionable language"

section "Summary"
printf 'Mode: %s\n' "$MODE"
printf 'API base: %s\n' "$API_BASE"
printf 'Batch ID: %s\n' "$BATCH_ID"
printf 'Passed checks: %s\n' "$pass_count"
printf 'Failed checks: %s\n' "$fail_count"
printf 'Acceptance smoke completed without external targets or real notification senders.\n'

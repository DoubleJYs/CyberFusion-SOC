#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MYSQL_CONTAINER="${CYBERFUSION_MYSQL_CONTAINER:-cyberfusion-platform-mysql-1}"
DB_NAME="${DB_NAME:-cyberfusion_soc}"
DB_USERNAME="${DB_USERNAME:-root}"
EXPECTATION="report"

usage() {
  cat <<USAGE
Usage: scripts/smoke/check-demo-data-state.sh [--mysql-container NAME] [--expect-empty|--expect-present]

Read-only verification for CyberFusion demo-data isolation:
- sql/data.sql must not seed SOC business demo tables at startup.
- sys_user and sys_role must remain present.
- current MySQL demo rows are counted using the same demo markers as the app.

Environment:
  CYBERFUSION_MYSQL_CONTAINER  Default: cyberfusion-platform-mysql-1
  DB_NAME                      Default: cyberfusion_soc
  DB_USERNAME                  Default: root
  DB_PASSWORD                  Optional; if absent, Docker MYSQL_ROOT_PASSWORD is
                               used as a local fallback without printing it.

Examples:
  scripts/smoke/check-demo-data-state.sh
  scripts/smoke/check-demo-data-state.sh --expect-empty
  scripts/smoke/check-demo-data-state.sh --expect-present
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mysql-container)
      MYSQL_CONTAINER="$2"
      shift 2
      ;;
    --expect-empty)
      EXPECTATION="empty"
      shift
      ;;
    --expect-present)
      EXPECTATION="present"
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

fail() {
  printf '[FAIL] %s\n' "$1" >&2
  exit 1
}

pass() {
  printf '[PASS] %s\n' "$1"
}

info() {
  printf '[INFO] %s\n' "$1"
}

check_startup_seed() {
  local pattern
  pattern='INSERT[[:space:]]+(IGNORE[[:space:]]+)?INTO[[:space:]]+(soc_asset|soc_alert|soc_external_event|soc_ticket|soc_ticket_timeline|soc_report|soc_notification_log|soc_alert_whitelist|soc_vulnerability|soc_baseline_check|soc_file_integrity_event|soc_incident_cluster|soc_incident_evidence|soc_algorithm_evaluation|soc_algorithm_evaluation_item)\b'
  if command -v rg >/dev/null 2>&1; then
    if rg -n -i "$pattern" "${PROJECT_ROOT}/sql/data.sql"; then
      fail "sql/data.sql still seeds SOC business demo tables"
    fi
  elif grep -E -n -i "$pattern" "${PROJECT_ROOT}/sql/data.sql"; then
    fail "sql/data.sql still seeds SOC business demo tables"
  fi
  pass "sql/data.sql does not seed SOC business demo tables"
}

mysql_query() {
  local query="$1"
  local err_file="/tmp/cyberfusion-demo-state-mysql.err"
  : >"$err_file"

  if ! command -v docker >/dev/null 2>&1; then
    fail "docker CLI is required for this local MySQL state check"
  fi
  if ! docker ps --format '{{.Names}}' 2>/tmp/cyberfusion-demo-state-docker.err | grep -qx "$MYSQL_CONTAINER"; then
    fail "MySQL container is not reachable: ${MYSQL_CONTAINER}. $(cat /tmp/cyberfusion-demo-state-docker.err 2>/dev/null)"
  fi

  local effective_password="${DB_PASSWORD:-}"
  if [[ -z "$effective_password" ]]; then
    effective_password="$(docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$MYSQL_CONTAINER" \
      | awk -F= '$1=="MYSQL_ROOT_PASSWORD" {print $2; exit}')"
  fi
  if [[ -z "$effective_password" ]]; then
    fail "DB_PASSWORD is not set and MYSQL_ROOT_PASSWORD is unavailable from Docker container metadata"
  fi

  MYSQL_PWD="$effective_password" docker exec -e MYSQL_PWD -i "$MYSQL_CONTAINER" \
    mysql \
      --default-character-set=utf8mb4 \
      -u"$DB_USERNAME" \
      --batch \
      --skip-column-names \
      "$DB_NAME" <<<"$query" 2>"$err_file" || {
        fail "MySQL query failed: $(cat "$err_file" 2>/dev/null)"
      }
}

read_current_counts() {
  mysql_query "
SELECT 'sys_user', COUNT(*) FROM sys_user WHERE deleted = 0
UNION ALL
SELECT 'sys_role', COUNT(*) FROM sys_role WHERE deleted = 0
UNION ALL
SELECT 'demo_total', COALESCE(SUM(cnt), 0)
FROM (
  SELECT COUNT(*) cnt FROM soc_asset
    WHERE source_type IN ('demo', 'mock', 'local-demo-client')
       OR (ip IN ('10.20.1.15','10.20.8.21','10.30.5.23','10.40.2.9')
           AND hostname IN ('prod-app-01','finance-db-01','office-win-23','mac-build-02'))
       OR (source_type IN ('macos-agent', 'windows-agent', 'host-agent')
           AND (hostname IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
                OR ip LIKE '192.0.2.%'
                OR ip LIKE '198.18.%'
                OR ip LIKE '198.19.%'))
  UNION ALL
  SELECT COUNT(*) FROM soc_alert
    WHERE batch_id LIKE 'DEMO-%'
       OR batch_id LIKE 'HOST-AGENT-INCIDENT-SMOKE-%'
       OR demo_case_id IS NOT NULL
       OR source_type IN ('demo', 'mock', 'local-demo-client')
       OR alert_uid IN ('MOCK-20260527-0001','MOCK-20260527-0002','MOCK-20260526-0003','MOCK-20260525-0004',
                        'MOCK-20260527-0005','SURICATA-20260527-0001','SURICATA-20260527-0002')
       OR raw_ref LIKE '%DEMO-%'
       OR raw_ref LIKE '%HOST-AGENT-INCIDENT-SMOKE-%'
       OR (source_type IN ('macos-agent', 'windows-agent', 'host-agent')
           AND (asset_name IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
                OR asset_ip LIKE '192.0.2.%'
                OR asset_ip LIKE '198.18.%'
                OR asset_ip LIKE '198.19.%'
                OR batch_id LIKE 'HOST-%fixture-agent-%'
                OR alert_uid LIKE '%FIXTURE%'
                OR raw_ref LIKE '%fixture%'
                OR rule_description LIKE '%fixture%'
                OR rule_description LIKE '%Fixture%'
                OR evidence_summary LIKE '%fixture%'))
  UNION ALL
  SELECT COUNT(*) FROM soc_external_event
    WHERE batch_id LIKE 'DEMO-%'
       OR batch_id LIKE 'HOST-AGENT-INCIDENT-SMOKE-%'
       OR demo_case_id IS NOT NULL
       OR source_type IN ('demo', 'mock', 'local-demo-client')
       OR event_uid IN ('EXT-SURICATA-20260527-0001','EXT-SURICATA-20260527-0002')
       OR event_uid LIKE '%HOST-AGENT-INCIDENT-SMOKE-%'
       OR (source_type IN ('macos-agent', 'windows-agent', 'host-agent')
           AND (asset_name IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
                OR asset_ip LIKE '192.0.2.%'
                OR asset_ip LIKE '198.18.%'
                OR asset_ip LIKE '198.19.%'
                OR batch_id LIKE 'HOST-%fixture-agent-%'
                OR event_uid LIKE '%FIXTURE%'
                OR CAST(raw_event AS CHAR) LIKE '%\"fixture\": true%'
                OR CAST(normalized_event AS CHAR) LIKE '%\"fixture\": true%'))
  UNION ALL
  SELECT COUNT(*) FROM soc_ticket
    WHERE ticket_no IN ('INC-202605260001','INC-202605250001')
       OR title LIKE '%DEMO-%'
       OR title LIKE '%演示数据%'
       OR title LIKE '%mac-dev-host%'
       OR title LIKE '%win-docker-host%'
       OR title LIKE '%mac-incident-host%'
       OR title LIKE '%win-incident-host%'
       OR title LIKE '%192.0.2.%'
       OR title LIKE '%198.18.%'
       OR title LIKE '%198.19.%'
  UNION ALL
  SELECT COUNT(*) FROM soc_report
    WHERE report_no = 'RPT-DAILY-202605270001'
       OR report_no LIKE 'RPT-VALIDATION-%'
       OR title LIKE '%DEMO-%'
       OR summary LIKE '%DEMO-%'
  UNION ALL
  SELECT COUNT(*) FROM soc_notification_log
    WHERE event_type = 'demo_range_batch_imported'
       OR title LIKE '%DEMO-%'
       OR content LIKE '%DEMO-%'
       OR content LIKE '%演示数据%'
       OR (title = '高危告警已转工单'
           AND content = '关键系统配置文件发生变更，已进入工单处置流程。'
           AND target = 'soc-team@example.local')
  UNION ALL
  SELECT COUNT(*) FROM soc_alert_whitelist
    WHERE (asset_ip IN ('10.20.1.15','10.20.8.21','10.30.5.23','10.40.2.9')
           AND (reason LIKE '%演示%' OR rule_id IN ('530','5502','WAF-DEMO-1001','WAF-DEMO-2001')))
  UNION ALL
  SELECT COUNT(*) FROM soc_vulnerability
    WHERE source_type IN ('demo', 'mock')
       OR cve_id LIKE 'CVE-2026-DEMO-RANGE-%'
       OR (cve_id IN ('CVE-2024-3094','CVE-2023-38408','CVE-2024-6387','CVE-2022-22965')
           AND asset_ip IN ('10.20.1.15','10.20.8.21','10.30.5.23','10.40.2.9'))
  UNION ALL
  SELECT COUNT(*) FROM soc_baseline_check
    WHERE source_type IN ('demo', 'mock')
       OR (check_code IN ('SSH_ROOT_LOGIN','PASSWORD_MAX_DAYS','FIREWALL_DEFAULT_DENY','SENSITIVE_FILE_PERMISSION','UNNEEDED_SERVICE')
           AND asset_ip IN ('10.20.1.15','10.20.8.21','10.30.5.23','10.40.2.9'))
       OR (source_type IN ('macos-agent', 'windows-agent', 'host-agent')
           AND (asset_name IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
                OR asset_ip LIKE '192.0.2.%'
                OR asset_ip LIKE '198.18.%'
                OR asset_ip LIKE '198.19.%'
                OR check_code LIKE '%fixture%'
                OR check_code LIKE '%FIXTURE%'
                OR check_item LIKE '%Fixture%'
                OR check_item LIKE '%fixture%'))
  UNION ALL
  SELECT COUNT(*) FROM soc_file_integrity_event
    WHERE source_type IN ('demo', 'mock')
       OR event_uid IN ('FIM-20260527-0001','FIM-20260527-0002','FIM-20260526-0003','FIM-20260525-0004')
       OR (source_type IN ('macos-agent', 'windows-agent', 'host-agent')
           AND (hostname IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
                OR asset_ip LIKE '192.0.2.%'
                OR asset_ip LIKE '198.18.%'
                OR asset_ip LIKE '198.19.%'
                OR event_uid LIKE '%FIXTURE%'
                OR rule_name LIKE '%Fixture%'
                OR rule_name LIKE '%fixture%'))
  UNION ALL
  SELECT COUNT(*) FROM soc_incident_cluster
    WHERE batch_id LIKE 'DEMO-%'
       OR batch_id LIKE 'HOST-AGENT-INCIDENT-SMOKE-%'
       OR demo_case_id IS NOT NULL
       OR correlation_key LIKE '%DEMO-%'
       OR correlation_key LIKE '%HOST-AGENT-INCIDENT-SMOKE-%'
       OR title LIKE '%HOST-AGENT-INCIDENT-SMOKE-%'
       OR hostname IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
       OR primary_hostname IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
       OR asset_ip LIKE '192.0.2.%'
       OR primary_asset_ip LIKE '192.0.2.%'
       OR asset_ip LIKE '198.18.%'
       OR primary_asset_ip LIKE '198.18.%'
       OR asset_ip LIKE '198.19.%'
       OR primary_asset_ip LIKE '198.19.%'
       OR title LIKE '%fixture%'
       OR title LIKE '%Fixture%'
  UNION ALL
  SELECT COUNT(*) FROM soc_incident_evidence
    WHERE batch_id LIKE 'DEMO-%'
       OR batch_id LIKE 'HOST-AGENT-INCIDENT-SMOKE-%'
       OR demo_case_id IS NOT NULL
       OR evidence_uid LIKE '%DEMO-%'
       OR evidence_uid LIKE '%HOST-AGENT-INCIDENT-SMOKE-%'
       OR evidence_uid LIKE '%FIXTURE%'
       OR asset_ip LIKE '192.0.2.%'
       OR asset_ip LIKE '198.18.%'
       OR asset_ip LIKE '198.19.%'
       OR hostname IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
  UNION ALL
  SELECT COUNT(*) FROM soc_algorithm_evaluation
    WHERE batch_id LIKE 'DEMO-%'
  UNION ALL
  SELECT COUNT(*) FROM soc_host_agent
    WHERE agent_id IN ('incident-macos-agent', 'incident-windows-agent')
       OR agent_id LIKE '%fixture-agent%'
       OR agent_id LIKE 'queue-replay-macos-agent-%'
       OR agent_id LIKE 'queue-pressure-macos-agent-%'
       OR hostname IN ('mac-dev-host', 'win-docker-host', 'mac-incident-host', 'win-incident-host')
       OR CAST(ip_addresses_json AS CHAR) LIKE '%192.0.2.%'
       OR CAST(ip_addresses_json AS CHAR) LIKE '%198.18.%'
       OR CAST(ip_addresses_json AS CHAR) LIKE '%198.19.%'
       OR CAST(labels_json AS CHAR) LIKE '%\"fixture\": \"true\"%'
       OR CAST(labels_json AS CHAR) LIKE '%\"fixture\":\"true\"%'
       OR CAST(labels_json AS CHAR) LIKE '%incident-chain%'
       OR CAST(labels_json AS CHAR) LIKE '%queue-replay%'
       OR CAST(labels_json AS CHAR) LIKE '%queue-pressure%'
  UNION ALL
  SELECT COUNT(*) FROM soc_ingest_batch
    WHERE batch_id LIKE 'HOST-AGENT-INCIDENT-SMOKE-%'
       OR agent_id IN ('incident-macos-agent', 'incident-windows-agent')
       OR agent_id LIKE '%fixture-agent%'
       OR agent_id LIKE 'queue-replay-macos-agent-%'
       OR agent_id LIKE 'queue-pressure-macos-agent-%'
       OR batch_id LIKE 'HOST-%fixture-agent-%'
  UNION ALL
  SELECT COUNT(*) FROM soc_ingest_reject_log
    WHERE batch_id LIKE 'HOST-AGENT-INCIDENT-SMOKE-%'
       OR agent_id IN ('incident-macos-agent', 'incident-windows-agent')
       OR agent_id LIKE '%fixture-agent%'
       OR agent_id LIKE 'queue-replay-macos-agent-%'
       OR agent_id LIKE 'queue-pressure-macos-agent-%'
       OR batch_id LIKE 'HOST-%fixture-agent-%'
) AS demo_counts;
"
}

check_counts() {
  local output sys_users sys_roles demo_total
  output="$(read_current_counts)"
  printf '%s\n' "$output" | sed 's/^/[INFO] mysql /'
  sys_users="$(awk -F'\t' '$1=="sys_user" {print $2}' <<<"$output")"
  sys_roles="$(awk -F'\t' '$1=="sys_role" {print $2}' <<<"$output")"
  demo_total="$(awk -F'\t' '$1=="demo_total" {print $2}' <<<"$output")"

  [[ "${sys_users:-0}" -gt 0 ]] || fail "sys_user has no active rows"
  [[ "${sys_roles:-0}" -gt 0 ]] || fail "sys_role has no active rows"
  pass "user and role rows are present"

  case "$EXPECTATION" in
    empty)
      [[ "${demo_total:-0}" -eq 0 ]] || fail "expected no demo data, found ${demo_total} rows"
      pass "current database has no demo data"
      ;;
    present)
      [[ "${demo_total:-0}" -gt 0 ]] || fail "expected demo data to be present"
      pass "current database has demo data (${demo_total} rows)"
      ;;
    report)
      if [[ "${demo_total:-0}" -gt 0 ]]; then
        info "current database has demo data (${demo_total} rows)"
      else
        info "current database has no demo data"
      fi
      ;;
  esac
}

check_startup_seed
check_counts

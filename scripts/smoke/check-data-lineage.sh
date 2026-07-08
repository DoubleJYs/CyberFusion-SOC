#!/usr/bin/env bash
set -euo pipefail

MYSQL_CONTAINER="${CYBERFUSION_MYSQL_CONTAINER:-cyberfusion-platform-mysql-1}"
DB_NAME="${CYBERFUSION_DB_NAME:-cyberfusion_soc}"

usage() {
  cat <<USAGE
Usage: scripts/smoke/check-data-lineage.sh [--mysql-container NAME] [--db NAME]

Read-only verification for SOC business data lineage:
- visible incidents must have non-fake source types and traceable evidence rows.
- evidence rows must point to an existing alert, external event, or vulnerability.
- tickets and tasks must connect back to a real alert.
- alerts and vulnerabilities must connect to an asset.
- macOS/Windows agent-sourced data must match an existing host agent OS family.

Environment:
  CYBERFUSION_MYSQL_CONTAINER  Default: cyberfusion-platform-mysql-1
  CYBERFUSION_DB_NAME          Default: cyberfusion_soc
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mysql-container)
      MYSQL_CONTAINER="$2"
      shift 2
      ;;
    --db)
      DB_NAME="$2"
      shift 2
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

pass() {
  printf '[PASS] %s\n' "$1"
}

fail() {
  printf '[FAIL] %s\n' "$1" >&2
  exit 1
}

mysql_query() {
  local query="$1"
  local err_file="${TMPDIR:-/tmp}/cyberfusion-data-lineage-mysql.err"
  if ! docker ps --format '{{.Names}}' 2>"$err_file" | grep -qx "$MYSQL_CONTAINER"; then
    fail "MySQL container is not reachable: ${MYSQL_CONTAINER}. $(cat "$err_file" 2>/dev/null)"
  fi
  docker exec -i "$MYSQL_CONTAINER" sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot "$1" --batch --skip-column-names' \
    sh "$DB_NAME" <<<"$query" 2>"$err_file" || {
      fail "MySQL query failed: $(cat "$err_file" 2>/dev/null)"
    }
}

read_lineage_counts() {
  mysql_query "
SELECT 'fake_source_business_rows', COALESCE(SUM(cnt), 0)
FROM (
  SELECT COUNT(*) cnt FROM soc_asset WHERE deleted = 0 AND (source_type IS NULL OR source_type IN ('demo','mock','local-demo-client','fixture'))
  UNION ALL
  SELECT COUNT(*) FROM soc_alert WHERE deleted = 0 AND (source_type IS NULL OR source_type IN ('demo','mock','local-demo-client','fixture'))
  UNION ALL
  SELECT COUNT(*) FROM soc_external_event WHERE deleted = 0 AND (source_type IS NULL OR source_type IN ('demo','mock','local-demo-client','fixture'))
  UNION ALL
  SELECT COUNT(*) FROM soc_vulnerability WHERE deleted = 0 AND (source_type IS NULL OR source_type IN ('demo','mock','local-demo-client','fixture'))
  UNION ALL
  SELECT COUNT(*) FROM soc_baseline_check WHERE deleted = 0 AND (source_type IS NULL OR source_type IN ('demo','mock','local-demo-client','fixture'))
  UNION ALL
  SELECT COUNT(*) FROM soc_file_integrity_event WHERE deleted = 0 AND (source_type IS NULL OR source_type IN ('demo','mock','local-demo-client','fixture'))
) fake_sources
UNION ALL
SELECT 'incident_clusters_without_traceable_evidence', COUNT(*)
FROM soc_incident_cluster c
LEFT JOIN (
  SELECT cluster_id, COUNT(*) cnt
  FROM soc_incident_evidence
  WHERE deleted = 0
    AND evidence_id IS NOT NULL
    AND evidence_type IN ('alert','external_event','vulnerability')
    AND source_type NOT IN ('demo','mock','local-demo-client','fixture')
  GROUP BY cluster_id
) e ON e.cluster_id = c.id
WHERE c.deleted = 0
  AND (
    COALESCE(c.evidence_count, 0) = 0
    OR c.source_types IS NULL
    OR c.source_types = ''
    OR c.source_types LIKE '%mock%'
    OR c.source_types LIKE '%local-demo-client%'
    OR c.source_types LIKE '%fixture%'
    OR c.source_types LIKE '%demo%'
    OR COALESCE(e.cnt, 0) = 0
  )
UNION ALL
SELECT 'incident_evidence_without_source_record', COUNT(*)
FROM soc_incident_evidence e
LEFT JOIN soc_incident_cluster c ON c.id = e.cluster_id AND c.deleted = 0
LEFT JOIN soc_alert a ON e.evidence_type = 'alert' AND a.id = e.evidence_id AND a.deleted = 0
LEFT JOIN soc_external_event x ON e.evidence_type = 'external_event' AND x.id = e.evidence_id AND x.deleted = 0
LEFT JOIN soc_vulnerability v ON e.evidence_type = 'vulnerability' AND v.id = e.evidence_id AND v.deleted = 0
WHERE e.deleted = 0
  AND (
    e.cluster_id IS NULL
    OR c.id IS NULL
    OR e.evidence_id IS NULL
    OR e.evidence_type NOT IN ('alert','external_event','vulnerability')
    OR e.source_type IS NULL
    OR e.source_type IN ('demo','mock','local-demo-client','fixture')
    OR (e.evidence_type = 'alert' AND a.id IS NULL)
    OR (e.evidence_type = 'external_event' AND x.id IS NULL)
    OR (e.evidence_type = 'vulnerability' AND v.id IS NULL)
  )
UNION ALL
SELECT 'alerts_without_asset_or_source', COUNT(*)
FROM soc_alert a
LEFT JOIN soc_asset s ON s.deleted = 0 AND (s.ip = a.asset_ip OR s.hostname = a.asset_name)
WHERE a.deleted = 0
  AND (
    a.source_type IS NULL
    OR a.source_type IN ('demo','mock','local-demo-client','fixture')
    OR a.asset_ip IS NULL
    OR a.asset_ip = ''
    OR s.id IS NULL
  )
UNION ALL
SELECT 'vulnerabilities_without_asset_or_source', COUNT(*)
FROM soc_vulnerability v
LEFT JOIN soc_asset s ON s.deleted = 0 AND (s.ip = v.asset_ip OR s.hostname = v.asset_name)
WHERE v.deleted = 0
  AND (
    v.source_type IS NULL
    OR v.source_type IN ('demo','mock','local-demo-client','fixture')
    OR v.asset_ip IS NULL
    OR v.asset_ip = ''
    OR s.id IS NULL
  )
UNION ALL
SELECT 'external_events_without_asset_or_source', COUNT(*)
FROM soc_external_event e
LEFT JOIN soc_asset s ON s.deleted = 0 AND (s.ip = e.asset_ip OR s.hostname = e.asset_name OR s.ip = e.dest_ip)
WHERE e.deleted = 0
  AND (
    e.source_type IS NULL
    OR e.source_type IN ('demo','mock','local-demo-client','fixture')
    OR COALESCE(e.asset_ip, e.dest_ip, '') = ''
    OR s.id IS NULL
  )
UNION ALL
SELECT 'baselines_without_asset_or_source', COUNT(*)
FROM soc_baseline_check b
LEFT JOIN soc_asset s ON s.deleted = 0 AND (s.ip = b.asset_ip OR s.hostname = b.asset_name)
WHERE b.deleted = 0
  AND (
    b.source_type IS NULL
    OR b.source_type IN ('demo','mock','local-demo-client','fixture')
    OR b.asset_ip IS NULL
    OR b.asset_ip = ''
    OR s.id IS NULL
  )
UNION ALL
SELECT 'fim_without_asset_or_source', COUNT(*)
FROM soc_file_integrity_event f
LEFT JOIN soc_asset s ON s.deleted = 0 AND (s.ip = f.asset_ip OR s.hostname = f.hostname)
WHERE f.deleted = 0
  AND (
    f.source_type IS NULL
    OR f.source_type IN ('demo','mock','local-demo-client','fixture')
    OR f.asset_ip IS NULL
    OR f.asset_ip = ''
    OR s.id IS NULL
  )
UNION ALL
SELECT 'tickets_without_real_alert', COUNT(*)
FROM soc_ticket t
LEFT JOIN soc_alert a ON a.id = t.alert_id AND a.deleted = 0 AND a.source_type NOT IN ('demo','mock','local-demo-client','fixture')
WHERE t.deleted = 0 AND (t.alert_id IS NULL OR a.id IS NULL)
UNION ALL
SELECT 'ticket_tasks_without_ticket_or_real_alert', COUNT(*)
FROM soc_ticket_task task
LEFT JOIN soc_ticket t ON t.id = task.ticket_id AND t.deleted = 0
LEFT JOIN soc_alert a ON a.id = COALESCE(task.alert_id, t.alert_id) AND a.deleted = 0 AND a.source_type NOT IN ('demo','mock','local-demo-client','fixture')
WHERE task.deleted = 0
  AND (
    task.ticket_id IS NULL
    OR t.id IS NULL
    OR COALESCE(task.alert_id, t.alert_id) IS NULL
    OR a.id IS NULL
  )
UNION ALL
SELECT 'reports_without_real_context', COUNT(*)
FROM soc_report r
WHERE r.deleted = 0
  AND NOT (
    EXISTS (
      SELECT 1 FROM soc_alert a
      WHERE a.deleted = 0
        AND a.source_type IS NOT NULL
        AND a.source_type NOT IN ('demo','mock','local-demo-client','fixture')
        AND DATE(a.event_time) BETWEEN r.period_start AND r.period_end
    )
    OR EXISTS (
      SELECT 1 FROM soc_external_event e
      WHERE e.deleted = 0
        AND e.source_type IS NOT NULL
        AND e.source_type NOT IN ('demo','mock','local-demo-client','fixture')
        AND DATE(e.event_time) BETWEEN r.period_start AND r.period_end
    )
    OR EXISTS (
      SELECT 1 FROM soc_ticket t
      JOIN soc_alert a ON a.id = t.alert_id AND a.deleted = 0 AND a.source_type NOT IN ('demo','mock','local-demo-client','fixture')
      WHERE t.deleted = 0
        AND DATE(t.created_at) BETWEEN r.period_start AND r.period_end
    )
  )
UNION ALL
SELECT 'macos_source_without_macos_agent', COALESCE(SUM(cnt), 0)
FROM (
  SELECT COUNT(*) cnt FROM soc_asset WHERE deleted = 0 AND source_type = 'macos-agent' AND NOT EXISTS (SELECT 1 FROM soc_host_agent WHERE deleted = 0 AND os_type = 'macos')
  UNION ALL
  SELECT COUNT(*) FROM soc_alert WHERE deleted = 0 AND source_type = 'macos-agent' AND NOT EXISTS (SELECT 1 FROM soc_host_agent WHERE deleted = 0 AND os_type = 'macos')
  UNION ALL
  SELECT COUNT(*) FROM soc_external_event WHERE deleted = 0 AND source_type = 'macos-agent' AND NOT EXISTS (SELECT 1 FROM soc_host_agent WHERE deleted = 0 AND os_type = 'macos')
  UNION ALL
  SELECT COUNT(*) FROM soc_baseline_check WHERE deleted = 0 AND source_type = 'macos-agent' AND NOT EXISTS (SELECT 1 FROM soc_host_agent WHERE deleted = 0 AND os_type = 'macos')
  UNION ALL
  SELECT COUNT(*) FROM soc_file_integrity_event WHERE deleted = 0 AND source_type = 'macos-agent' AND NOT EXISTS (SELECT 1 FROM soc_host_agent WHERE deleted = 0 AND os_type = 'macos')
) macos_missing_agent
UNION ALL
SELECT 'windows_source_without_windows_agent', COALESCE(SUM(cnt), 0)
FROM (
  SELECT COUNT(*) cnt FROM soc_asset WHERE deleted = 0 AND source_type = 'windows-agent' AND NOT EXISTS (SELECT 1 FROM soc_host_agent WHERE deleted = 0 AND os_type = 'windows')
  UNION ALL
  SELECT COUNT(*) FROM soc_alert WHERE deleted = 0 AND source_type = 'windows-agent' AND NOT EXISTS (SELECT 1 FROM soc_host_agent WHERE deleted = 0 AND os_type = 'windows')
  UNION ALL
  SELECT COUNT(*) FROM soc_external_event WHERE deleted = 0 AND source_type = 'windows-agent' AND NOT EXISTS (SELECT 1 FROM soc_host_agent WHERE deleted = 0 AND os_type = 'windows')
  UNION ALL
  SELECT COUNT(*) FROM soc_baseline_check WHERE deleted = 0 AND source_type = 'windows-agent' AND NOT EXISTS (SELECT 1 FROM soc_host_agent WHERE deleted = 0 AND os_type = 'windows')
  UNION ALL
  SELECT COUNT(*) FROM soc_file_integrity_event WHERE deleted = 0 AND source_type = 'windows-agent' AND NOT EXISTS (SELECT 1 FROM soc_host_agent WHERE deleted = 0 AND os_type = 'windows')
) windows_missing_agent;
"
}

printf 'CyberFusion data lineage gate\n'
printf 'MySQL container: %s\n' "$MYSQL_CONTAINER"
printf 'Database: %s\n' "$DB_NAME"

counts="$(read_lineage_counts)"
printf '%s\n' "$counts" | while IFS=$'\t' read -r name count; do
  [[ -n "${name:-}" ]] || continue
  if [[ "${count:-0}" == "0" ]]; then
    pass "$name = 0"
  else
    fail "$name = $count"
  fi
done

printf '[SUMMARY] Data lineage gate passed\n'

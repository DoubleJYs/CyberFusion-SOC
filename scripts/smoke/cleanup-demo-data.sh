#!/usr/bin/env bash
set -euo pipefail

MYSQL_CONTAINER="${CYBERFUSION_MYSQL_CONTAINER:-cyberfusion-platform-mysql-1}"
CONFIRM=0
BATCH_IDS=("DEMO-RANGE-OFFLINE-V1" "DEMO-RANGE-ACCEPTANCE-SMOKE")

usage() {
  cat <<USAGE
Usage: scripts/smoke/cleanup-demo-data.sh [--batch-id ID ...] [--mysql-container NAME] [--confirm]

Dry-run is the default. The script only targets known demo/smoke records
identified by batch IDs, demo incident clusters, linked demo tickets, reports,
vulnerabilities, and dry-run notification logs. It never deletes Docker volumes
or non-demo data. Real deletion requires --confirm.

Environment:
  CYBERFUSION_MYSQL_CONTAINER  Default: cyberfusion-platform-mysql-1
  DB_PASSWORD                  Required for MySQL authentication; not printed

Examples:
  scripts/smoke/cleanup-demo-data.sh
  scripts/smoke/cleanup-demo-data.sh --batch-id DEMO-RANGE-ACCEPTANCE-SMOKE --confirm
USAGE
}

escape_sql() {
  printf "%s" "$1" | sed "s/'/''/g"
}

validate_batch_id() {
  local value="$1"
  if [[ ! "$value" =~ ^[A-Za-z0-9._:-]+$ ]]; then
    printf 'Invalid batch id: %s\n' "$value" >&2
    exit 2
  fi
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --batch-id)
      validate_batch_id "$2"
      if [[ "${#BATCH_IDS[@]}" -eq 2 && "${BATCH_IDS[0]}" == "DEMO-RANGE-OFFLINE-V1" && "${BATCH_IDS[1]}" == "DEMO-RANGE-ACCEPTANCE-SMOKE" ]]; then
        BATCH_IDS=()
      fi
      BATCH_IDS+=("$2")
      shift 2
      ;;
    --mysql-container)
      MYSQL_CONTAINER="$2"
      shift 2
      ;;
    --confirm)
      CONFIRM=1
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

if [[ -z "${DB_PASSWORD:-}" ]]; then
  printf 'ERROR: DB_PASSWORD is required for MySQL cleanup dry-run and confirm modes. It must come from your local environment.\n' >&2
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  printf 'ERROR: docker CLI is required for local demo cleanup.\n' >&2
  exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -qx "$MYSQL_CONTAINER"; then
  printf 'ERROR: MySQL container is not running or not reachable: %s\n' "$MYSQL_CONTAINER" >&2
  exit 1
fi

batch_values=""
for batch_id in "${BATCH_IDS[@]}"; do
  validate_batch_id "$batch_id"
  if [[ -n "$batch_values" ]]; then
    batch_values+=","
  fi
  batch_values+="('$(escape_sql "$batch_id")')"
done

sql_file="$(mktemp /tmp/cyberfusion-demo-cleanup.XXXXXX.sql)"
trap 'rm -f "$sql_file"' EXIT

cat >"$sql_file" <<SQL
START TRANSACTION;

CREATE TEMPORARY TABLE tmp_demo_batch (
  batch_id VARCHAR(128) PRIMARY KEY
);
INSERT INTO tmp_demo_batch (batch_id) VALUES ${batch_values};

CREATE TEMPORARY TABLE tmp_demo_external_event AS
SELECT e.id
FROM soc_external_event e
WHERE e.deleted = 0
  AND EXISTS (
    SELECT 1 FROM tmp_demo_batch b
    WHERE e.batch_id = b.batch_id
       OR e.event_uid LIKE CONCAT('%', b.batch_id, '%')
       OR CAST(e.raw_event AS CHAR) LIKE CONCAT('%', b.batch_id, '%')
       OR CAST(e.normalized_event AS CHAR) LIKE CONCAT('%', b.batch_id, '%')
  );

CREATE TEMPORARY TABLE tmp_demo_alert AS
SELECT a.id
FROM soc_alert a
WHERE a.deleted = 0
  AND EXISTS (
    SELECT 1 FROM tmp_demo_batch b
    WHERE a.batch_id = b.batch_id
       OR a.alert_uid LIKE CONCAT('%', b.batch_id, '%')
       OR a.raw_ref LIKE CONCAT('%', b.batch_id, '%')
       OR a.rule_id LIKE CONCAT('%', b.batch_id, '%')
       OR a.rule_description LIKE CONCAT('%', b.batch_id, '%')
  );

CREATE TEMPORARY TABLE tmp_demo_vulnerability AS
SELECT v.id
FROM soc_vulnerability v
WHERE v.deleted = 0
  AND (
    v.cve_id LIKE '%DEMO-RANGE%'
    OR EXISTS (
      SELECT 1 FROM tmp_demo_batch b
      WHERE v.asset_name LIKE CONCAT('%', b.batch_id, '%')
         OR v.fix_suggestion LIKE CONCAT('%', b.batch_id, '%')
    )
  );

CREATE TEMPORARY TABLE tmp_demo_cluster AS
SELECT c.id
FROM soc_incident_cluster c
WHERE c.deleted = 0
  AND (
    EXISTS (
      SELECT 1 FROM tmp_demo_batch b
      WHERE c.batch_id = b.batch_id
         OR c.cluster_no LIKE CONCAT('%', b.batch_id, '%')
         OR c.title LIKE CONCAT('%', b.batch_id, '%')
         OR c.summary LIKE CONCAT('%', b.batch_id, '%')
         OR c.correlation_key LIKE CONCAT('%', b.batch_id, '%')
    )
    OR EXISTS (
      SELECT 1 FROM soc_incident_evidence ie
      WHERE ie.deleted = 0
        AND ie.cluster_id = c.id
        AND (
          (ie.evidence_type = 'external_event' AND ie.evidence_id IN (SELECT id FROM tmp_demo_external_event))
          OR (ie.evidence_type = 'alert' AND ie.evidence_id IN (SELECT id FROM tmp_demo_alert))
          OR (ie.evidence_type = 'vulnerability' AND ie.evidence_id IN (SELECT id FROM tmp_demo_vulnerability))
        )
    )
  );

CREATE TEMPORARY TABLE tmp_demo_ticket AS
SELECT DISTINCT t.id
FROM soc_ticket t
WHERE t.deleted = 0
  AND (
    t.alert_id IN (SELECT id FROM tmp_demo_alert)
    OR t.id IN (SELECT ticket_id FROM soc_incident_cluster WHERE ticket_id IS NOT NULL AND id IN (SELECT id FROM tmp_demo_cluster))
    OR EXISTS (
      SELECT 1 FROM tmp_demo_batch b
      WHERE t.ticket_no LIKE CONCAT('%', b.batch_id, '%')
         OR t.title LIKE CONCAT('%', b.batch_id, '%')
         OR t.resolution LIKE CONCAT('%', b.batch_id, '%')
    )
  );

CREATE TEMPORARY TABLE tmp_demo_report AS
SELECT r.id
FROM soc_report r
WHERE r.deleted = 0
  AND (
    r.report_type = 'security_validation'
    AND EXISTS (
      SELECT 1 FROM tmp_demo_batch b
      WHERE r.report_no LIKE CONCAT('%', b.batch_id, '%')
         OR r.title LIKE CONCAT('%', b.batch_id, '%')
         OR r.summary LIKE CONCAT('%', b.batch_id, '%')
    )
  );

CREATE TEMPORARY TABLE tmp_demo_notification AS
SELECT n.id
FROM soc_notification_log n
WHERE n.deleted = 0
  AND (
    n.biz_id IN (SELECT id FROM tmp_demo_ticket)
    OR EXISTS (
      SELECT 1 FROM tmp_demo_batch b
      WHERE n.title LIKE CONCAT('%', b.batch_id, '%')
         OR n.content LIKE CONCAT('%', b.batch_id, '%')
    )
  );

SELECT 'batch_ids', GROUP_CONCAT(batch_id ORDER BY batch_id) FROM tmp_demo_batch;
SELECT 'soc_external_event', COUNT(*) FROM tmp_demo_external_event;
SELECT 'soc_alert', COUNT(*) FROM tmp_demo_alert;
SELECT 'soc_vulnerability', COUNT(*) FROM tmp_demo_vulnerability;
SELECT 'soc_incident_cluster', COUNT(*) FROM tmp_demo_cluster;
SELECT 'soc_incident_evidence', COUNT(*) FROM soc_incident_evidence
 WHERE deleted = 0
   AND (
    cluster_id IN (SELECT id FROM tmp_demo_cluster)
    OR (evidence_type = 'external_event' AND evidence_id IN (SELECT id FROM tmp_demo_external_event))
    OR (evidence_type = 'alert' AND evidence_id IN (SELECT id FROM tmp_demo_alert))
    OR (evidence_type = 'vulnerability' AND evidence_id IN (SELECT id FROM tmp_demo_vulnerability))
  );
SELECT 'soc_ticket', COUNT(*) FROM tmp_demo_ticket;
SELECT 'soc_ticket_timeline', COUNT(*) FROM soc_ticket_timeline WHERE deleted = 0 AND ticket_id IN (SELECT id FROM tmp_demo_ticket);
SELECT 'soc_ticket_task', COUNT(*) FROM soc_ticket_task WHERE deleted = 0 AND ticket_id IN (SELECT id FROM tmp_demo_ticket);
SELECT 'soc_playbook_match_log', COUNT(*) FROM soc_playbook_match_log
 WHERE deleted = 0 AND (alert_id IN (SELECT id FROM tmp_demo_alert) OR ticket_id IN (SELECT id FROM tmp_demo_ticket));
SELECT 'soc_report', COUNT(*) FROM tmp_demo_report;
SELECT 'soc_notification_log', COUNT(*) FROM tmp_demo_notification;
SQL

if [[ "$CONFIRM" == "1" ]]; then
  cat >>"$sql_file" <<'SQL'

DELETE FROM soc_playbook_match_log
 WHERE deleted = 0 AND (alert_id IN (SELECT id FROM tmp_demo_alert) OR ticket_id IN (SELECT id FROM tmp_demo_ticket));
DELETE FROM soc_ticket_task WHERE deleted = 0 AND ticket_id IN (SELECT id FROM tmp_demo_ticket);
DELETE FROM soc_ticket_timeline WHERE deleted = 0 AND ticket_id IN (SELECT id FROM tmp_demo_ticket);
DELETE FROM soc_incident_evidence
 WHERE deleted = 0
   AND (
    cluster_id IN (SELECT id FROM tmp_demo_cluster)
    OR (evidence_type = 'external_event' AND evidence_id IN (SELECT id FROM tmp_demo_external_event))
    OR (evidence_type = 'alert' AND evidence_id IN (SELECT id FROM tmp_demo_alert))
    OR (evidence_type = 'vulnerability' AND evidence_id IN (SELECT id FROM tmp_demo_vulnerability))
  );
DELETE FROM soc_notification_log WHERE id IN (SELECT id FROM tmp_demo_notification);
DELETE FROM soc_report WHERE id IN (SELECT id FROM tmp_demo_report);
DELETE FROM soc_ticket WHERE id IN (SELECT id FROM tmp_demo_ticket);
DELETE FROM soc_incident_cluster WHERE id IN (SELECT id FROM tmp_demo_cluster);
DELETE FROM soc_external_event WHERE id IN (SELECT id FROM tmp_demo_external_event);
DELETE FROM soc_alert WHERE id IN (SELECT id FROM tmp_demo_alert);
DELETE FROM soc_vulnerability WHERE id IN (SELECT id FROM tmp_demo_vulnerability);

COMMIT;
SQL
else
  cat >>"$sql_file" <<'SQL'

ROLLBACK;
SQL
fi

printf 'CyberFusion demo data cleanup (%s)\n' "$([[ "$CONFIRM" == "1" ]] && printf 'CONFIRM' || printf 'dry-run')"
printf 'MySQL container: %s\n' "$MYSQL_CONTAINER"
printf 'Batch IDs: %s\n\n' "${BATCH_IDS[*]}"

MYSQL_PWD="${DB_PASSWORD}" docker exec -e MYSQL_PWD -i "$MYSQL_CONTAINER" \
  mysql --default-character-set=utf8mb4 -uroot --batch --skip-column-names cyberfusion_soc <"$sql_file"

if [[ "$CONFIRM" == "1" ]]; then
  printf '\nConfirmed cleanup completed for demo/smoke scoped records only.\n'
else
  printf '\nDry-run only. Re-run with --confirm to delete the scoped demo/smoke records.\n'
fi

# Database Guide

## Scope

CyberFusion SOC uses MySQL 8 with `utf8mb4_0900_ai_ci`. The canonical schema is `sql/schema.sql`; seed and demo baseline data are in `sql/data.sql`.

Runtime database files must stay outside source, normally under:

```text
/Users/zhangjiyan/Environment/cyberfusion-platform/mysql
E:\CyberFusion\Environment\cyberfusion-platform\mysql
```

Do not place MySQL data directories, backup dumps, customer exports, or Docker volumes under `00-cyberfusion-platform`.

## Initialization

macOS / Linux:

```sh
export DB_PASSWORD_VALUE="replace-with-local-db-password"

mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u root -p"$DB_PASSWORD_VALUE" < sql/schema.sql
mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u root -p"$DB_PASSWORD_VALUE" cyberfusion_soc < sql/data.sql
```

Windows PowerShell:

```powershell
cd E:\CyberFusion\00-cyberfusion-platform

$env:DB_HOST = "127.0.0.1"
$env:DB_PORT = "3306"
$env:DB_NAME = "cyberfusion_soc"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "replace-with-local-db-password"

.\scripts\win\init-local-db.ps1
```

The Windows initializer uses local `mysql.exe`, not Docker, and also applies `scripts/sql/apply-latest-menu-and-policy-seed.sql`.

## Table Groups

| Group | Tables | Purpose |
| --- | --- | --- |
| Identity and RBAC | `sys_user`, `sys_role`, `sys_menu`, `sys_user_role`, `sys_role_menu`, `sys_role_dept` | Login, authorization, menu permissions, and data scope. |
| Organization | `sys_dept`, `sys_post` | Department and post metadata used by ownership, dispatch, and data scope. |
| Platform config | `sys_config`, `sys_dict_type`, `sys_dict_data`, `sys_notice` | Runtime platform configuration, dictionaries, and announcements. |
| Audit and workflow | `sys_login_log`, `sys_operation_log`, `sys_import_export_log`, `sys_biz_sequence`, `sys_biz_flow_log` | Login audit, operation audit, import/export audit, and business process tracing. |
| File metadata | `sys_file`, `sys_attachment` | File metadata only. Binary uploads belong under Environment. |
| SOC core | `soc_asset`, `soc_external_event`, `soc_alert`, `soc_ticket`, `soc_ticket_timeline`, `soc_report` | Main event, alert, ticket, timeline, and report chain. |
| SOC security checks | `soc_vulnerability`, `soc_baseline_check`, `soc_file_integrity_event`, `soc_fim_watch_path` | Vulnerability, baseline, FIM evidence, and host-bound FIM directory authorizations. |
| SOC integration settings | `soc_wazuh_config`, `soc_sync_task`, `soc_notification_channel`, `soc_notification_log`, `soc_alert_whitelist` | Connector state, dry-run notifications, and alert noise controls. |

No separate rule table is required for the current Detection Rule Center. `/soc/rules` is a read-only projection from a built-in safe rule catalog plus `soc_external_event` and `soc_alert` hits.

## SOC Data Flow

1. Demo Range or adapter payload enters `POST /api/soc/external-events/cyberfusion/import`.
2. Non-Trivy sources write normalized records to `soc_external_event`.
3. Trivy JSON writes dependency risk rows to `soc_vulnerability`.
4. When `linkAlerts=true`, eligible normalized events create or refresh `soc_alert`.
5. Analysts can convert an alert to `soc_ticket`; timeline entries are written to `soc_ticket_timeline`.
6. Report generation writes `soc_report`.
7. Notification dry-run writes `soc_notification_log` only. No external sender is enabled by default.

## Demo Range Batch Keys

The offline batch uses stable identifiers to avoid uncontrolled duplicate data:

| Field | Location | Use |
| --- | --- | --- |
| `batchId` | `raw_event`, `normalized_event`, alert `raw_ref` | Groups one validation batch. |
| `demoCaseId` | `raw_event`, `normalized_event`, alert enrichment | Links evidence to the demo case. |
| `event_uid` | `soc_external_event.event_uid` | Stable external-event upsert key. |
| `alert_uid` | `soc_alert.alert_uid` | Stable alert upsert key. |
| `cveId + softwareName` | `soc_vulnerability` | Stable vulnerability refresh key. |

## Verification Queries

```sql
SELECT COUNT(*) AS external_events FROM soc_external_event;
SELECT source_type, COUNT(*) FROM soc_external_event GROUP BY source_type ORDER BY source_type;
SELECT severity, COUNT(*) FROM soc_alert GROUP BY severity ORDER BY severity;
SELECT ticket_status, COUNT(*) FROM soc_ticket GROUP BY ticket_status ORDER BY ticket_status;
SELECT report_type, COUNT(*) FROM soc_report GROUP BY report_type ORDER BY report_type;
SELECT status, COUNT(*) FROM soc_notification_log GROUP BY status ORDER BY status;
```

Batch-focused checks:

```sql
SELECT event_uid, source_type, event_type, severity
FROM soc_external_event
WHERE raw_event LIKE '%DEMO-RANGE-OFFLINE-V1%'
ORDER BY event_time DESC
LIMIT 20;

SELECT alert_uid, title, severity, status, raw_ref
FROM soc_alert
WHERE raw_ref LIKE '%DEMO-RANGE-OFFLINE-V1%'
ORDER BY first_seen_at DESC
LIMIT 20;
```

## Backup And Restore

Use the repository scripts, but keep backup output under Environment.

macOS/Linux:

```sh
scripts/mac/backup-runtime.sh
scripts/mac/restore-runtime.sh
```

Windows no-Docker:

```powershell
$env:DB_PASSWORD = "replace-with-local-db-password"
.\scripts\win\backup-runtime.ps1
.\scripts\win\restore-runtime.ps1 -BackupDir "E:\CyberFusion\Environment\cyberfusion-platform\backups\runtime\YYYYMMDD-HHMMSS" -ConfirmRestore
```

Before handoff, verify that backups, dumps, generated reports, logs, and uploads are not inside source.

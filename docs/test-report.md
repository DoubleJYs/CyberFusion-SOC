# Validation Report

Date: 2026-06-19

Scope: `/Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/00-cyberfusion-platform`

## Current Windows No-Docker Status 2026-07-06

The current Windows delivery path is no-Docker and D-drive based. Use `docs/windows-no-docker.md`, `README.md`, and `scripts/win/*.ps1` as the current startup source of truth:

- Source path: `D:\CyberFusion\00-cyberfusion-platform`.
- Runtime path: `D:\CyberFusion\Environment\cyberfusion-platform`.
- MySQL and Redis must be local or reachable Windows services started before CyberFusion.
- `scripts/win/start-no-docker.ps1` is the primary Windows entrypoint. It prepares D drive runtime folders, runs pre-start checks, starts backend/frontend, and runs post-start verification.
- `scripts/win/run-dev.ps1`, `scripts/win/init-local-db.ps1`, `scripts/win/dev-doctor.ps1`, `scripts/win/backup-runtime.ps1`, and `scripts/win/restore-runtime.ps1` use local MySQL client tools, not Docker.
- Windows scripts keep Maven, pnpm, and npm caches under `D:\CyberFusion\Environment\cyberfusion-platform\caches` instead of the default user profile on C drive.
- Older Docker fallback notes below are historical validation records for the macOS/Linux Docker-backed local path and should not be used as Windows startup instructions.

## SOC Operations Metrics Center v1 2026-06-22

Scope: A5 read-only operations metrics over existing SOC records. This phase does not add detection, Agent behavior, black-box ML, public scanning, automatic remediation, or real external notifications.

Coverage added:

- `GET /api/soc/operations/overview` returns a metric catalog with `metricCode`, `metricName`, `value`, `trend`, `explanation`, and `drilldownTarget`.
- `GET /api/soc/operations/sla` returns ticket total, pending, overdue, close rate, MTTA, MTTR, playbook application count, and playbook task completion rate.
- `GET /api/soc/operations/risk-trend` returns 7 risk trend points plus 24h and 7d score deltas.
- `GET /api/soc/operations/recommendation-adoption` returns recommendation count, viewed count, adopted count, and adoption rate.
- `GET /api/soc/operations/client-tasks` returns employee task total/completed/overdue counts, completion rate, and Security Keeper checkup coverage.
- `/soc/dashboard` shows an `运营指标中心` panel with drilldown-ready operation cards, SLA/efficiency, risk trend, recommendation adoption, employee task completion, Top risk assets, Top incident clusters, and Top trend anomalies.
- `security_validation` report summary now includes risk change, ticket efficiency, recommendation adoption, employee task completion, and dry-run notification count.
- `scripts/smoke/run-acceptance.sh --dry-run` now validates the five operations endpoints and asserts risk, ticket, recommendation, client task, and trend metric coverage.

Validation matrix:

| Check | Expected |
| --- | --- |
| Backend compile/test | Operations controller/service compile and existing SOC tests still pass. |
| Frontend build | Dashboard operation metric panel builds with typed API payloads. |
| Smoke API | Admin can call `/soc/operations/*` and every metric has explanation plus drilldown target. |
| Permission | Employee receives `403` for `/soc/operations/overview`. |
| Report | Generated `security_validation` summary contains `运营指标`. |
| Safety | No new command execution, scanner, external sender, token, key, or customer data path. |

## Docker MySQL Client Fallback 2026-06-22

Scope: local runtime diagnostics and SQL refresh documentation for environments without a host `mysql` command.

Changes verified:

- `scripts/smoke/dev-doctor.sh` no longer requires a host MySQL client for schema, seed, menu, and permission diagnostics.
- The doctor uses host `mysql` when available and falls back to `docker exec cyberfusion-platform-mysql-1 mysql` when host `mysql` is missing.
- `scripts/win/dev-doctor.ps1` has the same local-client-first, Docker-client-fallback behavior.
- `scripts/win/run-dev.ps1` now imports `schema.sql` and `data.sql` through the Docker Compose MySQL client with `MYSQL_PWD`, instead of passing the password through a `-p...` command argument.
- `scripts/mac/backup-runtime.sh`, `scripts/mac/restore-runtime.sh`, `scripts/win/backup-runtime.ps1`, and `scripts/win/restore-runtime.ps1` now pass `MYSQL_PWD` from the explicit local `DB_PASSWORD` instead of relying on container `MYSQL_ROOT_PASSWORD`.
- `scripts/smoke/cleanup-demo-data.sh` now requires `DB_PASSWORD` for both dry-run and confirm modes, and passes it through `MYSQL_PWD` to the Docker MySQL client.
- SQL checks prefer `DB_PASSWORD` when it is present. If it is absent in local development, `scripts/smoke/dev-doctor.sh` can read the existing Docker MySQL container `MYSQL_ROOT_PASSWORD` into process memory, use the Docker MySQL client, and still avoid printing or writing the password.
- `README.md` and `docs/common-bugs.md` now show Docker client commands for `SELECT 1`, `schema.sql`, `data.sql`, and menu/policy seed refresh so local MySQL installation is optional.

Validation results:

| Check | Result | Notes |
| --- | --- | --- |
| `command -v mysql || true` | PASS | No host `mysql` command was found in the current environment; fallback path was required. |
| `bash -n scripts/mac/backup-runtime.sh scripts/mac/restore-runtime.sh scripts/smoke/cleanup-demo-data.sh scripts/smoke/dev-doctor.sh scripts/smoke/check-visibility.sh scripts/smoke/run-acceptance.sh scripts/smoke/check-release-safety.sh` | PASS | Shell runtime and smoke scripts parse successfully. |
| `scripts/smoke/cleanup-demo-data.sh --help` | PASS | Help text is available without `DB_PASSWORD`. |
| `scripts/smoke/cleanup-demo-data.sh` | BLOCKED/EXPECTED | Without `DB_PASSWORD`, cleanup dry-run exits before connecting to MySQL and explains the missing credential. |
| `scripts/smoke/dev-doctor.sh --base-url http://127.0.0.1:5174 --api-base-url http://127.0.0.1:18080/api` | PASS | 15 pass, 0 warn, 0 fail. With no shell `DB_PASSWORD`, the doctor used the Docker MySQL container fallback without printing the password. |
| `DB_PASSWORD='__invalid_for_fallback_probe__' scripts/smoke/dev-doctor.sh ...` | BLOCKED/EXPECTED | With a non-secret invalid password and no host `mysql`, the doctor used the Docker client and returned MySQL `ERROR 1045`, proving fallback reached the container client. |
| `DB_PASSWORD='__compose_config_only__' MYSQL_PWD='__fallback_env_probe__' docker compose exec -T -e MYSQL_PWD mysql sh -c 'test -n "$MYSQL_PWD" ...'` | PASS | Verified Docker Compose can pass `MYSQL_PWD` into the MySQL container without printing its value. |
| `scripts/smoke/check-release-safety.sh` | PASS | 10 pass, 0 fail. |
| `mvn -pl backend test` | PASS | 44 tests run, 0 failures, 0 errors. |
| `pnpm --dir frontend build` | PASS | Vue typecheck and Vite build passed; existing `@vueuse/core` Rollup annotation warnings remain. |
| `git diff --check` | PASS | No whitespace errors; existing CRLF notices for unrelated Windows files remain. |

Remaining note:

- Write operations such as cleanup and schema refresh still require an explicit operator-controlled `DB_PASSWORD`; the Docker container fallback is used by read-only diagnostics.

## Main MySQL Volume Reconciliation 2026-06-22

Scope: non-destructive reconciliation of the primary Docker MySQL volume `cyberfusion-platform-mysql-1` against the current source SQL and smoke scripts.

Final status: resolved by archiving the unreadable Environment MySQL directory and initializing a fresh Environment-managed primary MySQL directory. The old directory was preserved at `/Users/zhangjiyan/Environment/cyberfusion-platform/mysql-auth-blocked-20260622-154059`. No Docker volume was deleted, no old root password was reset, and no old database directory was cleared.

Current primary database facts:

- Main container: `cyberfusion-platform-mysql-1`.
- Published port: `127.0.0.1:3306->3306/tcp`.
- Container state: running.
- Docker health status: healthy.
- Intended database name from compose and scripts: `cyberfusion_soc`.
- SQL-level table checks pass for `soc_incident_cluster`, `soc_incident_evidence`, `soc_correlation_rule`, `soc_risk_scoring_policy`, `soc_asset_risk_snapshot`, and `soc_asset_risk_factor`.
- SQL-level menu checks pass for `/soc/incidents`, `/soc/policies`, `/soc/tickets`, `/soc/reports`, and `/client/workbench`.

Authentication result:

| Check | Result | Notes |
| --- | --- | --- |
| Old archived directory | BLOCKED/ARCHIVED | The previous data directory could not be authenticated with the available password and was preserved as `mysql-auth-blocked-20260622-154059`. |
| Fresh primary SQL query | PASS | `SELECT 1` against `cyberfusion_soc` returned `1` through the Docker MySQL client. The password was supplied only as a process environment variable and was not written to source. |
| Docker health signal | PASS/WEAK | The container is healthy, but the acceptance proof is the real SQL query plus live smoke, not health alone. |

Actions taken:

- No Docker volume was deleted.
- No old root password was reset.
- The unreadable old data directory was moved aside under Environment instead of being deleted.
- A fresh Environment-managed MySQL directory was initialized by Docker Compose.
- A pre-refresh logical backup of the fresh primary database was exported to `/Users/zhangjiyan/Environment/cyberfusion-platform/backups/main-before-schema-refresh-20260622-154223.sql.gz`.
- Current `sql/schema.sql` and `sql/data.sql` were applied successfully.
- `sql/data.sql` was updated to include the `/client/workbench` employee-side menu seed required by the reconciliation checklist.
- `scripts/smoke/dev-doctor.sh` now checks all five key menu paths from the reconciliation checklist.

Validation results for this reconciliation pass:

| Check | Result | Notes |
| --- | --- | --- |
| `docker ps` primary database check | PASS | `cyberfusion-platform-mysql-1` is running on `127.0.0.1:3306` and reports Docker health `healthy`. |
| SQL authentication | PASS | `SELECT 1` returned `1`. |
| Key table check | PASS | SQL count for the six required SOC tables returned `6`. |
| Key menu check | PASS | SQL count for `/soc/incidents`, `/soc/policies`, `/soc/tickets`, `/soc/reports`, and `/client/workbench` returned `5`. |
| Direct frontend listener | PASS | `lsof` showed Vite listening on `127.0.0.1:5174`. |
| Direct backend listener | PASS | Spring Boot started on `18080` against the fresh primary database. |
| `/api/health` | PASS | Returned `UP`; database, schema, seed, and Redis dependencies all reported `UP`. |
| `mvn -pl backend test` | PASS | 44 tests run, 0 failures, 0 errors. |
| `pnpm --dir frontend build` | PASS | Vue typecheck and Vite build passed; existing `@vueuse/core` Rollup annotation warnings remain. |
| `scripts/smoke/check-release-safety.sh` | PASS | 10 pass, 0 fail. |
| `bash -n scripts/smoke/dev-doctor.sh scripts/smoke/check-visibility.sh scripts/smoke/run-acceptance.sh scripts/smoke/check-release-safety.sh` | PASS | Shell smoke scripts parse successfully. |
| `scripts/smoke/dev-doctor.sh --base-url http://127.0.0.1:5174 --api-base-url http://127.0.0.1:18080/api` | PASS | 15 pass, 0 warn, 0 fail. |
| `scripts/smoke/check-visibility.sh --base-url http://127.0.0.1:5174 --api-base-url http://127.0.0.1:18080/api` | PASS | 44 pass, 0 fail. |
| `scripts/smoke/run-acceptance.sh --dry-run` | PASS | 58 pass, 0 fail. |

Remaining note:

- The archived old directory is a physical fallback only. Because it could not be SQL-authenticated, it was not logically dumped or migrated row-by-row.

## Runtime Solidification And Demo Data Governance 2026-06-22

Scope: local startup scripts, Vite proxy recurrence prevention, dev doctor live checks, demo data cleanup dry-run, and smoke acceptance closure.

Status note: this section records an earlier same-day runtime pass. The current primary-volume state is superseded by the "Main MySQL Volume Reconciliation 2026-06-22" section above: as of the latest check, `18080` is not listening and the primary MySQL volume cannot be SQL-authenticated from the current shell because `DB_PASSWORD` is missing and the container environment password returns `ERROR 1045`.

Changes verified:

- macOS and Windows frontend startup scripts default to frontend `5174`, backend `18080`, and `VITE_API_PROXY_TARGET=http://127.0.0.1:18080`.
- `scripts/mac/frontend-dev.sh` now starts Vite with a clean `vite --host 127.0.0.1 --port 5174` command shape.
- `scripts/smoke/dev-doctor.sh` checks frontend/backend listeners, frontend `/api/health` proxy, backend `/api/health`, required tables, seed rows, admin menus/permissions, and employee 403 boundaries.
- `scripts/smoke/cleanup-demo-data.sh` defaults to dry-run and reports only scoped demo/smoke rows. Real cleanup requires `--confirm`.
- `README.md` and `docs/common-bugs.md` document standard startup, proxy target recurrence prevention, demo data accumulation, and safe cleanup.

Live verification:

| Check | Result | Notes |
| --- | --- | --- |
| Backend startup script | PASS | `DB_PASSWORD` was read from the local Docker MySQL container environment and `scripts/mac/backend-dev.sh` started Spring Boot on `18080`. The password was not written to source. |
| Frontend startup script | PASS | `scripts/mac/frontend-dev.sh` started Vite on `5174` with `VITE_API_PROXY_TARGET=http://127.0.0.1:18080`. |
| `scripts/smoke/dev-doctor.sh --base-url http://127.0.0.1:5174 --api-base-url http://127.0.0.1:18080/api` | PASS | 15 pass, 0 warn, 0 fail. Confirmed ports, proxy, health, key tables, admin menus/permissions, and employee 403 boundaries. |
| `scripts/smoke/cleanup-demo-data.sh` | PASS | Dry-run only. Reported scoped demo rows: 17 external events, 17 alerts, 1 vulnerability, 5 incident clusters, 52 incident evidence rows, 4 tickets, 14 timelines, 3 ticket tasks, 5 playbook logs, 10 reports, 36 notification logs. No data was deleted. |
| `scripts/smoke/check-release-safety.sh` | PASS | 10 pass, 0 fail. |
| `mvn -pl backend test` | PASS | 43 tests run, 0 failures, 0 errors. |
| `pnpm --dir frontend build` | PASS | Vue typecheck and Vite build passed; existing `@vueuse/core` Rollup annotation warnings remain. |
| `scripts/smoke/check-visibility.sh --base-url http://127.0.0.1:5174 --api-base-url http://127.0.0.1:18080/api` | PASS | 44 pass, 0 fail. |
| `scripts/smoke/run-acceptance.sh --dry-run` | PASS | 55 pass, 0 fail. |

Demo cleanup safety notes:

- Default mode is dry-run and ends with `ROLLBACK`.
- `--confirm` is required before any delete statements are committed.
- Target rows are limited to known demo/smoke batch IDs, demo-range CVEs, linked demo incident clusters/evidence, linked demo tickets/tasks/timeline rows, security-validation reports, playbook match logs, and dry-run notification logs.
- The script never deletes Docker volumes and does not target non-demo business data.

## Asset Risk Scoring Enhancement v2.1 2026-06-22

Scope: explainable asset risk scoring enhancement using existing incident clusters, response playbook tasks, employee tasks, and Security Keeper checkups.

Changes verified:

- `soc_risk_scoring_policy` now includes numeric weights for open incident clusters, high-risk incident clusters, employee checkup warning status, and employee checkup critical status.
- `RiskScoringService` still uses the existing `soc_asset_risk_snapshot` and `soc_asset_risk_factor` tables, and now records related business type and related business id for generated factors.
- Asset risk recalculation reads existing SOC rows only: alerts, vulnerabilities, baselines, FIM, external events, incident clusters, tickets, playbook tasks, employee tasks, and Security Keeper checkups.
- `/client/workbench` prioritizes employee-friendly next actions from risk factors when a risk profile is available.
- `scripts/smoke/run-acceptance.sh --dry-run` now recalculates asset risk after the demo incident and playbook chain, then asserts incident-cluster and playbook/employee-task factors are present.

Validation:

| Check | Result | Notes |
| --- | --- | --- |
| `mvn -pl backend test` | PASS | 44 tests run, 0 failures, 0 errors. Added coverage for incident/checkup factors and related evidence ids. |
| `pnpm --dir frontend build` | PASS | Vue typecheck and Vite build passed; existing `@vueuse/core` Rollup annotation warnings remain. |
| `scripts/smoke/dev-doctor.sh --base-url http://127.0.0.1:5174 --api-base-url http://127.0.0.1:18080/api --mysql-container cyberfusion-live-smoke-mysql` | PASS | 15 pass, 0 warn, 0 fail. Backend ran from current source on `18080`; frontend ran on `5174`; health, required tables, menus, permissions, and employee 403 boundaries passed. |
| `scripts/smoke/check-visibility.sh --base-url http://127.0.0.1:5174 --api-base-url http://127.0.0.1:18080/api` | PASS | 44 pass, 0 fail. |
| `scripts/smoke/run-acceptance.sh --dry-run` | PASS | 58 pass, 0 fail. Added asset risk recalculation and assertions for incident-cluster and playbook/employee-task factors. |
| `scripts/smoke/check-release-safety.sh` | PASS | 10 pass, 0 fail. |

Live runtime note:

- The existing `cyberfusion-platform-mysql-1` data volume rejected both the compose environment password and the documented local container password during this pass.
- To avoid deleting or resetting that volume, validation used an isolated smoke MySQL container on local port `3307`, initialized from the current `sql/schema.sql` and `sql/data.sql`.
- The backend was started with `DB_PORT=3307` for this validation only. No Docker volume was deleted, and no production/customer data was accessed.

Safety notes:

- The enhancement does not add scanning, Agent behavior, shell execution, automatic remediation, or real notifications.
- Risk policies remain numeric weights plus descriptive text; unsafe script, shell, SQL, scanner, downloader, and expression wording is still rejected.
- Employee-facing text avoids raw SOC terms where possible and routes action to existing safe pages: repair suggestions, security logs, safe toolbox, and pending tasks.

## Remediation Recommendation Ranking v1 2026-06-22

Scope: A3 recommendation ranking that turns existing risk factors, incident clusters, alerts, vulnerabilities, tickets, response playbook tasks, employee tasks, and Security Keeper checkups into explainable next actions.

Coverage added:

- `GET /api/soc/recommendations/top` returns Dashboard Top 5 recommendations for analysts.
- `GET /api/soc/assets/{id}/recommendations` returns asset-scoped recommended handling order.
- `GET /api/client/security-keeper/next-actions?assetIp={ip}` returns employee-facing next actions in ordinary language.
- `POST /api/soc/recommendations/{key}/record` writes recommendation adoption/view records to `soc_client_recommendation_action`.
- `scripts/smoke/run-acceptance.sh --dry-run` now checks that recommendations include incident-cluster, vulnerability, ticket/task, and employee next-action coverage.

Ranking rules:

| Input | Effect |
| --- | --- |
| High or critical incident cluster | Promoted to highest priority. |
| High or critical vulnerability | Promoted after event clusters. |
| Overdue ticket | Promoted as operational blocker. |
| Incomplete response playbook task | Promoted as manual closure work. |
| Employee pending task | Promoted for employee Security Keeper next action. |
| Closed ticket, completed task, confirmed/submitted recommendation | Down-ranked instead of removed from history. |

Validation status:

| Check | Result | Notes |
| --- | --- | --- |
| `mvn -pl backend test` | PASS | 49 tests run, 0 failures, 0 errors after adding recommendation service and rate-limit coverage. |
| `pnpm --dir frontend build` | PASS | Vue typecheck and Vite build passed; existing `@vueuse/core` Rollup annotation warnings remain. |
| `scripts/smoke/dev-doctor.sh --base-url http://127.0.0.1:5174 --api-base-url http://127.0.0.1:18080/api` | PASS | 15 pass, 0 warn, 0 fail. Backend was restarted from current source on `18080`; Docker MySQL fallback verified key tables and seed rows. |
| `scripts/smoke/check-visibility.sh --base-url http://127.0.0.1:5174 --api-base-url http://127.0.0.1:18080/api` | PASS | 44 pass, 0 fail. Admin, analyst, and employee visibility/permission checks passed. |
| `scripts/smoke/run-acceptance.sh --dry-run` | PASS | 65 pass, 0 fail. Covered recommendation API, incident/vulnerability/ticket/task recommendation types, adoption record, asset recommendations, and employee ordinary-language next actions. |
| `scripts/smoke/check-release-safety.sh` | PASS | 10 pass, 0 fail. No shell/script execution boundary regressions, no real notification sender, no high-confidence secrets. |

Safety notes:

- A3 does not add Agent behavior, ML models, scanner execution, public target access, shell execution, automatic remediation, or real notification sending.
- Recommendations are generated from existing SOC records and are explainable through `reason` plus `recommendedAction`.
- Employee endpoint remains scoped to the authenticated employee's visible asset.

## Trend Anomaly Detection v1 2026-06-22

Scope: A4 explainable statistical trend detection over existing `soc_external_event` and `soc_alert` data. The feature is read-only and does not add black-box ML, Agent behavior, external collectors, scans, attacks, automatic remediation, or real notification sending.

Coverage added:

- `GET /api/soc/trends/anomalies/top?limit=5` returns Top trend anomalies for Dashboard.
- `GET /api/soc/trends/anomalies` returns filtered anomalies for asset and external-event contexts.
- `GET /api/soc/trends/aggregations` returns time-window counts grouped by asset, source type, event type, rule id, severity, and hour/day bucket.
- `/soc/dashboard` shows `趋势异常 Top 5`.
- `/soc/assets` asset detail shows `近期异常趋势`.
- `/soc/external-events` shows `趋势异常提示`.
- `security_validation` report metrics can read the trend anomaly summary.
- `scripts/smoke/run-acceptance.sh --dry-run` now checks trend anomalies, explainable reason/recommendation text, spike/cross-source detection, hourly aggregation, and employee 403 on SOC trend APIs.

Detection rules:

| Rule | Behavior |
| --- | --- |
| Volume spike | Compares the latest 24-hour window with the previous 7-day daily average. |
| Severity ratio rise | Flags assets where high/critical share rises materially from baseline. |
| Consecutive asset anomaly | Flags assets with signals across multiple hourly windows. |
| Cross-source rise | Flags the same asset when WAF/ZAP/Wazuh/Suricata/Zeek-style sources rise together. |

Runtime note:

- Initial live smoke returned `500` for `/api/soc/trends/anomalies/top` because the Java backend on `18080` was an older process started before the A4 controller/service were loaded.
- Stopping only the stale Java process and restarting `scripts/mac/backend-dev.sh` from the current checkout fixed the live endpoint without deleting database volumes or changing data.
- After restart, `/api/soc/trends/anomalies/top?limit=5` returned 4 rows and `/api/soc/trends/aggregations?assetIp=10.20.1.15&granularity=hour&limit=20` returned 9 rows.

Validation status:

| Check | Result | Notes |
| --- | --- | --- |
| `mvn -pl backend test` | PASS | 52 tests run, 0 failures, 0 errors. Added `TrendAnomalyServiceTest` coverage for volume spike, cross-source rise, and aggregation behavior. |
| `pnpm --dir frontend build` | PASS | Vue typecheck and Vite build passed; existing `@vueuse/core` Rollup annotation warnings remain. |
| `scripts/smoke/dev-doctor.sh --base-url http://127.0.0.1:5174 --api-base-url http://127.0.0.1:18080/api` | PASS | 15 pass, 0 warn, 0 fail. Backend process started from current source at `2026-06-22 21:50:33`; health, Docker MySQL key tables, menus, permissions, and employee 403 checks passed. |
| `scripts/smoke/check-visibility.sh --base-url http://127.0.0.1:5174 --api-base-url http://127.0.0.1:18080/api` | PASS | 44 pass, 0 fail. |
| `scripts/smoke/run-acceptance.sh --dry-run` | PASS | 70 pass, 0 fail. Covered trend anomaly spike/cross-source detection, explainable reason/recommendation, hourly aggregation, and employee trend API denial. |
| `scripts/smoke/check-release-safety.sh` | PASS | 10 pass, 0 fail. No shell/script execution boundary regressions, no real notification sender, no high-confidence secrets. |

Safety notes:

- A4 only reads existing SOC rows and returns calculated summaries.
- No new SQL table was required; it reuses `soc_external_event` and `soc_alert`.
- Employee users cannot access SOC trend anomaly APIs.
- The algorithm is explainable statistics: rolling 7-day average, ratio spike, severity ratio, consecutive hourly windows, and cross-source coverage.

## Runtime Regression Closure 2026-06-21

Scope: live startup regression, health diagnostics, local smoke verification, seed password stability, and startup issue prevention.

### Live Smoke Blocking Closure 2026-06-21 23:49

This pass was executed because the live runtime was initially not listening on `5174` and `18080`.

Blocking cause:

- `soc_incident_cluster`, `soc_correlation_rule`, and `soc_incident_evidence` existed in the local Docker MySQL database.
- The real blocker was that the frontend and backend processes were not running.

Recovery action:

- Started backend from the current source checkout on `127.0.0.1:18080`.
- Started frontend on `127.0.0.1:5174` with `VITE_API_PROXY_TARGET=http://127.0.0.1:18080`.
- Did not delete Docker volumes and did not reset database passwords.

Live verification:

| Check | Result | Notes |
| --- | --- | --- |
| Required incident/correlation tables | PASS | `soc_correlation_rule`, `soc_incident_cluster`, and `soc_incident_evidence` are present. |
| `GET /api/health` | PASS | Returned HTTP 200 with `database`, `schema`, `seed`, and `redis` all `UP`. |
| `scripts/smoke/dev-doctor.sh --base-url http://127.0.0.1:5174 --api-base-url http://127.0.0.1:18080/api` | PASS | 5 pass, 0 warn, 0 fail. |
| `scripts/smoke/check-visibility.sh --base-url http://127.0.0.1:5174 --api-base-url http://127.0.0.1:18080/api` | PASS | 44 pass, 0 fail. Admin sees `/soc/policies`, `/soc/incidents`, `/soc/tickets`, `/soc/reports`; employee receives 403 for policy, incident, and correlation-rule APIs. |
| `scripts/smoke/run-acceptance.sh --dry-run` | PASS | 55 pass, 0 fail. |
| `scripts/smoke/check-release-safety.sh` | PASS | 10 pass, 0 fail. |
| `mvn -pl backend test` | PASS | 43 tests run, 0 failures, 0 errors. |
| `pnpm --dir frontend build` | PASS | Vue typecheck and Vite build passed; existing `@vueuse/core` Rollup annotation warnings remain. |

Changes verified:

- `sql/data.sql` no longer overwrites existing `sys_user.password_hash` for existing `admin` and `demo` rows. New databases still receive the BCrypt seed hash.
- `GET /api/health` reports `database`, `schema`, `seed`, and `redis`, plus `version` and active Spring profile.
- `scripts/smoke/dev-doctor.sh` checks frontend/backend ports, backend Java process start time, `/api/health`, Docker MySQL required tables, and key menu/permission seed rows.
- `scripts/win/dev-doctor.ps1` provides the same read-only diagnostics for Windows environments.
- `docs/common-bugs.md` records recovery steps for backend not listening, stale backend processes, missing tables, admin password mismatch, and smoke sandbox false negatives.

Live validation results from 2026-06-21:

| Command | Result | Notes |
| --- | --- | --- |
| `scripts/smoke/dev-doctor.sh --base-url http://127.0.0.1:5174 --api-base-url http://127.0.0.1:18080/api` | PASS | 5 pass, 0 warn, 0 fail. Health reported database/schema/seed/redis `UP`, 13 required tables present, and key seed rows present. |
| `scripts/smoke/check-release-safety.sh` | PASS | 10 checks passed; no source `.env`, high-confidence secrets, runtime DB/log files, or unexpected large source artifacts. |
| `scripts/smoke/check-visibility.sh --base-url http://127.0.0.1:5174 --api-base-url http://127.0.0.1:18080/api` | PASS | 44 pass, 0 fail. Admin, analyst, and employee visibility/permission checks passed. |
| `scripts/smoke/run-acceptance.sh --dry-run` | PASS | 55 pass, 0 fail. Demo batch, structured evidence, incident cluster, ticket, report, policy, adapter, playbook, and dry-run notification chain passed. |
| `mvn -pl backend test` | PASS | 43 tests run, 0 failures, 0 errors. |

Runtime notes:

- Backend was restarted from the current checkout on `127.0.0.1:18080`.
- Frontend was restarted on `127.0.0.1:5174` with `VITE_API_PROXY_TARGET=http://127.0.0.1:18080`.
- The live smoke scripts call only local services and do not scan public targets, execute attack payloads, or send real notifications.

## Correlation Engine v1

Date: 2026-06-21

Scope: structured correlation fields, incident clusters, evidence relation reasons, correlation rule lifecycle, SOC expert UI entry, and acceptance smoke coverage.

New or updated checks:

- `CorrelationServiceTest` covers WAF/ZAP/Wazuh same-asset evidence clustering, different-asset separation, active-rule filtering for draft/disabled rules, `value_count`, ordered temporal matching, repeated correlate stability for the cluster key, relation reason writing, incident-to-ticket timeline creation, data-scope rejection, and unsafe rule JSON rejection.
- `mvn -pl backend test`: passed on 2026-06-21. 43 tests run, 0 failures, 0 errors.
- `pnpm --dir frontend build`: passed on 2026-06-21. Vue typecheck and Vite production build completed; Rollup reported existing third-party pure-annotation warnings from `@vueuse/core`.
- `scripts/smoke/run-acceptance.sh --dry-run` now includes incident correlation, incident detail evidence, alert related incidents, incident-to-ticket conversion, active correlation rules, rule validation, analyst incident access, and employee incident 403 checks.
- `scripts/smoke/check-visibility.sh` now checks `/soc/incidents`, `/soc/policies`, the `事件关联规则` tab/API, admin and analyst rule visibility, and employee 403 boundaries for incident and correlation-rule APIs.
- Playwright page smoke now includes `/soc/incidents`, Dashboard Top 5 clusters, alert-detail related clusters, and the policy center correlation-rule tab.

Coverage matrix:

| Area | Checks |
| --- | --- |
| Schema | `soc_external_event` and `soc_alert` include structured correlation fields. `soc_correlation_rule`, `soc_incident_cluster`, and `soc_incident_evidence` are defined in `sql/schema.sql`. |
| Seed | `sql/data.sql` adds idempotent column migration, five active default correlation rules including `value_count`, `/soc/incidents` menu, and RBAC permissions. |
| Backend APIs | `/api/soc/incidents`, `/api/soc/incidents/{id}`, `/api/soc/incidents/correlate`, `/api/soc/incidents/{id}/ticket`, `/api/soc/incidents/{id}/close`, `/api/soc/alerts/{id}/related-incidents`, `/api/soc/assets/{id}/incidents`, and `/api/soc/correlation-rules`. |
| Frontend | `/soc/incidents` lists clusters and evidence; `/soc/dashboard` shows Top 5 clusters; alert detail shows related clusters; event detail shows cluster lookup; `/soc/demo-range` and `/showcase` show the current validation chain; `/soc/policies` can create, edit, validate, publish, and disable `事件关联规则`. |
| Smoke | Acceptance smoke imports the demo batch, runs correlation, checks cluster counts, verifies relation reasons, converts a cluster to a ticket, checks dry-run notification boundaries, and checks employee denial. |
| Visibility | Visibility smoke checks route/menu/API visibility for incidents and correlation rules, plus employee 403 behavior. |
| Safety | Correlation only reads existing SOC data and writes cluster/evidence/timeline rows. It does not add scanning, payload execution, shell commands, OpenSearch, Python ML, external queries, automatic remediation, or real notification senders. |

Closure validation on 2026-06-21:

| Command | Result | Notes |
| --- | --- | --- |
| `bash -n scripts/smoke/run-acceptance.sh` | PASS | Shell syntax is valid after adding incident-to-ticket coverage. |
| `bash -n scripts/smoke/check-visibility.sh` | PASS | Shell syntax is valid after adding correlation-rule visibility checks. |
| `pnpm --dir frontend exec playwright test --list` | PASS | Playwright discovers 6 tests, including `release-pages.spec.ts`, `visibility.spec.ts`, and `real-backend.spec.ts`. |
| `mvn -pl backend test` | PASS | 43 tests run, 0 failures, 0 errors. |
| `pnpm --dir frontend build` | PASS | Vue typecheck and production build pass; only existing Rollup pure-annotation warnings from `@vueuse/core` appear. |
| `scripts/smoke/run-acceptance.sh --dry-run` | BLOCKED | Local backend was not listening on `localhost:18080`; login failed with curl status `000`. |
| `scripts/smoke/check-visibility.sh --base-url http://127.0.0.1:5174 --api-base-url http://127.0.0.1:18080/api` | BLOCKED | Local frontend `5174` and backend `18080` were not listening. Source checks for `/showcase`, `/soc/policies`, `/soc/incidents`, `/client/tasks`, `/client/local-range`, `事件关联规则`, and `/soc/correlation-rules` passed. |

## Bug Bash 2026-06-21

Scope: P0/P1 stability for build/test, route/menu/RBAC, core SOC demo chain, employee safety boundary, smoke scripts, and documentation consistency.

Findings and fixes:

| Severity | Finding | Fix / status |
| --- | --- | --- |
| P0 | Local database was missing `soc_incident_cluster` and `soc_correlation_rule`, causing incident, correlation-rule, and employee-linked APIs to return 500. | Applied `sql/schema.sql` and `sql/data.sql` idempotently to the local MySQL container. Verified both tables, `/soc/incidents` menu seed, and incident/correlation permissions were present. |
| P0 | Running Java backend process was stale: process age was older than current `backend/target/classes`, so latest controllers/services were not loaded. | Stopped the stale backend process. Restart is blocked until a safe Environment-managed `DB_PASSWORD` is provided or exported by the user. |
| P1 | `/api/health` returned a generic 500 for callers that used the base health path instead of `/health/liveness` or `/health/readiness`. | Added `GET /api/health` as a readiness-compatible endpoint. |
| P1 | Smoke and Playwright tests hard-coded only `Admin@123456`, while the local admin password may have been changed to `admin123`. | Added safe admin login fallback to `admin123` unless `CYBERFUSION_ADMIN_PASSWORD` is set. Analyst, auditor, and employee accounts still use the seeded demo password or explicit env override. |

Bug Bash validation:

| Command | Result | Notes |
| --- | --- | --- |
| `mvn -pl backend test` | PASS | 43 tests run, 0 failures, 0 errors. |
| `pnpm --dir frontend typecheck` | PASS | Vue TypeScript project check passed. |
| `pnpm --dir frontend build` | PASS | Build passed; only existing Rollup pure-annotation warnings from `@vueuse/core` appeared. |
| `bash -n scripts/smoke/run-acceptance.sh && bash -n scripts/smoke/check-visibility.sh` | PASS | Shell syntax passed after login fallback changes. |
| `scripts/smoke/check-release-safety.sh` | PASS | 10 checks passed: no source `.env`, no high-confidence secrets, no runtime DB/log files, local terminal uses argv `ProcessBuilder`, adapter/playbook validation is present, and notification remains dry-run by default. |
| `scripts/smoke/check-visibility.sh --base-url http://127.0.0.1:5174 --api-base-url http://127.0.0.1:18080/api` | PARTIAL/BLOCKED | Before DB repair it showed frontend routes reachable but incident/correlation/employee APIs returning 500. After DB repair, live re-run is blocked because the stale backend was stopped and restart requires a safe `DB_PASSWORD` environment. |
| `scripts/smoke/run-acceptance.sh --dry-run` | BLOCKED | Requires backend restart on `18080`. |

## Asset Risk Scoring Engine

Date: 2026-06-20

Scope: asset risk score persistence, explainable risk factors, scoring policy lifecycle, asset profile APIs, dashboard/showcase/employee profile integration, and non-scanning safety boundary.

New or updated checks:

- `RiskScoringServiceTest` covers explainable scoring from alerts, vulnerabilities, tickets, employee tasks, closed-loop reduction, custom numeric weights, score clipping, and unsafe text rejection.
- `mvn -pl backend test`: passed. 32 tests run, 0 failures, 0 errors.
- `pnpm --dir frontend build`: passed. Vue typecheck and Vite production build completed; Rollup reported existing third-party pure-annotation warnings from `@vueuse/core`.
- Runtime smoke scripts were not completed in this local check because `127.0.0.1:5174` and `127.0.0.1:18080` were not listening. Restart frontend and backend from the current build before treating API/page smoke as runtime proof.

Coverage matrix:

| Area | Checks |
| --- | --- |
| Schema | `soc_asset.risk_score`, `soc_risk_scoring_policy`, `soc_asset_risk_snapshot`, and `soc_asset_risk_factor` are defined in `sql/schema.sql`. |
| Seed | Default active numeric policy and risk policy permissions are defined in `sql/data.sql` and the idempotent SQL patch. |
| Backend APIs | `/api/soc/risk-scoring/policies`, publish/disable/validate, `/api/soc/risk-scoring/recalculate`, `/api/soc/risk-scoring/top-assets`, `/api/soc/assets/{id}/risk-profile`, `/api/soc/assets/{id}/risk-history`, and `/api/client/devices/{ip}/risk-profile`. |
| Frontend | `/soc/assets` displays risk score and factor drawer; `/soc/policies` has `风险评分策略`; `/soc/dashboard` and `/showcase` prefer unified top-risk assets; `/client/workbench` uses risk-profile explanations when available. |
| Safety | Scoring only reads existing SOC tables and writes snapshots/factors; it does not execute commands, run scanners, send notifications, or perform auto-remediation. |

## P4.5 Change Visibility Reconciliation

Date: 2026-06-20

Scope: route, menu, permission, seed, runtime, screenshot, and smoke-test visibility for recently delivered CyberFusion SOC capabilities.

New or updated checks:

- `docs/change-visibility-matrix.md` records route/component/API/menu/permission/seed/manual/screenshot/smoke visibility for `/showcase`, `/soc/policies`, local check policy, event adapter mapping, response playbooks, alert playbook suggestions, ticket tasks, employee pages, adapter preview, Demo Range import, `security_validation` reports, and notification dry-run logs.
- `scripts/smoke/check-visibility.sh` logs in with local demo roles, reads `/api/auth/me`, prints menu paths, checks key frontend routes, checks policy/adapter/playbook APIs, checks employee 403 behavior, and confirms `/client/tasks` is routable.
- `scripts/sql/apply-latest-menu-and-policy-seed.sql` is an idempotent patch for already-initialized databases whose `sys_menu` or `sys_role_menu` rows do not include the latest policy/demo/playbook permissions.
- `frontend/tests/e2e/visibility.spec.ts` checks `/showcase`, `/soc/policies` tabs, `/soc/alerts`, `/soc/tickets`, `/client/workbench`, `/client/tasks`, and `/client/local-range`.
- `docs/screenshots/manifest.json` now marks screenshots generated before this P4.5 pass as stale inventory until screenshots are regenerated.

Runtime reconciliation results from the local 2026-06-20 check:

| Check | Result | Notes |
| --- | --- | --- |
| Source route and menu visibility | PASS | `/auth/me` returns `/soc/policies`, `/soc/alerts`, `/soc/tickets`, and `/soc/reports`; frontend routes for `/showcase`, `/soc/policies`, `/client/tasks`, and `/client/local-range` are reachable. |
| Existing database refresh | PARTIAL | `sql/schema.sql` and `sql/data.sql` were applied idempotently to the local MySQL container, followed by the menu/role patch. No table or volume was deleted. |
| Current `18080` runtime | BLOCKED | The running Java process was started before the latest Maven build. After DB refresh it still returns 500 for policy, adapter, playbook, local command, and employee task APIs. Restart the backend from current `backend/target/classes` before treating screenshots or API smoke as final proof. |
| Demo user passwords | CHANGED BY SEED | Applying `sql/data.sql` restores seeded demo password hashes. Use the documented demo password env overrides for local smoke scripts if your runtime uses a different password. |

Visibility notes:

| Area | Result |
| --- | --- |
| Customer demo route | `/showcase` is a static authenticated route and appears as a visible shell/sidebar entry. |
| Policy center route | `/soc/policies` exists in frontend fallback routes and `sys_menu` seed. |
| Employee tasks route | `/client/tasks` was added as the primary employee “我的待办” route; `/client/operations` remains as a compatible legacy route. |
| Menu seed for existing DBs | `scripts/sql/apply-latest-menu-and-policy-seed.sql` can upsert current menu and role permissions without deleting data; run it after `sql/schema.sql` and `sql/data.sql` on already initialized local databases. |
| Runtime freshness | Admin and employee user menus show lightweight version/build markers from frontend env values. |
| Screenshot freshness | Existing screenshot inventory is marked stale until regenerated. |

## Commands

```sh
cd /Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/00-cyberfusion-platform/backend
mvn test

cd /Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/00-cyberfusion-platform/frontend
pnpm build

cd /Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/00-cyberfusion-platform/deploy
docker compose config

cd /Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/00-cyberfusion-platform
rg -n "BEGIN .*PRIVATE KEY|AKIA[0-9A-Z]{16}|ghp_[A-Za-z0-9_]+|sk-(proj|live|test)-[A-Za-z0-9_-]+|xoxb-[A-Za-z0-9-]+|真实客户数据|客户数据" . \
  --glob '!frontend/dist/**' --glob '!backend/target/**' --glob '!docs/test-report.md' --glob '!docs/final-report.md'
```

## Acceptance Workflow

The external event page supports these safe demo steps:

1. Import Zeek `conn.log` rows through `POST /api/soc/external-events/cyberfusion/import`.
2. Import Suricata `eve.json` JSON Lines through the same endpoint or the compatibility Suricata endpoint.
3. Import Wazuh demo alerts through the same endpoint.
4. Import MISP IOC JSON through the same endpoint.
5. Import offline WAF / gateway demo JSON Lines from `demo-data/waf-demo-events.jsonl` with `sourceType=waf`.
6. Generate unified alerts by setting `linkAlerts=true`.
7. Convert an alert to a ticket through `POST /api/soc/alerts/{id}/ticket`.
8. Analyze IOC/network fields through `POST /api/soc/external-events/cyberchef/analyze`.
9. Trigger a Shuffle dry-run notification through `POST /api/soc/external-events/shuffle/demo-notification`.
10. Generate the comprehensive security report through `POST /api/soc/reports/generate`.
11. From `/soc/demo-range`, run the one-click offline batch import through `POST /api/soc/demo-range/batches/import`; it imports WAF, ZAP, Trivy, Suricata, Zeek, and Wazuh-style FIM demo evidence and shows the returned batch summary.
12. Query `GET /api/soc/demo-range/batches/{batchId}/evidence-chain` to show the fixed 10-minute security validation path: event detail, alert detail, ticket timeline, security validation report, and notification dry-run log.
13. Open `GET /api/soc/alerts/{id}` for a Demo Range alert and verify `sourceType`, `eventType`, `ruleId`, `ruleName`, `assetIp`, `targetUrl`, `action`, `evidenceSummary`, `demoCaseId`, and `batchId`.
14. Convert a Demo Range alert to a ticket and verify the ticket timeline contains a `Demo Range 来源` entry with `batchId` and `demoCaseId`.
15. Generate a batch security validation report with `{"reportType":"security_validation","batchId":"<batchId>"}` and verify the report summary counts events, alerts, vulnerabilities, block evidence, tickets, and notification dry-run logs.
16. Open `/soc/rules`, verify Sigma/WAF/ZAP/Suricata/Wazuh rules are visible, open a rule hit preview, and jump to the matching event or alert page.
17. Query `GET /api/soc/rules/adapter-mappings` and verify WAF/ZAP/Trivy/Wazuh/Suricata/Zeek/Sigma mappings include source field, normalized field, severity mapping, dedup key, alert link rule, sample file, and failure case.

## Expected Safety Properties

- No upstream directory is deleted or rewritten.
- No runtime database, Docker volume, large log, cache, upload, certificate, private key, token, or real customer data is stored in source.
- Import adapters normalize local demo data only and do not perform unauthorized scanning or attack automation.
- WAF Demo Range samples are static offline metadata and do not include reusable attack payloads.
- Demo Range one-click batch import is idempotent for the fixed `DEMO-RANGE-OFFLINE-V1` batch: external events and alerts use stable source identifiers, and Trivy vulnerability rows are upserted by `cveId + softwareName`.
- Shuffle notification is dry-run log writing only unless a future production sender is configured outside source.

## Results From 2026-06-18 Demo Range Batch Update

- `mvn -pl backend test`: passed. 7 tests run, 0 failures, 0 errors. Added coverage for offline Demo Range batch import summary across WAF, ZAP, Trivy, Suricata, Zeek, and Wazuh-style FIM samples.
- `pnpm --dir frontend build`: passed. Vue TypeScript build and Vite production build completed; Rollup only reported existing third-party pure-annotation warnings from `@vueuse/core`.

## Results From 2026-06-19 Event Adapter Policy Update

- Backend adds configurable event adapter policy tables: `soc_event_adapter_profile`, `soc_event_field_mapping`, `soc_event_severity_mapping`, and `soc_event_alert_link_rule`.
- Backend adds policy APIs under `/api/soc/policies/event-adapters` for list, detail, create, update, validate, publish, disable, mapping update, and preview.
- `/api/soc/external-events/cyberfusion/import` now prefers an active adapter profile for WAF/ZAP/Wazuh/Suricata/Zeek JSON records and falls back to built-in adapters when no valid active profile exists. Trivy import continues to write `soc_vulnerability`.
- Frontend `/soc/policies` adds the `事件适配映射` tab with adapter list, mapping drawer, JSON mapping editor, validate/publish/disable controls, and no-write preview.
- SQL seed creates active default adapters for WAF, ZAP, Trivy, Wazuh, Suricata, and Zeek using only simple field paths, fixed transforms, field-array dedup keys, and `{fieldName}` templates.
- `mvn -pl backend test`: passed. 19 tests run, 0 failures, 0 errors. Added coverage for active adapter preview, disabled adapter fallback behavior, invalid source field rejection, unsafe template rejection, and no-write preview.
- `pnpm --dir frontend build`: passed. Vue TypeScript build and Vite production build completed; Rollup reported existing third-party pure-annotation warnings from `@vueuse/core`.

## Release Candidate Acceptance Automation

New smoke scripts:

- `scripts/smoke/run-acceptance.sh --dry-run`: live local API acceptance chain using demo data and dry-run notification boundaries only.
- `scripts/smoke/check-release-safety.sh`: static source safety gate for secrets, runtime files, local terminal execution boundary, adapter validation, and dry-run notification defaults.
- `pnpm --dir frontend exec playwright test tests/e2e/release-pages.spec.ts`: optional page smoke and screenshot capture.

API smoke coverage:

| Area | Checks |
| --- | --- |
| Auth | Login as `admin`, read `/auth/me`, verify permissions and menus. |
| Demo Range | Import fixed offline batch, verify `batchId`, events, linked alerts, Trivy vulnerability count. |
| Evidence | Query evidence chain, external events for WAF/ZAP/Wazuh/Suricata/Zeek, and vulnerability center for Trivy. |
| Alert and ticket | Query linked alert, open alert detail, convert to ticket, verify ticket timeline contains Demo Range source. |
| Response playbook | Query alert playbook suggestions, apply a recommended playbook, verify ticket tasks, start and complete one task. |
| Report | Generate `security_validation` report for the batch. |
| Notification | Trigger Shuffle demo notification and verify `DRY_RUN` status plus batch notification logs. |
| Local check policy | Query active Linux local check commands from `/client/local-terminal/commands?os=Linux`. |
| Adapter policy | Query active adapters for WAF/ZAP/Trivy/Wazuh/Suricata/Zeek and run adapter preview. |
| Playbook policy | Query active response playbooks from `/soc/policies/playbooks`. |
| No-write preview | Compare external event count before and after adapter preview. |

Permission smoke coverage:

| Role | Expected result |
| --- | --- |
| `admin` | Can access `/soc/policies/local-check-commands`, `/soc/policies/event-adapters`, and import Demo Range batch. |
| `analyst` | Can read alerts, events, tickets, and reports; cannot publish event adapter policies and should receive `403`. |
| `operator` employee | Cannot access policy APIs; can read active local check commands and employee tasks; `/client/devices` is scoped to current user-owned assets. |

Page smoke coverage:

| Screenshot | Path |
| --- | --- |
| `acceptance-01-login.png` | `/login` |
| `acceptance-02-showcase.png` | `/showcase` |
| `acceptance-03-demo-range.png` | `/soc/demo-range` |
| `acceptance-04-alerts.png` | `/soc/alerts` |
| `acceptance-05-policies.png` | `/soc/policies` |
| `acceptance-06-reports.png` | `/soc/reports` |
| `acceptance-07-client-workbench.png` | `/client/workbench` |
| `acceptance-08-client-local-range.png` | `/client/local-range?ip=10.20.1.15&host=prod-app-01&os=Linux` |

Safety smoke coverage:

- No high-confidence private key, API token, GitHub token, AWS access key, OpenAI-style key, or Slack token patterns in source.
- No `.env` file, runtime database, log file, Docker volume, or large runtime artifact in source.
- Local terminal execution uses argv `ProcessBuilder` and keeps command validation in `LocalCheckPolicyService`.
- Event adapter mapping service has fixed transform, field-path, dedup key, and alert template validation.
- Response playbook service validates unsafe script, scanner, downloader, destructive, and auto-fix wording and does not contain shell/script execution logic.
- Notification schema and seed remain dry-run by default.

### Results From 2026-06-20 Response Playbook Closure

- Backend added `soc_response_playbook`, `soc_response_playbook_step`, `soc_ticket_task`, and `soc_playbook_match_log` entities, mappers, lifecycle APIs, alert suggestion/application APIs, ticket task APIs, and employee task APIs.
- SQL seed adds five active defensive playbooks for WAF, ZAP, Trivy, Wazuh, and Suricata/Zeek plus manual step checklists.
- Frontend added the `处置剧本` tab under `/soc/policies`, alert-detail playbook suggestions, ticket-detail task checklist, employee-side task list, and `/showcase` playbook summary.
- `mvn -pl backend test`: passed. 27 tests run, 0 failures, 0 errors. Added `ResponsePlaybookServiceTest` coverage for matching, dangerous text rejection, and task creation.
- `pnpm --dir frontend build`: passed. Vue TypeScript build and Vite production build completed; Rollup reported existing third-party pure-annotation warnings from `@vueuse/core`.

### Results From 2026-06-19 Release Candidate Closure

- `git status --short`: could not run because `00-cyberfusion-platform` is not a Git repository in this workspace.
- `mvn -pl backend test`: passed. 24 tests run, 0 failures, 0 errors. Added release-candidate hardening coverage for ZAP fallback normalization, local check policy read fallback, runtime compatibility default paths, and adapter list queries without a `sourceType` filter.
- `pnpm --dir frontend typecheck`: passed.
- `pnpm --dir frontend build`: passed. Vite reported only existing third-party pure-annotation warnings from `@vueuse/core`.
- `docker compose -f deploy/docker-compose.yml config`: failed without `DB_PASSWORD`, as designed. The Compose template requires an Environment-managed database password.
- `DB_PASSWORD=placeholder-for-compose-config docker compose -f deploy/docker-compose.yml config`: passed. This was config interpolation only; no container was started.
- `docker compose -f deploy/demo-range/docker-compose.yml config`: passed. Demo target and WAF bind to localhost and runtime paths point outside source.
- `scripts/smoke/check-release-safety.sh`: passed. 9 safety checks passed.
- `scripts/smoke/run-acceptance.sh --help`: passed.
- `scripts/smoke/run-acceptance.sh --dry-run`: passed against a current-source backend on `http://localhost:18082/api`. 33 checks passed, 0 failed. The run covered login, menus, Demo Range batch import, evidence chain, WAF/ZAP/Wazuh/Suricata/Zeek events, Trivy vulnerability lookup, linked alert, ticket timeline, `security_validation` report generation, Shuffle dry-run notification, DB-backed local check commands, active event adapters, no-write adapter preview, and role permission checks.
- `pnpm --dir frontend exec playwright test tests/e2e/release-pages.spec.ts`: passed. 1 Chromium page smoke passed and refreshed all 8 acceptance screenshots under `docs/screenshots/acceptance-*.png`. No page returned a 500 response and no uncaught console error was observed.
- Before live acceptance, `sql/schema.sql` and `sql/data.sql` were applied idempotently to the local MySQL container using the container-managed MySQL environment. This created or updated the current `soc_local_check_command` and event adapter seed rows without deleting data or volumes.

Release closure notes:

- The acceptance automation and screenshot capture are now repeatable.
- A minimal ZAP fallback normalization fix prevents offline ZAP findings from writing a long URL into `soc_external_event.asset_ip` when adapter configuration is unavailable.
- Read-only local check policy queries now fall back to built-in safe defaults when the policy table is unavailable, while create/update/publish/disable still require the database.
- Runtime compatibility now has an Environment-backed default data root if `app.file.base-dir` is not bound.
- Event adapter list filtering now works when `sourceType` is omitted and `status` or `keyword` is used, which keeps `/soc/policies` and the release smoke stable for the all-adapters view.

## Results From 2026-06-18 Demo Range Closure Update

- Backend service adds `GET /api/soc/demo-range/batches/{batchId}/evidence-chain`, enriched Demo Range alert detail fields, Demo Range ticket timeline source remarks, `security_validation` report generation, and dry-run notification log writes for batch import/report generation.
- Frontend `/soc/demo-range` now shows the batch evidence chain and direct entries to event detail, alert detail, ticket timeline, security validation report generation, and notification dry-run logging.
- API smoke coverage was added to `frontend/tests/e2e/real-backend.spec.ts` for import batch -> event lookup -> alert detail -> ticket -> ticket timeline -> security validation report -> dry-run log.
- `mvn -pl backend test`: passed. 7 tests run, 0 failures, 0 errors.
- `pnpm --dir frontend build`: passed. Vue TypeScript build and Vite production build completed; Rollup reported existing third-party pure-annotation warnings from `@vueuse/core`.
- `pnpm --dir frontend typecheck`: passed.
- `pnpm --dir frontend test:e2e`: passed. 4 Playwright tests passed, including the Demo Range closure API smoke. The Playwright config now runs CyberFusion on `127.0.0.1:5174` and proxies `/api` to `127.0.0.1:18080` to avoid reusing unrelated local projects on `5173`.

## Results From 2026-06-18 Detection Rule Center Update

- Backend adds read-only `GET /api/soc/rules`, `GET /api/soc/rules/hits`, and `GET /api/soc/rules/adapter-mappings`. No new SQL table is required; the rule center aggregates existing `soc_external_event` and `soc_alert` data plus a safe built-in catalog.
- Frontend adds `/soc/rules`, showing rule lifecycle fields, recent hit preview, event/alert jump entries, and adapter field mapping rows.
- SQL seed registers `检测规则中心` as `/soc/rules` with permission `soc:rules:view`.
- `mvn -pl backend test`: passed. 7 tests run, 0 failures, 0 errors.
- `pnpm --dir frontend build`: passed. Vue TypeScript build and Vite production build completed; Rollup reported existing third-party pure-annotation warnings from `@vueuse/core`.
- `pnpm --dir frontend test:e2e`: passed. 4 Playwright tests passed, including `/soc/rules` UI coverage and API smoke checks for rules, hits, and adapter mappings.

## Results From 2026-06-18 Enterprise Delivery Acceptance

- `git status --short`: could not run because `00-cyberfusion-platform` is not a Git repository in this workspace. Scoped file changes are listed in `docs/final-report.md`.
- `grep -RIn "token\|API Key\|BEGIN PRIVATE KEY\|password=" . --exclude-dir=.git || true`: completed. Initial output was noisy because it scanned generated `frontend/dist`, `frontend/node_modules/.vite`, and `backend/target` directories plus normal token variable names.
- `scripts/mac/clean-generated.sh`: passed. Removed `backend/target`, `frontend/dist`, `frontend/test-results`, and `.DS_Store`; kept `frontend/node_modules` as a local ignored dependency directory.
- Scoped sensitive-source scan after cleanup: no private keys, AWS access keys, GitHub tokens, OpenAI-style tokens, Slack tokens, database files, or runtime volumes found. Remaining matches were documentation command examples, frontend secret-detection regexes, and safety text saying not to store customer data.
- `mvn -pl backend test`: passed. 7 tests run, 0 failures, 0 errors.
- `pnpm --dir frontend build`: passed. Vue TypeScript build and Vite production build completed; Rollup reported existing third-party pure-annotation warnings from `@vueuse/core`.
- `docker compose config` from `deploy/` without `DB_PASSWORD`: failed by design because `deploy/docker-compose.yml` requires an explicit local database password. This is an intentional safety guard.
- `DB_PASSWORD=local-compose-config-only docker compose config` from `deploy/`: passed. MySQL, Redis, and Adminer bind to `127.0.0.1` and use Environment-backed volumes.
- `docker compose -f deploy/demo-range/docker-compose.yml config`: passed. Demo Range target and WAF bind to localhost by default and runtime logs are mapped to `/Users/zhangjiyan/Environment/cyberfusion-platform/demo-range`.
- Page screenshot pass: captured 38 pages into `docs/screenshots/` and embedded them into `docs/user-manual.md`. Coverage includes public error pages, SOC modules, system management pages, and client-side pages. `docs/screenshots/manifest.json` records page titles, paths, image filenames, and capture metadata.

## Results From 2026-06-16

- `mvn test`: passed. 4 tests run, 0 failures, 0 errors.
- `pnpm build`: passed after restoring frontend dependencies. Vite built the Vue app successfully.
- `docker compose config`: passed with `DB_PASSWORD=local-compose-config-only`; the initial run without `DB_PASSWORD` failed as expected because the Compose template intentionally requires a local password.
- Secret scan scoped to `00-cyberfusion-platform`: passed after excluding this report and final report command snippets. No matches in source code/templates.
- Full-workspace secret scan: not clean because upstream projects intentionally contain test fixtures, documentation examples, and sample certificates/private keys. Those are upstream artifacts, not new CyberFusion source.
- Browser plugin navigation was unavailable in this tool session. Playwright fallback was used.
- Playwright fallback: passed. `http://127.0.0.1:5174/` rendered `登录 - CyberFusion SOC`, contained `CyberFusion SOC`, and no longer contained the inherited `WAZUH SOC OPERATIONS` login hero text.
- Generated directories from verification (`backend/target`, `frontend/dist`, `frontend/node_modules`) were removed after validation to preserve source hygiene.

## Results From 2026-06-22 A6 Report and Demo Story Upgrade

Scope:

- `/showcase` now presents the customer story line: evidence import -> incident cluster -> risk scoring -> recommended action -> ticket handling -> employee task -> report output.
- `/soc/demo-range` now includes a post-import outcome panel with batch ID, multi-source evidence count, incident cluster count, Top risk asset, Top 3 recommendations, ticket/employee-task status, and dry-run notification count.
- `security_validation` report generation now writes business-readable summary sections for management summary, technical evidence, incident/risk, handling progress, employee collaboration, trend/operation metrics, and safety boundary.
- `/soc/reports` now parses upgraded `security_validation` reports into five detail sections and shows report status, batch, risk summary, incident count, and recommendation count in the list.
- `scripts/smoke/run-acceptance.sh --dry-run` now asserts that generated reports include incident, risk, recommendation, operation, client-task, dry-run, and safety-boundary language, and that employee users cannot access SOC report management APIs.

Coverage matrix:

| Area | A6 check |
| --- | --- |
| Customer demo | `/showcase` displays one-sentence value, current status, `batchId`, and seven-step security operations story. |
| Demo Range result | `/soc/demo-range` shows batch, evidence, incident clusters, risk asset, recommendations, tickets, employee tasks, dry-run count, and report entry. |
| Report generation | `security_validation` summary includes batch, multi-source evidence, incident clusters, Top risk asset, risk change, recommendations, tickets, employee tasks, trend anomalies, operations metrics, dry-run, and safety boundary. |
| Report center | List and detail views make `security_validation` reports readable without opening raw text. |
| Permission boundary | Employee account cannot access SOC report management APIs. |
| Safety boundary | A6 does not add detection, Agent, scanning, attack execution, auto-fix, real notification, or external security-system integration. |

Validation results for this A6 run:

| Command | Result | Notes |
| --- | --- | --- |
| `mvn -pl backend test` | PASS | 52 tests passed, 0 failures, 0 errors. |
| `pnpm --dir frontend build` | PASS | Vue typecheck and Vite production build completed. Rollup reported only existing third-party pure-annotation warnings from `@vueuse/core`. |
| `scripts/smoke/dev-doctor.sh --base-url http://127.0.0.1:5174 --api-base-url http://127.0.0.1:18080/api` | PASS | 15 pass, 0 warn, 0 fail. Confirmed current-source backend process on `18080`, frontend proxy, health, key tables, menu/permission seed, and employee 403 boundaries. |
| `scripts/smoke/check-visibility.sh --base-url http://127.0.0.1:5174 --api-base-url http://127.0.0.1:18080/api` | PASS | 46 pass, 0 fail. Confirmed `/showcase`, `/soc/demo-range`, `/soc/incidents`, `/soc/reports`, policy/correlation/operations APIs, and employee denial for SOC-only APIs. |
| `scripts/smoke/run-acceptance.sh --dry-run` | PASS | 87 pass, 0 fail. Validated demo batch import, incident cluster, risk profile, recommendation, ticket/task, trend/operations metrics, upgraded `security_validation` report summary, dry-run notification boundary, and employee report-management denial. |
| `scripts/smoke/check-release-safety.sh` | PASS | 10 pass. No source `.env`, high-confidence secrets, runtime DB/log files, or unexpected large artifacts; local terminal uses argv `ProcessBuilder`; adapter/playbook validation and dry-run notification defaults remain in place. |

Runtime note:

- The live backend on `18080` was restarted from the current source checkout before the final live smoke so the generated `security_validation` report used the upgraded A6 summary sections.

## Results From 2026-06-23 A7 Algorithm Governance and Replay Evaluation

Scope:

- `/soc/policies` now includes an `算法治理` tab for current event correlation, risk scoring, recommendation ranking, and trend anomaly governance.
- Backend adds `/api/soc/algorithm-center/overview`, `/api/soc/algorithm-center/replay`, `/api/soc/algorithm-center/evaluations`, and `/api/soc/algorithm-center/evaluations/{id}`.
- SQL adds the minimal evaluation persistence tables `soc_algorithm_evaluation` and `soc_algorithm_evaluation_item`.
- Replay evaluation is dry-run only. It reads existing external events, alerts, incidents, risk snapshots, recommendations, and trend anomalies, then returns explainable preview results without creating real incident clusters, risk snapshots, tickets, reports, or notifications.
- Optional saved evaluations write only the evaluation header and preview items for later review.

Coverage matrix:

| Area | A7 check |
| --- | --- |
| Algorithm overview | Admin can load four governance objects: event correlation, risk scoring, recommendation ranking, and trend anomaly. |
| Policy status | Overview exposes active/draft/disabled counts, latest run time, recent hit count, false-positive/ignored/closed counts, source coverage, version, and updater metadata where available. |
| Replay evaluation | Admin can run a demo-batch dry-run replay and receive incident-cluster, risk-change, recommendation, and trend-anomaly preview counts. |
| Explainability | Every replay preview item includes a `reason` explaining why the result would be produced. |
| No production writes | Acceptance smoke compares incident, ticket, and report counts before and after replay and confirms they are unchanged. |
| Evaluation records | When `saveEvaluation=true`, only `soc_algorithm_evaluation` and `soc_algorithm_evaluation_item` are written, and saved items keep explainable reasons. |
| Permission boundary | Admin can view and replay; analyst can view evaluation records but replay returns `403`; employee overview and replay both return `403`. |
| Safety boundary | A7 does not add detection, Agent, external collection, scanning, attack execution, arbitrary shell, auto-fix, real notification, or external WAF/IDS/SIEM/EDR integration. |

Validation results for this A7 run:

| Command | Result | Notes |
| --- | --- | --- |
| `mvn -pl backend test` | PASS | 52 tests passed, 0 failures, 0 errors. |
| `pnpm --dir frontend build` | PASS | Vue typecheck and Vite production build completed. Rollup reported only existing third-party pure-annotation warnings from `@vueuse/core`. |
| `bash -n scripts/smoke/run-acceptance.sh && bash -n scripts/smoke/check-visibility.sh && bash -n scripts/smoke/dev-doctor.sh && bash -n scripts/smoke/check-release-safety.sh` | PASS | Smoke scripts parsed successfully. |
| `scripts/smoke/dev-doctor.sh --base-url http://127.0.0.1:5174 --api-base-url http://127.0.0.1:18080/api` | PASS | 15 pass, 0 warn, 0 fail. Confirmed current-source backend process on `18080`, frontend proxy, health, key tables, policy/incident permissions, and employee 403 boundaries. |
| `scripts/smoke/check-visibility.sh --base-url http://127.0.0.1:5174 --api-base-url http://127.0.0.1:18080/api` | PASS | 53 pass, 0 fail. Confirmed `/soc/policies` algorithm tab source, `/soc/algorithm-center` API source, admin overview 200, analyst evaluation read 200, analyst replay 403, employee overview/replay 403. |
| `scripts/smoke/run-acceptance.sh --dry-run` | PASS | 98 pass, 0 fail. Confirmed algorithm overview, dry-run replay, explainable preview items, no incident/ticket/report production writes, saved evaluation item reasons, and employee denial. |
| `scripts/smoke/check-release-safety.sh` | PASS | 10 pass. No source `.env`, high-confidence secrets, runtime DB/log files, unexpected large artifacts, shell/script execution in local check/adapter/playbook paths, or real notification defaults. |

Replay dry-run result:

- `dryRun=true` and `realWrites=false` were asserted by smoke.
- The acceptance script compared current incident, ticket, and report counts before and after `/api/soc/algorithm-center/replay`; all three remained unchanged.
- Saved evaluation detail loaded successfully and every saved item contained a non-empty explainability `reason`.

Runtime note:

- The A7 live validation ran against the local frontend on `127.0.0.1:5174` and backend API on `127.0.0.1:18080/api`.
- The backend health endpoint reported `UP`; database, schema, seed, and Redis dependencies were healthy during validation.

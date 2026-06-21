# Validation Report

Date: 2026-06-19

Scope: `/Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/00-cyberfusion-platform`

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

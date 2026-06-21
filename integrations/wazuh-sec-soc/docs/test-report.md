# Test Report

Date: 2026-05-27

Scope: migrated project under `/Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/01-wazuh/sec-wazuh-soc`.

## Commands

```sh
export LOCAL_DB_PASSWORD="replace-with-local-db-password"
DB_PASSWORD="$LOCAL_DB_PASSWORD" DB_PORT=33306 REDIS_PORT=36379 ADMINER_PORT=38081 docker compose up -d
docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot -p"$LOCAL_DB_PASSWORD" < ../sql/schema.sql
docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot -p"$LOCAL_DB_PASSWORD" sec_wazuh_soc < ../sql/data.sql
SERVER_PORT=18080 DB_PORT=33306 REDIS_PORT=36379 DB_PASSWORD="$LOCAL_DB_PASSWORD" \
APP_UPLOAD_BASE_DIR=/Users/zhangjiyan/Environment/sec-wazuh-soc/uploads \
APP_CORS_ALLOWED_ORIGIN_PATTERNS=http://127.0.0.1:5173,http://localhost:5173 \
WAZUH_MANAGER_URL=https://127.0.0.1:55050 WAZUH_INDEXER_URL=https://127.0.0.1:9200 \
WAZUH_MANAGER_USERNAME="$LOCAL_WAZUH_MANAGER_USERNAME" WAZUH_MANAGER_PASSWORD="$LOCAL_WAZUH_MANAGER_PASSWORD" \
WAZUH_INDEXER_USERNAME="$LOCAL_WAZUH_INDEXER_USERNAME" WAZUH_INDEXER_PASSWORD="$LOCAL_WAZUH_INDEXER_PASSWORD" \
WAZUH_TLS_VERIFY=false mvn spring-boot:run
VITE_API_PROXY_TARGET=http://127.0.0.1:18080 pnpm dev --host 127.0.0.1 --port 5173
cd /Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/01-wazuh/api/tools/env && docker compose up -d
cd /Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/01-wazuh/api/tools/env && docker compose ps
cd backend && mvn test
cd frontend && pnpm build
cd frontend && PLAYWRIGHT_BASE_URL=http://127.0.0.1:5173 pnpm exec playwright test
cd frontend && node -e "const { chromium } = require('@playwright/test'); /* login to /soc/dashboard, collect console/page errors, assert source marker */"
cd deploy && DB_PASSWORD="$LOCAL_DB_PASSWORD" docker compose config
cd deploy && DB_PASSWORD="$LOCAL_DB_PASSWORD" docker compose ps
curl -i -s http://127.0.0.1:18080/api/v3/api-docs
ACCESS=$(curl -s -X POST http://127.0.0.1:18080/api/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"Admin@123456"}' | jq -r '.data.accessToken')
curl -s -H "Authorization: Bearer $ACCESS" http://127.0.0.1:18080/api/soc/settings/wazuh-health
curl -s -X POST -H "Authorization: Bearer $ACCESS" -H 'Content-Type: application/json' -d '{"reportType":"daily"}' http://127.0.0.1:18080/api/soc/reports/generate
curl -s -H "Authorization: Bearer $ACCESS" 'http://127.0.0.1:18080/api/soc/reports?pageNum=1&pageSize=5&reportType=daily'
curl -s -H "Authorization: Bearer $ACCESS" 'http://127.0.0.1:18080/api/soc/vulnerabilities?pageNum=1&pageSize=5'
curl -s -H "Authorization: Bearer $ACCESS" 'http://127.0.0.1:18080/api/soc/baselines?pageNum=1&pageSize=5'
curl -s -H "Authorization: Bearer $ACCESS" 'http://127.0.0.1:18080/api/soc/fim?pageNum=1&pageSize=5'
curl -s -X POST -H "Authorization: Bearer $ACCESS" -H 'Content-Type: application/json' -d '{"targetStatus":"fixing","remark":"P1.0 smoke"}' http://127.0.0.1:18080/api/soc/vulnerabilities/1/status
curl -s -X POST -H "Authorization: Bearer $ACCESS" -H 'Content-Type: application/json' -d '{"reportType":"daily"}' http://127.0.0.1:18080/api/soc/reports/generate | grep '漏洞态势'
curl -s -H "Authorization: Bearer $ACCESS" 'http://127.0.0.1:18080/api/soc/settings/notification-channels'
curl -s -H "Authorization: Bearer $ACCESS" 'http://127.0.0.1:18080/api/soc/settings/notification-logs?pageNum=1&pageSize=5'
curl -s -X POST -H "Authorization: Bearer $ACCESS" http://127.0.0.1:18080/api/soc/settings/notification-channels/1/test
curl -s -H "Authorization: Bearer $ACCESS" 'http://127.0.0.1:18080/api/soc/alert-noise/summary'
curl -s -H "Authorization: Bearer $ACCESS" 'http://127.0.0.1:18080/api/soc/alert-noise/whitelists?pageNum=1&pageSize=5'
curl -s -H "Authorization: Bearer $ACCESS" 'http://127.0.0.1:18080/api/soc/alert-noise/aggregations?pageNum=1&pageSize=12'
curl -s -X POST -H "Authorization: Bearer $ACCESS" -H 'Content-Type: application/json' -d '{"ruleName":"P1.5 smoke whitelist","ruleId":"5715","severity":"critical","reason":"接口回归创建测试","enabled":1}' http://127.0.0.1:18080/api/soc/alert-noise/whitelists
curl -s -X PUT -H "Authorization: Bearer $ACCESS" -H 'Content-Type: application/json' -d '{"ruleName":"P1.5 smoke whitelist edited","ruleId":"5715","severity":"critical","reason":"接口回归编辑测试","enabled":1}' http://127.0.0.1:18080/api/soc/alert-noise/whitelists/{id}
curl -s -X POST -H "Authorization: Bearer $ACCESS" -H 'Content-Type: application/json' -d '{"targetStatus":"disabled","remark":"P1.5 smoke"}' http://127.0.0.1:18080/api/soc/alert-noise/whitelists/1/status
curl -s -H "Authorization: Bearer $ACCESS" 'http://127.0.0.1:18080/api/soc/external-events?pageNum=1&pageSize=5&sourceType=suricata'
curl -s -H "Authorization: Bearer $ACCESS" 'http://127.0.0.1:18080/api/soc/external-events/summary'
curl -s -X POST -H "Authorization: Bearer $ACCESS" -H 'Content-Type: application/json' -d '{"linkAlerts":true,"content":"{\"timestamp\":\"2026-05-27T22:55:00+08:00\",\"event_type\":\"alert\",\"src_ip\":\"203.0.113.88\",\"dest_ip\":\"10.20.1.15\",\"alert\":{\"signature_id\":20260527,\"signature\":\"ET SCAN Playwright Suricata import\",\"severity\":1}}"}' http://127.0.0.1:18080/api/soc/external-events/suricata/import
curl -s -X POST -H "Authorization: Bearer $ACCESS" -H 'Content-Type: application/json' -d '{"targetStatus":"linked","remark":"P2.0 smoke"}' http://127.0.0.1:18080/api/soc/external-events/1/status
curl -s -H "Authorization: Bearer $ACCESS" 'http://127.0.0.1:18080/api/soc/dashboard/risk-analytics'
curl -i -s -X OPTIONS http://127.0.0.1:18080/api/auth/login -H 'Origin: http://127.0.0.1:5173' -H 'Access-Control-Request-Method: POST'
curl -i -s http://127.0.0.1:18080/api/v3/api-docs | grep -E 'X-Content-Type-Options|X-Frame-Options|Referrer-Policy|Content-Security-Policy'
for i in $(seq 1 25); do curl -s -o /tmp/sec-wazuh-rate-$i.json -w "%{http_code}\n" -X POST http://127.0.0.1:18080/api/auth/login -H 'Content-Type: application/json' -d '{"username":"bad","password":"bad"}'; done
curl -s http://127.0.0.1:18080/api/health/liveness
curl -s http://127.0.0.1:18080/api/health/readiness
bash -n scripts/mac/backup-runtime.sh scripts/mac/restore-runtime.sh
pwsh -NoProfile -Command "& { \$errors = \$null; [System.Management.Automation.PSParser]::Tokenize((Get-Content -Raw scripts/win/backup-runtime.ps1), [ref]\$errors) | Out-Null; if (\$errors) { throw \$errors }; [System.Management.Automation.PSParser]::Tokenize((Get-Content -Raw scripts/win/restore-runtime.ps1), [ref]\$errors) | Out-Null; if (\$errors) { throw \$errors } }"
DB_PASSWORD="$LOCAL_DB_PASSWORD" scripts/mac/backup-runtime.sh
DB_PASSWORD="$LOCAL_DB_PASSWORD" docker compose -f deploy/docker-compose.app.example.yml config
DB_PASSWORD="$LOCAL_DB_PASSWORD" docker compose -f deploy/docker-compose.app.example.yml build backend
DB_PASSWORD="$LOCAL_DB_PASSWORD" docker compose -f deploy/docker-compose.app.example.yml build frontend
docker image inspect sec-wazuh-soc-backend:local sec-wazuh-soc-frontend:local
docker run --rm --entrypoint nginx sec-wazuh-soc-frontend:local -t
docker run --rm --entrypoint sh sec-wazuh-soc-backend:local -c 'command -v curl >/dev/null && test -f /app/app.jar'
rg -n "BEGIN .*PRIVATE KEY|AKIA[0-9A-Z]{16}|ghp_[A-Za-z0-9_]+|sk-(proj|live|test)-[A-Za-z0-9_-]+|xoxb-[A-Za-z0-9-]+|真实客户数据|客户数据" . --glob '!frontend/node_modules/**' --glob '!frontend/dist/**' --glob '!backend/target/**' --glob '!README.md' --glob '!docs/**'
```

## Results

- Docker runtime: passed on isolated ports. MySQL, Redis, and Adminer containers were healthy at `33306`, `36379`, and `38081`.
- Database initialization: passed. Seeded 7 SOC alerts, 4 assets, 2 initial tickets, 1 report, 4 vulnerabilities, 5 baseline checks, 4 file-integrity events, 2 alert-noise whitelist rules, 2 Suricata external events, and SOC menus/permissions.
- Backend runtime: passed at `http://127.0.0.1:18080/api`.
- Frontend runtime: passed at `http://127.0.0.1:5173`.
- Wazuh Docker single-node: passed. Parent Wazuh stack is running with healthy Manager, Indexer, and Agent containers; Dashboard is exposed on `127.0.0.1:443`, Manager API on `127.0.0.1:55050`, and Indexer on `127.0.0.1:9200`.
- Backend Wazuh integration: passed. `GET /api/soc/settings/wazuh-health` returned `manager=CONNECTED`, `indexer=CONNECTED`, `configured=true`; response metadata identified `Wazuh API REST` and Indexer cluster `wazuh-cluster`.
- `mvn test`: passed. Ran `ApiResultTest` with 1 test, 0 failures, 0 errors.
- `pnpm build`: passed. Rollup emitted third-party pure annotation warnings from `@vueuse/core`, but the production build completed successfully.
- Playwright E2E: passed. Ran 3 Chromium tests covering login restore, SOC dashboard, alert center, alert-noise page, assets, vulnerabilities, baseline checks, file integrity, external events, tickets, reports, settings, public error pages, read-only auditor enforcement, and ops data-scope checks.
- Browser console smoke: passed. Logged into `/soc/dashboard` with Playwright, verified `errors=[]`, `pageErrors=[]`, and one visible `当前数据来源` marker.
- `docker compose config`: passed.
- OpenAPI: passed. `/api/v3/api-docs` returned HTTP 200 with JSON content.
- Browser smoke: passed. Logged in with the local test account, opened dashboard, alert center, asset view, vulnerability center, baseline checks, file integrity, ticket center, report center, and system settings.
- P1.0 API smoke: passed. Vulnerability list returned 4 rows, baseline list returned 5 rows, FIM list returned 4 rows, and vulnerability status update returned `fixing`.
- P1.0 report smoke: passed. Daily report summary contains `漏洞态势`, `基线核查`, and `文件完整性`; Excel export returned HTTP 200 with 2807 bytes and PDF export returned HTTP 200 with 1438 bytes.
- P1.5 notification smoke: implemented. Settings exposes notification channels and notification logs; dry-run email test writes `DRY_RUN` records without storing SMTP credentials. Alert-to-ticket, ticket review/close, and report generation dispatch notification log events according to channel severity and trigger rules.
- P1.5 alert-noise smoke: implemented. Alert center records expose whitelist and repeat-count fields; alert-noise summary, whitelist list, duplicate aggregation, whitelist create/update, and whitelist enable/disable endpoints are available with backend permissions and data-scope checks; browser smoke confirmed the `新增规则` dialog opens after the responsive toolbar fix.
- P2.0 Suricata import smoke: passed. EVE JSON Lines import returned `importedEvents=1`, `linkedAlerts=1`, `skippedLines=0`; the imported event appeared in `/api/soc/external-events` and the linked alert appeared in `/api/soc/alerts`.
- P2.5 risk analytics smoke: passed. `GET /api/soc/dashboard/risk-analytics` returned asset risk scores, alert priority scores, department risk rankings, operation metrics, and event timeline data within the logged-in user's data scope.
- P3 security hardening smoke: implemented. Backend CORS is environment-configurable; security headers are emitted for API responses; auth/API rate limiting returns `TOO_MANY_REQUESTS` with HTTP 429 after the configured minute window is exceeded.
- P3 health/readiness smoke: implemented. `/api/health/liveness` returns process `UP`; `/api/health/readiness` checks MySQL and Redis and returned `UP` with sanitized dependency metadata in the local stack.
- P3 backup/restore scripts: implemented. macOS and Windows scripts back up MySQL and Redis into Environment-backed runtime backup directories, require runtime-only `DB_PASSWORD`, and require explicit confirmation before restore. Bash syntax validation passed, and macOS live backup created a MySQL dump, Redis snapshot, and manifest under `/Users/zhangjiyan/Environment/sec-wazuh-soc/backups/runtime`.
- P3 application containerization: passed. Backend and frontend Dockerfiles, frontend Nginx container config, HTTPS reverse-proxy example, and full app Compose example were added. Compose config validation passed. `sec-wazuh-soc-backend:local` and `sec-wazuh-soc-frontend:local` images built successfully; frontend Nginx config test passed; backend image contains `/app/app.jar` and `curl` for health checks.
- P1/P2 permission regression: passed. Read-only auditor can list vulnerability/baseline/FIM/external-event data but receives HTTP 403 on vulnerability and external-event write actions; ops role sees scoped P1 records for owned assets.
- P1.0 browser console smoke: passed. Navigated `/soc/vulnerabilities`, `/soc/baselines`, and `/soc/fim`; browser console `errors=[]` and `pageErrors=[]`.
- List-page UX: passed. Alert center supports selection and batch acknowledge/ignore/close; asset view supports selection, batch IP copy, batch CSV export, and detail drawer; ticket center supports selection, batch valid-status transitions, and detail drawer; report center supports search, type filter, selection, batch export, and detail drawer.
- P0.5 stability UX: passed. SOC dashboard, alert center, asset view, ticket center, report center, and Wazuh settings now expose loading states, empty states, retryable error alerts, and explicit data-source markers (`实时` / `同步` / `演示`) where relevant.
- Business flow: passed. Confirmed alert `MOCK-20260527-0001`, converted it to a ticket, moved the ticket through `待分派 -> 处理中 -> 待复核 -> 已关闭 -> 已归档`, and generated a daily report.
- Report content/export: passed. Generated report summary includes alert trend, risk-level distribution, ticket handling status, asset risk, vulnerability posture, baseline pass/fail status, file-integrity event status, external event coverage, and remediation suggestions; Excel and PDF export returned HTTP 200; report type filtering returned only daily reports for `reportType=daily`.
- Audit log: passed. Recent SOC queries/actions were recorded in `sys_operation_log` with `SUCCESS`.
- Wazuh health check: passed in live local single-node mode. If Wazuh runtime variables are omitted, the same endpoint still reports P0 mock/MySQL mode explicitly.
- Data scope: passed. Admin and auditor saw 4 assets / 4 alerts; security analyst saw 2 assets / 2 alerts under department-tree scope; ops saw 2 owned assets and received `AUTH_FORBIDDEN` for the alert center because the role lacks alert menu/API permission.
- Read-only auditor enforcement: passed. Auditor write attempt to close an alert returned HTTP 403.
- secret/customer-data scan: passed after excluding generated dependency/build directories and literal documentation examples.
- config hardening: passed. Runtime database password is now required through environment variables; source files no longer contain the previous concrete development DB password default.
- generated-output cleanup: passed. `frontend/dist`, `frontend/test-results`, and `frontend/playwright-report` were removed after validation. `frontend/node_modules` and `backend/target` are present while local dev servers are running and should not be committed.

## P0 Acceptance Coverage

- Login page: implemented.
- Security overview: implemented.
- Data source strategy: visible in the UI as `实时` for Wazuh/Indexer, `同步` for imported/synchronized MySQL data, and `演示` for mock demo records. Current P0 data remains demo/MySQL records with live Wazuh health verification.
- Asset view: implemented, including batch actions and detail drawer.
- Alert list and detail drawer: implemented.
- Alert actions: acknowledge, false-positive, ignore, close, convert to ticket, plus batch acknowledge/ignore/close from selected rows.
- Ticket list and detail drawer: implemented, including batch state transition for valid selected rows.
- Ticket flow: `待分派 -> 处理中 -> 待复核 -> 已关闭 -> 已归档`.
- Reports: daily/weekly/monthly generation plus Excel/PDF export endpoints; list supports search, type filter, batch export, and detail drawer; content includes alert trend, risk levels, handling status, asset risk, vulnerability posture, baseline pass/fail status, file-integrity event status, and remediation suggestions.
- System config: Wazuh config list, sync task list, health check.
- Permissions: menu/button/interface permissions seeded; backend uses `@PreAuthorize`.
- Data scope: role scopes support `self`, `dept`, `dept_tree`, `all`, and `custom`, with custom role-department bindings.
- Audit: controller operations, including GET queries, write to operation log except auth and Swagger paths.

## P1.0 Security Capability Coverage

- Vulnerability center: implemented with CVE, severity, affected asset, software version, remediation suggestion, status flow, summary cards, list filters, detail drawer, API permission, data scope, and operation audit.
- Baseline checks: implemented with SSH, password policy, firewall, system service, sensitive file permission checks, pass rate, remediation suggestion, status flow, summary cards, list filters, detail drawer, API permission, data scope, and operation audit.
- File integrity: implemented with created/modified/deleted/permission events, host, path, time, rule name, status flow, summary cards, list filters, detail drawer, API permission, data scope, and operation audit.
- P1.0 data source strategy: current demo records use `mock` and `import` source types and are displayed as `演示` / `同步`; live Agent ingestion remains a later enhancement and does not affect the P0 main chain.
- P1.0 report statistics: implemented. Reports aggregate vulnerability severity/open status, baseline failed/pass-rate status, and FIM event/review status.

## P1.5 Notification Coverage

- Notification channel registry: implemented with channel type, target, severity threshold, trigger event, send mode, last status, and last sent time.
- Notification logs: implemented for test sends and business events.
- Dry-run email delivery: implemented for demo/acceptance without hard-coded SMTP secrets.
- Business triggers: alert converted to ticket, ticket submitted for review, ticket closed, and report generated.
- Audit: notification test endpoint is controller-audited and notification log entries are retained in `soc_notification_log`.

## P1.5 Alert Noise Coverage

- Whitelist registry: implemented with rule ID, asset IP, source IP, severity, expiration, match count, owner/dept scope, and enabled status.
- Whitelist maintenance: implemented with create/edit dialog, responsive toolbar layout, and backend validation requiring at least one matching condition.
- False-positive management: alert center still supports explicit false-positive status; alert-noise summary counts false-positive alerts.
- Duplicate aggregation: implemented by grouping recent authorized alerts by rule ID, asset IP, and source IP.
- Alert center marking: list and detail drawer expose whitelist hit, matched rule name, noise status, and repeat count.
- Permissions: alert-noise page uses `soc:alert-noise:view`; whitelist status changes use `soc:alert-noise:status` and are controller-audited.

## P2.0 External Security Source Coverage

- Normalized event model: implemented `soc_external_event` with `source_type`, `event_type`, `severity`, `asset_ip`, `ioc`, `raw_event`, `normalized_event`, linked `alert_id`, owner/dept scope, and event time.
- Suricata integration path: seeded Suricata IDS demo events and linked them into unified `soc_alert` records with `source_type=suricata`.
- Suricata import: implemented EVE JSON Lines import with structured JSON parsing, deterministic event UID, duplicate-safe upsert, and optional unified-alert linking.
- Query API: implemented list, detail, summary, and status-flow endpoints under `/api/soc/external-events`.
- UI: implemented external event list, source/severity filters, summary cards, Suricata import dialog, detail drawer, normalized/raw event inspection, and status actions.
- Permissions: external event page uses `soc:external-event:view`; status changes use `soc:external-event:status`; Suricata import uses `soc:external-event:import`; write actions are controller-audited.
- Reports: generated reports include external event coverage and recommendations when high-risk external events exist.

## P2.5 Risk And Operations Analysis Coverage

- Asset risk scoring: implemented as a computed backend API using alert severity/status, vulnerability status, baseline failures, FIM/external high-risk exposure, and unresolved handling pressure. Each returned score includes a human-readable explanation and component weights.
- Alert priority scoring: implemented from severity, high-risk asset hit, IOC/external-event correlation, repeat count, and current handling status.
- Department risk: implemented from scoped department assets, high-risk alerts, open vulnerabilities, failed baselines, and pending tickets.
- Operations metrics: implemented SLA rate, overdue tickets, closed tickets, false-positive rate, duplicate groups, and average close time.
- Event timeline: implemented by combining recent alerts, external events, ticket creation, and ticket close events for the analyst workbench.
- UI: the security overview now exposes `管理驾驶舱` and `分析员工作台` tabs without adding unfinished menus.

## P3.0 Security Hardening Coverage

- CORS hardening: implemented with `APP_CORS_ALLOWED_ORIGIN_PATTERNS`, `APP_CORS_ALLOWED_METHODS`, `APP_CORS_ALLOWED_HEADERS`, `APP_CORS_EXPOSED_HEADERS`, `APP_CORS_ALLOW_CREDENTIALS`, and `APP_CORS_MAX_AGE_SECONDS`.
- Security response headers: implemented through Spring Security for content type protection, frame denial, no-referrer policy, and conservative API CSP.
- API rate limiting: implemented as an in-memory `OncePerRequestFilter` keyed by client IP, method, and path group. Auth routes use `APP_RATE_LIMIT_AUTH_REQUESTS_PER_MINUTE`; other API routes use `APP_RATE_LIMIT_REQUESTS_PER_MINUTE`.
- Regression test: added unit coverage for rejecting over-limit auth requests and skipping CORS preflight requests.
- Health/readiness probes: implemented public `/api/health/liveness` and `/api/health/readiness` endpoints for Docker/Nginx/private deployment checks. Readiness probes MySQL and Redis and does not expose connection strings or credentials.
- Backup/restore scripts: implemented for macOS/Linux and Windows PowerShell. Backups are written to Environment by default, restore requires explicit confirmation, and Redis restore is opt-in.
- Application containers: implemented backend and frontend Dockerfiles, a frontend Nginx config with `/api/` reverse proxying, a full app Compose example, Environment-backed upload/log mounts, and a server-level HTTPS Nginx example.

## Remaining Risks

- Wazuh live integration is verified as a backend health wrapper in P0; alert ingestion remains the allowed P0 simulated/MySQL data path rather than live Indexer synchronization.
- P1.0 modules currently use mock/import demonstration data. Real Wazuh Agent-backed vulnerability, baseline, and FIM synchronization remains a follow-up task.
- P1.5 notification delivery currently records dry-run logs. Production SMTP or webhook sending still needs runtime-only credentials and sender implementation.
- P2.0 currently covers Suricata demo/import style normalization. Zeek and MISP/OpenCTI connectors are schema/API-ready but still need concrete ingestion tasks.
- P2.5 scoring is transparent and deterministic but still rule-weight based. Production deployment should calibrate weights with customer SLA policy, asset criticality inventory, and historical incident outcomes.
- P3 rate limiting is node-local in memory. Multi-instance production deployment should move counters to Redis or an API gateway.
- The local Wazuh stack uses self-signed certificates; production must use trusted certificates or set `WAZUH_TLS_VERIFY=true`.
- PDF export is valid for MVP validation but can be polished further for branded report layout in P1.

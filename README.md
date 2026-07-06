# CyberFusion SOC

CyberFusion SOC is the unified security operations portal for the local `cyberspace_Security_shot_time` workspace. The active product, backend, frontend, SQL, docs, deployment templates, demo data, and curated integration programs now live under this `00-cyberfusion-platform` directory. The sibling `01-16` directories remain upstream/reference sources only.

## Scope

- Main system: Spring Boot 3 backend plus Vue 3/Vite/Element Plus frontend.
- P0: login, RBAC, departments, data scope, SOC layout, assets, raw/normalized events, alerts, tickets, reports, audit logs, demo data.
- P1/P2 adapters: Wazuh demo alerts, Zeek logs, Suricata `eve.json`, Trivy JSON, MISP IOC, ZAP JSON, Sigma rule records, CyberChef field analysis, Shuffle dry-run notification workflow.
- Optional adapters: Security Onion, Falco, OpenCTI-lite, osquery, Velociraptor, Cowrie.
- Excluded mainline: `15-juice-shop`; kept only as a future training range source.

## Integration Programs

Curated integration-side programs are copied into `integrations/` so the current project no longer depends on manually jumping across sibling directories during demos and handoff.

- `integrations/catalog.json` is the local source of truth for integration program paths and CyberFusion API entrypoints.
- `integrations/README.md` explains the copied directories, source modules, safety boundaries, and import APIs.
- `GET /api/soc/integrations/catalog` exposes the same read-only catalog to the platform UI and smoke checks.
- All defensive evidence still enters CyberFusion through the platform APIs, primarily `POST /api/soc/external-events/cyberfusion/import`.
- Real runtime data, generated logs, Docker volumes, credentials, tokens, and private keys still belong outside source under Environment.

## Source And Runtime Boundaries

- Source: this `00-cyberfusion-platform` directory, including `integrations/` for curated local integration programs.
- Runtime root: set `CYBERFUSION_ENV_ROOT` outside the source tree, for example `$HOME/Environment/cyberfusion-platform` on macOS/Linux or `D:\CyberFusion\Environment\cyberfusion-platform` on Windows.
- Source keeps code, README, docs, `.env.example`, config templates, deploy scripts, and SQL seed scripts.
- Databases, logs, uploads, caches, certificates, secrets, Docker volumes, and backups must live under Environment or another protected runtime path.
- Do not commit real tokens, API keys, private keys, customer data, large logs, Docker volumes, model files, or runtime databases.

## Start

macOS / Linux:

```sh
export LOCAL_DB_PASSWORD="replace-with-local-db-password"
export CYBERFUSION_ENV_ROOT="$HOME/Environment/cyberfusion-platform"

cd deploy
DB_PASSWORD="$LOCAL_DB_PASSWORD" docker compose up -d

cd ..
MYSQL_PWD="$LOCAL_DB_PASSWORD" docker exec -e MYSQL_PWD -i cyberfusion-platform-mysql-1 \
  mysql --default-character-set=utf8mb4 -uroot < sql/schema.sql
MYSQL_PWD="$LOCAL_DB_PASSWORD" docker exec -e MYSQL_PWD -i cyberfusion-platform-mysql-1 \
  mysql --default-character-set=utf8mb4 -uroot cyberfusion_soc < sql/data.sql

DB_PASSWORD="$LOCAL_DB_PASSWORD" scripts/mac/backend-dev.sh

scripts/mac/frontend-dev.sh
```

The frontend script defaults to `FRONTEND_PORT=5174` and automatically sets
`VITE_API_PROXY_TARGET=http://127.0.0.1:18080`. If you start Vite manually, keep
the same proxy target:

```sh
VITE_API_PROXY_TARGET=http://127.0.0.1:18080 pnpm --dir frontend dev --host 127.0.0.1 --port 5174
```

Windows PowerShell:

```powershell
cd D:\CyberFusion\00-cyberfusion-platform
$env:DB_PASSWORD = "replace-with-local-db-password"
$env:CYBERFUSION_ENV_ROOT = "D:\CyberFusion\Environment\cyberfusion-platform"
.\scripts\win\run-dev.ps1 -DbPassword $env:DB_PASSWORD -FrontendPort 5174 -ServerPort 18080
```

The Windows entrypoint is the no-Docker path: put the project on D drive, expect local or reachable MySQL 8 and Redis services, use `mysql.exe` to apply `sql/schema.sql`, `sql/data.sql`, and `scripts/sql/apply-latest-menu-and-policy-seed.sql`, then start the Spring Boot backend and Vite frontend. See [docs/windows-no-docker.md](docs/windows-no-docker.md) for the full Windows checklist.

Default URLs:

- Frontend: `http://127.0.0.1:5174` when using the local dev scripts, or the port passed to the dev script.
- Backend API: `http://127.0.0.1:18080/api` when using the local dev scripts, or the `ServerPort` passed to the dev script.
- Health: `http://127.0.0.1:18080/api/health`
- Swagger: `http://127.0.0.1:18080/api/swagger-ui.html`

Demo account: `admin / Admin@123456` from the seed SQL. Some local demo databases may have changed the admin password to `admin123`; the smoke scripts also accept that fallback unless `CYBERFUSION_ADMIN_PASSWORD` is set. These are local demo-only accounts; the SQL stores BCrypt hashes. Re-running `sql/data.sql` preserves existing user password hashes and will not reset an already-initialized admin password.

## Runtime Doctor

Before live smoke or after any startup issue, run the read-only doctor:

```sh
scripts/smoke/dev-doctor.sh --base-url http://127.0.0.1:5174 --api-base-url http://127.0.0.1:18080/api
```

Windows PowerShell:

```powershell
.\scripts\win\dev-doctor.ps1 -BaseUrl http://127.0.0.1:5174 -ApiBaseUrl http://127.0.0.1:18080/api
```

The doctor checks frontend/backend ports, the frontend `/api` proxy to the backend, backend Java process start time, `/api/health`, required SOC tables, key menu/permission seed rows, admin `/auth/me` menus/permissions, and employee 403 boundaries for SOC-only APIs. It does not delete volumes, reset passwords, import demo data, scan targets, execute local terminal commands, or send notifications.

The Windows no-Docker startup requires local `mysql.exe` because it initializes the schema directly against the configured MySQL server. The read-only doctor can still use local `mysql` when available, or fall back to the MySQL client inside `cyberfusion-platform-mysql-1` for Docker-based macOS/Linux setups. In both modes `DB_PASSWORD` must come from the local environment and is not printed.

Safe backend startup sequence:

1. Start MySQL/Redis and keep runtime data under Environment. On Windows this means local services, not Docker.
2. Apply `sql/schema.sql`, then `sql/data.sql`, then `scripts/sql/apply-latest-menu-and-policy-seed.sql` for already-initialized local databases.
3. Stop any stale Java process that is still listening on the backend port.
4. Start the backend from the current source checkout with the active local `DB_PASSWORD`.
5. Confirm `GET /api/health` reports `database`, `schema`, `seed`, and `redis`.

## Local VM Field Compatibility

The client local field at `/client/local-range` is built to survive project restarts and OS changes:

- The page starts from a local VM-style login screen; demo passwords are not stored.
- Activity evidence and the optional noVNC / VM console URL are stored in browser local storage per asset IP.
- The terminal command menu adapts to the browser host OS: Windows uses `whoami /groups`, `tasklist`, `netstat`, and `reg query`; macOS uses `id`, `ps`, `lsof`, and `launchctl`; Linux uses `id`, `ps`, `ss`/fallbacks, and `systemctl`/fallbacks.
- Backend fact collection uses fixed read-only command candidates per OS and writes results to SOC as `osquery`-style evidence. It does not run arbitrary user commands.
- Optional VM console embedding only accepts localhost or private-network URLs such as `http://127.0.0.1:6080/vnc.html`.

Compatibility checks:

```sh
bash scripts/mac/local-vm-compat-check.sh
```

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\win\local-vm-compat-check.ps1
```

## Validation Loop

The external event page can import:

- Zeek `conn.log` rows.
- Suricata `eve.json` JSON Lines.
- Wazuh demo alert JSON.
- MISP IOC JSON.
- Trivy JSON into the vulnerability center.
- ZAP JSON findings.

Then use the alert center to convert a generated alert to a ticket, use CyberChef analysis on an IOC/network field, trigger the Shuffle dry-run notification, and generate a comprehensive report.

## One-Command Acceptance

Run these commands from `00-cyberfusion-platform` before handing over a release candidate:

```sh
git status --short
mvn -pl backend test
pnpm --dir frontend build
pnpm --dir frontend typecheck
docker compose -f deploy/docker-compose.yml config
docker compose -f deploy/demo-range/docker-compose.yml config
scripts/smoke/check-release-safety.sh
scripts/smoke/dev-doctor.sh --base-url http://127.0.0.1:5174 --api-base-url http://127.0.0.1:18080/api
scripts/smoke/check-visibility.sh --base-url http://127.0.0.1:5174 --api-base-url http://127.0.0.1:18080/api
scripts/smoke/run-acceptance.sh --dry-run
```

`scripts/smoke/run-acceptance.sh --dry-run` still calls the local API to prove the acceptance chain. It covers demo batch import, evidence, linked alerts, incident correlation, incident-cluster detail, alert-to-incident lookup, incident-to-ticket conversion, recommended response playbook application, ticket tasks, report generation, policy checks, adapter preview, and dry-run notifications. The dry-run boundary means it uses local demo data and dry-run notification logs only; it does not scan public targets and does not send real email, webhook, Feishu, WeCom, DingTalk, Slack, or SIEM/WAF/IDS traffic. Set `CYBERFUSION_API_BASE` if the backend is not on `http://localhost:18080/api`.

Before running live acceptance, start the backend from the current source checkout with Environment-managed database variables. `DB_PASSWORD` must match the active local MySQL container; an incorrect value can allow the backend process to start but make `/api/auth/login` return `500`.

## Demo Data Governance

Repeated live smoke runs reuse stable demo identifiers and are designed to be idempotent, but reports, dry-run notification logs, tickets, and incident evidence can still accumulate during manual demos. Use the cleanup script in dry-run mode first:

```sh
scripts/smoke/cleanup-demo-data.sh
```

The script only targets demo/smoke scoped rows for `DEMO-RANGE-OFFLINE-V1` and `DEMO-RANGE-ACCEPTANCE-SMOKE`: multi-source events, linked alerts, Trivy demo vulnerabilities, incident clusters/evidence, linked demo tickets/tasks/timeline rows, security-validation reports, playbook match logs, and dry-run notification logs. It does not delete Docker volumes or unrelated business data.

Real cleanup requires explicit confirmation:

```sh
scripts/smoke/cleanup-demo-data.sh --batch-id DEMO-RANGE-ACCEPTANCE-SMOKE --confirm
```

Optional page smoke screenshots:

```sh
pnpm --dir frontend exec playwright test tests/e2e/release-pages.spec.ts
```

The page smoke writes acceptance screenshots to `docs/screenshots/acceptance-*.png` and records the expected set in `docs/screenshots/manifest.json`.

If the latest UI changes are not visible, run the visibility check before changing code:

```sh
scripts/smoke/check-visibility.sh --base-url http://127.0.0.1:5174 --api-base-url http://127.0.0.1:18080/api
```

The visibility check includes the customer demo route, `/soc/incidents`, `/soc/policies`, the policy center `事件关联规则` tab/API, and employee 403 boundaries for incident and rule-management APIs.

It logs in with local demo users, prints `/api/auth/me` menu paths, checks key frontend routes such as `/showcase`, `/soc/policies`, `/client/tasks`, checks policy/adapter/playbook APIs, and confirms employees receive `403` for policy APIs. Existing databases may need a schema and seed refresh because `sql/data.sql` is only applied automatically for newly initialized databases:

```sh
MYSQL_PWD="$DB_PASSWORD" docker exec -e MYSQL_PWD -i cyberfusion-platform-mysql-1 \
  mysql --default-character-set=utf8mb4 -uroot < sql/schema.sql
MYSQL_PWD="$DB_PASSWORD" docker exec -e MYSQL_PWD -i cyberfusion-platform-mysql-1 \
  mysql --default-character-set=utf8mb4 -uroot cyberfusion_soc < sql/data.sql
MYSQL_PWD="$DB_PASSWORD" docker exec -e MYSQL_PWD -i cyberfusion-platform-mysql-1 \
  mysql --default-character-set=utf8mb4 -uroot cyberfusion_soc < scripts/sql/apply-latest-menu-and-policy-seed.sql
```

After refreshing SQL, restart the backend process so `/api` loads the latest `backend/target/classes`. A Java process that was started before the latest Maven build can still serve old controller/service code.

## Enterprise Delivery Checklist

- README, `.env.example`, `.gitignore`, `.gitattributes`, SQL, scripts, Docker Compose, Nginx templates, and docs are the reusable delivery assets.
- Runtime data, Docker volumes, logs, uploads, database files, generated reports, test output, real secrets, and customer data must stay outside source.
- The 10-minute acceptance demo is documented in [User Manual](docs/user-manual.md).
- Screenshot requirements, actual page screenshots, common bugs, and triage commands are documented in [User Manual](docs/user-manual.md). The screenshot assets live under `docs/screenshots/`.
- Handover checklist and final verification commands are documented in [Handover](docs/handover.md).

## Docs

- [Architecture](docs/architecture.md)
- [API](docs/api.md)
- [Database](docs/database.md)
- [Deploy](docs/deploy.md)
- [User Manual](docs/user-manual.md)
- [Handover](docs/handover.md)
- [Upstream And Licenses](docs/upstream.md)
- [Validation Report](docs/test-report.md)
- [Final Implementation Report](docs/final-report.md)

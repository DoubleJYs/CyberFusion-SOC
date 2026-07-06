# Handover Guide

## Delivery Scope

CyberFusion SOC is delivered as a defensive SOC platform plus an isolated Demo Range. The handover package includes source, configuration templates, SQL schema/seed, deployment templates, API docs, database docs, user manual, test report, final report, and upstream license notes.

The package intentionally excludes runtime data, Docker volumes, generated logs, uploaded files, customer data, private keys, real tokens, and production sender credentials.

## Asset Checklist

| Asset | Path | Status |
| --- | --- | --- |
| Project README | `README.md` | Required entrypoint. |
| Environment template | `.env.example` | Placeholder values only. |
| Ignore rules | `.gitignore` | Excludes runtime, cache, database, log, and generated files. |
| Line endings | `.gitattributes` | Normalizes source/script line endings. |
| Backend | `backend/` | Spring Boot 3, Java 21. |
| Frontend | `frontend/` | Vue 3, Vite, TypeScript, Element Plus, Pinia. |
| SQL schema and seed | `sql/schema.sql`, `sql/data.sql` | MySQL 8 initialization. |
| Windows no-Docker guide | `docs/windows-no-docker.md` | D drive layout, local MySQL/Redis, PowerShell startup. |
| Main deploy templates | `deploy/docker-compose.yml`, `deploy/nginx*.conf` | Local and server deployment examples. |
| Demo Range deploy templates | `deploy/demo-range/` | Isolated demo target, WAF, ZAP baseline, Trivy, bridge. |
| API docs | `docs/api.md` | Endpoint summary and request/response examples. |
| Database docs | `docs/database.md` | Table groups, flow, initialization, verification SQL. |
| Deploy docs | `docs/deploy.md` | Local, production, Demo Range deployment. |
| Test report | `docs/test-report.md` | Validation commands and acceptance workflow. |
| User manual | `docs/user-manual.md` | Ten-minute demo, screenshot list, common bugs. |
| Screenshot assets | `docs/screenshots/` | Captured page screenshots and `manifest.json` inventory. |
| Handover guide | `docs/handover.md` | This delivery guide. |
| Upstream/license record | `docs/upstream.md` | Upstream module roles, SHAs, license notes, adapter mappings. |
| Final report | `docs/final-report.md` | Delivery summary and validation status. |

## New Environment Bring-Up

Windows laptop no-Docker path:

1. Put source under `D:\CyberFusion\00-cyberfusion-platform`.
2. Put runtime data under `D:\CyberFusion\Environment\cyberfusion-platform`.
3. Install Java 21, Maven, Node.js with pnpm, MySQL 8 server/client, and a Redis-compatible Windows service.
4. Start MySQL and Redis as local or reachable services; do not start Docker for the Windows path.
5. Set `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, and `CYBERFUSION_ENV_ROOT` in the current PowerShell session.
6. Run `.\scripts\win\run-dev.ps1` from the source root.
7. Run `.\scripts\win\dev-doctor.ps1` after the app starts.

macOS/Linux Compose path:

1. Install Java 21, Maven, Node.js with pnpm, Docker Desktop or Docker Engine, and MySQL client.
2. Copy `.env.example` to an Environment-managed `.env` outside source. Replace placeholders locally.
3. Start MySQL/Redis with `deploy/docker-compose.yml`.
4. Apply `sql/schema.sql` and `sql/data.sql`.
5. Start backend with `SERVER_PORT=18080` and Environment-managed database variables.
6. Start frontend on `127.0.0.1:5174`.
7. Open the app and run the acceptance cases in `docs/user-manual.md`.

## Final Verification Commands

Run from `00-cyberfusion-platform`:

```sh
git status --short
mvn -pl backend test
pnpm --dir frontend build
pnpm --dir frontend typecheck
docker compose -f deploy/docker-compose.yml config
docker compose -f deploy/demo-range/docker-compose.yml config
scripts/smoke/check-release-safety.sh
scripts/smoke/run-acceptance.sh --dry-run
```

Windows no-Docker verification replaces the Compose config checks with:

```powershell
$env:DB_HOST = "127.0.0.1"
$env:DB_PORT = "3306"
$env:DB_NAME = "cyberfusion_soc"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "replace-with-local-db-password"
$env:CYBERFUSION_ENV_ROOT = "D:\CyberFusion\Environment\cyberfusion-platform"
.\scripts\win\prepare-d-drive.ps1
.\scripts\win\verify-no-docker.ps1 -PreStart
.\scripts\win\run-dev.ps1
.\scripts\win\verify-no-docker.ps1 -PostStart
```

If the source directory is not a Git repository, record the `git status` failure and list changed files manually.

## Interface Smoke Test

Preferred live smoke test:

```sh
pnpm --dir frontend test:e2e
```

Expected coverage:

- Login and dashboard.
- SOC pages, including Demo Range and Detection Rule Center.
- API smoke for import, alert detail, ticket conversion, report generation, notification dry-run, rules, hits, and adapter mappings.

If Playwright cannot bind localhost in a restricted sandbox, run it in a local terminal with the backend on `127.0.0.1:18080` and the frontend proxy on `127.0.0.1:5174`.

Optional page screenshot smoke:

```sh
pnpm --dir frontend exec playwright test tests/e2e/release-pages.spec.ts
```

If the backend is running on a non-default local port, pass the proxy target without editing source:

```sh
VITE_API_PROXY_TARGET=http://127.0.0.1:18082 pnpm --dir frontend exec playwright test tests/e2e/release-pages.spec.ts
```

The screenshot smoke opens `/login`, `/showcase`, `/soc/demo-range`, `/soc/alerts`, `/soc/policies`, `/soc/reports`, `/client/workbench`, and `/client/local-range?ip=10.20.1.15&host=prod-app-01&os=Linux`. Screenshots are written to `docs/screenshots/acceptance-*.png`.

Release-candidate runtime note:

- Run the smoke scripts against a backend process started from the current source checkout. A stale backend can still expose older adapter behavior, such as ZAP demo findings failing before the ZAP asset IP normalization fix is loaded.
- Apply the current `sql/schema.sql` and `sql/data.sql` before acceptance. Read-only local check policy endpoints can fall back to built-in safe defaults, but release acceptance still expects active DB-backed policies and active adapter profiles from the seed data.
- Keep database passwords in an Environment-managed shell or secret store. Do not place them in source files or command examples. Before final acceptance, verify that exported `DB_PASSWORD` matches the active MySQL service; a wrong value can still let the backend start, then fail on the first login or policy query.

## Operator Maintenance Notes

Administrators and security engineers maintain delivery-critical demo behavior from these pages:

| Area | Path | Maintenance action | Boundary |
| --- | --- | --- | --- |
| Local check policy | `/soc/policies` -> `本机检查策略` | Add/edit/publish only read-only argv command policies. | Employees still submit only `commandKey`; backend validates and uses `ProcessBuilder` argv, not shell strings. |
| Event adapter mapping | `/soc/policies` -> `事件适配映射` | Maintain WAF/ZAP/Trivy/Wazuh/Suricata/Zeek field mapping, severity mapping, dedup key, and alert-link preview. | No scripts, expressions, SQL, shell commands, external calls, or production WAF/IDS rule push. |
| Demo data | `/soc/demo-range` or `POST /api/soc/demo-range/batches/import` | Import the fixed offline Demo Range batch for customer demos. | Local static evidence only; no ZAP active scan, no public target, no Docker volume reset. |
| Reports | `/soc/reports` | Generate `security_validation` report for a batch. | Report uses existing batch data and dry-run notification logs. |
| Notifications | `/soc/settings` notification logs | Verify dry-run notification history. | No real sender is enabled by default; real credentials must stay outside source. |

## Security Review Checklist

- `.env` files are not in source.
- `.DS_Store`, logs, caches, generated builds, reports, and test-results are not packaged.
- MySQL/Redis volumes are outside source.
- Uploads and backend logs are under Environment.
- Demo Range runtime root is outside source.
- `docs/screenshots/` contains delivery screenshots only, not runtime logs or customer data.
- No real API token, private key, customer export, or production credential is committed.
- Notification remains dry-run unless a future production sender is configured outside source.
- Docker Demo Range binds to localhost or private network only.

## Operational Boundaries

- Do not run `docker compose down -v` unless data deletion is explicitly approved.
- Do not scan third-party or public targets.
- Do not enable ZAP Full Scan by default.
- Do not connect the bridge to production endpoints without an approved token and explicit customer authorization.
- Do not copy runtime evidence back into source.

## Handover Acceptance

Handover is complete when:

1. README can bring up a fresh local environment.
2. Backend tests pass or any blocker is documented.
3. Frontend build passes.
4. Windows no-Docker PowerShell verification passes, or the selected macOS/Linux Compose config validation passes.
5. Ten-minute demo path runs against local demo data.
6. Screenshot checklist has been captured or assigned.
7. Security review checklist is clean or residual risks are documented.
8. Customer receives source, docs, SQL, deploy templates, and validation report without runtime secrets or data.

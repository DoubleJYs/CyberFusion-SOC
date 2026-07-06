# Common Bugs And Runtime Triage

Date: 2026-06-22

Scope: CyberFusion SOC local development and demo runtime under `00-cyberfusion-platform`.

This document records repeatable triage steps for startup, schema, seed, password, and smoke-test issues. The steps are read-only unless they explicitly say to apply SQL. For Windows delivery, use the no-Docker D drive path in `docs/windows-no-docker.md`: source under `D:\CyberFusion\00-cyberfusion-platform`, runtime data under `D:\CyberFusion\Environment\cyberfusion-platform`, local MySQL 8, and local Redis. Do not delete Docker volumes, do not run `docker compose down -v`, and do not write real secrets into source.

## First Command

Run the read-only runtime doctor:

```sh
scripts/smoke/dev-doctor.sh --base-url http://127.0.0.1:5174 --api-base-url http://127.0.0.1:18080/api
```

Windows:

```powershell
.\scripts\win\dev-doctor.ps1 -BaseUrl http://127.0.0.1:5174 -ApiBaseUrl http://127.0.0.1:18080/api
```

The doctor checks:

- Frontend and backend listening ports.
- Backend Java process start time.
- `GET /api/health`.
- Required SOC tables.
- Key menu and permission seed rows.
- Frontend `/api` proxy reaches the backend on `18080`.
- Admin `/auth/me` includes current SOC menus and permissions.
- Employee account receives `403` for SOC-only policy, incident, and correlation-rule APIs.

Windows no-Docker diagnostics require local `mysql.exe` in `PATH`. macOS/Linux Docker-backed diagnostics can use the shell smoke scripts. In both modes, `DB_PASSWORD` is required for SQL authentication and must come from the local environment, not from source files.

## Backend Is Not Listening

Symptom:

- Browser shows `ERR_CONNECTION_REFUSED`.
- Smoke reports `curl: (7) Failed to connect`.
- `scripts/smoke/dev-doctor.sh` says backend is not listening on port `18080`.

Confirmed example from 2026-06-21:

- The incident/correlation tables existed, but both `5174` and `18080` were not listening.
- Restarting backend from current source and frontend with the correct proxy closed the live smoke blocker.

Triage:

```sh
lsof -nP -iTCP:18080 -sTCP:LISTEN
```

Start backend from the current source checkout:

```sh
export CYBERFUSION_ENV_ROOT="$HOME/Environment/cyberfusion-platform"
export DB_PASSWORD="your-local-container-password"
export SERVER_PORT=18080
scripts/mac/backend-dev.sh
```

Windows:

```powershell
$env:DB_PASSWORD = "your-local-mysql-password"
$env:SERVER_PORT = "18080"
.\scripts\win\backend-dev.ps1
```

Do not write the real DB password into README, docs, SQL, scripts, or `.env` files committed to source.

When starting the frontend for the local `18080` backend, prefer the project script:

```sh
scripts/mac/frontend-dev.sh
```

The script defaults to `FRONTEND_PORT=5174`, `SERVER_PORT=18080`, and `VITE_API_PROXY_TARGET=http://127.0.0.1:18080`. If you start Vite manually, set the proxy target explicitly:

```sh
VITE_API_PROXY_TARGET=http://127.0.0.1:18080 pnpm --dir frontend dev --host 127.0.0.1 --port 5174
```

Without this, Vite defaults to `127.0.0.1:8080`, which can make pages look loaded while API calls fail.

Doctor signal:

- `frontend proxy /api/health reaches backend on port 18080` means the current browser-facing dev server is proxying API calls correctly.
- If the process environment does not expose `VITE_API_PROXY_TARGET`, the proxied `/api/health` check is still the runtime proof.

## Stale Backend Process

Symptom:

- Source code contains a controller or endpoint, but OpenAPI or the browser still behaves as if it does not exist.
- Login works, but a new API returns `500` or missing-route behavior.
- Smoke passes source-route checks but fails live API checks.

Triage:

```sh
scripts/smoke/dev-doctor.sh --api-base-url http://127.0.0.1:18080/api
lsof -nP -iTCP:18080 -sTCP:LISTEN
ps -p <PID> -o pid,lstart,command
```

If the Java process was started before the latest Maven build or code change, stop only that backend process and restart it from the current checkout. Do not delete database volumes.

## Missing Tables Or Old Schema

Symptom:

- API returns `500`.
- Backend logs include messages such as `Unknown column` or `Table ... doesn't exist`.
- `GET /api/health` reports `schema: DOWN` with missing tables.
- Incident, policy, adapter, local-check, or risk APIs fail after a feature was added.

Windows no-Docker non-destructive repair:

```powershell
cd D:\CyberFusion\00-cyberfusion-platform
$env:DB_HOST = "127.0.0.1"
$env:DB_PORT = "3306"
$env:DB_NAME = "cyberfusion_soc"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "your-local-mysql-password"
.\scripts\win\init-local-db.ps1
```

macOS/Linux Docker-backed non-destructive repair:

```sh
MYSQL_PWD="$DB_PASSWORD" docker exec -e MYSQL_PWD -i cyberfusion-platform-mysql-1 \
  mysql --default-character-set=utf8mb4 -uroot < sql/schema.sql
MYSQL_PWD="$DB_PASSWORD" docker exec -e MYSQL_PWD -i cyberfusion-platform-mysql-1 \
  mysql --default-character-set=utf8mb4 -uroot cyberfusion_soc < sql/data.sql
MYSQL_PWD="$DB_PASSWORD" docker exec -e MYSQL_PWD -i cyberfusion-platform-mysql-1 \
  mysql --default-character-set=utf8mb4 -uroot cyberfusion_soc < scripts/sql/apply-latest-menu-and-policy-seed.sql
```

These commands are intended to be idempotent for the demo database. They create or patch missing objects and seed rows. They must not be replaced with `docker compose down -v`.

## Admin Password Mismatch

Symptom:

- `admin / Admin@123456` fails.
- Smoke login fails for admin but the backend is otherwise running.
- A local database was manually changed to use `admin123`.

Current behavior:

- `sql/data.sql` inserts demo password hashes only for new users.
- Re-running `sql/data.sql` does not overwrite an existing `sys_user.password_hash`.
- Smoke scripts support `CYBERFUSION_ADMIN_PASSWORD`.
- If `CYBERFUSION_ADMIN_PASSWORD` is not set, admin login also tries the local demo fallback `admin123`.

Recommended command:

```sh
CYBERFUSION_ADMIN_PASSWORD='your-local-demo-admin-password' \
scripts/smoke/check-visibility.sh --base-url http://127.0.0.1:5174 --api-base-url http://127.0.0.1:18080/api
```

Use the system user-management page to reset local demo passwords when possible. Do not store the password in source files.

## Windows MySQL Password Rejected

Symptom:

- `.\scripts\win\init-local-db.ps1` or `.\scripts\win\dev-doctor.ps1` reports MySQL authentication failure.
- `GET /api/health` reports `database: DOWN`.
- Login returns `500` even though backend and frontend ports are listening.

Triage:

```powershell
cd D:\CyberFusion\00-cyberfusion-platform
$env:DB_PASSWORD = "your-local-mysql-password"
mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u root -p cyberfusion_soc -e "SELECT 1;"
.\scripts\win\dev-doctor.ps1 -BaseUrl http://127.0.0.1:5174 -ApiBaseUrl http://127.0.0.1:18080/api
```

If the SQL probe fails, fix the Windows MySQL service user/password first. Do not write the password into source files. If the database is newly created or missing tables, rerun `.\scripts\win\init-local-db.ps1` after authentication works.

## Docker MySQL Root Password Rejected

This section applies to the macOS/Linux Docker-backed local path and older historical Docker troubleshooting. It does not apply to the current Windows no-Docker D drive path.

Symptom:

- Applying `sql/schema.sql` or `sql/data.sql` through Docker returns `ERROR 1045 (28000): Access denied for user 'root'@'localhost'`.
- `scripts/smoke/dev-doctor.sh` can see the MySQL container but cannot verify tables or seed rows.
- Docker may still show the container as `healthy`. Do not treat `mysqladmin ping` or Docker health as proof that SQL authentication works; verify with a real `SELECT 1` query.
- Live smoke may also report that the backend is not listening on `18080`, because the backend should not be started against the primary volume until a valid current DB credential is available.
- Current `dev-doctor` treats a reachable container plus failed SQL query as a failed check, not a soft warning. This is intentional because schema, seed, menu, and permission reconciliation require real SQL authentication.

Observed main-volume blocker on 2026-06-22:

- `cyberfusion-platform-mysql-1` was running and healthy on `127.0.0.1:3306`.
- The host machine did not have a local `mysql` command, but that is not a blocker because the smoke scripts can use the Docker MySQL client.
- `DB_PASSWORD` was not set in the current shell.
- The current container `MYSQL_ROOT_PASSWORD` value was not accepted by the existing data volume. A real SQL probe returned `ERROR 1045`.
- The data volume contained physical table files for `soc_incident_cluster`, `soc_incident_evidence`, `soc_correlation_rule`, `soc_risk_scoring_policy`, `soc_asset_risk_snapshot`, and `soc_asset_risk_factor`, but physical files are not enough to prove SQL compatibility or seed correctness.
- No Environment-local `.env` or application config file was found under `/Users/zhangjiyan/Environment/cyberfusion-platform`.

Resolution used on 2026-06-22:

- The stopped container object was removed so Docker Compose could recreate it; this did not delete the host data directory.
- The unreadable data directory was preserved by renaming it to `/Users/zhangjiyan/Environment/cyberfusion-platform/mysql-auth-blocked-20260622-154059`.
- A fresh `/Users/zhangjiyan/Environment/cyberfusion-platform/mysql` directory was initialized by Docker Compose with the local runtime password supplied only as an environment variable.
- A pre-refresh logical backup of the fresh primary database was written to `/Users/zhangjiyan/Environment/cyberfusion-platform/backups/main-before-schema-refresh-20260622-154223.sql.gz`.
- Current `sql/schema.sql` and `sql/data.sql` were applied.
- SQL-level checks then confirmed the six required SOC tables and five key menu paths.
- `GET /api/health`, `dev-doctor`, `check-visibility`, and `run-acceptance --dry-run` passed against the new primary database.

This is a non-destructive replacement of the active local development database directory: it preserves the unreadable old directory, but it does not migrate rows from the old directory because the old directory could not be SQL-authenticated. Use the archived directory only for forensic recovery or manual credential repair.

Triage:

1. Confirm you are using the active local container password from your runtime environment, not a value copied into source.
2. Do not inspect or print all container environment variables in shared logs because that can expose credentials.
3. Do not run `docker compose down -v` to solve a password mismatch.
4. If `DB_PASSWORD` is missing, export it only in your local shell or password manager-backed terminal session. Do not commit it to README, docs, scripts, SQL, or `.env` files inside the source tree.
5. If live smoke must proceed without changing the existing data volume, start an isolated local smoke database on a different port, initialize it from `sql/schema.sql` and `sql/data.sql`, and point the backend at that port for validation only.

Non-secret checks:

```sh
docker ps --filter name=cyberfusion-platform-mysql-1 --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
docker exec cyberfusion-platform-mysql-1 sh -c 'for t in soc_incident_cluster soc_incident_evidence soc_correlation_rule soc_risk_scoring_policy soc_asset_risk_snapshot soc_asset_risk_factor; do ls /var/lib/mysql/cyberfusion_soc/${t}.* >/dev/null 2>&1 && echo "TABLE_FILE_PRESENT $t" || echo "TABLE_FILE_MISSING $t"; done'
```

These only prove the container and table files exist. They do not prove schema compatibility, seed correctness, or credentials.

Real SQL authentication proof:

```sh
MYSQL_PWD="$DB_PASSWORD" docker exec -e MYSQL_PWD cyberfusion-platform-mysql-1 \
  mysql --default-character-set=utf8mb4 -h127.0.0.1 -uroot -N -e 'SELECT 1' cyberfusion_soc
```

If this returns `ERROR 1045`, stop and resolve the local credential source first. If `DB_PASSWORD` is not set, treat the check as blocked; do not guess the password, reset root, clear the database, or apply SQL through a guessed password. Once authentication works, export a backup into `~/Environment` before applying idempotent schema or seed SQL.

After SQL authentication works, run the non-destructive main-volume sequence:

```sh
mkdir -p "$HOME/Environment/cyberfusion-platform/backups"
MYSQL_PWD="$DB_PASSWORD" docker exec -e MYSQL_PWD cyberfusion-platform-mysql-1 \
  mysqldump --single-transaction --routines --events --default-character-set=utf8mb4 -uroot cyberfusion_soc \
  | gzip > "$HOME/Environment/cyberfusion-platform/backups/main-before-schema-refresh-$(date +%Y%m%d-%H%M%S).sql.gz"

MYSQL_PWD="$DB_PASSWORD" docker exec -e MYSQL_PWD -i cyberfusion-platform-mysql-1 \
  mysql --default-character-set=utf8mb4 -uroot < sql/schema.sql
MYSQL_PWD="$DB_PASSWORD" docker exec -e MYSQL_PWD -i cyberfusion-platform-mysql-1 \
  mysql --default-character-set=utf8mb4 -uroot cyberfusion_soc < sql/data.sql

scripts/smoke/dev-doctor.sh --base-url http://127.0.0.1:5174 --api-base-url http://127.0.0.1:18080/api
```

Example local-smoke pattern:

```sh
docker run -d --name cyberfusion-live-smoke-mysql \
  -e MYSQL_ROOT_PASSWORD="<local-demo-password>" \
  -e MYSQL_DATABASE=cyberfusion_soc \
  -p 127.0.0.1:3307:3306 mysql:8.4

docker exec -e MYSQL_PWD="<local-demo-password>" -i cyberfusion-live-smoke-mysql \
  mysql --default-character-set=utf8mb4 -uroot cyberfusion_soc < sql/schema.sql

docker exec -e MYSQL_PWD="<local-demo-password>" -i cyberfusion-live-smoke-mysql \
  mysql --default-character-set=utf8mb4 -uroot cyberfusion_soc < sql/data.sql

DB_PORT=3307 DB_PASSWORD="<local-demo-password>" SERVER_PORT=18080 scripts/mac/backend-dev.sh
```

This pattern is for local smoke only. It does not delete the original MySQL volume and must not be used as production credential management.

## Smoke Script Says Connection Refused But Manual Curl Works

Symptom:

- A smoke script reports `curl: (7) Failed to connect`.
- A direct approved `curl http://127.0.0.1:5174` or `curl http://127.0.0.1:18080/api/health` works in the same session.

Likely cause:

- The execution sandbox blocks `curl` calls spawned inside a script.

Triage:

- Re-run the same smoke script with approved local-only execution.
- Confirm the URLs are `127.0.0.1` or `localhost`.
- Do not treat this as a product regression until `dev-doctor` or direct local probes also fail.

## Live Smoke Order

Run from `00-cyberfusion-platform` after backend and frontend are both running:

```sh
scripts/smoke/dev-doctor.sh --base-url http://127.0.0.1:5174 --api-base-url http://127.0.0.1:18080/api
scripts/smoke/check-release-safety.sh
scripts/smoke/check-visibility.sh --base-url http://127.0.0.1:5174 --api-base-url http://127.0.0.1:18080/api
scripts/smoke/run-acceptance.sh --dry-run
```

Expected stable results on 2026-06-22:

- `dev-doctor.sh`: 15 pass, 0 warn, 0 fail.
- `check-release-safety.sh`: 10 pass, 0 fail.
- `check-visibility.sh`: 44 pass, 0 fail.
- `run-acceptance.sh --dry-run`: 55 pass, 0 fail.

## Demo Data Accumulation

Symptom:

- Repeated manual demos or smoke runs leave extra `security_validation` reports, dry-run notification logs, incident evidence, or tickets.
- The demo pages still work, but counts are higher than expected.

Safe first step:

```sh
scripts/smoke/cleanup-demo-data.sh
```

This is dry-run by default. It reports only rows scoped to the known demo/smoke batch IDs and linked demo records.

Confirmed cleanup requires an explicit flag:

```sh
scripts/smoke/cleanup-demo-data.sh --batch-id DEMO-RANGE-ACCEPTANCE-SMOKE --confirm
```

Safety rules:

- The script does not delete Docker volumes.
- The script does not touch rows unless they match demo/smoke batch IDs, demo-range CVEs, linked demo incident clusters, linked demo tickets, security-validation reports, or dry-run notification logs.
- Run dry-run output first and keep the default batch list unless you have a specific demo batch to remove.

## Security Boundaries

- Do not execute public scans.
- Do not run attack payloads.
- Do not execute arbitrary shell commands through the employee local-check surface.
- Do not send real email, webhook, Feishu, WeCom, DingTalk, Slack, WAF, IDS, SIEM, or EDR traffic.
- Do not delete Docker volumes.
- Do not commit `.env`, runtime logs, database files, tokens, private keys, or customer data.

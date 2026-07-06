# Deploy Notes

## Paths

- Source: `/Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/00-cyberfusion-platform`
- Runtime: `/Users/zhangjiyan/Environment/cyberfusion-platform`
- Uploads: `/Users/zhangjiyan/Environment/cyberfusion-platform/uploads`
- Logs: `/Users/zhangjiyan/Environment/cyberfusion-platform/logs`
- Backups: `/Users/zhangjiyan/Environment/cyberfusion-platform/backups`
- Certificates and real secrets: Environment or platform secret manager only.

## Local Services

Recommended restart entrypoints:

```sh
cd /Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/00-cyberfusion-platform
export DB_PASSWORD="replace-with-local-db-password"
scripts/mac/run-dev.sh
```

```powershell
cd D:\CyberFusion\00-cyberfusion-platform
$env:DB_PASSWORD = "replace-with-local-db-password"
.\scripts\win\run-dev.ps1
```

The macOS/Linux entrypoint uses Docker Compose for local MySQL/Redis. The Windows entrypoint is the no-Docker path: put the project under `D:\CyberFusion\00-cyberfusion-platform`, start MySQL 8 and Redis-compatible services first, make sure `mysql.exe` is in `PATH`, then run `scripts\win\run-dev.ps1`. Both entrypoints run the environment check and local VM compatibility check before starting the backend and frontend. They also create runtime folders under `CYBERFUSION_ENV_ROOT` and default to `~/Environment/cyberfusion-platform` on macOS/Linux or `D:\CyberFusion\Environment\cyberfusion-platform` on Windows.

To reuse an already verified machine without running the checks every time:

```sh
CYBERFUSION_SKIP_COMPAT_CHECK=1 scripts/mac/run-dev.sh
```

```powershell
.\scripts\win\run-dev.ps1 -SkipCompatCheck
```

Windows no-Docker database initialization:

```powershell
$env:DB_HOST = "127.0.0.1"
$env:DB_PORT = "3306"
$env:DB_NAME = "cyberfusion_soc"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "replace-with-local-db-password"

.\scripts\win\init-local-db.ps1
```

The initializer applies `sql/schema.sql`, `sql/data.sql`, and `scripts/sql/apply-latest-menu-and-policy-seed.sql`. See `docs/windows-no-docker.md` for the complete Windows checklist.

macOS/Linux Docker local services:

```sh
export LOCAL_DB_PASSWORD="replace-with-local-db-password"

cd /Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/00-cyberfusion-platform/deploy
DB_PASSWORD="$LOCAL_DB_PASSWORD" docker compose config
DB_PASSWORD="$LOCAL_DB_PASSWORD" docker compose up -d
```

Initialize data:

```sh
cd /Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/00-cyberfusion-platform
mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u root -p"$LOCAL_DB_PASSWORD" < sql/schema.sql
mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u root -p"$LOCAL_DB_PASSWORD" cyberfusion_soc < sql/data.sql
```

Run backend:

```sh
cd /Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/00-cyberfusion-platform/backend
SERVER_PORT=18080 \
DB_NAME=cyberfusion_soc \
DB_PASSWORD="$LOCAL_DB_PASSWORD" \
APP_UPLOAD_BASE_DIR=/Users/zhangjiyan/Environment/cyberfusion-platform/uploads \
APP_CORS_ALLOWED_ORIGIN_PATTERNS=http://127.0.0.1:5174,http://localhost:5174 \
mvn spring-boot:run
```

Run frontend:

```sh
cd /Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/00-cyberfusion-platform/frontend
VITE_API_PROXY_TARGET=http://127.0.0.1:18080 pnpm dev --host 127.0.0.1 --port 5174
```

## Production Checklist

- Use Docker Compose or platform-managed MySQL/Redis volumes outside source.
- Put Nginx certificates outside source.
- Set strict CORS origins.
- Keep upload limits enabled.
- Keep rate limiting enabled.
- Run `scripts/mac/backup-runtime.sh` and `scripts/mac/restore-runtime.sh` against Environment paths.
- Re-run backend tests, frontend build, Docker Compose config validation, and secret scan before handoff.

## Integration Programs

Curated integration-side programs are now kept inside `integrations/` at the root of `00-cyberfusion-platform`. This gives demos, smoke tests, and handoff work one local project boundary instead of requiring operators to jump between sibling `01-16` directories.

Use `integrations/catalog.json` as the source of truth for local paths and CyberFusion API entrypoints. The same metadata is exposed through the read-only platform API:

```text
GET /api/soc/integrations/catalog
```

The main import endpoint remains:

```text
POST /api/soc/external-events/cyberfusion/import
```

Special-purpose safe helper endpoints remain:

- `POST /api/soc/external-events/cyberchef/analyze`
- `POST /api/soc/external-events/shuffle/demo-notification`
- `GET /api/soc/settings/wazuh/check`

Do not copy runtime data, databases, generated logs, credentials, certificates, Docker volumes, or real `.env` files into `integrations/` or any other source directory.

## Docker Demo Range

The lightweight Demo Range lives under `deploy/demo-range` and is separate from the CyberFusion SOC core runtime. It is for isolated local or private-network demonstrations only. It must not be exposed to the public internet, must not run ZAP Full Scan by default, and must not scan third-party targets.

Services:

- `demo-target`: self-developed `demo-enterprise-portal` container for controlled enterprise business risk simulation. Juice Shop remains a separate optional future training target and is not the default SOC Demo Range target.
- `waf-gateway`: ModSecurity CRS Nginx gateway in front of `demo-target`, bound to localhost by default.
- `zap-baseline`: optional ZAP baseline profile that writes reports only.
- `trivy`: optional Trivy profile that writes JSON output only.
- `demo-event-bridge`: Python stdlib bridge that converts demo portal/WAF/ZAP/Trivy output to CyberFusion import payloads; default mode is dry-run.

`demo-enterprise-portal` is intentionally not a real vulnerable application. It exposes controlled simulations and writes structured JSONL evidence only. It does not execute commands, does not persist uploaded files, does not read real customer data, does not connect to external targets, and does not provide reusable attack payloads.

Supported modes:

- `safe`: compliant business behavior and low-severity evidence.
- `vulnerable`: risk-observed simulation and evidence only, with no exploitable behavior.
- `protected`: blocked or protected behavior with WAF/bridge-collectable evidence.

Supported cases:

- `DEMO-ACCESS-001`: access-control risk simulation.
- `DEMO-UPLOAD-001`: upload-policy risk simulation.
- `DEMO-INPUT-001`: input-validation risk simulation.
- `DEMO-HEADER-001`: security-response-header risk simulation.
- `DEMO-DEPENDENCY-001`: dependency-component risk simulation.

Prepare runtime folders outside source:

```sh
export DEMO_RANGE_RUNTIME_ROOT="$HOME/Environment/cyberfusion-platform/demo-range"
mkdir -p "$DEMO_RANGE_RUNTIME_ROOT"/{logs/demo-target,logs/waf,logs/nginx,zap,trivy,trivy-cache}
```

```powershell
$env:DEMO_RANGE_RUNTIME_ROOT = "D:\CyberFusion\Environment\cyberfusion-platform\demo-range"
New-Item -ItemType Directory -Force `
  "$env:DEMO_RANGE_RUNTIME_ROOT\logs\demo-target", `
  "$env:DEMO_RANGE_RUNTIME_ROOT\logs\waf", `
  "$env:DEMO_RANGE_RUNTIME_ROOT\logs\nginx", `
  "$env:DEMO_RANGE_RUNTIME_ROOT\zap", `
  "$env:DEMO_RANGE_RUNTIME_ROOT\trivy", `
  "$env:DEMO_RANGE_RUNTIME_ROOT\trivy-cache"
```

Validate the compose file without starting containers:

```sh
docker compose -f deploy/demo-range/docker-compose.yml config
```

Start only the target and WAF gateway:

```sh
docker compose -f deploy/demo-range/docker-compose.yml up -d demo-target waf-gateway
```

Default WAF URL:

```text
http://127.0.0.1:18081
```

Example controlled case URLs:

```text
http://127.0.0.1:18081/case/DEMO-ACCESS-001?mode=protected
http://127.0.0.1:18081/case/DEMO-UPLOAD-001?mode=protected
http://127.0.0.1:18081/case/DEMO-INPUT-001?mode=vulnerable
http://127.0.0.1:18081/case/DEMO-HEADER-001?mode=safe
http://127.0.0.1:18081/case/DEMO-DEPENDENCY-001?mode=vulnerable
```

The demo target writes structured evidence to:

```text
$DEMO_RANGE_RUNTIME_ROOT/logs/demo-target/demo-events.jsonl
```

Local syntax and behavior checks for the demo target:

```sh
node deploy/demo-range/demo-enterprise-portal/server.js --self-test
node --check deploy/demo-range/demo-enterprise-portal/server.js
```

Run optional report-only tools. These commands do not perform ZAP Full Scan and do not target public websites:

```sh
docker compose -f deploy/demo-range/docker-compose.yml --profile scan run --rm zap-baseline
docker compose -f deploy/demo-range/docker-compose.yml --profile scan run --rm trivy
```

Dry-run the bridge. This prints the payloads that would be sent to `POST /api/soc/external-events/cyberfusion/import` and sends nothing:

```sh
docker compose -f deploy/demo-range/docker-compose.yml --profile bridge run --rm demo-event-bridge
```

The bridge reads `$DEMO_RANGE_RUNTIME_ROOT/logs/demo-target/demo-events.jsonl` and converts portal evidence to the existing `sourceType=waf` import format, so WAF alert-linking behavior can be demonstrated without adding a new external source.

To send to a local CyberFusion backend, keep the backend on a private host/localhost, set an API token only in your shell or Environment-managed `.env`, and override the bridge command:

```sh
export CYBERFUSION_API_BASE="http://host.docker.internal:18080/api"
export CYBERFUSION_API_TOKEN=""
export BRIDGE_DRY_RUN=false
docker compose -f deploy/demo-range/docker-compose.yml --profile bridge run --rm demo-event-bridge --send
```

Check logs:

```sh
docker compose -f deploy/demo-range/docker-compose.yml logs --tail=80 waf-gateway
docker compose -f deploy/demo-range/docker-compose.yml --profile bridge run --rm demo-event-bridge --dry-run
```

Stop containers without deleting volumes or runtime output:

```sh
docker compose -f deploy/demo-range/docker-compose.yml stop
```

Clean temporary demo output only after confirming `DEMO_RANGE_RUNTIME_ROOT` points outside the source tree. Do not run `docker compose down -v` for this range.

```sh
printf '%s\n' "$DEMO_RANGE_RUNTIME_ROOT"
rm -rf "$DEMO_RANGE_RUNTIME_ROOT/logs" "$DEMO_RANGE_RUNTIME_ROOT/zap" "$DEMO_RANGE_RUNTIME_ROOT/trivy" "$DEMO_RANGE_RUNTIME_ROOT/trivy-cache"
```

```powershell
Write-Host $env:DEMO_RANGE_RUNTIME_ROOT
Remove-Item -Recurse -Force `
  "$env:DEMO_RANGE_RUNTIME_ROOT\logs", `
  "$env:DEMO_RANGE_RUNTIME_ROOT\zap", `
  "$env:DEMO_RANGE_RUNTIME_ROOT\trivy", `
  "$env:DEMO_RANGE_RUNTIME_ROOT\trivy-cache"
```

Safety boundaries:

- Default exposed port is `127.0.0.1:${WAF_HTTP_PORT:-18081}`.
- Use `.env.example` as a template only; store real `.env` files under Environment or a secret manager.
- Runtime logs, reports, Trivy cache, ZAP output, uploads, and secrets must remain outside source.
- `demo-event-bridge` has no notification sender and never sends real webhooks, mail, Feishu, WeCom, DingTalk, or Slack messages.

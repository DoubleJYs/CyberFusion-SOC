# Deploy

## Local Runtime

This self-developed SOC layer lives under the already runnable Wazuh workspace:

```text
/Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/01-wazuh/sec-wazuh-soc
```

The parent `01-wazuh` files are treated as upstream/runtime Wazuh assets and are not modified by this application startup flow.

```sh
export LOCAL_DB_PASSWORD="replace-with-local-db-password"

cd /Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/01-wazuh/sec-wazuh-soc/deploy
DB_PASSWORD="$LOCAL_DB_PASSWORD" docker compose up -d
```

Runtime data is mounted under:

```text
/Users/zhangjiyan/Environment/sec-wazuh-soc/mysql
/Users/zhangjiyan/Environment/sec-wazuh-soc/redis
/Users/zhangjiyan/Environment/sec-wazuh-soc/uploads
/Users/zhangjiyan/Environment/sec-wazuh-soc/backups
```

Initialize MySQL:

```sh
cd /Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/01-wazuh/sec-wazuh-soc
mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u root -p"$LOCAL_DB_PASSWORD" < sql/schema.sql
mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u root -p"$LOCAL_DB_PASSWORD" sec_wazuh_soc < sql/data.sql
```

Run backend and frontend:

```sh
cd backend
DB_PASSWORD="$LOCAL_DB_PASSWORD" mvn spring-boot:run

cd ../frontend
pnpm install --frozen-lockfile
pnpm dev
```

If default ports are occupied, use isolated local ports:

```sh
export LOCAL_DB_PASSWORD="replace-with-local-db-password"

cd /Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/01-wazuh/sec-wazuh-soc/deploy
DB_PASSWORD="$LOCAL_DB_PASSWORD" DB_PORT=33306 REDIS_PORT=36379 ADMINER_PORT=38081 docker compose up -d

cd ../backend
SERVER_PORT=18080 DB_PORT=33306 REDIS_PORT=36379 DB_PASSWORD="$LOCAL_DB_PASSWORD" \
APP_UPLOAD_BASE_DIR=/Users/zhangjiyan/Environment/sec-wazuh-soc/uploads \
APP_CORS_ALLOWED_ORIGIN_PATTERNS=http://127.0.0.1:5173,http://localhost:5173 \
mvn spring-boot:run

cd ../frontend
VITE_API_PROXY_TARGET=http://127.0.0.1:18080 pnpm dev --host 127.0.0.1 --port 5173
```

## Environment

Use `.env.example` as a shape reference only. Put real `.env`, certificates, keys, logs, and backups in `/Users/zhangjiyan/Environment/sec-wazuh-soc`.

## Application Containers

Use `deploy/docker-compose.app.example.yml` when validating a private deployment that includes MySQL, Redis, the Spring Boot backend, and the Vue/Nginx frontend container. Keep the real `.env` file outside the source tree, for example under `/Users/zhangjiyan/Environment/sec-wazuh-soc/.env`.

```sh
export LOCAL_DB_PASSWORD="replace-with-local-db-password"

cd /Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/01-wazuh/sec-wazuh-soc/deploy
DB_PASSWORD="$LOCAL_DB_PASSWORD" docker compose -f docker-compose.app.example.yml config
DB_PASSWORD="$LOCAL_DB_PASSWORD" docker compose -f docker-compose.app.example.yml build
DB_PASSWORD="$LOCAL_DB_PASSWORD" docker compose -f docker-compose.app.example.yml up -d
DB_PASSWORD="$LOCAL_DB_PASSWORD" docker compose -f docker-compose.app.example.yml ps
```

The example binds the backend to `127.0.0.1:18080` and the frontend Nginx container to `127.0.0.1:18081`. Runtime uploads and backend logs are mounted to:

```text
/Users/zhangjiyan/Environment/sec-wazuh-soc/uploads
/Users/zhangjiyan/Environment/sec-wazuh-soc/logs/backend
```

The frontend container uses `deploy/nginx.container.conf` and proxies `/api/` to the backend service inside the Compose network. For server-level HTTPS termination, adapt `deploy/nginx.https.example.conf` and place real certificates under an Environment or server secret directory. The repository only stores placeholder certificate paths.

## Health Checks

The backend exposes public probe endpoints for deployment platforms and reverse proxies:

```sh
curl -s http://127.0.0.1:18080/api/health/liveness
curl -s http://127.0.0.1:18080/api/health/readiness
```

- `liveness`: process-level check. It should stay `UP` if the API process is responsive.
- `readiness`: dependency check. It returns `UP` only when both MySQL and Redis probes are healthy.

The response intentionally exposes only dependency names, `UP` / `DOWN`, latency, and a sanitized error class/message. It does not return JDBC URLs, Redis URLs, Wazuh URLs, usernames, passwords, tokens, or certificates. These endpoints are excluded from API rate limiting so health probes do not interfere with real traffic.

## Backup And Restore

Back up runtime MySQL and Redis data before upgrades, migrations, demo resets, or delivery handoff. Backup files are written under `/Users/zhangjiyan/Environment/sec-wazuh-soc/backups/runtime` by default and must not be committed.

macOS / Linux:

```sh
cd /Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/01-wazuh/sec-wazuh-soc
DB_PASSWORD="$LOCAL_DB_PASSWORD" scripts/mac/backup-runtime.sh

# Restore can overwrite current runtime data. Redis restore is opt-in.
DB_PASSWORD="$LOCAL_DB_PASSWORD" RESTORE_CONFIRM=YES scripts/mac/restore-runtime.sh \
  /Users/zhangjiyan/Environment/sec-wazuh-soc/backups/runtime/<backup-dir>

DB_PASSWORD="$LOCAL_DB_PASSWORD" RESTORE_CONFIRM=YES RESTORE_REDIS=true scripts/mac/restore-runtime.sh \
  /Users/zhangjiyan/Environment/sec-wazuh-soc/backups/runtime/<backup-dir>
```

Windows PowerShell:

```powershell
$env:DB_PASSWORD = "replace-with-local-db-password"
.\scripts\win\backup-runtime.ps1

# Restore can overwrite current runtime data. Redis restore is opt-in.
.\scripts\win\restore-runtime.ps1 -BackupDir "C:\sec-wazuh-soc\backups\runtime\<backup-dir>" -ConfirmRestore
.\scripts\win\restore-runtime.ps1 -BackupDir "C:\sec-wazuh-soc\backups\runtime\<backup-dir>" -ConfirmRestore -RestoreRedis
```

The scripts intentionally do not store database passwords in backup manifests. The manifest records only project name, timestamp, compose file, database name, and included data categories. A restore operation requires explicit confirmation (`RESTORE_CONFIRM=YES` or `-ConfirmRestore`) to reduce accidental overwrites.

## Wazuh

P0 can run without a live Wazuh Agent. Configure Wazuh Manager / Indexer only when an authorized test environment is available. Wazuh Dashboard remains an advanced native entry and is not embedded into this frontend.

For a local single-node Wazuh runtime with self-signed certificates, keep real values outside source and pass them as environment variables when starting the backend:

```sh
WAZUH_MANAGER_URL=https://127.0.0.1:55050
WAZUH_INDEXER_URL=https://127.0.0.1:9200
WAZUH_MANAGER_USERNAME=example-manager-user
WAZUH_MANAGER_PASSWORD=example-manager-password
WAZUH_INDEXER_USERNAME=example-indexer-user
WAZUH_INDEXER_PASSWORD=example-indexer-password
WAZUH_TLS_VERIFY=false
```

The backend authenticates to Wazuh Manager with the Manager credential pair, obtains a short-lived token, and uses separate Basic authentication for Wazuh Indexer. The browser never receives these values.

## Security Hardening

The backend now ships with P3-ready defaults for security response headers, configurable CORS, and lightweight in-memory API rate limiting. Keep these settings in runtime environment variables or an Environment-local `.env`; do not put production domains, secrets, certificates, or private addresses into source files.

Recommended private deployment values:

```sh
APP_CORS_ALLOWED_ORIGIN_PATTERNS=https://soc.example.com
APP_CORS_ALLOWED_METHODS=GET,POST,PUT,DELETE,PATCH,OPTIONS
APP_CORS_ALLOWED_HEADERS=Authorization,Content-Type,X-Requested-With
APP_CORS_ALLOW_CREDENTIALS=true
APP_RATE_LIMIT_ENABLED=true
APP_RATE_LIMIT_REQUESTS_PER_MINUTE=120
APP_RATE_LIMIT_AUTH_REQUESTS_PER_MINUTE=20
APP_RATE_LIMIT_MAX_TRACKED_CLIENTS=5000
```

Defaults remain suitable for local development on `localhost` / `127.0.0.1`. Production should narrow CORS to the deployed frontend origin and place HTTPS/Nginx termination in front of the backend. The backend emits `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, and a conservative API content security policy.

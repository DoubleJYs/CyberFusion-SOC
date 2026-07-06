# Windows No-Docker Quick Start

This guide is for running the current `00-cyberfusion-platform` project on a Windows laptop without Docker. Put the project and runtime data on D drive, keep runtime data outside the source tree, and leave the macOS/Linux Docker-based local path unchanged.

## Required Software

- Windows 10/11 with PowerShell 5.1 or newer.
- JDK 21 or newer in `PATH`.
- Maven 3.9 or newer in `PATH`.
- Node.js 20 or newer in `PATH`.
- pnpm 11 or newer in `PATH`.
- MySQL 8 server running locally or on a reachable host.
- MySQL 8 client `mysql.exe` in `PATH`.
- Redis-compatible server running locally or on a reachable host.

CyberFusion does not start MySQL or Redis in Windows no-Docker mode. Start those two services first, then run the platform scripts.

## D Drive Layout

Use this Windows layout:

```text
D:\CyberFusion\00-cyberfusion-platform
D:\CyberFusion\Environment\cyberfusion-platform
```

The first path is the source project. The second path is runtime data for uploads, logs, backups, and local VM evidence. Do not put runtime data under the source project.

Recommended checkout or copy target:

```powershell
New-Item -ItemType Directory -Force D:\CyberFusion | Out-Null
cd D:\CyberFusion
# Copy this project folder here, or clone your project repository as:
# git clone <your-repo-url> 00-cyberfusion-platform
cd D:\CyberFusion\00-cyberfusion-platform
```

The Windows startup scripts intentionally fail fast when the project is started from `C:`. Move the project to `D:\CyberFusion\00-cyberfusion-platform` instead of running it from Desktop, Downloads, or `C:\Users\...`.

Create and verify the D drive runtime folders before startup:

```powershell
.\scripts\win\prepare-d-drive.ps1
```

The preparation script creates `uploads`, `logs\backend`, `backups\runtime`, `local-vm`, dependency caches, `tmp`, package output, and package staging under `D:\CyberFusion\Environment\cyberfusion-platform`, then verifies that MySQL and Redis ports are reachable. Use `-SkipServiceCheck` only when you want to create folders before starting those services.

## Database

Create or use a MySQL user that can create/update the `cyberfusion_soc` database. The seed SQL stores BCrypt hashes; the default local demo account is:

```text
admin / Admin@123456
```

Set the database password in the current PowerShell session. Do not write real passwords into source files.

```powershell
cd D:\CyberFusion\00-cyberfusion-platform

$env:DB_HOST = "127.0.0.1"
$env:DB_PORT = "3306"
$env:DB_NAME = "cyberfusion_soc"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "replace-with-local-db-password"
```

Initialize or refresh local tables and seed rows:

```powershell
.\scripts\win\init-local-db.ps1
```

The initializer applies these files in order:

1. `sql/schema.sql`
2. `sql/data.sql`
3. `scripts/sql/apply-latest-menu-and-policy-seed.sql`

The latest patch is intentionally rerunnable. It updates menus, permissions, SOC entries, and client-side menu visibility without truncating business data.

The bundled SQL files create and seed the fixed database name `cyberfusion_soc`. If you need a different database name, prepare that database manually and start the platform with `.\scripts\win\run-dev.ps1 -SkipDbInit`.

## Runtime Root

Keep uploads, logs, backups, and local VM evidence outside the repository:

```powershell
$env:CYBERFUSION_ENV_ROOT = "D:\CyberFusion\Environment\cyberfusion-platform"
```

The startup script creates these folders when missing:

- `uploads`
- `logs\backend`
- `backups`
- `local-vm`
- `caches\maven-repository`
- `caches\pnpm-store`
- `caches\npm`
- `tmp`
- `packages`
- `package-staging`

The Windows scripts pass Maven `-Dmaven.repo.local` and pnpm `--store-dir`, set npm cache, and set process `TEMP`/`TMP` plus Java `java.io.tmpdir` so dependency caches and temporary files stay under D drive instead of the default user profile on C drive.

## One-Command Start

After MySQL and Redis are running:

```powershell
$env:DB_HOST = "127.0.0.1"
$env:DB_PORT = "3306"
$env:DB_NAME = "cyberfusion_soc"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "replace-with-local-db-password"
$env:CYBERFUSION_ENV_ROOT = "D:\CyberFusion\Environment\cyberfusion-platform"
.\scripts\win\start-no-docker.ps1
```

The one-command script prepares D drive folders, runs pre-start verification, starts backend/frontend, then runs post-start verification.

For phased startup or troubleshooting:

```powershell
.\scripts\win\prepare-d-drive.ps1
.\scripts\win\run-dev.ps1 -FrontendPort 5174 -ServerPort 18080
```

The script checks Java, Maven, Node.js, pnpm, and `mysql.exe`, verifies that MySQL and Redis ports are reachable, initializes the database unless `-SkipDbInit` is passed, then opens backend and frontend PowerShell windows.
By default, `run-dev.ps1` uses `D:\CyberFusion\Environment\cyberfusion-platform` when `CYBERFUSION_ENV_ROOT` is not set.
If the project itself is not under D drive, the script stops before creating files or starting services.

Useful variants:

```powershell
.\scripts\win\run-dev.ps1 -SkipDbInit
.\scripts\win\run-dev.ps1 -SkipCompatCheck
.\scripts\win\run-dev.ps1 -DbHost 192.168.1.20 -RedisHost 192.168.1.21
```

Default URLs:

- Frontend: `http://127.0.0.1:5174`
- Backend API: `http://127.0.0.1:18080/api`
- Health: `http://127.0.0.1:18080/api/health`
- Swagger: `http://127.0.0.1:18080/api/swagger-ui.html`

## Manual Start

Backend only:

```powershell
.\scripts\win\backend-dev.ps1
```

Frontend only:

```powershell
.\scripts\win\frontend-dev.ps1
```

When starting manually, keep `SERVER_PORT` and `VITE_API_PROXY_TARGET` aligned:

```powershell
$env:SERVER_PORT = "18080"
$env:VITE_API_PROXY_TARGET = "http://127.0.0.1:18080"
```

## Diagnostics

Run the full Windows no-Docker verifier after startup:

```powershell
.\scripts\win\verify-no-docker.ps1
```

For pre-start checks only, skip the runtime doctor:

```powershell
.\scripts\win\verify-no-docker.ps1 -PreStart
```

After `run-dev.ps1` starts the backend and frontend, run the post-start runtime check:

```powershell
.\scripts\win\verify-no-docker.ps1 -PostStart
```

The verifier checks the D drive project location, required local tools, local MySQL schema/seed, frontend/backend build, and runtime doctor. It does not call Docker.

You can also run the read-only doctor directly after startup:

```powershell
.\scripts\win\dev-doctor.ps1 -BaseUrl http://127.0.0.1:5174 -ApiBaseUrl http://127.0.0.1:18080/api
```

The doctor checks frontend/backend connectivity, `/api/health`, required tables, seed rows, menu/permission visibility, and role boundaries. It does not reset data, delete volumes, execute terminal actions, or send notifications.

## Backup And Restore

Windows no-Docker backup uses local MySQL client tools and writes to D drive by default:

```powershell
$env:DB_PASSWORD = "replace-with-local-db-password"
.\scripts\win\backup-runtime.ps1
```

Default backup target:

```text
D:\CyberFusion\Environment\cyberfusion-platform\backups\runtime\YYYYMMDD-HHMMSS
```

If Redis persistence must be backed up too, set `REDIS_DUMP_PATH` to the local Redis `dump.rdb` file before running the backup:

```powershell
$env:REDIS_DUMP_PATH = "D:\CyberFusion\Redis\data\dump.rdb"
.\scripts\win\backup-runtime.ps1
```

Restore is intentionally explicit because it overwrites runtime data:

```powershell
$env:DB_PASSWORD = "replace-with-local-db-password"
.\scripts\win\restore-runtime.ps1 -BackupDir "D:\CyberFusion\Environment\cyberfusion-platform\backups\runtime\YYYYMMDD-HHMMSS" -ConfirmRestore
```

For Redis backup or restore, keep `REDIS_DUMP_PATH` or `-RedisTargetDumpPath` on D drive. Stop Redis before restore, then pass `-RestoreRedis` and the D drive dump path.

## Common Failures

- `Missing command: mysql`: install MySQL client tools and add the MySQL `bin` directory to `PATH`.
- `Missing command: mysqldump`: install MySQL client tools; backup requires `mysqldump.exe`.
- `Windows no-Docker mode requires the project under D:\CyberFusion`: move the project folder to `D:\CyberFusion\00-cyberfusion-platform`.
- `MySQL is not reachable`: start MySQL service or pass the correct `-DbHost` and `-DbPort`.
- `Redis is not reachable`: start a Redis-compatible Windows service or pass the correct `-RedisHost` and `-RedisPort`.
- Login returns `500`: check `DB_PASSWORD`, then open `http://127.0.0.1:18080/api/health`.
- Admin can log in but menus are missing: rerun `.\scripts\win\init-local-db.ps1` so the latest menu and permission patch is applied.

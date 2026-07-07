# CyberFusion SOC

CyberFusion SOC 是 `cyberspace_Security_shot_time` 工作区中的主安全运营平台项目。当前开发、运行、交付和演示都以本目录 `00-cyberfusion-platform` 为准；旁边的 `01-16` 目录只作为上游参考或历史素材，不作为主项目启动入口。

本项目面向本地研发、教学演示、小型内网安全运营和受控客户交付，聚合资产、事件、告警、工单、报告、策略、集成适配器和本地客户端安全视图。项目不执行未授权扫描、不连接公网攻击目标、不自动下发处置动作。

## 当前交付重点

- Windows 电脑可以在不使用 Docker 的情况下运行。
- Windows 项目源码可以放在用户自选盘符和目录，例如 `E:\CyberFusion\00-cyberfusion-platform`。
- Windows 运行数据、日志、上传、备份、缓存、临时文件和验证证据由 `CYBERFUSION_ENV_ROOT` 指定，必须在源码目录外。
- macOS/Linux 原有 Docker Compose 本地路径保留，不破坏原来的开发方式。
- 真实密码、Token、证书、客户数据、数据库文件、日志、Docker 卷和大模型文件不得进入源码仓库。

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 后端 | Java 21, Spring Boot 3, Maven, MyBatis-Plus |
| 前端 | Vue 3, Vite, TypeScript, Element Plus, Pinia, pnpm |
| 数据库 | MySQL 8 |
| 缓存/队列 | Redis 兼容服务 |
| 本地验证 | Maven test, Vite build, Playwright, PowerShell scripts |
| 集成素材 | Wazuh, Zeek, Suricata, Trivy, MISP, ZAP, Sigma, CyberChef, Shuffle 等防御侧数据适配 |

## 功能范围

- 登录、RBAC、部门、岗位、角色、菜单、字典、配置、审计日志。
- SOC 工作台、资产中心、外部事件、告警中心、降噪、事件聚类、工单、报告。
- 策略中心、检测规则、关联规则、风险评分、本地检查命令、响应剧本。
- 客户端工作台、本地靶场视图、设备管理、安全日志、数据报告、运维视图。
- 多源安全数据导入：Zeek `conn.log`、Suricata `eve.json`、Wazuh demo alert、MISP IOC、Trivy JSON、ZAP JSON。
- 集成目录 API：`GET /api/soc/integrations/catalog`。
- 防御侧统一导入入口：`POST /api/soc/external-events/cyberfusion/import`。

## 目录说明

```text
00-cyberfusion-platform/
  backend/          Spring Boot 后端
  frontend/         Vue/Vite 前端
  sql/              MySQL schema 和 seed
  scripts/          macOS、Windows、smoke、SQL 辅助脚本
  deploy/           Docker Compose、Nginx、Demo Range 部署模板
  integrations/     已整理到主项目内的集成素材
  docs/             架构、API、部署、使用、交接、测试报告
  demo-data/        本地演示数据
```

## Windows 无 Docker 快速启动

### 0. 无 Docker 容器配置说明

Windows 交付路径不启动 Docker、不依赖 Docker Desktop，也不会执行 `docker compose up`。原来由容器承担的基础设施改为用户机器上的本地服务和普通进程：

| 原 Docker 职责 | Windows 无 Docker 配置 | 必填配置 |
| --- | --- | --- |
| MySQL 容器 | 本机或内网可达的 MySQL 8 服务 | `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` |
| Redis 容器 | 本机或内网可达的 Redis 兼容服务 | `REDIS_HOST`, `REDIS_PORT` |
| 后端容器 | `mvn spring-boot:run` 启动的本机 Java 进程 | `SERVER_PORT`, `CYBERFUSION_ENV_ROOT` |
| 前端容器/静态服务 | `pnpm dev` 启动的本机 Vite 进程 | `FRONTEND_PORT`, `VITE_API_PROXY_TARGET` |
| Docker volume | `CYBERFUSION_ENV_ROOT` 下的上传、日志、缓存、备份和验证目录 | 用户自选绝对路径，且必须在源码目录外 |

脚本入口：

- 一键启动：`.\scripts\win\start-no-docker.ps1`
- 分阶段启动：`.\scripts\win\prepare-runtime.ps1`、`.\scripts\win\verify-no-docker.ps1 -PreStart`、`.\scripts\win\run-dev.ps1`、`.\scripts\win\verify-no-docker.ps1 -PostStart`
- 证据采集：`.\scripts\win\collect-windows-evidence.ps1`
- 备份恢复：`.\scripts\win\backup-runtime.ps1`、`.\scripts\win\restore-runtime.ps1`

`CYBERFUSION_ENV_ROOT` 不固定盘符。用户可以使用 `E:\CyberFusion\Environment\cyberfusion-platform`、`F:\Work\CyberFusion\Environment\cyberfusion-platform` 等路径；未设置时，脚本会根据当前项目所在目录自动推导一个源码目录外的 `Environment\cyberfusion-platform`。

### 1. 准备目录

```powershell
$CyberFusionRoot = "E:\CyberFusion"
New-Item -ItemType Directory -Force $CyberFusionRoot | Out-Null
cd $CyberFusionRoot
# 推荐把本仓库 clone 或复制为：
# E:\CyberFusion\00-cyberfusion-platform
cd .\00-cyberfusion-platform
```

不要从桌面、下载目录或临时目录启动。Windows 脚本不再固定盘符，但会阻止运行数据写入源码目录内部。

### 2. 安装并启动依赖

Windows 无 Docker 路径不会自动启动数据库服务，需要你先准备：

- JDK 21+
- Maven 3.9+
- Node.js 20+
- pnpm 11+
- MySQL 8 server
- MySQL 8 client `mysql.exe` 和 `mysqldump.exe`
- Redis 兼容服务

确认 MySQL 和 Redis 已经运行后，设置当前 PowerShell 会话变量：

```powershell
$env:DB_HOST = "127.0.0.1"
$env:DB_PORT = "3306"
$env:DB_NAME = "cyberfusion_soc"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "replace-with-local-db-password"
$env:CYBERFUSION_ENV_ROOT = Join-Path $CyberFusionRoot "Environment\cyberfusion-platform"
```

不要把真实数据库密码写进源码文件。

### 3. 一键启动

```powershell
.\scripts\win\start-no-docker.ps1
```

这个脚本会按顺序执行：

1. 创建用户配置的 Windows 运行目录。
2. 检查 MySQL/Redis 端口可达。
3. 执行 Windows pre-start 验证。
4. 初始化或刷新 MySQL schema/seed。
5. 启动 Spring Boot 后端和 Vite 前端。
6. 执行 post-start 运行检查。

默认地址：

- 前端：`http://127.0.0.1:5174`
- 后端 API：`http://127.0.0.1:18080/api`
- 健康检查：`http://127.0.0.1:18080/api/health`
- Swagger：`http://127.0.0.1:18080/api/swagger-ui.html`

本地演示账号：

```text
admin / Admin@123456
```

如果已有本地数据库曾修改过管理员密码，可能需要使用当前数据库中的密码；部分历史 smoke 脚本也兼容 `admin123` 作为本地 fallback。

### 4. 分阶段排障

如果一键启动失败，用下面的分阶段命令定位问题：

```powershell
.\scripts\win\prepare-runtime.ps1
.\scripts\win\verify-no-docker.ps1 -PreStart
.\scripts\win\run-dev.ps1
.\scripts\win\verify-no-docker.ps1 -PostStart
```

只启动后端：

```powershell
.\scripts\win\backend-dev.ps1
```

只启动前端：

```powershell
.\scripts\win\frontend-dev.ps1
```

## Windows 运行目录边界

Windows 脚本会把以下内容放在 `CYBERFUSION_ENV_ROOT` 下。这个目录可以在用户自选盘符上，但必须是绝对路径，并且不能位于 `00-cyberfusion-platform` 源码目录内：

- `uploads`
- `logs\backend`
- `backups\runtime`
- `local-vm`
- `caches\maven-repository`
- `caches\pnpm-store`
- `caches\npm`
- `tmp`
- `packages`
- `package-staging`
- `validation`

脚本会传递 Maven `-Dmaven.repo.local`、pnpm `--store-dir`，并设置 npm cache、`TEMP`、`TMP` 和 Java `java.io.tmpdir`，避免依赖缓存和临时文件落到默认用户目录或源码目录。

## Windows 验证证据

在 Windows 目标机器上设置好 `DB_PASSWORD` 后，可以生成验证证据包：

```powershell
.\scripts\win\collect-windows-evidence.ps1
```

如果后端和前端已经在运行，只采集 post-start 证据：

```powershell
.\scripts\win\collect-windows-evidence.ps1 -SkipStart
```

证据会写入：

```text
%CYBERFUSION_ENV_ROOT%\validation
```

证据脚本记录工具版本、Git revision、preflight、启动或 post-start 验证结果和 transcript，不接收数据库密码参数，也不会把密码写入证据 JSON。

## macOS / Linux 本地启动

macOS/Linux 仍保留 Docker Compose 路径：

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

如果手动启动前端，保持代理目标一致：

```sh
VITE_API_PROXY_TARGET=http://127.0.0.1:18080 pnpm --dir frontend dev --host 127.0.0.1 --port 5174
```

## 运行检查

Windows：

```powershell
.\scripts\win\dev-doctor.ps1 -BaseUrl http://127.0.0.1:5174 -ApiBaseUrl http://127.0.0.1:18080/api
```

macOS/Linux：

```sh
scripts/smoke/dev-doctor.sh --base-url http://127.0.0.1:5174 --api-base-url http://127.0.0.1:18080/api
```

Doctor 会检查前后端端口、前端 `/api` 代理、`/api/health`、核心 SOC 表、菜单/权限 seed、管理员菜单权限和员工账号 403 边界。它不会删除数据、重置密码、执行扫描或发送真实通知。

## 本地验收命令

Windows 无 Docker：

```powershell
git status --short
$env:DB_HOST = "127.0.0.1"
$env:DB_PORT = "3306"
$env:DB_NAME = "cyberfusion_soc"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "replace-with-local-db-password"
$env:CYBERFUSION_ENV_ROOT = "E:\CyberFusion\Environment\cyberfusion-platform"
.\scripts\win\start-no-docker.ps1
.\scripts\win\collect-windows-evidence.ps1 -SkipStart
```

macOS/Linux：

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

## 备份和恢复

Windows 无 Docker 备份使用本地 MySQL client：

```powershell
$env:DB_PASSWORD = "replace-with-local-db-password"
.\scripts\win\backup-runtime.ps1
```

默认备份位置：

```text
%CYBERFUSION_ENV_ROOT%\backups\runtime\YYYYMMDD-HHMMSS
```

恢复必须显式确认：

```powershell
$env:DB_PASSWORD = "replace-with-local-db-password"
.\scripts\win\restore-runtime.ps1 -BackupDir "E:\CyberFusion\Environment\cyberfusion-platform\backups\runtime\YYYYMMDD-HHMMSS" -ConfirmRestore
```

Redis 备份/恢复也必须使用源码目录外的绝对路径，例如：

```powershell
$env:REDIS_DUMP_PATH = "E:\CyberFusion\Redis\data\dump.rdb"
```

## 安全边界

- 不要把真实 `.env`、Token、API key、私钥、证书、客户数据提交到 Git。
- 不要把 MySQL data directory、Redis dump、上传文件、日志、备份、验证证据、依赖缓存放进源码目录。
- 不要运行未授权公网扫描。
- Demo Range、ZAP、Trivy、Shuffle 等演示链路默认只用于本地受控环境和 dry-run。
- 不要用 `docker compose down -v` 解决问题，除非明确确认要删除本地数据卷。

## 常用文档

- [架构说明](docs/architecture.md)
- [API 文档](docs/api.md)
- [数据库说明](docs/database.md)
- [部署说明](docs/deploy.md)
- [Windows 无 Docker 指南](docs/windows-no-docker.md)
- [用户手册](docs/user-manual.md)
- [交接清单](docs/handover.md)
- [上游与许可证](docs/upstream.md)
- [测试报告](docs/test-report.md)
- [最终实现报告](docs/final-report.md)

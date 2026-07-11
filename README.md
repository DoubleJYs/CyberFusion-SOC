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
- Agent 管理、真实主机资产/事件/FIM/基线上报、每日优先处理建议。
- 策略中心、检测内容规则设置、关联规则、风险评分、本地检查命令、人工响应模板。
- 客户端工作台、本地靶场视图、设备管理、安全日志、数据报告、运维视图。
- 报告中心支持 Excel/PDF 预览与下载；PDF 使用 inline 预览，Excel 使用结构化表格预览。
- 多源安全数据导入：Zeek `conn.log`、Suricata `eve.json`、Wazuh demo alert、MISP IOC、Trivy JSON、ZAP JSON。
- 集成目录 API：`GET /api/soc/integrations/catalog`。
- 防御侧统一导入入口：`POST /api/soc/external-events/cyberfusion/import`。

## 用户工作区与数据边界

- 全量专家视图的“安全运营工作台”保持原有全局驾驶舱，不经过用户卡片，也不在侧栏提供独立“用户工作区”菜单。
- 安全运营业务页会按当前目标显示用户选择卡片；事件簇、告警、漏洞和 FIM 等页面分别使用对应指标和入口。选择用户后，页面 URL 携带 `ownerId`，后端以各业务表的 `owner_id` 作为最终数据边界。
- 处置闭环的工单中心和报告中心分别提供“全局总览”和“按用户查看”两个页签；按用户卡片使用工单或报告专属指标，选择后回到原有列表并应用用户边界。
- Agent 安装保持原有直接安装页；Agent 管理提供“全局 Agent 管理”和“按用户查看”两个视图。按用户选择后仍进入原有管理界面，只过滤该用户的采集器。
- 管理员在 `系统管理 -> 用户管理` 中可通过“安全工作区”直接打开指定用户；普通员工只使用 `/client/*` 的“我的电脑安全助手”，只能访问自己的主机数据。
- 当前本地验证账号为张彥、松松、老曹、刘哥。张彥卡片标记为“真实采集数据”；其他三位是“预置验证数据”，所有列表均显示该来源，不能作为真实异地主机结论。
- 数据变更后执行 `scripts/sql/apply-user-workspace-seed.sql`，该脚本会补齐用户、角色、所有者字段和验证记录。不要将旧 `demo`、`mock` 或 `fixture` 记录当作用户工作区数据。

## 目录说明

```text
00-cyberfusion-platform/
  backend/          Spring Boot 后端
  frontend/         Vue/Vite 前端
  agent/            自研 Go Host Agent 骨架
  sql/              MySQL schema 和 seed
  scripts/          macOS、Windows、smoke、SQL 辅助脚本
  deploy/           Docker Compose、Nginx、Demo Range 部署模板
  integrations/     已整理到主项目内的集成素材
  docs/             架构、API、部署、使用、交接、测试报告
  demo-data/        本地演示数据
```

## Docker 与 Host Agent 配置总览

CyberFusion 分为两层运行边界：

- 平台服务：Spring Boot、Vue、MySQL、Redis、Adminer，可在本机进程或 Docker Compose 中运行。
- Host Agent：运行在宿主机上的轻量采集器，负责采集 macOS/Windows 主机文本元数据，再通过后端 API 上报。即使平台服务跑在 Windows Docker 容器里，Windows Host Agent 也必须作为 Windows Service 跑在宿主机上，不能放进 Linux 容器里代替宿主机采集。

### 平台 Docker 变量

主平台 Compose 文件是 `deploy/docker-compose.yml`。它只负责 MySQL、Redis 和 Adminer；后端/前端默认仍由本机脚本启动。

| 变量 | 必填 | 示例 | 作用 |
| --- | --- | --- | --- |
| `CYBERFUSION_ENV_ROOT` | 是 | macOS: `$HOME/Environment/cyberfusion-platform`；Windows: `E:/CyberFusion/Environment/cyberfusion-platform` | MySQL/Redis 数据、上传、日志、缓存、备份、验证证据的统一运行根目录，必须在源码目录外 |
| `DB_PASSWORD` | 是 | `replace-with-local-db-password` | MySQL root 密码，只放在本机 shell、运行配置或安全密钥管理中，不写入 Git |
| `DB_NAME` | 否 | `cyberfusion_soc` | 平台数据库名 |
| `DB_PORT` | 否 | `3306` | MySQL 本机映射端口 |
| `REDIS_PORT` | 否 | `6379` | Redis 本机映射端口 |
| `ADMINER_PORT` | 否 | `8081` | Adminer 本机映射端口 |

macOS / Linux 配置检查：

```sh
export CYBERFUSION_ENV_ROOT="$HOME/Environment/cyberfusion-platform"
export DB_PASSWORD="replace-with-local-db-password"
docker compose -f deploy/docker-compose.yml config
docker compose -f deploy/docker-compose.yml up -d mysql redis adminer
```

Windows PowerShell 配置检查：

```powershell
$env:CYBERFUSION_ENV_ROOT = "E:/CyberFusion/Environment/cyberfusion-platform"
$env:DB_PASSWORD = "replace-with-local-db-password"
docker compose -f deploy\docker-compose.yml config
docker compose -f deploy\docker-compose.yml up -d mysql redis adminer
```

Demo Range 使用独立运行根目录，避免把 WAF/ZAP/Trivy 产物混进平台数据：

| 变量 | 必填 | 示例 | 作用 |
| --- | --- | --- | --- |
| `DEMO_RANGE_RUNTIME_ROOT` | 是 | `E:/CyberFusion/Environment/cyberfusion-platform/demo-range` | Demo target 日志、WAF 审计日志、ZAP 输出、Trivy 输出和缓存 |
| `DEMO_RANGE_BIND_HOST` | 否 | `127.0.0.1` | WAF gateway 监听地址 |
| `WAF_HTTP_PORT` | 否 | `18081` | WAF gateway 本机端口 |
| `CYBERFUSION_API_BASE` | 否 | `http://host.docker.internal:18080/api` | Demo bridge 回传到平台后端的 API 地址 |
| `CYBERFUSION_API_TOKEN` | 否 | 留空或本机临时 token | Demo bridge 可选 bearer token，不得提交到源码 |

Demo Range 配置检查：

```sh
export DEMO_RANGE_RUNTIME_ROOT="$HOME/Environment/cyberfusion-platform/demo-range"
docker compose -f deploy/demo-range/docker-compose.yml config
```

不要用 `docker compose down -v` 作为常规排障方式；它会删除本机数据库或演示运行数据。需要清理时先备份，再明确确认删除范围。

### Host Agent 变量

Host Agent 的源码在 `agent/`，安装脚本在 `scripts/mac/` 和 `scripts/win/`。推荐先在全量专家视图中进入 `Agent 中心 -> Agent 安装命令设置与建立`（路由 `/soc/agents/install`），填写目标系统、Agent ID、主机名和运行目录，点击“建立 / 轮换 Agent Token”后复制页面生成的安装命令。`Agent 管理`（路由 `/soc/agents`）保留在线状态、队列、批次、拒收和启停操作；当某个 Agent 显示“本地未安装”时，点击“去设置安装”会带入该 Agent 信息回到安装页。

Agent token 也可以通过平台 `/api/soc/agents/register` 生成。服务端只保存 hash，明文 token 只在安装时显示一次，应放入本机运行目录的 `agent.env` 或操作系统密钥保护机制。

| 变量 | 必填 | 示例 | 作用 |
| --- | --- | --- | --- |
| `CYBERFUSION_API_BASE` | 否 | `http://127.0.0.1:18080/api` | Agent 上报后端地址 |
| `CYBERFUSION_AGENT_ID` | 否 | `macos-host-agent` / `windows-host-agent` | 稳定 Agent 标识；生产环境建议一机一 ID |
| `CYBERFUSION_AGENT_TOKEN` | 是 | `replace-with-local-agent-token` | Agent 上报凭据，只写入本机运行配置，不提交 |
| `CYBERFUSION_ADMIN_ACCESS_TOKEN` | 可选 | `replace-with-local-admin-access-token` | 用于安装脚本自动注册 Agent；有 `CYBERFUSION_AGENT_TOKEN` 时不需要 |
| `CYBERFUSION_AGENT_RUNTIME_DIR` | 否 | 安装脚本自动设为 `CYBERFUSION_ENV_ROOT/agent/<agentId>/runtime` | 本地队列、状态和日志目录 |
| `CYBERFUSION_AGENT_FIM_PATH` | 否 | `/etc/hosts` 或受控测试文件 | 兼容单一路径 FIM 元数据采集；生产目录应在“策略与规则中心 -> 文件监控授权”发布 |
| `CYBERFUSION_AGENT_INTERVAL` | 否 | `60s` | daemon / service 模式采集间隔 |

生产环境不要把根目录或通配符写入 Agent 配置。由安全工程师在 `策略与规则中心 -> 文件监控授权` 按“主机 + OS + 明确目录”建立并发布目录授权；Agent 在下一采集周期拉取授权，只记录路径、大小、时间、权限与元数据哈希的新增、修改、删除和权限变化，不上传文件内容。

macOS 安装到 launchd：

```sh
export CYBERFUSION_ENV_ROOT="$HOME/Environment/cyberfusion-platform"
export CYBERFUSION_API_BASE="http://127.0.0.1:18080/api"
export CYBERFUSION_AGENT_ID="macos-host-agent"
export CYBERFUSION_AGENT_TOKEN="replace-with-local-agent-token"
scripts/mac/install-agent.sh
scripts/mac/start-agent.sh
scripts/mac/verify-agent.sh
```

Windows 安装为 Service：

```powershell
$env:CYBERFUSION_ENV_ROOT = "E:\CyberFusion\Environment\cyberfusion-platform"
$env:CYBERFUSION_API_BASE = "http://127.0.0.1:18080/api"
$env:CYBERFUSION_AGENT_TOKEN = "replace-with-local-agent-token"
.\scripts\win\install-agent.ps1 -AgentId "windows-host-agent"
.\scripts\win\start-agent.ps1 -AgentId "windows-host-agent"
.\scripts\win\verify-agent.ps1 -AgentId "windows-host-agent" -UploadOnce
```

页面建立 Agent 只代表平台生成了接收身份，不代表主机在线。安装脚本启动后，真实 Agent 通过心跳和上报把状态更新为在线；如果只有注册记录而没有宿主机进程，管理页会显示待心跳或本机未安装。

开发机预检：

```sh
cd agent
GOCACHE=/private/tmp/cyberfusion-go-build GOMODCACHE=/private/tmp/cyberfusion-go-mod go test ./...
cd ..
scripts/smoke/host-agent-go-smoke.sh
scripts/smoke/host-agent-fixture-residue-gate.sh
scripts/smoke/host-agent-package-smoke.sh
```

Windows fixture 只能验证协议、构建和页面展示；没有真实 Windows 主机时，不能宣称已完成 EventLog、Defender、Sysmon、Windows Service 重启自恢复或 Docker 后端停机后的真实队列补传验收。

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

## Windows 有 Docker 容器启动

如果目标 Windows 机器已经安装 Docker Desktop，可以使用容器承载 MySQL、Redis 和 Adminer，后端/前端仍按本机进程启动；也可以参考 `deploy\docker-compose.app.example.yml` 构建完整应用容器示例。

### 0. 有 Docker 容器配置说明

| 容器/进程 | 默认配置 | 说明 |
| --- | --- | --- |
| MySQL 容器 | `deploy\docker-compose.yml` 的 `mysql` 服务 | 数据卷挂载到 `%CYBERFUSION_ENV_ROOT%\mysql`，端口由 `DB_PORT` 控制 |
| Redis 容器 | `deploy\docker-compose.yml` 的 `redis` 服务 | 数据卷挂载到 `%CYBERFUSION_ENV_ROOT%\redis`，端口由 `REDIS_PORT` 控制 |
| Adminer 容器 | `deploy\docker-compose.yml` 的 `adminer` 服务 | 仅本机访问，端口由 `ADMINER_PORT` 控制 |
| 后端本机进程 | `.\scripts\win\backend-dev.ps1` 或 `.\scripts\win\run-dev.ps1` | 连接 `127.0.0.1:$env:DB_PORT` 和 `127.0.0.1:$env:REDIS_PORT` |
| 前端本机进程 | `.\scripts\win\frontend-dev.ps1` 或 `.\scripts\win\run-dev.ps1` | 代理到 `VITE_API_PROXY_TARGET` |

Windows Docker volume 路径同样不固定盘符。`CYBERFUSION_ENV_ROOT` 必须由用户设置为源码目录外的绝对路径；在 Docker Compose 中建议使用正斜杠：

```powershell
$CyberFusionRoot = "E:\CyberFusion" # 替换成用户自己的项目根位置
cd (Join-Path $CyberFusionRoot "00-cyberfusion-platform")

$env:CYBERFUSION_ENV_ROOT = "E:/CyberFusion/Environment/cyberfusion-platform"
$env:DB_HOST = "127.0.0.1"
$env:DB_PORT = "3306"
$env:DB_NAME = "cyberfusion_soc"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "replace-with-local-db-password"
$env:REDIS_HOST = "127.0.0.1"
$env:REDIS_PORT = "6379"
$env:ADMINER_PORT = "8081"
```

启动容器：

```powershell
docker compose -f deploy\docker-compose.yml config
docker compose -f deploy\docker-compose.yml up -d mysql redis adminer
docker compose -f deploy\docker-compose.yml ps
```

初始化数据库并启动平台：

```powershell
.\scripts\win\init-local-db.ps1
.\scripts\win\run-dev.ps1 -SkipDbInit
```

验证：

```powershell
.\scripts\win\dev-doctor.ps1 -BaseUrl http://127.0.0.1:5174 -ApiBaseUrl http://127.0.0.1:18080/api
```

停止容器不要删除卷：

```powershell
docker compose -f deploy\docker-compose.yml stop
```

不要执行 `docker compose down -v`，除非已经明确确认要删除本机 MySQL/Redis 数据。

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
- [Host Agent macOS / Windows 同步建设计划](docs/host-agent-mac-windows-plan.md)
- [用户手册](docs/user-manual.md)
- [交接清单](docs/handover.md)
- [上游与许可证](docs/upstream.md)
- [测试报告](docs/test-report.md)
- [最终实现报告](docs/final-report.md)

# CyberFusion SOC Windows Docker 配置说明

本文说明在 Windows 机器上使用 Docker Desktop 启动 CyberFusion SOC 依赖容器的标准配置。目标是让源码、运行数据、Docker 卷、日志和临时文件边界清楚，并且不固定用户盘符。

## 1. 适用范围

Windows 有 Docker 模式推荐用于本地演示、研发联调和可控内网交付：

| 部分 | 推荐运行方式 | 配置文件 |
| --- | --- | --- |
| MySQL 8 | Docker 容器 | `deploy\docker-compose.yml` |
| Redis | Docker 容器 | `deploy\docker-compose.yml` |
| Adminer | Docker 容器，仅本机访问 | `deploy\docker-compose.yml` |
| Spring Boot 后端 | 默认本机 Maven/Java 进程 | `scripts\win\run-dev.ps1` |
| Vue 前端 | 默认本机 pnpm/Vite 进程 | `scripts\win\run-dev.ps1` |
| 完整应用容器示例 | 可选，不作为默认交付路径 | `deploy\docker-compose.app.example.yml` |
| Demo Range 靶场容器 | 可选，独立于 SOC 主运行环境 | `deploy\demo-range\docker-compose.yml` |

默认建议只把 MySQL、Redis 和 Adminer 放进 Docker。后端和前端保留为本机进程，便于 Windows 上调试、改配置和采集证据。

## 2. Windows 前置条件

确认 Windows 机器已经具备：

- Windows 10/11 64 位。
- Docker Desktop，建议使用 WSL 2 backend。
- PowerShell 5.1+ 或 PowerShell 7+。
- JDK 21+。
- Maven 3.9+。
- Node.js 20+。
- pnpm 11+。
- MySQL client 工具可用，至少包含 `mysql.exe`；如需备份恢复，还需要 `mysqldump.exe`。

检查命令：

```powershell
docker version
docker compose version
java -version
mvn -version
node -v
pnpm -v
mysql --version
```

## 3. 目录与盘符规则

项目不固定 `C:`、`D:` 或任何具体盘符。用户可以把源码放在自己的目录，例如：

```text
E:\CyberFusion\00-cyberfusion-platform
F:\Work\Security\00-cyberfusion-platform
```

运行数据必须放在源码目录外，由 `CYBERFUSION_ENV_ROOT` 指定。Docker Compose 里建议使用正斜杠路径，减少 Windows 路径转义问题：

```powershell
$CyberFusionRoot = "E:\CyberFusion"
$ProjectRoot = Join-Path $CyberFusionRoot "00-cyberfusion-platform"
$env:CYBERFUSION_ENV_ROOT = "E:/CyberFusion/Environment/cyberfusion-platform"
```

`CYBERFUSION_ENV_ROOT` 下会保存：

| 子目录 | 用途 |
| --- | --- |
| `mysql` | MySQL 容器数据目录，挂载到 `/var/lib/mysql` |
| `redis` | Redis 容器数据目录，挂载到 `/data` |
| `uploads` | 后端上传文件 |
| `logs\backend` | 后端运行日志 |
| `backups\runtime` | 数据库和运行目录备份 |
| `validation` | Windows 验证证据 |
| `caches` | Maven、pnpm、npm 缓存 |
| `demo-range` | 可选 Demo Range 靶场运行数据 |

禁止把这些目录放进 `00-cyberfusion-platform` 源码目录内，也不要提交真实 `.env`、数据库文件、日志、上传文件、证书或 Docker 数据卷。

## 4. 主系统容器配置

主系统 Docker Compose 文件是：

```text
deploy\docker-compose.yml
```

它定义三个本机容器：

| 服务 | 镜像 | 端口 | 数据卷 |
| --- | --- | --- | --- |
| `mysql` | `mysql:8.4` | `127.0.0.1:${DB_PORT:-3306}:3306` | `${CYBERFUSION_ENV_ROOT}/mysql:/var/lib/mysql` |
| `redis` | `redis:7-alpine` | `127.0.0.1:${REDIS_PORT:-6379}:6379` | `${CYBERFUSION_ENV_ROOT}/redis:/data` |
| `adminer` | `adminer:latest` | `127.0.0.1:${ADMINER_PORT:-8081}:8080` | 无持久数据 |

`DB_PASSWORD` 和 `CYBERFUSION_ENV_ROOT` 是必填变量。Compose 文件故意要求显式设置，避免容器用空密码或把数据卷落到源码目录。

## 5. PowerShell 启动步骤

### 5.1 进入项目目录

```powershell
$CyberFusionRoot = "E:\CyberFusion"
$ProjectRoot = Join-Path $CyberFusionRoot "00-cyberfusion-platform"
cd $ProjectRoot
```

把 `E:\CyberFusion` 替换为用户自己的实际路径。

### 5.2 设置环境变量

```powershell
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

注意：

- `CYBERFUSION_ENV_ROOT` 在 Compose 中建议写成 `E:/...`。
- `DB_PASSWORD` 只设置在当前 PowerShell 会话或用户自己的本地环境中，不要写入 Git 仓库。
- 如 3306、6379 或 8081 已被占用，可以改 `DB_PORT`、`REDIS_PORT`、`ADMINER_PORT`。

### 5.3 创建运行目录

```powershell
New-Item -ItemType Directory -Force `
  "$env:CYBERFUSION_ENV_ROOT/mysql", `
  "$env:CYBERFUSION_ENV_ROOT/redis", `
  "$env:CYBERFUSION_ENV_ROOT/uploads", `
  "$env:CYBERFUSION_ENV_ROOT/logs/backend", `
  "$env:CYBERFUSION_ENV_ROOT/backups/runtime", `
  "$env:CYBERFUSION_ENV_ROOT/validation" | Out-Null
```

### 5.4 校验 Compose 配置

```powershell
docker compose -f deploy\docker-compose.yml config
```

必须先看到配置正常展开，再启动容器。重点检查输出中的 volume 是否指向用户设置的 `CYBERFUSION_ENV_ROOT`，而不是源码目录。

### 5.5 启动 MySQL、Redis、Adminer

```powershell
docker compose -f deploy\docker-compose.yml up -d mysql redis adminer
docker compose -f deploy\docker-compose.yml ps
```

查看日志：

```powershell
docker compose -f deploy\docker-compose.yml logs --tail=80 mysql
docker compose -f deploy\docker-compose.yml logs --tail=80 redis
```

Adminer 地址：

```text
http://127.0.0.1:8081
```

登录信息：

| 字段 | 值 |
| --- | --- |
| System | `MySQL` |
| Server | `mysql` 或 `127.0.0.1:3306` |
| Username | `root` |
| Password | 当前 PowerShell 中的 `$env:DB_PASSWORD` |
| Database | `cyberfusion_soc` |

## 6. 初始化数据库并启动平台

容器启动后，执行数据库初始化：

```powershell
.\scripts\win\init-local-db.ps1
```

初始化脚本会应用：

- `sql\schema.sql`
- `sql\data.sql`
- `scripts\sql\apply-latest-menu-and-policy-seed.sql`

然后启动后端和前端：

```powershell
.\scripts\win\run-dev.ps1 -SkipDbInit
```

默认访问地址：

| 服务 | 地址 |
| --- | --- |
| 前端 | `http://127.0.0.1:5174` |
| 后端 API | `http://127.0.0.1:18080/api` |
| 健康检查 | `http://127.0.0.1:18080/api/health` |
| Swagger | `http://127.0.0.1:18080/api/swagger-ui.html` |
| Adminer | `http://127.0.0.1:8081` |

演示账号：

```text
admin / Admin@123456
```

## 7. 验证命令

启动后执行：

```powershell
.\scripts\win\dev-doctor.ps1 -BaseUrl http://127.0.0.1:5174 -ApiBaseUrl http://127.0.0.1:18080/api
```

检查数据库：

```powershell
mysql --default-character-set=utf8mb4 `
  -h 127.0.0.1 `
  -P $env:DB_PORT `
  -u $env:DB_USERNAME `
  -p"$env:DB_PASSWORD" `
  $env:DB_NAME `
  -e "SHOW TABLES;"
```

采集交付证据：

```powershell
.\scripts\win\collect-windows-evidence.ps1 -SkipStart
```

证据目录：

```text
%CYBERFUSION_ENV_ROOT%\validation
```

## 8. 停止与重启

停止后端和前端时，关闭对应 PowerShell 窗口或停止脚本进程。

停止容器但保留数据：

```powershell
docker compose -f deploy\docker-compose.yml stop
```

重新启动容器：

```powershell
docker compose -f deploy\docker-compose.yml up -d mysql redis adminer
```

不要执行：

```powershell
docker compose -f deploy\docker-compose.yml down -v
```

`down -v` 会删除本机 Docker 数据卷，只有在明确确认要清空本地 MySQL/Redis 数据时才能使用。

## 9. 完整应用容器示例

如果要把后端和前端也容器化，参考：

```text
deploy\docker-compose.app.example.yml
```

校验示例配置：

```powershell
docker compose -f deploy\docker-compose.app.example.yml config
```

该文件同样使用 `CYBERFUSION_ENV_ROOT` 挂载：

- `${CYBERFUSION_ENV_ROOT}/mysql`
- `${CYBERFUSION_ENV_ROOT}/redis`
- `${CYBERFUSION_ENV_ROOT}/uploads`
- `${CYBERFUSION_ENV_ROOT}/logs/backend`

完整应用容器模式适合打包演示或封闭环境验证。日常开发仍建议使用“数据库/缓存容器 + 后端/前端本机进程”的方式。

## 10. Demo Range Docker 配置

Demo Range 是独立靶场，不使用主系统的 `CYBERFUSION_ENV_ROOT` 作为根目录，而是使用：

```powershell
$env:DEMO_RANGE_RUNTIME_ROOT = "E:/CyberFusion/Environment/cyberfusion-platform/demo-range"
$env:DEMO_RANGE_BIND_HOST = "127.0.0.1"
$env:WAF_HTTP_PORT = "18081"
```

准备目录：

```powershell
New-Item -ItemType Directory -Force `
  "$env:DEMO_RANGE_RUNTIME_ROOT/logs/demo-target", `
  "$env:DEMO_RANGE_RUNTIME_ROOT/logs/waf", `
  "$env:DEMO_RANGE_RUNTIME_ROOT/logs/nginx", `
  "$env:DEMO_RANGE_RUNTIME_ROOT/zap", `
  "$env:DEMO_RANGE_RUNTIME_ROOT/trivy", `
  "$env:DEMO_RANGE_RUNTIME_ROOT/trivy-cache" | Out-Null
```

校验并启动基础靶场：

```powershell
docker compose -f deploy\demo-range\docker-compose.yml config
docker compose -f deploy\demo-range\docker-compose.yml up -d demo-target waf-gateway
```

默认地址：

```text
http://127.0.0.1:18081
```

Demo Range 只用于本机或受控内网演示，不要暴露到公网，不要扫描第三方目标。

## 11. 常见问题

| 问题 | 处理方式 |
| --- | --- |
| `CYBERFUSION_ENV_ROOT` 未设置 | 在 PowerShell 中设置绝对路径，Compose 中建议用正斜杠 |
| 3306 端口占用 | 修改 `$env:DB_PORT`，重新执行 `docker compose config` 和 `up -d` |
| Redis 6379 端口占用 | 修改 `$env:REDIS_PORT` |
| Adminer 8081 端口占用 | 修改 `$env:ADMINER_PORT` |
| 后端连接 MySQL 失败 | 检查 `docker compose ps`、MySQL 日志、`DB_HOST`、`DB_PORT`、`DB_PASSWORD` |
| 数据库表不存在 | 执行 `.\scripts\win\init-local-db.ps1` |
| 前端 500 或接口失败 | 先看 `http://127.0.0.1:18080/api/health`，再运行 `dev-doctor.ps1` |
| Docker volume 写到源码目录 | 立刻停止容器，修正 `CYBERFUSION_ENV_ROOT` 到源码外路径后重新 `config` |

## 12. 最小验收清单

交付或汇报前至少确认：

- `docker compose -f deploy\docker-compose.yml config` 通过。
- `docker compose -f deploy\docker-compose.yml ps` 中 MySQL、Redis、Adminer 运行中。
- `.\scripts\win\init-local-db.ps1` 执行成功。
- `.\scripts\win\run-dev.ps1 -SkipDbInit` 可以启动后端和前端。
- `http://127.0.0.1:18080/api/health` 返回正常。
- `http://127.0.0.1:5174` 可以登录。
- 运行数据位于 `CYBERFUSION_ENV_ROOT`，不在源码目录。
- 没有真实密码、Token、证书、数据库文件、日志或 Docker 卷进入 Git。

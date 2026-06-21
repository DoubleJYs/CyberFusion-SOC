# sec-wazuh-soc

基于 Wazuh 的企业安全监测与告警处置平台。Wazuh 作为底层 SIEM/XDR 安全引擎，自研 Spring Boot + Vue 系统作为产品化业务层，承载安全总览、告警处置、工单流转、报表导出、权限管理和审计日志。

## 目录

- `backend/`：Spring Boot 3 后端，封装 Wazuh/Indexer 查询入口并承载业务状态。
- `frontend/`：Vue 3 + TypeScript + Element Plus + ECharts 深色 SOC 控制台。
- `sql/`：MySQL 8 初始化结构和 P0 演示数据。
- `deploy/`：本地 MySQL/Redis/Adminer Docker Compose。
- `docs/`：上游合规、架构、部署、API、测试报告。

## P0 数据来源

当前 P0 默认使用 MySQL 中的模拟告警、资产、工单和报表数据完成闭环；Wazuh Manager / Indexer 连接通过环境变量配置，前端不直连 Wazuh，不暴露凭据、证书或内部地址。

本目录已迁移到跑通后的 Wazuh 工程下，作为 `01-wazuh` 仓库内的自研业务层；上级 Wazuh Docker / API 工具文件保持原状，不作为本系统源码的一部分重写。

## 环境约束

- 源码：`/Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/01-wazuh/sec-wazuh-soc`
- 环境、日志、密钥、Docker volume：`/Users/zhangjiyan/Environment/sec-wazuh-soc`
- 真实 `.env`、证书、私钥、日志、备份不能放入源码目录。

## 启动

默认端口适合独立环境；如果本机已有 MySQL/Redis/后端占用端口，可使用后面的隔离端口示例。

```sh
export LOCAL_DB_PASSWORD="replace-with-local-db-password"

cd /Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/01-wazuh/sec-wazuh-soc/deploy
DB_PASSWORD="$LOCAL_DB_PASSWORD" docker compose up -d

cd /Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/01-wazuh/sec-wazuh-soc
mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u root -p"$LOCAL_DB_PASSWORD" < sql/schema.sql
mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u root -p"$LOCAL_DB_PASSWORD" sec_wazuh_soc < sql/data.sql

cd backend
DB_PASSWORD="$LOCAL_DB_PASSWORD" mvn spring-boot:run

cd ../frontend
pnpm install --frozen-lockfile
pnpm dev
```

默认地址：

- 前端：`http://127.0.0.1:5173`
- 后端 API：`http://127.0.0.1:8080/api`
- Swagger：`http://127.0.0.1:8080/api/swagger-ui.html`

本地演示账号：`admin / Admin@123456`。该密码只用于授权测试环境，SQL 中保存的是 BCrypt hash。

隔离端口示例：

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

## Wazuh 配置

`.env.example` 只提供示例值。真实值放到 Environment 下的本地 `.env` 或运行环境变量中：

```sh
WAZUH_MANAGER_URL=https://wazuh-manager.example.local:55000
WAZUH_INDEXER_URL=https://wazuh-indexer.example.local:9200
WAZUH_MANAGER_USERNAME=example-manager-user
WAZUH_MANAGER_PASSWORD=example-manager-password
WAZUH_INDEXER_USERNAME=example-indexer-user
WAZUH_INDEXER_PASSWORD=example-indexer-password
WAZUH_TLS_VERIFY=true
```

兼容变量 `WAZUH_USERNAME` / `WAZUH_PASSWORD` 仍可作为 Manager 和 Indexer 的共同回退值；本地 single-node 环境建议显式分开配置 Manager 与 Indexer 凭据。

## 健康检查

后端提供公开探针，便于 Docker、Nginx 或私有化部署平台判断服务状态：

```sh
curl -s http://127.0.0.1:18080/api/health/liveness
curl -s http://127.0.0.1:18080/api/health/readiness
```

`liveness` 只判断 API 进程可响应；`readiness` 检查 MySQL 和 Redis，响应中只包含依赖名、UP/DOWN、耗时和脱敏错误，不返回连接串、账号、密码、token 或证书内容。

## 容器化部署样例

`deploy/docker-compose.yml` 保持为本地 MySQL / Redis / Adminer 运行底座；应用容器化样例放在 `deploy/docker-compose.app.example.yml`，用于私有化部署演练：

```sh
export LOCAL_DB_PASSWORD="replace-with-local-db-password"

cd /Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/01-wazuh/sec-wazuh-soc/deploy
DB_PASSWORD="$LOCAL_DB_PASSWORD" docker compose -f docker-compose.app.example.yml config
DB_PASSWORD="$LOCAL_DB_PASSWORD" docker compose -f docker-compose.app.example.yml build
DB_PASSWORD="$LOCAL_DB_PASSWORD" docker compose -f docker-compose.app.example.yml up -d
```

应用容器暴露：

- 后端：`http://127.0.0.1:18080/api`
- 前端 Nginx：`http://127.0.0.1:18081`

上传目录和后端日志仍挂载到 `/Users/zhangjiyan/Environment/sec-wazuh-soc`，不进入源码。HTTPS 反向代理参考 `deploy/nginx.https.example.conf`，证书路径必须指向 Environment 或服务器安全目录中的真实证书。

## 备份与恢复

运行数据备份输出到 Environment 目录，不进入源码仓库。脚本只从运行时环境变量读取数据库密码：

```sh
cd /Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/01-wazuh/sec-wazuh-soc
DB_PASSWORD="$LOCAL_DB_PASSWORD" scripts/mac/backup-runtime.sh
DB_PASSWORD="$LOCAL_DB_PASSWORD" RESTORE_CONFIRM=YES scripts/mac/restore-runtime.sh /Users/zhangjiyan/Environment/sec-wazuh-soc/backups/runtime/<backup-dir>
```

Windows PowerShell：

```powershell
$env:DB_PASSWORD = "replace-with-local-db-password"
.\scripts\win\backup-runtime.ps1
.\scripts\win\restore-runtime.ps1 -BackupDir "C:\sec-wazuh-soc\backups\runtime\<backup-dir>" -ConfirmRestore
```

Redis 恢复默认跳过；macOS 脚本需额外设置 `RESTORE_REDIS=true`，PowerShell 脚本需额外传入 `-RestoreRedis`。

## 通知配置

P1.5 已提供邮件优先的通知通道和发送日志。默认种子通道为 `dry_run`，用于演示告警转工单、工单复核/关闭、报表生成时的通知闭环，不会发送真实邮件，也不会在源码保存 SMTP 密码。

生产 SMTP 或企业微信/钉钉 Webhook 密钥必须放在 Environment 目录或运行环境变量中，并在后续发送器实现中读取，禁止写入源码、SQL 种子或前端配置。

## P3 安全加固开关

后端默认启用安全响应头、环境化 CORS 和轻量接口限流。生产环境应显式设置允许的前端域名，避免使用过宽的来源匹配：

```sh
APP_CORS_ALLOWED_ORIGIN_PATTERNS=https://soc.example.com
APP_RATE_LIMIT_ENABLED=true
APP_RATE_LIMIT_REQUESTS_PER_MINUTE=120
APP_RATE_LIMIT_AUTH_REQUESTS_PER_MINUTE=20
```

限流按客户端 IP、HTTP 方法和接口分组统计，`/auth/*` 使用更低阈值；OpenAPI/Swagger 路径默认排除，便于部署验收。

## 验证

```sh
cd backend && mvn test
cd frontend && pnpm build
cd deploy && docker compose config
rg -n "BEGIN .*PRIVATE KEY|AKIA[0-9A-Z]{16}|ghp_[A-Za-z0-9_]+|sk-(proj|live|test)-[A-Za-z0-9_-]+|xoxb-[A-Za-z0-9-]+|真实客户数据|客户数据" . \
  --glob '!frontend/node_modules/**' --glob '!frontend/dist/**' --glob '!backend/target/**' --glob '!README.md' --glob '!docs/**'
```

## 文档

- [上游与合规](docs/upstream.md)
- [架构说明](docs/architecture.md)
- [部署说明](docs/deploy.md)
- [API 文档](docs/api.md)
- [测试报告](docs/test-report.md)
- [前端设计规范](frontend/DESIGN.md)

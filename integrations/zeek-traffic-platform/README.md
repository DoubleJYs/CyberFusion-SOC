# Zeek 网络流量行为分析平台

这是基于当前 `03-zeek` 母版源码树新增的二次开发模块，不重新 clone Zeek，也不改动核心抓包逻辑。平台只处理授权环境中已经存在的 Zeek 日志或 demo 日志，默认关注 `conn.log`、`dns.log`、`http.log`、`ssl.log`、`notice.log`。

## 运行数据位置

默认环境根目录为 `/Users/zhangjiyan/Environment`：

- 数据库：`02-databases/03-zeek/zeek_traffic.sqlite3`
- 日志样例：`11-logs/03-zeek/demo`
- 缓存目录：`10-cache/03-zeek`
- 上传归档：`13-uploads/03-zeek`
- 报告导出：`08-docs/03-zeek`

可用 `--env-root` 或环境变量 `ZEEK_PLATFORM_ENV` 覆盖。

## 快速开始

```bash
cd /Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/03-zeek
python3 tools/traffic-platform/zeek_traffic_platform.py init-env
python3 tools/traffic-platform/zeek_traffic_platform.py init-demo
python3 tools/traffic-platform/zeek_traffic_platform.py status
python3 tools/traffic-platform/zeek_traffic_platform.py serve --host 127.0.0.1 --port 18083
```

浏览器打开 `http://127.0.0.1:18083`。

## 导入真实授权日志

```bash
python3 tools/traffic-platform/zeek_traffic_platform.py import /path/to/conn.log /path/to/dns.log
python3 tools/traffic-platform/zeek_traffic_platform.py import /path/to/zeek-log-directory
python3 tools/traffic-platform/zeek_traffic_platform.py import-failures
python3 tools/traffic-platform/zeek_traffic_platform.py retry-failed
```

导入时会解析 Zeek `#fields` 元数据并写入 SQLite，同时把源日志复制到 `Environment/13-uploads/03-zeek` 便于追溯。文件级 SHA-256 去重保证重复导入不会重复入库；解析失败会写入 `import_failures` 表，可通过 `import-failures` 查看并在修复文件后用 `retry-failed` 重试。不要把生产日志、大文件或数据库放进源码仓库。

## 标准字段模型

平台为 `conn`、`dns`、`http`、`ssl`、`notice` 统一生成 `traffic_events` 表，字段包括：

`ts`、`uid`、`src_ip`、`src_port`、`dst_ip`、`dst_port`、`proto`、`service`、`duration`、`bytes`、`domain`、`uri`、`status`。

Web UI 的 `/events` 页面提供多日志查询，支持时间范围、IP、域名、协议、服务和状态筛选。

## 功能范围

- P0：样例日志生成与导入、总览页、连接数、DNS 请求数、HTTP 访问数、异常事件数、Top 源 IP、Top 目标 IP。
- P1：`conn`、`dns`、`http`、`ssl` 列表页与详情页，支持 IP、时间范围、DNS query、HTTP host 筛选，支持 CSV 导出；`/events` 支持多日志标准字段查询。
- P2：异常 DNS、可疑长连接、异常端口、罕见域名、HTTP 高风险状态码、notice 事件；输出资产通信关系和风险评分。
- P3：生成 `normalized_events` 表，记录本地 `audit_logs`，并可导出或推送兼容 `07-falco/runtime-security-platform` `/api/import` 的 SOC JSON。

## 报告与导出

```bash
python3 tools/traffic-platform/zeek_traffic_platform.py export-csv conn
python3 tools/traffic-platform/zeek_traffic_platform.py export-report
python3 tools/traffic-platform/zeek_traffic_platform.py export-report --kind daily
python3 tools/traffic-platform/zeek_traffic_platform.py export-report --kind anomaly
python3 tools/traffic-platform/zeek_traffic_platform.py export-report --kind asset
python3 tools/traffic-platform/zeek_traffic_platform.py export-report --out /Users/zhangjiyan/Environment/08-docs/03-zeek/zeek-traffic-report.json
python3 tools/traffic-platform/zeek_traffic_platform.py export-soc
python3 tools/traffic-platform/zeek_traffic_platform.py push-soc --url http://127.0.0.1:8767/api/import
```

## 安全边界

本模块不抓包、不生成攻击流量、不绕过授权边界。它只读取已有 Zeek 日志文本，并把运行数据写入用户指定的 Environment 目录。

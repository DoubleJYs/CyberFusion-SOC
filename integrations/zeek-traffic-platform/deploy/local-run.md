# 本地部署说明

## 前置条件

- Python 3.10+。
- 不需要网络依赖。
- 不需要启动 Zeek 抓包进程。

## 初始化

```bash
cd /Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/03-zeek
python3 tools/traffic-platform/zeek_traffic_platform.py init-env
```

## 导入 demo 日志并启动

```bash
python3 tools/traffic-platform/zeek_traffic_platform.py init-demo
python3 tools/traffic-platform/zeek_traffic_platform.py serve --host 127.0.0.1 --port 18083
```

## 导入授权环境日志

```bash
python3 tools/traffic-platform/zeek_traffic_platform.py import /absolute/path/to/zeek/logs
python3 tools/traffic-platform/zeek_traffic_platform.py analyze
python3 tools/traffic-platform/zeek_traffic_platform.py export-report
```

导入是幂等的：同一日志文件内容重复导入时会被 `import_dedupe` 跳过。失败导入会进入 `import_failures`：

```bash
python3 tools/traffic-platform/zeek_traffic_platform.py import-failures
python3 tools/traffic-platform/zeek_traffic_platform.py retry-failed
```

标准字段查询页面：

```text
http://127.0.0.1:18083/events
```

## 统一 SOC 对接

当前版本在 SQLite 中维护 `normalized_events` 表和 `audit_logs` 表。`normalized_events` 字段包括：

- `event_time`
- `source`
- `event_type`
- `severity`
- `src_ip`
- `dst_ip`
- `entity`
- `title`
- `detail`
- `risk_score`
- `raw_ref`

同一工作区的 `07-falco/runtime-security-platform` 暴露 `/api/import`，本模块可导出兼容 payload：

```bash
python3 tools/traffic-platform/zeek_traffic_platform.py export-soc
python3 tools/traffic-platform/zeek_traffic_platform.py push-soc --url http://127.0.0.1:8767/api/import
```

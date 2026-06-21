# 测试报告

## 范围

- Zeek demo 日志生成。
- `conn`、`dns`、`http`、`ssl`、`notice` 导入。
- SQLite 入库。
- 幂等导入、失败记录与失败重试。
- 标准字段 `traffic_events` 生成与多日志查询。
- 总览指标。
- 异常分析与 `normalized_events` 生成。
- 列表筛选。
- Markdown 报告导出。
- 流量日报、异常连接报告、资产通信报告导出。

## 命令

```bash
cd /Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/03-zeek/tools/traffic-platform
PYTHONPATH=. python3 -m unittest discover -s tests
cd /Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/03-zeek
python3 tools/traffic-platform/zeek_traffic_platform.py init-demo
python3 tools/traffic-platform/zeek_traffic_platform.py status
python3 tools/traffic-platform/zeek_traffic_platform.py export-report
python3 tools/traffic-platform/zeek_traffic_platform.py export-report --kind daily
python3 tools/traffic-platform/zeek_traffic_platform.py export-report --kind anomaly
python3 tools/traffic-platform/zeek_traffic_platform.py export-report --kind asset
```

## 当前结论

2026-05-31 本地验证通过：

- `PYTHONPATH=. python3 -m unittest discover -s tests`：通过，覆盖 demo 导入、幂等导入、失败记录、失败重试、标准字段查询、总览、筛选、报告导出和 `normalized_events` 生成。
- `python3 tools/traffic-platform/zeek_traffic_platform.py init-demo`：生成并导入 5 类 demo Zeek 日志。
- `python3 tools/traffic-platform/zeek_traffic_platform.py status`：连接数 3、DNS 请求数 2、HTTP 访问数 2、SSL 记录数 1、notice 记录数 1、异常事件数 9。
- `python3 tools/traffic-platform/zeek_traffic_platform.py export-report`：输出 `/Users/zhangjiyan/Environment/08-docs/03-zeek/zeek-traffic-report.md`。
- `python3 tools/traffic-platform/zeek_traffic_platform.py export-report --kind daily|anomaly|asset`：分别输出流量日报、异常连接报告、资产通信报告。
- `python3 tools/traffic-platform/zeek_traffic_platform.py export-csv conn --ip 10.1.10.8`：输出 `/Users/zhangjiyan/Environment/08-docs/03-zeek/conn-export.csv`。
- `python3 tools/traffic-platform/zeek_traffic_platform.py export-soc`：输出兼容 `07-falco/runtime-security-platform` `/api/import` 的 `/Users/zhangjiyan/Environment/08-docs/03-zeek/zeek-normalized-events.json`。
- `python3 tools/traffic-platform/zeek_traffic_platform.py push-soc --url http://127.0.0.1:8767/api/import`：向临时启动的统一 SOC 测试实例导入 9 条 Zeek 事件，返回 `imported=9`。
- 浏览器烟测 `http://127.0.0.1:18083`：总览、`conn` 筛选、详情、异常分析、资产通信、风险评分和审计页面均可访问。

验收闭环“导入 Zeek 日志 -> 标准化解析 -> 通信画像 -> 异常标记 -> 导出分析报告”已在 demo 数据上跑通；重复导入不重复入库，错误导入可在 `import_failures` 中追踪并重试。

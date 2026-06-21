# 主机资产与基线审计平台

这是基于当前 `10-osquery` 母版新增的二次开发封装层，不修改 osquery 核心采集实现。平台默认只处理 demo 数据、手工导入的授权结果，或显式声明授权的本机 `osqueryi` 查询结果。

## 目录边界

- 源码目录：查询模板、基线策略、Python 后端、静态前端、README、docs、`.env.example`。
- 运行数据：SQLite、日志、缓存、导出报告全部写入 `/Users/zhangjiyan/Environment`。

默认运行目录：

```text
/Users/zhangjiyan/Environment/02-databases/10-osquery/audit.sqlite3
/Users/zhangjiyan/Environment/11-logs/10-osquery/audit-platform.log
/Users/zhangjiyan/Environment/10-cache/10-osquery/
/Users/zhangjiyan/Environment/08-docs/10-osquery/reports/
```

## 快速启动

```bash
cd /Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/10-osquery
python3 audit-platform/auditctl.py init
python3 audit-platform/auditctl.py demo-run
python3 audit-platform/auditctl.py schedule-add --template host_asset_core --target demo --schedule daily
python3 audit-platform/auditctl.py schedule-run --force
python3 audit-platform/auditctl.py report
python3 audit-platform/auditctl.py serve --bind 127.0.0.1 --port 8765
```

浏览器打开：

```text
http://127.0.0.1:8765
```

如果端口已被占用，服务会自动尝试后续 20 个端口，并在终端输出实际可用 URL。也可以使用 `--port 0` 让系统分配端口。

## 闭环流程

1. 在页面选择 `主机资产核心模板`。
2. 点击 `执行 demo 查询`，生成 demo 资产快照。
3. 查看主机、系统、用户、进程、端口、软件和基线失败项。
4. 点击 `导出审计报告`，生成 Markdown 报告到 Environment 文档目录。

## 导入授权结果

页面导入或 CLI 导入均使用 JSON：

```json
{
  "template_id": "host_asset_core",
  "hosts": [
    {
      "host": {
        "hostname": "HOST-01",
        "ip": "10.10.1.10",
        "os_name": "Ubuntu Server",
        "os_version": "22.04",
        "platform": "linux",
        "department": "运维中心",
        "owner": "ops",
        "asset_type": "服务器",
        "status": "online"
      },
      "sections": {
        "users": [],
        "processes": [],
        "ports": [],
        "software": []
      }
    }
  ]
}
```

CLI：

```bash
python3 audit-platform/auditctl.py import --file /path/to/authorized-result.json --source authorized-json
```

## 授权本机查询

仅当确认当前主机属于授权范围时使用：

```bash
python3 audit-platform/auditctl.py execute --template host_asset_core --authorized-host YOUR-HOSTNAME
```

该命令只调用模板内的资产类 SQL，不读取个人文件内容、浏览器历史、密钥、令牌或绕权数据。

## 功能映射

- 查询模板库：进程、端口、用户、软件、计划任务、启动项、内核模块、关键文件哈希。
- 任务模型：`target`、`query`、`schedule`、`status`、`started_at`、`finished_at`、`result_count`、`error`。
- 资产画像：主机、系统、IP、用户数、软件数、开放端口数、最近查询时间。
- 基线策略：SSH 配置、弱口令风险、危险服务、异常端口、敏感权限、账号和防护组件。
- 结果差异：本次与上次快照对比，展示新增、删除和章节变化。
- 周期巡检：每日/每周 demo 调度、失败重试计数、执行日志。
- 权限与审计：记录 actor、查询目标、查询内容、导入和报告导出。
- 报告：资产清单、基线失败项、变更报告、整改建议。

## 模板库

| 模板 | 覆盖内容 |
| --- | --- |
| `host_asset_core` | 主机核心资产、系统、用户、进程、端口、软件、计划任务、启动项、内核模块、文件哈希 |
| `process_inventory` | 进程 |
| `port_inventory` | 开放端口 |
| `user_inventory` | 用户与权限 |
| `software_inventory` | 软件清单 |
| `persistence_inventory` | 计划任务、启动项 |
| `kernel_module_inventory` | 内核模块 |
| `file_hash_baseline` | 关键文件哈希 |

## 任务与周期巡检

查看任务与执行日志：

```bash
python3 audit-platform/auditctl.py tasks
```

创建每日巡检并强制执行一次：

```bash
python3 audit-platform/auditctl.py schedule-add --template host_asset_core --target demo --schedule daily
python3 audit-platform/auditctl.py schedule-run --force
```

当前本地执行器只直接运行 `demo` target。真实主机应通过授权导入或显式 `execute --authorized-host` 接入，避免隐藏采集。

## 安全限制

- 不重新 clone，不改写上游 osquery 核心。
- 不做隐私窃取、隐藏采集、绕权采集。
- 不把 SQLite、日志、缓存、导出报告放进源码目录。
- 不在 `.env.example` 或文档里写入令牌、密码、私钥。

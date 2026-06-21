# 授权 Web 漏洞扫描与报告平台

该子模块位于现有 `14-zaproxy` 母版内，用于完成授权目标管理、扫描任务、漏洞中心、复测、差异对比、报告导出、CI/CD 门禁和审计。当前实现是安全演示闭环：`baseline`、`full`、`api` 都生成 ZAP 风格 demo 结果，不发起任何网络请求，不执行漏洞利用、绕过或攻击载荷增强。

## 输出目录

默认环境根目录为 `/Users/zhangjiyan/Environment`，可通过 `ZAP_PLATFORM_ENV_ROOT` 或 `--env-root` 覆盖。

| 类型 | 默认目录 |
| --- | --- |
| 数据库 | `/Users/zhangjiyan/Environment/02-databases/14-zaproxy/platform.sqlite3` |
| 日志 | `/Users/zhangjiyan/Environment/11-logs/14-zaproxy/platform.log` |
| 上传文件 | `/Users/zhangjiyan/Environment/13-uploads/14-zaproxy` |
| 报告 | `/Users/zhangjiyan/Environment/08-docs/14-zaproxy/reports` |
| ZAP API key 文件 | `/Users/zhangjiyan/Environment/12-secrets/14-zaproxy/zap-api-key` |

不要把 ZAP API key、扫描日志、报告、上传文件、SQLite 数据库提交进源码仓库。

## 启动

在 `14-zaproxy` 目录执行：

```bash
python3 authorized-scan-platform/app.py --host 127.0.0.1 --port 18014
```

打开：

```text
http://127.0.0.1:18014
```

母版 ZAP 源码本身仍按原方式启动：

```bash
./gradlew :zap:run
```

## 闭环流程

1. 添加授权目标：必须勾选“已授权”，填写授权说明、负责人、有效期，扫描范围必须与基础 URL 同源，可配置白名单/黑名单。
2. 创建扫描任务：支持 `baseline`、`full`、`api` 三类 demo 扫描；记录状态、任务日志、超时、速率限制；危险动作始终拒绝。
3. 查看漏洞：展示风险等级、URL、参数、证据、CWE、WASC、修复建议和状态。
4. CI/CD 门禁：按 High/Medium/Low 阈值计算活跃漏洞并返回 success/failure demo 结果。
5. 复测/关闭：漏洞状态为 `pending_fix -> fixed_pending_retest -> retest_passed/retest_failed -> closed`，复测任务会生成差异对比。
6. 导出报告：支持 `scan`、`retest`、`remediation` HTML 报告，写入 Environment 报告目录。

## 验证

```bash
python3 -m py_compile authorized-scan-platform/app.py authorized-scan-platform/smoke_test.py
python3 authorized-scan-platform/smoke_test.py
```

`smoke_test.py` 使用临时环境目录，验证未授权目标拒绝、添加授权目标、创建 demo 扫描、查看漏洞、复测、关闭、导出报告、创建复测任务。

## API 摘要

| 能力 | Endpoint |
| --- | --- |
| 授权目标 | `POST /api/targets`, `GET /api/targets` |
| 扫描任务 | `POST /api/tasks`, `GET /api/tasks`, `GET /api/tasks/{id}` |
| 取消任务 | `POST /api/tasks/{id}/cancel` |
| 复测任务 | `POST /api/tasks/{id}/retest` |
| 差异对比 | `POST /api/tasks/{baseline}/compare/{current}` |
| CI/CD 门禁 | `POST /api/tasks/{id}/ci-gate` |
| 漏洞状态 | `POST /api/vulnerabilities/{id}/mark-fixed`, `POST /api/vulnerabilities/{id}/close` |
| 报告导出 | `POST /api/tasks/{id}/export` with `report_type=scan|retest|remediation` |

## 后续集成边界

接入真实 ZAP API 时必须保留当前授权和范围校验：

- 只允许对已登记、已确认授权、状态为 `active` 的目标创建任务。
- 真实扫描 URL 必须落在目标 `scope_prefix` 内。
- API key 只能从 Environment 下的 secret 文件或进程环境读取，不写入源码。
- 默认策略应保持被动或只读基线，不自动化利用漏洞、不增强攻击载荷。

# User Manual

## Audience

This manual is for customer demos, internal acceptance, and operator handoff of CyberFusion SOC with the offline Demo Range. The system is a defensive SOC platform. It does not provide attack automation, public target scanning, real webhook sending, or production WAF/IDS rule deployment.

## Login And Navigation

1. Open the frontend, usually `http://127.0.0.1:5174`.
2. Sign in with the local demo administrator account created by `sql/data.sql`.
3. For customer-facing demos, open `/showcase` first. This page is the guided 5-step customer demo shell.
4. Use `SOC 专家后台` only when the audience needs operator-level details:
   - `/soc/dashboard`: 安全总览.
   - `/soc/demo-range`: 安全验证.
   - `/soc/external-events`: 证据中心.
   - `/soc/alerts`: 告警处置.
   - `/soc/tickets`: 工单中心.
   - `/soc/reports`: 报告中心.
   - `/soc/rules`: 检测规则.
   - `/soc/policies`: 策略与规则中心.
5. Use `系统管理` only for administrator handoff. System management pages are not part of the customer demo path.

## Demo Mode And Expert Mode

| Mode | Entry | Audience | Purpose | Notes |
| --- | --- | --- | --- | --- |
| 客户演示模式 | `/showcase` | Customers and non-security stakeholders | Show the 5-step story: risk, evidence, alert, ticket, report. | Default demo path; no complex tables on the first screen. |
| SOC 专家模式 | `/soc/dashboard` and other `/soc/*` pages | SOC analysts and implementation teams | Inspect evidence, alerts, assets, rules, tickets, and reports in detail. | Keeps all original SOC pages and compatible routes. |
| 系统管理 | `/system/*` and system settings | Administrators | Manage users, roles, menus, dictionaries, logs, and platform parameters. | Visible from navigation only for admin users. |
| 员工端 | `/client/workbench` | Regular employees | Let employees understand current computer status and submit logs or tasks. | Default employee navigation is 我的电脑 / 我的待办 / 提交日志. |

## Employee Self-Service

The employee-side client is organized as **我的电脑安全助手**. Default navigation keeps only three core entries:

- `/client/workbench`: 我的电脑. Shows whether the current computer is safe, what the employee needs to do, and which button to click next.
- `/client/tasks`: 我的待办. Shows only current-computer tasks and controlled handling actions. `/client/operations` remains a compatible legacy route.
- `/client/data-report`: 提交日志. Lets the employee submit authorized logs or a current-computer summary to the security team.

`/client/device-admin` remains available as **设备信息** from detail buttons. `/client/local-range` remains available as **本机检查** from buttons or task context, but it is not a default top-level employee navigation item. If client APIs fail, employee pages must show recovery actions: retry, use offline demo data, and view diagnostics.

## Policy And Rule Center

`/soc/policies` is the operator-facing **策略与规则中心**. It supports maintainable local read-only check policies, configurable event adapter mappings for WAF, ZAP, Trivy, Wazuh, Suricata, and Zeek, defensive response playbooks, and asset risk scoring policies.

The same page now includes **处置剧本**. Security engineers can maintain defensive response playbooks for WAF, ZAP, Trivy, Wazuh, Suricata, and Zeek evidence. Alert detail pages recommend matching active playbooks, and applying a playbook creates or reuses a ticket, generates a manual task checklist, writes ticket timeline records, and exposes employee confirmation tasks under `/client/operations` when needed.

Playbook safety boundary:

- Playbooks only store matching metadata, human instructions, expected evidence, and task ownership.
- Playbooks do not store scripts, commands, payloads, scanner configuration, auto-fix actions, or external sender targets.
- Applying a playbook never changes production WAF/IDS/SIEM/EDR configuration and never sends real external notifications.
- Employee tasks are scoped to the current authenticated employee and are not visible to unrelated users.

Local check policy workflow:

1. Open `/soc/policies` and stay on `本机检查策略`.
2. Create or edit a policy with `commandKey`, display name, OS, category, description, and `commandArgvJson`.
3. `commandArgvJson` must be a JSON array, for example `["id"]`, `["ss","-tunap"]`, or `["ps","-axo","pid,comm"]`. It must not be a shell string.
4. Click `安全预检`. The backend rejects shell metacharacters, shell launchers, write/delete/install/download commands, public URL access, scanner tooling, and attack tooling.
5. Save as `draft`, then publish to `active`. Only `active + enabled` policies are visible to employees.
6. Disable policies when they should no longer appear in employee-side checks.
7. Open `变更审计` to review recent policy status, version, publisher, and update timestamps.

Employees still cannot execute arbitrary commands. `/client/local-range` only submits `commandKey` and OS context; the backend loads the argv from `soc_local_check_command`, validates it again, executes through argv-based `ProcessBuilder`, and writes SOC evidence/audit records. If no active database policy exists for an OS, the page and API fall back to marked built-in safe defaults.

Event adapter mapping workflow:

1. Open `/soc/policies` and select `事件适配映射`.
2. Review the adapter list for `waf`, `zap`, `trivy`, `wazuh`, `suricata`, and `zeek`.
3. Open `映射` to inspect source fields, normalized fields, severity mapping, dedup key fields, and alert link rules.
4. Use `编辑映射 JSON` only for field-path configuration. Do not enter scripts, expressions, SQL, shell commands, public URLs, attack payloads, or external sender settings.
5. Click `预览归一化` with an offline demo JSON sample. Preview shows normalized output, severity, dedup key, and whether an alert would be linked; it does not write database records.
6. Click `校验` before publishing. Invalid source paths, invalid dedup key arrays, and unsafe template syntax are rejected by the backend.
7. Publish to `active` only after validation. The CyberFusion import endpoint prefers the active adapter; disabled or invalid adapters fall back to the built-in safe adapter.

Asset risk scoring workflow:

1. Open `/soc/policies` and select `风险评分策略`.
2. Maintain only numeric weights such as critical alert weight, high vulnerability weight, failed baseline weight, overdue ticket weight, employee pending task weight, and risk-reduction weights for closed tickets or completed playbook tasks.
3. Click `安全校验` before publishing. The backend rejects script, SQL, shell, scanner, downloader, or expression wording in policy text.
4. Publish one policy as `active`. Asset risk profiles use the latest active policy; if the table is unavailable, the backend falls back to a built-in numeric policy.
5. Click `重新计算全部资产` only when you want to refresh scores from existing SOC data. This does not run scans, execute commands, modify assets, or send notifications.

Asset risk profile workflow:

1. Open `/soc/assets`.
2. Review `风险分` and `风险等级`.
3. Click a row to open the asset detail drawer.
4. Review `风险画像` to see why the asset received the current score, including factors from alerts, vulnerabilities, baseline checks, FIM, external evidence, tickets, playbook tasks, employee tasks, and closed-loop reductions.
5. Use `重新计算` to refresh that one asset from existing SOC records. It never starts a scanner or remediation action.

Employee risk explanation:

- `/client/workbench` still focuses on plain-language status: 安全 / 注意 / 严重.
- When available, the page uses the same asset risk profile to explain the status reason and administrator recommendation.
- If the risk-profile API is unavailable, employee pages fall back to the existing current-computer metrics and recoverable error states.

## Ten-Minute Demo Script

Default customer demo entry is `/showcase`. Expert pages are opened only from the guided actions or appendix.

| Minute | Step | Page | Expected evidence |
| --- | --- | --- | --- |
| 0:00 | Login | `/login` | CyberFusion SOC shell loads after authentication. |
| 1:00 | Open customer demo mode | `/showcase` | First screen shows the product value in plain language: current status, highest risk, pending alerts, open tickets, and `batchId`. |
| 2:00 | Choose scenario | `/showcase` | Step 1 explains the business scenario without attack wording or raw technical fields. |
| 3:00 | Import evidence | `/showcase` | Step 2 imports the offline batch when backend is available, or clearly switches to marked offline demo data when the backend is unavailable. |
| 4:00 | Review evidence summary | `/showcase` | Web gateway block, Web risk scan, component vulnerability, host file change, and network detection summaries are visible. |
| 5:00 | Open technical evidence | `/showcase` | The evidence drawer shows `sourceType`, `eventType`, `ruleId`, `requestId`, `demoCaseId`, and normalized/raw sample fields only on demand. |
| 6:00 | Review alert | `/showcase` to `/soc/alerts` | Step 3 opens the linked alert or alert center with the current `batchId` context. |
| 7:00 | Review event cluster | `/soc/incidents` | The `安全事件簇` page shows which WAF/ZAP/Trivy/Wazuh/Suricata/Zeek evidence belongs to the same case and why each item is related. |
| 8:00 | Apply playbook and convert to ticket | `/soc/incidents` or `/soc/alerts` then `/soc/tickets` | Step 4 applies the recommended response playbook or converts the incident cluster to a ticket, then shows the task checklist plus Demo Range timeline source. |
| 9:00 | Generate report and confirm dry-run boundary | `/showcase` to `/soc/reports` | Step 5 generates or opens the `security_validation` report for this batch. Confirm no email, webhook, Feishu, WeCom, DingTalk, or Slack sender is used. |

## Release Candidate Smoke

Run the automated acceptance check from the project root when preparing a release candidate:

```sh
scripts/smoke/run-acceptance.sh --dry-run
```

This proves the local demo acceptance chain by logging in, importing the offline batch, checking events, alerts, incident clusters, incident-to-ticket conversion, tickets, reports, dry-run notifications, local check policy, correlation rules, and event adapter preview. The command uses local demo data only and does not contact public targets or real notification senders.

Optional screenshot smoke:

```sh
pnpm --dir frontend exec playwright test tests/e2e/release-pages.spec.ts
```

Screenshots are written to `docs/screenshots/acceptance-*.png`; the expected list is recorded in `docs/screenshots/manifest.json`.

## Change Visibility Troubleshooting

If a newly delivered page, tab, button, or menu is not visible in the running browser, check visibility before adding new features:

```sh
scripts/smoke/check-visibility.sh --base-url http://127.0.0.1:5174 --api-base-url http://127.0.0.1:18080/api
```

The check prints the active frontend/backend URLs, logs in as demo roles, reads `/api/auth/me`, lists returned menu paths, confirms frontend routes such as `/showcase`, `/soc/incidents`, `/soc/policies`, `/client/tasks`, and `/client/local-range`, checks policy/adapter/playbook/incident APIs, and verifies that employee users cannot access policy or incident-cluster APIs.

Common causes:

- The browser is pointed at an old Vite dev server or a different project port.
- The browser still holds an old token/menu state in local storage or session storage.
- `sql/data.sql` was updated but the existing local database was not reseeded.
- A page route exists in frontend code but is not present in `sys_menu` or `sys_role_menu`.
- The screenshot gallery is older than the current source checkout.

For an already initialized local database, refresh schema and seed data idempotently instead of deleting data:

```sh
docker exec -i cyberfusion-platform-mysql-1 sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" cyberfusion_soc' < sql/schema.sql
docker exec -i cyberfusion-platform-mysql-1 sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" cyberfusion_soc' < sql/data.sql
docker exec -i cyberfusion-platform-mysql-1 sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" cyberfusion_soc' < scripts/sql/apply-latest-menu-and-policy-seed.sql
```

Then restart the backend so the running `/api` process loads the latest compiled classes. If a Java process was started before the latest Maven build, policy, adapter, playbook, or employee task APIs can still return old-runtime 500 errors even after the database has been refreshed.

The user menu in the admin and employee shells shows a lightweight version/build marker. Use it to confirm whether the browser is seeing the expected runtime.

## Screenshot Checklist

Capture these pages for customer acceptance. Screenshots should avoid real customer identifiers and should use demo data only.

| Screenshot | Page / state | Acceptance note |
| --- | --- | --- |
| 1 | Login page | Shows CyberFusion SOC branding. |
| 2 | Showcase hero | Shows customer demo mode, current status, highest risk, pending alerts, open tickets, `batchId`, and the primary CTA. |
| 3 | Showcase 5-step flow | Shows the guided stepper and the right-side next action panel. |
| 4 | Showcase recoverable error | Shows `演示数据加载失败`, retry, offline demo data, and diagnostics actions. |
| 5 | Showcase evidence drawer | Shows business summary on the main page and technical fields only after clicking `查看技术证据`. |
| 6 | Showcase closure loop and report preview | Shows priority alert, ticket/report entry points, evidence counts, block count, vulnerabilities, and notification dry-run count. |
| 7 | Security incident cluster | Shows `/soc/incidents`, evidence counts, relation reasons, status, and ticket/close actions. |
| 8 | Employee self-service entry and local check | Shows 我的电脑安全助手 with current status, tasks, the three employee actions, and the 本机检查 4-step read-only wizard. |

Current screenshot inventory under `docs/screenshots/manifest.json` includes images from the prior release smoke. Any screenshot generated before the 2026-06-20 P4.5 visibility pass is marked stale until regenerated with `pnpm --dir frontend exec playwright test tests/e2e/release-pages.spec.ts`.

### Expert Appendix Screenshot Inventory

Use these as appendix screenshots only when the customer or acceptance reviewer asks for implementation detail.

| Appendix | Page / state | Acceptance note |
| --- | --- | --- |
| A1 | SOC expert overview | Shows KPI cards and SOC dashboard charts. |
| A2 | Security validation expert page | Shows expert batch import and evidence chain sections. |
| A3 | Evidence center | Shows WAF/ZAP/Trivy/Wazuh/Suricata/Zeek-style evidence rows. |
| A4 | Alert detail | Shows rule ID, rule name, source type, event type, action, target URL, demo case ID, and batch ID. |
| A5 | Ticket timeline | Shows Demo Range creation source, state transitions, and playbook task checklist. |
| A6 | Playbook tasks | Shows recommended playbook and at least one employee cooperation task when applicable. |
| A7 | Report center | Shows generated security validation report summary. |
| A8 | Policy and rule center | Shows local check policy list, adapter mappings, response playbooks, safe precheck, publish/disable controls, and change audit. |
| A9 | Correlation rule center | Shows `/soc/policies` -> `事件关联规则`, rule lifecycle, validation, and publish/disable controls. |
| A10 | System management | Shows administrator-only user, role, menu, log, or platform configuration handoff pages. |

## Screenshot Gallery Appendix

The following screenshots were captured from the local delivery runtime at `http://127.0.0.1:5174` with a `1440x1000` viewport. The full screenshot inventory is also recorded in `docs/screenshots/manifest.json`.

### Public Pages

#### 登录页

![登录页](screenshots/01-login.png)

#### 401 未授权页

![401 未授权页](screenshots/02-error-401.png)

#### 403 无权限页

![403 无权限页](screenshots/03-error-403.png)

#### 500 服务错误页

![500 服务错误页](screenshots/04-error-500.png)

#### 404 未找到页

![404 未找到页](screenshots/05-error-404.png)

### SOC Expert Pages

#### 安全总览

![安全总览](screenshots/10-soc-dashboard.png)

#### 平台能力说明

![平台能力说明](screenshots/11-soc-capabilities.png)

#### 安全验证

![安全验证](screenshots/12-soc-demo-range.png)

#### 告警处置

![告警处置](screenshots/13-soc-alerts.png)

#### 检测规则中心

![检测规则中心](screenshots/14-soc-rules.png)

#### 策略与规则中心

`/soc/policies` 由管理员或安全工程师维护。本阶段支持本机检查策略、事件适配映射、处置剧本和风险评分策略：新增/编辑、启用/停用、发布草稿、安全预检、归一化预览、评分权重维护和变更审计。事件适配映射不会下发到生产 WAF/IDS，也不发送真实外部通知；风险评分只基于已有 SOC 数据计算展示分值，不执行扫描或自动修复。

#### 告警降噪

![告警降噪](screenshots/15-soc-alert-noise.png)

#### 资产视图

![资产视图](screenshots/16-soc-assets.png)

资产视图显示统一 `风险分`，资产详情抽屉显示可解释风险画像、主要风险因子、建议动作和单资产重新计算入口。重新计算只读取已有告警、漏洞、基线、FIM、多源事件、工单和剧本任务数据。

#### 漏洞中心

![漏洞中心](screenshots/17-soc-vulnerabilities.png)

#### 基线核查

![基线核查](screenshots/18-soc-baselines.png)

#### 文件完整性

![文件完整性](screenshots/19-soc-fim.png)

#### 证据中心

![证据中心](screenshots/20-soc-external-events.png)

#### 工单中心

![工单中心](screenshots/21-soc-tickets.png)

#### 报告中心

![报告中心](screenshots/22-soc-reports.png)

#### 系统配置

![系统配置](screenshots/23-soc-settings.png)

### System Pages

#### 仪表盘

![仪表盘](screenshots/30-dashboard.png)

#### 用户管理

![用户管理](screenshots/31-system-user.png)

#### 部门管理

![部门管理](screenshots/32-system-dept.png)

#### 岗位管理

![岗位管理](screenshots/33-system-post.png)

#### 角色管理

![角色管理](screenshots/34-system-role.png)

#### 菜单管理

![菜单管理](screenshots/35-system-menu.png)

#### 字典管理

![字典管理](screenshots/36-system-dict.png)

#### 系统日志

![系统日志](screenshots/37-system-log.png)

#### 文件管理

![文件管理](screenshots/38-system-file.png)

#### 导入导出日志

![导入导出日志](screenshots/39-system-excel-logs.png)

#### 编号规则

![编号规则](screenshots/40-system-biz-sequence.png)

#### 流程日志

![流程日志](screenshots/41-system-biz-flow-log.png)

#### 通知公告

![通知公告](screenshots/42-system-notice.png)

#### 参数配置

![参数配置](screenshots/43-system-config.png)

### Client Pages

#### 我的电脑

`/client/workbench` 是普通员工默认入口。第一屏只回答三个问题：当前电脑是否安全、员工需要做什么、下一步应该点哪个按钮。主按钮是 `查看我的待办`，辅助入口是 `提交日志` 和 `开始本机检查`。

![我的电脑](screenshots/50-client-workbench.png)

#### 设备信息

![设备信息](screenshots/51-client-device-admin.png)

#### 提交日志

![提交日志](screenshots/52-client-data-report.png)

#### 我的待办

![我的待办](screenshots/53-client-operations.png)

#### 本机检查

`/client/local-range` 是二级任务页，不作为默认一级导航。页面按 `确认设备 -> 选择检查项 -> 运行检查 -> 提交记录` 组织，只展示当前电脑上下文。员工只能运行安全团队预设的只读检查项；技术命令、诊断信息和原始输出默认收起到抽屉中。

![本机检查](screenshots/54-client-local-range.png)

## Acceptance Cases

| Case | Action | Expected result |
| --- | --- | --- |
| AC-01 | Login as demo admin | Redirects to `/soc/dashboard`. |
| AC-02 | Open `/showcase` | Independent customer demo shell loads without the complex admin left menu. |
| AC-03 | Click `开始安全验证` | Page scrolls to the 5-step guided flow. |
| AC-04 | Backend unavailable on `/showcase` | Recoverable error card shows retry, offline demo data, and diagnostics actions. |
| AC-05 | Use offline demo data | Page is still usable and clearly marked as `离线演示数据`. |
| AC-06 | Open technical evidence drawer | Drawer shows normalized fields and sample JSON; main page remains business-readable. |
| AC-07 | Import live demo batch when backend is available | Returns a stable `batchId` and refreshes evidence, alert, vulnerability, ticket, and report metrics. |
| AC-08 | Navigate from `/showcase` to expert pages | Evidence, alert, ticket, and report entry points preserve the current `batchId` context where supported. |
| AC-09 | Generate security validation report | Report summary includes batch event, alert, vulnerability, block, ticket, and notification counts. |
| AC-10 | Confirm notification boundary | Dry-run records are visible; no real webhook, email, Feishu, WeCom, DingTalk, or Slack sender is used. |
| AC-11 | Open `/soc/incidents` and click `执行关联` | The Correlation Engine creates or refreshes incident clusters from existing WAF/ZAP/Trivy/Wazuh/Suricata/Zeek evidence. |
| AC-12 | Open alert detail after correlation | `关联事件簇` shows the related evidence chain and can jump to `/soc/incidents`. |
| AC-13 | Convert an incident cluster to ticket | A SOC ticket and timeline entry are created without running commands or external integrations. |
| AC-14 | Open `/showcase` or `/soc/demo-range` after correlation | `本次验证事件链` shows the current batch incident clusters and links to `/soc/incidents`. |
| AC-15 | Open `/soc/policies` -> `事件关联规则` | Security engineers can create, edit, validate, publish, and disable structured correlation rules without scripts or external queries. |

## Security Incident Cluster

`/soc/incidents` is the analyst-facing view for CyberFusion Correlation Engine v1. It answers one question: which distributed evidence items are part of the same security case.

Use it after importing a Demo Range batch or after new SOC evidence arrives:

1. Open `/soc/incidents`.
2. Click `执行关联` to refresh clusters from existing SOC data.
3. Open a cluster and review its timeline, evidence, alerts, vulnerabilities, ticket link, score, and `relation_reason`.
4. Convert the cluster to a ticket when human handling is needed, or close it with a reason when the case is understood.
5. Maintain rule thresholds from `/soc/policies` -> `事件关联规则` only as an administrator or security engineer.

The engine is not black-box AI. It uses structured fields such as `assetIp`, `hostname`, `batchId`, `demoCaseId`, `sourceType`, `eventType`, `ruleId`, `targetUrl`, severity, and time window. It does not run scans, commands, payloads, automatic remediation, or real notifications.

## Correlation Engine Operator Path

1. Open `/soc/demo-range` or `/showcase` and import an offline demo batch.
2. Click `执行关联` in `本次验证事件链`, or open `/soc/incidents` and click `执行关联`.
3. Open the generated incident cluster.
4. Review `可解释证据`: every row includes source type, event type, related record id, score, and relation reason.
5. Open `/soc/alerts`, choose the related alert, and check `关联事件簇`.
6. Convert the incident cluster to a ticket when manual handling is needed.
7. Open `/soc/policies` -> `事件关联规则` only when a security engineer needs to adjust thresholds, grouping fields, source sequence, or lifecycle status.

The Correlation Engine only reads existing SOC records and writes cluster/evidence/ticket timeline records. It does not execute scans, payloads, shell commands, automatic remediation, or real notifications.

## Common Bugs And Triage Commands

| Symptom | Likely cause | Commands |
| --- | --- | --- |
| Browser shows connection refused on `5174` | Frontend dev server is not running or used another port. | `lsof -iTCP:5174 -sTCP:LISTEN`; `pnpm --dir frontend dev --host 127.0.0.1 --port 5174` |
| Frontend shows backend 500 | Backend cannot reach MySQL/Redis or schema is missing. | `lsof -iTCP:18080 -sTCP:LISTEN`; `docker compose -f deploy/docker-compose.yml ps`; `mysql -h 127.0.0.1 -P 3306 -u root -p cyberfusion_soc -e "SHOW TABLES;"` |
| Login fails | Seed data not applied or wrong database selected. | `mysql -h 127.0.0.1 -P 3306 -u root -p cyberfusion_soc -e "SELECT username,status FROM sys_user;"` |
| Admin login fails but analyst/operator can login | Local admin password was changed after seed, or smoke uses the old demo password. | Set `CYBERFUSION_ADMIN_PASSWORD` for smoke, or use the documented local fallback `admin123` if that is the current local demo password. |
| `/soc/rules` not in menu | Menu seed not applied in this database. | `mysql -h 127.0.0.1 -P 3306 -u root -p cyberfusion_soc -e "SELECT id,name,path,permission FROM sys_menu WHERE path='/soc/rules';"` |
| Demo batch import returns no vulnerability | Trivy sample did not import or was already refreshed. | Check `/soc/vulnerabilities`; run `SELECT cve_id,software_name,severity FROM soc_vulnerability ORDER BY updated_at DESC LIMIT 10;` |
| Notification appears missing | Dry-run logs are filtered or not generated yet. | Open notification log page; run `SELECT channel_name,status,message FROM soc_notification_log ORDER BY created_at DESC LIMIT 10;` |
| `/soc/incidents` is empty | Demo evidence has not been imported or correlation has not been executed. | Import a demo batch, then run `POST /api/soc/incidents/correlate`; check `SELECT cluster_no,status,correlation_key FROM soc_incident_cluster ORDER BY updated_at DESC LIMIT 10;` |
| Incident rule changes do not take effect | Rule is draft/disabled or has not been published. | Check `/soc/policies` -> `事件关联规则`; run `SELECT rule_key,rule_type,status,enabled FROM soc_correlation_rule;` |
| `/soc/incidents` is not in the menu | Menu seed or existing database patch has not been applied. | Run `scripts/smoke/check-visibility.sh --base-url http://127.0.0.1:5174 --api-base-url http://127.0.0.1:18080/api`; check `SELECT path,permission FROM sys_menu WHERE path='/soc/incidents';` |
| `/soc/incidents` or `/soc/correlation-rules` returns 500 | Current database is missing the correlation tables or the backend was not restarted after applying SQL. | Apply `sql/schema.sql` and `sql/data.sql` idempotently, verify `soc_incident_cluster` and `soc_correlation_rule`, then restart the backend from current `backend/target/classes`. |
| Employee receives 403 on incident APIs | Expected permission boundary. Employees cannot access expert incident-cluster management. | Confirm the same request succeeds as admin or analyst; do not grant employee incident permissions. |
| Duplicate clusters appear after repeated runs | Correlation key, batch fields, or close status are inconsistent. | Check `SELECT cluster_no,correlation_key,status,evidence_count FROM soc_incident_cluster ORDER BY updated_at DESC LIMIT 20;` |
| Alert detail has no related incident | Correlation has not run after alert creation or the alert lacks structured batch/correlation fields. | Run `POST /api/soc/incidents/correlate`; query `GET /api/soc/alerts/{id}/related-incidents`. |
| Docker Demo Range compose fails | Environment variables or Docker daemon are missing. | `docker compose -f deploy/demo-range/docker-compose.yml config` |
| ZAP/Trivy containers try to use network images | Image not present locally and network is restricted. | Use compose `config` for validation; pull images only in authorized environments. |

## Safety Boundaries

- Use demo logs and offline JSON only.
- Do not scan public or third-party targets.
- Do not connect to production WAF, IDS, SIEM, email, or webhook systems during demo acceptance.
- Do not store real tokens, API keys, private keys, customer data, uploaded files, logs, or database files in source.
- Keep runtime data under Environment.

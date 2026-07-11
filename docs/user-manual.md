# User Manual

## Audience

This manual is for customer demos, internal acceptance, and operator handoff of CyberFusion SOC with the offline Demo Range. The system is a defensive SOC platform. It does not provide attack automation, public target scanning, real webhook sending, or production WAF/IDS rule deployment.

## Login And Navigation

1. Open the frontend, usually `http://127.0.0.1:5174`.
2. Sign in with the local demo administrator account created by `sql/data.sql`.
3. For customer-facing demos, open `/showcase` first. This page is the guided 5-step customer demo shell.
4. Use `SOC 专家后台` only when the audience needs operator-level details:
   - `/soc/user-workspaces`: 先选择普通用户及其主机工作区，再进入对应业务页面；管理员可从系统用户管理跳转到指定用户。
   - `/soc/dashboard`: 安全总览.
   - `/soc/demo-range`: 安全验证.
   - `/soc/external-events`: 证据中心.
   - `/soc/alerts`: 告警处置.
   - `/soc/tickets`: 工单中心.
   - `/soc/reports`: 报告中心.
   - `/soc/rules`: 检测内容规则设置.
   - `/soc/policies`: 策略与规则中心.
5. Use `系统管理` only for administrator handoff. System management pages are not part of the customer demo path.

## Demo Mode And Expert Mode

| Mode | Entry | Audience | Purpose | Notes |
| --- | --- | --- | --- | --- |
| 客户演示模式 | `/showcase` | Customers and non-security stakeholders | Show the 5-step story: risk, evidence, alert, ticket, report. | Default demo path; no complex tables on the first screen. |
| SOC 专家模式 | `/soc/dashboard` and other `/soc/*` pages | SOC analysts and implementation teams | Inspect evidence, alerts, assets, rules, tickets, and reports in detail. | Keeps all original SOC pages and compatible routes. |
| 系统管理 | `/system/*` and system settings | Administrators | Manage users, roles, menus, dictionaries, logs, and platform parameters. | Visible from navigation only for admin users. |
| 员工端 | `/client/workbench` | Regular employees | Let employees understand current computer status and submit logs or tasks. | Default employee navigation is 我的电脑 / 我的待办 / 提交日志. |

## Role Navigation And View Density

B1 keeps every compatible route, but changes the default navigation density by role.

| Role | Default entry | Navigation density | Default viewMode |
| --- | --- | --- | --- |
| `super_admin` / local `admin` | `/soc/dashboard` | Full groups: 工作区, 安全运营, 处置闭环, 策略治理, 平台管理. | `expert` |
| `admin` / platform administrator | `/soc/dashboard` | SOC expert backend plus system management. | `detail` |
| `security_engineer` / `security_admin` | `/soc/policies` | Policy governance, event adapter, playbook, risk scoring, correlation, algorithm governance. | `detail` |
| `analyst` / `security_analyst` | `/soc/dashboard` | Operations workbench, incident clusters, alerts, tickets, asset risk, reports. | `detail` |
| `employee` / `ops` / `user` | `/client/workbench` | Security Keeper only: 我的电脑, 我的待办, 提交日志, 安全工具, 安全日志. | `simple` |
| `customer` / `demo` | `/showcase` | Customer demo shell only by default. | `simple` |

`expert` mode shows diagnostics, raw evidence, relation reasons, adapter mapping entries, policy version, and audit entry points. `simple` mode keeps conclusion, next action, and key metrics on the first screen, with technical details kept in drawers.

Backend permissions remain authoritative. Employee and customer-class roles do not receive effective `soc:*`, `system:*`, or `dashboard:view` permissions even if an old local database still contains stale menu rows.

## 用户工作区与验证数据

“安全运营工作台”保持全局驾驶舱，不显示用户卡片。Agent 安装直接进入原有安装页；Agent 管理可在“全局 Agent 管理”和“按用户查看”之间切换。

管理员进入安全运营的具体业务页时，会看到与目标页面对应的用户卡片，例如事件簇卡片显示开放/高危事件簇。处置闭环的工单中心和报告中心默认保留原有全局总览，并可切换到“按用户查看”；两类卡片分别显示工单或报告专属指标和按钮。选择用户后，各业务数据按该用户的 `ownerId` 过滤。用户选择页是业务中转页，不在侧栏提供独立菜单。

张彥是当前开发机的真实采集用户。松松、老曹、刘哥用于权限和闭环验证，页面会明确显示“预置验证数据”，不应被解释为实际远程设备或生产告警。管理员可以在 `系统管理 -> 用户管理` 的“安全工作区”入口直接检查指定用户。

## Project Structure And Function Architecture

CyberFusion SOC is organized into five frontend experiences and one backend data-control layer.

### Frontend Structure

| Area | Route family | Main users | Purpose |
| --- | --- | --- | --- |
| Public and error pages | `/login`, `/401`, `/403`, `/500`, `/:pathMatch(.*)*` | All users | Authentication, recoverable failure states, and access-denied explanations. |
| Customer demo shell | `/showcase` | Customers, sales engineers, acceptance reviewers | Presents the complete SOC value chain as a guided story instead of a dense backend console. |
| SOC expert backend | `/soc/*` | SOC analysts, security engineers, admins | Evidence, alerts, incident clusters, assets, vulnerabilities, reports, policies, integrations, and governance. |
| Employee security assistant | `/client/*` | Regular employees | Shows current computer status, pending tasks, safe local checks, log submission, and personal security history. |
| System administration | `/dashboard`, `/system/*` | Administrators | User, role, menu, dictionary, audit, file, workflow, notice, and platform configuration management. |

### Backend And Data Structure

| Domain | Core tables | Data flow |
| --- | --- | --- |
| Multi-source evidence | `soc_external_event`, `soc_vulnerability` | Offline WAF/ZAP/Wazuh/Suricata/Zeek logs are normalized into external events; Trivy-style findings write vulnerability records. |
| Alert handling | `soc_alert`, `soc_ticket`, `soc_ticket_timeline`, `soc_ticket_task` | Import with `linkAlerts=true` creates or links alerts; alerts can become tickets; playbooks create manual task checklists. |
| Correlation engine | `soc_correlation_rule`, `soc_incident_cluster`, `soc_incident_evidence` | Existing evidence, alerts, vulnerabilities, and tickets are grouped into explainable incident clusters. |
| Risk and recommendations | `soc_asset`, `soc_asset_risk_snapshot`, `soc_risk_scoring_policy` | Asset risk uses existing SOC data, policy weights, ticket status, employee tasks, and closed-loop reductions. |
| Algorithm governance | `soc_algorithm_evaluation`, `soc_algorithm_evaluation_item` | A7 dry-run replay previews incident, risk, recommendation, and trend outcomes without writing production results. |
| Employee security assistant | `soc_client_checkup`, `soc_client_checkup_item`, employee task and local-check evidence records | Employee pages aggregate current-computer data into safe, plain-language status, actions, and logs. |
| Policy center | `soc_local_check_command`, adapter policy tables, playbook tables, risk and correlation rule tables | Security engineers maintain allowlisted commands, field mappings, defensive playbooks, scoring weights, and rule lifecycle. |
| System governance | `sys_user`, `sys_role`, `sys_menu`, `sys_role_menu`, `sys_operation_log`, `sys_login_log` | RBAC menus and backend permissions enforce admin, analyst, security engineer, and employee boundaries. |

### Core Demo Data Flow

1. `/showcase` or `/soc/demo-range` imports an offline Demo Range batch.
2. WAF/ZAP/Wazuh/Suricata/Zeek records write `soc_external_event`; Trivy records write `soc_vulnerability`.
3. `linkAlerts=true` creates or links `soc_alert` records.
4. Correlation Engine groups related evidence into `soc_incident_cluster` and `soc_incident_evidence`.
5. Risk scoring updates explainable asset risk from existing SOC records.
6. Recommendation ranking prioritizes incident, vulnerability, ticket, and employee-task actions.
7. Playbook application creates or reuses `soc_ticket` and manual `soc_ticket_task` records.
8. Employee pages expose only current-computer tasks, checks, and logs.
9. Report center generates `security_validation` reports and dry-run notification logs.
10. Algorithm governance can replay the batch as dry-run to evaluate rules without changing production entities.

### Page Function Reference

| Route | Page | Main functions | Main data / APIs |
| --- | --- | --- | --- |
| `/login` | 登录 | Local demo login, clear failed-login hint, health diagnosis entry when backend is unavailable. | `/api/auth/login`, `/api/health`. |
| `/showcase` | 安全运营演示台 | Customer-facing 5-step story: evidence import, incident cluster, alert, ticket, report, and expert-mode jumps. | Demo batch import, evidence chain, dashboard, incidents, reports, fallback offline demo data. |
| `/soc/dashboard` | 安全总览 | SOC KPIs, alert trend, severity distribution, affected assets, risk analytics, Top incidents, Top risk assets, recommendations, trend anomalies. | `soc_alert`, `soc_external_event`, `soc_incident_cluster`, risk/recommendation/trend APIs. |
| `/soc/capabilities` | 平台能力说明 | Defensive product capability map, integration boundary, dry-run safety explanation. | Static capability model plus SOC integration metadata. |
| `/soc/demo-range` | 安全验证 | Offline demo batch import, batch status, topology, evidence chain, incident chain, ticket/report/notification entries. | Demo Range import API, evidence chain API, report API, notification dry-run log. |
| `/soc/alerts` | 告警处置 | Alert list, filters, detail drawer, related incident clusters, playbook suggestions, convert to ticket, false-positive/close handling. | `soc_alert`, `soc_incident_cluster`, `soc_response_playbook`, `soc_ticket`. |
| `/soc/incidents` | 安全事件簇 | Correlation execution, cluster list, evidence timeline, relation reasons, ticket conversion, close workflow. | `soc_incident_cluster`, `soc_incident_evidence`, `soc_correlation_rule`, ticket APIs. |
| `/soc/rules` | 检测内容规则设置 | Configure unified detection content, severity, publish state and hit preview for ingested source rules. | Existing event/alert tables, built-in rule catalog, and `soc_detection_rule_policy`. |
| `/soc/policies` | 策略与规则中心 | Maintains host checks, event field mappings, manual response templates, and audit views. | Policy tables, adapter tables, playbook tables, and audit APIs. |
| `/soc/assets` | 资产视图 | Asset inventory, risk level, risk profile drawer, single-asset recalculation, linked incidents and recommendations. | `soc_asset`, `soc_asset_risk_snapshot`, alert/vulnerability/ticket factors. |
| `/soc/client-security` | 员工终端安全态势 | Employee Security Keeper coverage, high-risk computers, pending tasks, local-check submissions. | Client checkups, tasks, assets, risk profile, ticket/task APIs. |
| `/soc/vulnerabilities` | 漏洞中心 | Vulnerability list, severity filters, Trivy demo evidence, asset linkage. | `soc_vulnerability`. |
| `/soc/baselines` | 基线核查 | Baseline failure and pass records, asset baseline posture. | Baseline records from existing SOC data. |
| `/soc/fim` | 文件完整性 | File-change evidence and review state. | FIM-style external evidence and normalized fields. |
| `/soc/external-events` | 外部事件 | 外部访问、主机外联、外部扫描和 IOC 情报风险。 | Zeek、Suricata、WAF、ZAP、Trivy、MISP、OpenCTI 的规范化风险记录与告警联动。 |
| `/soc/tickets` | 工单中心 | Ticket list, ticket timeline, playbook tasks, employee task state, report linkage. | `soc_ticket`, `soc_ticket_timeline`, `soc_ticket_task`. |
| `/soc/reports` | 报告中心 | Security validation report generation, readable report detail sections, dry-run notification boundary display. | `soc_report`, report generation API, notification dry-run logs. |
| `/soc/settings` | 系统配置 | Data source and notification settings, integration catalog, dry-run sender state. | Integration settings, notification logs, config APIs. |
| `/dashboard` | 仪表盘 | Generic admin dashboard and platform overview. | System dashboard APIs. |
| `/system/user` | 用户管理 | Account CRUD, enable/disable, role assignment, reset password. | `sys_user`, `sys_user_role`. |
| `/system/dept` | 部门管理 | Department tree, data-scope basis, organization maintenance. | `sys_dept`. |
| `/system/post` | 岗位管理 | Post dictionary and user-position support. | `sys_post`. |
| `/system/role` | 角色管理 | Role CRUD, menu permissions, data-scope configuration. | `sys_role`, `sys_role_menu`. |
| `/system/menu` | 菜单管理 | Route menu, button permission, icon, visibility, and sort management. | `sys_menu`. |
| `/system/dict` | 字典管理 | Dictionary type and dictionary data management. | `sys_dict_type`, `sys_dict_data`. |
| `/system/log` | 系统日志 | Login audit and operation audit review. | `sys_login_log`, `sys_operation_log`. |
| `/system/file` | 文件管理 | Uploaded file metadata and controlled file viewing. | `sys_file`. |
| `/system/excel/logs` | 导入导出日志 | Import/export task history and result tracking. | `sys_import_export_log`. |
| `/system/workflow/biz-sequence` | 编号规则 | Business number prefix/date/current-value rules for tickets and workflows. | `sys_biz_sequence`. |
| `/system/workflow/biz-flow-log` | 流程日志 | Business flow transition records and traceability. | `sys_biz_flow_log`. |
| `/system/notice` | 通知公告 | Internal notices and publication state. | `sys_notice`. |
| `/system/config` | 参数配置 | Platform key-value parameters and built-in config guardrails. | `sys_config`. |
| `/client/workbench` | CyberFusion 安全管家 | Current safety status, score, main risk reasons, pending tasks, one-click checkup entry, repair/log/tool shortcuts. | Security Keeper APIs, risk profile, tasks, local check history. |
| `/client/device-admin` | 设备信息 | Current-device context and details. | Current user asset and runtime context. |
| `/client/data-report` | 提交日志 | Employee log submission, local context redaction, safe evidence upload path. | Client data report API and external-event linkage. |
| `/client/tasks` | 我的待办 | Employee-visible tasks, repair suggestions, evidence submission, confirmation actions. | `soc_ticket_task`, client task APIs. |
| `/client/operations` | 我的待办兼容路由 | Backward-compatible route to the same employee task experience. | Same as `/client/tasks`. |
| `/client/local-range` | 安全工具箱 | Read-only local checks from active backend policy, no free shell input, technical output drawer. | `/api/client/local-terminal/commands`, `/api/client/local-terminal/run`. |
| `/client/security-logs` | 安全日志 | Checkup, local check, log submission, task, risk-state, and employee confirmation history. | Security Keeper logs API. |

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
| 2:00 | Read the story line | `/showcase` | The story line shows: 证据导入 -> 事件簇 -> 风险评分 -> 推荐动作 -> 工单处置 -> 员工待办 -> 报告输出. Each card has status, count, explanation, and a jump entry. |
| 3:00 | Import evidence | `/showcase` or `/soc/demo-range` | Import the offline batch when backend is available, or use clearly marked offline demo data when the backend is unavailable. |
| 4:00 | Review event cluster and evidence | `/showcase` to `/soc/incidents` | The `安全事件簇` page shows which WAF/ZAP/Trivy/Wazuh/Suricata/Zeek evidence belongs to the same case and why each item is related. |
| 5:00 | Review risk and recommendations | `/soc/assets` and `/soc/dashboard` | Risk profile explains why the asset is high risk; Top recommendations show the next actions without automatic repair. |
| 6:00 | Apply playbook and convert to ticket | `/soc/incidents` or `/soc/alerts` then `/soc/tickets` | Apply the recommended defensive playbook or convert the incident cluster to a ticket, then show the task checklist plus Demo Range timeline source. |
| 7:00 | Show employee collaboration | `/client/workbench` and `/client/tasks` | Employee Security Keeper shows current computer status, pending tasks, and safe read-only local-check entry points. |
| 8:00 | Generate report | `/showcase` to `/soc/reports` | Generate or open the `security_validation` report for this batch. The detail drawer shows 管理摘要 / 技术证据 / 处置进度 / 员工配合 / 安全边界. |
| 9:00 | Confirm dry-run boundary | `/soc/reports` and notification logs | Confirm the report says no public scanning, no real notification sender, and no attack execution. |

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

The current full manual screenshot set is stored under `docs/screenshots/*.png` and indexed in `docs/screenshots/manifest.json`. The older `acceptance-*.png` smoke set is no longer the complete manual inventory.

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

For an already initialized Windows no-Docker database, refresh schema and seed data idempotently instead of deleting data:

```powershell
cd E:\CyberFusion\00-cyberfusion-platform
$env:DB_HOST = "127.0.0.1"
$env:DB_PORT = "3306"
$env:DB_NAME = "cyberfusion_soc"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "replace-with-local-db-password"
.\scripts\win\init-local-db.ps1
```

For the macOS/Linux Docker-backed local path:

```sh
docker exec -i cyberfusion-platform-mysql-1 sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" cyberfusion_soc' < sql/schema.sql
docker exec -i cyberfusion-platform-mysql-1 sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" cyberfusion_soc' < sql/data.sql
docker exec -i cyberfusion-platform-mysql-1 sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" cyberfusion_soc' < scripts/sql/apply-latest-menu-and-policy-seed.sql
```

Then restart the backend so the running `/api` process loads the latest compiled classes. If a Java process was started before the latest Maven build, policy, adapter, playbook, or employee task APIs can still return old-runtime 500 errors even after the database has been refreshed.

The user menu in the admin and employee shells shows a lightweight version/build marker. Use it to confirm whether the browser is seeing the expected runtime.

## Screenshot Checklist

Screenshots are regenerated from the local delivery runtime and stored under `docs/screenshots`. The authoritative inventory is `docs/screenshots/manifest.json`, generated with a `1440x1000` viewport against `http://127.0.0.1:5174`.

Core customer acceptance screenshots:

| Screenshot | File | Page / state | Acceptance note |
| --- | --- | --- | --- |
| 1 | `06-showcase.png` | `/showcase` | Customer-facing demo shell, current risk, story line, and guided actions. |
| 2 | `12-soc-demo-range.png` | `/soc/demo-range` | Offline batch import, evidence chain, alert, ticket, report, and dry-run notification entry. |
| 3 | `14-soc-incidents.png` | `/soc/incidents` | Correlation Engine event clusters, evidence counts, reasons, status, and ticket/close actions. |
| 4 | `24-soc-assets.png` | `/soc/assets` | Asset risk score, risk explanation, and single-asset recalculation entry. |
| 5 | `21-soc-policies-algorithm.png` | `/soc/policies` -> `算法治理` | A7 algorithm governance cards and dry-run replay form. |
| 6 | `30-soc-tickets.png` | `/soc/tickets` | Ticket handling, timeline, and playbook task checklist. |
| 7 | `31-soc-reports.png` | `/soc/reports` | `security_validation` report list and readable report sections. |
| 8 | `60-client-workbench.png` | `/client/workbench` | Employee Security Keeper status, next actions, and safe tool entry. |

Full screenshot inventory:

| Area | File | Route or state | Description |
| --- | --- | --- | --- |
| Public | `01-login.png` | `/login` | Login page and local demo account hint. |
| Public | `02-error-401.png` | `/401` | Unauthorized state. |
| Public | `03-error-403.png` | `/403` | Permission denied state. |
| Public | `04-error-500.png` | `/500` | Recoverable server error state. |
| Public | `05-error-404.png` | unknown route | Not-found state. |
| Demo | `06-showcase.png` | `/showcase` | Security operations demo console. |
| SOC | `10-soc-dashboard.png` | `/soc/dashboard` | Security overview and operational KPIs. |
| SOC | `11-soc-capabilities.png` | `/soc/capabilities` | Platform capability and integration boundaries. |
| SOC | `12-soc-demo-range.png` | `/soc/demo-range` | Security validation batch workflow. |
| SOC | `13-soc-alerts.png` | `/soc/alerts` | Alert handling center. |
| SOC | `14-soc-incidents.png` | `/soc/incidents` | Security incident clusters. |
| SOC | `15-soc-rules.png` | `/soc/rules` | Detection rule center. |
| SOC | `16-soc-policies-local-check.png` | `/soc/policies` | Local check policy tab. |
| SOC | `17-soc-policies-adapters.png` | `/soc/policies` -> `事件适配映射` | Event adapter mapping tab. |
| SOC | `18-soc-policies-playbooks.png` | `/soc/policies` -> `处置剧本` | Response playbook tab. |
| SOC | `19-soc-policies-risk.png` | `/soc/policies` -> `风险评分策略` | Risk scoring policy tab. |
| SOC | `20-soc-policies-correlation.png` | `/soc/policies` -> `事件关联规则` | Correlation rule tab. |
| SOC | `21-soc-policies-algorithm.png` | `/soc/policies` -> `算法治理` | Algorithm governance and replay tab. |
| SOC | `22-soc-policies-audit.png` | `/soc/policies` -> `变更审计` | Policy change audit tab. |
| SOC | `24-soc-assets.png` | `/soc/assets` | Asset risk view. |
| SOC | `25-soc-client-security.png` | `/soc/client-security` | Employee endpoint security posture. |
| SOC | `26-soc-vulnerabilities.png` | `/soc/vulnerabilities` | Vulnerability center. |
| SOC | `27-soc-baselines.png` | `/soc/baselines` | Baseline checks. |
| SOC | `28-soc-fim.png` | `/soc/fim` | File integrity monitoring. |
| SOC | `29-soc-external-events.png` | `/soc/external-events` | Evidence center. |
| SOC | `30-soc-tickets.png` | `/soc/tickets` | Ticket center. |
| SOC | `31-soc-reports.png` | `/soc/reports` | Report center. |
| SOC | `32-soc-settings.png` | `/soc/settings` | Data source and notification settings. |
| System | `40-dashboard.png` | `/dashboard` | Generic management dashboard. |
| System | `41-system-user.png` | `/system/user` | User account management. |
| System | `42-system-dept.png` | `/system/dept` | Department management. |
| System | `43-system-post.png` | `/system/post` | Post management. |
| System | `44-system-role.png` | `/system/role` | Role and data-scope management. |
| System | `45-system-menu.png` | `/system/menu` | Menu and permission management. |
| System | `46-system-dict.png` | `/system/dict` | Dictionary management. |
| System | `47-system-log.png` | `/system/log` | Login and operation logs. |
| System | `48-system-file.png` | `/system/file` | File metadata management. |
| System | `49-system-excel-logs.png` | `/system/excel/logs` | Import/export logs. |
| System | `50-system-biz-sequence.png` | `/system/workflow/biz-sequence` | Business sequence rules. |
| System | `51-system-biz-flow-log.png` | `/system/workflow/biz-flow-log` | Business flow logs. |
| System | `52-system-notice.png` | `/system/notice` | Notices. |
| System | `53-system-config.png` | `/system/config` | System parameters. |
| Client | `60-client-workbench.png` | `/client/workbench` | CyberFusion Security Keeper. |
| Client | `61-client-device-admin.png` | `/client/device-admin` | Device information. |
| Client | `62-client-data-report.png` | `/client/data-report` | Log submission. |
| Client | `63-client-tasks.png` | `/client/tasks` | My tasks. |
| Client | `64-client-operations.png` | `/client/operations` | Compatibility route for my tasks. |
| Client | `65-client-local-range.png` | `/client/local-range` | Security toolbox and read-only local check. |
| Client | `66-client-security-logs.png` | `/client/security-logs` | Employee security logs. |

## Screenshot Gallery Appendix

### Public Pages

#### 登录页

![登录页](screenshots/01-login.png)

#### 401 未授权

![401 未授权](screenshots/02-error-401.png)

#### 403 无权限

![403 无权限](screenshots/03-error-403.png)

#### 500 服务错误

![500 服务错误](screenshots/04-error-500.png)

#### 404 未找到

![404 未找到](screenshots/05-error-404.png)

### Customer Demo

#### 安全运营演示台

![安全运营演示台](screenshots/06-showcase.png)

### SOC Expert Pages

#### 安全总览

![安全总览](screenshots/10-soc-dashboard.png)

#### 平台能力说明

![平台能力说明](screenshots/11-soc-capabilities.png)

#### 安全验证

![安全验证](screenshots/12-soc-demo-range.png)

#### 告警处置

![告警处置](screenshots/13-soc-alerts.png)

#### 安全事件簇

![安全事件簇](screenshots/14-soc-incidents.png)

从“每日处理”进入事件簇后，按以下顺序推进闭环：先点击“开始研判”记录核查范围；高危和严重事件必须转为处置工单；在工单中完成处置任务、填写复核结论并关闭或归档工单；返回事件簇查看“闭环检查”，补齐证据链和阻塞项后提交不少于 12 个字符的最终结论。满足条件才允许关闭事件簇，关闭后仍保留证据链和工单时间线。

#### 检测内容规则设置

![检测内容规则设置](screenshots/15-soc-rules.png)

#### 策略与规则中心：本机检查策略

![策略与规则中心：本机检查策略](screenshots/16-soc-policies-local-check.png)

#### 策略与规则中心：事件适配映射

![策略与规则中心：事件适配映射](screenshots/17-soc-policies-adapters.png)

#### 策略与规则中心：处置剧本

![策略与规则中心：处置剧本](screenshots/18-soc-policies-playbooks.png)

#### 策略与规则中心：风险评分策略

![策略与规则中心：风险评分策略](screenshots/19-soc-policies-risk.png)

#### 策略与规则中心：事件关联规则

![策略与规则中心：事件关联规则](screenshots/20-soc-policies-correlation.png)

#### 策略与规则中心：算法治理

![策略与规则中心：算法治理](screenshots/21-soc-policies-algorithm.png)

#### 策略与规则中心：变更审计

![策略与规则中心：变更审计](screenshots/22-soc-policies-audit.png)

### 算法治理与回放评估

`/soc/policies` -> `算法治理` 用于解释和评估现有确定性算法，而不是新增检测能力。页面覆盖四类对象：

- 事件关联规则。
- 风险评分策略。
- 推荐排序规则。
- 趋势异常规则。

管理员或安全工程师可以查看每类对象的 active、draft、disabled 数量、最近运行时间、最近命中数量、误报/忽略/关闭统计、数据来源覆盖、最近变更人和版本。

回放评估流程：

1. 打开 `/soc/policies`。
2. 选择 `算法治理`。
3. 输入演示批次，例如 `DEMO-RANGE-OFFLINE-V1`，或选择时间范围。
4. 选择 `active` 或 `draft` 策略模式。
5. 点击 `回放评估`。
6. 查看 dry-run 预览：预计事件簇、风险分变化、推荐动作、趋势异常，以及和当前 active 结果的差异。
7. 展开预览表，查看每一条 `reason`。例如同一资产在窗口内出现 WAF、ZAP、Wazuh 证据，因此会被关联为事件簇。

安全边界：

- 回放不会创建真实事件簇。
- 回放不会更新资产风险快照。
- 回放不会创建工单、报告或通知。
- 如果开启 `保存评估记录`，只写入 `soc_algorithm_evaluation` 和 `soc_algorithm_evaluation_item`，用于后续复盘。
- 分析员只能查看评估结果，不能执行回放。
- 员工端账号不能访问算法治理接口。

#### 资产视图

![资产视图](screenshots/24-soc-assets.png)

资产视图显示统一 `风险分`，资产详情抽屉显示可解释风险画像、主要风险因子、建议动作和单资产重新计算入口。重新计算只读取已有告警、漏洞、基线、FIM、多源事件、工单和剧本任务数据。

#### 员工终端安全态势

![员工终端安全态势](screenshots/25-soc-client-security.png)

#### 漏洞中心

![漏洞中心](screenshots/26-soc-vulnerabilities.png)

#### 基线核查

![基线核查](screenshots/27-soc-baselines.png)

#### 文件完整性

![文件完整性](screenshots/28-soc-fim.png)

文件完整性页面只展示已授权主机目录的元数据变化。要新增监控对象，进入 `策略与规则中心 -> 文件监控授权`，选择已登记的主机、操作系统、明确目录、用途和递归上限，保存并发布。Agent 在下一次采集周期读取授权，首次创建基线，后续记录新增、修改、删除和权限变化。文件内容不会上传；根目录、通配符和路径穿越不允许发布。

#### 证据中心

![证据中心](screenshots/29-soc-external-events.png)

#### 工单中心

![工单中心](screenshots/30-soc-tickets.png)

#### 报告中心

![报告中心](screenshots/31-soc-reports.png)

#### 系统配置

![系统配置](screenshots/32-soc-settings.png)

### System Pages

#### 仪表盘

![仪表盘](screenshots/40-dashboard.png)

#### 用户管理

![用户管理](screenshots/41-system-user.png)

#### 部门管理

![部门管理](screenshots/42-system-dept.png)

#### 岗位管理

![岗位管理](screenshots/43-system-post.png)

#### 角色管理

![角色管理](screenshots/44-system-role.png)

#### 菜单管理

![菜单管理](screenshots/45-system-menu.png)

#### 字典管理

![字典管理](screenshots/46-system-dict.png)

#### 系统日志

![系统日志](screenshots/47-system-log.png)

#### 文件管理

![文件管理](screenshots/48-system-file.png)

#### 导入导出日志

![导入导出日志](screenshots/49-system-excel-logs.png)

#### 编号规则

![编号规则](screenshots/50-system-biz-sequence.png)

#### 流程日志

![流程日志](screenshots/51-system-biz-flow-log.png)

#### 通知公告

![通知公告](screenshots/52-system-notice.png)

#### 参数配置

![参数配置](screenshots/53-system-config.png)

### Client Pages

#### 我的电脑

`/client/workbench` 是普通员工默认入口。第一屏只回答三个问题：当前电脑是否安全、员工需要做什么、下一步应该点哪个按钮。主按钮是 `查看我的待办`，辅助入口是 `提交日志` 和 `开始本机检查`。

![我的电脑](screenshots/60-client-workbench.png)

#### 设备信息

![设备信息](screenshots/61-client-device-admin.png)

#### 提交日志

![提交日志](screenshots/62-client-data-report.png)

#### 我的待办

![我的待办](screenshots/63-client-tasks.png)

#### 处置操作兼容入口

![处置操作兼容入口](screenshots/64-client-operations.png)

#### 本机检查

`/client/local-range` 是二级任务页，不作为默认一级导航。页面按 `确认设备 -> 选择检查项 -> 运行检查 -> 提交记录` 组织，只展示当前电脑上下文。员工只能运行安全团队预设的只读检查项；技术命令、诊断信息和原始输出默认收起到抽屉中。

![本机检查](screenshots/65-client-local-range.png)

#### 安全日志

![安全日志](screenshots/66-client-security-logs.png)

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
| AC-16 | Open `/soc/policies` -> `算法治理` | Admin and security engineers can see correlation, risk scoring, recommendation, and trend anomaly governance cards. |
| AC-17 | Run algorithm replay for a demo batch | Dry-run preview returns explainable reasons and does not create real incident clusters, tickets, reports, risk snapshots, or notifications. |
| AC-18 | Access algorithm governance as employee | Backend returns `403`; employee cannot view or execute algorithm governance. |

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
| Frontend shows backend 500 | Backend cannot reach MySQL/Redis or schema is missing. | Windows: `.\scripts\win\dev-doctor.ps1 -BaseUrl http://127.0.0.1:5174 -ApiBaseUrl http://127.0.0.1:18080/api`; `mysql -h 127.0.0.1 -P 3306 -u root -p cyberfusion_soc -e "SHOW TABLES;"`. macOS/Linux: `lsof -iTCP:18080 -sTCP:LISTEN`; `docker compose -f deploy/docker-compose.yml ps`. |
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
| Algorithm governance page is empty | Evaluation tables or policy seed are missing, or current user lacks `soc:algorithm:view`. | Apply `sql/schema.sql`/`sql/data.sql`, restart backend, run `GET /api/soc/algorithm-center/overview` as admin. |
| Algorithm replay appears to change real results | Replay should only save optional evaluation records; incident/ticket/report counts must not change. | Compare `GET /api/soc/incidents`, `/soc/tickets`, and `/soc/reports` totals before and after `POST /api/soc/algorithm-center/replay`. |
| Analyst cannot run replay | Expected permission boundary. Analysts can view evaluation results but need security-engineer permission for replay. | Confirm `GET /api/soc/algorithm-center/evaluations` succeeds and `POST /api/soc/algorithm-center/replay` returns 403. |
| Docker Demo Range compose fails | Environment variables or Docker daemon are missing. | `docker compose -f deploy/demo-range/docker-compose.yml config` |
| ZAP/Trivy containers try to use network images | Image not present locally and network is restricted. | Use compose `config` for validation; pull images only in authorized environments. |

## Safety Boundaries

- Use demo logs and offline JSON only.
- Do not scan public or third-party targets.
- Do not connect to production WAF, IDS, SIEM, email, or webhook systems during demo acceptance.
- Do not store real tokens, API keys, private keys, customer data, uploaded files, logs, or database files in source.
- Keep runtime data under Environment.

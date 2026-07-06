# CyberFusion SOC Function Architecture And Data Flow

## Current System Function Classification

This section is the current high-level function model for reports, presentations, and defense-oriented handoff documents. The system should not be described as a flat list of pages or a loose collection of tools. The correct top-level view is:

```text
CyberFusion SOC 统一安全运营平台
├── C1 多源检测接入
├── C2 证据归一与规则
├── C3 关联研判与风险
├── C4 处置闭环与报告
├── C5 自动化与员工协同
└── C6 平台治理与审计
```

For presentation purposes, Wazuh, Zeek, Suricata, Trivy, ZAP, MISP, Sigma, CyberChef, and Shuffle are treated as CyberFusion's core functional capabilities because they are the visible detection, enrichment, field-analysis, and automation functions shown to the audience. The implementation boundary is still important: CyberFusion owns the unified portal, import adapters, normalized evidence model, workflow, permission, algorithm governance, and reporting layer; the upstream projects remain referenced or integrated security tools.

### C1 Multi-source Detection Ingestion

C1 answers: "Where does CyberFusion get security evidence from?"

| Core function | Current CyberFusion role | Main UI/API/data entry |
| --- | --- | --- |
| Wazuh host threat center | Host security evidence, login/configuration change evidence, and FIM evidence enter the SOC host-threat view. | `/soc/settings`, `/soc/external-events`, `GET /api/soc/settings/wazuh-health`, `POST /api/soc/external-events/cyberfusion/import`, `soc_wazuh_config`, `soc_external_event`, `soc_file_integrity_event` |
| Zeek network detection center | Zeek traffic metadata is imported as network context, source/destination IP evidence, service evidence, and correlation input. | `/soc/external-events`, `POST /api/soc/external-events/cyberfusion/import`, `soc_external_event` |
| Suricata intrusion detection center | Suricata EVE events become IDS alerts, signature hits, severity-mapped evidence, and linked SOC alerts. | `/soc/external-events`, `POST /api/soc/external-events/suricata/import`, `soc_external_event`, `soc_alert` |
| Trivy vulnerability center | Trivy JSON is normalized into dependency/package vulnerability rows and feeds asset risk scoring and remediation reports. | `/soc/vulnerabilities`, `POST /api/soc/external-events/cyberfusion/import`, `soc_vulnerability` |
| ZAP Web exposure center | ZAP baseline findings are imported as authorized Web risk evidence and can be linked to alerts or reports. | `/soc/external-events`, `/soc/vulnerabilities`, `POST /api/soc/external-events/cyberfusion/import`, `soc_external_event` |
| MISP threat intelligence center | Authorized IOC evidence becomes searchable threat-intel evidence and can promote alert/incident priority. | `/soc/external-events`, `POST /api/soc/external-events/cyberfusion/import`, `soc_external_event` |
| Sigma detection rule reference | Sigma-style rule metadata and rule-hit evidence support rule catalog display and hit preview. | `/soc/rules`, `GET /api/soc/rules`, `GET /api/soc/rules/hits`, `soc_external_event`, `soc_alert` |
| Demo Range / WAF evidence | Offline validation evidence proves the platform's evidence-to-alert-to-report chain without live public scanning. | `/soc/demo-range`, `POST /api/soc/demo-range/batches/import`, `GET /api/soc/demo-range/batches/{batchId}/evidence-chain` |

The import pattern is unified: most source types use `POST /api/soc/external-events/cyberfusion/import`; Trivy writes vulnerability rows; other eligible sources write `soc_external_event` and can create or refresh `soc_alert` when `linkAlerts=true`.

### C2 Evidence Normalization And Rule Management

C2 answers: "How do different tool outputs become one SOC evidence model?"

| Submodule | Function detail | Main implementation objects |
| --- | --- | --- |
| Unified event model | Normalizes `sourceType`, `eventType`, `severity`, `assetIp`, `eventTime`, `ruleId`, `batchId`, `demoCaseId`, `correlationKey`, and source-specific fields. | `SocOperationService`, `soc_external_event`, `soc_alert` |
| Adapter mapping | Maintains source-to-normalized field mappings, severity mappings, dedup keys, and alert title templates. | `/soc/policies`, `soc_event_adapter_profile`, `soc_event_field_mapping`, `soc_event_severity_mapping`, `soc_event_alert_link_rule` |
| Rule catalog | Shows built-in safe rule catalog plus live event/alert hits; no separate online rule-execution table is required for the current phase. | `/soc/rules`, `GET /api/soc/rules`, `GET /api/soc/rules/adapter-mappings` |
| Hit preview | Lets analysts see which evidence and alerts matched one source/rule pair. | `GET /api/soc/rules/hits?sourceType=...&ruleId=...` |
| Alert noise control | Supports whitelist, ignored/false-positive handling, adapter transparency, and noise-reduction visibility. | `/soc/alert-noise`, `soc_alert_whitelist`, `soc_alert` |
| Evidence center | Provides the analyst-facing list/detail/status surface for all imported security evidence. | `/soc/external-events`, `GET /api/soc/external-events`, `GET /api/soc/external-events/{id}` |

This layer is the bridge between tool-specific data and platform-level SOC operations. It is also where the system explains mapping failures and keeps safe fallback behavior when an active adapter is missing.

### C3 Correlation, Analysis, And Risk

C3 answers: "How does CyberFusion decide what matters first?"

| Capability | Algorithm or logic type | Output |
| --- | --- | --- |
| Unified alert analysis | Deterministic status and severity handling over linked alerts. | Alert queue, alert detail, related incidents, ticket conversion |
| Incident correlation | Explainable rule/threshold/window grouping over existing evidence, alerts, vulnerabilities, assets, and batch keys. | `soc_incident_cluster`, `soc_incident_evidence`, `relation_reason` |
| Asset risk scoring | Numeric weighted scoring over alerts, vulnerabilities, baselines, FIM, incidents, tickets, employee tasks, and checkups. | `soc_asset_risk_snapshot`, `soc_asset_risk_factor`, `soc_asset.risk_score`, `soc_asset.risk_level` |
| Recommendation ranking | Deterministic priority sorting for incident handling, vulnerability repair, ticket follow-up, employee tasks, and playbook tasks. | `/api/soc/recommendations/top`, asset recommendations, employee next actions |
| Trend anomaly detection | Read-only statistical comparison between current windows and prior baseline windows. | `/api/soc/trends/*`, dashboard trend cards, report summaries |
| Algorithm governance | Version status, dry-run replay, saved evaluation, and explainable preview without writing production results. | `/soc/policies`, `/api/soc/algorithm-center/*`, `soc_algorithm_evaluation`, `soc_algorithm_evaluation_item` |

These are explainable rule, weight, aggregation, and replay mechanisms. They are not black-box AI, not LLM-driven automatic response, and not live scanner orchestration.

### C4 Response Closure And Reporting

C4 answers: "How is security work closed and proven?"

| Submodule | Function detail | Main objects |
| --- | --- | --- |
| Alert handling | Confirm, ignore, false-positive, close, or convert alert to ticket. | `/soc/alerts`, `soc_alert` |
| Ticket center | Create ticket, assign owner, track SLA, transition status, and expose ticket detail. | `/soc/tickets`, `soc_ticket` |
| Timeline | Records state changes, notes, evidence submission, playbook task changes, and employee confirmations. | `soc_ticket_timeline`, `soc_ticket_task` |
| Report center | Generates daily, monthly, and `security_validation` reports, with Excel or pseudo-PDF export paths. | `/soc/reports`, `soc_report` |
| Demo story | Shows evidence chain, incident cluster, risk score, recommendation, ticket progress, employee task, and report output in a presentation-friendly sequence. | `/showcase`, `/soc/demo-range`, `DemoRangeEvidenceChain` |
| Operations metrics | Gives managers SLA, risk trend, recommendation adoption, employee-task completion, playbook use, and notification dry-run indicators. | `/soc/dashboard`, `/api/soc/operations/*` |

This layer is the platform's closed-loop proof: evidence becomes alert, alert becomes case or ticket, ticket creates timeline, and reports summarize the result.

### C5 Automation And Employee Collaboration

C5 answers: "How do analysts, automation examples, and employee-side actions work together?"

| Submodule | Function detail | Safety boundary |
| --- | --- | --- |
| Shuffle automation examples | Notification templates and workflow examples write `DRY_RUN` logs for demo or validation evidence. | No real webhook, email, or chat sender is enabled by default. |
| CyberChef field analysis | Local field helper decodes or extracts URLs, IPs, domains, Base64/Hex hints, and IOC candidates. | Analysis helper only; it does not exfiltrate data or execute payloads. |
| Security Keeper checkup | Employee-side Web checkup aggregates existing SOC records for the authenticated employee's device. | No command execution, scan, auto-fix, public target, or external sender. |
| Local check policy | Admin-maintained safe read-only command policies are stored as argv arrays and exposed only when active. | Backend validates and resolves `commandKey`; browser-submitted command text is not trusted. |
| Employee tasks | Playbook or ticket tasks become employee-visible to-dos and confirmations. | Task confirmation writes timeline/status records only. |
| Repair recommendations | Converts alerts, vulnerabilities, tickets, tasks, and checkups into employee-readable action guidance. | Guidance and status records only; no automatic remediation. |

The collaboration layer turns SOC findings into concrete human work while keeping execution auditable and bounded.

### C6 Platform Governance And Audit

C6 answers: "How is the platform controlled, scoped, and audited?"

| Governance area | Current implementation |
| --- | --- |
| Auth and session | `POST /api/auth/login`, `POST /api/auth/refresh`, `GET /api/auth/me`; Spring Security backend enforcement. |
| RBAC and menu | `sys_user`, `sys_role`, `sys_menu`, `sys_user_role`, `sys_role_menu`; dynamic frontend routes from menu data with backend permission checks. |
| Data scope | Department and role scopes are applied by backend security services before returning SOC records. |
| System configuration | `/system/config`, `/system/dict`, `/system/notice`, `/system/file`, `/system/workflow/*`. |
| Audit | Login logs, operation logs, import/export logs, flow logs, notification logs, playbook logs, and algorithm evaluation logs. |
| Health diagnosis | `/api/health/liveness`, `/api/health/readiness`, Wazuh health, sync tasks, and platform readiness checks. |

The governance layer should be drawn as a platform foundation, not as a separate security tool. It supports every functional category above.

### Current Technology Stack Snapshot

The active platform root is `00-cyberfusion-platform`.

| Layer | Stack |
| --- | --- |
| Backend | Java 21, Spring Boot 3.5.3, Spring Security, Spring AOP, Spring Validation, Spring Data Redis, MyBatis-Plus 3.5.12, Springdoc OpenAPI 2.8.9, MySQL Connector/J, Lombok, Maven |
| Frontend | Vue 3.5, Vite 7, TypeScript 5.8, Element Plus 2.10, Pinia 3, Vue Router 4, Axios 1.12, ECharts 5.6, pnpm 11 |
| Database/cache | MySQL 8 with `sys_*` governance tables and `soc_*` SOC tables; Redis for cache/session support |
| Testing and validation | Spring Boot tests, Playwright browser smoke tests, Vite build/typecheck, API smoke scripts, health readiness checks |
| Local integration catalog | `integrations/catalog.json` maps core tool functions to local integration paths and safe CyberFusion API entrypoints |

### Current Windows No-Docker Runtime Path

For Windows delivery and classroom/demo laptops, the current `00-cyberfusion-platform` project now has a no-Docker startup path. This path keeps the existing Spring Boot/Vue architecture unchanged and only replaces Docker-managed infrastructure with local or reachable services.

| Runtime part | Windows no-Docker implementation |
| --- | --- |
| Required local tools | JDK 21, Maven 3.9+, Node.js 20+, pnpm, MySQL 8 server/client, Redis-compatible server, PowerShell |
| Database initialization | `scripts/win/init-local-db.ps1` applies `sql/schema.sql`, `sql/data.sql`, then `scripts/sql/apply-latest-menu-and-policy-seed.sql` through local `mysql.exe` |
| One-command startup | `scripts/win/start-no-docker.ps1` prepares D drive folders, runs pre-start verification, calls `run-dev.ps1`, then runs post-start verification |
| Backend runtime | `scripts/win/backend-dev.ps1` or `run-dev.ps1` starts Spring Boot on `SERVER_PORT=18080` by default |
| Frontend runtime | `scripts/win/frontend-dev.ps1` or `run-dev.ps1` starts Vite on `FRONTEND_PORT=5174` and points `VITE_API_PROXY_TARGET` to `http://127.0.0.1:18080` |
| Runtime root | `CYBERFUSION_ENV_ROOT`, defaulting to `D:\CyberFusion\Environment\cyberfusion-platform`, stores uploads, backend logs, backups, local VM evidence, Maven cache, pnpm store, and npm cache outside source |
| Diagnosis | `scripts/win/dev-doctor.ps1` checks frontend/backend connectivity, `/api/health`, key SOC tables, seed rows, menus, permissions, and role boundaries |

Windows no-Docker mode expects the source project under `D:\CyberFusion\00-cyberfusion-platform` and runtime data under `D:\CyberFusion\Environment\cyberfusion-platform`. It does not launch MySQL or Redis. Operators must start those two services first. The bundled SQL seed files currently create and seed the fixed database name `cyberfusion_soc`; custom database names require a manually prepared schema and `start-no-docker.ps1 -SkipDbInit`.

### Drawing Guidance For Function Module Diagrams

For future PPT or documentation diagrams, use this hierarchy:

1. First page: draw only the total function categories `C1-C6` under `CyberFusion SOC`.
2. Second level: draw one page per category, for example C1 expands into Wazuh, Zeek, Suricata, Trivy/ZAP, MISP/Sigma, and Demo Range.
3. Third level: draw one detailed module page for a concrete function, for example Wazuh expands into connection configuration, host alert import, FIM normalization, and alert linkage.
4. Keep support capabilities under the bottom/foundation layer: unified API, RBAC/data scope, MySQL/Redis, audit logs, algorithm governance, and safety boundaries.
5. Avoid drawing tools, pages, APIs, and database tables in one flat layer. Tools are functional capabilities for the report, but APIs and tables are implementation/support elements.

## Integration Program Catalog

CyberFusion now keeps curated integration-side programs under the root `integrations/` directory. The sibling `01-16` upstream directories remain reference origins, but demos, smoke tests, and handoff work should use the local paths recorded in `integrations/catalog.json` or returned by `GET /api/soc/integrations/catalog`.

The catalog maps each source to its local program directory and safe CyberFusion API entrypoint. Most evidence sources use:

```text
POST /api/soc/external-events/cyberfusion/import
```

Helper integrations keep their existing safe endpoints:

- `POST /api/soc/external-events/cyberchef/analyze`
- `POST /api/soc/external-events/shuffle/demo-notification`
- `GET /api/soc/settings/wazuh/check`

This is a source-organization change only. Runtime logs, databases, Docker volumes, uploads, API tokens, certificates, and customer data still remain outside source under Environment.

## Local Check Policy Flow

The employee-side local check flow now separates maintainable policy from the execution boundary.

1. Security engineer opens `/soc/policies`.
2. The backend stores local check policy rows in `soc_local_check_command`.
3. Each policy stores `command_argv_json` as a JSON array, not as a shell string.
4. Publishing a policy runs backend safety validation before it can become `active`.
5. Employee opens `/client/local-range`.
6. Frontend calls `GET /api/client/local-terminal/commands?os={Linux|macOS|Windows}`.
7. Backend returns only `active + enabled` rows for that OS.
8. If no active rows exist for that OS, backend returns built-in safe defaults and marks them as fallback.
9. Employee selects a check item. The frontend submits only `commandKey`, `assetIp`, and `osType`.
10. Backend resolves `commandKey + osType` from `soc_local_check_command`; it does not trust command text from the browser.
11. Backend executes the argv through `ProcessBuilder`.
12. Backend writes the result into SOC evidence (`soc_external_event`) and optionally linked alert (`soc_alert`).

## Safety Boundary

- No arbitrary shell input is accepted.
- `command_argv_json` cannot contain shell metacharacters such as `;`, `|`, `&`, redirects, backticks, `$()`, `${}`, or newlines.
- Shell launchers and high-risk tools are rejected, including `sh`, `bash`, `zsh`, `cmd`, `powershell`, `curl`, `wget`, `nmap`, `masscan`, `hydra`, `sqlmap`, package installers, Docker, and similar tools.
- The local-run loopback restriction remains in place.
- The employee UI never exposes disabled or draft policies.
- The fallback catalog remains available only to avoid breaking demos when the database has not been initialized.

## Role Navigation And View Density Flow

B1 adds a lightweight role-experience layer over the existing RBAC model. It does not delete routes or business functions.

1. Login still loads roles, permissions, and menu rows from `sys_user`, `sys_role`, `sys_menu`, and `sys_role_menu`.
2. Backend `RolePermissionBoundary` filters employee/customer-class effective permissions so stale local menu grants cannot expose `soc:*`, `system:*`, or `dashboard:view`.
3. Frontend maps compatible role names into personas:
   - `super_admin` and local `admin` -> full expert view.
   - `security_engineer` and `security_admin` -> policy governance view.
   - `analyst` and `security_analyst` -> operations view.
   - `employee`, `ops`, and `user` -> Security Keeper view.
   - `customer` and `demo` -> showcase view.
4. The router chooses the default entry from that persona, while all compatible routes remain registered and still require backend permission.
5. `viewMode` controls density:
   - `simple`: conclusion, next action, key metrics.
   - `detail`: operator tables and drawer details.
   - `expert`: raw JSON, diagnostics, full `relation_reason`, adapter mapping, policy versions, and audit entry points.
6. Super administrators default to `expert`; employees and customer/demo accounts default to `simple`.

## Policy Tables

`soc_local_check_command` stores local read-only check policy:

- `command_key`: stable key submitted by the employee UI.
- `display_name`: employee-facing label.
- `os_type`: `Linux`, `macOS`, or `Windows`.
- `category`: `identity`, `network`, `process`, `startup`, `host`, or `custom_readonly`.
- `command_argv_json`: argv JSON array.
- `timeout_seconds`, `output_limit_kb`: execution guardrails.
- `enabled`, `status`: employee visibility and lifecycle state.
- `version`, `approved_by`, `approved_at`: release and audit metadata.

## Event Adapter Policy Flow

WAF/ZAP/Trivy/Wazuh/Suricata/Zeek event adapter mappings are now maintainable from `/soc/policies` without allowing online scripts or executable rule logic.

1. Security engineer opens `/soc/policies` and selects `事件适配映射`.
2. The backend stores adapter lifecycle rows in `soc_event_adapter_profile`.
3. Field mappings are stored in `soc_event_field_mapping`.
4. Severity mappings are stored in `soc_event_severity_mapping`.
5. Alert link rules are stored in `soc_event_alert_link_rule`.
6. Before publishing, backend validation checks source field paths, normalized field names, transform types, severity values, dedup key arrays, and alert title templates.
7. `/api/soc/external-events/cyberfusion/import` first tries the active adapter profile for the incoming `sourceType`.
8. If no active adapter exists, or if the active adapter fails validation/preview, the import falls back to the existing built-in adapter.
9. Preview calls under `/api/soc/policies/event-adapters/{id}/preview` return normalized output, dedup key, severity, and alert-link decision without writing `soc_external_event`, `soc_alert`, or `soc_vulnerability`.
10. Trivy import still writes vulnerabilities through the existing vulnerability-center path; the Trivy adapter profile is used for transparency and preview.

## Adapter Safety Boundary

- `source_field_path` only supports simple JSON dot paths or comma-separated first-non-empty paths.
- No script engines, SpEL, Groovy, SQL snippets, regex execution, shell commands, or external calls are accepted.
- `transform_type` is limited to `direct`, `string`, `number`, `timestamp`, `lowercase`, `uppercase`, `join`, and `first_non_empty`.
- `dedup_key_fields_json` must be a JSON array of normalized field names.
- `alert_name_template` only supports `{fieldName}` placeholders.
- Disabled or draft adapters are not used by the import endpoint.

## Response Playbook Flow

CyberFusion separates response playbook maintenance from automated remediation.

1. Security engineer opens `/soc/policies` and selects `处置剧本`.
2. Backend stores playbook lifecycle rows in `soc_response_playbook`.
3. Backend stores human response steps in `soc_response_playbook_step`.
4. Alert detail calls `GET /api/soc/alerts/{id}/playbook-suggestions`.
5. Backend matches only `active + enabled` playbooks by `sourceType`, `eventType`, `ruleIdPattern`, and minimum severity.
6. Analyst applies a playbook with `POST /api/soc/alerts/{id}/apply-playbook`.
7. Backend reuses or creates the linked `soc_ticket`.
8. Backend creates manual checklist rows in `soc_ticket_task`.
9. Backend writes `soc_playbook_match_log` and a `soc_ticket_timeline` entry.
10. Ticket detail returns task checklist rows together with the existing timeline.
11. Employee-facing tasks are available through `GET /api/client/tasks`, scoped to the current authenticated employee.
12. Employee confirmation writes task evidence and a timeline record, but it does not run commands or change assets.

## Playbook Safety Boundary

- Playbooks contain no executable command, script, payload, scanner configuration, or automatic remediation step.
- Backend validation rejects shell metacharacters and common script, scanner, downloader, exploitation, destructive, or auto-fix wording in playbook text.
- Applying a playbook only creates task rows and timeline records.
- Ticket task actions only change task status: `pending`, `in_progress`, `submitted`, `confirmed`, `completed`, or `skipped`.
- No real WAF/IDS/SIEM/EDR configuration is changed.
- Notifications remain dry-run through the existing notification log path.
- Employee tasks are filtered by `assignee_type=employee` and current `assignee_id`.

## Asset Risk Scoring Flow

CyberFusion calculates an explainable asset risk profile from existing SOC records. The scoring engine is a defensive prioritization layer. It does not scan, attack, remediate, or connect to external systems.

1. Security engineer opens `/soc/policies` and selects `风险评分策略`.
2. Backend stores scoring policy lifecycle rows in `soc_risk_scoring_policy`.
3. `POST /api/soc/risk-scoring/recalculate` recalculates every in-scope asset from current database records.
4. `POST /api/soc/risk-scoring/recalculate/{assetId}` recalculates one asset.
5. Calculation reads existing rows from `soc_asset`, `soc_alert`, `soc_vulnerability`, `soc_baseline_check`, `soc_file_integrity_event`, `soc_external_event`, `soc_incident_cluster`, `soc_ticket`, `soc_ticket_task`, and `soc_client_checkup`.
6. Backend writes a point-in-time summary to `soc_asset_risk_snapshot`.
7. Backend writes explainable factors to `soc_asset_risk_factor`.
8. Backend updates `soc_asset.risk_score` and `soc_asset.risk_level` for list sorting and quick display.
9. Asset pages call `GET /api/soc/assets/{id}/risk-profile` and `GET /api/soc/assets/{id}/risk-history`.
10. Dashboard and `/showcase` can call `GET /api/soc/risk-scoring/top-assets`.
11. Employee workbench can call `GET /api/client/devices/{ip}/risk-profile`, scoped to the authenticated user's accessible computer.

## Risk Score Inputs

Positive factors increase priority:

- critical, high, and medium open alerts.
- critical and high open vulnerabilities.
- failed baseline checks.
- unreviewed FIM events.
- high-risk external evidence from WAF, ZAP, Wazuh, Suricata, Zeek, and related adapters.
- open security incident clusters.
- high or critical security incident clusters.
- overdue tickets.
- open response playbook tasks.
- employee pending tasks.
- recent employee Security Keeper checkup status of `attention`, `warning`, `serious`, or `critical`.
- production or critical asset hints.
- internet-exposed or gateway/IDS evidence.

Negative factors reduce priority:

- closed tickets.
- completed response playbook tasks.

The final score is clipped to `0..maxScore` and mapped to:

- `0-29`: low.
- `30-59`: medium.
- `60-79`: high.
- `80-100`: critical.

## Risk Scoring Safety Boundary

- Risk policies only contain numeric weights and descriptive text.
- Backend rejects policy text containing script, SQL, shell, downloader, scanner, expression, or external-call wording.
- No policy row can store executable logic, payloads, commands, public URLs, notification targets, or automation scripts.
- Recalculation only reads existing SOC tables and writes snapshot/factor records.
- Recalculation does not start ZAP, Trivy, Wazuh, Suricata, Zeek, Docker, shell commands, or external webhooks.
- Incident clusters, playbook tasks, employee tasks, and Security Keeper checkups are scored as explainable context only. They do not trigger automatic remediation.
- If no active database policy exists, the backend uses an internal numeric fallback policy so demos do not fail with a 500.
- Data-scope checks are applied before returning admin and employee risk profiles.

## Correlation Engine v1 Flow

CyberFusion Correlation Engine v1 is a defensive evidence correlation layer. It only reads existing SOC rows and writes event-cluster records. It does not scan, attack, run shell commands, call OpenSearch, run Python ML, or connect to external systems.

1. Multi-source import writes structured fields to `soc_external_event`: `batch_id`, `demo_case_id`, `target_url`, `action`, `request_id`, and `correlation_key`.
2. Linked alerts write structured fields to `soc_alert`: `event_type`, `target_url`, `action`, `evidence_summary`, `batch_id`, `demo_case_id`, and `correlation_key`.
3. Security engineers maintain lifecycle rows in `soc_correlation_rule`.
4. Analyst runs `POST /api/soc/incidents/correlate`.
5. Backend loads only `active + enabled` rules. If no active rule exists, it uses a built-in safe fallback rule.
6. Backend filters source type, event type, severity floor, active status, and data scope, then groups in-scope `soc_external_event`, `soc_alert`, and `soc_vulnerability` rows by configured `group_by_json` plus time-window.
7. Backend writes or refreshes `soc_incident_cluster` by stable `correlation_key`.
8. Backend upserts `soc_incident_evidence` rows and writes a readable `relation_reason` for every evidence item.
9. Alert detail calls `GET /api/soc/alerts/{id}/related-incidents`.
10. Asset detail can call `GET /api/soc/assets/{id}/incidents`.
11. Incident detail can be converted into a ticket with `POST /api/soc/incidents/{id}/ticket`.

### Correlation Data Objects

| Object | Role in the flow |
| --- | --- |
| `soc_correlation_rule` | Stores lifecycle-managed rule metadata, source/event filters, grouping fields, time windows, thresholds, and weights. It does not store scripts or external query logic. |
| `soc_incident_cluster` | Stores the analyst-facing security case: title, asset, severity, status, score, counts, first/last seen time, recommendation, ticket link, and stable `correlation_key`. |
| `soc_incident_evidence` | Stores the explainable relationship between a cluster and each evidence row. Every row records evidence type, source/event type, score, and human-readable `relation_reason`. |

### Explainable Scoring Inputs

The v1 rule score is deterministic and explainable. It can use same asset IP, same hostname, same `batchId` or `demoCaseId`, same rule id, same target URL, same time window, cross-source evidence such as WAF + ZAP + Wazuh/Zeek/Suricata/Trivy, high severity, and linked alert or vulnerability presence.

### Frontend Visibility Flow

- `/soc/dashboard` shows Top 5 priority incident clusters.
- `/soc/incidents` lists clusters and opens detail with timeline, evidence, alert, vulnerability, ticket, score, and relation reason.
- `/soc/alerts` detail shows `关联事件簇`.
- `/soc/external-events` detail shows the event's incident-cluster membership when available.
- `/soc/demo-range` and `/showcase` show the current validation batch event chain.
- `/soc/policies` exposes `事件关联规则` for authorized administrators and security engineers.

This is not black-box AI. There is no model inference, no OpenSearch query language execution, no user-provided expression evaluation, and no external detector invocation. The engine persists readable relation reasons so analysts can explain why evidence was grouped.

### Correlation Rule Types

Implemented in v1:

- `event_count`: same grouping key reaches a threshold within a timeframe.
- `value_count`: same grouping key reaches a threshold of distinct structured values such as `targetUrl`, `ruleId`, or `eventType`.
- `frequency`: same grouping key repeats within a timeframe.
- `temporal`: related evidence appears in the same time window; when `sequence_json` is set, every listed source, event type, rule id, or evidence type must be present.
- `temporal_ordered`: ordered demo or validation-chain evidence appears in the same batch/window according to `sequence_json`.
- `cross_source_chain`: WAF/ZAP/Wazuh/Suricata/Zeek/Trivy evidence shares asset or batch context.

Reserved but not implemented in v1:

- spike.
- new_term.
- cardinality.

### Correlation Safety Boundary

- Rules store arrays, thresholds, time windows, severity floors, and descriptive text only.
- Backend rejects rule JSON that is not an array.
- Backend rejects script, shell, downloader, external URL, and command-execution wording in rule JSON.
- Rules do not contain user-provided expressions, SQL, scripts, payloads, scanners, or external query definitions.
- Incident correlation is manually triggered and idempotent by `correlation_key`.
- Closing an incident or converting it to a ticket only updates SOC records and timeline entries.

## Remediation Recommendation Ranking v1 Flow

CyberFusion A3 converts existing SOC context into explainable next actions. It is a deterministic prioritization layer, not an Agent, not black-box ML, and not an automatic remediation engine.

1. Dashboard calls `GET /api/soc/recommendations/top`.
2. Asset detail calls `GET /api/soc/assets/{id}/recommendations`.
3. Employee Security Keeper calls `GET /api/client/security-keeper/next-actions?assetIp={ip}`.
4. Backend reads existing records from `soc_incident_cluster`, `soc_vulnerability`, `soc_ticket`, `soc_ticket_task`, `soc_client_checkup`, `soc_asset_risk_snapshot`, and `soc_asset_risk_factor`.
5. Backend calculates a priority score from explicit rules:
   - high or critical incident clusters rank first.
   - high or critical vulnerabilities rank next.
   - overdue tickets are promoted.
   - incomplete response-playbook tasks are promoted.
   - employee-assigned pending tasks are promoted.
   - closed tickets, completed tasks, confirmed tasks, or already-recorded confirmations are down-ranked.
6. Each recommendation returns `title`, `priority`, `reason`, `recommendedAction`, `relatedBizType`, `relatedBizId`, `assigneeType`, and `status`.
7. Admin pages show analyst-facing recommendations such as event-cluster handling, vulnerability remediation, ticket follow-up, and playbook task completion.
8. Employee pages translate the same context into ordinary language such as:
   - `请先完成本机检查`.
   - `请提交安全日志`.
   - `请确认安全团队分配的待办`.
9. Recommendation adoption is recorded by `POST /api/soc/recommendations/{key}/record`.
10. Adoption records are stored in the existing `soc_client_recommendation_action` table with action types such as `view`, `apply_playbook`, `ticket`, `confirm`, and `note`.

### Recommendation Data Objects

| Object | Role in the flow |
| --- | --- |
| `soc_asset_risk_factor` | Provides explainable risk factors and related business object ids. |
| `soc_incident_cluster` | Supplies high-priority multi-source evidence clusters. |
| `soc_vulnerability` | Supplies unresolved component or dependency vulnerabilities. |
| `soc_ticket` | Supplies overdue or still-open work items. |
| `soc_ticket_task` | Supplies response playbook tasks and employee tasks. |
| `soc_client_checkup` | Supplies employee Security Keeper checkup status. |
| `soc_client_recommendation_action` | Stores recommendation adoption or view records; no new recommendation master table is required. |

### Recommendation Safety Boundary

- Recommendations only read existing SOC tables and write adoption records.
- They do not execute commands, run scanners, call external WAF/IDS/SIEM/EDR systems, send notifications, or modify host settings.
- They do not store scripts, expressions, payloads, public targets, tokens, API keys, passwords, or private keys.
- Employee requests are scoped to the current authenticated user's visible asset through existing data-scope checks.
- A recommendation can guide the user into existing playbook, ticket, or employee-task flows, but those flows remain manual and auditable.

## Trend Anomaly Detection v1 Flow

CyberFusion A4 adds a read-only statistical signal layer for analysts. It detects unusual changes in existing SOC records and explains the reason in plain operational terms. It is not black-box ML, not an Agent, not a scanner, and not an external collector.

1. Trend APIs read in-scope `soc_external_event` and `soc_alert` rows from the recent analysis window.
2. Backend applies existing `SocSecurityScope` filters before returning any trend result.
3. Signals are normalized to structured fields already present in the schema:
   - `assetIp`
   - `sourceType`
   - `eventType`
   - `ruleId`
   - `severity`
   - `eventTime`
4. `GET /api/soc/trends/aggregations` groups signals by `assetIp`, `sourceType`, `eventType`, `ruleId`, `severity`, and hour/day bucket.
5. `GET /api/soc/trends/anomalies` and `/api/soc/trends/anomalies/top` compare the current 24-hour window against the previous 7-day daily average.
6. Detection rules are deterministic:
   - volume spike: current count exceeds the 7-day average by a fixed ratio.
   - severity ratio rise: high/critical share rises materially over the baseline.
   - consecutive asset activity: one asset appears across multiple hourly windows.
   - cross-source rise: one asset has WAF/ZAP/Wazuh/Suricata/Zeek sources rising in the same current window.
7. Each anomaly returns `title`, `assetIp`, `sourceType`, `eventType`, `severity`, `currentCount`, `baselineCount`, `changeRatio`, `anomalyScore`, `reason`, and `recommendation`.
8. `/soc/dashboard` shows Trend Anomaly Top 5.
9. `/soc/assets` detail shows recent anomaly trends for the selected asset.
10. `/soc/external-events` shows trend hints matching the current filters.
11. Report generation reads the same Top 5 summary and includes it in the report summary and recommendation text.

### Trend Data Objects

No new database table is required in v1.

| Existing object | Role in trend detection |
| --- | --- |
| `soc_external_event` | Primary multi-source event signal table. It provides source, event type, rule, severity, asset, and event time. |
| `soc_alert` | Alert signal table. It supplements trends with linked alert severity, source, event type, asset, and event time. |
| `soc_asset` | Used by the asset page to choose the asset IP filter; trend calculation itself does not mutate assets. |
| `soc_report` | Stores generated report text that can include the trend anomaly summary. |

### Trend Safety Boundary

- Trend detection only reads existing SOC records and returns calculated summaries.
- It does not execute commands, run scans, connect to external WAF/IDS/SIEM/EDR systems, send notifications, or perform automatic remediation.
- It does not evaluate user-provided expressions, SQL, scripts, rules, or payloads.
- It does not store raw customer logs in a new table.
- Employee users cannot access `/api/soc/trends/*` because the endpoints require SOC dashboard, event, or asset permissions.

## SOC Operations Metrics Center v1 Flow

CyberFusion A5 adds a read-only operations metric layer for managers and analysts. It aggregates existing SOC records into explainable operation indicators. It does not add detection, Agent behavior, black-box ML, automatic repair, external sender behavior, or public scanning.

1. `/soc/dashboard` calls `GET /api/soc/operations/overview`.
2. The backend returns a metric catalog. Every metric includes:
   - `metricCode`
   - `metricName`
   - `value`
   - `trend`
   - `explanation`
   - `drilldownTarget`
3. The metric catalog reads existing data from:
   - `soc_incident_cluster`
   - `soc_asset`
   - `soc_asset_risk_snapshot`
   - `soc_ticket`
   - `soc_ticket_task`
   - `soc_playbook_match_log`
   - `soc_client_checkup`
   - `soc_client_recommendation_action`
   - `soc_notification_log`
   - A4 trend anomaly APIs.
4. Detailed endpoints expose the same data by operational topic:
   - `GET /api/soc/operations/sla`
   - `GET /api/soc/operations/risk-trend`
   - `GET /api/soc/operations/recommendation-adoption`
   - `GET /api/soc/operations/client-tasks`
5. `/soc/dashboard` shows:
   - open and high-risk incident clusters.
   - ticket total, pending, overdue, close rate, MTTA, and MTTR.
   - recommendation count and adoption rate.
   - playbook application count and task completion rate.
   - employee task total/completed/overdue counts.
   - Security Keeper checkup coverage.
   - risk trend changes and Top trend anomaly sources.
6. `security_validation` report generation appends the operations summary to the report text:
   - 24h and 7d risk change.
   - ticket efficiency.
   - recommendation adoption rate.
   - employee task completion rate.
   - dry-run notification count.

### Operations Metric Data Objects

No new database table is required in v1.

| Existing object | Role in operations metrics |
| --- | --- |
| `soc_incident_cluster` | Counts open/high-risk security cases and provides Top incident clusters. |
| `soc_asset` | Provides accessible assets and current risk score/risk level. |
| `soc_asset_risk_snapshot` | Provides 7-day risk trend points and 24h/7d score deltas. |
| `soc_ticket` | Provides total, pending, overdue, closed, MTTA, and MTTR metrics. |
| `soc_ticket_task` | Provides response playbook task completion and employee task completion. |
| `soc_playbook_match_log` | Counts playbook application events. |
| `soc_client_checkup` | Provides Security Keeper checkup coverage. |
| `soc_client_recommendation_action` | Stores recommendation views/adoptions used to calculate adoption rate. |
| `soc_notification_log` | Counts `DRY_RUN` notification evidence for reports. |

### Operations Safety Boundary

- Operations metrics only read existing SOC records and return aggregate summaries.
- Recommendation adoption reads/writes remain in the existing recommendation endpoint; the operations endpoints themselves do not mutate data.
- Operations metrics do not execute commands, run local terminal checks, start scanners, call external systems, send notifications, or modify tickets.
- Operations endpoints require SOC dashboard permission. Employee accounts cannot access `/api/soc/operations/*`.
- The metric explanations are derived from deterministic count/rate rules, not black-box AI or model inference.

## Report and Demo Story Upgrade v1 Flow

CyberFusion A6 is a presentation and reporting layer over existing SOC data. It does not add detection logic, Agent behavior, black-box ML, scanners, automated repair, or external senders.

1. `/showcase` loads the customer story from existing APIs:
   - Demo Range evidence chain.
   - external events, alerts, vulnerabilities, tickets, reports.
   - incident clusters.
   - Top risk asset profile.
   - Top recommendation actions.
   - operations overview.
2. The customer story is ordered as:
   - evidence import.
   - incident cluster.
   - risk scoring.
   - recommended action.
   - ticket handling.
   - employee task.
   - report output.
3. `/soc/demo-range` shows the same post-import outcome in expert mode:
   - `batchId`.
   - multi-source evidence count.
   - incident cluster count.
   - Top risk asset.
   - Top 3 recommendations.
   - ticket and employee-task status.
   - dry-run notification count.
4. `security_validation` report generation reads:
   - `DemoRangeEvidenceChain` summary.
   - `soc_incident_cluster` rows matching the batch or correlation key.
   - operations overview metrics.
   - Top risk asset, recommendation adoption, client-task metrics, and trend anomaly summary.
5. The report stores business-readable text in existing `soc_report.summary` and `soc_report.recommendation` fields. No new report table is required.
6. `/soc/reports` parses the report summary markers and displays:
   - management summary.
   - technical evidence.
   - handling progress.
   - employee collaboration.
   - safety boundary.
7. Export continues to reuse the existing pseudo PDF and Excel export paths.

### Report Story Data Objects

| Existing object | Role in A6 |
| --- | --- |
| `soc_external_event` | Evidence import count, source coverage, batch context, and technical evidence drawer. |
| `soc_alert` | Linked alert count and highest-priority risk explanation. |
| `soc_vulnerability` | Component vulnerability count and risk contribution. |
| `soc_incident_cluster` | Incident-chain count, evidence relationship count, and business story anchor. |
| `soc_asset` / `soc_asset_risk_snapshot` | Top risk asset, risk score, risk level, and 24h/7d score changes. |
| `soc_ticket` / `soc_ticket_task` | Handling progress, response playbook task status, and employee-task status. |
| `soc_client_recommendation_action` | Recommendation adoption count and adoption rate. |
| `soc_notification_log` | `DRY_RUN` notification evidence. |
| `soc_report` | Stores the generated `security_validation` report text and supports existing export. |

### Report Story Safety Boundary

- A6 only organizes existing SOC records and offline demo evidence into customer-facing copy.
- It does not run scans, execute commands, generate payloads, call external WAF/IDS/SIEM/EDR systems, send notifications, or repair endpoints.
- The report explicitly states: no public-network scanning, no real notification sending, and no attack execution.
- Employee accounts cannot access SOC report management APIs; they only see their scoped Security Keeper pages and assigned tasks.

## Algorithm Governance and Replay Evaluation v1 Flow

CyberFusion A7 adds a governance and dry-run evaluation layer over existing deterministic algorithms. It does not add new detection capability, Agent behavior, black-box ML, scanners, automatic remediation, or external integrations.

1. An administrator or security engineer opens `/soc/policies` and selects `算法治理`.
2. The frontend calls `GET /api/soc/algorithm-center/overview`.
3. The backend aggregates strategy state from:
   - `soc_correlation_rule` for event correlation rules.
   - `soc_risk_scoring_policy` for asset risk scoring policy versions.
   - built-in recommendation ranking logic backed by incident, vulnerability, ticket, employee-task, and checkup records.
   - built-in trend anomaly logic backed by recent external events and alerts.
4. The overview returns one card per governance object:
   - active, draft, and disabled strategy counts.
   - latest run time.
   - recent hit count.
   - false-positive, ignored, and closed counts where available.
   - source coverage.
   - latest updater and version.
5. For dry-run replay, the operator selects a `batchId` or a time range and chooses `active` or `draft` policy mode.
6. `POST /api/soc/algorithm-center/replay` reads existing `soc_external_event` and `soc_alert` records and produces preview results:
   - predicted incident clusters.
   - predicted asset risk score changes.
   - recommended actions.
   - trend anomaly previews.
   - diff summary against current incident, ticket, and report counts.
7. Each preview item includes an explainable `reason`.
8. If `saveEvaluation=true`, only evaluation evidence is stored:
   - `soc_algorithm_evaluation`
   - `soc_algorithm_evaluation_item`
9. Replay never writes real production results:
   - no `soc_incident_cluster` insert/update.
   - no `soc_asset_risk_snapshot` update.
   - no `soc_ticket` creation.
   - no `soc_report` generation.
   - no notification or external sender call.

### Algorithm Governance Data Objects

| Object | Role in A7 |
| --- | --- |
| `soc_correlation_rule` | Event correlation policy lifecycle and version source. |
| `soc_risk_scoring_policy` | Risk scoring policy lifecycle and version source. |
| `soc_external_event` | Replay input signal source for imported evidence. |
| `soc_alert` | Replay input signal source for linked alerts. |
| `soc_incident_cluster` | Current result baseline for diff comparison only. |
| `soc_asset` / `soc_asset_risk_snapshot` | Current risk baseline for dry-run risk delta preview only. |
| `soc_ticket` / `soc_report` | Current business-result counts for no-write comparison only. |
| `soc_algorithm_evaluation` | Optional saved dry-run evaluation header. |
| `soc_algorithm_evaluation_item` | Optional saved dry-run preview item and reason. |

### Algorithm Governance Permission Boundary

- `admin` and security engineers can view governance status and execute replay.
- `analyst` can read saved evaluation results but cannot execute replay.
- employee accounts cannot access algorithm governance endpoints.
- Permission is enforced in the backend with `soc:algorithm:view`, `soc:algorithm:replay`, and `soc:algorithm:evaluation`; frontend hiding is not the only control.

### Algorithm Governance Safety Boundary

- Replay reads already-imported SOC records only.
- Replay does not execute shell commands, scan networks, call external systems, run user-provided scripts, or send notifications.
- The implementation is deterministic and explainable. It is not black-box AI or ML.

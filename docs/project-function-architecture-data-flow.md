# CyberFusion SOC Function Architecture And Data Flow

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
5. Calculation reads existing rows from `soc_asset`, `soc_alert`, `soc_vulnerability`, `soc_baseline_check`, `soc_file_integrity_event`, `soc_external_event`, `soc_ticket`, and `soc_ticket_task`.
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
- overdue tickets.
- open response playbook tasks.
- employee pending tasks.
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

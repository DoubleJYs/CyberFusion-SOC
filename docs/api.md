# API Summary

All endpoints are under `/api`.

## Auth And System

- `POST /auth/login`
- `POST /auth/refresh`
- `GET /auth/me`
- `GET /system/users`, `/system/depts`, `/system/roles`, `/system/menus`
- `GET /system/logs/operations`, `GET /system/logs/logins`
- `GET /health/liveness`, `GET /health/readiness`

## SOC Core

- `GET /soc/dashboard/overview`
- `GET /soc/dashboard/alert-trend`
- `GET /soc/dashboard/severity-distribution`
- `GET /soc/dashboard/risk-analytics`
- `GET /soc/assets`
- `GET /soc/alerts`
- `GET /soc/alerts/{id}`
- `POST /soc/alerts/{id}/acknowledge`
- `POST /soc/alerts/{id}/false-positive`
- `POST /soc/alerts/{id}/ignore`
- `POST /soc/alerts/{id}/close`
- `POST /soc/alerts/{id}/ticket`
- `GET /soc/tickets`
- `GET /soc/tickets/{id}`
- `POST /soc/tickets/{id}/transition`
- `GET /soc/reports`
- `POST /soc/reports/generate`
- `GET /soc/reports/{id}/preview?format=xlsx|pdf`
- `GET /soc/reports/{id}/export?format=xlsx|pdf&disposition=inline|attachment`
- `GET /soc/rules`
- `GET /soc/rules/hits?sourceType={sourceType}&ruleId={ruleId}`
- `GET /soc/rules/adapter-mappings`
- `GET /soc/policies/local-check-commands`
- `POST /soc/policies/local-check-commands`
- `PUT /soc/policies/local-check-commands/{id}`
- `POST /soc/policies/local-check-commands/{id}/validate`
- `POST /soc/policies/local-check-commands/{id}/publish`
- `POST /soc/policies/local-check-commands/{id}/disable`
- `GET /soc/policies/event-adapters`
- `GET /soc/policies/event-adapters/{id}`
- `POST /soc/policies/event-adapters`
- `PUT /soc/policies/event-adapters/{id}`
- `POST /soc/policies/event-adapters/{id}/validate`
- `POST /soc/policies/event-adapters/{id}/publish`
- `POST /soc/policies/event-adapters/{id}/disable`
- `GET /soc/policies/event-adapters/{id}/mappings`
- `PUT /soc/policies/event-adapters/{id}/mappings`
- `POST /soc/policies/event-adapters/{id}/preview`

## Vulnerability, Baseline, FIM

- `GET /soc/vulnerabilities`
- `POST /soc/vulnerabilities/{id}/status`
- `GET /soc/vulnerabilities/summary`
- `GET /soc/baselines`
- `POST /soc/baselines/{id}/status`
- `GET /soc/fim`
- `POST /soc/fim/{id}/status`

## Integrations

- `GET /soc/integrations/catalog`
- `GET /soc/external-events`
- `GET /soc/external-events/{id}`
- `POST /soc/external-events/{id}/status`
- `GET /soc/external-events/summary`
- `POST /soc/external-events/suricata/import`
- `POST /soc/external-events/cyberfusion/import`
- `POST /soc/external-events/cyberchef/analyze`
- `POST /soc/external-events/shuffle/demo-notification`
- `POST /soc/demo-range/batches/import`
- `GET /soc/demo-range/batches/{batchId}/evidence-chain`
- `GET /soc/settings/wazuh-configs`
- `GET /soc/settings/wazuh-health`
- `GET /soc/settings/sync-tasks`
- `GET /soc/settings/notification-channels`
- `POST /soc/settings/notification-channels/{id}/test`
- `GET /soc/settings/notification-logs`

`GET /soc/integrations/catalog` returns the local read-only catalog for copied integration programs under `integrations/`, including each source type, local path, source module, API entrypoints, operating mode, and safety note. It does not execute integration programs, connect to external systems, or import evidence.

`POST /soc/external-events/cyberfusion/import` accepts:

```json
{
  "sourceType": "zeek",
  "content": "#fields\tts\tuid\tid.orig_h\tid.resp_h\tproto\tservice\n1719490200.1\tC8demo\t203.0.113.77\t10.20.1.15\ttcp\tssh",
  "linkAlerts": true
}
```

Supported `sourceType` values: `wazuh`, `zeek`, `suricata`, `trivy`, `misp`, `zap`, `sigma`, `shuffle`, `falco`, `opencti`, `osquery`, `velociraptor`, `cowrie`, `waf`.

`trivy` records are imported into `soc_vulnerability`; the other core sources are normalized into `soc_external_event` and can create linked unified alerts when `linkAlerts=true`.

`waf` records are offline WAF / gateway audit evidence for Demo Range. Supported WAF `eventType` values are `waf_block`, `waf_detect`, `upload_block`, and `api_abuse_block`. Sample JSON Lines are stored in `integrations/samples/waf-demo-events.jsonl`; import the file content with `sourceType=waf` and `linkAlerts=true` to write `soc_external_event` records and linked `soc_alert` records. The sample is static demo metadata only; it does not contain attack payloads and does not call ModSecurity, ZAP, or any external target.

Offline WAF demo import body:

```json
{
  "sourceType": "waf",
  "content": "<paste JSON Lines from integrations/samples/waf-demo-events.jsonl>",
  "linkAlerts": true
}
```

### Demo Range Batch Import

`POST /soc/demo-range/batches/import` imports a fixed offline Demo Range batch. The frontend calls this endpoint from `/soc/demo-range` through the normal `/api` proxy prefix.

Request body is optional:

```json
{
  "batchId": "DEMO-RANGE-OFFLINE-V1",
  "linkAlerts": true
}
```

If `batchId` is omitted, the backend uses `DEMO-RANGE-OFFLINE-V1`. The batch covers WAF, ZAP, Trivy, Suricata, Zeek, and Wazuh-style FIM evidence. It writes WAF/ZAP/Suricata/Zeek/Wazuh records to `soc_external_event`, links alerts when `linkAlerts=true`, and writes Trivy dependency data to `soc_vulnerability`.

Response summary:

```json
{
  "batchId": "DEMO-RANGE-OFFLINE-V1",
  "importedEvents": 6,
  "createdAlerts": 6,
  "createdVulnerabilities": 1,
  "skippedItems": 0,
  "failedItems": 0,
  "updatedEvents": 0,
  "dedupRule": "fixed batchId and stable source identifiers are upserted"
}
```

Dedup rule: the built-in batch uses stable `batchId`, request IDs, source event IDs, `event_uid`, `alert_uid`, and Trivy `cveId + softwareName`. Repeating the import refreshes the same demo evidence instead of creating unbounded duplicate records. Batch metadata is retained in `raw_event` / `normalized_event`; linked alerts include the batch in `raw_ref` for navigation from the Demo Range page.

### Demo Range Evidence Chain

`GET /soc/demo-range/batches/{batchId}/evidence-chain` returns the current SOC closure state for one offline validation batch:

```json
{
  "summary": {
    "batchId": "DEMO-RANGE-OFFLINE-V1",
    "eventCount": 6,
    "alertCount": 6,
    "vulnerabilityCount": 1,
    "blockedCount": 2,
    "ticketCount": 1,
    "reportCount": 1,
    "notificationLogCount": 2,
    "sourceCoverage": "suricata, waf, wazuh, zap, zeek"
  },
  "events": [],
  "alerts": [],
  "vulnerabilities": [],
  "tickets": [],
  "reports": [],
  "notificationLogs": []
}
```

Alert detail (`GET /soc/alerts/{id}`) enriches Demo Range linked alerts with `sourceType`, `eventType`, `ruleId`, `ruleName`, `assetIp`, `targetUrl`, `action`, `evidenceSummary`, `demoCaseId`, `batchId`, `httpMethod`, `httpStatus`, `requestId`, and `engine` when those fields exist in the linked external event.

To generate a batch report, call:

```json
{
  "reportType": "security_validation",
  "batchId": "DEMO-RANGE-OFFLINE-V1"
}
```

The report summary counts this batch's events, alerts, vulnerabilities, block evidence, tickets, reports, and dry-run notification logs. Notification remains dry-run only: report generation and batch import write `soc_notification_log`; `POST /soc/external-events/shuffle/demo-notification` can be used to add an explicit demo automation dry-run log.

### Detection Rule Center

`GET /soc/rules` returns a paged rule view assembled from the built-in safe rule catalog plus live `soc_external_event` and `soc_alert` hits. No separate rule table is required in this phase because the current goal is lifecycle visibility and mapping transparency, not online rule editing or production rule distribution.

Query parameters follow the existing SOC page contract:

```text
pageNum=1&pageSize=10&keyword=WAF-DEMO&sourceType=waf&severity=high
```

Response records include:

```json
{
  "ruleId": "WAF-DEMO-1001",
  "ruleName": "Admin route protected by WAF policy",
  "sourceType": "waf",
  "severity": "high",
  "enabled": true,
  "version": "crs-demo-1.0",
  "lastHitAt": "2026-06-18T10:00:00",
  "hitCount": 1,
  "falsePositiveCount": 0
}
```

`GET /soc/rules/hits?sourceType=waf&ruleId=WAF-DEMO-1001` returns recent matching `soc_external_event` rows and linked `soc_alert` rows. The frontend uses those rows to jump to `/soc/external-events` or `/soc/alerts`.

`GET /soc/rules/adapter-mappings` returns adapter mapping rows for `waf`, `zap`, `trivy`, `wazuh`, `suricata`, `zeek`, and `sigma`, including source fields, normalized fields, required/optional notes, severity mapping, dedup key, alert link rule, sample file, and failure case.

### Policy And Adapter Center

`GET /soc/policies/local-check-commands` returns backend-maintained local check policy rows. Employees do not see this management API; they only call `GET /client/local-terminal/commands?os=Linux` and receive `active + enabled` command options for the selected OS.

`GET /soc/policies/event-adapters` returns configurable adapter profiles for `waf`, `zap`, `trivy`, `wazuh`, `suricata`, and `zeek`.

`POST /soc/policies/event-adapters/{id}/preview` accepts an offline JSON payload string and returns normalized output without writing database records:

```json
{
  "payload": "{\"eventType\":\"waf_block\",\"severity\":\"high\",\"ruleId\":\"WAF-PREVIEW\",\"assetIp\":\"10.20.1.15\",\"requestId\":\"preview-only\"}"
}
```

Response fields:

```json
{
  "normalizedEvent": {
    "eventType": "waf_block",
    "severity": "high",
    "ruleId": "WAF-PREVIEW"
  },
  "severity": "high",
  "dedupKey": "waf|waf_block|WAF-PREVIEW|10.20.1.15|preview-only",
  "willCreateAlert": true,
  "validationErrors": []
}
```

Adapter publish validation rejects script-like or executable configuration. Source field paths are simple JSON dot paths or comma-separated first-non-empty paths; transform types are fixed; dedup keys must be field-name arrays; alert title templates only support `{fieldName}` placeholders.

### Asset Risk Scoring

`GET /soc/risk-scoring/policies` returns maintainable numeric scoring policies. Management actions:

- `POST /soc/risk-scoring/policies`
- `PUT /soc/risk-scoring/policies/{id}`
- `POST /soc/risk-scoring/policies/{id}/validate`
- `POST /soc/risk-scoring/policies/{id}/publish`
- `POST /soc/risk-scoring/policies/{id}/disable`

Policy bodies only contain numeric weights and descriptive text. The backend rejects script, SQL, shell, scanner, downloader, expression, or external-call wording.

`POST /soc/risk-scoring/recalculate` recalculates all in-scope assets from existing SOC data. `POST /soc/risk-scoring/recalculate/{assetId}` recalculates one asset. These endpoints do not start scans, run commands, send notifications, or modify external systems.

`GET /soc/assets/{id}/risk-profile` returns the latest explainable profile:

```json
{
  "asset": { "id": 1, "hostname": "prod-app-01", "ip": "10.20.1.15", "riskScore": 92, "riskLevel": "critical" },
  "snapshot": { "score": 92, "riskLevel": "critical", "calculatedAt": "2026-06-20T17:45:00" },
  "factors": [
    {
      "factorType": "alert_critical",
      "factorName": "严重告警",
      "factorScore": 25,
      "factorCount": 1,
      "explanation": "存在未关闭严重告警。",
      "recommendation": "优先确认告警详情并转入处置工单。"
    }
  ],
  "recommendationSummary": "优先确认告警详情并转入处置工单。",
  "statusReason": "存在未关闭严重告警。"
}
```

Related read APIs:

- `GET /soc/assets/{id}/risk-history`
- `GET /soc/risk-scoring/top-assets?limit=5`
- `GET /client/devices/{ip}/risk-profile`

Employee risk-profile reads are scoped to the authenticated employee's accessible computer. If no active policy exists, the backend uses an internal numeric fallback policy.

### Correlation Engine v1

Correlation Engine v1 turns existing SOC evidence into explainable incident clusters. It reads `soc_external_event`, `soc_alert`, and `soc_vulnerability`, then writes `soc_incident_cluster` and `soc_incident_evidence`. It does not run scans, execute commands, connect to OpenSearch, use Python ML, query external systems, or send real notifications.

Incident APIs:

- `GET /soc/incidents?pageNum=1&pageSize=10&keyword=...`
- `GET /soc/incidents/{id}`
- `POST /soc/incidents/correlate`
- `POST /soc/incidents/{id}/ticket`
- `POST /soc/incidents/{id}/close`
- `GET /soc/alerts/{id}/related-incidents`
- `GET /soc/assets/{id}/incidents`

`POST /soc/incidents/correlate` response:

```json
{
  "upsertedClusters": 1,
  "createdClusters": 1,
  "evidenceWritten": 6,
  "activeRules": 4
}
```

Incident detail includes `evidence[]`. Each evidence row contains `evidenceType`, `sourceType`, `eventType`, `severity`, `assetIp`, `batchId`, `demoCaseId`, `relationScore`, and `relationReason`.

Correlation rule APIs:

- `GET /soc/correlation-rules`
- `POST /soc/correlation-rules`
- `PUT /soc/correlation-rules/{id}`
- `POST /soc/correlation-rules/{id}/validate`
- `POST /soc/correlation-rules/{id}/publish`
- `POST /soc/correlation-rules/{id}/disable`

Rule payloads store structured arrays, thresholds, time windows, severity floors, and descriptions only. Supported `ruleType` values are `event_count`, `value_count`, `frequency`, `temporal`, `temporal_ordered`, and `cross_source_chain`. Backend validation rejects scripts, shell/download/external URL wording, non-array JSON rule lists, and unsafe command/external query semantics.

### Report Preview And Export

Report preview and export use the same report data, so the UI can show content before downloading a file:

- `GET /soc/reports/{id}/preview?format=xlsx`
- `GET /soc/reports/{id}/preview?format=pdf`
- `GET /soc/reports/{id}/export?format=xlsx&disposition=attachment`
- `GET /soc/reports/{id}/export?format=pdf&disposition=inline`

Preview returns metadata plus renderable content:

```json
{
  "reportId": 18,
  "reportNo": "RPT-VALIDATION-20260708183556",
  "title": "安全验证报告",
  "format": "xlsx",
  "filename": "RPT-VALIDATION-20260708183556.xlsx",
  "headers": ["模块", "指标", "内容"],
  "rows": [["基础信息", "报表编号", "RPT-VALIDATION-20260708183556"]],
  "lines": ["报表编号：RPT-VALIDATION-20260708183556"]
}
```

`format=xlsx` exports an Office Open XML workbook. `format=pdf` exports a valid browser-previewable PDF byte stream. `disposition=inline` is intended for preview panes, while `disposition=attachment` is intended for explicit download. Export endpoints require `soc:report:export`; preview requires `soc:report:view`.

### Host Agent Ingest v1

Host Agent Ingest v1 is the shared contract for macOS development validation and Windows Docker host collection. The Docker platform receives normalized host facts; the host-side Agent collects operating-system data and sends it to the backend. Backend ingest does not execute commands, scan files, collect file contents, or reach into the host by itself.

Agent registration is an administrator action:

- `POST /soc/agents/register`
- `GET /soc/agents`

Registration returns `agentToken` once. Store it only in the host Agent local configuration or a protected runtime secret. The backend stores only `tokenHash`.

Agent runtime endpoints use header `X-CyberFusion-Agent-Token` and do not use user JWT:

- `POST /soc/agents/heartbeat`
- `POST /soc/ingest/host/assets`
- `POST /soc/ingest/host/events`
- `POST /soc/ingest/host/fim`
- `POST /soc/ingest/host/baseline`

Shared request fields:

```json
{
  "agentId": "macos-fixture-agent",
  "batchId": "HOST-macos-fixture-agent-EVENT",
  "osType": "macos",
  "collectedAt": "2026-07-07T18:30:00",
  "events": []
}
```

`osType` is one of `macos`, `windows`, or `linux`. The same schema is used for Mac and Windows fixture validation. Event and FIM payloads must provide stable `eventUid` values; repeated `eventUid` submissions are counted as duplicates and not inserted again.

Assets write or update `soc_asset` with source types such as `macos-agent` or `windows-agent`. Host events write `soc_external_event`. FIM writes `soc_file_integrity_event`. Baseline checks write or update `soc_baseline_check`. These rows must not use demo markers such as `batch_id LIKE 'DEMO-%'`, `demo_case_id`, or `source_type='demo'`.

Example host event item:

```json
{
  "eventUid": "WIN-FIXTURE-EVENT-0001",
  "sourceModule": "eventlog",
  "eventType": "windows_logon_failure",
  "severity": "high",
  "ruleId": "WIN-4625",
  "ruleName": "Windows failed logon",
  "assetName": "win-docker-host",
  "assetIp": "192.0.2.20",
  "action": "review",
  "raw": { "channel": "Security", "eventId": 4625 },
  "normalized": { "agentId": "windows-fixture-agent" }
}
```

Host Agent ingest accepts only structured metadata needed by the SOC workflow. It must not upload file bodies, passwords, browser cookies, private keys, access tokens, customer documents, or full raw event-log archives.

### Client Security Keeper Checkup

The Web security keeper checkup aggregates existing SOC data for the authenticated employee's current computer. It does not execute commands, start scans, auto-fix issues, connect to public targets, or send external notifications.

- `POST /client/security-keeper/checkup`
- `GET /client/security-keeper/checkups?assetIp=10.20.1.15`
- `GET /client/security-keeper/checkups/{id}`

Request:

```json
{
  "assetIp": "10.20.1.15"
}
```

Response includes `checkup.id`, `checkup.score`, `checkup.status`, `checkup.summary`, `riskItems`, and `recommendations`. Checkup persistence stores summarized counts and recommendations only. A closed `client-checkup/security_keeper_checkup` security log event is written with normalized summary fields and no raw logs or command output.

### Client Security Keeper Repair Recommendations

Risk repair recommendations convert existing alerts, vulnerabilities, tickets, and playbook tasks into employee-readable actions. They are guidance and status records only: no automatic remediation, command execution, process termination, file deletion, registry edit, system setting change, public scan, or external notification is performed.

- `GET /client/security-keeper/recommendations?assetIp=10.20.1.15`
- `POST /client/security-keeper/recommendations/{id}/confirm`
- `POST /client/security-keeper/recommendations/{id}/submit-note`

Recommendation IDs use scoped keys such as `task-12`, `alert-75`, or `vulnerability-4`. `task-*` actions reuse the employee task workflow so submitted notes or confirmations enter the task/ticket timeline. `alert-*` and `vulnerability-*` actions write `client-remediation/repair_guidance_action` security log records and recommendation status records without changing alert or vulnerability state.

## Safety Notes

- Import payloads are bounded by request validation.
- CyberChef analysis is local demo decoding/hash/IOC extraction only.
- Shuffle integration is dry-run notification logging only; it does not call real webhooks unless a future production sender is explicitly configured outside source.
- WAF Demo Range import handles offline JSON logs only and must not be connected to real scanning, attack automation, or public targets.
- Demo Range batch import uses static offline metadata only; it does not start Docker services, run ZAP/Trivy, send notifications, scan targets, or contact public networks.

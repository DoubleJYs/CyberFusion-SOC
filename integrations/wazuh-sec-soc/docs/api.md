# API

Swagger UI: `http://127.0.0.1:8080/api/swagger-ui.html`

All authenticated APIs require `Authorization: Bearer <accessToken>` except login/refresh/logout.

## Health

- `GET /api/health/liveness`
- `GET /api/health/readiness`

Health endpoints are public so Docker, Nginx, or a private deployment platform can call them without user tokens. `liveness` only proves the JVM/API process is responsive. `readiness` checks MySQL and Redis with bounded probes and returns dependency status plus latency without exposing connection URLs, usernames, passwords, or internal certificates.

## Auth And System

- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- Existing system APIs cover users, roles, departments, menus, dictionaries, configs, files, import/export logs, login logs, and operation logs.
- Role APIs include `dataScope` values `self`, `dept`, `dept_tree`, `all`, `custom`, plus `deptIds` for custom data ranges.

## SOC Dashboard

- `GET /api/soc/dashboard/overview`
- `GET /api/soc/dashboard/alert-trend`
- `GET /api/soc/dashboard/severity-distribution`
- `GET /api/soc/dashboard/affected-assets`
- `GET /api/soc/dashboard/risk-analytics`

`risk-analytics` returns P2.5 operational analysis for the current user's data scope:

- `assetRisks`: asset risk scores with an explanation and component weights from alert, vulnerability, baseline, exposure, and handling signals.
- `alertPriorities`: alert priority scores derived from severity, high-risk assets, IOC hits, repeat count, and handling status.
- `departmentRisks`: department risk rankings with high alerts, open vulnerabilities, failed baselines, and pending tickets.
- `operationMetrics`: SLA rate, overdue tickets, false-positive rate, duplicate groups, and average close hours.
- `eventTimeline`: recent alert, external event, ticket, and close events for analyst review.

## Alert Center

- `GET /api/soc/alerts?pageNum=1&pageSize=10&keyword=&severity=&status=`
- `GET /api/soc/alerts/{id}`
- `POST /api/soc/alerts/{id}/acknowledge`
- `POST /api/soc/alerts/{id}/false-positive`
- `POST /api/soc/alerts/{id}/ignore`
- `POST /api/soc/alerts/{id}/close`
- `POST /api/soc/alerts/{id}/ticket`

Action body:

```json
{ "remark": "处置说明", "assigneeId": 5 }
```

Alert list records include noise fields for P1.5 handling: `whitelistHit`, `whitelistRuleName`, `noiseStatus`, and `repeatCount`.

## Alert Noise

- `GET /api/soc/alert-noise/summary`
- `GET /api/soc/alert-noise/whitelists?pageNum=1&pageSize=10&keyword=&status=`
- `GET /api/soc/alert-noise/aggregations?pageNum=1&pageSize=12`
- `POST /api/soc/alert-noise/whitelists`
- `PUT /api/soc/alert-noise/whitelists/{id}`
- `POST /api/soc/alert-noise/whitelists/{id}/status`

Whitelist save body:

```json
{
  "ruleName": "维护窗口内配置调整",
  "ruleId": "5502",
  "assetIp": "10.20.8.21",
  "sourceIp": "10.10.4.12",
  "severity": "high",
  "reason": "已登记变更单，仅对同规则、同资产、同来源降噪",
  "enabled": 1,
  "expiresAt": "2026-06-30T23:59:59"
}
```

Whitelist status body:

```json
{ "targetStatus": "disabled", "remark": "临时停用白名单规则" }
```

Supported status values: `enabled`, `disabled`. Whitelist save requires at least one matching condition among rule ID, asset IP, source IP, and severity. Matching is scoped by rule ID, asset IP, source IP, severity, enabled status, expiration time, and backend data-scope permission. Aggregations group recent authorized alerts by rule ID, asset IP, and source IP.

## Assets

- `GET /api/soc/assets?pageNum=1&pageSize=10&keyword=&riskLevel=`

## Vulnerabilities

- `GET /api/soc/vulnerabilities?pageNum=1&pageSize=10&keyword=&severity=&status=`
- `GET /api/soc/vulnerabilities/{id}`
- `GET /api/soc/vulnerabilities/summary`
- `POST /api/soc/vulnerabilities/{id}/status`

Status body:

```json
{ "targetStatus": "reviewing", "remark": "补丁已安装，提交复核" }
```

Supported status values: `open`, `fixing`, `reviewing`, `fixed`, `accepted`.

## Baseline Checks

- `GET /api/soc/baselines?pageNum=1&pageSize=10&keyword=&category=&result=&status=`
- `GET /api/soc/baselines/{id}`
- `GET /api/soc/baselines/summary`
- `POST /api/soc/baselines/{id}/status`

Supported status values: `failed`, `remediating`, `reviewing`, `passed`, `accepted`.

## File Integrity

- `GET /api/soc/fim?pageNum=1&pageSize=10&keyword=&action=&severity=&status=`
- `GET /api/soc/fim/{id}`
- `GET /api/soc/fim/summary`
- `POST /api/soc/fim/{id}/status`

Supported action values: `created`, `modified`, `deleted`, `permission`.
Supported status values: `new`, `reviewing`, `confirmed`, `ignored`, `closed`.

## External Events

- `GET /api/soc/external-events?pageNum=1&pageSize=10&keyword=&sourceType=&eventType=&severity=&status=`
- `GET /api/soc/external-events/{id}`
- `GET /api/soc/external-events/summary`
- `POST /api/soc/external-events/suricata/import`
- `POST /api/soc/external-events/{id}/status`

Supported `sourceType` values start with `suricata`; `zeek`, `misp`, and `opencti` are reserved for later connectors. The normalized event model stores `sourceType`, `eventType`, `severity`, `assetIp`, `ioc`, `rawEvent`, `normalizedEvent`, optional `alertId`, owner/dept scope, and event time. Linked Suricata demo events are also represented in the unified alert center through `soc_alert.source_type=suricata`.

Suricata import body:

```json
{
  "linkAlerts": true,
  "content": "{\"timestamp\":\"2026-05-27T22:55:00+08:00\",\"event_type\":\"alert\",\"src_ip\":\"203.0.113.88\",\"dest_ip\":\"10.20.1.15\",\"alert\":{\"signature_id\":20260527,\"signature\":\"ET SCAN Demo inbound scan\",\"severity\":1}}"
}
```

The import endpoint accepts EVE JSON Lines in `content`, parses each line with structured JSON parsing, writes normalized `soc_external_event` records, and links Suricata alert events into `soc_alert` when `linkAlerts=true`. Duplicate imports update records by deterministic event UID.

Status body:

```json
{ "targetStatus": "linked", "remark": "已关联统一告警并进入处置队列" }
```

Supported status values: `new`, `reviewing`, `linked`, `ignored`, `closed`.

## Tickets

- `GET /api/soc/tickets?pageNum=1&pageSize=10&keyword=&severity=&status=`
- `GET /api/soc/tickets/{id}`
- `POST /api/soc/tickets/{id}/transition`

Transition body:

```json
{ "targetStatus": "待复核", "remark": "处置完成，提交复核" }
```

## Reports

- `GET /api/soc/reports?pageNum=1&pageSize=10&keyword=`
- `POST /api/soc/reports/generate`
- `GET /api/soc/reports/{id}/export?format=xlsx`
- `GET /api/soc/reports/{id}/export?format=pdf`

Generated report content includes alert trend, alert severity, ticket handling status, asset risk, vulnerability posture, baseline pass/fail status, file-integrity event status, external security event coverage, and remediation recommendations.

Generate body:

```json
{ "reportType": "daily" }
```

## Settings

- `GET /api/soc/settings/wazuh-configs`
- `GET /api/soc/settings/sync-tasks`
- `GET /api/soc/settings/wazuh-health`
- `GET /api/soc/settings/notification-channels`
- `GET /api/soc/settings/notification-logs?pageNum=1&pageSize=10&keyword=&status=`
- `POST /api/soc/settings/notification-channels/{id}/test`

`wazuh-health` is backend-only integration evidence: the backend authenticates to Wazuh Manager with a runtime credential pair, obtains a bearer token, and checks the Indexer with separate runtime Basic credentials. The response exposes only connection status and non-sensitive service metadata.

Notification channels currently support email-oriented dry-run delivery for demo and acceptance. The channel stores target, severity threshold, trigger event, and send mode only; SMTP host, account, password, certificates, and webhook secrets must be supplied by runtime environment in a later production sender and must not be written to source.

Automatic notification log events:

- `alert_ticketed`: alert converted to a ticket.
- `ticket_review`: ticket submitted for review.
- `ticket_closed`: ticket closed.
- `report_generated`: daily/weekly/monthly report generated.

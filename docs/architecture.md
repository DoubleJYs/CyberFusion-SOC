# CyberFusion SOC Architecture

`00-cyberfusion-platform` is the unified portal and primary system. Curated integration-side programs are stored under `integrations/`; the sibling upstream projects in `01-16` are now reference/source origins only.

For API coverage, database relationships, and demo acceptance flows, see [API](api.md), [Database](database.md), and [User Manual](user-manual.md).

## Layers

```text
Vue 3 SOC console
  -> Spring Boot 3 REST API
  -> MySQL business state / Redis cache
  -> local integration catalog under integrations/
  -> adapter parsers for Wazuh, Zeek, Suricata, Trivy, MISP, ZAP, Sigma, CyberChef, Shuffle
  -> optional external import adapters for Security Onion, Falco, OpenCTI, osquery, Velociraptor, Cowrie
```

Browsers never receive upstream credentials or internal service tokens. Runtime values are read by the backend from environment variables or protected runtime config under `/Users/zhangjiyan/Environment/cyberfusion-platform`.

## Main Modules

- `auth`: login, refresh token, RBAC, departments, role menu permissions, data scope.
- `asset`: unified asset inventory and scoped asset risk.
- `event`: raw external event plus normalized event records in `soc_external_event`.
- `alert`: unified alert center, status transitions, false-positive handling, alert-to-ticket.
- `vulnerability`: vulnerability center with Trivy JSON import support.
- `intel`: IOC signals represented through MISP/OpenCTI-style external events.
- `rule`: detection rule center assembled from safe built-in rule catalog, Sigma detection-rule external events, WAF/ZAP/Suricata/Wazuh rule hits, and false-positive counts from unified alerts.
- `ticket/case`: ticket state machine, timeline, review/close/archive.
- `report`: daily/weekly/monthly report generation and export.
- `automation`: dry-run notification workflow and Shuffle adapter entry.
- `integrations`: adapter entry points for Wazuh, Zeek, Suricata, Trivy, MISP, ZAP, Sigma, CyberChef, Shuffle.
- `system`: data source configuration, import/export log, audit logs, health checks.

## Integration Catalog

`integrations/catalog.json` maps each local integration program to its source module and CyberFusion API. `GET /api/soc/integrations/catalog` exposes the same read-only metadata to the platform. Operators should use this file, the API, and `integrations/README.md` instead of referencing sibling upstream directories directly.

## Data Model

- `soc_external_event` keeps source type, event type, severity, source/destination IP, IOC, raw JSON, normalized JSON, linked alert id, owner/dept scope, and event time.
- `soc_alert` is the unified alert surface. High-signal imported events can create linked alerts.
- `soc_vulnerability` stores Trivy JSON findings as vulnerability-center records.
- `soc_ticket` and `soc_ticket_timeline` provide closed-loop case handling.
- `soc_notification_*` stores dry-run notification channels/logs for SOAR workflow proof.

## Rule Center Data Flow

The rule center does not introduce a new persistence table in this phase. It is a read-only operational projection:

```text
adapter import -> soc_external_event(rule_id, rule_name, source_type, severity, alert_id)
             -> optional soc_alert(rule_id, rule_description, source_type, status)
             -> /soc/rules aggregation
             -> /soc/rules/hits preview and jumps to event/alert details
```

Static catalog rows make supported sources visible before they receive hits. Live rows update `lastHitAt`, `hitCount`, and `falsePositiveCount`. `mock` seed alerts are displayed as Wazuh rules because they represent local Wazuh-style seed data.

## Adapter Policy

Allowed:

- Parse user-supplied demo logs/reports.
- Normalize events and vulnerabilities.
- Create local alerts, tickets, notification logs, and reports.
- Link to external tools as analysis/automation entry points.

Not allowed:

- Unauthorized scanning.
- Attack automation, credential stuffing, phishing, malware, evasion, privilege bypass, data theft, or customer-data processing.
- Writing secrets, certificates, tokens, private keys, Docker volumes, large logs, caches, or runtime databases into source.

## Production Hardening Hooks

The inherited platform already includes CORS configuration, security headers, rate limiting, upload path validation, health endpoints, backup/restore scripts, Docker Compose examples, Nginx examples, audit logging, and permission checks. Production rollout must re-run the validation checklist in `docs/test-report.md` after configuring real runtime endpoints.

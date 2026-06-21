# Architecture

## Boundary

Wazuh Core and Wazuh Dashboard are not modified. The custom platform is a separate business layer:

`Vue3 frontend -> Spring Boot backend -> Wazuh API / Wazuh Indexer API -> Wazuh`

The browser never talks to Wazuh directly. Wazuh credentials and internal URLs are read by the backend from environment variables or protected runtime config.

## Modules

- `system/*`: inherited RBAC foundation for users, roles, departments, menus, button permissions, interface permissions, data scopes, login logs, operation logs, parameters, dictionaries, and file/export logs.
- `soc.alert`: alert list, detail, acknowledge, false-positive, ignore, close, and ticket conversion.
- `soc.ticket`: security incident ticket lifecycle: `待分派 -> 处理中 -> 待复核 -> 已关闭 -> 已归档`.
- `soc.asset`: asset inventory with hostname, IP, OS, risk level, department, owner, and last-seen time.
- `soc.report`: daily/weekly/monthly report generation and PDF/Excel export endpoints.
- `soc.settings`: Wazuh connection metadata, sync task list, and manager/indexer health checks.
- `soc.wazuh`: backend-only Wazuh client wrapper. Manager calls use a short-lived bearer token obtained from `/security/user/authenticate`; Indexer calls use separate Basic credentials. Both credential pairs are supplied at runtime only.
- `soc.external`: normalized external security events for Suricata first, with Zeek, MISP, and OpenCTI reserved. Records keep source, event type, severity, asset, IOC, raw event, normalized event, linked alert, owner/dept scope, and review status. Suricata EVE JSON Lines can be imported through the backend and linked into the unified alert center.
- `soc.dashboard`: P0 overview plus P2.5 risk analytics. The risk analytics endpoint computes scoped asset risk scores, alert priority scores, department risk rankings, SLA/false-positive/duplicate metrics, and an analyst event timeline from existing MySQL business state without adding browser-side Wazuh access.
- `common.health`: public liveness and readiness probes for deployment platforms. Liveness checks API process responsiveness; readiness checks MySQL and Redis with sanitized dependency status.

## Data Ownership

- Raw security events stay in Wazuh / Indexer.
- Alert handling status, ticket state, report metadata, RBAC, and audit logs are stored in MySQL.
- Redis is reserved for cache, session, and task status.
- P0 seed data uses `source_type=mock` and can later be replaced by Wazuh API / Indexer sync.
- P2 seed data uses `source_type=suricata` in both normalized external events and linked unified alerts, proving the external-source path without running an IDS container in the source tree.
- When a local Wazuh single-node stack is running, backend health checks prove connectivity without moving Wazuh credentials into the browser.

## Security

- Backend endpoints use `@PreAuthorize`.
- Role data scope supports `self`, `dept`, `dept_tree`, `all`, and `custom`. SOC alert, asset, ticket, external event, and dashboard queries apply owner/dept filtering based on the effective scope of the current user's roles.
- Key actions are covered by operation audit: queries, exports, alert confirmation, false-positive marking, ticket conversion, ticket close, and Wazuh config checks.
- P3 hardening adds environment-configurable CORS, security response headers, and lightweight node-local API rate limiting. Production multi-instance deployments should replace node-local counters with Redis or an API gateway.
- Health endpoints are public but non-sensitive. They are excluded from rate limiting and never expose connection strings, credentials, Wazuh addresses, tokens, certificates, or raw exception traces.
- Logs and source files must not contain real passwords, API keys, private keys, certificates, or customer data.

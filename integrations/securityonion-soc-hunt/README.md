# SOC Hunt Overlay

This directory is a non-invasive SOC threat hunting and alert-analysis overlay for the local Security Onion source tree. It does not replace or modify the upstream Security Onion Salt, setup, or pillar components.

## Scope

- Import demo Zeek and Suricata alerts.
- Import Zeek JSON logs and Suricata EVE JSONL files with `import-file`.
- Normalize alerts to `source`, `event_type`, `severity`, `src_ip`, `dst_ip`, `asset`, `rule`, `raw_event`, and `status`.
- Search and inspect alerts by time, severity, source, asset, rule, tags, and status.
- Track Zeek, Suricata, Elastic/Indexer, and sensor data-source health.
- Create cases from alerts, assign owners, add handling records, review, close, and archive.
- Record hunting tasks with hypothesis, query conditions, result, conclusion, and linked case.
- Export case reports, hunt reports, and daily/weekly SOC summaries to the Environment upload directory.
- Record audit logs for analyst actions.
- Clear demo data and reports for reproducible acceptance runs.

## Runtime Layout

Source files, docs, demo data, and config templates stay in this repository. Runtime state stays under Environment:

```text
/Users/zhangjiyan/Environment/02-databases/02-securityonion/soc_hunt.sqlite3
/Users/zhangjiyan/Environment/10-cache/02-securityonion/
/Users/zhangjiyan/Environment/11-logs/02-securityonion/
/Users/zhangjiyan/Environment/13-uploads/02-securityonion/
/Users/zhangjiyan/Environment/08-docs/02-securityonion/
```

Override the runtime root for tests or temporary runs:

```bash
SOC_HUNT_ENV_ROOT=/private/tmp/so-hunt-demo python3 -m soc_hunt.cli init
```

## Quick Start

```bash
cd /Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/02-securityonion/soc-hunt
python3 -m soc_hunt.cli init
python3 -m soc_hunt.cli import-demo
python3 -m soc_hunt.cli import-file --source zeek --path data/zeek-sample.jsonl
python3 -m soc_hunt.cli import-file --source suricata --path data/suricata-eve-sample.jsonl
python3 -m soc_hunt.cli alerts --source suricata --asset finance-api-01 --tags Trojan --severity critical
python3 -m soc_hunt.cli create-case --alert-id SO-DEMO-0001 --title "C2 beacon investigation" --summary "Investigate finance-api-01." --assignee analyst-a
python3 -m soc_hunt.cli assign-case --case-id 1 --assignee analyst-b
python3 -m soc_hunt.cli add-record --case-id 1 --action analysis --note "Correlated IDS and DNS anomalies."
python3 -m soc_hunt.cli review-case --case-id 1 --reviewer lead --note "Evidence reviewed."
python3 -m soc_hunt.cli create-hunt --title "Find related DNS beacons" --query "source:zeek asset:finance-api-01" --hypothesis "Compromised host may beacon through DNS." --case-id 1
python3 -m soc_hunt.cli record-hunt-result --hunt-id 1 --result "Found one related DNS alert." --conclusion "No additional affected assets in demo set."
python3 -m soc_hunt.cli set-status --case-id 1 --status closed
python3 -m soc_hunt.cli set-status --case-id 1 --status archived
python3 -m soc_hunt.cli export-report --case-id 1
python3 -m soc_hunt.cli export-hunt-report --hunt-id 1
python3 -m soc_hunt.cli export-summary-report --period daily
python3 -m soc_hunt.cli backup
```

Run the local UI:

```bash
python3 -m soc_hunt.cli serve --host 127.0.0.1 --port 8765
```

Open `http://127.0.0.1:8765`.

## Private RBAC Mode

The UI/API defaults to local development mode. For a private deployment, enable token enforcement:

```bash
export SOC_HUNT_AUTH_MODE=required
export SOC_HUNT_TOKENS='viewer-token:viewer:viewer-user,analyst-token:analyst:analyst-user,lead-token:lead:lead-user,admin-token:admin:admin-user'
python3 -m soc_hunt.cli serve --host 127.0.0.1 --port 8765
```

API clients then send `Authorization: Bearer <token>`. Role gates:

- `viewer`: dashboard, alerts, cases, hunts, health, audit read APIs.
- `analyst`: imports, case creation/records, hunt creation/results, report exports.
- `lead`: case assignment, review, status transitions, close/archive.
- `admin`: demo reset and runtime backup/restore operations.

## Zeek and Suricata Import

`import-file` accepts one JSON object per line. Use `--source auto` for simple detection, or set the source explicitly:

```bash
python3 -m soc_hunt.cli import-file --source zeek --path /path/to/zeek-dns.jsonl
python3 -m soc_hunt.cli import-file --source suricata --path /path/to/eve.json
```

The adapters preserve the full original event in `raw_event` and map source-specific fields into the unified alert schema.

## Reproducible Demo Reset

Use `reset-demo` before an acceptance run when you need a clean local state:

```bash
python3 -m soc_hunt.cli reset-demo
python3 -m soc_hunt.cli import-demo
```

`reset-demo` clears demo alerts, cases, case records, hunt tasks, data-source health rows, audit rows, and generated markdown reports in the configured Environment runtime root.

## Safety Notes

- Do not put real tokens, private keys, certificates, customer logs, or production `.env` files in this repository.
- Keep Security Onion license notices intact.
- Keep the UI bound to `127.0.0.1` unless it is protected by a private reverse proxy with authentication.
- Use `backup` before handoff or restore drills. Do not put backup archives in the source repository.

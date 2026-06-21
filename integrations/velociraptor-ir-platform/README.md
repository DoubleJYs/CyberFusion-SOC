# IR Platform Wrapper

This directory contains the production-oriented second-development wrapper for the local `11-velociraptor` master copy.

Velociraptor remains the DFIR query and collection base. This wrapper owns case management, task approval, result views, evidence chain records, timeline aggregation, disposition records, audit logs, and Markdown forensic reports for authorized endpoint forensics and incident response.

## Runtime Boundary

Source repository:

- Business wrapper code: `ir_platform/`
- Config template: `ir_platform/config.example.json`
- Velociraptor API config template: `ir_platform/velociraptor.api.example.json`
- Synthetic demo data: `ir_platform/demo/demo_collection.json`
- Documentation: `docs/ir-platform.md`

Environment runtime data:

- Case/task state: `/Users/zhangjiyan/Environment/02-databases/11-velociraptor/ir_state.json`
- Audit logs: `/Users/zhangjiyan/Environment/11-logs/11-velociraptor/audit.jsonl`
- Evidence packages: `/Users/zhangjiyan/Environment/13-uploads/11-velociraptor/evidence/`
- Reports: `/Users/zhangjiyan/Environment/08-docs/11-velociraptor/reports/`

## Production Closed Loop Demo

```bash
cd /Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/11-velociraptor
python3 -m ir_platform init
python3 -m ir_platform templates
python3 -m ir_platform run-demo
python3 -m ir_platform serve-web
```

The demo performs:

1. Create an emergency case.
2. Create a sensitive collection task from a Velociraptor artifact template.
3. Approve the task with approver and reason.
4. Generate an auditable Velociraptor collection request dispatch package.
5. Import synthetic collection results.
6. Display result table, raw JSON, attachments, and evidence summary through `results`.
7. Aggregate event timeline by process, network, file, login, browser, and alert events.
8. Create evidence chain records with evidence ID, hash, source, collector, time, and storage path.
9. Record analysis conclusion, recommended action, reviewer, and case status.
10. Export a case forensic report.
11. Verify evidence/report/dispatch hashes and close the case after review.

Open the local console at `http://127.0.0.1:8765`. The console provides case creation, task creation, task approval, demo result import, result tables, raw JSON, attachments, evidence chain, case timeline, disposition records, report export, and audit log pages.

## Local Role Separation

The wrapper has local role checks for command and Web actions. CLI defaults to `admin` for compatibility, and production runs should start with an explicit role:

```bash
python3 -m ir_platform --role analyst create-task --case-id CASE-001 --template suspicious_process --reason "Authorized triage"
python3 -m ir_platform --role reviewer approve-task --task-id IR-... --approver incident-manager --reason "Scoped DFIR approval"
python3 -m ir_platform --role reviewer close-case --case-id CASE-001 --reviewer incident-manager --reason "Evidence chain verified"
python3 -m ir_platform --role analyst serve-web --host 127.0.0.1 --port 8765
```

Roles:

- `viewer`: read-only cases, tasks, templates, results, timelines, evidence, audit, and health checks.
- `analyst`: case/task creation, dispatch plan generation, result import, disposition draft, report export, and chain verification.
- `reviewer`: approval, review records, report export, chain verification, and case close.
- `admin`: all local actions.

## Manual Commands

```bash
python3 -m ir_platform create-case \
  --case-id CASE-001 \
  --asset endpoint-01 \
  --incident-type malware \
  --severity high \
  --owner zhangjiyan

python3 -m ir_platform create-task \
  --case-id CASE-001 \
  --template suspicious_process \
  --reason "Authorized suspicious process triage"

python3 -m ir_platform approve-task \
  --task-id IR-... \
  --approver incident-manager \
  --reason "Approved for scoped endpoint DFIR"

python3 -m ir_platform dispatch-plan --task-id IR-...
python3 -m ir_platform import-demo --task-id IR-...
python3 -m ir_platform results --task-id IR-... --view table
python3 -m ir_platform results --task-id IR-... --view raw
python3 -m ir_platform results --task-id IR-... --view attachments
python3 -m ir_platform timeline --case-id CASE-001
python3 -m ir_platform evidence --case-id CASE-001
python3 -m ir_platform dispose \
  --case-id CASE-001 \
  --conclusion "Suspicious process chain confirmed in scoped evidence." \
  --recommendation "Preserve evidence and isolate endpoint for remediation." \
  --reviewer incident-manager
python3 -m ir_platform report --case-id CASE-001
python3 -m ir_platform verify-chain --case-id CASE-001
python3 -m ir_platform close-case \
  --case-id CASE-001 \
  --reviewer incident-manager \
  --reason "Report exported and evidence chain verified"
python3 -m ir_platform serve-web --host 127.0.0.1 --port 8765
```

## Safety

This wrapper is scoped to authorized endpoint forensics and incident response. It does not implement unauthorized collection, credential capture, privacy theft, lateral movement, persistence, or exploitation workflows.

Live Velociraptor credentials must stay outside source, for example under `/Users/zhangjiyan/Environment/12-secrets/11-velociraptor/`. Use `ir_platform/velociraptor.api.example.json` only as a non-secret config shape.

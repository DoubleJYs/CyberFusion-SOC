# Falco Runtime Security Platform

This is a lightweight secondary-development module for the local `07-falco`
template. It does not modify Falco's detection engine, does not generate attack
traffic, and only imports authorized Falco JSON/NDJSON events or the bundled demo
events.

## Runtime Paths

The platform keeps runtime data outside the source tree:

- Database: `/Users/zhangjiyan/Environment/02-databases/07-falco/falco-events.sqlite3`
- Runtime log: `/Users/zhangjiyan/Environment/11-logs/07-falco/platform.log`
- Cache directory: `/Users/zhangjiyan/Environment/10-cache/07-falco`
- Reports: `/Users/zhangjiyan/Environment/08-docs/07-falco`

Override the root with `FALCO_PLATFORM_ENV_ROOT` or `--env-root`.

## Run

```bash
cd /Users/zhangjiyan/Programs/projects/cyberspace_Security_shot_time/07-falco
python3 runtime-security-platform/platform_server.py --host 127.0.0.1 --port 8767
```

Open `http://127.0.0.1:8767`.

## Import Demo Events

Use the UI button `导入 demo 事件`, or import from the command line:

```bash
python3 runtime-security-platform/platform_server.py \
  --import-file runtime-security-platform/static/demo-falco-events.ndjson
```

The import command initializes the Environment directories and SQLite database.

## Import Authorized Falco Events

The API accepts Falco JSON arrays or NDJSON:

```bash
curl -X POST http://127.0.0.1:8767/api/import \
  -H 'Content-Type: application/json' \
  -d '{"text":"{\"time\":\"2026-05-31T12:00:00Z\",\"priority\":\"WARNING\",\"rule\":\"Demo\",\"output\":\"authorized demo\"}"}'
```

Falco webhook-style JSON can be posted directly:

```bash
curl -X POST http://127.0.0.1:8767/api/webhook/falco \
  -H 'Content-Type: application/json' \
  -d '{"time":"2026-05-31T12:00:00Z","priority":"ERROR","rule":"Demo","output":"authorized webhook demo","output_fields":{"k8s.ns.name":"demo","proc.name":"sh"}}'
```

Each import is recorded as a replay batch. Replaying a batch increments
`dedupe_count` on existing events instead of creating duplicate rows:

```bash
curl -X POST http://127.0.0.1:8767/api/replay \
  -H 'Content-Type: application/json' \
  -d '{"batch_uid":"<batch uid from /api/replay-batches>"}'
```

## Current Feature Scope

- Event access: JSON/NDJSON file import, Falco webhook receiver, replay batches,
  stable event de-duplication, and per-event `dedupe_count`.
- Event model: rule, priority, output, time, hostname, container id/name, image,
  namespace, pod, node, user, process, command, labels, tags, raw event.
- Overview: event trend, high-risk count, rule hits, container distribution,
  namespace distribution, open tickets, queued notifications, suppressed events.
- Event center: filters, details, raw event, rule description, resource context,
  severity tags, JSON/CSV export.
- Kubernetes correlation: namespace, pod, container, node, image, labels.
- Noise reduction: false-positive disposition plus suppression by rule, image,
  namespace, container, process, or command fragment.
- Notification/ticket flow: high-risk unsuppressed events create local tickets
  and queued notification delivery records for enabled configs. Delivery records
  can be marked sent locally or posted to an approved HTTP(S) target.
- Reports: daily runtime security report, rule hit report, and container risk
  report written to Environment docs, with list and download APIs.

The SOC/ticket integration is intentionally represented as local records. No
external notification is sent without an explicit integration step.

## Reports, Tickets, And Notifications

```bash
curl -X POST http://127.0.0.1:8767/api/report \
  -H 'Content-Type: application/json' \
  -d '{"type":"daily"}'
curl http://127.0.0.1:8767/api/reports
curl http://127.0.0.1:8767/api/reports/<report-name>.md
curl -X PATCH http://127.0.0.1:8767/api/tickets/1 \
  -H 'Content-Type: application/json' \
  -d '{"status":"closed","owner":"runtime"}'
curl -X POST http://127.0.0.1:8767/api/notification-deliveries/1/deliver \
  -H 'Content-Type: application/json' \
  -d '{}'
```

#!/usr/bin/env python3
"""Falco runtime security event platform.

This module intentionally uses only the Python standard library so the Falco
template can run the demo platform without adding a build system or vendored
dependencies. It consumes Falco JSON/NDJSON events from authorized environments
or the bundled demo sample and stores runtime state under /Users/zhangjiyan/Environment
by default.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import io
import json
import logging
import os
import sqlite3
import sys
from datetime import datetime, timezone
from http import HTTPStatus
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, unquote, urlparse
from urllib.request import Request, urlopen


DEFAULT_ENV_ROOT = Path(os.environ.get("FALCO_PLATFORM_ENV_ROOT", "/Users/zhangjiyan/Environment"))
APP_NAME = "07-falco"
PRIORITY_SCORE = {
    "EMERGENCY": 100,
    "ALERT": 95,
    "CRITICAL": 90,
    "ERROR": 78,
    "WARNING": 64,
    "NOTICE": 48,
    "INFO": 30,
    "DEBUG": 12,
}
HIGH_PRIORITIES = {"EMERGENCY", "ALERT", "CRITICAL", "ERROR"}
PRIORITY_ORDER = {
    "DEBUG": 10,
    "INFO": 20,
    "NOTICE": 30,
    "WARNING": 40,
    "ERROR": 50,
    "CRITICAL": 60,
    "ALERT": 70,
    "EMERGENCY": 80,
}
RULE_DESCRIPTIONS = {
    "Terminal shell in container": "A shell process was started inside a container. Review the user, command, namespace, pod, and image before deciding whether this is approved operations activity.",
    "Write below binary dir": "A process wrote below a system binary directory. This can indicate unexpected image mutation or package installation at runtime.",
    "Unexpected outbound connection": "A container process opened outbound network traffic that should be reviewed against the service allowlist.",
    "Kubernetes client tool launched in container": "A Kubernetes client was launched inside a container. Confirm whether the workload is an approved operations toolbox.",
    "Container started": "Container lifecycle event used for runtime inventory and correlation.",
    "Sensitive mount read from container": "A process read a sensitive host-mounted path from a container. Review mount configuration and workload ownership.",
}


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def environment_paths(env_root: Path = DEFAULT_ENV_ROOT) -> dict[str, Path]:
    return {
        "database": env_root / "02-databases" / APP_NAME / "falco-events.sqlite3",
        "logs": env_root / "11-logs" / APP_NAME / "platform.log",
        "cache": env_root / "10-cache" / APP_NAME,
        "docs": env_root / "08-docs" / APP_NAME,
    }


def ensure_environment(env_root: Path = DEFAULT_ENV_ROOT) -> dict[str, Path]:
    paths = environment_paths(env_root)
    for key, path in paths.items():
        target = path.parent if key in {"database", "logs"} else path
        target.mkdir(parents=True, exist_ok=True)
    return paths


def configure_logging(log_file: Path) -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(message)s",
        handlers=[logging.FileHandler(log_file), logging.StreamHandler(sys.stdout)],
    )


def parse_event_time(value: Any) -> str:
    if not value:
        return utc_now()
    text = str(value)
    if text.endswith("Z"):
        return text
    if text.isdigit():
        raw = int(text)
        if raw > 10_000_000_000_000_000:
            return datetime.fromtimestamp(raw / 1_000_000_000, tz=timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")
        if raw > 10_000_000_000:
            return datetime.fromtimestamp(raw / 1000, tz=timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")
        return datetime.fromtimestamp(raw, tz=timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")
    try:
        return datetime.fromisoformat(text.replace("Z", "+00:00")).astimezone(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")
    except ValueError:
        return utc_now()


def pick(fields: dict[str, Any], *names: str) -> str:
    for name in names:
        value = fields.get(name)
        if value not in (None, "", "<NA>"):
            return str(value)
    return ""


def parse_labels(value: Any) -> dict[str, str]:
    if not value:
        return {}
    if isinstance(value, dict):
        return {str(key): str(val) for key, val in value.items() if val not in (None, "")}
    if isinstance(value, list):
        labels: dict[str, str] = {}
        for item in value:
            labels.update(parse_labels(item))
        return labels
    text = str(value).strip()
    if not text:
        return {}
    try:
        parsed = json.loads(text)
        if isinstance(parsed, dict):
            return {str(key): str(val) for key, val in parsed.items() if val not in (None, "")}
    except json.JSONDecodeError:
        pass
    labels = {}
    for part in text.replace(",", " ").split():
        if "=" in part:
            key, val = part.split("=", 1)
            labels[key.strip()] = val.strip()
    return labels


def extract_labels(event: dict[str, Any], fields: dict[str, Any]) -> dict[str, str]:
    labels: dict[str, str] = {}
    for value in (
        event.get("labels"),
        event.get("k8s_labels"),
        fields.get("k8s.pod.labels"),
        fields.get("k8s.ns.labels"),
        fields.get("k8s.labels"),
        fields.get("container.labels"),
    ):
        labels.update(parse_labels(value))
    for key, value in fields.items():
        if key.startswith("k8s.pod.label.") or key.startswith("k8s.ns.label.") or key.startswith("container.label."):
            labels[key.rsplit(".", 1)[-1]] = str(value)
    return labels


def stable_uid(event: dict[str, Any]) -> str:
    explicit = event.get("uuid") or event.get("id")
    if explicit:
        return str(explicit)
    payload = json.dumps({key: value for key, value in event.items() if not key.startswith("_")}, sort_keys=True, ensure_ascii=False)
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def stable_batch_uid(source_name: str, events: list[dict[str, Any]]) -> tuple[str, str]:
    payload = json.dumps(events, ensure_ascii=False, sort_keys=True)
    sha = hashlib.sha256(payload.encode("utf-8")).hexdigest()
    return hashlib.sha256(f"{source_name}:{sha}".encode("utf-8")).hexdigest(), sha


def priority_at_least(priority: str, minimum: str) -> bool:
    return PRIORITY_ORDER.get(priority.upper(), 0) >= PRIORITY_ORDER.get(minimum.upper(), PRIORITY_ORDER["ERROR"])


def calculate_risk(priority: str, fields: dict[str, Any], tags: list[str], whitelisted: bool) -> int:
    score = PRIORITY_SCORE.get(priority.upper(), 35)
    command = pick(fields, "proc.cmdline", "proc.cmd", "command")
    image = pick(fields, "container.image.repository", "container.image", "image")
    namespace = pick(fields, "k8s.ns.name", "namespace")
    if namespace and namespace not in {"default", "dev", "demo"}:
        score += 5
    if image and ":latest" in image:
        score += 4
    if any(token in command.lower() for token in ("/bin/sh", "bash", "curl ", "wget ", "nc ")):
        score += 6
    if any(tag.lower() in {"mitre_execution", "container", "k8s"} for tag in tags):
        score += 3
    if whitelisted:
        score -= 35
    return max(1, min(score, 100))


def normalize_event(event: dict[str, Any], whitelisted: bool = False) -> dict[str, Any]:
    raw_event = {key: value for key, value in event.items() if not key.startswith("_")}
    fields = event.get("output_fields") or event.get("fields") or {}
    if not isinstance(fields, dict):
        fields = {}
    tags = event.get("tags") or fields.get("tags") or []
    if isinstance(tags, str):
        tags = [part.strip() for part in tags.split(",") if part.strip()]
    if not isinstance(tags, list):
        tags = []
    priority = str(event.get("priority") or event.get("level") or "INFO").upper()
    labels = extract_labels(event, fields)
    container_image = pick(fields, "container.image.repository", "container.image")
    image_tag = pick(fields, "container.image.tag")
    if container_image and image_tag and ":" not in container_image:
        container_image = f"{container_image}:{image_tag}"
    normalized = {
        "event_uid": stable_uid(event),
        "event_time": parse_event_time(event.get("time") or fields.get("evt.time")),
        "source": str(event.get("source") or fields.get("evt.source") or "syscall"),
        "priority": priority,
        "rule": str(event.get("rule") or "Unknown Falco Rule"),
        "output": str(event.get("output") or event.get("message") or ""),
        "hostname": str(event.get("hostname") or pick(fields, "fd.hostname", "host.name", "hostname")),
        "namespace": pick(fields, "k8s.ns.name", "k8s.namespace.name", "namespace"),
        "pod": pick(fields, "k8s.pod.name", "pod.name", "pod"),
        "container_id": pick(fields, "container.id"),
        "container_name": pick(fields, "container.name"),
        "node": pick(fields, "k8s.node.name", "node.name", "node"),
        "image": container_image,
        "process_name": pick(fields, "proc.name", "process.name"),
        "process_pid": pick(fields, "proc.pid", "process.pid"),
        "command": pick(fields, "proc.cmdline", "proc.cmd", "command"),
        "user_name": pick(fields, "user.name", "user.loginname"),
        "tags_json": json.dumps(tags, ensure_ascii=False),
        "labels_json": json.dumps(labels, ensure_ascii=False, sort_keys=True),
        "raw_json": json.dumps(raw_event, ensure_ascii=False, sort_keys=True),
        "false_positive": 0,
        "status": "whitelisted" if whitelisted else "new",
        "whitelisted": 1 if whitelisted else 0,
        "suppressed": 1 if whitelisted else 0,
        "suppression_reason": "whitelist" if whitelisted else "",
        "risk_score": calculate_risk(priority, fields, tags, whitelisted),
        "dedupe_count": 1,
        "last_seen_at": utc_now(),
        "last_ingest_source": str(event.get("_ingest_source") or "import"),
        "created_at": utc_now(),
        "updated_at": utc_now(),
    }
    return normalized


class FalcoStore:
    def __init__(self, db_path: Path, docs_path: Path):
        self.db_path = db_path
        self.docs_path = docs_path
        self.conn = sqlite3.connect(db_path, check_same_thread=False)
        self.conn.row_factory = sqlite3.Row
        self.init_schema()

    def init_schema(self) -> None:
        self.conn.executescript(
            """
            CREATE TABLE IF NOT EXISTS falco_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                event_uid TEXT NOT NULL UNIQUE,
                event_time TEXT NOT NULL,
                source TEXT NOT NULL,
                priority TEXT NOT NULL,
                rule TEXT NOT NULL,
                output TEXT NOT NULL,
                hostname TEXT,
                namespace TEXT,
                pod TEXT,
                container_id TEXT,
                container_name TEXT,
                node TEXT,
                image TEXT,
                process_name TEXT,
                process_pid TEXT,
                command TEXT,
                user_name TEXT,
                tags_json TEXT NOT NULL,
                labels_json TEXT NOT NULL DEFAULT '{}',
                raw_json TEXT NOT NULL,
                risk_score INTEGER NOT NULL,
                false_positive INTEGER NOT NULL DEFAULT 0,
                whitelisted INTEGER NOT NULL DEFAULT 0,
                suppressed INTEGER NOT NULL DEFAULT 0,
                suppression_reason TEXT,
                dedupe_count INTEGER NOT NULL DEFAULT 1,
                last_seen_at TEXT,
                last_ingest_source TEXT,
                status TEXT NOT NULL DEFAULT 'new',
                disposition_note TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS whitelist_rules (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                rule TEXT,
                namespace TEXT,
                container_name TEXT,
                image TEXT,
                process_name TEXT,
                command_contains TEXT,
                enabled INTEGER NOT NULL DEFAULT 1,
                created_at TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS notification_configs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                channel TEXT NOT NULL,
                target TEXT NOT NULL,
                min_priority TEXT NOT NULL DEFAULT 'ERROR',
                enabled INTEGER NOT NULL DEFAULT 1,
                created_at TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS replay_batches (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                batch_uid TEXT NOT NULL UNIQUE,
                source_name TEXT NOT NULL,
                payload_sha TEXT NOT NULL,
                payload_json TEXT NOT NULL,
                event_count INTEGER NOT NULL,
                result_json TEXT NOT NULL,
                created_at TEXT NOT NULL,
                replayed_at TEXT
            );
            CREATE TABLE IF NOT EXISTS ticket_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                event_id INTEGER NOT NULL,
                title TEXT NOT NULL,
                severity TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'open',
                owner TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS notification_deliveries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                event_id INTEGER NOT NULL,
                notification_config_id INTEGER,
                channel TEXT NOT NULL,
                target TEXT NOT NULL,
                status TEXT NOT NULL,
                detail_json TEXT NOT NULL,
                created_at TEXT NOT NULL,
                sent_at TEXT,
                response_json TEXT
            );
            CREATE TABLE IF NOT EXISTS audit_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                action TEXT NOT NULL,
                target_type TEXT NOT NULL,
                target_id TEXT,
                detail_json TEXT NOT NULL,
                created_at TEXT NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_falco_events_time ON falco_events(event_time);
            CREATE INDEX IF NOT EXISTS idx_falco_events_rule ON falco_events(rule);
            CREATE INDEX IF NOT EXISTS idx_falco_events_priority ON falco_events(priority);
            CREATE INDEX IF NOT EXISTS idx_falco_events_namespace ON falco_events(namespace);
            CREATE INDEX IF NOT EXISTS idx_falco_events_container ON falco_events(container_id, container_name);
            CREATE INDEX IF NOT EXISTS idx_falco_events_suppressed ON falco_events(suppressed);
            """
        )
        self.migrate_schema()
        self.conn.commit()

    def migrate_schema(self) -> None:
        self.ensure_column("falco_events", "labels_json", "TEXT NOT NULL DEFAULT '{}'")
        self.ensure_column("falco_events", "suppressed", "INTEGER NOT NULL DEFAULT 0")
        self.ensure_column("falco_events", "suppression_reason", "TEXT")
        self.ensure_column("falco_events", "dedupe_count", "INTEGER NOT NULL DEFAULT 1")
        self.ensure_column("falco_events", "last_seen_at", "TEXT")
        self.ensure_column("falco_events", "last_ingest_source", "TEXT")
        self.ensure_column("whitelist_rules", "process_name", "TEXT")
        self.ensure_column("notification_deliveries", "sent_at", "TEXT")
        self.ensure_column("notification_deliveries", "response_json", "TEXT")

    def ensure_column(self, table: str, column: str, definition: str) -> None:
        columns = {row["name"] for row in self.conn.execute(f"PRAGMA table_info({table})").fetchall()}
        if column not in columns:
            self.conn.execute(f"ALTER TABLE {table} ADD COLUMN {column} {definition}")

    def audit(self, action: str, target_type: str, target_id: str | int | None, detail: dict[str, Any]) -> None:
        self.conn.execute(
            "INSERT INTO audit_log(action, target_type, target_id, detail_json, created_at) VALUES (?, ?, ?, ?, ?)",
            (action, target_type, str(target_id) if target_id is not None else None, json.dumps(detail, ensure_ascii=False), utc_now()),
        )
        self.conn.commit()

    def whitelist_match(self, event: dict[str, Any]) -> bool:
        rules = self.conn.execute("SELECT * FROM whitelist_rules WHERE enabled = 1").fetchall()
        for rule in rules:
            checks = []
            for key in ("rule", "namespace", "container_name", "image", "process_name"):
                expected = rule[key]
                if expected:
                    checks.append(str(event.get(key) or "") == expected)
            command_contains = rule["command_contains"]
            if command_contains:
                checks.append(command_contains.lower() in str(event.get("command") or "").lower())
            if checks and all(checks):
                return True
        return False

    def import_events(self, events: list[dict[str, Any]], source_name: str = "api", record_batch: bool = True) -> dict[str, Any]:
        imported = 0
        skipped = 0
        updated = 0
        triggered_tickets = 0
        notification_attempts = 0
        batch_uid, payload_sha = stable_batch_uid(source_name, events)
        for event in events:
            event["_ingest_source"] = source_name
            normalized = normalize_event(event)
            whitelisted = self.whitelist_match(normalized)
            normalized = normalize_event(event, whitelisted=whitelisted)
            columns = ", ".join(normalized.keys())
            placeholders = ", ".join(["?"] * len(normalized))
            values = list(normalized.values())
            try:
                cur = self.conn.execute(f"INSERT INTO falco_events ({columns}) VALUES ({placeholders})", values)
                imported += 1
                workflow = self.trigger_event_workflow(cur.lastrowid)
                triggered_tickets += workflow["tickets"]
                notification_attempts += workflow["notifications"]
            except sqlite3.IntegrityError:
                self.conn.execute(
                    """
                    UPDATE falco_events SET
                        risk_score = ?,
                        false_positive = false_positive,
                        whitelisted = MAX(whitelisted, ?),
                        suppressed = MAX(suppressed, ?),
                        suppression_reason = CASE WHEN ? = 1 THEN 'whitelist' ELSE suppression_reason END,
                        status = CASE WHEN ? = 1 THEN 'whitelisted' ELSE status END,
                        dedupe_count = dedupe_count + 1,
                        last_seen_at = ?,
                        last_ingest_source = ?,
                        updated_at = ?
                    WHERE event_uid = ?
                    """,
                    (
                        normalized["risk_score"],
                        normalized["whitelisted"],
                        normalized["suppressed"],
                        normalized["suppressed"],
                        normalized["whitelisted"],
                        utc_now(),
                        source_name,
                        utc_now(),
                        normalized["event_uid"],
                    ),
                )
                updated += 1
            except sqlite3.Error:
                skipped += 1
                logging.exception("failed to import falco event")
        self.conn.commit()
        result = {
            "batch_uid": batch_uid,
            "imported": imported,
            "deduped": updated,
            "skipped": skipped,
            "tickets": triggered_tickets,
            "notifications": notification_attempts,
        }
        if record_batch:
            self.record_replay_batch(batch_uid, source_name, payload_sha, events, result)
        self.audit("import_events", "event_batch", source_name, result)
        return result

    def record_replay_batch(self, batch_uid: str, source_name: str, payload_sha: str, events: list[dict[str, Any]], result: dict[str, Any]) -> None:
        clean_events = [{key: value for key, value in event.items() if not key.startswith("_")} for event in events]
        payload_json = json.dumps(clean_events, ensure_ascii=False, sort_keys=True)
        self.conn.execute(
            """
            INSERT INTO replay_batches(batch_uid, source_name, payload_sha, payload_json, event_count, result_json, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(batch_uid) DO UPDATE SET result_json = excluded.result_json
            """,
            (batch_uid, source_name, payload_sha, payload_json, len(events), json.dumps(result, ensure_ascii=False), utc_now()),
        )
        self.conn.commit()

    def replay_batch(self, batch_uid: str) -> dict[str, Any] | None:
        row = self.conn.execute("SELECT * FROM replay_batches WHERE batch_uid = ? OR id = ?", (batch_uid, batch_uid)).fetchone()
        if not row:
            return None
        events = json.loads(row["payload_json"])
        result = self.import_events(events, f"replay:{row['source_name']}", record_batch=False)
        self.conn.execute("UPDATE replay_batches SET replayed_at = ? WHERE id = ?", (utc_now(), row["id"]))
        self.conn.commit()
        self.audit("replay_batch", "event_batch", row["batch_uid"], result)
        return {"replayed_batch": dict(row), "result": result}

    def trigger_event_workflow(self, event_id: int) -> dict[str, int]:
        event = self.conn.execute("SELECT * FROM falco_events WHERE id = ?", (event_id,)).fetchone()
        if not event or event["suppressed"] or event["whitelisted"] or event["false_positive"]:
            return {"tickets": 0, "notifications": 0}
        if not priority_at_least(event["priority"], "ERROR"):
            return {"tickets": 0, "notifications": 0}
        ticket_count = self.ensure_ticket(event)
        notification_count = self.record_notification_deliveries(event)
        return {"tickets": ticket_count, "notifications": notification_count}

    def ensure_ticket(self, event: sqlite3.Row) -> int:
        existing = self.conn.execute("SELECT id FROM ticket_records WHERE event_id = ?", (event["id"],)).fetchone()
        if existing:
            return 0
        self.conn.execute(
            """
            INSERT INTO ticket_records(event_id, title, severity, status, owner, created_at, updated_at)
            VALUES (?, ?, ?, 'open', ?, ?, ?)
            """,
            (
                event["id"],
                f"[{event['priority']}] {event['rule']}",
                event["priority"],
                event["namespace"] or event["hostname"] or "runtime",
                utc_now(),
                utc_now(),
            ),
        )
        self.conn.commit()
        return 1

    def record_notification_deliveries(self, event: sqlite3.Row, configs: list[sqlite3.Row] | None = None) -> int:
        configs = configs if configs is not None else self.conn.execute("SELECT * FROM notification_configs WHERE enabled = 1").fetchall()
        count = 0
        for config in configs:
            if not priority_at_least(event["priority"], config["min_priority"]):
                continue
            existing = self.conn.execute(
                "SELECT id FROM notification_deliveries WHERE event_id = ? AND notification_config_id = ?",
                (event["id"], config["id"]),
            ).fetchone()
            if existing:
                continue
            self.conn.execute(
                """
                INSERT INTO notification_deliveries(event_id, notification_config_id, channel, target, status, detail_json, created_at)
                VALUES (?, ?, ?, ?, 'queued', ?, ?)
                """,
                (
                    event["id"],
                    config["id"],
                    config["channel"],
                    config["target"],
                    json.dumps({"rule": event["rule"], "priority": event["priority"], "risk_score": event["risk_score"]}, ensure_ascii=False),
                    utc_now(),
                ),
            )
            count += 1
        self.conn.commit()
        return count

    def deliver_notification(self, delivery_id: int) -> dict[str, Any] | None:
        delivery = self.conn.execute("SELECT * FROM notification_deliveries WHERE id = ?", (delivery_id,)).fetchone()
        if not delivery:
            return None
        detail = json.loads(delivery["detail_json"] or "{}")
        response: dict[str, Any]
        status = "sent"
        if delivery["target"].startswith(("http://", "https://")):
            try:
                request = Request(
                    delivery["target"],
                    data=json.dumps(detail, ensure_ascii=False).encode("utf-8"),
                    headers={"Content-Type": "application/json", "User-Agent": "FalcoRuntimePlatform/1.0"},
                    method="POST",
                )
                with urlopen(request, timeout=5) as result:
                    response = {"http_status": result.status, "reason": result.reason}
                    status = "sent" if 200 <= result.status < 300 else "failed"
            except Exception as exc:  # noqa: BLE001 - standard-library server records delivery failures.
                status = "failed"
                response = {"error": str(exc)}
        else:
            response = {"mode": "local-record", "target": delivery["target"]}
        self.conn.execute(
            "UPDATE notification_deliveries SET status = ?, sent_at = ?, response_json = ? WHERE id = ?",
            (status, utc_now(), json.dumps(response, ensure_ascii=False), delivery_id),
        )
        self.conn.commit()
        self.audit("deliver_notification", "notification_delivery", delivery_id, {"status": status, "response": response})
        return dict(self.conn.execute("SELECT * FROM notification_deliveries WHERE id = ?", (delivery_id,)).fetchone())

    def update_ticket(self, ticket_id: int, payload: dict[str, Any]) -> dict[str, Any] | None:
        row = self.conn.execute("SELECT * FROM ticket_records WHERE id = ?", (ticket_id,)).fetchone()
        if not row:
            return None
        status = str(payload.get("status") or row["status"])
        owner = str(payload.get("owner") or row["owner"] or "")
        self.conn.execute(
            "UPDATE ticket_records SET status = ?, owner = ?, updated_at = ? WHERE id = ?",
            (status, owner, utc_now(), ticket_id),
        )
        self.conn.commit()
        self.audit("update_ticket", "ticket", ticket_id, {"status": status, "owner": owner})
        return dict(self.conn.execute("SELECT * FROM ticket_records WHERE id = ?", (ticket_id,)).fetchone())

    def query_events(self, params: dict[str, list[str]]) -> tuple[list[sqlite3.Row], int]:
        where, args = self._event_filters(params)
        limit = min(int(first(params, "limit", "50")), 500)
        offset = max(int(first(params, "offset", "0")), 0)
        total = self.conn.execute(f"SELECT COUNT(*) AS count FROM falco_events {where}", args).fetchone()["count"]
        rows = self.conn.execute(
            f"SELECT * FROM falco_events {where} ORDER BY event_time DESC, id DESC LIMIT ? OFFSET ?",
            [*args, limit, offset],
        ).fetchall()
        return rows, total

    def _event_filters(self, params: dict[str, list[str]]) -> tuple[str, list[Any]]:
        clauses: list[str] = []
        args: list[Any] = []
        for field in ("priority", "rule", "namespace", "pod", "container_name", "node", "image", "status", "process_name"):
            value = first(params, field)
            if value:
                clauses.append(f"{field} = ?")
                args.append(value)
        q = first(params, "q")
        if q:
            clauses.append("(output LIKE ? OR command LIKE ? OR rule LIKE ? OR image LIKE ?)")
            like = f"%{q}%"
            args.extend([like, like, like, like])
        min_risk = first(params, "min_risk")
        if min_risk:
            clauses.append("risk_score >= ?")
            args.append(int(min_risk))
        false_positive = first(params, "false_positive")
        if false_positive in {"0", "1"}:
            clauses.append("false_positive = ?")
            args.append(int(false_positive))
        suppressed = first(params, "suppressed")
        if suppressed in {"0", "1"}:
            clauses.append("suppressed = ?")
            args.append(int(suppressed))
        where = "WHERE " + " AND ".join(clauses) if clauses else ""
        return where, args

    def dashboard(self, params: dict[str, list[str]]) -> dict[str, Any]:
        where, args = self._event_filters(params)
        total = self.conn.execute(f"SELECT COUNT(*) AS count FROM falco_events {where}", args).fetchone()["count"]
        high = self.conn.execute(
            f"SELECT COUNT(*) AS count FROM falco_events {where} {'AND' if where else 'WHERE'} priority IN ({','.join(['?'] * len(HIGH_PRIORITIES))})",
            [*args, *sorted(HIGH_PRIORITIES)],
        ).fetchone()["count"]
        avg_risk = self.conn.execute(f"SELECT COALESCE(ROUND(AVG(risk_score), 1), 0) AS value FROM falco_events {where}", args).fetchone()["value"]
        return {
            "total_events": total,
            "high_events": high,
            "avg_risk_score": avg_risk,
            "false_positive_events": self.scalar_count("false_positive = 1"),
            "whitelisted_events": self.scalar_count("whitelisted = 1"),
            "suppressed_events": self.scalar_count("suppressed = 1"),
            "open_tickets": self.scalar_count_table("ticket_records", "status = 'open'"),
            "queued_notifications": self.scalar_count_table("notification_deliveries", "status = 'queued'"),
            "priority_breakdown": self.group_count("priority", where, args),
            "rule_hits": self.group_count("rule", where, args, limit=8),
            "container_distribution": self.group_count("COALESCE(NULLIF(container_name, ''), NULLIF(container_id, ''), 'host')", where, args, limit=8),
            "host_distribution": self.group_count("COALESCE(NULLIF(node, ''), NULLIF(hostname, ''), 'unknown')", where, args, limit=8),
            "namespace_distribution": self.group_count("COALESCE(NULLIF(namespace, ''), 'host')", where, args, limit=8),
            "time_trend": self.time_trend(where, args),
        }

    def scalar_count(self, clause: str) -> int:
        return self.conn.execute(f"SELECT COUNT(*) AS count FROM falco_events WHERE {clause}").fetchone()["count"]

    def scalar_count_table(self, table: str, clause: str) -> int:
        return self.conn.execute(f"SELECT COUNT(*) AS count FROM {table} WHERE {clause}").fetchone()["count"]

    def group_count(self, field_expr: str, where: str = "", args: list[Any] | None = None, limit: int = 10) -> list[dict[str, Any]]:
        args = args or []
        rows = self.conn.execute(
            f"SELECT {field_expr} AS name, COUNT(*) AS count FROM falco_events {where} GROUP BY name ORDER BY count DESC, name ASC LIMIT ?",
            [*args, limit],
        ).fetchall()
        return [{"name": row["name"] or "unknown", "count": row["count"]} for row in rows]

    def time_trend(self, where: str = "", args: list[Any] | None = None) -> list[dict[str, Any]]:
        args = args or []
        rows = self.conn.execute(
            f"""
            SELECT substr(event_time, 1, 13) || ':00Z' AS bucket, COUNT(*) AS count,
                   SUM(CASE WHEN priority IN ('EMERGENCY','ALERT','CRITICAL','ERROR') THEN 1 ELSE 0 END) AS high
            FROM falco_events {where}
            GROUP BY bucket
            ORDER BY bucket ASC
            LIMIT 48
            """,
            args,
        ).fetchall()
        return [{"bucket": row["bucket"], "count": row["count"], "high": row["high"]} for row in rows]

    def rule_detail(self, rule_name: str) -> dict[str, Any] | None:
        rows = self.conn.execute("SELECT * FROM falco_events WHERE rule = ? ORDER BY event_time DESC", (rule_name,)).fetchall()
        if not rows:
            return None
        return {
            "rule": rule_name,
            "description": RULE_DESCRIPTIONS.get(rule_name, "Falco rule observed in imported runtime events. Review the output fields, Kubernetes context, process, image, and user before disposition."),
            "hits": len(rows),
            "highest_risk": max(row["risk_score"] for row in rows),
            "priorities": self.group_count("priority", "WHERE rule = ?", [rule_name]),
            "latest_events": [row_to_event(row) for row in rows[:20]],
            "tags": sorted({tag for row in rows for tag in json.loads(row["tags_json"] or "[]")}),
        }

    def disposition(self, event_id: int, payload: dict[str, Any]) -> dict[str, Any] | None:
        row = self.conn.execute("SELECT * FROM falco_events WHERE id = ?", (event_id,)).fetchone()
        if not row:
            return None
        false_positive = 1 if payload.get("false_positive") else 0
        status = str(payload.get("status") or ("false_positive" if false_positive else row["status"]))
        note = str(payload.get("note") or "")
        whitelisted = 1 if status == "whitelisted" else row["whitelisted"]
        suppressed = 1 if false_positive or whitelisted or status in {"suppressed", "whitelisted", "false_positive"} else row["suppressed"]
        suppression_reason = note if suppressed else row["suppression_reason"]
        risk = max(1, row["risk_score"] - 30) if false_positive or whitelisted else row["risk_score"]
        self.conn.execute(
            """
            UPDATE falco_events
            SET false_positive = ?, status = ?, disposition_note = ?, whitelisted = ?, suppressed = ?,
                suppression_reason = ?, risk_score = ?, updated_at = ?
            WHERE id = ?
            """,
            (false_positive, status, note, whitelisted, suppressed, suppression_reason, risk, utc_now(), event_id),
        )
        if status in {"closed", "false_positive", "whitelisted", "suppressed"}:
            self.conn.execute("UPDATE ticket_records SET status = 'closed', updated_at = ? WHERE event_id = ?", (utc_now(), event_id))
        self.conn.commit()
        self.audit("disposition", "event", event_id, {"status": status, "false_positive": false_positive, "note": note})
        return row_to_event(self.conn.execute("SELECT * FROM falco_events WHERE id = ?", (event_id,)).fetchone())

    def add_whitelist(self, payload: dict[str, Any]) -> dict[str, Any]:
        created_at = utc_now()
        cur = self.conn.execute(
            """
            INSERT INTO whitelist_rules(name, rule, namespace, container_name, image, process_name, command_contains, enabled, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                str(payload.get("name") or "runtime whitelist"),
                empty_to_none(payload.get("rule")),
                empty_to_none(payload.get("namespace")),
                empty_to_none(payload.get("container_name")),
                empty_to_none(payload.get("image")),
                empty_to_none(payload.get("process_name")),
                empty_to_none(payload.get("command_contains")),
                1 if payload.get("enabled", True) else 0,
                created_at,
            ),
        )
        self.conn.commit()
        self.apply_whitelists()
        self.audit("add_whitelist", "whitelist", cur.lastrowid, payload)
        return dict(self.conn.execute("SELECT * FROM whitelist_rules WHERE id = ?", (cur.lastrowid,)).fetchone())

    def apply_whitelists(self) -> int:
        changed = 0
        rows = self.conn.execute("SELECT * FROM falco_events").fetchall()
        for row in rows:
            event = row_to_event(row)
            if self.whitelist_match(event) and not row["whitelisted"]:
                self.conn.execute(
                    "UPDATE falco_events SET whitelisted = 1, suppressed = 1, suppression_reason = 'whitelist', status = 'whitelisted', risk_score = ?, updated_at = ? WHERE id = ?",
                    (max(1, row["risk_score"] - 35), utc_now(), row["id"]),
                )
                self.conn.execute("UPDATE ticket_records SET status = 'closed', updated_at = ? WHERE event_id = ?", (utc_now(), row["id"]))
                changed += 1
        self.conn.commit()
        return changed

    def add_notification(self, payload: dict[str, Any]) -> dict[str, Any]:
        cur = self.conn.execute(
            """
            INSERT INTO notification_configs(name, channel, target, min_priority, enabled, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            (
                str(payload.get("name") or "runtime notification"),
                str(payload.get("channel") or "webhook"),
                str(payload.get("target") or "local-demo-only"),
                str(payload.get("min_priority") or "ERROR").upper(),
                1 if payload.get("enabled", True) else 0,
                utc_now(),
            ),
        )
        self.conn.commit()
        self.audit("add_notification", "notification", cur.lastrowid, payload)
        self.trigger_existing_workflow_for_config(cur.lastrowid)
        return dict(self.conn.execute("SELECT * FROM notification_configs WHERE id = ?", (cur.lastrowid,)).fetchone())

    def trigger_existing_workflow_for_config(self, config_id: int) -> int:
        config = self.conn.execute("SELECT * FROM notification_configs WHERE id = ?", (config_id,)).fetchone()
        if not config:
            return 0
        rows = self.conn.execute("SELECT * FROM falco_events WHERE suppressed = 0 AND whitelisted = 0 AND false_positive = 0").fetchall()
        count = 0
        for event in rows:
            if priority_at_least(event["priority"], config["min_priority"]):
                self.ensure_ticket(event)
                count += self.record_notification_deliveries(event, [config])
        self.audit("trigger_existing_notifications", "notification", config_id, {"deliveries": count})
        return count

    def export_events(self, params: dict[str, list[str]]) -> tuple[str, str, bytes]:
        rows, _ = self.query_events({**params, "limit": ["500"]})
        fmt = first(params, "format", "json")
        stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
        if fmt == "csv":
            output = io.StringIO()
            fieldnames = [
                "id",
                "event_time",
                "priority",
                "risk_score",
                "rule",
                "namespace",
                "pod",
                "container_name",
                "node",
                "image",
                "process_name",
                "command",
                "status",
                "false_positive",
                "suppressed",
                "dedupe_count",
                "labels",
            ]
            writer = csv.DictWriter(output, fieldnames=fieldnames)
            writer.writeheader()
            for row in rows:
                event = row_to_event(row)
                writer.writerow({key: json.dumps(event.get(key), ensure_ascii=False) if key == "labels" else event.get(key, "") for key in fieldnames})
            return f"falco-events-{stamp}.csv", "text/csv; charset=utf-8", output.getvalue().encode("utf-8")
        payload = json.dumps([row_to_event(row) for row in rows], ensure_ascii=False, indent=2)
        return f"falco-events-{stamp}.json", "application/json; charset=utf-8", payload.encode("utf-8")

    def report(self, report_type: str = "daily") -> dict[str, Any]:
        report_type = report_type if report_type in {"daily", "rules", "containers"} else "daily"
        dashboard = self.dashboard({})
        recent_rows, _ = self.query_events({"limit": ["10"]})
        stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
        path = self.docs_path / f"falco-{report_type}-runtime-report-{stamp}.md"
        lines = [
            f"# 码研工坊运行时安全告警报告：{report_type.title()}",
            "",
            f"- Generated: {utc_now()}",
            f"- Total events: {dashboard['total_events']}",
            f"- High priority events: {dashboard['high_events']}",
            f"- Average risk score: {dashboard['avg_risk_score']}",
            f"- False positive events: {dashboard['false_positive_events']}",
            f"- Suppressed events: {dashboard['suppressed_events']}",
            f"- Open tickets: {dashboard['open_tickets']}",
            f"- Queued notifications: {dashboard['queued_notifications']}",
        ]
        if report_type == "rules":
            lines += ["", "## Rule Hit Report"]
            for item in dashboard["rule_hits"]:
                detail = self.rule_detail(item["name"]) or {}
                lines.append(f"- {item['name']}: hits={item['count']}, highest_risk={detail.get('highest_risk', 0)}")
        elif report_type == "containers":
            lines += ["", "## Container Risk Report"]
            rows = self.conn.execute(
                """
                SELECT COALESCE(NULLIF(container_name, ''), NULLIF(container_id, ''), 'host') AS container,
                       COALESCE(NULLIF(namespace, ''), 'host') AS namespace,
                       COALESCE(NULLIF(image, ''), 'unknown') AS image,
                       COUNT(*) AS events,
                       MAX(risk_score) AS highest_risk,
                       SUM(CASE WHEN priority IN ('EMERGENCY','ALERT','CRITICAL','ERROR') THEN 1 ELSE 0 END) AS high
                FROM falco_events
                GROUP BY container, namespace, image
                ORDER BY highest_risk DESC, events DESC
                LIMIT 20
                """
            ).fetchall()
            for row in rows:
                lines.append(f"- {row['namespace']}/{row['container']} image={row['image']} events={row['events']} high={row['high']} highest_risk={row['highest_risk']}")
        else:
            lines += ["", "## Top Rules"]
            lines += [f"- {item['name']}: {item['count']}" for item in dashboard["rule_hits"]]
            lines += ["", "## Recent Events"]
            for row in recent_rows:
                event = row_to_event(row)
                lines.append(f"- [{event['priority']}] risk={event['risk_score']} rule={event['rule']} namespace={event['namespace'] or 'host'} status={event['status']}")
        lines += [
            "",
            "## Scope",
            "This report summarizes authorized Falco JSON/demo events already imported into the local platform. It does not generate exploit traffic or container escape behavior.",
        ]
        path.write_text("\n".join(lines) + "\n", encoding="utf-8")
        self.audit("generate_report", "report", path.name, {"path": str(path), "type": report_type})
        return {"path": str(path), "type": report_type, "dashboard": dashboard}

    def list_reports(self) -> list[dict[str, Any]]:
        reports = []
        for path in sorted(self.docs_path.glob("falco-*-runtime-report-*.md"), reverse=True):
            reports.append(
                {
                    "name": path.name,
                    "path": str(path),
                    "size": path.stat().st_size,
                    "updated_at": datetime.fromtimestamp(path.stat().st_mtime, tz=timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z"),
                }
            )
        return reports

    def read_report(self, name: str) -> tuple[Path, bytes] | None:
        safe_name = Path(name).name
        path = self.docs_path / safe_name
        if not path.exists() or not path.is_file() or not safe_name.startswith("falco-") or path.suffix != ".md":
            return None
        return path, path.read_bytes()


def empty_to_none(value: Any) -> str | None:
    if value in (None, ""):
        return None
    return str(value)


def first(params: dict[str, list[str]], key: str, default: str = "") -> str:
    value = params.get(key)
    if not value:
        return default
    return value[0]


def row_to_event(row: sqlite3.Row | None) -> dict[str, Any]:
    if row is None:
        return {}
    event = dict(row)
    event["tags"] = json.loads(event.pop("tags_json") or "[]")
    event["labels"] = json.loads(event.pop("labels_json") or "{}")
    event["raw"] = json.loads(event.pop("raw_json") or "{}")
    event["false_positive"] = bool(event["false_positive"])
    event["whitelisted"] = bool(event["whitelisted"])
    event["suppressed"] = bool(event["suppressed"])
    event["resource_context"] = {
        "namespace": event.get("namespace"),
        "pod": event.get("pod"),
        "container_id": event.get("container_id"),
        "container_name": event.get("container_name"),
        "node": event.get("node"),
        "image": event.get("image"),
        "labels": event["labels"],
    }
    return event


def load_events_from_text(text: str) -> list[dict[str, Any]]:
    stripped = text.strip()
    if not stripped:
        return []
    if stripped.startswith("["):
        data = json.loads(stripped)
        if not isinstance(data, list):
            raise ValueError("JSON array import expected")
        return [item for item in data if isinstance(item, dict)]
    events = []
    for line_number, line in enumerate(stripped.splitlines(), start=1):
        if not line.strip():
            continue
        item = json.loads(line)
        if not isinstance(item, dict):
            raise ValueError(f"line {line_number} is not a JSON object")
        events.append(item)
    return events


class PlatformHandler(SimpleHTTPRequestHandler):
    store: FalcoStore
    static_dir: Path
    server_version = "FalcoRuntimePlatform/1.0"

    def __init__(self, *args: Any, **kwargs: Any):
        super().__init__(*args, directory=str(self.static_dir), **kwargs)

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path.startswith("/api/"):
            self.handle_api_get(parsed.path, parse_qs(parsed.query))
            return
        if parsed.path == "/":
            self.path = "/index.html"
        super().do_GET()

    def do_POST(self) -> None:
        parsed = urlparse(self.path)
        if not parsed.path.startswith("/api/"):
            self.send_error(HTTPStatus.NOT_FOUND)
            return
        payload = self.read_json()
        if parsed.path == "/api/import":
            events = payload.get("events") if isinstance(payload, dict) else None
            if events is None and isinstance(payload, dict) and payload.get("text"):
                events = load_events_from_text(str(payload["text"]))
            if not isinstance(events, list):
                self.write_json({"error": "events array or text payload required"}, HTTPStatus.BAD_REQUEST)
                return
            result = self.store.import_events([event for event in events if isinstance(event, dict)], "api")
            self.write_json(result)
            return
        if parsed.path == "/api/webhook/falco":
            if isinstance(payload, list):
                events = [event for event in payload if isinstance(event, dict)]
            elif isinstance(payload, dict) and isinstance(payload.get("events"), list):
                events = [event for event in payload["events"] if isinstance(event, dict)]
            elif isinstance(payload, dict):
                events = [payload]
            else:
                events = []
            if not events:
                self.write_json({"error": "Falco webhook payload must be an object, array, or events array"}, HTTPStatus.BAD_REQUEST)
                return
            self.write_json(self.store.import_events(events, "webhook"))
            return
        if parsed.path == "/api/replay":
            batch_uid = str(payload.get("batch_uid") or payload.get("id") or "") if isinstance(payload, dict) else ""
            result = self.store.replay_batch(batch_uid)
            if not result:
                self.write_json({"error": "replay batch not found"}, HTTPStatus.NOT_FOUND)
                return
            self.write_json(result)
            return
        if parsed.path == "/api/whitelist":
            self.write_json(self.store.add_whitelist(payload))
            return
        if parsed.path == "/api/notifications":
            self.write_json(self.store.add_notification(payload))
            return
        if parsed.path == "/api/report":
            report_type = str(payload.get("type") or "daily") if isinstance(payload, dict) else "daily"
            self.write_json(self.store.report(report_type))
            return
        if parsed.path.startswith("/api/notification-deliveries/") and parsed.path.endswith("/deliver"):
            delivery_id = int(parsed.path.split("/")[3])
            result = self.store.deliver_notification(delivery_id)
            if not result:
                self.write_json({"error": "notification delivery not found"}, HTTPStatus.NOT_FOUND)
                return
            self.write_json(result)
            return
        self.send_error(HTTPStatus.NOT_FOUND)

    def do_PATCH(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path.startswith("/api/events/") and parsed.path.endswith("/disposition"):
            event_id = int(parsed.path.split("/")[3])
            result = self.store.disposition(event_id, self.read_json())
            if not result:
                self.write_json({"error": "event not found"}, HTTPStatus.NOT_FOUND)
                return
            self.write_json(result)
            return
        if parsed.path.startswith("/api/tickets/"):
            ticket_id = int(parsed.path.split("/")[3])
            result = self.store.update_ticket(ticket_id, self.read_json())
            if not result:
                self.write_json({"error": "ticket not found"}, HTTPStatus.NOT_FOUND)
                return
            self.write_json(result)
            return
        self.send_error(HTTPStatus.NOT_FOUND)

    def handle_api_get(self, path: str, params: dict[str, list[str]]) -> None:
        if path == "/api/health":
            self.write_json({"ok": True, "database": str(self.store.db_path), "docs": str(self.store.docs_path)})
            return
        if path == "/api/dashboard":
            self.write_json(self.store.dashboard(params))
            return
        if path == "/api/events":
            rows, total = self.store.query_events(params)
            self.write_json({"total": total, "events": [row_to_event(row) for row in rows]})
            return
        if path.startswith("/api/events/"):
            event_id = int(path.split("/")[3])
            row = self.store.conn.execute("SELECT * FROM falco_events WHERE id = ?", (event_id,)).fetchone()
            if not row:
                self.write_json({"error": "event not found"}, HTTPStatus.NOT_FOUND)
                return
            self.write_json(row_to_event(row))
            return
        if path == "/api/rules":
            self.write_json({"rules": self.store.group_count("rule", limit=100)})
            return
        if path.startswith("/api/rules/"):
            name = unquote(path.removeprefix("/api/rules/"))
            detail = self.store.rule_detail(name)
            if not detail:
                self.write_json({"error": "rule not found"}, HTTPStatus.NOT_FOUND)
                return
            self.write_json(detail)
            return
        if path == "/api/containers":
            self.write_json({"containers": self.store.group_count("COALESCE(NULLIF(container_name, ''), NULLIF(container_id, ''), 'host')", limit=100)})
            return
        if path == "/api/processes":
            self.write_json({"processes": self.store.group_count("COALESCE(NULLIF(process_name, ''), 'unknown')", limit=100)})
            return
        if path == "/api/commands":
            self.write_json({"commands": self.store.group_count("COALESCE(NULLIF(command, ''), 'unknown')", limit=100)})
            return
        if path == "/api/whitelist":
            rows = self.store.conn.execute("SELECT * FROM whitelist_rules ORDER BY id DESC").fetchall()
            self.write_json({"whitelist": [dict(row) for row in rows]})
            return
        if path == "/api/replay-batches":
            rows = self.store.conn.execute("SELECT id, batch_uid, source_name, payload_sha, event_count, result_json, created_at, replayed_at FROM replay_batches ORDER BY id DESC LIMIT 100").fetchall()
            self.write_json({"batches": [dict(row) for row in rows]})
            return
        if path == "/api/tickets":
            rows = self.store.conn.execute("SELECT * FROM ticket_records ORDER BY id DESC LIMIT 100").fetchall()
            self.write_json({"tickets": [dict(row) for row in rows]})
            return
        if path == "/api/notification-deliveries":
            rows = self.store.conn.execute("SELECT * FROM notification_deliveries ORDER BY id DESC LIMIT 100").fetchall()
            self.write_json({"deliveries": [dict(row) for row in rows]})
            return
        if path == "/api/reports":
            self.write_json({"reports": self.store.list_reports()})
            return
        if path.startswith("/api/reports/"):
            report_name = unquote(path.removeprefix("/api/reports/"))
            report = self.store.read_report(report_name)
            if not report:
                self.write_json({"error": "report not found"}, HTTPStatus.NOT_FOUND)
                return
            report_path, data = report
            self.send_response(HTTPStatus.OK)
            self.send_header("Content-Type", "text/markdown; charset=utf-8")
            self.send_header("Content-Disposition", f'attachment; filename="{report_path.name}"')
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return
        if path == "/api/notifications":
            rows = self.store.conn.execute("SELECT * FROM notification_configs ORDER BY id DESC").fetchall()
            self.write_json({"notifications": [dict(row) for row in rows]})
            return
        if path == "/api/audit":
            rows = self.store.conn.execute("SELECT * FROM audit_log ORDER BY id DESC LIMIT 100").fetchall()
            self.write_json({"audit": [dict(row) for row in rows]})
            return
        if path == "/api/export":
            filename, content_type, data = self.store.export_events(params)
            self.send_response(HTTPStatus.OK)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Disposition", f'attachment; filename="{filename}"')
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return
        self.send_error(HTTPStatus.NOT_FOUND)

    def read_json(self) -> Any:
        length = int(self.headers.get("Content-Length", "0"))
        if not length:
            return {}
        data = self.rfile.read(length).decode("utf-8")
        return json.loads(data)

    def write_json(self, payload: dict[str, Any], status: HTTPStatus = HTTPStatus.OK) -> None:
        data = json.dumps(payload, ensure_ascii=False, indent=2).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


def import_file(store: FalcoStore, path: Path) -> dict[str, Any]:
    events = load_events_from_text(path.read_text(encoding="utf-8"))
    return store.import_events(events, str(path))


def build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Falco runtime security event platform")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8767)
    parser.add_argument("--env-root", default=str(DEFAULT_ENV_ROOT))
    parser.add_argument("--import-file", type=Path, help="Import Falco JSON array or NDJSON file, then exit unless --serve-after-import is set.")
    parser.add_argument("--serve-after-import", action="store_true")
    return parser


def main() -> int:
    args = build_arg_parser().parse_args()
    paths = ensure_environment(Path(args.env_root))
    configure_logging(paths["logs"])
    store = FalcoStore(paths["database"], paths["docs"])
    if args.import_file:
        result = import_file(store, args.import_file)
        print(json.dumps(result, ensure_ascii=False, indent=2))
        if not args.serve_after_import:
            return 0
    PlatformHandler.store = store
    PlatformHandler.static_dir = Path(__file__).resolve().parent / "static"
    server = ThreadingHTTPServer((args.host, args.port), PlatformHandler)
    logging.info("Falco runtime platform listening on http://%s:%s", args.host, args.port)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        logging.info("shutdown requested")
    finally:
        server.server_close()
        store.conn.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

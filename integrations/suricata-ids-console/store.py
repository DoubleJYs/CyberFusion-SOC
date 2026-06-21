"""SQLite-backed Suricata eve.json alert store.

The console is intentionally offline and passive: it imports authorized
Suricata eve.json alert records, normalizes fields for analysis, and keeps the
original raw event for audit/detail views.
"""

from __future__ import annotations

import csv
import hashlib
import io
import json
import os
import shutil
import sqlite3
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, Iterable, List, Mapping, Optional, Tuple

APP_KEY = "04-suricata"
DEFAULT_ENV_ROOT = Path(os.environ.get("IDS_CONSOLE_ENV_ROOT", str(Path.home() / "Environment")))
DEFAULT_DB_PATH = (
    DEFAULT_ENV_ROOT
    / "02-databases"
    / APP_KEY
    / "ids-console.sqlite3"
)
DEFAULT_LOG_DIR = DEFAULT_ENV_ROOT / "11-logs" / APP_KEY
DEFAULT_REPORT_DIR = DEFAULT_ENV_ROOT / "08-docs" / APP_KEY


def connect(db_path: Optional[str] = None) -> sqlite3.Connection:
    path = Path(db_path).expanduser() if db_path else DEFAULT_DB_PATH
    path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(path)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    conn.execute("PRAGMA journal_mode = WAL")
    init_db(conn)
    return conn


def init_db(conn: sqlite3.Connection) -> None:
    conn.executescript(
        """
        CREATE TABLE IF NOT EXISTS imports (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            source_path TEXT NOT NULL,
            imported_at TEXT NOT NULL,
            total_lines INTEGER NOT NULL DEFAULT 0,
            imported_alerts INTEGER NOT NULL DEFAULT 0,
            skipped_events INTEGER NOT NULL DEFAULT 0
        );

        CREATE TABLE IF NOT EXISTS import_files (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            source_path TEXT NOT NULL UNIQUE,
            fingerprint TEXT,
            status TEXT NOT NULL,
            attempts INTEGER NOT NULL DEFAULT 0,
            last_import_id INTEGER,
            last_error TEXT,
            updated_at TEXT NOT NULL,
            FOREIGN KEY (last_import_id) REFERENCES imports(id)
        );

        CREATE TABLE IF NOT EXISTS import_logs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            source_path TEXT NOT NULL,
            status TEXT NOT NULL,
            message TEXT NOT NULL,
            total_lines INTEGER NOT NULL DEFAULT 0,
            imported_alerts INTEGER NOT NULL DEFAULT 0,
            skipped_events INTEGER NOT NULL DEFAULT 0,
            created_at TEXT NOT NULL
        );

        CREATE TABLE IF NOT EXISTS alerts (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            event_hash TEXT NOT NULL UNIQUE,
            import_id INTEGER,
            timestamp TEXT NOT NULL,
            signature TEXT NOT NULL,
            category TEXT NOT NULL,
            severity INTEGER NOT NULL,
            src_ip TEXT NOT NULL,
            src_port INTEGER,
            dest_ip TEXT NOT NULL,
            dest_port INTEGER,
            proto TEXT NOT NULL,
            app_proto TEXT,
            flow_id INTEGER,
            in_iface TEXT,
            action TEXT,
            gid INTEGER,
            signature_id INTEGER,
            rev INTEGER,
            raw_event TEXT NOT NULL,
            false_positive INTEGER NOT NULL DEFAULT 0,
            false_positive_reason TEXT,
            false_positive_at TEXT,
            ignored INTEGER NOT NULL DEFAULT 0,
            ignore_reason TEXT,
            ignored_at TEXT,
            ticket_id INTEGER,
            created_at TEXT NOT NULL,
            FOREIGN KEY (import_id) REFERENCES imports(id),
            FOREIGN KEY (ticket_id) REFERENCES tickets(id)
        );

        CREATE TABLE IF NOT EXISTS tickets (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            status TEXT NOT NULL DEFAULT 'open',
            severity INTEGER NOT NULL,
            assignee TEXT,
            note TEXT,
            review_conclusion TEXT,
            closed_at TEXT,
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL
        );

        CREATE TABLE IF NOT EXISTS whitelist_entries (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            kind TEXT NOT NULL,
            value TEXT NOT NULL,
            reason TEXT,
            enabled INTEGER NOT NULL DEFAULT 1,
            created_at TEXT NOT NULL,
            UNIQUE(kind, value)
        );

        CREATE INDEX IF NOT EXISTS idx_alerts_timestamp ON alerts(timestamp);
        CREATE INDEX IF NOT EXISTS idx_alerts_severity ON alerts(severity);
        CREATE INDEX IF NOT EXISTS idx_alerts_signature ON alerts(signature);
        CREATE INDEX IF NOT EXISTS idx_alerts_category ON alerts(category);
        CREATE INDEX IF NOT EXISTS idx_alerts_src_ip ON alerts(src_ip);
        CREATE INDEX IF NOT EXISTS idx_alerts_dest_ip ON alerts(dest_ip);
        CREATE INDEX IF NOT EXISTS idx_alerts_fp ON alerts(false_positive);
        CREATE INDEX IF NOT EXISTS idx_alerts_ignored ON alerts(ignored);
        """
    )
    ensure_column(conn, "alerts", "ignored", "INTEGER NOT NULL DEFAULT 0")
    ensure_column(conn, "alerts", "ignore_reason", "TEXT")
    ensure_column(conn, "alerts", "ignored_at", "TEXT")
    ensure_column(conn, "tickets", "review_conclusion", "TEXT")
    ensure_column(conn, "tickets", "closed_at", "TEXT")
    conn.commit()


def ensure_column(conn: sqlite3.Connection, table: str, column: str, definition: str) -> None:
    columns = {row["name"] for row in conn.execute(f"PRAGMA table_info({table})")}
    if column not in columns:
        conn.execute(f"ALTER TABLE {table} ADD COLUMN {column} {definition}")


def import_path(
    conn: sqlite3.Connection,
    source_path: str,
    incremental: bool = True,
    retry_failed: bool = False,
) -> Dict[str, Any]:
    source = Path(source_path).expanduser()
    files = discover_eve_files(source)
    result = {
        "source_path": str(source),
        "files_seen": len(files),
        "files_imported": 0,
        "files_skipped": 0,
        "files_failed": 0,
        "total_lines": 0,
        "imported_alerts": 0,
        "skipped_events": 0,
        "details": [],
    }
    if not files:
        log_import(conn, str(source), "failed", "No eve.json/jsonl files found")
        result["files_failed"] = 1
        return result

    for file_path in files:
        detail = import_eve_file(conn, file_path, incremental=incremental, retry_failed=retry_failed)
        result["details"].append(detail)
        if detail["status"] == "imported":
            result["files_imported"] += 1
        elif detail["status"] == "skipped":
            result["files_skipped"] += 1
        else:
            result["files_failed"] += 1
        result["total_lines"] += detail.get("total_lines", 0)
        result["imported_alerts"] += detail.get("imported_alerts", 0)
        result["skipped_events"] += detail.get("skipped_events", 0)
    return result


def discover_eve_files(source: Path) -> List[Path]:
    if source.is_file():
        return [source]
    if not source.exists():
        return []
    candidates = []
    for path in sorted(source.rglob("*")):
        if path.is_file() and (
            path.name == "eve.json"
            or path.name.endswith(".eve.json")
            or path.suffix.lower() in {".json", ".jsonl", ".ndjson"}
        ):
            candidates.append(path)
    return candidates


def import_eve_file(
    conn: sqlite3.Connection,
    eve_path: Path,
    incremental: bool = True,
    retry_failed: bool = False,
) -> Dict[str, Any]:
    source = eve_path.expanduser()
    try:
        fingerprint = file_fingerprint(source)
    except OSError as exc:
        mark_import_file(conn, str(source), None, "failed", None, str(exc))
        log_import(conn, str(source), "failed", str(exc))
        return {"source_path": str(source), "status": "failed", "error": str(exc)}

    previous = conn.execute(
        "SELECT status, fingerprint FROM import_files WHERE source_path = ?",
        (str(source),),
    ).fetchone()
    if incremental and previous and previous["status"] == "success" and previous["fingerprint"] == fingerprint:
        log_import(conn, str(source), "skipped", "Unchanged file already imported")
        return {"source_path": str(source), "status": "skipped", "total_lines": 0, "imported_alerts": 0, "skipped_events": 0}
    if previous and previous["status"] == "failed" and not retry_failed:
        log_import(conn, str(source), "skipped", "Previous failure retained; run retry-failed")
        return {"source_path": str(source), "status": "skipped", "total_lines": 0, "imported_alerts": 0, "skipped_events": 0}

    try:
        stats = import_eve(conn, str(source))
    except OSError as exc:
        mark_import_file(conn, str(source), fingerprint, "failed", None, str(exc))
        log_import(conn, str(source), "failed", str(exc))
        return {"source_path": str(source), "status": "failed", "error": str(exc)}

    mark_import_file(conn, str(source), fingerprint, "success", stats["import_id"], None)
    log_import(
        conn,
        str(source),
        "success",
        "Imported eve alerts",
        stats["total_lines"],
        stats["imported_alerts"],
        stats["skipped_events"],
    )
    return {"source_path": str(source), "status": "imported", **stats}


def retry_failed_imports(conn: sqlite3.Connection) -> Dict[str, Any]:
    rows = conn.execute(
        "SELECT source_path FROM import_files WHERE status = 'failed' ORDER BY updated_at ASC"
    ).fetchall()
    result = {"retried": len(rows), "details": []}
    for row in rows:
        result["details"].append(import_eve_file(conn, Path(row["source_path"]), incremental=False, retry_failed=True))
    return result


def import_logs(conn: sqlite3.Connection, limit: int = 100) -> List[Dict[str, Any]]:
    return [
        dict(row)
        for row in conn.execute(
            """
            SELECT id, source_path, status, message, total_lines, imported_alerts, skipped_events, created_at
            FROM import_logs
            ORDER BY id DESC
            LIMIT ?
            """,
            (limit,),
        )
    ]


def import_eve(conn: sqlite3.Connection, eve_path: str) -> Dict[str, int]:
    source = Path(eve_path).expanduser()
    now = utc_now()
    cur = conn.execute(
        """
        INSERT INTO imports(source_path, imported_at, total_lines, imported_alerts, skipped_events)
        VALUES (?, ?, 0, 0, 0)
        """,
        (str(source), now),
    )
    import_id = int(cur.lastrowid)
    total_lines = 0
    imported_alerts = 0
    skipped_events = 0

    with source.open("r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            total_lines += 1
            try:
                event = json.loads(line)
            except json.JSONDecodeError:
                skipped_events += 1
                continue
            if event.get("event_type") != "alert" or not isinstance(event.get("alert"), dict):
                skipped_events += 1
                continue
            if is_whitelisted(conn, event):
                skipped_events += 1
                continue
            normalized = normalize_alert(event, import_id)
            inserted = insert_alert(conn, normalized)
            if inserted:
                imported_alerts += 1
            else:
                skipped_events += 1

    conn.execute(
        """
        UPDATE imports
        SET total_lines = ?, imported_alerts = ?, skipped_events = ?
        WHERE id = ?
        """,
        (total_lines, imported_alerts, skipped_events, import_id),
    )
    conn.commit()
    return {
        "import_id": import_id,
        "total_lines": total_lines,
        "imported_alerts": imported_alerts,
        "skipped_events": skipped_events,
    }


def normalize_alert(event: Mapping[str, Any], import_id: Optional[int]) -> Dict[str, Any]:
    alert = event.get("alert") if isinstance(event.get("alert"), Mapping) else {}
    timestamp = str(event.get("timestamp") or utc_now())
    signature = str(alert.get("signature") or "Unknown signature")
    category = str(alert.get("category") or "Uncategorized")
    severity = coerce_int(alert.get("severity"), default=3)
    src_ip = str(event.get("src_ip") or "unknown")
    dest_ip = str(event.get("dest_ip") or "unknown")
    proto = str(event.get("proto") or "unknown").upper()
    raw_event = json.dumps(event, ensure_ascii=False, sort_keys=True)
    event_hash = stable_hash(
        [
            timestamp,
            signature,
            str(alert.get("signature_id") or ""),
            src_ip,
            str(event.get("src_port") or ""),
            dest_ip,
            str(event.get("dest_port") or ""),
            proto,
            raw_event,
        ]
    )
    return {
        "event_hash": event_hash,
        "import_id": import_id,
        "timestamp": timestamp,
        "signature": signature,
        "category": category,
        "severity": severity,
        "src_ip": src_ip,
        "src_port": coerce_optional_int(event.get("src_port")),
        "dest_ip": dest_ip,
        "dest_port": coerce_optional_int(event.get("dest_port")),
        "proto": proto,
        "app_proto": event.get("app_proto"),
        "flow_id": coerce_optional_int(event.get("flow_id")),
        "in_iface": event.get("in_iface"),
        "action": alert.get("action"),
        "gid": coerce_optional_int(alert.get("gid")),
        "signature_id": coerce_optional_int(alert.get("signature_id")),
        "rev": coerce_optional_int(alert.get("rev")),
        "raw_event": raw_event,
        "created_at": utc_now(),
    }


def file_fingerprint(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def mark_import_file(
    conn: sqlite3.Connection,
    source_path: str,
    fingerprint: Optional[str],
    status: str,
    import_id: Optional[int],
    error: Optional[str],
) -> None:
    now = utc_now()
    conn.execute(
        """
        INSERT INTO import_files(source_path, fingerprint, status, attempts, last_import_id, last_error, updated_at)
        VALUES (?, ?, ?, 1, ?, ?, ?)
        ON CONFLICT(source_path) DO UPDATE SET
            fingerprint = excluded.fingerprint,
            status = excluded.status,
            attempts = import_files.attempts + 1,
            last_import_id = excluded.last_import_id,
            last_error = excluded.last_error,
            updated_at = excluded.updated_at
        """,
        (source_path, fingerprint, status, import_id, error, now),
    )
    conn.commit()


def log_import(
    conn: sqlite3.Connection,
    source_path: str,
    status: str,
    message: str,
    total_lines: int = 0,
    imported_alerts: int = 0,
    skipped_events: int = 0,
) -> None:
    conn.execute(
        """
        INSERT INTO import_logs(source_path, status, message, total_lines, imported_alerts, skipped_events, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        (source_path, status, message, total_lines, imported_alerts, skipped_events, utc_now()),
    )
    conn.commit()


def insert_alert(conn: sqlite3.Connection, alert: Mapping[str, Any]) -> bool:
    try:
        conn.execute(
            """
            INSERT INTO alerts(
                event_hash, import_id, timestamp, signature, category, severity,
                src_ip, src_port, dest_ip, dest_port, proto, app_proto, flow_id,
                in_iface, action, gid, signature_id, rev, raw_event, created_at
            )
            VALUES (
                :event_hash, :import_id, :timestamp, :signature, :category, :severity,
                :src_ip, :src_port, :dest_ip, :dest_port, :proto, :app_proto, :flow_id,
                :in_iface, :action, :gid, :signature_id, :rev, :raw_event, :created_at
            )
            """,
            alert,
        )
        return True
    except sqlite3.IntegrityError:
        return False


def summary(conn: sqlite3.Connection) -> Dict[str, Any]:
    counts = conn.execute(
        """
        SELECT
            COUNT(*) AS total,
            SUM(CASE WHEN severity <= 2 THEN 1 ELSE 0 END) AS high,
            SUM(CASE WHEN false_positive = 1 THEN 1 ELSE 0 END) AS false_positive,
            COUNT(DISTINCT signature) AS signatures,
            COUNT(DISTINCT src_ip) AS source_ips,
            COUNT(DISTINCT dest_ip) AS destination_ips
        FROM alerts
        """
    ).fetchone()
    return {
        "totals": dict(counts) if counts else {},
        "severity": query_counts(conn, "severity", "severity", limit=8),
        "categories": query_counts(conn, "category", "category", limit=12),
        "top_sources": query_counts(conn, "src_ip", "src_ip", limit=10),
        "top_destinations": query_counts(conn, "dest_ip", "dest_ip", limit=10),
        "protocols": query_counts(conn, "proto", "proto", limit=10),
        "trend": time_trend(conn),
        "latest_imports": [dict(row) for row in conn.execute(
            """
            SELECT id, source_path, imported_at, total_lines, imported_alerts, skipped_events
            FROM imports
            ORDER BY id DESC
            LIMIT 5
            """
        )],
        "latest_import_logs": import_logs(conn, limit=5),
    }


def query_counts(
    conn: sqlite3.Connection, field: str, alias: str, limit: int = 10
) -> List[Dict[str, Any]]:
    sql = f"""
        SELECT {field} AS {alias}, COUNT(*) AS count
        FROM alerts
        GROUP BY {field}
        ORDER BY count DESC, {field} ASC
        LIMIT ?
    """
    return [dict(row) for row in conn.execute(sql, (limit,))]


def time_trend(conn: sqlite3.Connection) -> List[Dict[str, Any]]:
    return [
        dict(row)
        for row in conn.execute(
            """
            SELECT substr(timestamp, 1, 13) || ':00' AS bucket, COUNT(*) AS count
            FROM alerts
            GROUP BY bucket
            ORDER BY bucket ASC
            LIMIT 240
            """
        )
    ]


def list_alerts(conn: sqlite3.Connection, params: Mapping[str, str]) -> Dict[str, Any]:
    where, values = build_alert_filters(params)
    page = max(coerce_int(params.get("page"), 1), 1)
    page_size = min(max(coerce_int(params.get("page_size"), 20), 1), 200)
    offset = (page - 1) * page_size
    order = "timestamp DESC, id DESC"

    total = conn.execute(f"SELECT COUNT(*) AS count FROM alerts {where}", values).fetchone()[
        "count"
    ]
    rows = conn.execute(
        f"""
        SELECT id, timestamp, signature, category, severity, src_ip, src_port,
               dest_ip, dest_port, proto, app_proto, action, false_positive, ignored, ticket_id
        FROM alerts
        {where}
        ORDER BY {order}
        LIMIT ? OFFSET ?
        """,
        (*values, page_size, offset),
    ).fetchall()
    return {
        "items": [serialize_alert_row(row) for row in rows],
        "page": page,
        "page_size": page_size,
        "total": total,
    }


def alert_detail(conn: sqlite3.Connection, alert_id: int) -> Optional[Dict[str, Any]]:
    row = conn.execute(
        """
        SELECT a.*, t.title AS ticket_title, t.status AS ticket_status,
               t.review_conclusion AS ticket_review_conclusion,
               t.closed_at AS ticket_closed_at
        FROM alerts a
        LEFT JOIN tickets t ON t.id = a.ticket_id
        WHERE a.id = ?
        """,
        (alert_id,),
    ).fetchone()
    if row is None:
        return None
    item = serialize_alert_row(row)
    item.update(
        {
            "gid": row["gid"],
            "signature_id": row["signature_id"],
            "rev": row["rev"],
            "flow_id": row["flow_id"],
            "in_iface": row["in_iface"],
            "raw_event": json.loads(row["raw_event"]),
            "false_positive_reason": row["false_positive_reason"],
            "false_positive_at": row["false_positive_at"],
            "ignore_reason": row["ignore_reason"],
            "ignored_at": row["ignored_at"],
            "ticket": {
                "id": row["ticket_id"],
                "title": row["ticket_title"],
                "status": row["ticket_status"],
                "review_conclusion": row["ticket_review_conclusion"],
                "closed_at": row["ticket_closed_at"],
            }
            if row["ticket_id"]
            else None,
        }
    )
    return item


def aggregate_alerts(conn: sqlite3.Connection) -> Dict[str, Any]:
    duplicate_groups = [
        dict(row)
        for row in conn.execute(
            """
            SELECT signature, signature_id, category, severity, src_ip, dest_ip, proto,
                   substr(timestamp, 1, 15) || '0:00' AS time_window,
                   COUNT(*) AS count,
                   MIN(timestamp) AS first_seen,
                   MAX(timestamp) AS last_seen,
                   SUM(CASE WHEN false_positive = 1 THEN 1 ELSE 0 END) AS false_positive_count,
                   SUM(CASE WHEN ignored = 1 THEN 1 ELSE 0 END) AS ignored_count
            FROM alerts
            GROUP BY signature, signature_id, category, severity, src_ip, dest_ip, proto, time_window
            HAVING COUNT(*) > 1
            ORDER BY count DESC, last_seen DESC
            LIMIT 50
            """
        )
    ]
    by_rule = [
        dict(row)
        for row in conn.execute(
            """
            SELECT signature, category, severity, COUNT(*) AS count,
                   COUNT(DISTINCT src_ip) AS source_ips,
                   COUNT(DISTINCT dest_ip) AS destination_ips,
                   MIN(timestamp) AS first_seen,
                   MAX(timestamp) AS last_seen
            FROM alerts
            GROUP BY signature, category, severity
            ORDER BY count DESC, last_seen DESC
            LIMIT 50
            """
        )
    ]
    return {"duplicate_groups": duplicate_groups, "by_rule": by_rule}


def ip_analysis(conn: sqlite3.Connection, ip: Optional[str] = None) -> Dict[str, Any]:
    values: Tuple[Any, ...] = ()
    where = ""
    if ip:
        where = "WHERE src_ip = ? OR dest_ip = ?"
        values = (ip, ip)
    rows = [
        dict(row)
        for row in conn.execute(
            f"""
            SELECT
                CASE WHEN src_ip = ? THEN dest_ip ELSE src_ip END AS peer_ip,
                SUM(CASE WHEN src_ip = ? THEN 1 ELSE 0 END) AS outbound_count,
                SUM(CASE WHEN dest_ip = ? THEN 1 ELSE 0 END) AS inbound_count,
                COUNT(*) AS total,
                MIN(timestamp) AS first_seen,
                MAX(timestamp) AS last_seen
            FROM alerts
            WHERE (? IS NULL OR src_ip = ? OR dest_ip = ?)
            GROUP BY peer_ip
            ORDER BY total DESC
            LIMIT 50
            """,
            (ip or "", ip or "", ip or "", ip, ip or "", ip or ""),
        )
    ]
    top_ips = [
        dict(row)
        for row in conn.execute(
            f"""
            SELECT ip, SUM(count) AS count
            FROM (
                SELECT src_ip AS ip, COUNT(*) AS count FROM alerts {where} GROUP BY src_ip
                UNION ALL
                SELECT dest_ip AS ip, COUNT(*) AS count FROM alerts {where} GROUP BY dest_ip
            )
            GROUP BY ip
            ORDER BY count DESC
            LIMIT 50
            """,
            (*values, *values),
        )
    ]
    return {"focus_ip": ip, "top_ips": top_ips, "peers": rows}


def timeline(conn: sqlite3.Connection, params: Mapping[str, str]) -> List[Dict[str, Any]]:
    where, values = build_alert_filters(params)
    return [
        serialize_alert_row(row)
        for row in conn.execute(
            f"""
            SELECT id, timestamp, signature, category, severity, src_ip, src_port,
                   dest_ip, dest_port, proto, app_proto, action, false_positive, ignored, ticket_id
            FROM alerts
            {where}
            ORDER BY timestamp ASC, id ASC
            LIMIT 300
            """,
            values,
        )
    ]


def mark_false_positive(
    conn: sqlite3.Connection, alert_id: int, value: bool, reason: Optional[str]
) -> bool:
    cur = conn.execute(
        """
        UPDATE alerts
        SET false_positive = ?,
            false_positive_reason = ?,
            false_positive_at = ?
        WHERE id = ?
        """,
        (1 if value else 0, reason if value else None, utc_now() if value else None, alert_id),
    )
    conn.commit()
    return cur.rowcount > 0


def mark_ignored(conn: sqlite3.Connection, alert_id: int, value: bool, reason: Optional[str]) -> bool:
    cur = conn.execute(
        """
        UPDATE alerts
        SET ignored = ?,
            ignore_reason = ?,
            ignored_at = ?
        WHERE id = ?
        """,
        (1 if value else 0, reason if value else None, utc_now() if value else None, alert_id),
    )
    conn.commit()
    return cur.rowcount > 0


def add_whitelist(conn: sqlite3.Connection, kind: str, value: str, reason: Optional[str]) -> Dict[str, Any]:
    if kind not in {"src_ip", "dest_ip", "signature", "signature_id"}:
        raise ValueError("Unsupported whitelist kind")
    now = utc_now()
    conn.execute(
        """
        INSERT INTO whitelist_entries(kind, value, reason, enabled, created_at)
        VALUES (?, ?, ?, 1, ?)
        ON CONFLICT(kind, value) DO UPDATE SET
            reason = excluded.reason,
            enabled = 1
        """,
        (kind, value, reason, now),
    )
    conn.commit()
    return dict(
        conn.execute(
            "SELECT id, kind, value, reason, enabled, created_at FROM whitelist_entries WHERE kind = ? AND value = ?",
            (kind, value),
        ).fetchone()
    )


def whitelist_entries(conn: sqlite3.Connection) -> List[Dict[str, Any]]:
    return [
        dict(row)
        for row in conn.execute(
            "SELECT id, kind, value, reason, enabled, created_at FROM whitelist_entries ORDER BY id DESC"
        )
    ]


def is_whitelisted(conn: sqlite3.Connection, event: Mapping[str, Any]) -> bool:
    alert = event.get("alert") if isinstance(event.get("alert"), Mapping) else {}
    checks = {
        "src_ip": str(event.get("src_ip") or ""),
        "dest_ip": str(event.get("dest_ip") or ""),
        "signature": str(alert.get("signature") or ""),
        "signature_id": str(alert.get("signature_id") or ""),
    }
    rows = conn.execute(
        "SELECT kind, value FROM whitelist_entries WHERE enabled = 1"
    ).fetchall()
    return any(checks.get(row["kind"]) == row["value"] for row in rows)


def create_ticket(
    conn: sqlite3.Connection, alert_id: int, assignee: Optional[str], note: Optional[str]
) -> Optional[Dict[str, Any]]:
    alert = conn.execute("SELECT * FROM alerts WHERE id = ?", (alert_id,)).fetchone()
    if alert is None:
        return None
    now = utc_now()
    title = f"IDS alert: {alert['signature']}"
    cur = conn.execute(
        """
        INSERT INTO tickets(title, status, severity, assignee, note, created_at, updated_at)
        VALUES (?, 'open', ?, ?, ?, ?, ?)
        """,
        (title, alert["severity"], assignee, note, now, now),
    )
    ticket_id = int(cur.lastrowid)
    conn.execute("UPDATE alerts SET ticket_id = ? WHERE id = ?", (ticket_id, alert_id))
    conn.commit()
    return dict(
        conn.execute(
            """
            SELECT id, title, status, severity, assignee, note, review_conclusion, closed_at, created_at, updated_at
            FROM tickets
            WHERE id = ?
            """,
            (ticket_id,),
        ).fetchone()
    )


def update_ticket(
    conn: sqlite3.Connection,
    ticket_id: int,
    status: str,
    review_conclusion: Optional[str] = None,
    note: Optional[str] = None,
) -> Optional[Dict[str, Any]]:
    if status not in {"open", "investigating", "resolved", "closed"}:
        raise ValueError("Unsupported ticket status")
    now = utc_now()
    closed_at = now if status in {"resolved", "closed"} else None
    cur = conn.execute(
        """
        UPDATE tickets
        SET status = ?,
            review_conclusion = COALESCE(?, review_conclusion),
            note = COALESCE(?, note),
            closed_at = COALESCE(?, closed_at),
            updated_at = ?
        WHERE id = ?
        """,
        (status, review_conclusion, note, closed_at, now, ticket_id),
    )
    conn.commit()
    if cur.rowcount == 0:
        return None
    return dict(
        conn.execute(
            """
            SELECT id, title, status, severity, assignee, note, review_conclusion, closed_at, created_at, updated_at
            FROM tickets
            WHERE id = ?
            """,
            (ticket_id,),
        ).fetchone()
    )


def report(conn: sqlite3.Connection) -> Dict[str, Any]:
    return {
        "generated_at": utc_now(),
        "summary": summary(conn),
        "aggregation": aggregate_alerts(conn),
        "open_tickets": [
            dict(row)
            for row in conn.execute(
                """
                SELECT id, title, status, severity, assignee, note, review_conclusion, closed_at, created_at, updated_at
                FROM tickets
                ORDER BY updated_at DESC
                LIMIT 100
                """
            )
        ],
    }


def report_by_kind(conn: sqlite3.Connection, kind: str = "daily") -> Dict[str, Any]:
    if kind == "daily":
        return ids_daily_report(conn)
    if kind == "high":
        return high_severity_report(conn)
    if kind == "rules":
        return rule_hit_report(conn)
    raise ValueError("Unsupported report kind")


def ids_daily_report(conn: sqlite3.Connection) -> Dict[str, Any]:
    return {
        "kind": "ids_daily",
        "generated_at": utc_now(),
        "summary": summary(conn),
        "aggregation": aggregate_alerts(conn),
        "tickets": ticket_report(conn),
    }


def high_severity_report(conn: sqlite3.Connection) -> Dict[str, Any]:
    rows = [
        serialize_alert_row(row)
        for row in conn.execute(
            """
            SELECT id, timestamp, signature, category, severity, src_ip, src_port,
                   dest_ip, dest_port, proto, app_proto, action, false_positive, ignored, ticket_id
            FROM alerts
            WHERE severity <= 2 AND ignored = 0
            ORDER BY severity ASC, timestamp DESC
            LIMIT 200
            """
        )
    ]
    return {"kind": "high_severity", "generated_at": utc_now(), "alerts": rows}


def rule_hit_report(conn: sqlite3.Connection) -> Dict[str, Any]:
    rows = [
        dict(row)
        for row in conn.execute(
            """
            SELECT signature, signature_id, category, severity,
                   COUNT(*) AS hits,
                   COUNT(DISTINCT src_ip) AS source_ips,
                   COUNT(DISTINCT dest_ip) AS destination_ips,
                   SUM(CASE WHEN false_positive = 1 THEN 1 ELSE 0 END) AS false_positive_count,
                   SUM(CASE WHEN ignored = 1 THEN 1 ELSE 0 END) AS ignored_count,
                   MIN(timestamp) AS first_seen,
                   MAX(timestamp) AS last_seen
            FROM alerts
            GROUP BY signature, signature_id, category, severity
            ORDER BY hits DESC, last_seen DESC
            LIMIT 200
            """
        )
    ]
    return {"kind": "rule_hits", "generated_at": utc_now(), "rules": rows}


def ticket_report(conn: sqlite3.Connection) -> List[Dict[str, Any]]:
    return [
        dict(row)
        for row in conn.execute(
            """
            SELECT id, title, status, severity, assignee, note, review_conclusion, closed_at, created_at, updated_at
            FROM tickets
            ORDER BY updated_at DESC
            LIMIT 200
            """
        )
    ]


def export_report_file(conn: sqlite3.Connection, kind: str, output_path: str) -> str:
    target = Path(output_path).expanduser()
    target.parent.mkdir(parents=True, exist_ok=True)
    payload = report_by_kind(conn, kind)
    if target.suffix.lower() == ".json":
        target.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    else:
        target.write_text(render_report_markdown(payload), encoding="utf-8")
    return str(target)


def init_demo_eve(source_example: Optional[Path] = None) -> str:
    DEFAULT_LOG_DIR.mkdir(parents=True, exist_ok=True)
    target = DEFAULT_LOG_DIR / "demo-eve.json"
    if source_example and source_example.exists():
        shutil.copy2(source_example, target)
    else:
        target.write_text("", encoding="utf-8")
    return str(target)


def render_report_markdown(payload: Mapping[str, Any]) -> str:
    lines = [f"# 码研工坊 IDS 告警分析报告：{payload.get('kind')}", "", f"Generated: {payload.get('generated_at')}", ""]
    if payload.get("summary"):
        totals = payload["summary"].get("totals", {})
        lines.extend(["## Summary", ""])
        for key, value in totals.items():
            lines.append(f"- {key}: {value}")
        lines.append("")
    if payload.get("alerts"):
        lines.extend(["## High Severity Alerts", ""])
        for alert in payload["alerts"][:50]:
            lines.append(f"- [{alert['severity_label']}] {alert['timestamp']} {alert['signature']} {alert['src_ip']} -> {alert['dest_ip']}")
        lines.append("")
    if payload.get("rules"):
        lines.extend(["## Rule Hits", ""])
        for rule in payload["rules"][:50]:
            lines.append(f"- {rule['signature']} ({rule['signature_id']}): {rule['hits']} hits")
        lines.append("")
    if payload.get("tickets"):
        lines.extend(["## Tickets", ""])
        for ticket in payload["tickets"][:50]:
            lines.append(f"- #{ticket['id']} {ticket['status']} {ticket['title']}")
        lines.append("")
    return "\n".join(lines)


def export_alerts_csv(conn: sqlite3.Connection, params: Mapping[str, str]) -> str:
    where, values = build_alert_filters(params)
    rows = conn.execute(
        f"""
        SELECT id, timestamp, signature, category, severity, src_ip, src_port,
               dest_ip, dest_port, proto, app_proto, action, false_positive, ignored, ticket_id
        FROM alerts
        {where}
        ORDER BY timestamp DESC, id DESC
        LIMIT 10000
        """,
        values,
    )
    output = io.StringIO()
    writer = csv.writer(output)
    writer.writerow(
        [
            "id",
            "timestamp",
            "signature",
            "category",
            "severity",
            "src_ip",
            "src_port",
            "dest_ip",
            "dest_port",
            "proto",
            "app_proto",
            "action",
            "false_positive",
            "ignored",
            "ticket_id",
        ]
    )
    for row in rows:
        writer.writerow([row[key] for key in row.keys()])
    return output.getvalue()


def build_alert_filters(params: Mapping[str, str]) -> Tuple[str, Tuple[Any, ...]]:
    clauses: List[str] = []
    values: List[Any] = []
    search = (params.get("search") or "").strip()
    if search:
        clauses.append(
            "(signature LIKE ? OR category LIKE ? OR src_ip LIKE ? OR dest_ip LIKE ? OR proto LIKE ?)"
        )
        like = f"%{search}%"
        values.extend([like, like, like, like, like])
    if params.get("severity"):
        clauses.append("severity = ?")
        values.append(coerce_int(params.get("severity"), 3))
    if params.get("category"):
        clauses.append("category = ?")
        values.append(params["category"])
    if params.get("src_ip"):
        clauses.append("src_ip = ?")
        values.append(params["src_ip"])
    if params.get("dest_ip"):
        clauses.append("dest_ip = ?")
        values.append(params["dest_ip"])
    if params.get("proto"):
        clauses.append("proto = ?")
        values.append(params["proto"].upper())
    if params.get("from"):
        clauses.append("timestamp >= ?")
        values.append(params["from"])
    if params.get("to"):
        clauses.append("timestamp <= ?")
        values.append(params["to"])
    if params.get("false_positive") in {"0", "1"}:
        clauses.append("false_positive = ?")
        values.append(int(params["false_positive"]))
    if params.get("ignored") in {"0", "1"}:
        clauses.append("ignored = ?")
        values.append(int(params["ignored"]))
    where = "WHERE " + " AND ".join(clauses) if clauses else ""
    return where, tuple(values)


def serialize_alert_row(row: sqlite3.Row) -> Dict[str, Any]:
    return {
        "id": row["id"],
        "timestamp": row["timestamp"],
        "signature": row["signature"],
        "category": row["category"],
        "severity": row["severity"],
        "severity_label": severity_label(row["severity"]),
        "src_ip": row["src_ip"],
        "src_port": row["src_port"],
        "dest_ip": row["dest_ip"],
        "dest_port": row["dest_port"],
        "proto": row["proto"],
        "app_proto": row["app_proto"],
        "action": row["action"],
        "false_positive": bool(row["false_positive"]),
        "ignored": bool(row["ignored"]),
        "ticket_id": row["ticket_id"],
    }


def severity_label(severity: int) -> str:
    if severity <= 1:
        return "critical"
    if severity == 2:
        return "high"
    if severity == 3:
        return "medium"
    return "low"


def coerce_int(value: Any, default: int) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def coerce_optional_int(value: Any) -> Optional[int]:
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def stable_hash(parts: Iterable[str]) -> str:
    digest = hashlib.sha256()
    for part in parts:
        digest.update(part.encode("utf-8"))
        digest.update(b"\0")
    return digest.hexdigest()


def utc_now() -> str:
    return datetime.utcnow().replace(microsecond=0).isoformat() + "Z"


def reset_db(path: str) -> None:
    db_path = Path(path).expanduser()
    if db_path.exists():
        os.remove(db_path)

#!/usr/bin/env python3
"""Local host asset and baseline audit wrapper for the osquery source tree.

Runtime data is written under /Users/zhangjiyan/Environment by default. The
source tree only stores query templates, frontend/backend wrapper code, docs,
and the .env.example file.
"""

from __future__ import annotations

import argparse
import json
import logging
import mimetypes
import os
import sqlite3
import subprocess
import sys
import uuid
from datetime import datetime, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Tuple
from urllib.parse import parse_qs, unquote, urlparse


APP_ROOT = Path(__file__).resolve().parent
REPO_ROOT = APP_ROOT.parent
STATIC_ROOT = APP_ROOT / "static"
TEMPLATE_ROOT = APP_ROOT / "templates"
BASELINE_ROOT = APP_ROOT / "baselines"

DEFAULT_ENV_ROOT = Path(os.environ.get("HOST_AUDIT_ENV_ROOT", "/Users/zhangjiyan/Environment"))
DEFAULT_ENV_NAME = os.environ.get("HOST_AUDIT_ENV_NAME", "10-osquery")
DEFAULT_ACTOR = os.environ.get("HOST_AUDIT_ACTOR", "local-admin")


def now_iso() -> str:
    return datetime.now().astimezone().isoformat(timespec="seconds")


def slug_time() -> str:
    return datetime.now().strftime("%Y%m%d-%H%M%S")


def read_json(path: Path) -> Dict[str, Any]:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def as_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, indent=2)


def safe_int(value: Any, default: int = 0) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def casefold_text(value: Any) -> str:
    return str(value or "").casefold()


def ensure_column(conn: sqlite3.Connection, table: str, column: str, definition: str) -> None:
    columns = {row["name"] for row in conn.execute(f"PRAGMA table_info({table})")}
    if column not in columns:
        conn.execute(f"ALTER TABLE {table} ADD COLUMN {column} {definition}")


class AuditPlatform:
    def __init__(self, env_root: Path = DEFAULT_ENV_ROOT, env_name: str = DEFAULT_ENV_NAME) -> None:
        self.env_root = env_root
        self.env_name = env_name
        self.db_dir = env_root / "02-databases" / env_name
        self.log_dir = env_root / "11-logs" / env_name
        self.cache_dir = env_root / "10-cache" / env_name
        self.docs_dir = env_root / "08-docs" / env_name
        self.report_dir = self.docs_dir / "reports"
        self.db_path = self.db_dir / "audit.sqlite3"
        self.log_path = self.log_dir / "audit-platform.log"

    def ensure_runtime(self) -> None:
        for path in [self.db_dir, self.log_dir, self.cache_dir, self.docs_dir, self.report_dir]:
            path.mkdir(parents=True, exist_ok=True)
        logging.basicConfig(
            filename=str(self.log_path),
            level=logging.INFO,
            format="%(asctime)s %(levelname)s %(message)s",
        )
        self.init_db()

    def connect(self) -> sqlite3.Connection:
        self.ensure_runtime_dirs_only()
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        return conn

    def ensure_runtime_dirs_only(self) -> None:
        for path in [self.db_dir, self.log_dir, self.cache_dir, self.docs_dir, self.report_dir]:
            path.mkdir(parents=True, exist_ok=True)

    def init_db(self) -> None:
        with self.connect() as conn:
            conn.executescript(
                """
                CREATE TABLE IF NOT EXISTS runs (
                    id TEXT PRIMARY KEY,
                    task_id TEXT,
                    template_id TEXT NOT NULL,
                    mode TEXT NOT NULL,
                    source TEXT NOT NULL,
                    status TEXT NOT NULL,
                    started_at TEXT NOT NULL,
                    finished_at TEXT,
                    host_count INTEGER NOT NULL DEFAULT 0,
                    finding_count INTEGER NOT NULL DEFAULT 0,
                    report_path TEXT,
                    error TEXT
                );

                CREATE TABLE IF NOT EXISTS hosts (
                    id TEXT PRIMARY KEY,
                    run_id TEXT NOT NULL,
                    hostname TEXT NOT NULL,
                    ip TEXT,
                    os_name TEXT,
                    os_version TEXT,
                    platform TEXT,
                    department TEXT,
                    owner TEXT,
                    asset_type TEXT,
                    last_seen TEXT,
                    status TEXT,
                    risk_score INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY (run_id) REFERENCES runs(id)
                );

                CREATE TABLE IF NOT EXISTS asset_sections (
                    run_id TEXT NOT NULL,
                    host_id TEXT NOT NULL,
                    section TEXT NOT NULL,
                    payload TEXT NOT NULL,
                    PRIMARY KEY (run_id, host_id, section)
                );

                CREATE TABLE IF NOT EXISTS findings (
                    id TEXT PRIMARY KEY,
                    run_id TEXT NOT NULL,
                    host_id TEXT NOT NULL,
                    policy_id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    severity TEXT NOT NULL,
                    category TEXT NOT NULL,
                    status TEXT NOT NULL,
                    evidence TEXT NOT NULL,
                    recommendation TEXT NOT NULL
                );

                CREATE TABLE IF NOT EXISTS audit_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ts TEXT NOT NULL,
                    actor TEXT NOT NULL,
                    action TEXT NOT NULL,
                    detail TEXT NOT NULL
                );

                CREATE TABLE IF NOT EXISTS tasks (
                    id TEXT PRIMARY KEY,
                    target TEXT NOT NULL,
                    query TEXT NOT NULL,
                    schedule TEXT NOT NULL,
                    status TEXT NOT NULL,
                    started_at TEXT NOT NULL,
                    finished_at TEXT,
                    result_count INTEGER NOT NULL DEFAULT 0,
                    error TEXT,
                    run_id TEXT,
                    actor TEXT NOT NULL
                );

                CREATE TABLE IF NOT EXISTS task_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    task_id TEXT,
                    run_id TEXT,
                    ts TEXT NOT NULL,
                    level TEXT NOT NULL,
                    message TEXT NOT NULL,
                    detail TEXT NOT NULL
                );

                CREATE TABLE IF NOT EXISTS schedules (
                    id TEXT PRIMARY KEY,
                    template_id TEXT NOT NULL,
                    target TEXT NOT NULL,
                    schedule TEXT NOT NULL,
                    status TEXT NOT NULL,
                    next_run_at TEXT NOT NULL,
                    retry_limit INTEGER NOT NULL DEFAULT 2,
                    retry_count INTEGER NOT NULL DEFAULT 0,
                    last_run_id TEXT,
                    last_error TEXT,
                    created_by TEXT NOT NULL,
                    created_at TEXT NOT NULL
                );
                """
            )
            ensure_column(conn, "runs", "task_id", "TEXT")

    def log_action(self, actor: str, action: str, detail: Dict[str, Any]) -> None:
        with self.connect() as conn:
            conn.execute(
                "INSERT INTO audit_logs(ts, actor, action, detail) VALUES (?, ?, ?, ?)",
                (now_iso(), actor, action, as_json(detail)),
            )
        logging.info("%s %s %s", actor, action, json.dumps(detail, ensure_ascii=False))

    def create_task(self, actor: str, target: str, query: str, schedule: str = "manual") -> str:
        task_id = str(uuid.uuid4())
        with self.connect() as conn:
            conn.execute(
                """
                INSERT INTO tasks(id, target, query, schedule, status, started_at, actor)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                (task_id, target, query, schedule, "running", now_iso(), actor),
            )
        self.log_task(task_id, None, "info", "task_started", {"target": target, "query": query, "schedule": schedule})
        return task_id

    def complete_task(self, task_id: str, status: str, result_count: int, error: Optional[str], run_id: Optional[str]) -> None:
        with self.connect() as conn:
            conn.execute(
                """
                UPDATE tasks
                SET status = ?, finished_at = ?, result_count = ?, error = ?, run_id = ?
                WHERE id = ?
                """,
                (status, now_iso(), result_count, error, run_id, task_id),
            )
        self.log_task(task_id, run_id, "error" if error else "info", "task_" + status, {"result_count": result_count, "error": error})

    def log_task(self, task_id: Optional[str], run_id: Optional[str], level: str, message: str, detail: Dict[str, Any]) -> None:
        with self.connect() as conn:
            conn.execute(
                """
                INSERT INTO task_logs(task_id, run_id, ts, level, message, detail)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                (task_id, run_id, now_iso(), level, message, as_json(detail)),
            )

    def list_tasks(self, limit: int = 100) -> List[Dict[str, Any]]:
        with self.connect() as conn:
            rows = conn.execute("SELECT * FROM tasks ORDER BY started_at DESC LIMIT ?", (limit,)).fetchall()
            return [dict(row) for row in rows]

    def list_task_logs(self, limit: int = 100) -> List[Dict[str, Any]]:
        with self.connect() as conn:
            rows = conn.execute("SELECT * FROM task_logs ORDER BY id DESC LIMIT ?", (limit,)).fetchall()
            return [dict(row) for row in rows]

    def add_schedule(self, template_id: str, target: str, schedule: str, actor: str = DEFAULT_ACTOR) -> str:
        if schedule not in ("daily", "weekly"):
            raise ValueError("schedule must be daily or weekly")
        self.load_template(template_id)
        schedule_id = str(uuid.uuid4())
        with self.connect() as conn:
            conn.execute(
                """
                INSERT INTO schedules(
                    id, template_id, target, schedule, status, next_run_at,
                    retry_limit, retry_count, created_by, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (schedule_id, template_id, target, schedule, "enabled", now_iso(), 2, 0, actor, now_iso()),
            )
        self.log_action(actor, "schedule_add", {"schedule_id": schedule_id, "template_id": template_id, "target": target, "schedule": schedule})
        return schedule_id

    def list_schedules(self) -> List[Dict[str, Any]]:
        with self.connect() as conn:
            rows = conn.execute("SELECT * FROM schedules ORDER BY created_at DESC").fetchall()
            return [dict(row) for row in rows]

    def run_schedules(self, force: bool = False, actor: str = DEFAULT_ACTOR) -> List[Dict[str, Any]]:
        self.ensure_runtime()
        current = now_iso()
        with self.connect() as conn:
            if force:
                rows = conn.execute("SELECT * FROM schedules WHERE status = 'enabled'").fetchall()
            else:
                rows = conn.execute("SELECT * FROM schedules WHERE status = 'enabled' AND next_run_at <= ?", (current,)).fetchall()
        results = []
        for row in rows:
            schedule = dict(row)
            try:
                if schedule["target"] != "demo":
                    raise ValueError("only demo target is executable without an explicit authorized import/execution adapter")
                run_id = self.create_demo_run(actor=actor, schedule=schedule["schedule"], source=f"schedule:{schedule['id']}")
                next_run = next_schedule_time(schedule["schedule"])
                with self.connect() as conn:
                    conn.execute(
                        """
                        UPDATE schedules
                        SET next_run_at = ?, retry_count = 0, last_run_id = ?, last_error = NULL
                        WHERE id = ?
                        """,
                        (next_run, run_id, schedule["id"]),
                    )
                results.append({"schedule_id": schedule["id"], "status": "completed", "run_id": run_id})
            except Exception as exc:
                retry_count = safe_int(schedule.get("retry_count")) + 1
                status = "enabled" if retry_count <= safe_int(schedule.get("retry_limit"), 2) else "failed"
                next_run = (datetime.now().astimezone() + timedelta(minutes=15)).isoformat(timespec="seconds")
                with self.connect() as conn:
                    conn.execute(
                        """
                        UPDATE schedules
                        SET status = ?, next_run_at = ?, retry_count = ?, last_error = ?
                        WHERE id = ?
                        """,
                        (status, next_run, retry_count, str(exc), schedule["id"]),
                    )
                self.log_task(None, None, "error", "schedule_failed", {"schedule_id": schedule["id"], "error": str(exc), "retry_count": retry_count})
                results.append({"schedule_id": schedule["id"], "status": status, "error": str(exc), "retry_count": retry_count})
        self.log_action(actor, "schedule_run", {"force": force, "result_count": len(results)})
        return results

    def load_templates(self) -> List[Dict[str, Any]]:
        templates = []
        for path in sorted(TEMPLATE_ROOT.glob("*.json")):
            template = read_json(path)
            template["path"] = str(path)
            templates.append(template)
        return templates

    def load_template(self, template_id: str) -> Dict[str, Any]:
        for template in self.load_templates():
            if template.get("id") == template_id:
                return template
        raise ValueError(f"unknown template: {template_id}")

    def load_baseline(self) -> Dict[str, Any]:
        return read_json(BASELINE_ROOT / "default_baseline.json")

    def create_demo_run(self, actor: str = DEFAULT_ACTOR, schedule: str = "manual", source: str = "built-in-demo") -> str:
        data = {"hosts": build_demo_hosts()}
        run_id = self.create_run("host_asset_core", "demo", source, data, actor, schedule=schedule)
        self.log_action(actor, "demo_run", {"run_id": run_id})
        return run_id

    def import_run(self, payload: Dict[str, Any], source: str = "json-import", actor: str = DEFAULT_ACTOR) -> str:
        run_id = self.create_run(
            payload.get("template_id", "host_asset_core"),
            "import",
            source,
            normalize_import_payload(payload),
            actor,
        )
        self.log_action(actor, "import_run", {"run_id": run_id, "source": source})
        return run_id

    def execute_osqueryi(self, template_id: str, authorized_host: str, actor: str = DEFAULT_ACTOR) -> str:
        if not authorized_host:
            raise ValueError("--authorized-host is required before executing osqueryi")
        template = self.load_template(template_id)
        sections: Dict[str, Any] = {}
        for section, query in template.get("queries", {}).items():
            completed = subprocess.run(
                ["osqueryi", "--json", query],
                check=True,
                capture_output=True,
                text=True,
                timeout=30,
            )
            sections[section] = json.loads(completed.stdout or "[]")
        host = host_from_sections(sections)
        host["hostname"] = host.get("hostname") or authorized_host
        host["department"] = host.get("department") or "授权主机"
        run_id = self.create_run(template_id, "authorized-osqueryi", authorized_host, {"hosts": [{"host": host, "sections": sections}]}, actor)
        self.log_action(actor, "execute_osqueryi", {"run_id": run_id, "authorized_host": authorized_host})
        return run_id

    def create_run(self, template_id: str, mode: str, source: str, data: Dict[str, Any], actor: str, schedule: str = "manual") -> str:
        self.ensure_runtime()
        run_id = str(uuid.uuid4())
        started_at = now_iso()
        template = self.load_template(template_id)
        task_id = self.create_task(actor, source, describe_template_query(template), schedule)
        baseline = self.load_baseline()
        hosts = data.get("hosts", [])
        try:
            with self.connect() as conn:
                conn.execute(
                    """
                    INSERT INTO runs(id, task_id, template_id, mode, source, status, started_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    (run_id, task_id, template_id, mode, source, "running", started_at),
                )
                finding_count = 0
                for entry in hosts:
                    host, sections = normalize_host_entry(entry)
                    source_host_id = host.get("id") or host.get("hostname") or str(uuid.uuid4())
                    host_id = f"{run_id}:{source_host_id}"
                    findings = evaluate_host(host, sections, baseline)
                    risk_score = calculate_risk_score(findings)
                    host["risk_score"] = risk_score
                    conn.execute(
                        """
                        INSERT INTO hosts(
                            id, run_id, hostname, ip, os_name, os_version, platform, department,
                            owner, asset_type, last_seen, status, risk_score
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        (
                            host_id,
                            run_id,
                            host.get("hostname") or "unknown-host",
                            host.get("ip") or "",
                            host.get("os_name") or "",
                            host.get("os_version") or "",
                            host.get("platform") or "",
                            host.get("department") or "",
                            host.get("owner") or "",
                            host.get("asset_type") or "终端",
                            host.get("last_seen") or now_iso(),
                            host.get("status") or "online",
                            risk_score,
                        ),
                    )
                    for section, payload in sections.items():
                        conn.execute(
                            """
                            INSERT OR REPLACE INTO asset_sections(run_id, host_id, section, payload)
                            VALUES (?, ?, ?, ?)
                            """,
                            (run_id, host_id, section, as_json(payload)),
                        )
                    for finding in findings:
                        finding_count += 1
                        conn.execute(
                            """
                            INSERT INTO findings(
                                id, run_id, host_id, policy_id, title, severity, category, status,
                                evidence, recommendation
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                            (
                                str(uuid.uuid4()),
                                run_id,
                                host_id,
                                finding["policy_id"],
                                finding["title"],
                                finding["severity"],
                                finding["category"],
                                finding["status"],
                                finding["evidence"],
                                finding["recommendation"],
                            ),
                        )
                result_count = count_result_rows(hosts)
                conn.execute(
                    """
                    UPDATE runs
                    SET status = ?, finished_at = ?, host_count = ?, finding_count = ?
                    WHERE id = ?
                    """,
                    ("completed", now_iso(), len(hosts), finding_count, run_id),
                )
            self.complete_task(task_id, "completed", result_count, None, run_id)
        except Exception as exc:
            with self.connect() as conn:
                conn.execute(
                    """
                    UPDATE runs
                    SET status = ?, finished_at = ?, error = ?
                    WHERE id = ?
                    """,
                    ("failed", now_iso(), str(exc), run_id),
                    )
            self.complete_task(task_id, "failed", 0, str(exc), run_id)
            raise
        self.log_action(actor, "create_run", {"run_id": run_id, "mode": mode, "hosts": len(hosts)})
        return run_id

    def latest_run_id(self) -> Optional[str]:
        with self.connect() as conn:
            row = conn.execute("SELECT id FROM runs ORDER BY started_at DESC LIMIT 1").fetchone()
            return row["id"] if row else None

    def previous_run_id(self, run_id: str) -> Optional[str]:
        with self.connect() as conn:
            current = conn.execute("SELECT started_at FROM runs WHERE id = ?", (run_id,)).fetchone()
            if not current:
                return None
            row = conn.execute(
                """
                SELECT id FROM runs
                WHERE started_at < ?
                ORDER BY started_at DESC
                LIMIT 1
                """,
                (current["started_at"],),
            ).fetchone()
            return row["id"] if row else None

    def list_runs(self) -> List[Dict[str, Any]]:
        with self.connect() as conn:
            rows = conn.execute("SELECT * FROM runs ORDER BY started_at DESC").fetchall()
            return [dict(row) for row in rows]

    def get_run(self, run_id: Optional[str] = None) -> Optional[Dict[str, Any]]:
        resolved = run_id or self.latest_run_id()
        if not resolved:
            return None
        with self.connect() as conn:
            row = conn.execute("SELECT * FROM runs WHERE id = ?", (resolved,)).fetchone()
            return dict(row) if row else None

    def list_hosts(self, run_id: Optional[str] = None) -> List[Dict[str, Any]]:
        resolved = run_id or self.latest_run_id()
        if not resolved:
            return []
        with self.connect() as conn:
            rows = conn.execute(
                "SELECT * FROM hosts WHERE run_id = ? ORDER BY risk_score DESC, hostname ASC",
                (resolved,),
            ).fetchall()
            hosts = []
            for row in rows:
                host = dict(row)
                section_rows = conn.execute(
                    "SELECT section, payload FROM asset_sections WHERE run_id = ? AND host_id = ?",
                    (resolved, host["id"]),
                ).fetchall()
                counts = {section["section"] + "_count": len(normalize_rows(json.loads(section["payload"]))) for section in section_rows}
                host.update(counts)
                host["last_query_at"] = host.get("last_seen")
                hosts.append(host)
            return hosts

    def get_asset_detail(self, host_id: str, run_id: Optional[str] = None) -> Optional[Dict[str, Any]]:
        resolved = run_id or self.latest_run_id()
        if not resolved:
            return None
        with self.connect() as conn:
            host = conn.execute("SELECT * FROM hosts WHERE run_id = ? AND id = ?", (resolved, host_id)).fetchone()
            if not host:
                return None
            sections = {}
            for row in conn.execute("SELECT section, payload FROM asset_sections WHERE run_id = ? AND host_id = ?", (resolved, host_id)):
                sections[row["section"]] = json.loads(row["payload"])
            findings = [
                dict(row)
                for row in conn.execute(
                    "SELECT * FROM findings WHERE run_id = ? AND host_id = ? ORDER BY status ASC, severity DESC",
                    (resolved, host_id),
                )
            ]
            return {"host": dict(host), "sections": sections, "findings": findings}

    def list_findings(self, run_id: Optional[str] = None, failed_only: bool = False) -> List[Dict[str, Any]]:
        resolved = run_id or self.latest_run_id()
        if not resolved:
            return []
        sql = """
            SELECT f.*, h.hostname, h.ip, h.department
            FROM findings f
            JOIN hosts h ON h.id = f.host_id AND h.run_id = f.run_id
            WHERE f.run_id = ?
        """
        params: List[Any] = [resolved]
        if failed_only:
            sql += " AND f.status = ?"
            params.append("failed")
        sql += " ORDER BY CASE f.severity WHEN 'critical' THEN 0 WHEN 'high' THEN 1 WHEN 'medium' THEN 2 ELSE 3 END, h.hostname"
        with self.connect() as conn:
            return [dict(row) for row in conn.execute(sql, params)]

    def department_view(self, run_id: Optional[str] = None) -> List[Dict[str, Any]]:
        hosts = self.list_hosts(run_id)
        groups: Dict[str, Dict[str, Any]] = {}
        for host in hosts:
            department = host.get("department") or "未分组"
            group = groups.setdefault(
                department,
                {"department": department, "asset_count": 0, "online_count": 0, "risk_total": 0, "high_risk_count": 0},
            )
            group["asset_count"] += 1
            group["online_count"] += 1 if host.get("status") == "online" else 0
            group["risk_total"] += safe_int(host.get("risk_score"))
            if safe_int(host.get("risk_score")) >= 70:
                group["high_risk_count"] += 1
        for group in groups.values():
            count = group["asset_count"] or 1
            group["avg_risk_score"] = round(group["risk_total"] / count)
            group["baseline_pass_rate"] = max(0, 100 - group["avg_risk_score"])
            del group["risk_total"]
        return sorted(groups.values(), key=lambda item: item["avg_risk_score"], reverse=True)

    def compare_with_previous(self, run_id: Optional[str] = None) -> Dict[str, Any]:
        current_id = run_id or self.latest_run_id()
        if not current_id:
            return {"current_run_id": None, "previous_run_id": None, "changes": []}
        previous_id = self.previous_run_id(current_id)
        if not previous_id:
            return {"current_run_id": current_id, "previous_run_id": None, "changes": []}
        current_hosts = {host["hostname"]: host for host in self.list_hosts(current_id)}
        previous_hosts = {host["hostname"]: host for host in self.list_hosts(previous_id)}
        current_sections = self.snapshot_sections(current_id)
        previous_sections = self.snapshot_sections(previous_id)
        changes: List[Dict[str, Any]] = []
        for hostname, host in current_hosts.items():
            old = previous_hosts.get(hostname)
            if not old:
                changes.append({"type": "added", "hostname": hostname, "detail": "新增资产"})
                continue
            fields = ["ip", "os_name", "os_version", "department", "status"]
            diff = {field: {"before": old.get(field), "after": host.get(field)} for field in fields if old.get(field) != host.get(field)}
            risk_delta = safe_int(host.get("risk_score")) - safe_int(old.get("risk_score"))
            if diff or risk_delta:
                changes.append({"type": "changed", "hostname": hostname, "detail": diff, "risk_delta": risk_delta})
            section_diff = diff_sections(previous_sections.get(hostname, {}), current_sections.get(hostname, {}))
            for item in section_diff:
                changes.append({"type": "section_changed", "hostname": hostname, "detail": item})
        for hostname in previous_hosts:
            if hostname not in current_hosts:
                changes.append({"type": "removed", "hostname": hostname, "detail": "资产未在本次快照出现"})
        return {"current_run_id": current_id, "previous_run_id": previous_id, "changes": changes}

    def snapshot_sections(self, run_id: str) -> Dict[str, Dict[str, List[Dict[str, Any]]]]:
        snapshot: Dict[str, Dict[str, List[Dict[str, Any]]]] = {}
        with self.connect() as conn:
            rows = conn.execute(
                """
                SELECT h.hostname, s.section, s.payload
                FROM asset_sections s
                JOIN hosts h ON h.id = s.host_id AND h.run_id = s.run_id
                WHERE s.run_id = ?
                """,
                (run_id,),
            ).fetchall()
        for row in rows:
            snapshot.setdefault(row["hostname"], {})[row["section"]] = normalize_rows(json.loads(row["payload"]))
        return snapshot

    def summary(self, run_id: Optional[str] = None) -> Dict[str, Any]:
        run = self.get_run(run_id)
        if not run:
            return {
                "run": None,
                "host_count": 0,
                "failed_count": 0,
                "pass_count": 0,
                "avg_risk_score": 0,
                "high_risk_count": 0,
            }
        hosts = self.list_hosts(run["id"])
        findings = self.list_findings(run["id"])
        failed = [item for item in findings if item["status"] == "failed"]
        pass_count = len(findings) - len(failed)
        avg_risk = round(sum(safe_int(host.get("risk_score")) for host in hosts) / (len(hosts) or 1))
        return {
            "run": run,
            "host_count": len(hosts),
            "failed_count": len(failed),
            "pass_count": pass_count,
            "finding_count": len(findings),
            "avg_risk_score": avg_risk,
            "high_risk_count": len([host for host in hosts if safe_int(host.get("risk_score")) >= 70]),
        }

    def generate_report(self, run_id: Optional[str] = None, actor: str = DEFAULT_ACTOR) -> Dict[str, Any]:
        resolved = run_id or self.latest_run_id()
        if not resolved:
            raise ValueError("no run available; run demo-run or import first")
        summary = self.summary(resolved)
        hosts = self.list_hosts(resolved)
        failed_findings = self.list_findings(resolved, failed_only=True)
        departments = self.department_view(resolved)
        changes = self.compare_with_previous(resolved)
        task = self.task_for_run(resolved)
        report_path = self.report_dir / f"audit-report-{slug_time()}-{resolved[:8]}.md"
        lines = [
            "# 终端主机资产与基线审计报告",
            "",
            f"- 报告生成时间：{now_iso()}",
            f"- 运行编号：{resolved}",
            f"- 模板：{summary['run']['template_id']}",
            f"- 模式：{summary['run']['mode']}",
            f"- 任务目标：{task.get('target', 'n/a') if task else 'n/a'}",
            f"- 查询内容：{task.get('query', 'n/a') if task else 'n/a'}",
            f"- 调度：{task.get('schedule', 'n/a') if task else 'n/a'}",
            f"- 结果数量：{task.get('result_count', 0) if task else 0}",
            f"- 资产数：{summary['host_count']}",
            f"- 失败项：{summary['failed_count']}",
            f"- 平均风险评分：{summary['avg_risk_score']}/100",
            "",
            "## 资产清单",
            "",
            "| 主机 | IP | 操作系统 | 部门 | 状态 | 风险 |",
            "| --- | --- | --- | --- | --- | --- |",
        ]
        for host in hosts:
            os_text = f"{host.get('os_name', '')} {host.get('os_version', '')}".strip()
            lines.append(
                f"| {host['hostname']} | {host.get('ip', '')} | {os_text} | {host.get('department', '')} | {host.get('status', '')} | {host.get('risk_score', 0)} |"
            )
        lines.extend(["", "## 基线失败项", ""])
        if failed_findings:
            lines.extend(["| 主机 | 策略 | 严重度 | 证据 | 整改建议 |", "| --- | --- | --- | --- | --- |"])
            for finding in failed_findings:
                lines.append(
                    f"| {finding['hostname']} | {finding['title']} | {finding['severity']} | {finding['evidence']} | {finding['recommendation']} |"
                )
        else:
            lines.append("本次审计没有发现基线失败项。")
        lines.extend(["", "## 部门资产视图", "", "| 部门 | 资产数 | 在线数 | 平均风险 | 基线合规率 |", "| --- | ---: | ---: | ---: | ---: |"])
        for group in departments:
            lines.append(
                f"| {group['department']} | {group['asset_count']} | {group['online_count']} | {group['avg_risk_score']} | {group['baseline_pass_rate']}% |"
            )
        lines.extend(["", "## 快照对比与变更发现", ""])
        if changes["previous_run_id"]:
            if changes["changes"]:
                for change in changes["changes"]:
                    risk_delta = f"，风险变化 {change.get('risk_delta')}" if "risk_delta" in change else ""
                    lines.append(f"- {change['hostname']}：{change['type']}，{change['detail']}{risk_delta}")
            else:
                lines.append("与上一快照相比未发现资产字段变化。")
        else:
            lines.append("当前为首个快照，暂无上一快照可对比。")
        lines.extend(
            [
                "",
                "## 合规边界",
                "",
                "本平台仅处理授权主机或 demo 数据。禁止用于隐私窃取、隐藏采集、绕权采集或读取个人文件内容。",
            ]
        )
        content = "\n".join(lines) + "\n"
        report_path.write_text(content, encoding="utf-8")
        with self.connect() as conn:
            conn.execute("UPDATE runs SET report_path = ? WHERE id = ?", (str(report_path), resolved))
        self.log_action(actor, "generate_report", {"run_id": resolved, "report_path": str(report_path)})
        return {"run_id": resolved, "report_path": str(report_path), "content": content}

    def task_for_run(self, run_id: str) -> Optional[Dict[str, Any]]:
        with self.connect() as conn:
            row = conn.execute("SELECT * FROM tasks WHERE run_id = ? ORDER BY started_at DESC LIMIT 1", (run_id,)).fetchone()
            return dict(row) if row else None

    def audit_logs(self, limit: int = 50) -> List[Dict[str, Any]]:
        with self.connect() as conn:
            rows = conn.execute(
                "SELECT * FROM audit_logs ORDER BY id DESC LIMIT ?",
                (limit,),
            ).fetchall()
            return [dict(row) for row in rows]


def normalize_import_payload(payload: Dict[str, Any]) -> Dict[str, Any]:
    if "hosts" in payload:
        return payload
    if "host" in payload or "sections" in payload:
        return {"hosts": [payload]}
    return {"hosts": [{"host": host_from_sections(payload), "sections": payload}]}


def describe_template_query(template: Dict[str, Any]) -> str:
    sections = ",".join(template.get("sections", []))
    return f"{template.get('id', 'unknown')}[{sections}]"


def count_result_rows(hosts: List[Dict[str, Any]]) -> int:
    total = 0
    for entry in hosts:
        sections = entry.get("sections", {}) if isinstance(entry, dict) else {}
        if not isinstance(sections, dict):
            continue
        for payload in sections.values():
            if isinstance(payload, list):
                total += len(payload)
            elif isinstance(payload, dict):
                total += 1
    return total


def next_schedule_time(schedule: str) -> str:
    delta = timedelta(days=7 if schedule == "weekly" else 1)
    return (datetime.now().astimezone() + delta).isoformat(timespec="seconds")


def normalize_host_entry(entry: Dict[str, Any]) -> Tuple[Dict[str, Any], Dict[str, Any]]:
    if "sections" in entry:
        sections = dict(entry.get("sections") or {})
        host = dict(entry.get("host") or host_from_sections(sections))
    else:
        sections = {key: value for key, value in entry.items() if key != "host"}
        host = dict(entry.get("host") or host_from_sections(sections))
    sections.setdefault("host", host)
    host.setdefault("hostname", first_value(sections.get("host"), "hostname", "computer_name", "name") or "demo-host")
    host.setdefault("ip", first_value(sections.get("interfaces"), "ip", "address") or "127.0.0.1")
    host.setdefault("os_name", first_value(sections.get("system"), "name") or first_value(sections.get("os_version"), "name") or "Unknown OS")
    host.setdefault("os_version", first_value(sections.get("system"), "version") or first_value(sections.get("os_version"), "version") or "")
    host.setdefault("platform", first_value(sections.get("system"), "platform") or "unknown")
    host.setdefault("department", "未分组")
    host.setdefault("owner", "未登记")
    host.setdefault("asset_type", "终端")
    host.setdefault("last_seen", now_iso())
    host.setdefault("status", "online")
    return host, sections


def first_value(section: Any, *fields: str) -> str:
    if isinstance(section, list):
        row = section[0] if section else {}
    elif isinstance(section, dict):
        row = section
    else:
        row = {}
    for field in fields:
        value = row.get(field)
        if value not in (None, ""):
            return str(value)
    return ""


def host_from_sections(sections: Dict[str, Any]) -> Dict[str, Any]:
    host_row = sections.get("host") or sections.get("system_info") or {}
    os_row = sections.get("system") or sections.get("os_version") or {}
    if isinstance(host_row, list):
        host_row = host_row[0] if host_row else {}
    if isinstance(os_row, list):
        os_row = os_row[0] if os_row else {}
    return {
        "hostname": host_row.get("hostname") or host_row.get("computer_name") or "authorized-host",
        "ip": host_row.get("ip") or host_row.get("primary_ip") or "",
        "os_name": os_row.get("name") or "",
        "os_version": os_row.get("version") or "",
        "platform": os_row.get("platform") or "",
        "department": host_row.get("department") or "授权主机",
        "owner": host_row.get("owner") or "未登记",
        "asset_type": host_row.get("asset_type") or "终端",
        "last_seen": now_iso(),
        "status": "online",
    }


def evaluate_host(host: Dict[str, Any], sections: Dict[str, Any], baseline: Dict[str, Any]) -> List[Dict[str, str]]:
    findings = []
    for policy in baseline.get("policies", []):
        passed, evidence = evaluate_rule(policy.get("rule", {}), host, sections)
        findings.append(
            {
                "policy_id": policy["id"],
                "title": policy["title"],
                "severity": policy.get("severity", "low"),
                "category": policy.get("category", "通用"),
                "status": "passed" if passed else "failed",
                "evidence": evidence,
                "recommendation": policy.get("recommendation", ""),
            }
        )
    return findings


def evaluate_rule(rule: Dict[str, Any], host: Dict[str, Any], sections: Dict[str, Any]) -> Tuple[bool, str]:
    rule_type = rule.get("type")
    if rule_type == "host_fields_present":
        missing = [field for field in rule.get("fields", []) if not host.get(field)]
        if missing:
            return False, "缺少字段：" + ", ".join(missing)
        return True, "主机身份字段完整"
    rows = normalize_rows(sections.get(rule.get("section", "")))
    field = rule.get("field")
    if rule_type == "count_lte":
        count = len([row for row in rows if casefold_text(row.get(field)) == casefold_text(rule.get("equals"))])
        max_count = safe_int(rule.get("max"))
        return count <= max_count, f"匹配数量 {count}，阈值 <= {max_count}"
    if rule_type == "no_rows_match":
        blocked = set(casefold_text(item) for item in rule.get("in", []))
        matches = [row for row in rows if casefold_text(row.get(field)) in blocked]
        if matches:
            values = sorted(set(str(row.get(field)) for row in matches))
            return False, "发现命中项：" + ", ".join(values)
        return True, "未发现禁止项"
    if rule_type == "no_rows_match_all":
        expected = {
            key: set(casefold_text(item) for item in values)
            for key, values in (rule.get("match") or {}).items()
        }
        matches = [
            row
            for row in rows
            if all(casefold_text(row.get(key)) in allowed for key, allowed in expected.items())
        ]
        if matches:
            values = [", ".join(f"{key}={row.get(key)}" for key in expected) for row in matches]
            return False, "发现命中配置：" + "; ".join(values)
        return True, "未发现命中配置"
    if rule_type == "any_contains":
        needle = casefold_text(rule.get("contains"))
        matches = [row for row in rows if needle in casefold_text(row.get(field))]
        return bool(matches), "命中 " + str(len(matches)) + " 项" if matches else "未发现要求项"
    if rule_type == "any_contains_any":
        needles = [casefold_text(item) for item in rule.get("contains_any", [])]
        matches = [row for row in rows if any(needle in casefold_text(row.get(field)) for needle in needles)]
        return bool(matches), "命中 " + str(len(matches)) + " 项" if matches else "未发现要求项"
    return True, "规则类型未启用，默认通过"


def normalize_rows(value: Any) -> List[Dict[str, Any]]:
    if isinstance(value, list):
        return [row for row in value if isinstance(row, dict)]
    if isinstance(value, dict):
        return [value]
    return []


def calculate_risk_score(findings: Iterable[Dict[str, str]]) -> int:
    weights = {"critical": 35, "high": 25, "medium": 12, "low": 6}
    score = 10
    for finding in findings:
        if finding.get("status") == "failed":
            score += weights.get(finding.get("severity", "low"), 6)
    return min(score, 100)


def diff_sections(old_sections: Dict[str, List[Dict[str, Any]]], new_sections: Dict[str, List[Dict[str, Any]]]) -> List[Dict[str, Any]]:
    changes = []
    for section in sorted(set(old_sections) | set(new_sections)):
        old_rows = {row_identity(row): row for row in old_sections.get(section, [])}
        new_rows = {row_identity(row): row for row in new_sections.get(section, [])}
        added = sorted(set(new_rows) - set(old_rows))
        removed = sorted(set(old_rows) - set(new_rows))
        changed = [key for key in sorted(set(old_rows) & set(new_rows)) if stable_row(old_rows[key]) != stable_row(new_rows[key])]
        if added or removed or changed:
            changes.append(
                {
                    "section": section,
                    "added": len(added),
                    "removed": len(removed),
                    "changed": len(changed),
                    "sample": (added + removed + changed)[:3],
                }
            )
    return changes


def row_identity(row: Dict[str, Any]) -> str:
    for field in ["hostname", "username", "name", "path", "port", "pid", "option", "sha256", "risk"]:
        if row.get(field) not in (None, ""):
            return f"{field}:{row.get(field)}"
    return json.dumps(stable_row(row), ensure_ascii=False, sort_keys=True)


def stable_row(row: Dict[str, Any]) -> Dict[str, Any]:
    volatile_fields = {"last_seen", "last_query_at", "risk_score"}
    return {key: row[key] for key in sorted(row) if key not in volatile_fields}


def build_demo_hosts() -> List[Dict[str, Any]]:
    seen = now_iso()
    return [
        {
            "host": {
                "id": "demo-rd-mac-01",
                "hostname": "RD-MAC-01",
                "ip": "10.10.2.21",
                "os_name": "macOS",
                "os_version": "14.5",
                "platform": "darwin",
                "department": "研发中心",
                "owner": "zhangjiyan",
                "asset_type": "研发终端",
                "last_seen": seen,
                "status": "online",
            },
            "sections": {
                "system": [{"name": "macOS", "version": "14.5", "platform": "darwin", "build": "23F79"}],
                "users": [
                    {"username": "zhangjiyan", "uid": "501", "type": "admin", "shell": "/bin/zsh"},
                    {"username": "build", "uid": "502", "type": "standard", "shell": "/bin/zsh"},
                ],
                "processes": [
                    {"pid": "1", "name": "launchd", "path": "/sbin/launchd", "state": "running"},
                    {"pid": "441", "name": "osqueryd", "path": "/usr/local/bin/osqueryd", "state": "running"},
                    {"pid": "510", "name": "sshd", "path": "/usr/sbin/sshd", "state": "sleeping"},
                ],
                "ports": [
                    {"pid": "510", "port": "22", "protocol": "6", "address": "10.10.2.21"},
                    {"pid": "441", "port": "0", "protocol": "0", "address": "localhost"},
                ],
                "software": [
                    {"name": "osquery", "version": "5.12.2", "source": "pkg"},
                    {"name": "Sentinel EDR", "version": "23.4", "source": "mdm"},
                    {"name": "Docker Desktop", "version": "4.31", "source": "app"},
                ],
                "scheduled_tasks": [
                    {"event": "daily", "command": "/usr/local/bin/osqueryi --version", "path": "/etc/periodic/daily/osquery-check"}
                ],
                "startup_items": [
                    {"name": "com.osquery.agent", "path": "/Library/LaunchDaemons/com.osquery.agent.plist", "type": "launchd", "status": "enabled"}
                ],
                "kernel_modules": [
                    {"name": "com.apple.filesystems.apfs", "path": "/System/Library/Extensions/apfs.kext", "size": "0", "status": "loaded"}
                ],
                "file_hashes": [
                    {"path": "/etc/hosts", "sha256": "demo-rd-hosts-sha256"},
                    {"path": "/etc/ssh/sshd_config", "sha256": "demo-rd-sshd-sha256"}
                ],
                "ssh_config": [
                    {"option": "PasswordAuthentication", "value": "no"},
                    {"option": "PermitRootLogin", "value": "no"}
                ],
                "password_risks": [],
                "sensitive_permissions": [],
            },
        },
        {
            "host": {
                "id": "demo-fin-win-02",
                "hostname": "FIN-WIN-02",
                "ip": "10.10.8.34",
                "os_name": "Windows 11",
                "os_version": "23H2",
                "platform": "windows",
                "department": "财务部",
                "owner": "finance-shared",
                "asset_type": "办公终端",
                "last_seen": seen,
                "status": "online",
            },
            "sections": {
                "system": [{"name": "Windows 11", "version": "23H2", "platform": "windows", "build": "22631"}],
                "users": [
                    {"username": "finance01", "uid": "1001", "type": "admin", "shell": "powershell"},
                    {"username": "auditor", "uid": "1002", "type": "admin", "shell": "powershell"},
                    {"username": "guest", "uid": "501", "type": "standard", "shell": "cmd"},
                ],
                "processes": [
                    {"pid": "804", "name": "MsMpEng.exe", "path": "C:\\ProgramData\\Microsoft\\Windows Defender", "state": "running"},
                    {"pid": "1108", "name": "svchost.exe", "path": "C:\\Windows\\System32\\svchost.exe", "state": "running"},
                ],
                "ports": [
                    {"pid": "1108", "port": "3389", "protocol": "6", "address": "10.10.8.34"},
                ],
                "software": [
                    {"name": "osquery", "version": "5.12.2", "source": "msi"},
                    {"name": "Microsoft Defender", "version": "4.18", "source": "windows"},
                    {"name": "Office", "version": "2021", "source": "msi"},
                ],
                "scheduled_tasks": [
                    {"event": "daily", "command": "Windows Defender Scheduled Scan", "path": "\\Microsoft\\Windows\\Windows Defender"}
                ],
                "startup_items": [
                    {"name": "SecurityHealth", "path": "C:\\Windows\\System32\\SecurityHealthSystray.exe", "type": "registry", "status": "enabled"}
                ],
                "kernel_modules": [
                    {"name": "WdFilter", "path": "C:\\Windows\\System32\\drivers\\WdFilter.sys", "size": "0", "status": "loaded"}
                ],
                "file_hashes": [
                    {"path": "C:\\Windows\\System32\\drivers\\etc\\hosts", "sha256": "demo-fin-hosts-sha256"}
                ],
                "ssh_config": [],
                "password_risks": [
                    {"username": "guest", "risk": "weak_password", "evidence": "demo 弱口令线索"}
                ],
                "sensitive_permissions": [],
            },
        },
        {
            "host": {
                "id": "demo-ops-linux-03",
                "hostname": "OPS-LINUX-03",
                "ip": "10.10.12.17",
                "os_name": "Ubuntu Server",
                "os_version": "22.04",
                "platform": "linux",
                "department": "运维中心",
                "owner": "ops",
                "asset_type": "服务器",
                "last_seen": seen,
                "status": "offline",
            },
            "sections": {
                "system": [{"name": "Ubuntu Server", "version": "22.04", "platform": "linux", "build": "jammy"}],
                "users": [
                    {"username": "root", "uid": "0", "type": "admin", "shell": "/bin/bash"},
                    {"username": "ops", "uid": "1000", "type": "admin", "shell": "/bin/bash"},
                    {"username": "deploy", "uid": "1001", "type": "admin", "shell": "/bin/bash"},
                ],
                "processes": [
                    {"pid": "1", "name": "systemd", "path": "/usr/lib/systemd/systemd", "state": "running"},
                    {"pid": "733", "name": "nginx", "path": "/usr/sbin/nginx", "state": "running"},
                    {"pid": "901", "name": "telnetd", "path": "/usr/sbin/in.telnetd", "state": "sleeping"},
                ],
                "ports": [
                    {"pid": "733", "port": "80", "protocol": "6", "address": "10.10.12.17"},
                    {"pid": "733", "port": "443", "protocol": "6", "address": "10.10.12.17"},
                    {"pid": "901", "port": "23", "protocol": "6", "address": "10.10.12.17"},
                ],
                "software": [
                    {"name": "osquery", "version": "5.12.2", "source": "deb"},
                    {"name": "nginx", "version": "1.24", "source": "apt"},
                ],
                "scheduled_tasks": [
                    {"event": "*/5 * * * *", "command": "/usr/local/bin/backup.sh", "path": "/var/spool/cron/crontabs/root"}
                ],
                "startup_items": [
                    {"name": "nginx.service", "path": "/lib/systemd/system/nginx.service", "type": "systemd", "status": "enabled"}
                ],
                "kernel_modules": [
                    {"name": "overlay", "path": "/lib/modules/overlay.ko", "size": "0", "status": "loaded"}
                ],
                "file_hashes": [
                    {"path": "/etc/hosts", "sha256": "demo-ops-hosts-sha256"},
                    {"path": "/etc/ssh/sshd_config", "sha256": "demo-ops-sshd-sha256"}
                ],
                "ssh_config": [
                    {"option": "PasswordAuthentication", "value": "yes"},
                    {"option": "PermitRootLogin", "value": "prohibit-password"}
                ],
                "password_risks": [],
                "sensitive_permissions": [
                    {"path": "/usr/local/bin/backup.sh", "mode": "4755", "risk": "suid_unreviewed"}
                ],
            },
        },
    ]


class AuditHttpHandler(BaseHTTPRequestHandler):
    platform: AuditPlatform

    def log_message(self, fmt: str, *args: Any) -> None:
        logging.info("http " + fmt, *args)

    def do_GET(self) -> None:
        try:
            parsed = urlparse(self.path)
            if parsed.path == "/":
                self.send_static("index.html")
                return
            if parsed.path.startswith("/static/"):
                self.send_static(parsed.path.removeprefix("/static/"))
                return
            if parsed.path.startswith("/api/"):
                self.handle_api_get(parsed.path, parse_qs(parsed.query))
                return
            self.send_error(404, "Not found")
        except Exception as exc:
            self.send_json({"error": str(exc)}, status=500)

    def do_POST(self) -> None:
        try:
            parsed = urlparse(self.path)
            length = int(self.headers.get("Content-Length", "0") or "0")
            body = self.rfile.read(length).decode("utf-8") if length else "{}"
            payload = json.loads(body or "{}")
            if parsed.path == "/api/runs/demo":
                run_id = self.platform.create_demo_run(payload.get("actor", DEFAULT_ACTOR))
                self.send_json({"run_id": run_id, "summary": self.platform.summary(run_id)})
                return
            if parsed.path == "/api/runs/import":
                run_id = self.platform.import_run(payload, payload.get("source", "browser-import"), payload.get("actor", DEFAULT_ACTOR))
                self.send_json({"run_id": run_id, "summary": self.platform.summary(run_id)})
                return
            if parsed.path == "/api/reports":
                report = self.platform.generate_report(payload.get("run_id"), payload.get("actor", DEFAULT_ACTOR))
                self.send_json(report)
                return
            if parsed.path == "/api/schedules":
                schedule_id = self.platform.add_schedule(
                    payload.get("template_id", "host_asset_core"),
                    payload.get("target", "demo"),
                    payload.get("schedule", "daily"),
                    payload.get("actor", DEFAULT_ACTOR),
                )
                self.send_json({"schedule_id": schedule_id, "schedules": self.platform.list_schedules()})
                return
            if parsed.path == "/api/schedules/run":
                results = self.platform.run_schedules(bool(payload.get("force")), payload.get("actor", DEFAULT_ACTOR))
                self.send_json({"results": results, "schedules": self.platform.list_schedules()})
                return
            self.send_error(404, "Not found")
        except Exception as exc:
            self.send_json({"error": str(exc)}, status=500)

    def handle_api_get(self, path: str, query: Dict[str, List[str]]) -> None:
        run_id = first_query(query, "run_id")
        if path == "/api/health":
            self.send_json({"status": "ok", "db_path": str(self.platform.db_path), "docs_dir": str(self.platform.docs_dir)})
            return
        if path == "/api/templates":
            self.send_json({"templates": self.platform.load_templates()})
            return
        if path == "/api/runs":
            self.send_json({"runs": self.platform.list_runs()})
            return
        if path == "/api/tasks":
            self.send_json({"tasks": self.platform.list_tasks()})
            return
        if path == "/api/task-logs":
            self.send_json({"logs": self.platform.list_task_logs()})
            return
        if path == "/api/schedules":
            self.send_json({"schedules": self.platform.list_schedules()})
            return
        if path == "/api/summary":
            self.send_json(self.platform.summary(run_id))
            return
        if path == "/api/assets":
            self.send_json({"hosts": self.platform.list_hosts(run_id)})
            return
        if path.startswith("/api/assets/"):
            host_id = unquote(path.removeprefix("/api/assets/"))
            detail = self.platform.get_asset_detail(host_id, run_id)
            if not detail:
                self.send_error(404, "Asset not found")
                return
            self.send_json(detail)
            return
        if path == "/api/baselines":
            self.send_json({"findings": self.platform.list_findings(run_id)})
            return
        if path == "/api/failures":
            self.send_json({"findings": self.platform.list_findings(run_id, failed_only=True)})
            return
        if path == "/api/departments":
            self.send_json({"departments": self.platform.department_view(run_id)})
            return
        if path == "/api/changes":
            self.send_json(self.platform.compare_with_previous(run_id))
            return
        if path == "/api/audit-logs":
            self.send_json({"logs": self.platform.audit_logs()})
            return
        self.send_error(404, "Not found")

    def send_static(self, relative: str) -> None:
        target = (STATIC_ROOT / relative).resolve()
        if not str(target).startswith(str(STATIC_ROOT.resolve())) or not target.exists():
            self.send_error(404, "Static file not found")
            return
        content_type = mimetypes.guess_type(str(target))[0] or "application/octet-stream"
        content = target.read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(content)))
        self.end_headers()
        self.wfile.write(content)

    def send_json(self, payload: Dict[str, Any], status: int = 200) -> None:
        content = json.dumps(payload, ensure_ascii=False, indent=2).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(content)))
        self.end_headers()
        self.wfile.write(content)


def first_query(query: Dict[str, List[str]], key: str) -> Optional[str]:
    values = query.get(key)
    return values[0] if values else None


def serve(platform: AuditPlatform, bind: str, port: int) -> None:
    platform.ensure_runtime()
    AuditHttpHandler.platform = platform
    server = create_http_server(bind, port)
    actual_port = server.server_address[1]
    if actual_port != port:
        print(f"Requested port {port} is unavailable; using {actual_port}.", flush=True)
    print(f"Host audit platform: http://{bind}:{actual_port}", flush=True)
    print(f"Runtime database: {platform.db_path}", flush=True)
    server.serve_forever()


def create_http_server(bind: str, port: int) -> ThreadingHTTPServer:
    candidates = [0] if port == 0 else list(range(port, port + 21))
    last_error: Optional[OSError] = None
    for candidate in candidates:
        try:
            return ThreadingHTTPServer((bind, candidate), AuditHttpHandler)
        except OSError as exc:
            last_error = exc
            if exc.errno != 48:
                raise
    raise OSError(f"no available port found from {port} to {port + 20}") from last_error


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Host asset and baseline audit platform wrapper")
    parser.add_argument("--env-root", default=str(DEFAULT_ENV_ROOT), help="Environment root for runtime data")
    parser.add_argument("--env-name", default=DEFAULT_ENV_NAME, help="Environment subdirectory name")
    subparsers = parser.add_subparsers(dest="command", required=True)

    subparsers.add_parser("init", help="Create Environment directories and SQLite schema")
    subparsers.add_parser("demo-run", help="Create a demo audit run")

    import_parser = subparsers.add_parser("import", help="Import JSON osquery/demo results")
    import_parser.add_argument("--file", required=True, help="JSON file to import")
    import_parser.add_argument("--source", default="json-file", help="Source label")

    execute_parser = subparsers.add_parser("execute", help="Execute osqueryi against an explicitly authorized local host")
    execute_parser.add_argument("--template", default="host_asset_core", help="Template id")
    execute_parser.add_argument("--authorized-host", required=True, help="Authorization label/hostname")

    report_parser = subparsers.add_parser("report", help="Generate markdown audit report")
    report_parser.add_argument("--run-id", help="Run id, defaults to latest")

    summary_parser = subparsers.add_parser("summary", help="Print latest or selected run summary")
    summary_parser.add_argument("--run-id", help="Run id, defaults to latest")

    subparsers.add_parser("tasks", help="List recent query tasks")

    schedule_add_parser = subparsers.add_parser("schedule-add", help="Create a daily or weekly demo inspection schedule")
    schedule_add_parser.add_argument("--template", default="host_asset_core", help="Template id")
    schedule_add_parser.add_argument("--target", default="demo", help="Target label; demo is executable locally")
    schedule_add_parser.add_argument("--schedule", choices=["daily", "weekly"], default="daily")

    schedule_run_parser = subparsers.add_parser("schedule-run", help="Run due schedules or force all enabled schedules")
    schedule_run_parser.add_argument("--force", action="store_true", help="Run all enabled schedules immediately")

    serve_parser = subparsers.add_parser("serve", help="Start local web UI and API")
    serve_parser.add_argument("--bind", default=os.environ.get("HOST_AUDIT_BIND", "127.0.0.1"))
    serve_parser.add_argument("--port", type=int, default=safe_int(os.environ.get("HOST_AUDIT_PORT"), 8765), help="Port to bind; use 0 for an OS-assigned port. If occupied, the server tries the next 20 ports.")
    return parser


def main(argv: Optional[List[str]] = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    platform = AuditPlatform(Path(args.env_root), args.env_name)
    if args.command == "init":
        platform.ensure_runtime()
        print(as_json({"db_path": str(platform.db_path), "log_path": str(platform.log_path), "report_dir": str(platform.report_dir)}))
        return 0
    if args.command == "demo-run":
        run_id = platform.create_demo_run()
        print(as_json({"run_id": run_id, "summary": platform.summary(run_id)}))
        return 0
    if args.command == "import":
        payload = read_json(Path(args.file))
        run_id = platform.import_run(payload, args.source)
        print(as_json({"run_id": run_id, "summary": platform.summary(run_id)}))
        return 0
    if args.command == "execute":
        run_id = platform.execute_osqueryi(args.template, args.authorized_host)
        print(as_json({"run_id": run_id, "summary": platform.summary(run_id)}))
        return 0
    if args.command == "report":
        print(as_json(platform.generate_report(args.run_id)))
        return 0
    if args.command == "summary":
        print(as_json(platform.summary(args.run_id)))
        return 0
    if args.command == "tasks":
        print(as_json({"tasks": platform.list_tasks(), "task_logs": platform.list_task_logs()}))
        return 0
    if args.command == "schedule-add":
        schedule_id = platform.add_schedule(args.template, args.target, args.schedule)
        print(as_json({"schedule_id": schedule_id, "schedules": platform.list_schedules()}))
        return 0
    if args.command == "schedule-run":
        print(as_json({"results": platform.run_schedules(args.force), "schedules": platform.list_schedules()}))
        return 0
    if args.command == "serve":
        serve(platform, args.bind, args.port)
        return 0
    parser.print_help()
    return 1


if __name__ == "__main__":
    sys.exit(main())

from __future__ import annotations

import json
import shutil
import sqlite3
from pathlib import Path
from typing import Any

from .adapters import load_events
from .normalizer import SEVERITY_ORDER, normalize_alert, utc_now
from .paths import RuntimePaths


class SocHuntStore:
    def __init__(self, paths: RuntimePaths | None = None) -> None:
        self.paths = paths or RuntimePaths()
        self.paths.ensure()
        self.conn = sqlite3.connect(self.paths.db_path)
        self.conn.row_factory = sqlite3.Row
        self.migrate()

    def close(self) -> None:
        self.conn.close()

    def migrate(self) -> None:
        self.conn.executescript(
            """
            CREATE TABLE IF NOT EXISTS alerts (
                id TEXT PRIMARY KEY,
                source TEXT NOT NULL,
                event_type TEXT NOT NULL,
                severity TEXT NOT NULL,
                src_ip TEXT,
                dst_ip TEXT,
                asset TEXT,
                rule TEXT,
                tags TEXT,
                raw_event TEXT NOT NULL,
                status TEXT NOT NULL,
                created_at TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS cases (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                severity TEXT NOT NULL,
                status TEXT NOT NULL,
                summary TEXT,
                alert_id TEXT,
                assignee TEXT,
                reviewer TEXT,
                reviewed_at TEXT,
                closed_at TEXT,
                archived_at TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS case_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                case_id INTEGER NOT NULL,
                action TEXT NOT NULL,
                note TEXT NOT NULL,
                analyst TEXT NOT NULL,
                created_at TEXT NOT NULL,
                FOREIGN KEY(case_id) REFERENCES cases(id)
            );
            CREATE TABLE IF NOT EXISTS hunt_tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                query TEXT NOT NULL,
                status TEXT NOT NULL,
                owner TEXT NOT NULL,
                notes TEXT,
                hypothesis TEXT,
                result TEXT,
                conclusion TEXT,
                case_id INTEGER,
                created_at TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS data_source_health (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source TEXT NOT NULL,
                component TEXT NOT NULL,
                status TEXT NOT NULL,
                last_seen TEXT NOT NULL,
                detail TEXT,
                updated_at TEXT NOT NULL,
                UNIQUE(source, component)
            );
            CREATE TABLE IF NOT EXISTS audit_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                actor TEXT NOT NULL,
                action TEXT NOT NULL,
                entity_type TEXT NOT NULL,
                entity_id TEXT NOT NULL,
                detail TEXT,
                created_at TEXT NOT NULL
            );
            """
        )
        self.ensure_column("alerts", "tags", "TEXT")
        self.ensure_column("cases", "assignee", "TEXT")
        self.ensure_column("cases", "reviewer", "TEXT")
        self.ensure_column("cases", "reviewed_at", "TEXT")
        self.ensure_column("cases", "closed_at", "TEXT")
        self.ensure_column("cases", "archived_at", "TEXT")
        self.ensure_column("hunt_tasks", "hypothesis", "TEXT")
        self.ensure_column("hunt_tasks", "result", "TEXT")
        self.ensure_column("hunt_tasks", "conclusion", "TEXT")
        self.ensure_column("hunt_tasks", "case_id", "INTEGER")
        self.ensure_column("hunt_tasks", "updated_at", "TEXT")
        self.conn.commit()

    def ensure_column(self, table: str, column: str, definition: str) -> None:
        columns = {
            row["name"]
            for row in self.conn.execute(f"PRAGMA table_info({table})").fetchall()
        }
        if column not in columns:
            self.conn.execute(f"ALTER TABLE {table} ADD COLUMN {column} {definition}")

    def import_alerts(self, events: list[dict[str, Any]], actor: str = "system") -> int:
        imported = 0
        seen_sources: set[str] = set()
        for event in events:
            alert = normalize_alert(event)
            self.conn.execute(
                """
                INSERT OR REPLACE INTO alerts (
                    id, source, event_type, severity, src_ip, dst_ip, asset, rule,
                    tags, raw_event, status, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    alert["id"],
                    alert["source"],
                    alert["event_type"],
                    alert["severity"],
                    alert["src_ip"],
                    alert["dst_ip"],
                    alert["asset"],
                    alert["rule"],
                    alert["tags"],
                    alert["raw_event"],
                    alert["status"],
                    alert["created_at"],
                ),
            )
            seen_sources.add(alert["source"])
            imported += 1
        for source in seen_sources:
            self.upsert_data_source_health(
                source,
                "ingest",
                "healthy",
                utc_now(),
                f"Imported {imported} events in latest batch.",
                commit=False,
            )
        self.audit(actor, "import_alerts", "alert", "bulk", f"count={imported}")
        self.conn.commit()
        return imported

    def import_jsonl(self, path: Path, actor: str = "system") -> int:
        events = []
        with path.open("r", encoding="utf-8") as handle:
            for line in handle:
                line = line.strip()
                if line:
                    events.append(json.loads(line))
        return self.import_alerts(events, actor=actor)

    def import_event_file(self, path: Path, source: str = "auto", actor: str = "system") -> int:
        return self.import_alerts(load_events(path, source), actor=actor)

    def list_alerts(
        self,
        query: str = "",
        severity: str = "",
        source: str = "",
        asset: str = "",
        rule: str = "",
        tags: str = "",
        status: str = "",
        start: str = "",
        end: str = "",
        limit: int = 100,
        offset: int = 0,
    ) -> list[dict[str, Any]]:
        sql = "SELECT * FROM alerts WHERE 1=1"
        params: list[Any] = []
        if query:
            like = f"%{query.lower()}%"
            sql += " AND (lower(rule) LIKE ? OR lower(asset) LIKE ? OR lower(tags) LIKE ? OR src_ip LIKE ? OR dst_ip LIKE ? OR lower(raw_event) LIKE ?)"
            params.extend([like, like, like, like, like, like])
        if severity:
            sql += " AND severity = ?"
            params.append(severity)
        if source:
            sql += " AND source = ?"
            params.append(source)
        if asset:
            sql += " AND lower(asset) LIKE ?"
            params.append(f"%{asset.lower()}%")
        if rule:
            sql += " AND lower(rule) LIKE ?"
            params.append(f"%{rule.lower()}%")
        if tags:
            sql += " AND lower(tags) LIKE ?"
            params.append(f"%{tags.lower()}%")
        if status:
            sql += " AND status = ?"
            params.append(status)
        if start:
            sql += " AND created_at >= ?"
            params.append(start)
        if end:
            sql += " AND created_at <= ?"
            params.append(end)
        sql += " ORDER BY CASE severity WHEN 'critical' THEN 4 WHEN 'high' THEN 3 WHEN 'medium' THEN 2 ELSE 1 END DESC, created_at DESC LIMIT ? OFFSET ?"
        params.extend([limit, offset])
        return [dict(row) for row in self.conn.execute(sql, params).fetchall()]

    def get_alert(self, alert_id: str) -> dict[str, Any] | None:
        row = self.conn.execute("SELECT * FROM alerts WHERE id = ?", (alert_id,)).fetchone()
        return dict(row) if row else None

    def create_case(
        self,
        title: str,
        alert_id: str | None = None,
        summary: str = "",
        severity: str | None = None,
        assignee: str = "",
        actor: str = "analyst",
    ) -> int:
        alert = self.get_alert(alert_id) if alert_id else None
        chosen_severity = severity or (alert["severity"] if alert else "medium")
        now = utc_now()
        cursor = self.conn.execute(
            """
            INSERT INTO cases (title, severity, status, summary, alert_id, assignee, created_at, updated_at)
            VALUES (?, ?, 'open', ?, ?, ?, ?, ?)
            """,
            (title, chosen_severity, summary, alert_id, assignee, now, now),
        )
        case_id = int(cursor.lastrowid)
        if alert_id:
            self.conn.execute("UPDATE alerts SET status = 'in_case' WHERE id = ?", (alert_id,))
            self.add_case_record(case_id, "created_from_alert", f"Linked alert {alert_id}", actor, commit=False)
        self.audit(actor, "create_case", "case", str(case_id), title, commit=False)
        self.conn.commit()
        return case_id

    def list_cases(self) -> list[dict[str, Any]]:
        cases = []
        for row in self.conn.execute("SELECT * FROM cases ORDER BY updated_at DESC").fetchall():
            item = dict(row)
            item["records"] = self.case_records(item["id"])
            cases.append(item)
        return cases

    def get_case(self, case_id: int) -> dict[str, Any] | None:
        row = self.conn.execute("SELECT * FROM cases WHERE id = ?", (case_id,)).fetchone()
        if not row:
            return None
        item = dict(row)
        item["records"] = self.case_records(case_id)
        item["alert"] = self.get_alert(item["alert_id"]) if item.get("alert_id") else None
        return item

    def add_case_record(
        self,
        case_id: int,
        action: str,
        note: str,
        analyst: str = "analyst",
        commit: bool = True,
    ) -> int:
        now = utc_now()
        cursor = self.conn.execute(
            "INSERT INTO case_records (case_id, action, note, analyst, created_at) VALUES (?, ?, ?, ?, ?)",
            (case_id, action, note, analyst, now),
        )
        self.conn.execute("UPDATE cases SET updated_at = ? WHERE id = ?", (now, case_id))
        record_id = int(cursor.lastrowid)
        self.audit(analyst, "add_case_record", "case", str(case_id), action, commit=False)
        if commit:
            self.conn.commit()
        return record_id

    def update_case_status(self, case_id: int, status: str, actor: str = "analyst") -> None:
        now = utc_now()
        closed_at = now if status == "closed" else None
        archived_at = now if status == "archived" else None
        self.conn.execute(
            """
            UPDATE cases
            SET status = ?,
                updated_at = ?,
                closed_at = COALESCE(?, closed_at),
                archived_at = COALESCE(?, archived_at)
            WHERE id = ?
            """,
            (status, now, closed_at, archived_at, case_id),
        )
        self.add_case_record(case_id, "status_change", f"Case status changed to {status}", actor, commit=False)
        self.audit(actor, "update_case_status", "case", str(case_id), status, commit=False)
        self.conn.commit()

    def assign_case(self, case_id: int, assignee: str, actor: str = "lead") -> None:
        now = utc_now()
        self.conn.execute("UPDATE cases SET assignee = ?, updated_at = ? WHERE id = ?", (assignee, now, case_id))
        self.add_case_record(case_id, "assignment", f"Assigned to {assignee}", actor, commit=False)
        self.audit(actor, "assign_case", "case", str(case_id), assignee, commit=False)
        self.conn.commit()

    def review_case(self, case_id: int, reviewer: str, note: str, actor: str = "lead") -> None:
        now = utc_now()
        self.conn.execute(
            "UPDATE cases SET reviewer = ?, reviewed_at = ?, updated_at = ? WHERE id = ?",
            (reviewer, now, now, case_id),
        )
        self.add_case_record(case_id, "review", note, reviewer or actor, commit=False)
        self.audit(actor, "review_case", "case", str(case_id), reviewer, commit=False)
        self.conn.commit()

    def case_records(self, case_id: int) -> list[dict[str, Any]]:
        rows = self.conn.execute(
            "SELECT * FROM case_records WHERE case_id = ? ORDER BY created_at ASC, id ASC",
            (case_id,),
        ).fetchall()
        return [dict(row) for row in rows]

    def create_hunt_task(
        self,
        title: str,
        query: str,
        owner: str = "analyst",
        status: str = "planned",
        notes: str = "",
        hypothesis: str = "",
        result: str = "",
        conclusion: str = "",
        case_id: int | None = None,
    ) -> int:
        now = utc_now()
        cursor = self.conn.execute(
            """
            INSERT INTO hunt_tasks (
                title, query, status, owner, notes, hypothesis, result, conclusion,
                case_id, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (title, query, status, owner, notes, hypothesis, result, conclusion, case_id, now, now),
        )
        hunt_id = int(cursor.lastrowid)
        self.audit(owner, "create_hunt_task", "hunt_task", str(hunt_id), title, commit=False)
        self.conn.commit()
        return hunt_id

    def record_hunt_result(
        self,
        hunt_id: int,
        result: str,
        conclusion: str,
        status: str = "completed",
        actor: str = "analyst",
    ) -> None:
        now = utc_now()
        self.conn.execute(
            """
            UPDATE hunt_tasks
            SET result = ?, conclusion = ?, status = ?, updated_at = ?
            WHERE id = ?
            """,
            (result, conclusion, status, now, hunt_id),
        )
        self.audit(actor, "record_hunt_result", "hunt_task", str(hunt_id), status, commit=False)
        self.conn.commit()

    def list_hunt_tasks(self) -> list[dict[str, Any]]:
        rows = self.conn.execute("SELECT * FROM hunt_tasks ORDER BY created_at DESC").fetchall()
        return [dict(row) for row in rows]

    def summary(self) -> dict[str, Any]:
        alerts = self.list_alerts(limit=10000)
        cases = self.list_cases()
        by_severity = {key: 0 for key in SEVERITY_ORDER}
        by_status: dict[str, int] = {}
        case_status: dict[str, int] = {}
        for alert in alerts:
            by_severity[alert["severity"]] = by_severity.get(alert["severity"], 0) + 1
            by_status[alert["status"]] = by_status.get(alert["status"], 0) + 1
        for case in cases:
            case_status[case["status"]] = case_status.get(case["status"], 0) + 1
        return {
            "alerts_total": len(alerts),
            "cases_total": len(cases),
            "open_cases": sum(1 for case in cases if case["status"] not in {"closed", "archived"}),
            "by_severity": by_severity,
            "by_status": by_status,
            "case_status": case_status,
            "hunt_tasks_total": len(self.list_hunt_tasks()),
            "data_sources": self.list_data_source_health(),
            "recent_high_alerts": self.list_alerts(severity="critical", limit=5)
            + self.list_alerts(severity="high", limit=5),
        }

    def seed_demo_health(self, actor: str = "demo") -> None:
        now = utc_now()
        rows = [
            ("zeek", "Zeek", "healthy", "Demo Zeek JSONL received."),
            ("suricata", "Suricata", "healthy", "Demo Suricata EVE alerts received."),
            ("indexer", "Elastic/Indexer", "healthy", "Demo index status available."),
            ("sensor", "sensor-grid-01", "healthy", "Demo sensor heartbeat available."),
        ]
        for source, component, status, detail in rows:
            self.upsert_data_source_health(source, component, status, now, detail, commit=False)
        self.audit(actor, "seed_demo_health", "data_source", "demo", "count=4", commit=False)
        self.conn.commit()

    def upsert_data_source_health(
        self,
        source: str,
        component: str,
        status: str,
        last_seen: str,
        detail: str = "",
        commit: bool = True,
    ) -> None:
        now = utc_now()
        self.conn.execute(
            """
            INSERT INTO data_source_health (source, component, status, last_seen, detail, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(source, component)
            DO UPDATE SET status = excluded.status,
                          last_seen = excluded.last_seen,
                          detail = excluded.detail,
                          updated_at = excluded.updated_at
            """,
            (source, component, status, last_seen, detail, now),
        )
        if commit:
            self.conn.commit()

    def list_data_source_health(self) -> list[dict[str, Any]]:
        rows = self.conn.execute(
            "SELECT * FROM data_source_health ORDER BY source ASC, component ASC"
        ).fetchall()
        return [dict(row) for row in rows]

    def export_case_report(self, case_id: int, actor: str = "analyst") -> Path:
        case = self.get_case(case_id)
        if not case:
            raise ValueError(f"case {case_id} not found")
        report_path = self.paths.upload_dir / f"case-{case_id}-report.md"
        alert = case.get("alert") or {}
        lines = [
            f"# SOC Case Report: {case['title']}",
            "",
            f"- Case ID: {case['id']}",
            f"- Status: {case['status']}",
            f"- Severity: {case['severity']}",
            f"- Assignee: {case.get('assignee') or 'N/A'}",
            f"- Reviewer: {case.get('reviewer') or 'N/A'}",
            f"- Created: {case['created_at']}",
            f"- Closed: {case.get('closed_at') or 'N/A'}",
            f"- Archived: {case.get('archived_at') or 'N/A'}",
            "",
            "## Summary",
            case.get("summary") or "No summary provided.",
            "",
            "## Linked Alert",
            f"- Alert ID: {alert.get('id', 'N/A')}",
            f"- Source: {alert.get('source', 'N/A')}",
            f"- Event Type: {alert.get('event_type', 'N/A')}",
            f"- Source IP: {alert.get('src_ip', 'N/A')}",
            f"- Destination IP: {alert.get('dst_ip', 'N/A')}",
            f"- Asset: {alert.get('asset', 'N/A')}",
            f"- Rule: {alert.get('rule', 'N/A')}",
            "",
            "## Handling Records",
        ]
        for record in case["records"]:
            lines.append(f"- {record['created_at']} {record['analyst']} {record['action']}: {record['note']}")
        lines.extend(["", "## Raw Event", "```json", alert.get("raw_event", "{}"), "```", ""])
        report_path.write_text("\n".join(lines), encoding="utf-8")
        self.audit(actor, "export_report", "case", str(case_id), str(report_path))
        self.conn.commit()
        return report_path

    def export_hunt_report(self, hunt_id: int, actor: str = "analyst") -> Path:
        hunt = self.conn.execute("SELECT * FROM hunt_tasks WHERE id = ?", (hunt_id,)).fetchone()
        if not hunt:
            raise ValueError(f"hunt {hunt_id} not found")
        item = dict(hunt)
        report_path = self.paths.upload_dir / f"hunt-{hunt_id}-report.md"
        lines = [
            f"# SOC Hunt Report: {item['title']}",
            "",
            f"- Hunt ID: {item['id']}",
            f"- Status: {item['status']}",
            f"- Owner: {item['owner']}",
            f"- Linked Case: {item.get('case_id') or 'N/A'}",
            "",
            "## Hypothesis",
            item.get("hypothesis") or item.get("notes") or "No hypothesis provided.",
            "",
            "## Query Conditions",
            item["query"],
            "",
            "## Result",
            item.get("result") or "No result recorded.",
            "",
            "## Conclusion",
            item.get("conclusion") or "No conclusion recorded.",
            "",
        ]
        report_path.write_text("\n".join(lines), encoding="utf-8")
        self.audit(actor, "export_hunt_report", "hunt_task", str(hunt_id), str(report_path))
        self.conn.commit()
        return report_path

    def export_summary_report(self, period: str = "daily", actor: str = "analyst") -> Path:
        report_path = self.paths.upload_dir / f"soc-{period}-summary.md"
        summary = self.summary()
        lines = [
            f"# SOC {period.title()} Summary",
            "",
            f"- Alerts: {summary['alerts_total']}",
            f"- Cases: {summary['cases_total']}",
            f"- Open Cases: {summary['open_cases']}",
            f"- Hunt Tasks: {summary['hunt_tasks_total']}",
            "",
            "## Severity",
        ]
        for severity, count in summary["by_severity"].items():
            lines.append(f"- {severity}: {count}")
        lines.extend(["", "## Data Source Health"])
        for health in summary["data_sources"]:
            lines.append(
                f"- {health['component']} ({health['source']}): {health['status']} last_seen={health['last_seen']}"
            )
        report_path.write_text("\n".join(lines), encoding="utf-8")
        self.audit(actor, "export_summary_report", "report", period, str(report_path))
        self.conn.commit()
        return report_path

    def reset_demo(self, actor: str = "demo") -> None:
        for table in ("alerts", "cases", "case_records", "hunt_tasks", "data_source_health", "audit_logs"):
            self.conn.execute(f"DELETE FROM {table}")
        for report in self.paths.upload_dir.glob("*.md"):
            report.unlink()
        self.conn.commit()
        self.audit(actor, "reset_demo", "runtime", "all", "cleared demo state")

    def backup_runtime(self, actor: str = "admin") -> Path:
        backup_root = self.paths.env_root / "backups" / "02-securityonion"
        backup_root.mkdir(parents=True, exist_ok=True)
        stamp = utc_now().replace(":", "").replace("+", "").replace("-", "")
        target = backup_root / f"soc-hunt-{stamp}"
        target.mkdir(parents=True, exist_ok=True)
        self.conn.commit()
        if self.paths.db_path.exists():
            shutil.copy2(self.paths.db_path, target / self.paths.db_path.name)
        if self.paths.upload_dir.exists():
            shutil.copytree(self.paths.upload_dir, target / "uploads", dirs_exist_ok=True)
        self.audit(actor, "backup_runtime", "runtime", "backup", str(target))
        return target

    def restore_runtime(self, backup_path: Path, actor: str = "admin") -> None:
        source_db = backup_path / self.paths.db_path.name
        if not source_db.exists():
            raise ValueError(f"backup database not found: {source_db}")
        self.conn.close()
        shutil.copy2(source_db, self.paths.db_path)
        uploads = backup_path / "uploads"
        if uploads.exists():
            shutil.copytree(uploads, self.paths.upload_dir, dirs_exist_ok=True)
        self.conn = sqlite3.connect(self.paths.db_path)
        self.conn.row_factory = sqlite3.Row
        self.migrate()
        self.audit(actor, "restore_runtime", "runtime", "backup", str(backup_path))

    def audit(
        self,
        actor: str,
        action: str,
        entity_type: str,
        entity_id: str,
        detail: str = "",
        commit: bool = True,
    ) -> None:
        self.conn.execute(
            "INSERT INTO audit_logs (actor, action, entity_type, entity_id, detail, created_at) VALUES (?, ?, ?, ?, ?, ?)",
            (actor, action, entity_type, entity_id, detail, utc_now()),
        )
        if commit:
            self.conn.commit()

    def audit_logs(self, limit: int = 100) -> list[dict[str, Any]]:
        rows = self.conn.execute(
            "SELECT * FROM audit_logs ORDER BY created_at DESC, id DESC LIMIT ?",
            (limit,),
        ).fetchall()
        return [dict(row) for row in rows]

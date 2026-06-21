#!/usr/bin/env python3
"""Authorized ZAP scan, retest, reporting, and CI gate platform.

The module is a production-oriented orchestration layer around ZAP-style scan
results. The bundled scan runner is intentionally demo-only and never sends
network traffic. Real ZAP API integration must stay behind the same target
authorization, scope, rate, concurrency, and dangerous-action checks.
"""

from __future__ import annotations

import argparse
import fnmatch
import hashlib
import html
import ipaddress
import json
import logging
import os
import re
import sqlite3
import sys
import uuid
from datetime import datetime, timezone
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, quote, unquote, urlparse


APP_DIR = Path(__file__).resolve().parent
STATIC_DIR = APP_DIR / "static"
DEFAULT_ENV_ROOT = Path("/Users/zhangjiyan/Environment")

RISKS = ("Critical", "High", "Medium", "Low", "Informational")
RISK_WEIGHT = {risk: index for index, risk in enumerate(RISKS)}
SCAN_TYPES = ("baseline", "full", "api")
POLICIES = ("passive-safe", "standard-readonly", "api-readonly")
VULN_ACTIVE_STATUSES = ("pending_fix", "fixed_pending_retest", "retest_failed")
MAX_CONCURRENT_SCANS = 2
MAX_RATE_LIMIT_PER_MIN = 120
MAX_TIMEOUT_SECONDS = 3600


def utcnow() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def parse_time(value: Any, field: str, required: bool = False) -> str:
    if value in (None, ""):
        if required:
            raise ValidationError(f"{field}不能为空")
        return ""
    text = str(value).strip()
    if len(text) == 10:
        text = f"{text}T23:59:59+00:00"
    if text.endswith("Z"):
        text = text[:-1] + "+00:00"
    try:
        parsed = datetime.fromisoformat(text)
    except ValueError as exc:
        raise ValidationError(f"{field}必须是 ISO 日期或日期时间") from exc
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc).replace(microsecond=0).isoformat()


def new_id(prefix: str) -> str:
    return f"{prefix}_{uuid.uuid4().hex[:12]}"


class ValidationError(Exception):
    pass


class Store:
    def __init__(self, env_root: Path):
        self.env_root = env_root
        self.db_dir = env_root / "02-databases" / "14-zaproxy"
        self.log_dir = env_root / "11-logs" / "14-zaproxy"
        self.upload_dir = env_root / "13-uploads" / "14-zaproxy"
        self.docs_dir = env_root / "08-docs" / "14-zaproxy"
        self.report_dir = self.docs_dir / "reports"
        self.secret_dir = env_root / "12-secrets" / "14-zaproxy"
        self.db_path = self.db_dir / "platform.sqlite3"
        self._ensure_dirs()
        self._migrate()

    def _ensure_dirs(self) -> None:
        for directory in (
            self.db_dir,
            self.log_dir,
            self.upload_dir,
            self.docs_dir,
            self.report_dir,
            self.secret_dir,
        ):
            directory.mkdir(parents=True, exist_ok=True)

    def connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA foreign_keys = ON")
        return conn

    def _migrate(self) -> None:
        with self.connect() as conn:
            conn.executescript(
                """
                CREATE TABLE IF NOT EXISTS targets (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    base_url TEXT NOT NULL,
                    scope_prefix TEXT NOT NULL,
                    owner TEXT NOT NULL,
                    authorization_note TEXT NOT NULL,
                    authorization_confirmed INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    created_at TEXT NOT NULL
                );

                CREATE TABLE IF NOT EXISTS tasks (
                    id TEXT PRIMARY KEY,
                    target_id TEXT NOT NULL REFERENCES targets(id),
                    name TEXT NOT NULL,
                    scan_type TEXT NOT NULL,
                    policy TEXT NOT NULL,
                    status TEXT NOT NULL,
                    risk_gate TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    started_at TEXT,
                    completed_at TEXT,
                    notes TEXT NOT NULL DEFAULT ''
                );

                CREATE TABLE IF NOT EXISTS vulnerabilities (
                    id TEXT PRIMARY KEY,
                    task_id TEXT NOT NULL REFERENCES tasks(id),
                    risk TEXT NOT NULL,
                    title TEXT NOT NULL,
                    url TEXT NOT NULL,
                    parameter TEXT NOT NULL,
                    evidence TEXT NOT NULL,
                    recommendation TEXT NOT NULL,
                    status TEXT NOT NULL,
                    retest_count INTEGER NOT NULL DEFAULT 0,
                    first_seen TEXT NOT NULL,
                    last_seen TEXT NOT NULL,
                    closed_at TEXT,
                    closure_reason TEXT NOT NULL DEFAULT ''
                );

                CREATE TABLE IF NOT EXISTS reports (
                    id TEXT PRIMARY KEY,
                    task_id TEXT NOT NULL REFERENCES tasks(id),
                    format TEXT NOT NULL,
                    path TEXT NOT NULL,
                    created_at TEXT NOT NULL
                );

                CREATE TABLE IF NOT EXISTS task_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    task_id TEXT NOT NULL REFERENCES tasks(id),
                    timestamp TEXT NOT NULL,
                    level TEXT NOT NULL,
                    message TEXT NOT NULL
                );

                CREATE TABLE IF NOT EXISTS vulnerability_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    vulnerability_id TEXT NOT NULL REFERENCES vulnerabilities(id),
                    timestamp TEXT NOT NULL,
                    from_status TEXT NOT NULL,
                    to_status TEXT NOT NULL,
                    note TEXT NOT NULL
                );

                CREATE TABLE IF NOT EXISTS comparisons (
                    id TEXT PRIMARY KEY,
                    baseline_task_id TEXT NOT NULL REFERENCES tasks(id),
                    current_task_id TEXT NOT NULL REFERENCES tasks(id),
                    created_at TEXT NOT NULL,
                    new_count INTEGER NOT NULL,
                    fixed_count INTEGER NOT NULL,
                    carried_count INTEGER NOT NULL,
                    risk_trend_json TEXT NOT NULL,
                    summary_json TEXT NOT NULL
                );

                CREATE TABLE IF NOT EXISTS ci_gate_runs (
                    id TEXT PRIMARY KEY,
                    task_id TEXT NOT NULL REFERENCES tasks(id),
                    status TEXT NOT NULL,
                    threshold TEXT NOT NULL,
                    fail_on TEXT NOT NULL,
                    message TEXT NOT NULL,
                    result_json TEXT NOT NULL,
                    created_at TEXT NOT NULL
                );

                CREATE TABLE IF NOT EXISTS audit_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp TEXT NOT NULL,
                    actor TEXT NOT NULL,
                    action TEXT NOT NULL,
                    object_type TEXT NOT NULL,
                    object_id TEXT NOT NULL,
                    details_json TEXT NOT NULL
                );
                """
            )
            self._ensure_columns(conn)

    def _ensure_columns(self, conn: sqlite3.Connection) -> None:
        target_columns = {
            "allowlist_json": "TEXT NOT NULL DEFAULT '[]'",
            "blocklist_json": "TEXT NOT NULL DEFAULT '[]'",
            "valid_from": "TEXT NOT NULL DEFAULT ''",
            "valid_until": "TEXT NOT NULL DEFAULT ''",
            "updated_at": "TEXT NOT NULL DEFAULT ''",
        }
        task_columns = {
            "timeout_seconds": "INTEGER NOT NULL DEFAULT 600",
            "rate_limit_per_min": "INTEGER NOT NULL DEFAULT 30",
            "dangerous_actions_enabled": "INTEGER NOT NULL DEFAULT 0",
            "canceled_at": "TEXT",
            "parent_task_id": "TEXT",
            "task_kind": "TEXT NOT NULL DEFAULT 'scan'",
            "log_path": "TEXT NOT NULL DEFAULT ''",
        }
        vuln_columns = {
            "cwe": "TEXT NOT NULL DEFAULT ''",
            "wasc": "TEXT NOT NULL DEFAULT ''",
            "fingerprint": "TEXT NOT NULL DEFAULT ''",
            "confidence": "TEXT NOT NULL DEFAULT 'Medium'",
            "scan_phase": "TEXT NOT NULL DEFAULT 'baseline'",
            "original_vulnerability_id": "TEXT",
        }
        report_columns = {
            "report_type": "TEXT NOT NULL DEFAULT 'scan'",
        }
        for table, columns in (
            ("targets", target_columns),
            ("tasks", task_columns),
            ("vulnerabilities", vuln_columns),
            ("reports", report_columns),
        ):
            existing = {row["name"] for row in conn.execute(f"PRAGMA table_info({table})")}
            for name, definition in columns.items():
                if name not in existing:
                    conn.execute(f"ALTER TABLE {table} ADD COLUMN {name} {definition}")

    def list_targets(self) -> list[dict[str, Any]]:
        with self.connect() as conn:
            rows = conn.execute("SELECT * FROM targets ORDER BY created_at DESC").fetchall()
        return [decode_target(dict(row)) for row in rows]

    def get_target(self, target_id: str) -> dict[str, Any]:
        with self.connect() as conn:
            row = conn.execute("SELECT * FROM targets WHERE id = ?", (target_id,)).fetchone()
        if not row:
            raise ValidationError("授权目标不存在")
        return decode_target(dict(row))

    def create_target(self, data: dict[str, Any]) -> dict[str, Any]:
        name = clean_text(data.get("name"), "目标名称", 2, 80)
        base_url = normalize_url(data.get("base_url") or data.get("target_url"))
        scope_prefix = normalize_scope(data.get("scope_prefix") or base_url, base_url)
        owner = clean_text(data.get("owner"), "负责人", 2, 80)
        authorization_note = clean_text(data.get("authorization_note"), "授权依据", 8, 1000)
        if not bool(data.get("authorization_confirmed")):
            raise ValidationError("必须确认该目标已获得扫描授权")
        valid_from = parse_time(data.get("valid_from"), "授权开始时间")
        valid_until = parse_time(data.get("valid_until"), "授权截止时间", required=True)
        if valid_until <= utcnow():
            raise ValidationError("授权截止时间必须晚于当前时间")
        allowlist = normalize_patterns(data.get("allowlist"), scope_prefix, base_url, required=False)
        blocklist = normalize_patterns(data.get("blocklist"), scope_prefix, base_url, required=False)

        target = {
            "id": new_id("tgt"),
            "name": name,
            "base_url": base_url,
            "scope_prefix": scope_prefix,
            "owner": owner,
            "authorization_note": authorization_note,
            "authorization_confirmed": 1,
            "allowlist_json": json.dumps(allowlist, ensure_ascii=False),
            "blocklist_json": json.dumps(blocklist, ensure_ascii=False),
            "valid_from": valid_from,
            "valid_until": valid_until,
            "status": "active",
            "created_at": utcnow(),
            "updated_at": utcnow(),
        }
        with self.connect() as conn:
            conn.execute(
                """
                INSERT INTO targets (
                    id, name, base_url, scope_prefix, owner, authorization_note,
                    authorization_confirmed, allowlist_json, blocklist_json,
                    valid_from, valid_until, status, created_at, updated_at
                ) VALUES (
                    :id, :name, :base_url, :scope_prefix, :owner, :authorization_note,
                    :authorization_confirmed, :allowlist_json, :blocklist_json,
                    :valid_from, :valid_until, :status, :created_at, :updated_at
                )
                """,
                target,
            )
            self._audit(conn, "operator", "target.create", "target", target["id"], target)
        return decode_target(target)

    def create_task(self, data: dict[str, Any]) -> dict[str, Any]:
        target = self.get_target(str(data.get("target_id") or ""))
        self._validate_target_for_scan(target)
        scan_type = normalize_scan_type(data.get("scan_type"))
        policy = str(data.get("policy") or default_policy(scan_type))
        if policy not in POLICIES:
            raise ValidationError("扫描策略不在允许列表内")
        timeout_seconds = bounded_int(data.get("timeout_seconds"), "超时时间", 60, MAX_TIMEOUT_SECONDS, 600)
        rate_limit = bounded_int(data.get("rate_limit_per_min"), "速率限制", 1, MAX_RATE_LIMIT_PER_MIN, 30)
        dangerous = bool(data.get("dangerous_actions_enabled"))
        if dangerous:
            raise ValidationError("危险动作默认关闭，demo 平台不允许开启")

        with self.connect() as conn:
            running = self._scalar(conn, "SELECT COUNT(*) FROM tasks WHERE status IN ('queued', 'running')")
            if running >= MAX_CONCURRENT_SCANS:
                raise ValidationError("当前扫描并发已达上限")

            now = utcnow()
            task = {
                "id": new_id("scan"),
                "target_id": target["id"],
                "name": clean_text(data.get("name") or f"{target['name']} {scan_type} 扫描", "任务名称", 2, 100),
                "scan_type": scan_type,
                "policy": policy,
                "status": "queued" if bool(data.get("queue_only")) else "running",
                "risk_gate": normalize_risk(data.get("risk_gate") or "High"),
                "created_at": now,
                "started_at": None if bool(data.get("queue_only")) else now,
                "completed_at": None,
                "notes": clean_optional(data.get("notes"), 800),
                "timeout_seconds": timeout_seconds,
                "rate_limit_per_min": rate_limit,
                "dangerous_actions_enabled": 0,
                "canceled_at": None,
                "parent_task_id": clean_optional(data.get("parent_task_id"), 80) or None,
                "task_kind": str(data.get("task_kind") or "scan"),
                "log_path": str(self.log_dir / f"{now[:10]}-{new_id('task')}.log"),
            }
            conn.execute(
                """
                INSERT INTO tasks (
                    id, target_id, name, scan_type, policy, status, risk_gate,
                    created_at, started_at, completed_at, notes, timeout_seconds,
                    rate_limit_per_min, dangerous_actions_enabled, canceled_at,
                    parent_task_id, task_kind, log_path
                ) VALUES (
                    :id, :target_id, :name, :scan_type, :policy, :status, :risk_gate,
                    :created_at, :started_at, :completed_at, :notes, :timeout_seconds,
                    :rate_limit_per_min, :dangerous_actions_enabled, :canceled_at,
                    :parent_task_id, :task_kind, :log_path
                )
                """,
                task,
            )
            self._log_task(conn, task["id"], "info", f"任务创建：{scan_type}, policy={policy}, rate={rate_limit}/min")
            self._audit(conn, "operator", "task.create", "task", task["id"], task)
            if not bool(data.get("queue_only")):
                self._run_demo_scan(conn, task, target, omit_fingerprints=set(data.get("omit_fingerprints") or []))
        return self.get_task(task["id"])

    def _run_demo_scan(
        self,
        conn: sqlite3.Connection,
        task: dict[str, Any],
        target: dict[str, Any],
        omit_fingerprints: set[str],
    ) -> None:
        findings = [
            finding for finding in demo_findings(task["id"], target, task["scan_type"])
            if finding["fingerprint"] not in omit_fingerprints
        ]
        for finding in findings:
            conn.execute(
                """
                INSERT INTO vulnerabilities (
                    id, task_id, risk, title, url, parameter, evidence,
                    recommendation, status, retest_count, first_seen, last_seen,
                    cwe, wasc, fingerprint, confidence, scan_phase, original_vulnerability_id
                ) VALUES (
                    :id, :task_id, :risk, :title, :url, :parameter, :evidence,
                    :recommendation, :status, :retest_count, :first_seen, :last_seen,
                    :cwe, :wasc, :fingerprint, :confidence, :scan_phase, :original_vulnerability_id
                )
                """,
                finding,
            )
        now = utcnow()
        conn.execute(
            "UPDATE tasks SET status = 'completed', completed_at = ? WHERE id = ?",
            (now, task["id"]),
        )
        self._log_task(conn, task["id"], "info", f"demo 扫描完成，生成 {len(findings)} 条漏洞")

    def cancel_task(self, task_id: str, data: dict[str, Any]) -> dict[str, Any]:
        reason = clean_text(data.get("reason") or "operator canceled", "取消原因", 4, 300)
        with self.connect() as conn:
            row = conn.execute("SELECT * FROM tasks WHERE id = ?", (task_id,)).fetchone()
            if not row:
                raise ValidationError("扫描任务不存在")
            if row["status"] not in {"queued", "running"}:
                raise ValidationError("只有 queued/running 任务可取消")
            now = utcnow()
            conn.execute(
                "UPDATE tasks SET status = 'canceled', canceled_at = ?, completed_at = COALESCE(completed_at, ?) WHERE id = ?",
                (now, now, task_id),
            )
            self._log_task(conn, task_id, "warning", f"任务取消：{reason}")
            self._audit(conn, "operator", "task.cancel", "task", task_id, {"reason": reason})
        return self.get_task(task_id)

    def list_tasks(self) -> list[dict[str, Any]]:
        with self.connect() as conn:
            rows = conn.execute(
                """
                SELECT tasks.*, targets.name AS target_name, targets.base_url AS target_url
                FROM tasks
                JOIN targets ON targets.id = tasks.target_id
                ORDER BY tasks.created_at DESC
                """
            ).fetchall()
        return [self._task_summary(dict(row)) for row in rows]

    def get_task(self, task_id: str) -> dict[str, Any]:
        with self.connect() as conn:
            row = conn.execute(
                """
                SELECT tasks.*, targets.name AS target_name, targets.base_url AS target_url,
                       targets.scope_prefix, targets.owner
                FROM tasks
                JOIN targets ON targets.id = tasks.target_id
                WHERE tasks.id = ?
                """,
                (task_id,),
            ).fetchone()
        if not row:
            raise ValidationError("扫描任务不存在")
        task = self._task_summary(dict(row))
        task["vulnerabilities"] = self.list_vulnerabilities(task_id)
        task["logs"] = self.list_task_logs(task_id)
        task["comparison"] = self.get_comparison_for_task(task_id)
        task["ci_gate"] = self.latest_ci_gate(task_id)
        return task

    def _task_summary(self, task: dict[str, Any]) -> dict[str, Any]:
        counts = self.risk_counts(task["id"])
        task["risk_counts"] = counts
        task["active_count"] = sum(counts.values())
        task["open_count"] = task["active_count"]
        return task

    def risk_counts(self, task_id: str) -> dict[str, int]:
        counts = {risk: 0 for risk in RISKS}
        placeholders = ",".join("?" for _ in VULN_ACTIVE_STATUSES)
        with self.connect() as conn:
            rows = conn.execute(
                f"""
                SELECT risk, COUNT(*) AS count
                FROM vulnerabilities
                WHERE task_id = ? AND status IN ({placeholders})
                GROUP BY risk
                """,
                (task_id, *VULN_ACTIVE_STATUSES),
            ).fetchall()
        for row in rows:
            counts[row["risk"]] = row["count"]
        return counts

    def list_vulnerabilities(self, task_id: str | None = None) -> list[dict[str, Any]]:
        params: tuple[Any, ...] = ()
        where = ""
        if task_id:
            where = "WHERE vulnerabilities.task_id = ?"
            params = (task_id,)
        with self.connect() as conn:
            rows = conn.execute(
                f"""
                SELECT vulnerabilities.*, tasks.name AS task_name, targets.name AS target_name
                FROM vulnerabilities
                JOIN tasks ON tasks.id = vulnerabilities.task_id
                JOIN targets ON targets.id = tasks.target_id
                {where}
                ORDER BY
                  CASE risk
                    WHEN 'Critical' THEN 1 WHEN 'High' THEN 2 WHEN 'Medium' THEN 3
                    WHEN 'Low' THEN 4 ELSE 5
                  END,
                  last_seen DESC
                """,
                params,
            ).fetchall()
        return [dict(row) for row in rows]

    def mark_fixed(self, vuln_id: str, data: dict[str, Any]) -> dict[str, Any]:
        note = clean_text(data.get("note") or "已修复，等待复测", "修复说明", 4, 500)
        return self._transition_vulnerability(vuln_id, "fixed_pending_retest", note, allowed={"pending_fix", "retest_failed"})

    def close_vulnerability(self, vuln_id: str, data: dict[str, Any]) -> dict[str, Any]:
        reason = clean_text(data.get("closure_reason"), "关闭说明", 4, 500)
        with self.connect() as conn:
            row = conn.execute("SELECT * FROM vulnerabilities WHERE id = ?", (vuln_id,)).fetchone()
            if not row:
                raise ValidationError("漏洞不存在")
            if row["status"] not in {"retest_passed", "closed"}:
                raise ValidationError("仅复测通过的漏洞可关闭")
            now = utcnow()
            conn.execute(
                """
                UPDATE vulnerabilities
                SET status = 'closed', closed_at = ?, closure_reason = ?
                WHERE id = ?
                """,
                (now, reason, vuln_id),
            )
            self._insert_history(conn, vuln_id, row["status"], "closed", reason)
            self._audit(conn, "operator", "vulnerability.close", "vulnerability", vuln_id, {"reason": reason})
        return self.get_vulnerability(vuln_id)

    def mark_retesting(self, vuln_id: str) -> dict[str, Any]:
        return self._transition_vulnerability(vuln_id, "fixed_pending_retest", "标记为已修复待复测", allowed={"pending_fix", "retest_failed"})

    def _transition_vulnerability(
        self,
        vuln_id: str,
        new_status: str,
        note: str,
        *,
        allowed: set[str],
    ) -> dict[str, Any]:
        with self.connect() as conn:
            row = conn.execute("SELECT * FROM vulnerabilities WHERE id = ?", (vuln_id,)).fetchone()
            if not row:
                raise ValidationError("漏洞不存在")
            if row["status"] not in allowed:
                raise ValidationError(f"当前状态 {row['status']} 不允许流转到 {new_status}")
            conn.execute(
                "UPDATE vulnerabilities SET status = ?, retest_count = retest_count + 1, last_seen = ? WHERE id = ?",
                (new_status, utcnow(), vuln_id),
            )
            self._insert_history(conn, vuln_id, row["status"], new_status, note)
            self._audit(conn, "operator", "vulnerability.transition", "vulnerability", vuln_id, {"to": new_status, "note": note})
        return self.get_vulnerability(vuln_id)

    def get_vulnerability(self, vuln_id: str) -> dict[str, Any]:
        with self.connect() as conn:
            row = conn.execute("SELECT * FROM vulnerabilities WHERE id = ?", (vuln_id,)).fetchone()
        if not row:
            raise ValidationError("漏洞不存在")
        return dict(row)

    def create_retest_task(self, task_id: str) -> dict[str, Any]:
        original = self.get_task(task_id)
        target = self.get_target(original["target_id"])
        fixed_fps = {
            vuln["fingerprint"]
            for vuln in original["vulnerabilities"]
            if vuln["status"] == "fixed_pending_retest"
        }
        data = {
            "target_id": target["id"],
            "name": f"{original['name']} 复测",
            "scan_type": original["scan_type"],
            "policy": original["policy"],
            "risk_gate": original["risk_gate"],
            "notes": f"复测来源任务：{task_id}",
            "parent_task_id": task_id,
            "task_kind": "retest",
            "omit_fingerprints": sorted(fixed_fps),
        }
        retest = self.create_task(data)
        comparison = self.compare_tasks(task_id, retest["id"])
        retest["comparison"] = comparison
        return retest

    def compare_tasks(self, baseline_task_id: str, current_task_id: str) -> dict[str, Any]:
        baseline = self.get_task(baseline_task_id)
        current = self.get_task(current_task_id)
        baseline_by_fp = {v["fingerprint"]: v for v in baseline["vulnerabilities"]}
        current_by_fp = {v["fingerprint"]: v for v in current["vulnerabilities"]}
        baseline_fps = set(baseline_by_fp)
        current_fps = set(current_by_fp)
        new_fps = sorted(current_fps - baseline_fps)
        fixed_fps = sorted(baseline_fps - current_fps)
        carried_fps = sorted(baseline_fps & current_fps)
        with self.connect() as conn:
            for fp in fixed_fps:
                vuln = baseline_by_fp[fp]
                if vuln["status"] == "fixed_pending_retest":
                    conn.execute("UPDATE vulnerabilities SET status = 'retest_passed', last_seen = ? WHERE id = ?", (utcnow(), vuln["id"]))
                    self._insert_history(conn, vuln["id"], vuln["status"], "retest_passed", f"复测任务 {current_task_id} 未再发现")
            for fp in carried_fps:
                vuln = baseline_by_fp[fp]
                if vuln["status"] == "fixed_pending_retest":
                    conn.execute("UPDATE vulnerabilities SET status = 'retest_failed', last_seen = ? WHERE id = ?", (utcnow(), vuln["id"]))
                    self._insert_history(conn, vuln["id"], vuln["status"], "retest_failed", f"复测任务 {current_task_id} 仍然发现")
            trend = risk_trend(
                [baseline_by_fp[fp] for fp in baseline_fps],
                [current_by_fp[fp] for fp in current_fps],
            )
            summary = {
                "new": [current_by_fp[fp] for fp in new_fps],
                "fixed": [baseline_by_fp[fp] for fp in fixed_fps],
                "carried": [current_by_fp[fp] for fp in carried_fps],
            }
            row = {
                "id": new_id("cmp"),
                "baseline_task_id": baseline_task_id,
                "current_task_id": current_task_id,
                "created_at": utcnow(),
                "new_count": len(new_fps),
                "fixed_count": len(fixed_fps),
                "carried_count": len(carried_fps),
                "risk_trend_json": json.dumps(trend, ensure_ascii=False, sort_keys=True),
                "summary_json": json.dumps(summary, ensure_ascii=False, sort_keys=True),
            }
            conn.execute(
                """
                INSERT INTO comparisons (
                    id, baseline_task_id, current_task_id, created_at,
                    new_count, fixed_count, carried_count, risk_trend_json, summary_json
                ) VALUES (
                    :id, :baseline_task_id, :current_task_id, :created_at,
                    :new_count, :fixed_count, :carried_count, :risk_trend_json, :summary_json
                )
                """,
                row,
            )
            self._audit(conn, "operator", "task.compare", "comparison", row["id"], row)
        return decode_comparison(row)

    def get_comparison_for_task(self, task_id: str) -> dict[str, Any] | None:
        with self.connect() as conn:
            row = conn.execute(
                """
                SELECT * FROM comparisons
                WHERE current_task_id = ? OR baseline_task_id = ?
                ORDER BY created_at DESC
                LIMIT 1
                """,
                (task_id, task_id),
            ).fetchone()
        return decode_comparison(dict(row)) if row else None

    def run_ci_gate(self, task_id: str, data: dict[str, Any]) -> dict[str, Any]:
        threshold = normalize_risk(data.get("threshold") or data.get("risk_gate") or "High")
        fail_on = str(data.get("fail_on") or "active_at_or_above_threshold")
        task = self.get_task(task_id)
        blocking = [
            vuln for vuln in task["vulnerabilities"]
            if vuln["status"] in VULN_ACTIVE_STATUSES and RISK_WEIGHT[vuln["risk"]] <= RISK_WEIGHT[threshold]
        ]
        status = "failed" if blocking else "passed"
        result = {
            "threshold": threshold,
            "blocking_count": len(blocking),
            "blocking": blocking,
            "build_result": "failure" if blocking else "success",
        }
        row = {
            "id": new_id("gate"),
            "task_id": task_id,
            "status": status,
            "threshold": threshold,
            "fail_on": fail_on,
            "message": f"CI/CD 门禁{status}: {len(blocking)} 个 {threshold}+ 活跃漏洞",
            "result_json": json.dumps(result, ensure_ascii=False, sort_keys=True),
            "created_at": utcnow(),
        }
        with self.connect() as conn:
            conn.execute(
                """
                INSERT INTO ci_gate_runs
                    (id, task_id, status, threshold, fail_on, message, result_json, created_at)
                VALUES (:id, :task_id, :status, :threshold, :fail_on, :message, :result_json, :created_at)
                """,
                row,
            )
            self._log_task(conn, task_id, "info", row["message"])
            self._audit(conn, "ci", "ci_gate.run", "task", task_id, row)
        return decode_gate(row)

    def latest_ci_gate(self, task_id: str) -> dict[str, Any] | None:
        with self.connect() as conn:
            row = conn.execute(
                "SELECT * FROM ci_gate_runs WHERE task_id = ? ORDER BY created_at DESC LIMIT 1",
                (task_id,),
            ).fetchone()
        return decode_gate(dict(row)) if row else None

    def export_report(self, task_id: str, report_type: str = "scan") -> dict[str, Any]:
        if report_type not in {"scan", "retest", "remediation"}:
            raise ValidationError("报告类型必须是 scan/retest/remediation")
        task = self.get_task(task_id)
        report_id = new_id("rpt")
        html_report = render_report(task, report_type)
        filename = f"{task_id}-{report_type}-{datetime.now().strftime('%Y%m%d-%H%M%S')}.html"
        report_path = self.report_dir / filename
        report_path.write_text(html_report, encoding="utf-8")
        row = {
            "id": report_id,
            "task_id": task_id,
            "format": "html",
            "report_type": report_type,
            "path": str(report_path),
            "created_at": utcnow(),
        }
        with self.connect() as conn:
            conn.execute(
                "INSERT INTO reports (id, task_id, format, report_type, path, created_at) VALUES (:id, :task_id, :format, :report_type, :path, :created_at)",
                row,
            )
            self._audit(conn, "operator", "report.export", "report", report_id, row)
        logging.info("exported %s report %s for task %s", report_type, report_path, task_id)
        return {**row, "download_url": f"/reports/{quote(filename)}"}

    def list_task_logs(self, task_id: str) -> list[dict[str, Any]]:
        with self.connect() as conn:
            rows = conn.execute(
                "SELECT timestamp, level, message FROM task_logs WHERE task_id = ? ORDER BY id",
                (task_id,),
            ).fetchall()
        return [dict(row) for row in rows]

    def _validate_target_for_scan(self, target: dict[str, Any]) -> None:
        now = utcnow()
        if target["status"] != "active" or not target["authorization_confirmed"]:
            raise ValidationError("目标未处于已授权可扫描状态")
        if target.get("valid_from") and target["valid_from"] > now:
            raise ValidationError("目标授权尚未生效")
        if target.get("valid_until") and target["valid_until"] < now:
            raise ValidationError("目标授权已过期")

    def _log_task(self, conn: sqlite3.Connection, task_id: str, level: str, message: str) -> None:
        conn.execute(
            "INSERT INTO task_logs (task_id, timestamp, level, message) VALUES (?, ?, ?, ?)",
            (task_id, utcnow(), level, message),
        )

    def _insert_history(self, conn: sqlite3.Connection, vuln_id: str, old: str, new: str, note: str) -> None:
        conn.execute(
            """
            INSERT INTO vulnerability_history
                (vulnerability_id, timestamp, from_status, to_status, note)
            VALUES (?, ?, ?, ?, ?)
            """,
            (vuln_id, utcnow(), old, new, note),
        )

    def _audit(
        self,
        conn: sqlite3.Connection,
        actor: str,
        action: str,
        object_type: str,
        object_id: str,
        details: dict[str, Any],
    ) -> None:
        conn.execute(
            """
            INSERT INTO audit_events
                (timestamp, actor, action, object_type, object_id, details_json)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            (utcnow(), actor, action, object_type, object_id, json.dumps(details, ensure_ascii=False, sort_keys=True)),
        )

    @staticmethod
    def _scalar(conn: sqlite3.Connection, sql: str) -> int:
        return int(conn.execute(sql).fetchone()[0] or 0)


def clean_optional(value: Any, max_length: int) -> str:
    if value is None:
        return ""
    return str(value).strip()[:max_length]


def clean_text(value: Any, field: str, min_length: int, max_length: int) -> str:
    text = clean_optional(value, max_length)
    if len(text) < min_length:
        raise ValidationError(f"{field}长度不足")
    return text


def bounded_int(value: Any, field: str, minimum: int, maximum: int, default: int) -> int:
    if value in (None, ""):
        return default
    try:
        parsed = int(value)
    except (TypeError, ValueError) as exc:
        raise ValidationError(f"{field}必须是整数") from exc
    if parsed < minimum or parsed > maximum:
        raise ValidationError(f"{field}必须在 {minimum}-{maximum} 之间")
    return parsed


def normalize_url(value: Any) -> str:
    raw = clean_text(value, "URL", 8, 400)
    parsed = urlparse(raw)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        raise ValidationError("URL 必须是 http/https 地址")
    if parsed.username or parsed.password:
        raise ValidationError("URL 不允许包含账号密码")
    if not valid_host(parsed.hostname or ""):
        raise ValidationError("URL 主机名或 IP 非法")
    path = parsed.path.rstrip("/")
    return parsed._replace(path=path or "", params="", query="", fragment="").geturl()


def valid_host(host: str) -> bool:
    if not host or len(host) > 253:
        return False
    try:
        ipaddress.ip_address(host)
        return True
    except ValueError:
        return all(re.fullmatch(r"[A-Za-z0-9-]{1,63}", part) for part in host.rstrip(".").split("."))


def normalize_scope(value: Any, base_url: str) -> str:
    scope = normalize_url(value)
    base = urlparse(base_url)
    candidate = urlparse(scope)
    if base.scheme != candidate.scheme or base.netloc != candidate.netloc:
        raise ValidationError("扫描范围必须位于授权目标同源下")
    return scope


def normalize_patterns(value: Any, scope_prefix: str, base_url: str, required: bool) -> list[str]:
    if value in (None, ""):
        return [scope_prefix.rstrip("/") + "*"] if required else []
    if isinstance(value, str):
        raw = [line.strip() for line in re.split(r"[\n,]", value) if line.strip()]
    elif isinstance(value, list):
        raw = [str(item).strip() for item in value if str(item).strip()]
    else:
        raise ValidationError("黑白名单必须是字符串或数组")
    patterns: list[str] = []
    for item in raw:
        if item.startswith(("http://", "https://")):
            normalized = normalize_scope(item.rstrip("*"), base_url)
            patterns.append(normalized + ("*" if item.endswith("*") else ""))
        elif item.startswith("/"):
            patterns.append(scope_prefix.rstrip("/") + item + ("*" if not item.endswith("*") else ""))
        else:
            patterns.append(scope_prefix.rstrip("/") + "/" + item.lstrip("*"))
    return patterns


def normalize_scan_type(value: Any) -> str:
    scan_type = str(value or "baseline").replace("demo_", "")
    if scan_type not in SCAN_TYPES:
        raise ValidationError("扫描类型必须是 baseline/full/api")
    return scan_type


def default_policy(scan_type: str) -> str:
    return "api-readonly" if scan_type == "api" else "passive-safe"


def normalize_risk(value: Any) -> str:
    text = str(value or "High")
    if text not in RISKS:
        raise ValidationError("风险阈值非法")
    return text


def decode_target(row: dict[str, Any]) -> dict[str, Any]:
    row["target_url"] = row.get("base_url", "")
    row["allowlist"] = json.loads(row.get("allowlist_json") or "[]")
    row["blocklist"] = json.loads(row.get("blocklist_json") or "[]")
    return row


def decode_comparison(row: dict[str, Any]) -> dict[str, Any]:
    row["risk_trend"] = json.loads(row.get("risk_trend_json") or "{}")
    row["summary"] = json.loads(row.get("summary_json") or "{}")
    return row


def decode_gate(row: dict[str, Any]) -> dict[str, Any]:
    row["result"] = json.loads(row.get("result_json") or "{}")
    return row


def ensure_allowed_url(url: str, target: dict[str, Any]) -> str:
    normalized = normalize_url(url)
    if not normalized.startswith(target["scope_prefix"].rstrip("/")):
        raise ValidationError("生成的结果 URL 超出授权范围")
    allowlist = target.get("allowlist") or [target["scope_prefix"].rstrip("/") + "*"]
    blocklist = target.get("blocklist") or []
    if not any(fnmatch.fnmatch(normalized, pattern) for pattern in allowlist):
        raise ValidationError("URL 不在授权白名单内")
    if any(fnmatch.fnmatch(normalized, pattern) for pattern in blocklist):
        raise ValidationError("URL 命中扫描黑名单")
    return normalized


def fingerprint(title: str, url: str, parameter: str, cwe: str) -> str:
    raw = "|".join([title, url, parameter, cwe]).encode("utf-8")
    return hashlib.sha256(raw).hexdigest()[:24]


def demo_findings(task_id: str, target: dict[str, Any], scan_type: str) -> list[dict[str, Any]]:
    now = utcnow()
    base = target["scope_prefix"].rstrip("/")
    candidates = [
        ("High", "缺少关键响应头：Content-Security-Policy", f"{base}/login", "response.header.Content-Security-Policy", "响应头未包含 Content-Security-Policy。", "CWE-693", "WASC-15", "配置最小权限 CSP，逐步收敛 script-src、object-src、frame-ancestors。", "baseline"),
        ("Medium", "会话 Cookie 缺少 SameSite 或 Secure 属性", f"{base}/", "Set-Cookie", "检测到会话 Cookie 属性不完整。", "CWE-614", "WASC-13", "为生产环境 Cookie 设置 Secure、HttpOnly、SameSite=Lax/Strict。", "baseline"),
        ("Low", "服务器版本信息暴露", f"{base}/health", "Server", "响应头包含较具体的服务端版本信息。", "CWE-200", "WASC-13", "隐藏或泛化版本信息，并在资产台账中维护真实版本。", "baseline"),
        ("Informational", "发现登录入口", f"{base}/login", "path", "登录路径纳入后续人工验证范围。", "CWE-0", "WASC-0", "确认认证、锁定、审计和错误提示策略符合基线要求。", "baseline"),
    ]
    if scan_type in {"full", "api"}:
        candidates.append(("Medium", "API 响应缺少缓存控制", f"{base}/api/profile", "Cache-Control", "敏感 API 响应未显式禁止缓存。", "CWE-525", "WASC-13", "为认证态 API 增加 Cache-Control: no-store。", "api"))
    if scan_type == "full":
        candidates.append(("Critical", "管理端路径暴露且缺少二次确认", f"{base}/admin", "path", "发现管理端入口，应进入人工授权验证。", "CWE-284", "WASC-01", "限制管理端来源、启用 MFA，并纳入人工复核。", "full"))

    findings = []
    for risk, title, url, parameter, evidence, cwe, wasc, recommendation, phase in candidates:
        checked_url = ensure_allowed_url(url, target)
        fp = fingerprint(title, checked_url, parameter, cwe)
        findings.append(
            {
                "id": new_id("vuln"),
                "task_id": task_id,
                "risk": risk,
                "title": title,
                "url": checked_url,
                "parameter": parameter,
                "evidence": evidence,
                "recommendation": recommendation,
                "status": "pending_fix",
                "retest_count": 0,
                "first_seen": now,
                "last_seen": now,
                "cwe": cwe,
                "wasc": wasc,
                "fingerprint": fp,
                "confidence": "Medium",
                "scan_phase": phase,
                "original_vulnerability_id": None,
            }
        )
    return findings


def risk_trend(old: list[dict[str, Any]], new: list[dict[str, Any]]) -> dict[str, dict[str, int]]:
    result: dict[str, dict[str, int]] = {}
    for risk in RISKS:
        old_count = sum(1 for item in old if item["risk"] == risk)
        new_count = sum(1 for item in new if item["risk"] == risk)
        result[risk] = {"before": old_count, "after": new_count, "delta": new_count - old_count}
    return result


def render_report(task: dict[str, Any], report_type: str) -> str:
    rows = []
    for vuln in task["vulnerabilities"]:
        rows.append(
            "<tr>"
            f"<td>{html.escape(vuln['risk'])}</td>"
            f"<td>{html.escape(vuln['title'])}</td>"
            f"<td>{html.escape(vuln['url'])}</td>"
            f"<td>{html.escape(vuln['parameter'])}</td>"
            f"<td>{html.escape(vuln.get('cwe', ''))}</td>"
            f"<td>{html.escape(vuln.get('wasc', ''))}</td>"
            f"<td>{html.escape(vuln['evidence'])}</td>"
            f"<td>{html.escape(vuln['recommendation'])}</td>"
            f"<td>{html.escape(vuln['status'])}</td>"
            "</tr>"
        )
    comparison = task.get("comparison") or {}
    gate = task.get("ci_gate") or {}
    counts = task["risk_counts"]
    title = {"scan": "扫描报告", "retest": "复测报告", "remediation": "整改报告"}[report_type]
    return f"""<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <title>{html.escape(task['name'])} - {title}</title>
  <style>
    body {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; margin: 32px; color: #1f2937; }}
    h1 {{ font-size: 24px; margin-bottom: 4px; }}
    h2 {{ font-size: 18px; margin-top: 24px; }}
    .meta {{ color: #667085; margin-bottom: 20px; }}
    .counts {{ display: flex; flex-wrap: wrap; gap: 12px; margin: 16px 0; }}
    .pill {{ border: 1px solid #d0d5dd; border-radius: 6px; padding: 8px 12px; }}
    table {{ width: 100%; border-collapse: collapse; font-size: 13px; }}
    th, td {{ border: 1px solid #d0d5dd; padding: 8px; vertical-align: top; overflow-wrap: anywhere; }}
    th {{ background: #f2f4f7; text-align: left; }}
  </style>
</head>
<body>
  <h1>Web 安全{title}</h1>
  <div class="meta">任务：{html.escape(task['name'])} | 目标：{html.escape(task['target_name'])} | 类型：{html.escape(task['scan_type'])} | 导出时间：{html.escape(utcnow())}</div>
  <div class="counts">
    <div class="pill">Critical {counts['Critical']}</div>
    <div class="pill">High {counts['High']}</div>
    <div class="pill">Medium {counts['Medium']}</div>
    <div class="pill">Low {counts['Low']}</div>
    <div class="pill">Info {counts['Informational']}</div>
  </div>
  <h2>差异与门禁</h2>
  <p>新增：{html.escape(str(comparison.get('new_count', 'N/A')))} | 已修复：{html.escape(str(comparison.get('fixed_count', 'N/A')))} | 遗留：{html.escape(str(comparison.get('carried_count', 'N/A')))} | CI 门禁：{html.escape(gate.get('status', '未运行') if isinstance(gate, dict) else '未运行')}</p>
  <h2>漏洞明细</h2>
  <table>
    <thead>
      <tr><th>风险</th><th>漏洞</th><th>URL</th><th>参数</th><th>CWE</th><th>WASC</th><th>证据</th><th>修复建议</th><th>状态</th></tr>
    </thead>
    <tbody>{''.join(rows)}</tbody>
  </table>
  <h2>处置建议</h2>
  <ul>
    <li>仅在授权范围内使用 ZAP 引擎，默认禁用危险动作。</li>
    <li>高危及以上漏洞应先整改，标记已修复待复测，再通过复测关闭。</li>
    <li>CI/CD 门禁建议阻断 High+ 活跃漏洞进入发布流程。</li>
  </ul>
</body>
</html>
"""


class Handler(BaseHTTPRequestHandler):
    server_version = "ZapAuthorizedPlatform/0.2"

    @property
    def store(self) -> Store:
        return self.server.store  # type: ignore[attr-defined]

    def do_GET(self) -> None:
        try:
            self.route_get()
        except Exception as exc:  # noqa: BLE001
            self.handle_error(exc)

    def do_POST(self) -> None:
        try:
            self.route_post()
        except Exception as exc:  # noqa: BLE001
            self.handle_error(exc)

    def route_get(self) -> None:
        parsed = urlparse(self.path)
        path = parsed.path
        query = parse_qs(parsed.query)
        if path == "/":
            return self.send_static("index.html")
        if path.startswith("/static/"):
            return self.send_static(path.removeprefix("/static/"))
        if path == "/api/health":
            return self.send_json(
                {
                    "status": "ok",
                    "env_root": str(self.store.env_root),
                    "db_path": str(self.store.db_path),
                    "report_dir": str(self.store.report_dir),
                    "zap_api_key_file": str(self.store.secret_dir / "zap-api-key"),
                    "network_scan_enabled": False,
                    "max_concurrent_scans": MAX_CONCURRENT_SCANS,
                    "dangerous_actions_default": False,
                    "supported_scan_types": SCAN_TYPES,
                }
            )
        if path == "/api/targets":
            return self.send_json({"targets": self.store.list_targets()})
        if path == "/api/tasks":
            return self.send_json({"tasks": self.store.list_tasks()})
        match = re.fullmatch(r"/api/tasks/([^/]+)", path)
        if match:
            return self.send_json({"task": self.store.get_task(unquote(match.group(1)))})
        if path == "/api/vulnerabilities":
            task_id = query.get("task_id", [None])[0]
            return self.send_json({"vulnerabilities": self.store.list_vulnerabilities(task_id)})
        match = re.fullmatch(r"/reports/([^/]+)", path)
        if match:
            return self.send_report(unquote(match.group(1)))
        self.send_error_json(HTTPStatus.NOT_FOUND, "not_found", "资源不存在")

    def route_post(self) -> None:
        path = urlparse(self.path).path
        body = self.read_json()
        if path == "/api/targets":
            return self.send_json({"target": self.store.create_target(body)}, HTTPStatus.CREATED)
        if path == "/api/tasks":
            return self.send_json({"task": self.store.create_task(body)}, HTTPStatus.CREATED)
        match = re.fullmatch(r"/api/tasks/([^/]+)/cancel", path)
        if match:
            return self.send_json({"task": self.store.cancel_task(unquote(match.group(1)), body)})
        match = re.fullmatch(r"/api/tasks/([^/]+)/retest", path)
        if match:
            return self.send_json({"task": self.store.create_retest_task(unquote(match.group(1)))}, HTTPStatus.CREATED)
        match = re.fullmatch(r"/api/tasks/([^/]+)/compare/([^/]+)", path)
        if match:
            return self.send_json({"comparison": self.store.compare_tasks(unquote(match.group(1)), unquote(match.group(2)))}, HTTPStatus.CREATED)
        match = re.fullmatch(r"/api/tasks/([^/]+)/ci-gate", path)
        if match:
            return self.send_json({"ci_gate": self.store.run_ci_gate(unquote(match.group(1)), body)}, HTTPStatus.CREATED)
        match = re.fullmatch(r"/api/tasks/([^/]+)/export", path)
        if match:
            report_type = str(body.get("report_type") or "scan")
            return self.send_json({"report": self.store.export_report(unquote(match.group(1)), report_type)}, HTTPStatus.CREATED)
        match = re.fullmatch(r"/api/vulnerabilities/([^/]+)/mark-fixed", path)
        if match:
            return self.send_json({"vulnerability": self.store.mark_fixed(unquote(match.group(1)), body)})
        match = re.fullmatch(r"/api/vulnerabilities/([^/]+)/close", path)
        if match:
            return self.send_json({"vulnerability": self.store.close_vulnerability(unquote(match.group(1)), body)})
        match = re.fullmatch(r"/api/vulnerabilities/([^/]+)/retest", path)
        if match:
            return self.send_json({"vulnerability": self.store.mark_retesting(unquote(match.group(1)))})
        self.send_error_json(HTTPStatus.NOT_FOUND, "not_found", "资源不存在")

    def read_json(self) -> dict[str, Any]:
        length = int(self.headers.get("content-length") or 0)
        if length > 1024 * 1024:
            raise ValidationError("请求体过大")
        raw = self.rfile.read(length) if length else b"{}"
        try:
            data = json.loads(raw.decode("utf-8"))
        except json.JSONDecodeError as exc:
            raise ValidationError("JSON 格式错误") from exc
        if not isinstance(data, dict):
            raise ValidationError("请求体必须是对象")
        return data

    def send_static(self, relative: str) -> None:
        safe = (STATIC_DIR / relative).resolve()
        if not str(safe).startswith(str(STATIC_DIR.resolve())) or not safe.is_file():
            self.send_error_json(HTTPStatus.NOT_FOUND, "not_found", "资源不存在")
            return
        types = {".html": "text/html; charset=utf-8", ".css": "text/css; charset=utf-8", ".js": "application/javascript; charset=utf-8"}
        data = safe.read_bytes()
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", types.get(safe.suffix, "application/octet-stream"))
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def send_report(self, filename: str) -> None:
        if "/" in filename or ".." in filename:
            self.send_error_json(HTTPStatus.BAD_REQUEST, "bad_report_name", "报告名称非法")
            return
        path = (self.store.report_dir / filename).resolve()
        if not str(path).startswith(str(self.store.report_dir.resolve())) or not path.is_file():
            self.send_error_json(HTTPStatus.NOT_FOUND, "not_found", "报告不存在")
            return
        data = path.read_bytes()
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Disposition", f"attachment; filename={quote(filename)}")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def send_json(self, payload: dict[str, Any], status: HTTPStatus = HTTPStatus.OK) -> None:
        data = json.dumps(payload, ensure_ascii=False, indent=2).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def send_error_json(self, status: HTTPStatus, code: str, message: str) -> None:
        self.send_json({"error": {"code": code, "message": message}}, status)

    def handle_error(self, exc: Exception) -> None:
        if isinstance(exc, ValidationError):
            self.send_error_json(HTTPStatus.BAD_REQUEST, "validation_error", str(exc))
            return
        logging.exception("request failed")
        self.send_error_json(HTTPStatus.INTERNAL_SERVER_ERROR, "internal_error", "服务异常")

    def log_message(self, fmt: str, *args: Any) -> None:
        logging.info("%s - %s", self.address_string(), fmt % args)


def configure_logging(log_dir: Path) -> None:
    log_dir.mkdir(parents=True, exist_ok=True)
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(message)s",
        handlers=[logging.StreamHandler(sys.stdout), logging.FileHandler(log_dir / "platform.log", encoding="utf-8")],
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Authorized ZAP reporting platform")
    parser.add_argument("--host", default=os.environ.get("ZAP_PLATFORM_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.environ.get("ZAP_PLATFORM_PORT", "18014")))
    parser.add_argument(
        "--env-root",
        default=os.environ.get("ZAP_PLATFORM_ENV_ROOT", str(DEFAULT_ENV_ROOT)),
        help="Environment root for databases, logs, uploads, reports, and secrets.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    store = Store(Path(args.env_root).expanduser().resolve())
    configure_logging(store.log_dir)
    server = ThreadingHTTPServer((args.host, args.port), Handler)
    server.store = store  # type: ignore[attr-defined]
    logging.info("authorized scan platform listening on http://%s:%s", args.host, args.port)
    logging.info("environment root: %s", store.env_root)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        logging.info("shutting down")
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

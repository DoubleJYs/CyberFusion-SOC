#!/usr/bin/env python3
"""Local Sigma detection-rule management platform.

This module intentionally uses only the Python standard library so the Sigma
upstream rule repository can be managed without vendoring runtime dependencies.
Runtime state is written under /Users/zhangjiyan/Environment by default.
"""

from __future__ import annotations

import argparse
import base64
import datetime as dt
import difflib
import hashlib
import html
import json
import os
import re
import shutil
import sqlite3
import subprocess
import sys
import tempfile
import textwrap
import traceback
import zipfile
from functools import lru_cache
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, urlparse


REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_ENV_ROOT = Path(os.environ.get("SIGMA_MANAGER_ENV_ROOT", "/Users/zhangjiyan/Environment"))
ENV_DIRS = {
    "db": DEFAULT_ENV_ROOT / "02-databases" / "05-sigma",
    "uploads": DEFAULT_ENV_ROOT / "13-uploads" / "05-sigma",
    "logs": DEFAULT_ENV_ROOT / "11-logs" / "05-sigma",
    "docs": DEFAULT_ENV_ROOT / "08-docs" / "05-sigma",
}
DB_PATH = ENV_DIRS["db"] / "sigma_manager.sqlite3"
APP_LOG = ENV_DIRS["logs"] / "sigma_manager.log"
RULE_ROOTS = (
    "rules",
    "rules-threat-hunting",
    "rules-emerging-threats",
    "rules-compliance",
    "rules-placeholder",
    "rules-dfir",
    "unsupported",
    "deprecated",
)
LICENSE_NAME = "Detection Rule License (DRL) 1.1"
SUPPORTED_TARGETS = {"splunk", "lucene", "eql", "sentinel-kql"}
RELEASE_STATES = {"draft", "pending_review", "published", "disabled", "archived"}
REQUIRED_RULE_FIELDS = ("title", "id", "status", "level", "logsource", "detection_conditions")
QUALITY_WARN_FIELDS = ("author", "date", "modified", "references")


def utc_now() -> str:
    return dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat()


def ensure_environment() -> None:
    for directory in ENV_DIRS.values():
        directory.mkdir(parents=True, exist_ok=True)
    (ENV_DIRS["uploads"] / "rules").mkdir(parents=True, exist_ok=True)
    (ENV_DIRS["uploads"] / "conversions").mkdir(parents=True, exist_ok=True)
    (ENV_DIRS["uploads"] / "imports").mkdir(parents=True, exist_ok=True)


def log_line(message: str, **fields: Any) -> None:
    ensure_environment()
    record = {"ts": utc_now(), "message": message, **fields}
    with APP_LOG.open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(record, ensure_ascii=False) + "\n")


def db_connect() -> sqlite3.Connection:
    ensure_environment()
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


def ensure_columns(conn: sqlite3.Connection, table: str, columns: dict[str, str]) -> None:
    existing = {row["name"] for row in conn.execute(f"PRAGMA table_info({table})")}
    for name, definition in columns.items():
        if name not in existing:
            conn.execute(f"ALTER TABLE {table} ADD COLUMN {name} {definition}")


def init_db() -> None:
    with db_connect() as conn:
        conn.executescript(
            """
            CREATE TABLE IF NOT EXISTS rules (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sigma_id TEXT UNIQUE,
                title TEXT NOT NULL,
                description TEXT,
                level TEXT,
                status TEXT,
                enabled INTEGER NOT NULL DEFAULT 1,
                category TEXT,
                source_bucket TEXT,
                source_path TEXT,
                source_sha TEXT,
                upstream_sha TEXT,
                license_name TEXT,
                author TEXT,
                date TEXT,
                modified TEXT,
                tags_json TEXT NOT NULL DEFAULT '[]',
                references_json TEXT NOT NULL DEFAULT '[]',
                logsource_json TEXT NOT NULL DEFAULT '{}',
                detection_json TEXT NOT NULL DEFAULT '[]',
                validation_errors_json TEXT NOT NULL DEFAULT '[]',
                quality_findings_json TEXT NOT NULL DEFAULT '[]',
                raw_yaml TEXT NOT NULL,
                quality_score INTEGER NOT NULL DEFAULT 0,
                release_status TEXT NOT NULL DEFAULT 'draft',
                published_at TEXT,
                archived_at TEXT,
                last_rollback_version INTEGER,
                deleted_at TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS rule_versions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                rule_id INTEGER NOT NULL REFERENCES rules(id) ON DELETE CASCADE,
                version_no INTEGER NOT NULL,
                changed_by TEXT NOT NULL,
                change_note TEXT,
                raw_yaml TEXT NOT NULL,
                metadata_json TEXT NOT NULL,
                diff_text TEXT,
                enabled INTEGER,
                release_status TEXT,
                created_at TEXT NOT NULL,
                UNIQUE(rule_id, version_no)
            );

            CREATE TABLE IF NOT EXISTS test_samples (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                rule_id INTEGER NOT NULL REFERENCES rules(id) ON DELETE CASCADE,
                sample_name TEXT NOT NULL,
                sample_type TEXT NOT NULL DEFAULT 'positive',
                event_json TEXT NOT NULL,
                expected_hit INTEGER,
                result_hit INTEGER,
                result_reason TEXT,
                created_at TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS hit_stats (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                rule_id INTEGER NOT NULL REFERENCES rules(id) ON DELETE CASCADE,
                sample_id INTEGER REFERENCES test_samples(id) ON DELETE SET NULL,
                hit INTEGER NOT NULL,
                reason TEXT,
                created_at TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS conversions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                rule_id INTEGER NOT NULL REFERENCES rules(id) ON DELETE CASCADE,
                target TEXT NOT NULL,
                status TEXT NOT NULL,
                query TEXT,
                failure_reason TEXT,
                log_path TEXT,
                created_at TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS platform_adapters (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                target TEXT UNIQUE NOT NULL,
                status TEXT NOT NULL,
                notes TEXT,
                supported_fields_json TEXT NOT NULL DEFAULT '[]',
                updated_at TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS approvals (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                rule_id INTEGER NOT NULL REFERENCES rules(id) ON DELETE CASCADE,
                action TEXT NOT NULL,
                status TEXT NOT NULL,
                requester TEXT,
                reviewer TEXT,
                from_state TEXT,
                to_state TEXT,
                reason TEXT,
                created_at TEXT NOT NULL,
                decided_at TEXT
            );

            CREATE TABLE IF NOT EXISTS audit_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                actor TEXT NOT NULL,
                action TEXT NOT NULL,
                entity_type TEXT NOT NULL,
                entity_id TEXT,
                detail_json TEXT NOT NULL DEFAULT '{}',
                created_at TEXT NOT NULL
            );

            CREATE INDEX IF NOT EXISTS idx_rules_search
                ON rules(title, level, status, category, enabled);
            CREATE INDEX IF NOT EXISTS idx_rules_modified ON rules(modified);
            CREATE INDEX IF NOT EXISTS idx_audit_entity ON audit_logs(entity_type, entity_id);
            """
        )
        ensure_columns(
            conn,
            "rules",
            {
                "references_json": "TEXT NOT NULL DEFAULT '[]'",
                "validation_errors_json": "TEXT NOT NULL DEFAULT '[]'",
                "quality_findings_json": "TEXT NOT NULL DEFAULT '[]'",
                "release_status": "TEXT NOT NULL DEFAULT 'draft'",
                "published_at": "TEXT",
                "archived_at": "TEXT",
                "last_rollback_version": "INTEGER",
            },
        )
        ensure_columns(
            conn,
            "rule_versions",
            {
                "diff_text": "TEXT",
                "enabled": "INTEGER",
                "release_status": "TEXT",
            },
        )
        ensure_columns(conn, "test_samples", {"sample_type": "TEXT NOT NULL DEFAULT 'positive'"})
        ensure_columns(conn, "approvals", {"from_state": "TEXT", "to_state": "TEXT"})
        conn.execute("CREATE INDEX IF NOT EXISTS idx_rules_release ON rules(release_status)")
        for target in sorted(SUPPORTED_TARGETS):
            conn.execute(
                """
                INSERT INTO platform_adapters(target, status, notes, supported_fields_json, updated_at)
                VALUES(?, 'draft', ?, '[]', ?)
                ON CONFLICT(target) DO NOTHING
                """,
                (target, "Built-in lightweight validation adapter.", utc_now()),
            )
    log_line("database initialized", db=str(DB_PATH))


@lru_cache(maxsize=1)
def git_sha() -> str:
    try:
        result = subprocess.run(
            ["git", "-C", str(REPO_ROOT), "rev-parse", "HEAD"],
            check=True,
            capture_output=True,
            text=True,
        )
        return result.stdout.strip()
    except Exception:
        return "unknown"


def relative_source_path(path: Path) -> str:
    resolved = path.resolve()
    try:
        return resolved.relative_to(REPO_ROOT).as_posix()
    except ValueError:
        return str(resolved)


def source_bucket(path: Path) -> str:
    rel = relative_source_path(path)
    if rel.startswith("/"):
        return "external"
    return rel.split("/", 1)[0]


def clean_scalar(value: str) -> str:
    value = value.strip()
    if value in {"", "null", "Null", "NULL", "~"}:
        return ""
    if "#" in value and not value.startswith(("'", '"')):
        value = value.split("#", 1)[0].rstrip()
    if (value.startswith("'") and value.endswith("'")) or (value.startswith('"') and value.endswith('"')):
        return value[1:-1]
    return value


def parse_inline_list(value: str) -> list[str]:
    value = value.strip()
    if not (value.startswith("[") and value.endswith("]")):
        return []
    body = value[1:-1].strip()
    if not body:
        return []
    return [clean_scalar(part.strip()) for part in body.split(",") if part.strip()]


def collect_indented_block(lines: list[str], key: str) -> list[str]:
    start = None
    pattern = re.compile(rf"^{re.escape(key)}:\s*(.*)$")
    for idx, line in enumerate(lines):
        if pattern.match(line):
            start = idx + 1
            break
    if start is None:
        return []
    block: list[str] = []
    for line in lines[start:]:
        if line and not line.startswith((" ", "\t", "-")) and re.match(r"^[A-Za-z_][\w-]*:", line):
            break
        block.append(line.rstrip("\n"))
    return block


def parse_simple_yaml(text: str) -> dict[str, Any]:
    """Parse the Sigma metadata we need without a full YAML dependency."""
    lines = text.splitlines()
    data: dict[str, Any] = {}
    for line in lines:
        if not line or line.startswith((" ", "\t", "-")):
            continue
        match = re.match(r"^([A-Za-z_][\w-]*):\s*(.*)$", line)
        if not match:
            continue
        key, raw_value = match.groups()
        value = clean_scalar(raw_value)
        if value.startswith("[") and value.endswith("]"):
            data[key] = parse_inline_list(value)
        elif value:
            data[key] = value
        else:
            data.setdefault(key, None)

    for list_key in ("tags", "references", "falsepositives"):
        if isinstance(data.get(list_key), list):
            continue
        block = collect_indented_block(lines, list_key)
        values = []
        for line in block:
            match = re.match(r"^\s*-\s+(.+?)\s*$", line)
            if match:
                values.append(clean_scalar(match.group(1)))
        if values:
            data[list_key] = values

    logsource: dict[str, str] = {}
    for line in collect_indented_block(lines, "logsource"):
        match = re.match(r"^\s+([A-Za-z_][\w-]*):\s*(.+?)\s*$", line)
        if match:
            logsource[match.group(1)] = clean_scalar(match.group(2))
    data["logsource"] = logsource

    data["detection_conditions"] = parse_detection_conditions(lines)
    return data


def parse_detection_conditions(lines: list[str]) -> list[dict[str, Any]]:
    conditions: list[dict[str, Any]] = []
    block = collect_indented_block(lines, "detection")
    idx = 0
    while idx < len(block):
        line = block[idx]
        match = re.match(r"^\s{4,}([A-Za-z0-9_.*-]+(?:\|[A-Za-z0-9_]+)?):\s*(.*?)\s*$", line)
        idx += 1
        if not match:
            continue
        key, raw_value = match.groups()
        if key == "condition":
            continue
        field, op = (key.split("|", 1) + ["equals"])[:2] if "|" in key else (key, "equals")
        values: list[str] = []
        if raw_value:
            values.append(clean_scalar(raw_value))
        else:
            while idx < len(block):
                list_match = re.match(r"^\s{8,}-\s+(.+?)\s*$", block[idx])
                if not list_match:
                    break
                values.append(clean_scalar(list_match.group(1)))
                idx += 1
        if values:
            conditions.append({"field": field, "operator": op, "values": values})
    return conditions


def validate_rule_metadata(metadata: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    for key in REQUIRED_RULE_FIELDS:
        value = metadata.get(key)
        if key == "logsource" and not value:
            errors.append("missing required logsource")
        elif key == "detection_conditions" and not value:
            errors.append("missing supported detection selection")
        elif key not in {"logsource", "detection_conditions"} and not value:
            errors.append(f"missing required {key}")
    if metadata.get("id") and not re.match(r"^[A-Za-z0-9_.:-]+$", str(metadata["id"])):
        errors.append("id contains unsupported characters")
    return errors


def quality_findings(metadata: dict[str, Any]) -> list[dict[str, Any]]:
    findings: list[dict[str, Any]] = []
    for key in REQUIRED_RULE_FIELDS:
        value = metadata.get(key)
        if (key in {"logsource", "detection_conditions"} and not value) or (key not in {"logsource", "detection_conditions"} and not value):
            findings.append({"severity": "high", "code": f"missing_{key}", "message": f"Missing required field: {key}"})
    for key in QUALITY_WARN_FIELDS:
        if not metadata.get(key):
            findings.append({"severity": "medium", "code": f"missing_{key}", "message": f"Missing recommended field: {key}"})
    description = str(metadata.get("description") or "").strip()
    if not description or description == "|" or len(description) < 20:
        findings.append({"severity": "medium", "code": "weak_description", "message": "Description is missing, block-only, or too short."})
    refs = metadata.get("references") or []
    if not refs:
        findings.append({"severity": "medium", "code": "no_references", "message": "Rule has no references."})
    status = str(metadata.get("status") or "").lower()
    level = str(metadata.get("level") or "").lower()
    if status in {"deprecated", "unsupported"}:
        findings.append({"severity": "medium", "code": "non_production_status", "message": f"Sigma status is {status}."})
    if level in {"", "informational"}:
        findings.append({"severity": "low", "code": "low_signal_level", "message": "Rule level is empty or informational."})
    return findings


def quality_score(metadata: dict[str, Any]) -> int:
    score = 100
    weights = {"high": 12, "medium": 6, "low": 3}
    for finding in quality_findings(metadata):
        score -= weights.get(str(finding.get("severity")), 4)
    return max(0, min(100, score))


def yaml_validation_error(text: str) -> str | None:
    if "\t" in text:
        return "YAML contains tab indentation; use spaces."
    lines = [line for line in text.splitlines() if line.strip() and not line.lstrip().startswith("#")]
    if not lines:
        return "YAML content is empty."
    if not any(re.match(r"^[A-Za-z_][\w-]*:\s*", line) for line in lines):
        return "No top-level YAML mapping keys were found."
    return None


def metadata_for_path(path: Path) -> dict[str, Any]:
    text = path.read_text(encoding="utf-8", errors="replace")
    yaml_error = yaml_validation_error(text)
    metadata = parse_simple_yaml(text)
    validation_errors = validate_rule_metadata(metadata)
    if yaml_error:
        validation_errors.insert(0, yaml_error)
    metadata["raw_yaml"] = text
    metadata["source_path"] = relative_source_path(path)
    metadata["source_bucket"] = source_bucket(path)
    metadata["source_sha"] = hashlib.sha256(text.encode("utf-8")).hexdigest()
    metadata["upstream_sha"] = git_sha()
    metadata["license_name"] = LICENSE_NAME
    metadata["category"] = infer_category(path, metadata)
    metadata["quality_score"] = quality_score(metadata)
    metadata["quality_findings"] = quality_findings(metadata)
    metadata["validation_errors"] = validation_errors
    metadata["title"] = metadata.get("title") or path.stem
    return metadata


def infer_category(path: Path, metadata: dict[str, Any]) -> str:
    logsource = metadata.get("logsource") or {}
    if logsource.get("product"):
        return str(logsource["product"])
    rel_parts = Path(relative_source_path(path)).parts
    if len(rel_parts) > 1:
        return rel_parts[1]
    return "uncategorized"


def audit(conn: sqlite3.Connection, actor: str, action: str, entity_type: str, entity_id: Any, detail: Any) -> None:
    conn.execute(
        """
        INSERT INTO audit_logs(actor, action, entity_type, entity_id, detail_json, created_at)
        VALUES(?, ?, ?, ?, ?, ?)
        """,
        (actor, action, entity_type, str(entity_id) if entity_id is not None else None, json.dumps(detail, ensure_ascii=False), utc_now()),
    )


def unified_diff_text(before: str, after: str, from_label: str = "previous", to_label: str = "current") -> str:
    if before == after:
        return ""
    return "".join(
        difflib.unified_diff(
            before.splitlines(keepends=True),
            after.splitlines(keepends=True),
            fromfile=from_label,
            tofile=to_label,
        )
    )


def upsert_rule(conn: sqlite3.Connection, metadata: dict[str, Any], actor: str = "importer") -> int:
    now = utc_now()
    sigma_id = metadata.get("id") or metadata["source_sha"][:16]
    row = conn.execute("SELECT id, raw_yaml FROM rules WHERE sigma_id = ?", (sigma_id,)).fetchone()
    params = {
        "sigma_id": sigma_id,
        "title": metadata["title"],
        "description": metadata.get("description") or "",
        "level": metadata.get("level") or "",
        "status": metadata.get("status") or "experimental",
        "category": metadata["category"],
        "source_bucket": metadata["source_bucket"],
        "source_path": metadata["source_path"],
        "source_sha": metadata["source_sha"],
        "upstream_sha": metadata["upstream_sha"],
        "license_name": metadata["license_name"],
        "author": metadata.get("author") or "",
        "date": metadata.get("date") or "",
        "modified": metadata.get("modified") or "",
        "tags_json": json.dumps(metadata.get("tags") or [], ensure_ascii=False),
        "references_json": json.dumps(metadata.get("references") or [], ensure_ascii=False),
        "logsource_json": json.dumps(metadata.get("logsource") or {}, ensure_ascii=False),
        "detection_json": json.dumps(metadata.get("detection_conditions") or [], ensure_ascii=False),
        "validation_errors_json": json.dumps(metadata.get("validation_errors") or [], ensure_ascii=False),
        "quality_findings_json": json.dumps(metadata.get("quality_findings") or [], ensure_ascii=False),
        "raw_yaml": metadata["raw_yaml"],
        "quality_score": metadata["quality_score"],
        "release_status": metadata.get("release_status") or "draft",
        "now": now,
    }
    if row:
        conn.execute(
            """
            UPDATE rules
            SET title = :title,
                description = :description,
                level = :level,
                status = :status,
                category = :category,
                source_bucket = :source_bucket,
                source_path = :source_path,
                source_sha = :source_sha,
                upstream_sha = :upstream_sha,
                license_name = :license_name,
                author = :author,
                date = :date,
                modified = :modified,
                tags_json = :tags_json,
                references_json = :references_json,
                logsource_json = :logsource_json,
                detection_json = :detection_json,
                validation_errors_json = :validation_errors_json,
                quality_findings_json = :quality_findings_json,
                raw_yaml = :raw_yaml,
                quality_score = :quality_score,
                deleted_at = NULL,
                updated_at = :now
            WHERE sigma_id = :sigma_id
            """,
            params,
        )
        rule_id = int(row["id"])
        action = "rule.reimported"
        if row["raw_yaml"] != metadata["raw_yaml"]:
            add_version(
                conn,
                rule_id,
                metadata["raw_yaml"],
                metadata,
                actor=actor,
                note=action,
                previous_raw=row["raw_yaml"],
                release_status=params["release_status"],
            )
    else:
        cursor = conn.execute(
            """
            INSERT INTO rules(
                sigma_id, title, description, level, status, enabled, category,
                source_bucket, source_path, source_sha, upstream_sha, license_name,
                author, date, modified, tags_json, references_json, logsource_json,
                detection_json, validation_errors_json, quality_findings_json,
                raw_yaml, quality_score, release_status, created_at, updated_at
            )
            VALUES(
                :sigma_id, :title, :description, :level, :status, 1, :category,
                :source_bucket, :source_path, :source_sha, :upstream_sha, :license_name,
                :author, :date, :modified, :tags_json, :references_json, :logsource_json,
                :detection_json, :validation_errors_json, :quality_findings_json,
                :raw_yaml, :quality_score, :release_status, :now, :now
            )
            """,
            params,
        )
        rule_id = int(cursor.lastrowid)
        action = "rule.imported"
        add_version(conn, rule_id, metadata["raw_yaml"], metadata, actor=actor, note=action, release_status=params["release_status"])
    audit(conn, actor, action, "rule", rule_id, {"source_path": metadata["source_path"], "sigma_id": sigma_id})
    return rule_id


def add_version(
    conn: sqlite3.Connection,
    rule_id: int,
    raw_yaml: str,
    metadata: dict[str, Any],
    actor: str,
    note: str,
    previous_raw: str = "",
    enabled: bool | None = None,
    release_status: str | None = None,
) -> None:
    current = conn.execute("SELECT COALESCE(MAX(version_no), 0) FROM rule_versions WHERE rule_id = ?", (rule_id,)).fetchone()[0]
    diff_text = unified_diff_text(previous_raw, raw_yaml) if previous_raw else ""
    conn.execute(
        """
        INSERT OR IGNORE INTO rule_versions(rule_id, version_no, changed_by, change_note, raw_yaml, metadata_json, diff_text, enabled, release_status, created_at)
        VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        (
            rule_id,
            int(current) + 1,
            actor,
            note,
            raw_yaml,
            json.dumps(metadata, ensure_ascii=False),
            diff_text,
            int(enabled) if enabled is not None else None,
            release_status,
            utc_now(),
        ),
    )


def import_rules(limit: int | None = None, roots: list[str] | None = None) -> dict[str, Any]:
    init_db()
    selected_roots = roots or list(RULE_ROOTS)
    imported = 0
    failed: list[dict[str, str]] = []
    with db_connect() as conn:
        for root in selected_roots:
            base = REPO_ROOT / root
            if not base.exists():
                continue
            for path in sorted(base.rglob("*.yml")):
                if limit is not None and imported >= limit:
                    break
                try:
                    metadata = metadata_for_path(path)
                    upsert_rule(conn, metadata)
                    imported += 1
                except Exception as exc:  # noqa: BLE001 - collect import failures for the report.
                    failed.append({"path": relative_source_path(path), "error": str(exc)})
                    log_line("rule import failed", path=str(path), error=str(exc))
            if limit is not None and imported >= limit:
                break
    result = {"imported": imported, "failed": failed, "db": str(DB_PATH)}
    log_line("rule import completed", **result)
    return result


def yml_files_under(path: Path) -> list[Path]:
    if path.is_file() and path.suffix.lower() in {".yml", ".yaml"}:
        return [path]
    if path.is_dir():
        return sorted([p for p in path.rglob("*") if p.is_file() and p.suffix.lower() in {".yml", ".yaml"}])
    return []


def safe_extract_zip(zip_path: Path) -> Path:
    ensure_environment()
    target = ENV_DIRS["uploads"] / "imports" / f"{zip_path.stem}-{dt.datetime.now().strftime('%Y%m%d%H%M%S')}"
    target.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(zip_path) as archive:
        for member in archive.infolist():
            member_path = Path(member.filename)
            if member_path.is_absolute() or ".." in member_path.parts:
                raise ValueError(f"unsafe zip member path: {member.filename}")
            if member.is_dir():
                continue
            destination = target / member_path
            destination.parent.mkdir(parents=True, exist_ok=True)
            with archive.open(member) as source, destination.open("wb") as sink:
                shutil.copyfileobj(source, sink)
    return target


def import_path(source: str, limit: int | None = None) -> dict[str, Any]:
    init_db()
    path = Path(source).expanduser()
    if not path.is_absolute():
        path = (REPO_ROOT / path).resolve()
    if not path.exists():
        raise FileNotFoundError(f"import path not found: {source}")
    working_path = safe_extract_zip(path) if path.suffix.lower() == ".zip" else path
    files = yml_files_under(working_path)
    imported = 0
    failed: list[dict[str, str]] = []
    with db_connect() as conn:
        for file_path in files:
            if limit is not None and imported >= limit:
                break
            try:
                metadata = metadata_for_path(file_path)
                errors = metadata.get("validation_errors") or []
                if errors:
                    failed.append({"path": relative_source_path(file_path), "error": "; ".join(errors)})
                    continue
                upsert_rule(conn, metadata, actor="path-importer")
                imported += 1
            except Exception as exc:  # noqa: BLE001
                failed.append({"path": relative_source_path(file_path), "error": str(exc)})
                log_line("path import failed", path=str(file_path), error=str(exc))
    result = {"source": str(path), "expanded_path": str(working_path), "imported": imported, "failed": failed, "db": str(DB_PATH)}
    log_line("path import completed", **result)
    return result


def import_zip_payload(payload: dict[str, Any]) -> dict[str, Any]:
    content = payload.get("content_base64") or ""
    if not content:
        raise ValueError("content_base64 is required for zip payload import")
    filename = Path(payload.get("filename") or f"rules-{utc_now()}.zip").name
    if not filename.endswith(".zip"):
        filename += ".zip"
    ensure_environment()
    zip_path = ENV_DIRS["uploads"] / "imports" / filename
    zip_path.write_bytes(base64.b64decode(content))
    return import_path(str(zip_path), limit=payload.get("limit"))


def row_to_rule(row: sqlite3.Row, include_yaml: bool = False) -> dict[str, Any]:
    item = dict(row)
    item["enabled"] = bool(item["enabled"])
    item["tags"] = json.loads(item.pop("tags_json") or "[]")
    item["references"] = json.loads(item.pop("references_json") or "[]")
    item["logsource"] = json.loads(item.pop("logsource_json") or "{}")
    item["detection"] = json.loads(item.pop("detection_json") or "[]")
    item["validation_errors"] = json.loads(item.pop("validation_errors_json") or "[]")
    item["quality_findings"] = json.loads(item.pop("quality_findings_json") or "[]")
    if not include_yaml:
        item.pop("raw_yaml", None)
    return item


def search_rules(params: dict[str, list[str]]) -> dict[str, Any]:
    limit = min(int(params.get("limit", ["100"])[0]), 500)
    offset = int(params.get("offset", ["0"])[0])
    clauses = ["deleted_at IS NULL"]
    values: list[Any] = []
    q = params.get("q", [""])[0].strip()
    if q:
        clauses.append(
            "(title LIKE ? OR description LIKE ? OR author LIKE ? OR tags_json LIKE ? OR logsource_json LIKE ? OR source_path LIKE ?)"
        )
        like = f"%{q}%"
        values.extend([like] * 6)
    for key in ("level", "status", "category", "release_status"):
        value = params.get(key, [""])[0].strip()
        if value:
            clauses.append(f"{key} = ?")
            values.append(value)
    enabled = params.get("enabled", [""])[0].strip()
    if enabled in {"0", "1"}:
        clauses.append("enabled = ?")
        values.append(int(enabled))
    sql_where = " AND ".join(clauses)
    with db_connect() as conn:
        total = conn.execute(f"SELECT COUNT(*) FROM rules WHERE {sql_where}", values).fetchone()[0]
        rows = conn.execute(
            f"""
            SELECT * FROM rules
            WHERE {sql_where}
            ORDER BY updated_at DESC, id DESC
            LIMIT ? OFFSET ?
            """,
            values + [limit, offset],
        ).fetchall()
    return {"total": total, "limit": limit, "offset": offset, "items": [row_to_rule(row) for row in rows]}


def get_rule(rule_id: int) -> dict[str, Any] | None:
    with db_connect() as conn:
        row = conn.execute("SELECT * FROM rules WHERE id = ? AND deleted_at IS NULL", (rule_id,)).fetchone()
        if not row:
            return None
        rule = row_to_rule(row, include_yaml=True)
        rule["versions"] = [
            dict(v)
            for v in conn.execute(
                """
                SELECT id, version_no, changed_by, change_note, diff_text, enabled, release_status, created_at
                FROM rule_versions
                WHERE rule_id = ?
                ORDER BY version_no DESC
                """,
                (rule_id,),
            ).fetchall()
        ]
        rule["conversions"] = [dict(c) for c in conn.execute("SELECT * FROM conversions WHERE rule_id = ? ORDER BY id DESC LIMIT 20", (rule_id,))]
        rule["samples"] = [dict(s) for s in conn.execute("SELECT * FROM test_samples WHERE rule_id = ? ORDER BY id DESC LIMIT 20", (rule_id,))]
        return rule


def create_or_update_rule(payload: dict[str, Any], rule_id: int | None = None) -> dict[str, Any]:
    init_db()
    raw_yaml = payload.get("raw_yaml") or render_rule_yaml(payload)
    metadata = parse_simple_yaml(raw_yaml)
    validation_errors = validate_rule_metadata(metadata)
    yaml_error = yaml_validation_error(raw_yaml)
    if yaml_error:
        validation_errors.insert(0, yaml_error)
    metadata.update(
        {
            "raw_yaml": raw_yaml,
            "source_path": payload.get("source_path") or f"local/{payload.get('title', 'manual-rule')}.yml",
            "source_bucket": payload.get("source_bucket") or "local",
            "source_sha": hashlib.sha256(raw_yaml.encode("utf-8")).hexdigest(),
            "upstream_sha": git_sha(),
            "license_name": LICENSE_NAME,
            "category": payload.get("category") or payload.get("logsource", {}).get("product", "local"),
            "quality_score": quality_score(metadata),
            "quality_findings": quality_findings(metadata),
            "validation_errors": validation_errors,
            "title": payload.get("title") or metadata.get("title") or "Untitled Sigma Rule",
            "release_status": payload.get("release_status") or "draft",
        }
    )
    actor = payload.get("actor") or "web"
    with db_connect() as conn:
        if rule_id is None:
            new_id = upsert_rule(conn, metadata, actor=actor)
            return {"id": new_id, "action": "created"}
        existing = conn.execute("SELECT id, raw_yaml FROM rules WHERE id = ? AND deleted_at IS NULL", (rule_id,)).fetchone()
        if not existing:
            raise KeyError(f"rule {rule_id} not found")
        metadata["id"] = payload.get("sigma_id") or metadata.get("id") or f"local-{rule_id}"
        conn.execute(
            """
            UPDATE rules
            SET title = ?, description = ?, level = ?, status = ?, category = ?, enabled = ?,
                author = ?, date = ?, modified = ?, tags_json = ?, references_json = ?,
                logsource_json = ?, detection_json = ?, validation_errors_json = ?,
                quality_findings_json = ?, raw_yaml = ?, source_sha = ?, quality_score = ?,
                release_status = ?, updated_at = ?
            WHERE id = ?
            """,
            (
                metadata["title"],
                metadata.get("description") or "",
                metadata.get("level") or "",
                metadata.get("status") or "experimental",
                metadata["category"],
                int(bool(payload.get("enabled", True))),
                metadata.get("author") or "",
                metadata.get("date") or "",
                metadata.get("modified") or "",
                json.dumps(metadata.get("tags") or payload.get("tags") or [], ensure_ascii=False),
                json.dumps(metadata.get("references") or payload.get("references") or [], ensure_ascii=False),
                json.dumps(metadata.get("logsource") or payload.get("logsource") or {}, ensure_ascii=False),
                json.dumps(metadata.get("detection_conditions") or [], ensure_ascii=False),
                json.dumps(metadata.get("validation_errors") or [], ensure_ascii=False),
                json.dumps(metadata.get("quality_findings") or [], ensure_ascii=False),
                raw_yaml,
                metadata["source_sha"],
                metadata["quality_score"],
                metadata["release_status"],
                utc_now(),
                rule_id,
            ),
        )
        add_version(
            conn,
            rule_id,
            raw_yaml,
            metadata,
            actor=actor,
            note=payload.get("change_note") or "manual update",
            previous_raw=existing["raw_yaml"],
            enabled=bool(payload.get("enabled", True)),
            release_status=metadata["release_status"],
        )
        audit(conn, actor, "rule.updated", "rule", rule_id, {"title": metadata["title"]})
    return {"id": rule_id, "action": "updated"}


def render_rule_yaml(payload: dict[str, Any]) -> str:
    title = payload.get("title") or "Untitled Sigma Rule"
    sigma_id = payload.get("sigma_id") or f"local-{hashlib.sha1(title.encode()).hexdigest()[:12]}"
    tags = payload.get("tags") or []
    logsource = payload.get("logsource") or {"product": payload.get("category") or "generic"}
    lines = [
        f"title: {title}",
        f"id: {sigma_id}",
        f"status: {payload.get('status', 'experimental')}",
        f"description: {payload.get('description', 'Managed local Sigma rule.')}",
        f"author: {payload.get('author', 'local')}",
        f"date: {payload.get('date', dt.date.today().isoformat())}",
        f"modified: {payload.get('modified', dt.date.today().isoformat())}",
        "tags:",
    ]
    lines.extend([f"    - {tag}" for tag in tags] or ["    - local.managed"])
    lines.append("references:")
    lines.extend([f"    - {ref}" for ref in payload.get("references", [])] or ["    - local://managed-rule"])
    lines.append("logsource:")
    for key, value in logsource.items():
        lines.append(f"    {key}: {value}")
    lines.extend(
        [
            "detection:",
            "    selection:",
            "        EventID: 1",
            "    condition: selection",
            "falsepositives:",
            "    - Local rule requires analyst review.",
            f"level: {payload.get('level', 'medium')}",
            "",
        ]
    )
    return "\n".join(lines)


def delete_rule(rule_id: int, actor: str = "web") -> dict[str, Any]:
    with db_connect() as conn:
        conn.execute("UPDATE rules SET deleted_at = ?, updated_at = ? WHERE id = ?", (utc_now(), utc_now(), rule_id))
        audit(conn, actor, "rule.deleted", "rule", rule_id, {})
    return {"id": rule_id, "action": "deleted"}


def set_rule_enabled(rule_id: int, enabled: bool, actor: str = "web") -> dict[str, Any]:
    with db_connect() as conn:
        conn.execute("UPDATE rules SET enabled = ?, updated_at = ? WHERE id = ?", (int(enabled), utc_now(), rule_id))
        audit(conn, actor, "rule.enabled_changed", "rule", rule_id, {"enabled": enabled})
    return {"id": rule_id, "enabled": enabled}


def upload_rule(payload: dict[str, Any]) -> dict[str, Any]:
    filename = Path(payload.get("filename") or f"uploaded-{utc_now()}.yml").name
    if not filename.endswith((".yml", ".yaml")):
        filename += ".yml"
    content = payload.get("content") or payload.get("raw_yaml") or ""
    if not content.strip():
        raise ValueError("content is required")
    ensure_environment()
    target = ENV_DIRS["uploads"] / "rules" / filename
    target.write_text(content, encoding="utf-8")
    yaml_error = yaml_validation_error(content)
    metadata = parse_simple_yaml(content)
    validation_errors = validate_rule_metadata(metadata)
    if yaml_error:
        validation_errors.insert(0, yaml_error)
    metadata.update(
        {
            "raw_yaml": content,
            "source_path": str(target),
            "source_bucket": "uploaded",
            "source_sha": hashlib.sha256(content.encode("utf-8")).hexdigest(),
            "upstream_sha": git_sha(),
            "license_name": LICENSE_NAME,
            "category": payload.get("category") or "uploaded",
            "quality_score": quality_score(metadata),
            "quality_findings": quality_findings(metadata),
            "validation_errors": validation_errors,
            "title": metadata.get("title") or target.stem,
            "release_status": payload.get("release_status") or "draft",
        }
    )
    with db_connect() as conn:
        rule_id = upsert_rule(conn, metadata, actor=payload.get("actor") or "uploader")
    return {"id": rule_id, "path": str(target)}


def event_value(event: dict[str, Any], field: str) -> str:
    if field in event:
        return str(event[field])
    lowered = {str(k).lower(): v for k, v in event.items()}
    return str(lowered.get(field.lower(), ""))


def evaluate_conditions(conditions: list[dict[str, Any]], event: dict[str, Any]) -> tuple[bool, str]:
    if not conditions:
        return False, "No supported Sigma selection fields were parsed."
    matched = []
    missed = []
    for condition in conditions:
        value = event_value(event, condition["field"])
        op = condition["operator"]
        values = [str(v) for v in condition["values"]]
        if op == "contains":
            hit = any(candidate.lower() in value.lower() for candidate in values)
        elif op == "endswith":
            hit = any(value.lower().endswith(candidate.lower()) for candidate in values)
        elif op == "startswith":
            hit = any(value.lower().startswith(candidate.lower()) for candidate in values)
        elif op == "re":
            hit = any(re.search(candidate, value) for candidate in values)
        else:
            hit = any(value.lower() == candidate.lower() for candidate in values)
        label = f"{condition['field']}|{op}"
        (matched if hit else missed).append(label)
    if matched:
        return True, "Matched: " + ", ".join(matched)
    return False, "No condition matched. Missed: " + ", ".join(missed[:8])


def test_rule(rule_id: int, payload: dict[str, Any]) -> dict[str, Any]:
    rule = get_rule(rule_id)
    if not rule:
        raise KeyError(f"rule {rule_id} not found")
    event = payload.get("event") or {}
    if isinstance(event, str):
        event = json.loads(event)
    hit, reason = evaluate_conditions(rule["detection"], event)
    with db_connect() as conn:
        cursor = conn.execute(
            """
            INSERT INTO test_samples(rule_id, sample_name, sample_type, event_json, expected_hit, result_hit, result_reason, created_at)
            VALUES(?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                rule_id,
                payload.get("sample_name") or "ad-hoc sample",
                payload.get("sample_type") or ("positive" if payload.get("expected_hit", True) else "negative"),
                json.dumps(event, ensure_ascii=False),
                int(payload["expected_hit"]) if "expected_hit" in payload else None,
                int(hit),
                reason,
                utc_now(),
            ),
        )
        conn.execute(
            "INSERT INTO hit_stats(rule_id, sample_id, hit, reason, created_at) VALUES(?, ?, ?, ?, ?)",
            (rule_id, cursor.lastrowid, int(hit), reason, utc_now()),
        )
        audit(conn, payload.get("actor") or "tester", "rule.tested", "rule", rule_id, {"hit": hit, "reason": reason})
    return {"rule_id": rule_id, "hit": hit, "reason": reason}


def render_condition(condition: dict[str, Any], target: str) -> str:
    field = condition["field"]
    values = [str(v).replace('"', '\\"') for v in condition["values"]]
    op = condition["operator"]
    if target == "splunk":
        if op == "contains":
            return "(" + " OR ".join(f'{field}="*{value}*"' for value in values) + ")"
        if op == "endswith":
            return "(" + " OR ".join(f'{field}="*{value}"' for value in values) + ")"
        return "(" + " OR ".join(f'{field}="{value}"' for value in values) + ")"
    if target == "lucene":
        if op == "contains":
            return "(" + " OR ".join(f'{field}:*{value}*' for value in values) + ")"
        return "(" + " OR ".join(f'{field}:"{value}"' for value in values) + ")"
    if target == "sentinel-kql":
        if op == "contains":
            return "(" + " or ".join(f'{field} contains "{value}"' for value in values) + ")"
        if op == "endswith":
            return "(" + " or ".join(f'{field} endswith "{value}"' for value in values) + ")"
        return "(" + " or ".join(f'{field} == "{value}"' for value in values) + ")"
    if target == "eql":
        return "(" + " or ".join(f'{field} == "{value}"' for value in values) + ")"
    raise ValueError(f"unsupported target {target}")


def convert_rule(rule_id: int, payload: dict[str, Any]) -> dict[str, Any]:
    target = payload.get("target") or "splunk"
    rule = get_rule(rule_id)
    if not rule:
        raise KeyError(f"rule {rule_id} not found")
    ensure_environment()
    log_path = ENV_DIRS["uploads"] / "conversions" / f"rule-{rule_id}-{target}-{dt.datetime.now().strftime('%Y%m%d%H%M%S')}.log"
    status = "success"
    failure_reason = ""
    query = ""
    try:
        if target not in SUPPORTED_TARGETS:
            raise ValueError(f"Unsupported target platform: {target}")
        if not rule["detection"]:
            raise ValueError("No supported detection selections were parsed from YAML.")
        query = " AND ".join(render_condition(condition, target) for condition in rule["detection"])
        log_path.write_text(
            json.dumps(
                {"rule_id": rule_id, "target": target, "status": status, "query": query, "ts": utc_now()},
                ensure_ascii=False,
                indent=2,
            ),
            encoding="utf-8",
        )
    except Exception as exc:  # noqa: BLE001 - failure reason is part of product behavior.
        status = "failed"
        failure_reason = str(exc)
        log_path.write_text(
            json.dumps(
                {"rule_id": rule_id, "target": target, "status": status, "failure_reason": failure_reason, "ts": utc_now()},
                ensure_ascii=False,
                indent=2,
            ),
            encoding="utf-8",
        )
    with db_connect() as conn:
        cursor = conn.execute(
            """
            INSERT INTO conversions(rule_id, target, status, query, failure_reason, log_path, created_at)
            VALUES(?, ?, ?, ?, ?, ?, ?)
            """,
            (rule_id, target, status, query, failure_reason, str(log_path), utc_now()),
        )
        audit(conn, payload.get("actor") or "converter", "rule.converted", "rule", rule_id, {"target": target, "status": status})
    return {
        "id": cursor.lastrowid,
        "rule_id": rule_id,
        "target": target,
        "status": status,
        "query": query,
        "failure_reason": failure_reason,
        "log_path": str(log_path),
    }


def governance_summary() -> dict[str, Any]:
    cutoff = (dt.date.today() - dt.timedelta(days=730)).isoformat()
    with db_connect() as conn:
        totals = dict(
            conn.execute(
                """
                SELECT
                    COUNT(*) AS total,
                    SUM(CASE WHEN enabled = 1 THEN 1 ELSE 0 END) AS enabled,
                    SUM(CASE WHEN quality_score < 70 THEN 1 ELSE 0 END) AS low_quality,
                    SUM(CASE WHEN modified != '' AND modified < ? THEN 1 ELSE 0 END) AS expired
                FROM rules
                WHERE deleted_at IS NULL
                """,
                (cutoff,),
            ).fetchone()
        )
        duplicates = [
            dict(row)
            for row in conn.execute(
                """
                SELECT sigma_id, COUNT(*) AS count, GROUP_CONCAT(source_path, '; ') AS paths
                FROM rules
                WHERE deleted_at IS NULL
                GROUP BY sigma_id
                HAVING COUNT(*) > 1
                ORDER BY count DESC
                LIMIT 50
                """
            )
        ]
        low_quality = [
            row_to_rule(row)
            for row in conn.execute(
                "SELECT * FROM rules WHERE deleted_at IS NULL AND quality_score < 70 ORDER BY quality_score ASC LIMIT 50"
            )
        ]
        expired = [
            row_to_rule(row)
            for row in conn.execute(
                """
                SELECT * FROM rules
                WHERE deleted_at IS NULL AND modified != '' AND modified < ?
                ORDER BY modified ASC
                LIMIT 50
                """,
                (cutoff,),
            )
        ]
        conversions = [dict(row) for row in conn.execute("SELECT status, COUNT(*) AS count FROM conversions GROUP BY status")]
        levels = [dict(row) for row in conn.execute("SELECT COALESCE(NULLIF(level, ''), 'unknown') AS level, COUNT(*) AS count FROM rules WHERE deleted_at IS NULL GROUP BY level ORDER BY count DESC")]
        releases = [dict(row) for row in conn.execute("SELECT release_status, COUNT(*) AS count FROM rules WHERE deleted_at IS NULL GROUP BY release_status ORDER BY count DESC")]
        coverage = [dict(row) for row in conn.execute("SELECT source_bucket, COUNT(*) AS count FROM rules WHERE deleted_at IS NULL GROUP BY source_bucket ORDER BY count DESC")]
        test_totals = dict(
            conn.execute(
                """
                SELECT
                    COUNT(*) AS total,
                    SUM(CASE WHEN expected_hit IS NOT NULL AND expected_hit = result_hit THEN 1 ELSE 0 END) AS passed,
                    SUM(CASE WHEN expected_hit IS NOT NULL AND expected_hit != result_hit THEN 1 ELSE 0 END) AS failed
                FROM test_samples
                """
            ).fetchone()
        )
        change_stats = dict(
            conn.execute(
                """
                SELECT
                    COUNT(*) AS total_versions,
                    SUM(CASE WHEN created_at >= ? THEN 1 ELSE 0 END) AS versions_last_30d
                FROM rule_versions
                """,
                ((dt.datetime.now(dt.timezone.utc) - dt.timedelta(days=30)).replace(microsecond=0).isoformat(),),
            ).fetchone()
        )
    return {
        "totals": totals,
        "duplicate_rules": duplicates,
        "low_quality_rules": low_quality,
        "expired_rules": expired,
        "conversion_status": conversions,
        "level_distribution": levels,
        "release_distribution": releases,
        "coverage_by_source": coverage,
        "test_summary": test_totals,
        "change_summary": change_stats,
        "expiry_cutoff": cutoff,
    }


def create_approval(rule_id: int, payload: dict[str, Any]) -> dict[str, Any]:
    with db_connect() as conn:
        row = conn.execute("SELECT release_status FROM rules WHERE id = ? AND deleted_at IS NULL", (rule_id,)).fetchone()
        if not row:
            raise KeyError(f"rule {rule_id} not found")
        cursor = conn.execute(
            """
            INSERT INTO approvals(rule_id, action, status, requester, reviewer, from_state, to_state, reason, created_at, decided_at)
            VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                rule_id,
                payload.get("action") or "change",
                payload.get("status") or "pending",
                payload.get("requester") or "web",
                payload.get("reviewer") or "",
                row["release_status"],
                payload.get("to_state") or "",
                payload.get("reason") or "",
                utc_now(),
                utc_now() if payload.get("status") in {"approved", "rejected"} else None,
            ),
        )
        audit(conn, payload.get("requester") or "web", "approval.created", "rule", rule_id, {"approval_id": cursor.lastrowid})
    return {"id": cursor.lastrowid, "rule_id": rule_id}


def transition_rule(rule_id: int, payload: dict[str, Any]) -> dict[str, Any]:
    target = payload.get("to_state") or payload.get("release_status")
    if target not in RELEASE_STATES:
        raise ValueError(f"release status must be one of: {', '.join(sorted(RELEASE_STATES))}")
    actor = payload.get("actor") or "workflow"
    with db_connect() as conn:
        row = conn.execute("SELECT id, release_status, raw_yaml FROM rules WHERE id = ? AND deleted_at IS NULL", (rule_id,)).fetchone()
        if not row:
            raise KeyError(f"rule {rule_id} not found")
        previous = row["release_status"]
        enabled = 0 if target in {"disabled", "archived"} else 1
        published_at = utc_now() if target == "published" else None
        archived_at = utc_now() if target == "archived" else None
        conn.execute(
            """
            UPDATE rules
            SET release_status = ?,
                enabled = ?,
                published_at = COALESCE(?, published_at),
                archived_at = COALESCE(?, archived_at),
                updated_at = ?
            WHERE id = ?
            """,
            (target, enabled, published_at, archived_at, utc_now(), rule_id),
        )
        cursor = conn.execute(
            """
            INSERT INTO approvals(rule_id, action, status, requester, reviewer, from_state, to_state, reason, created_at, decided_at)
            VALUES(?, 'lifecycle_transition', ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                rule_id,
                payload.get("approval_status") or ("approved" if target in {"published", "disabled", "archived"} else "pending"),
                actor,
                payload.get("reviewer") or "",
                previous,
                target,
                payload.get("reason") or "",
                utc_now(),
                utc_now(),
            ),
        )
        add_version(
            conn,
            rule_id,
            row["raw_yaml"],
            {"release_status": target, "previous_release_status": previous},
            actor=actor,
            note=f"lifecycle {previous} -> {target}",
            enabled=bool(enabled),
            release_status=target,
        )
        audit(conn, actor, "rule.lifecycle_transition", "rule", rule_id, {"from": previous, "to": target, "approval_id": cursor.lastrowid})
    return {"id": rule_id, "from_state": previous, "to_state": target, "enabled": bool(enabled)}


def rollback_rule(rule_id: int, payload: dict[str, Any]) -> dict[str, Any]:
    version_no = int(payload.get("version_no") or 0)
    if version_no <= 0:
        raise ValueError("version_no is required")
    actor = payload.get("actor") or "rollback"
    with db_connect() as conn:
        current = conn.execute("SELECT raw_yaml, enabled, release_status FROM rules WHERE id = ? AND deleted_at IS NULL", (rule_id,)).fetchone()
        if not current:
            raise KeyError(f"rule {rule_id} not found")
        version = conn.execute("SELECT raw_yaml, metadata_json FROM rule_versions WHERE rule_id = ? AND version_no = ?", (rule_id, version_no)).fetchone()
        if not version:
            raise KeyError(f"version {version_no} not found")
        raw_yaml = version["raw_yaml"]
        metadata = parse_simple_yaml(raw_yaml)
        validation_errors = validate_rule_metadata(metadata)
        metadata.update(
            {
                "raw_yaml": raw_yaml,
                "source_sha": hashlib.sha256(raw_yaml.encode("utf-8")).hexdigest(),
                "quality_score": quality_score(metadata),
                "quality_findings": quality_findings(metadata),
                "validation_errors": validation_errors,
            }
        )
        conn.execute(
            """
            UPDATE rules
            SET title = ?, description = ?, level = ?, status = ?, author = ?, date = ?, modified = ?,
                tags_json = ?, references_json = ?, logsource_json = ?, detection_json = ?,
                validation_errors_json = ?, quality_findings_json = ?, raw_yaml = ?, source_sha = ?,
                quality_score = ?, last_rollback_version = ?, updated_at = ?
            WHERE id = ?
            """,
            (
                metadata.get("title") or f"rule-{rule_id}",
                metadata.get("description") or "",
                metadata.get("level") or "",
                metadata.get("status") or "experimental",
                metadata.get("author") or "",
                metadata.get("date") or "",
                metadata.get("modified") or "",
                json.dumps(metadata.get("tags") or [], ensure_ascii=False),
                json.dumps(metadata.get("references") or [], ensure_ascii=False),
                json.dumps(metadata.get("logsource") or {}, ensure_ascii=False),
                json.dumps(metadata.get("detection_conditions") or [], ensure_ascii=False),
                json.dumps(metadata.get("validation_errors") or [], ensure_ascii=False),
                json.dumps(metadata.get("quality_findings") or [], ensure_ascii=False),
                raw_yaml,
                metadata["source_sha"],
                metadata["quality_score"],
                version_no,
                utc_now(),
                rule_id,
            ),
        )
        add_version(
            conn,
            rule_id,
            raw_yaml,
            metadata,
            actor=actor,
            note=f"rollback to version {version_no}",
            previous_raw=current["raw_yaml"],
            enabled=bool(current["enabled"]),
            release_status=current["release_status"],
        )
        audit(conn, actor, "rule.rolled_back", "rule", rule_id, {"version_no": version_no})
    return {"id": rule_id, "rolled_back_to": version_no}


def export_report() -> dict[str, Any]:
    ensure_environment()
    summary = governance_summary()
    with db_connect() as conn:
        latest = [row_to_rule(row) for row in conn.execute("SELECT * FROM rules WHERE deleted_at IS NULL ORDER BY updated_at DESC LIMIT 20")]
        tests = [dict(row) for row in conn.execute("SELECT hit, COUNT(*) AS count FROM hit_stats GROUP BY hit")]
        conversions = [dict(row) for row in conn.execute("SELECT target, status, COUNT(*) AS count FROM conversions GROUP BY target, status")]
        lifecycle = [dict(row) for row in conn.execute("SELECT release_status, COUNT(*) AS count FROM rules WHERE deleted_at IS NULL GROUP BY release_status")]
    path = ENV_DIRS["docs"] / f"sigma-rule-report-{dt.datetime.now().strftime('%Y%m%d-%H%M%S')}.md"
    test_summary = summary["test_summary"]
    test_rate = 0.0
    if test_summary.get("total"):
        test_rate = (test_summary.get("passed") or 0) / test_summary["total"] * 100
    lines = [
        "# 码研工坊检测规则治理报告",
        "",
        f"- Generated: {utc_now()}",
        f"- Upstream repository: https://github.com/SigmaHQ/sigma",
        f"- Upstream commit SHA: {git_sha()}",
        f"- License preserved: {LICENSE_NAME}",
        f"- Runtime database: {DB_PATH}",
        "",
        "## Governance Summary",
        "",
        f"- Total active rules: {summary['totals'].get('total') or 0}",
        f"- Enabled rules: {summary['totals'].get('enabled') or 0}",
        f"- Low quality rules: {summary['totals'].get('low_quality') or 0}",
        f"- Expired rules: {summary['totals'].get('expired') or 0}",
        f"- Duplicate Sigma IDs: {len(summary['duplicate_rules'])}",
        f"- Test pass rate: {test_rate:.1f}%",
        f"- Version records: {summary['change_summary'].get('total_versions') or 0}",
        f"- Version records in last 30 days: {summary['change_summary'].get('versions_last_30d') or 0}",
        "",
        "## Coverage By Source",
        "",
    ]
    lines.extend([f"- {item['source_bucket']}: {item['count']}" for item in summary["coverage_by_source"]] or ["- No source coverage data."])
    lines.extend(["", "## Level Distribution", ""])
    lines.extend([f"- {item['level']}: {item['count']}" for item in summary["level_distribution"]] or ["- No level distribution data."])
    lines.extend(["", "## Release Lifecycle", ""])
    lines.extend([f"- {item['release_status']}: {item['count']}" for item in lifecycle] or ["- No lifecycle data."])
    lines.extend(
        [
            "",
        "## Test Results",
        "",
        ]
    )
    lines.extend([f"- hit={item['hit']}: {item['count']}" for item in tests] or ["- No rule tests recorded."])
    lines.extend(["", "## Conversion Results", ""])
    lines.extend([f"- {item['target']} / {item['status']}: {item['count']}" for item in conversions] or ["- No conversions recorded."])
    lines.extend(["", "## Recent Rules", ""])
    for rule in latest:
        lines.append(f"- #{rule['id']} {rule['title']} [{rule['level']}/{rule['status']}] {rule['source_path']}")
    lines.extend(["", "## Remaining Governance Work", "", "- Review low quality and expired rules before production enablement.", "- Validate generated conversion queries against the target SIEM dialect before deployment."])
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    log_line("report exported", path=str(path))
    return {"path": str(path), "summary": summary}


INDEX_HTML = r"""<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>码研工坊检测规则治理平台</title>
  <style>
    :root { color-scheme: light; --bg:#f5f7fa; --panel:#ffffff; --ink:#172033; --muted:#667085; --line:#d8dee8; --blue:#1f6feb; --green:#1a7f37; --red:#c9372c; }
    * { box-sizing: border-box; }
    body { margin: 0; font: 14px/1.5 -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; color: var(--ink); background: var(--bg); }
    header { display:flex; align-items:center; justify-content:space-between; gap:16px; padding:16px 22px; border-bottom:1px solid var(--line); background:#0f2744; color:#fff; }
    h1 { margin:0; font-size:20px; letter-spacing:0; }
    main { display:grid; grid-template-columns: minmax(360px, 42%) 1fr; min-height: calc(100vh - 65px); }
    aside { border-right:1px solid var(--line); background:var(--panel); overflow:auto; }
    section { overflow:auto; }
    .toolbar { display:grid; grid-template-columns:1fr 100px 100px 96px; gap:8px; padding:14px; border-bottom:1px solid var(--line); position:sticky; top:0; background:var(--panel); z-index:2; }
    input, select, textarea { width:100%; border:1px solid var(--line); border-radius:6px; padding:8px 10px; font:inherit; background:#fff; }
    button { border:1px solid #b8c6da; background:#fff; color:var(--ink); border-radius:6px; padding:8px 10px; font-weight:600; cursor:pointer; }
    button.primary { background:var(--blue); border-color:var(--blue); color:#fff; }
    button.danger { border-color:#e2a19b; color:var(--red); }
    .row { padding:12px 14px; border-bottom:1px solid var(--line); cursor:pointer; }
    .row:hover, .row.active { background:#eef4ff; }
    .title { font-weight:700; }
    .meta { display:flex; flex-wrap:wrap; gap:6px; margin-top:6px; color:var(--muted); font-size:12px; }
    .pill { border:1px solid var(--line); border-radius:999px; padding:1px 7px; background:#fff; }
    .content { padding:18px; max-width:1120px; }
    .grid { display:grid; grid-template-columns: repeat(4, minmax(110px, 1fr)); gap:10px; margin:12px 0; }
    .metric { background:var(--panel); border:1px solid var(--line); border-radius:8px; padding:12px; }
    .metric b { display:block; font-size:22px; }
    .panel { background:var(--panel); border:1px solid var(--line); border-radius:8px; padding:14px; margin:12px 0; }
    .tabs { display:flex; gap:8px; border-bottom:1px solid var(--line); margin-bottom:12px; }
    .tabs button { border-bottom:0; border-radius:6px 6px 0 0; }
    pre { white-space:pre-wrap; background:#111827; color:#f9fafb; border-radius:8px; padding:14px; overflow:auto; max-height:420px; }
    textarea { min-height:140px; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
    .split { display:grid; grid-template-columns:1fr 1fr; gap:12px; }
    .ok { color:var(--green); }
    .bad { color:var(--red); }
    @media (max-width: 900px) { main { grid-template-columns:1fr; } aside { max-height:48vh; border-right:0; border-bottom:1px solid var(--line); } .toolbar,.split,.grid { grid-template-columns:1fr; } }
  </style>
</head>
<body>
  <header>
    <h1>码研工坊检测规则治理平台</h1>
    <div>
      <button onclick="bulkImport()">导入规则</button>
      <button onclick="exportReport()" class="primary">生成报告</button>
    </div>
  </header>
  <main>
    <aside>
      <div class="toolbar">
        <input id="q" placeholder="搜索名称、标签、作者、logsource" onkeydown="if(event.key==='Enter') loadRules()">
        <select id="level"><option value="">等级</option><option>critical</option><option>high</option><option>medium</option><option>low</option><option>informational</option></select>
        <select id="status"><option value="">状态</option><option>stable</option><option>test</option><option>experimental</option><option>deprecated</option></select>
        <button onclick="loadRules()">检索</button>
      </div>
      <div id="list"></div>
    </aside>
    <section>
      <div class="content">
        <div id="governance" class="grid"></div>
        <div id="detail" class="panel">请先导入或选择一条 Sigma 规则。</div>
      </div>
    </section>
  </main>
<script>
let currentId = null;
async function api(path, options={}) {
  const res = await fetch(path, {headers: {'Content-Type':'application/json'}, ...options});
  if (!res.ok) throw new Error(await res.text());
  return await res.json();
}
function esc(s) { return String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }
async function loadRules() {
  const params = new URLSearchParams({limit: 100, q: q.value, level: level.value, status: status.value});
  const data = await api('/api/rules?' + params.toString());
  list.innerHTML = data.items.map(rule => `
    <div class="row ${rule.id===currentId?'active':''}" onclick="loadDetail(${rule.id})">
      <div class="title">${esc(rule.title)}</div>
      <div class="meta">
        <span class="pill">${esc(rule.level || 'none')}</span><span class="pill">${esc(rule.status || 'none')}</span>
        <span class="pill">${esc(rule.release_status || 'draft')}</span><span class="pill">${rule.enabled ? '启用' : '停用'}</span><span class="pill">质量 ${rule.quality_score}</span>
      </div>
      <div class="meta">${esc(rule.source_path)}</div>
    </div>`).join('');
}
async function loadGovernance() {
  const data = await api('/api/governance');
  governance.innerHTML = `
    <div class="metric"><b>${data.totals.total || 0}</b>规则总数</div>
    <div class="metric"><b>${data.totals.enabled || 0}</b>启用规则</div>
    <div class="metric"><b>${data.totals.low_quality || 0}</b>低质量</div>
    <div class="metric"><b>${data.totals.expired || 0}</b>过期提醒</div>`;
}
async function loadDetail(id) {
  currentId = id;
  await loadRules();
  const rule = await api('/api/rules/' + id);
  detail.innerHTML = `
    <h2>${esc(rule.title)}</h2>
    <div class="meta">
      <span class="pill">${esc(rule.level)}</span><span class="pill">${esc(rule.status)}</span><span class="pill">${rule.enabled ? '启用' : '停用'}</span>
      <span class="pill">发布: ${esc(rule.release_status || 'draft')}</span>
      <span class="pill">author: ${esc(rule.author)}</span><span class="pill">modified: ${esc(rule.modified)}</span>
    </div>
    <p>${esc(rule.description)}</p>
    <div class="tabs">
      <button onclick="showYaml(${id})">YAML 预览</button>
      <button onclick="showTest(${id})">测试样例</button>
      <button onclick="convert(${id}, 'splunk')">转换 Splunk</button>
      <button onclick="convert(${id}, 'sentinel-kql')">转换 KQL</button>
      <button onclick="toggle(${id}, ${rule.enabled ? 0 : 1})">${rule.enabled ? '停用' : '启用'}</button>
      <button onclick="transition(${id}, 'pending_review')">送审</button>
      <button onclick="transition(${id}, 'published')">发布</button>
      <button onclick="transition(${id}, 'archived')">归档</button>
      <button class="danger" onclick="removeRule(${id})">删除</button>
    </div>
    <div class="split">
      <div><h3>分类与标签</h3><p>${esc(rule.category)}</p><p>${rule.tags.map(t=>`<span class="pill">${esc(t)}</span>`).join(' ')}</p><h3>References</h3><ul>${rule.references.map(r=>`<li>${esc(r)}</li>`).join('') || '<li>暂无</li>'}</ul></div>
      <div><h3>Logsource</h3><pre>${esc(JSON.stringify(rule.logsource, null, 2))}</pre></div>
    </div>
    <h3>质量检查</h3>
    <ul>${rule.quality_findings.map(f=>`<li>${esc(f.severity)} / ${esc(f.code)} - ${esc(f.message)}</li>`).join('') || '<li>无质量问题</li>'}</ul>
    <h3>版本记录</h3>
    <ul>${rule.versions.map(v=>`<li>v${v.version_no} ${esc(v.changed_by)} ${esc(v.change_note)} ${esc(v.created_at)}</li>`).join('')}</ul>
    <h3>转换记录</h3>
    <ul>${rule.conversions.map(c=>`<li class="${c.status==='success'?'ok':'bad'}">${esc(c.target)} ${esc(c.status)} ${esc(c.failure_reason || c.query || '')}</li>`).join('') || '<li>暂无</li>'}</ul>
    <div id="work"></div>`;
}
async function showYaml(id) {
  const rule = await api('/api/rules/' + id);
  work.innerHTML = `<pre>${esc(rule.raw_yaml)}</pre>`;
}
function showTest(id) {
  work.innerHTML = `<h3>规则测试样例</h3><textarea id="sample">{\n  "Image": "/usr/bin/osascript",\n  "CommandLine": "osascript -e display dialog"\n}</textarea><p><button class="primary" onclick="runTest(${id})">运行测试</button></p><pre id="testResult"></pre>`;
}
async function runTest(id) {
  const result = await api('/api/rules/' + id + '/test', {method:'POST', body: JSON.stringify({sample_name:'UI sample', event: JSON.parse(sample.value), expected_hit: true})});
  testResult.textContent = JSON.stringify(result, null, 2);
}
async function convert(id, target) {
  const result = await api('/api/rules/' + id + '/convert', {method:'POST', body: JSON.stringify({target})});
  await loadDetail(id);
  work.innerHTML = `<pre>${esc(JSON.stringify(result, null, 2))}</pre>`;
}
async function toggle(id, enabled) {
  await api('/api/rules/' + id + '/enable', {method:'POST', body: JSON.stringify({enabled: Boolean(enabled)})});
  await loadGovernance(); await loadDetail(id);
}
async function transition(id, to_state) {
  await api('/api/rules/' + id + '/transition', {method:'POST', body: JSON.stringify({to_state, actor:'ui', reason:'UI lifecycle action'})});
  await loadGovernance(); await loadDetail(id);
}
async function removeRule(id) {
  if (!confirm('确认删除该规则记录？源 YAML 不会被删除。')) return;
  await api('/api/rules/' + id, {method:'DELETE'});
  currentId = null; await loadRules(); await loadGovernance(); detail.textContent = '规则已删除。';
}
async function bulkImport() {
  const result = await api('/api/import', {method:'POST', body: JSON.stringify({limit: 300})});
  await loadRules(); await loadGovernance();
  detail.innerHTML = `<pre>${esc(JSON.stringify(result, null, 2))}</pre>`;
}
async function exportReport() {
  const result = await api('/api/report', {method:'POST', body: '{}'});
  detail.innerHTML = `<pre>${esc(JSON.stringify(result, null, 2))}</pre>`;
}
loadRules().then(loadGovernance);
</script>
</body>
</html>
"""


class Handler(BaseHTTPRequestHandler):
    server_version = "SigmaManager/0.1"

    def log_message(self, fmt: str, *args: Any) -> None:
        log_line("http", client=self.client_address[0], request=fmt % args)

    def read_json(self) -> dict[str, Any]:
        length = int(self.headers.get("Content-Length") or "0")
        if length <= 0:
            return {}
        raw = self.rfile.read(length).decode("utf-8")
        return json.loads(raw or "{}")

    def send_json(self, data: Any, status: HTTPStatus = HTTPStatus.OK) -> None:
        body = json.dumps(data, ensure_ascii=False, indent=2).encode("utf-8")
        self.send_response(status.value)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def send_text(self, text: str, content_type: str = "text/html; charset=utf-8") -> None:
        body = text.encode("utf-8")
        self.send_response(HTTPStatus.OK.value)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def handle_error(self, exc: Exception) -> None:
        log_line("request failed", path=self.path, error=str(exc), traceback=traceback.format_exc())
        status = HTTPStatus.NOT_FOUND if isinstance(exc, KeyError) else HTTPStatus.BAD_REQUEST
        self.send_json({"error": str(exc)}, status)

    def do_GET(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler API.
        try:
            init_db()
            parsed = urlparse(self.path)
            path = parsed.path
            if path == "/":
                self.send_text(INDEX_HTML)
                return
            if path == "/api/rules":
                self.send_json(search_rules(parse_qs(parsed.query)))
                return
            match = re.match(r"^/api/rules/(\d+)$", path)
            if match:
                rule = get_rule(int(match.group(1)))
                if not rule:
                    raise KeyError("rule not found")
                self.send_json(rule)
                return
            if path == "/api/governance":
                self.send_json(governance_summary())
                return
            match = re.match(r"^/api/rules/(\d+)/quality$", path)
            if match:
                rule = get_rule(int(match.group(1)))
                if not rule:
                    raise KeyError("rule not found")
                self.send_json({"rule_id": rule["id"], "quality_score": rule["quality_score"], "validation_errors": rule["validation_errors"], "quality_findings": rule["quality_findings"]})
                return
            if path == "/api/audit":
                with db_connect() as conn:
                    rows = [dict(row) for row in conn.execute("SELECT * FROM audit_logs ORDER BY id DESC LIMIT 200")]
                self.send_json({"items": rows})
                return
            self.send_json({"error": "not found"}, HTTPStatus.NOT_FOUND)
        except Exception as exc:  # noqa: BLE001
            self.handle_error(exc)

    def do_POST(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler API.
        try:
            init_db()
            parsed = urlparse(self.path)
            path = parsed.path
            payload = self.read_json()
            if path == "/api/import":
                if payload.get("zip_content_base64") or payload.get("content_base64"):
                    self.send_json(import_zip_payload({"content_base64": payload.get("zip_content_base64") or payload.get("content_base64"), "filename": payload.get("filename"), "limit": payload.get("limit")}))
                elif payload.get("path"):
                    self.send_json(import_path(payload["path"], limit=payload.get("limit")))
                else:
                    self.send_json(import_rules(limit=payload.get("limit"), roots=payload.get("roots")))
                return
            if path == "/api/rules":
                self.send_json(create_or_update_rule(payload), HTTPStatus.CREATED)
                return
            if path == "/api/uploads":
                self.send_json(upload_rule(payload), HTTPStatus.CREATED)
                return
            match = re.match(r"^/api/rules/(\d+)/enable$", path)
            if match:
                self.send_json(set_rule_enabled(int(match.group(1)), bool(payload.get("enabled")), payload.get("actor") or "web"))
                return
            match = re.match(r"^/api/rules/(\d+)/test$", path)
            if match:
                self.send_json(test_rule(int(match.group(1)), payload))
                return
            match = re.match(r"^/api/rules/(\d+)/convert$", path)
            if match:
                self.send_json(convert_rule(int(match.group(1)), payload))
                return
            match = re.match(r"^/api/rules/(\d+)/approvals$", path)
            if match:
                self.send_json(create_approval(int(match.group(1)), payload), HTTPStatus.CREATED)
                return
            match = re.match(r"^/api/rules/(\d+)/transition$", path)
            if match:
                self.send_json(transition_rule(int(match.group(1)), payload))
                return
            match = re.match(r"^/api/rules/(\d+)/rollback$", path)
            if match:
                self.send_json(rollback_rule(int(match.group(1)), payload))
                return
            if path == "/api/report":
                self.send_json(export_report(), HTTPStatus.CREATED)
                return
            self.send_json({"error": "not found"}, HTTPStatus.NOT_FOUND)
        except Exception as exc:  # noqa: BLE001
            self.handle_error(exc)

    def do_PUT(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler API.
        try:
            init_db()
            match = re.match(r"^/api/rules/(\d+)$", urlparse(self.path).path)
            if not match:
                self.send_json({"error": "not found"}, HTTPStatus.NOT_FOUND)
                return
            self.send_json(create_or_update_rule(self.read_json(), int(match.group(1))))
        except Exception as exc:  # noqa: BLE001
            self.handle_error(exc)

    def do_DELETE(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler API.
        try:
            init_db()
            match = re.match(r"^/api/rules/(\d+)$", urlparse(self.path).path)
            if not match:
                self.send_json({"error": "not found"}, HTTPStatus.NOT_FOUND)
                return
            self.send_json(delete_rule(int(match.group(1))))
        except Exception as exc:  # noqa: BLE001
            self.handle_error(exc)


def serve(host: str, port: int) -> None:
    init_db()
    address = (host, port)
    httpd = ThreadingHTTPServer(address, Handler)
    print(f"Sigma manager running at http://{host}:{port}")
    print(f"Runtime database: {DB_PATH}")
    log_line("server started", host=host, port=port)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping server.")
    finally:
        httpd.server_close()
        log_line("server stopped", host=host, port=port)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Sigma detection-rule management platform")
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("init", help="create Environment runtime directories and SQLite schema")
    import_parser = sub.add_parser("import", help="import Sigma YAML rules into the management database")
    import_parser.add_argument("--limit", type=int, default=None)
    import_parser.add_argument("--roots", nargs="*", default=None)
    import_path_parser = sub.add_parser("import-path", help="import one YAML file, a directory, or a zip archive")
    import_path_parser.add_argument("path")
    import_path_parser.add_argument("--limit", type=int, default=None)
    serve_parser = sub.add_parser("serve", help="start local web/API server")
    serve_parser.add_argument("--host", default="127.0.0.1")
    serve_parser.add_argument("--port", type=int, default=8055)
    report_parser = sub.add_parser("report", help="export a Markdown governance report")
    report_parser.add_argument("--json", action="store_true")
    args = parser.parse_args(argv)

    if args.command == "init":
        init_db()
        print(json.dumps({"db": str(DB_PATH), "env_dirs": {k: str(v) for k, v in ENV_DIRS.items()}}, ensure_ascii=False, indent=2))
    elif args.command == "import":
        print(json.dumps(import_rules(limit=args.limit, roots=args.roots), ensure_ascii=False, indent=2))
    elif args.command == "import-path":
        print(json.dumps(import_path(args.path, limit=args.limit), ensure_ascii=False, indent=2))
    elif args.command == "serve":
        serve(args.host, args.port)
    elif args.command == "report":
        result = export_report()
        if args.json:
            print(json.dumps(result, ensure_ascii=False, indent=2))
        else:
            print(result["path"])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

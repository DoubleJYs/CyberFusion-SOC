#!/usr/bin/env python3
"""Local Zeek log traffic behavior analysis platform.

The tool intentionally uses only Python standard library modules so it can run
beside the Zeek source tree without downloading dependencies. Runtime data is
kept under /Users/zhangjiyan/Environment by default.
"""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import html
import ipaddress
import json
import os
import shutil
import sqlite3
import sys
import textwrap
from collections import Counter
from hashlib import sha256
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, urlencode, urlparse
from urllib.request import Request, urlopen


DEFAULT_ENV_ROOT = Path(os.environ.get("ZEEK_PLATFORM_ENV", "/Users/zhangjiyan/Environment"))
APP_KEY = "03-zeek"
LOG_TYPES = ("conn", "dns", "http", "ssl", "notice")


def env_paths(env_root: Path = DEFAULT_ENV_ROOT) -> dict[str, Path]:
    return {
        "db_dir": env_root / "02-databases" / APP_KEY,
        "log_dir": env_root / "11-logs" / APP_KEY,
        "cache_dir": env_root / "10-cache" / APP_KEY,
        "upload_dir": env_root / "13-uploads" / APP_KEY,
        "doc_dir": env_root / "08-docs" / APP_KEY,
    }


def ensure_env(env_root: Path = DEFAULT_ENV_ROOT) -> dict[str, Path]:
    paths = env_paths(env_root)
    for path in paths.values():
        path.mkdir(parents=True, exist_ok=True)
    return paths


def db_path(env_root: Path = DEFAULT_ENV_ROOT, create: bool = True) -> Path:
    paths = ensure_env(env_root) if create else env_paths(env_root)
    return paths["db_dir"] / "zeek_traffic.sqlite3"


def connect(db: Path) -> sqlite3.Connection:
    db.parent.mkdir(parents=True, exist_ok=True)
    con = sqlite3.connect(db)
    con.row_factory = sqlite3.Row
    con.execute("PRAGMA foreign_keys=ON")
    return con


SCHEMA = """
CREATE TABLE IF NOT EXISTS imports (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  log_type TEXT NOT NULL,
  source_path TEXT NOT NULL,
  imported_at TEXT NOT NULL,
  row_count INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS import_dedupe (
  source_hash TEXT NOT NULL,
  log_type TEXT NOT NULL,
  import_id INTEGER NOT NULL,
  source_path TEXT NOT NULL,
  imported_at TEXT NOT NULL,
  PRIMARY KEY(source_hash, log_type),
  FOREIGN KEY(import_id) REFERENCES imports(id)
);

CREATE TABLE IF NOT EXISTS import_failures (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  source_path TEXT NOT NULL,
  log_type TEXT,
  failed_at TEXT NOT NULL,
  retry_count INTEGER NOT NULL DEFAULT 0,
  resolved INTEGER NOT NULL DEFAULT 0,
  error TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS conn_logs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  import_id INTEGER,
  ts REAL, uid TEXT, src_ip TEXT, src_port INTEGER, dst_ip TEXT, dst_port INTEGER,
  proto TEXT, service TEXT, duration REAL, orig_bytes INTEGER, resp_bytes INTEGER,
  conn_state TEXT, local_orig TEXT, local_resp TEXT, missed_bytes INTEGER,
  history TEXT, orig_pkts INTEGER, orig_ip_bytes INTEGER, resp_pkts INTEGER,
  resp_ip_bytes INTEGER,
  FOREIGN KEY(import_id) REFERENCES imports(id)
);

CREATE TABLE IF NOT EXISTS dns_logs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  import_id INTEGER,
  ts REAL, uid TEXT, src_ip TEXT, src_port INTEGER, dst_ip TEXT, dst_port INTEGER,
  proto TEXT, trans_id INTEGER, query TEXT, qclass TEXT, qclass_name TEXT,
  qtype TEXT, qtype_name TEXT, rcode TEXT, rcode_name TEXT, aa TEXT, tc TEXT,
  rd TEXT, ra TEXT, z TEXT, answers TEXT, ttls TEXT, rejected TEXT,
  FOREIGN KEY(import_id) REFERENCES imports(id)
);

CREATE TABLE IF NOT EXISTS http_logs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  import_id INTEGER,
  ts REAL, uid TEXT, src_ip TEXT, src_port INTEGER, dst_ip TEXT, dst_port INTEGER,
  trans_depth INTEGER, method TEXT, host TEXT, uri TEXT, referrer TEXT,
  version TEXT, user_agent TEXT, request_body_len INTEGER, response_body_len INTEGER,
  status_code INTEGER, status_msg TEXT, info_code INTEGER, info_msg TEXT,
  tags TEXT, username TEXT, password TEXT, proxied TEXT, orig_fuids TEXT,
  orig_filenames TEXT, orig_mime_types TEXT, resp_fuids TEXT, resp_filenames TEXT,
  resp_mime_types TEXT,
  FOREIGN KEY(import_id) REFERENCES imports(id)
);

CREATE TABLE IF NOT EXISTS ssl_logs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  import_id INTEGER,
  ts REAL, uid TEXT, src_ip TEXT, src_port INTEGER, dst_ip TEXT, dst_port INTEGER,
  version TEXT, cipher TEXT, curve TEXT, server_name TEXT, resumed TEXT,
  last_alert TEXT, next_protocol TEXT, established TEXT, cert_chain_fuids TEXT,
  client_cert_chain_fuids TEXT, subject TEXT, issuer TEXT, client_subject TEXT,
  client_issuer TEXT, validation_status TEXT,
  FOREIGN KEY(import_id) REFERENCES imports(id)
);

CREATE TABLE IF NOT EXISTS notice_logs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  import_id INTEGER,
  ts REAL, uid TEXT, src_ip TEXT, dst_ip TEXT, note TEXT, msg TEXT, sub TEXT,
  src_port INTEGER, dst_port INTEGER, actions TEXT, email_dest TEXT, suppress_for REAL,
  dropped TEXT, remote_location_country_code TEXT, remote_location_region TEXT,
  remote_location_city TEXT, remote_location_latitude REAL, remote_location_longitude REAL,
  FOREIGN KEY(import_id) REFERENCES imports(id)
);

CREATE TABLE IF NOT EXISTS anomalies (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  category TEXT NOT NULL,
  severity TEXT NOT NULL,
  score INTEGER NOT NULL,
  entity TEXT NOT NULL,
  detail TEXT NOT NULL,
  first_seen REAL,
  last_seen REAL,
  source_table TEXT NOT NULL,
  source_id INTEGER
);

CREATE TABLE IF NOT EXISTS normalized_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  event_time REAL,
  source TEXT NOT NULL,
  event_type TEXT NOT NULL,
  severity TEXT NOT NULL,
  src_ip TEXT,
  dst_ip TEXT,
  entity TEXT,
  title TEXT NOT NULL,
  detail TEXT NOT NULL,
  risk_score INTEGER NOT NULL,
  raw_ref TEXT
);

CREATE TABLE IF NOT EXISTS traffic_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  import_id INTEGER,
  log_type TEXT NOT NULL,
  ts REAL,
  uid TEXT,
  src_ip TEXT,
  src_port INTEGER,
  dst_ip TEXT,
  dst_port INTEGER,
  proto TEXT,
  service TEXT,
  duration REAL,
  bytes INTEGER,
  domain TEXT,
  uri TEXT,
  status TEXT,
  raw_table TEXT NOT NULL,
  raw_id INTEGER NOT NULL,
  fingerprint TEXT NOT NULL UNIQUE,
  FOREIGN KEY(import_id) REFERENCES imports(id)
);

CREATE TABLE IF NOT EXISTS audit_logs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  created_at TEXT NOT NULL,
  action TEXT NOT NULL,
  target TEXT NOT NULL,
  detail TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_conn_ts ON conn_logs(ts);
CREATE INDEX IF NOT EXISTS idx_conn_src ON conn_logs(src_ip);
CREATE INDEX IF NOT EXISTS idx_conn_dst ON conn_logs(dst_ip);
CREATE INDEX IF NOT EXISTS idx_dns_query ON dns_logs(query);
CREATE INDEX IF NOT EXISTS idx_dns_ts ON dns_logs(ts);
CREATE INDEX IF NOT EXISTS idx_http_ts ON http_logs(ts);
CREATE INDEX IF NOT EXISTS idx_ssl_ts ON ssl_logs(ts);
CREATE INDEX IF NOT EXISTS idx_notice_ts ON notice_logs(ts);
CREATE INDEX IF NOT EXISTS idx_anom_category ON anomalies(category);
CREATE INDEX IF NOT EXISTS idx_traffic_ts ON traffic_events(ts);
CREATE INDEX IF NOT EXISTS idx_traffic_src ON traffic_events(src_ip);
CREATE INDEX IF NOT EXISTS idx_traffic_dst ON traffic_events(dst_ip);
CREATE INDEX IF NOT EXISTS idx_traffic_domain ON traffic_events(domain);
CREATE INDEX IF NOT EXISTS idx_traffic_status ON traffic_events(status);
CREATE INDEX IF NOT EXISTS idx_import_failures_resolved ON import_failures(resolved);
"""


def init_db(db: Path) -> None:
    with connect(db) as con:
        con.executescript(SCHEMA)


def clean_scalar(value: str | None) -> str | None:
    if value is None or value == "-" or value == "":
        return None
    return value


def to_float(value: str | None) -> float | None:
    value = clean_scalar(value)
    if value is None:
        return None
    try:
        return float(value)
    except ValueError:
        return None


def to_int(value: str | None) -> int | None:
    value = clean_scalar(value)
    if value is None:
        return None
    try:
        return int(float(value))
    except ValueError:
        return None


def infer_log_type(path: Path) -> str:
    name = path.name.lower()
    for log_type in LOG_TYPES:
        if name == f"{log_type}.log" or name.startswith(f"{log_type}.") or f".{log_type}." in name:
            return log_type
    raise ValueError(f"Cannot infer Zeek log type from {path.name}")


def file_sha256(path: Path) -> str:
    digest = sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def parse_zeek_log(path: Path) -> tuple[list[str], list[dict[str, str]]]:
    fields: list[str] | None = None
    rows: list[dict[str, str]] = []
    with path.open("r", encoding="utf-8", errors="replace") as fh:
        for line in fh:
            line = line.rstrip("\n")
            if not line:
                continue
            if line.startswith("#fields"):
                fields = line.split("\t")[1:]
                continue
            if line.startswith("#"):
                continue
            if fields is None:
                raise ValueError(f"{path} is missing #fields header")
            values = line.split("\t")
            if len(values) < len(fields):
                values.extend(["-"] * (len(fields) - len(values)))
            rows.append(dict(zip(fields, values)))
    if fields is None:
        raise ValueError(f"{path} is missing Zeek #fields metadata")
    return fields, rows


def get_any(row: dict[str, str], *names: str) -> str | None:
    for name in names:
        if name in row:
            return clean_scalar(row[name])
    return None


def convert_row(log_type: str, row: dict[str, str]) -> dict[str, Any]:
    common = {
        "ts": to_float(row.get("ts")),
        "uid": clean_scalar(row.get("uid")),
        "src_ip": get_any(row, "id.orig_h", "src_ip", "src"),
        "src_port": to_int(get_any(row, "id.orig_p", "src_port", "src_p")),
        "dst_ip": get_any(row, "id.resp_h", "dst_ip", "dst"),
        "dst_port": to_int(get_any(row, "id.resp_p", "dst_port", "dst_p")),
    }
    if log_type == "conn":
        return common | {
            "proto": clean_scalar(row.get("proto")),
            "service": clean_scalar(row.get("service")),
            "duration": to_float(row.get("duration")),
            "orig_bytes": to_int(row.get("orig_bytes")),
            "resp_bytes": to_int(row.get("resp_bytes")),
            "conn_state": clean_scalar(row.get("conn_state")),
            "local_orig": clean_scalar(row.get("local_orig")),
            "local_resp": clean_scalar(row.get("local_resp")),
            "missed_bytes": to_int(row.get("missed_bytes")),
            "history": clean_scalar(row.get("history")),
            "orig_pkts": to_int(row.get("orig_pkts")),
            "orig_ip_bytes": to_int(row.get("orig_ip_bytes")),
            "resp_pkts": to_int(row.get("resp_pkts")),
            "resp_ip_bytes": to_int(row.get("resp_ip_bytes")),
        }
    if log_type == "dns":
        return common | {
            "proto": clean_scalar(row.get("proto")),
            "trans_id": to_int(row.get("trans_id")),
            "query": clean_scalar(row.get("query")),
            "qclass": clean_scalar(row.get("qclass")),
            "qclass_name": clean_scalar(row.get("qclass_name")),
            "qtype": clean_scalar(row.get("qtype")),
            "qtype_name": clean_scalar(row.get("qtype_name")),
            "rcode": clean_scalar(row.get("rcode")),
            "rcode_name": clean_scalar(row.get("rcode_name")),
            "aa": clean_scalar(row.get("AA")),
            "tc": clean_scalar(row.get("TC")),
            "rd": clean_scalar(row.get("RD")),
            "ra": clean_scalar(row.get("RA")),
            "z": clean_scalar(row.get("Z")),
            "answers": clean_scalar(row.get("answers")),
            "ttls": clean_scalar(row.get("TTLs")),
            "rejected": clean_scalar(row.get("rejected")),
        }
    if log_type == "http":
        return common | {
            "trans_depth": to_int(row.get("trans_depth")),
            "method": clean_scalar(row.get("method")),
            "host": clean_scalar(row.get("host")),
            "uri": clean_scalar(row.get("uri")),
            "referrer": clean_scalar(row.get("referrer")),
            "version": clean_scalar(row.get("version")),
            "user_agent": clean_scalar(row.get("user_agent")),
            "request_body_len": to_int(row.get("request_body_len")),
            "response_body_len": to_int(row.get("response_body_len")),
            "status_code": to_int(row.get("status_code")),
            "status_msg": clean_scalar(row.get("status_msg")),
            "info_code": to_int(row.get("info_code")),
            "info_msg": clean_scalar(row.get("info_msg")),
            "tags": clean_scalar(row.get("tags")),
            "username": clean_scalar(row.get("username")),
            "password": clean_scalar(row.get("password")),
            "proxied": clean_scalar(row.get("proxied")),
            "orig_fuids": clean_scalar(row.get("orig_fuids")),
            "orig_filenames": clean_scalar(row.get("orig_filenames")),
            "orig_mime_types": clean_scalar(row.get("orig_mime_types")),
            "resp_fuids": clean_scalar(row.get("resp_fuids")),
            "resp_filenames": clean_scalar(row.get("resp_filenames")),
            "resp_mime_types": clean_scalar(row.get("resp_mime_types")),
        }
    if log_type == "ssl":
        return common | {
            "version": clean_scalar(row.get("version")),
            "cipher": clean_scalar(row.get("cipher")),
            "curve": clean_scalar(row.get("curve")),
            "server_name": clean_scalar(row.get("server_name")),
            "resumed": clean_scalar(row.get("resumed")),
            "last_alert": clean_scalar(row.get("last_alert")),
            "next_protocol": clean_scalar(row.get("next_protocol")),
            "established": clean_scalar(row.get("established")),
            "cert_chain_fuids": clean_scalar(row.get("cert_chain_fuids")),
            "client_cert_chain_fuids": clean_scalar(row.get("client_cert_chain_fuids")),
            "subject": clean_scalar(row.get("subject")),
            "issuer": clean_scalar(row.get("issuer")),
            "client_subject": clean_scalar(row.get("client_subject")),
            "client_issuer": clean_scalar(row.get("client_issuer")),
            "validation_status": clean_scalar(row.get("validation_status")),
        }
    if log_type == "notice":
        return {
            "ts": to_float(row.get("ts")),
            "uid": clean_scalar(row.get("uid")),
            "src_ip": get_any(row, "id.orig_h", "src", "src_ip"),
            "dst_ip": get_any(row, "id.resp_h", "dst", "dst_ip"),
            "note": clean_scalar(row.get("note")),
            "msg": clean_scalar(row.get("msg")),
            "sub": clean_scalar(row.get("sub")),
            "src_port": to_int(get_any(row, "id.orig_p", "p", "src_port")),
            "dst_port": to_int(get_any(row, "id.resp_p", "dst_port")),
            "actions": clean_scalar(row.get("actions")),
            "email_dest": clean_scalar(row.get("email_dest")),
            "suppress_for": to_float(row.get("suppress_for")),
            "dropped": clean_scalar(row.get("dropped")),
            "remote_location_country_code": clean_scalar(row.get("remote_location.country_code")),
            "remote_location_region": clean_scalar(row.get("remote_location.region")),
            "remote_location_city": clean_scalar(row.get("remote_location.city")),
            "remote_location_latitude": to_float(row.get("remote_location.latitude")),
            "remote_location_longitude": to_float(row.get("remote_location.longitude")),
        }
    raise ValueError(log_type)


def table_for(log_type: str) -> str:
    if log_type not in LOG_TYPES:
        raise ValueError(f"Unsupported log type: {log_type}")
    return f"{log_type}_logs"


def audit(db: Path, action: str, target: str, detail: dict[str, Any] | str) -> None:
    payload = detail if isinstance(detail, str) else json.dumps(detail, ensure_ascii=False, sort_keys=True)
    with connect(db) as con:
        con.execute(
            "INSERT INTO audit_logs(created_at, action, target, detail) VALUES (?, ?, ?, ?)",
            (dt.datetime.now(dt.timezone.utc).isoformat(), action, target, payload),
        )
        con.commit()


def import_log(db: Path, source: Path, copy_to_uploads: bool = True, env_root: Path = DEFAULT_ENV_ROOT) -> tuple[str, int]:
    init_db(db)
    log_type = infer_log_type(source)
    source_hash = file_sha256(source)
    with connect(db) as con:
        prior = con.execute(
            "SELECT import_id, source_path FROM import_dedupe WHERE source_hash=? AND log_type=?",
            (source_hash, log_type),
        ).fetchone()
    if prior:
        audit(db, "import_skip_duplicate", log_type, {"source": str(source), "prior_import_id": prior["import_id"]})
        return log_type, 0

    _, raw_rows = parse_zeek_log(source)
    rows = [convert_row(log_type, row) for row in raw_rows]
    upload_source = str(source)
    if copy_to_uploads:
        paths = ensure_env(env_root)
        target = paths["upload_dir"] / source.name
        if source.resolve() != target.resolve():
            shutil.copy2(source, target)
        upload_source = str(target)
    with connect(db) as con:
        cur = con.execute(
            "INSERT INTO imports(log_type, source_path, imported_at, row_count) VALUES (?, ?, ?, ?)",
            (log_type, upload_source, dt.datetime.now(dt.timezone.utc).isoformat(), len(rows)),
        )
        import_id = cur.lastrowid
        if rows:
            keys = list(rows[0].keys())
            placeholders = ", ".join(["?"] * (len(keys) + 1))
            cols = ", ".join(["import_id"] + keys)
            sql = f"INSERT INTO {table_for(log_type)}({cols}) VALUES ({placeholders})"
            con.executemany(sql, [(import_id, *[row.get(key) for key in keys]) for row in rows])
            insert_standard_events(con, import_id, log_type)
        con.execute(
            "INSERT OR REPLACE INTO import_dedupe(source_hash, log_type, import_id, source_path, imported_at) VALUES (?, ?, ?, ?, ?)",
            (source_hash, log_type, import_id, upload_source, dt.datetime.now(dt.timezone.utc).isoformat()),
        )
        con.commit()
    analyze(db)
    audit(db, "import_log", log_type, {"source": upload_source, "rows": len(rows)})
    return log_type, len(rows)


def traffic_fingerprint(log_type: str, row: sqlite3.Row) -> str:
    raw = "|".join(str(row[key] if row[key] is not None else "") for key in (
        "ts", "uid", "src_ip", "src_port", "dst_ip", "dst_port", "domain", "uri", "status", "raw_id"
    ))
    return sha256(f"{log_type}|{raw}".encode("utf-8")).hexdigest()


def insert_standard_events(con: sqlite3.Connection, import_id: int, log_type: str) -> None:
    table = table_for(log_type)
    if log_type == "conn":
        sql = """
            SELECT id AS raw_id, import_id, ts, uid, src_ip, src_port, dst_ip, dst_port,
                   proto, service, duration,
                   COALESCE(orig_bytes, 0) + COALESCE(resp_bytes, 0) AS bytes,
                   NULL AS domain, NULL AS uri, conn_state AS status
            FROM conn_logs WHERE import_id=?
        """
    elif log_type == "dns":
        sql = """
            SELECT id AS raw_id, import_id, ts, uid, src_ip, src_port, dst_ip, dst_port,
                   proto, 'dns' AS service, NULL AS duration, NULL AS bytes,
                   query AS domain, NULL AS uri, rcode_name AS status
            FROM dns_logs WHERE import_id=?
        """
    elif log_type == "http":
        sql = """
            SELECT id AS raw_id, import_id, ts, uid, src_ip, src_port, dst_ip, dst_port,
                   'tcp' AS proto, 'http' AS service, NULL AS duration,
                   COALESCE(request_body_len, 0) + COALESCE(response_body_len, 0) AS bytes,
                   host AS domain, uri, CAST(status_code AS TEXT) AS status
            FROM http_logs WHERE import_id=?
        """
    elif log_type == "ssl":
        sql = """
            SELECT id AS raw_id, import_id, ts, uid, src_ip, src_port, dst_ip, dst_port,
                   'tcp' AS proto, 'ssl' AS service, NULL AS duration, NULL AS bytes,
                   server_name AS domain, NULL AS uri,
                   COALESCE(validation_status, established) AS status
            FROM ssl_logs WHERE import_id=?
        """
    elif log_type == "notice":
        sql = """
            SELECT id AS raw_id, import_id, ts, uid, src_ip, src_port, dst_ip, dst_port,
                   NULL AS proto, 'notice' AS service, NULL AS duration, NULL AS bytes,
                   sub AS domain, NULL AS uri, note AS status
            FROM notice_logs WHERE import_id=?
        """
    else:
        raise ValueError(log_type)

    rows = con.execute(sql, (import_id,)).fetchall()
    for row in rows:
        payload = dict(row)
        payload["log_type"] = log_type
        payload["raw_table"] = table
        payload["fingerprint"] = traffic_fingerprint(log_type, row)
        con.execute(
            """
            INSERT OR IGNORE INTO traffic_events(
              import_id, log_type, ts, uid, src_ip, src_port, dst_ip, dst_port, proto,
              service, duration, bytes, domain, uri, status, raw_table, raw_id, fingerprint
            ) VALUES (
              :import_id, :log_type, :ts, :uid, :src_ip, :src_port, :dst_ip, :dst_port,
              :proto, :service, :duration, :bytes, :domain, :uri, :status, :raw_table,
              :raw_id, :fingerprint
            )
            """,
            payload,
        )


def record_import_failure(db: Path, source: Path, error: Exception, log_type: str | None = None) -> None:
    init_db(db)
    with connect(db) as con:
        prior = con.execute(
            "SELECT id, retry_count FROM import_failures WHERE source_path=? AND resolved=0 ORDER BY id DESC LIMIT 1",
            (str(source),),
        ).fetchone()
        if prior:
            con.execute(
                "UPDATE import_failures SET failed_at=?, retry_count=?, error=? WHERE id=?",
                (dt.datetime.now(dt.timezone.utc).isoformat(), int(prior["retry_count"]) + 1, str(error), prior["id"]),
            )
        else:
            con.execute(
                "INSERT INTO import_failures(source_path, log_type, failed_at, retry_count, resolved, error) VALUES (?, ?, ?, 0, 0, ?)",
                (str(source), log_type, dt.datetime.now(dt.timezone.utc).isoformat(), str(error)),
            )
        con.commit()
    audit(db, "import_failure", log_type or "unknown", {"source": str(source), "error": str(error)})


def import_failures(db: Path, unresolved_only: bool = True) -> list[dict[str, Any]]:
    init_db(db)
    clause = "WHERE resolved=0" if unresolved_only else ""
    with connect(db) as con:
        return [dict(row) for row in con.execute(
            f"SELECT * FROM import_failures {clause} ORDER BY id DESC"
        ).fetchall()]


def count(con: sqlite3.Connection, table: str, where: str = "1=1", params: tuple[Any, ...] = ()) -> int:
    return int(con.execute(f"SELECT COUNT(*) FROM {table} WHERE {where}", params).fetchone()[0])


def top_values(con: sqlite3.Connection, table: str, column: str, limit: int = 8) -> list[sqlite3.Row]:
    return con.execute(
        f"""
        SELECT {column} AS value, COUNT(*) AS total
        FROM {table}
        WHERE {column} IS NOT NULL AND {column} != ''
        GROUP BY {column}
        ORDER BY total DESC, value
        LIMIT ?
        """,
        (limit,),
    ).fetchall()


def overview(db: Path) -> dict[str, Any]:
    init_db(db)
    with connect(db) as con:
        return {
            "counts": {
                "connections": count(con, "conn_logs"),
                "dns": count(con, "dns_logs"),
                "http": count(con, "http_logs"),
                "ssl": count(con, "ssl_logs"),
                "notice": count(con, "notice_logs"),
                "anomalies": count(con, "anomalies"),
            },
            "top_src_ip": [dict(row) for row in top_values(con, "conn_logs", "src_ip")],
            "top_dst_ip": [dict(row) for row in top_values(con, "conn_logs", "dst_ip")],
            "top_domain": [dict(row) for row in top_values(con, "traffic_events", "domain")],
            "recent_anomalies": [dict(row) for row in con.execute(
                "SELECT * FROM anomalies ORDER BY score DESC, id DESC LIMIT 10"
            ).fetchall()],
        }


def is_public_ip(value: str | None) -> bool:
    if not value:
        return False
    try:
        ip = ipaddress.ip_address(value)
    except ValueError:
        return False
    return not (ip.is_private or ip.is_loopback or ip.is_multicast or ip.is_unspecified)


def severity_for(score: int) -> str:
    if score >= 80:
        return "high"
    if score >= 50:
        return "medium"
    return "low"


def add_anomaly(
    anomalies: list[dict[str, Any]],
    category: str,
    score: int,
    entity: str,
    detail: str,
    source_table: str,
    source_id: int | None = None,
    first_seen: float | None = None,
    last_seen: float | None = None,
) -> None:
    anomalies.append({
        "category": category,
        "severity": severity_for(score),
        "score": score,
        "entity": entity,
        "detail": detail,
        "first_seen": first_seen,
        "last_seen": last_seen,
        "source_table": source_table,
        "source_id": source_id,
    })


def analyze(db: Path) -> int:
    init_db(db)
    anomalies: list[dict[str, Any]] = []
    with connect(db) as con:
        for row in con.execute(
            "SELECT id, ts, src_ip, query, rcode_name, answers FROM dns_logs WHERE query IS NOT NULL"
        ):
            query = row["query"] or ""
            labels = [part for part in query.split(".") if part]
            if row["rcode_name"] in {"NXDOMAIN", "SERVFAIL", "REFUSED"}:
                add_anomaly(anomalies, "abnormal_dns", 65, query, f"DNS response code {row['rcode_name']}", "dns_logs", row["id"], row["ts"], row["ts"])
            if len(query) > 80 or any(len(label) > 35 for label in labels):
                add_anomaly(anomalies, "abnormal_dns", 70, query, "Long or high-entropy-looking domain", "dns_logs", row["id"], row["ts"], row["ts"])
            if not row["answers"] and row["answers"] is not None:
                add_anomaly(anomalies, "abnormal_dns", 45, query, "DNS query returned an empty answers field", "dns_logs", row["id"], row["ts"], row["ts"])

        for row in con.execute(
            "SELECT id, ts, src_ip, dst_ip, dst_port, proto, duration, conn_state FROM conn_logs"
        ):
            if row["duration"] is not None and row["duration"] >= 1800:
                add_anomaly(anomalies, "long_connection", 75, f"{row['src_ip']}->{row['dst_ip']}", f"Duration {row['duration']} seconds", "conn_logs", row["id"], row["ts"], row["ts"])
            if row["dst_port"] and row["dst_port"] not in {53, 80, 123, 443, 587, 993, 995, 22}:
                score = 55 if row["dst_port"] < 1024 else 40
                add_anomaly(anomalies, "abnormal_port", score, f"{row['dst_ip']}:{row['dst_port']}", f"Uncommon destination port over {row['proto'] or 'unknown'}", "conn_logs", row["id"], row["ts"], row["ts"])
            if row["conn_state"] in {"S0", "REJ", "RSTOS0"}:
                add_anomaly(anomalies, "connection_failure", 45, f"{row['src_ip']}->{row['dst_ip']}", f"Connection state {row['conn_state']}", "conn_logs", row["id"], row["ts"], row["ts"])

        domain_counts = Counter()
        for row in con.execute("SELECT host FROM http_logs WHERE host IS NOT NULL"):
            domain_counts[row["host"]] += 1
        for row in con.execute("SELECT query FROM dns_logs WHERE query IS NOT NULL"):
            domain_counts[row["query"]] += 1
        for domain, total in domain_counts.items():
            if total == 1 and domain and "." in domain:
                add_anomaly(anomalies, "rare_domain", 35, domain, "Domain appears once in current dataset", "dns_logs")

        for row in con.execute(
            "SELECT id, ts, src_ip, dst_ip, host, uri, status_code FROM http_logs WHERE status_code IS NOT NULL"
        ):
            if row["status_code"] >= 500:
                score = 70 if row["status_code"] in {500, 502, 503, 504} else 55
                add_anomaly(anomalies, "http_high_risk_status", score, row["host"] or row["dst_ip"] or "unknown", f"HTTP {row['status_code']} {row['uri'] or ''}".strip(), "http_logs", row["id"], row["ts"], row["ts"])
            elif row["status_code"] in {401, 403, 407, 429}:
                add_anomaly(anomalies, "http_high_risk_status", 50, row["host"] or row["dst_ip"] or "unknown", f"HTTP {row['status_code']} {row['uri'] or ''}".strip(), "http_logs", row["id"], row["ts"], row["ts"])

        for row in con.execute("SELECT id, ts, src_ip, dst_ip, note, msg FROM notice_logs"):
            add_anomaly(anomalies, "notice", 80, row["note"] or "notice", row["msg"] or "Zeek notice event", "notice_logs", row["id"], row["ts"], row["ts"])

        con.execute("DELETE FROM anomalies")
        con.execute("DELETE FROM normalized_events")
        for item in anomalies:
            cur = con.execute(
                """
                INSERT INTO anomalies(category, severity, score, entity, detail, first_seen, last_seen, source_table, source_id)
                VALUES (:category, :severity, :score, :entity, :detail, :first_seen, :last_seen, :source_table, :source_id)
                """,
                item,
            )
            con.execute(
                """
                INSERT INTO normalized_events(event_time, source, event_type, severity, src_ip, dst_ip, entity, title, detail, risk_score, raw_ref)
                VALUES (?, 'zeek', ?, ?, NULL, NULL, ?, ?, ?, ?, ?)
                """,
                (
                    item.get("first_seen"),
                    item["category"],
                    item["severity"],
                    item["entity"],
                    item["category"].replace("_", " ").title(),
                    item["detail"],
                    item["score"],
                    f"{item['source_table']}:{item.get('source_id') or cur.lastrowid}",
                ),
            )
        con.commit()
    return len(anomalies)


def ts_to_text(ts: Any) -> str:
    if ts is None:
        return ""
    try:
        return dt.datetime.fromtimestamp(float(ts), dt.timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")
    except (ValueError, OSError, TypeError):
        return str(ts)


def query_table(db: Path, log_type: str, params: dict[str, str], limit: int = 50) -> tuple[list[dict[str, Any]], int]:
    init_db(db)
    table = table_for(log_type)
    where = []
    values: list[Any] = []
    ip = params.get("ip")
    if ip:
        where.append("(src_ip LIKE ? OR dst_ip LIKE ?)")
        values.extend([f"%{ip}%", f"%{ip}%"])
    start = params.get("start")
    if start:
        where.append("ts >= ?")
        values.append(float(start))
    end = params.get("end")
    if end:
        where.append("ts <= ?")
        values.append(float(end))
    if log_type == "dns" and params.get("query"):
        where.append("query LIKE ?")
        values.append(f"%{params['query']}%")
    if log_type == "http" and params.get("host"):
        where.append("host LIKE ?")
        values.append(f"%{params['host']}%")
    clause = " AND ".join(where) if where else "1=1"
    page = max(int(params.get("page") or 1), 1)
    offset = (page - 1) * limit
    with connect(db) as con:
        total = count(con, table, clause, tuple(values))
        rows = [dict(row) for row in con.execute(
            f"SELECT * FROM {table} WHERE {clause} ORDER BY ts DESC, id DESC LIMIT ? OFFSET ?",
            (*values, limit, offset),
        ).fetchall()]
    return rows, total


def query_events(db: Path, params: dict[str, str], limit: int = 100) -> tuple[list[dict[str, Any]], int]:
    init_db(db)
    where = []
    values: list[Any] = []
    if params.get("ip"):
        where.append("(src_ip LIKE ? OR dst_ip LIKE ?)")
        values.extend([f"%{params['ip']}%", f"%{params['ip']}%"])
    if params.get("domain"):
        where.append("domain LIKE ?")
        values.append(f"%{params['domain']}%")
    if params.get("proto"):
        where.append("proto = ?")
        values.append(params["proto"])
    if params.get("service"):
        where.append("service = ?")
        values.append(params["service"])
    if params.get("status"):
        where.append("status LIKE ?")
        values.append(f"%{params['status']}%")
    if params.get("log_type"):
        where.append("log_type = ?")
        values.append(params["log_type"])
    if params.get("start"):
        where.append("ts >= ?")
        values.append(float(params["start"]))
    if params.get("end"):
        where.append("ts <= ?")
        values.append(float(params["end"]))
    clause = " AND ".join(where) if where else "1=1"
    page = max(int(params.get("page") or 1), 1)
    offset = (page - 1) * limit
    with connect(db) as con:
        total = count(con, "traffic_events", clause, tuple(values))
        rows = [dict(row) for row in con.execute(
            f"SELECT * FROM traffic_events WHERE {clause} ORDER BY ts DESC, id DESC LIMIT ? OFFSET ?",
            (*values, limit, offset),
        ).fetchall()]
    return rows, total


def export_csv(db: Path, log_type: str, target: Path, params: dict[str, str] | None = None) -> Path:
    rows, _ = query_table(db, log_type, params or {}, limit=100000)
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("w", newline="", encoding="utf-8") as fh:
        if not rows:
            fh.write("")
            audit(db, "export_csv", log_type, {"target": str(target), "rows": 0, "filters": params or {}})
            return target
        writer = csv.DictWriter(fh, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)
    audit(db, "export_csv", log_type, {"target": str(target), "rows": len(rows), "filters": params or {}})
    return target


def asset_graph(db: Path) -> list[dict[str, Any]]:
    init_db(db)
    with connect(db) as con:
        return [dict(row) for row in con.execute(
            """
            SELECT src_ip, dst_ip, COUNT(*) AS connections,
                   SUM(COALESCE(orig_bytes, 0) + COALESCE(resp_bytes, 0)) AS bytes,
                   MAX(ts) AS last_seen
            FROM conn_logs
            WHERE src_ip IS NOT NULL AND dst_ip IS NOT NULL
            GROUP BY src_ip, dst_ip
            ORDER BY connections DESC, bytes DESC
            LIMIT 200
            """
        ).fetchall()]


def risk_scores(db: Path) -> list[dict[str, Any]]:
    init_db(db)
    with connect(db) as con:
        return [dict(row) for row in con.execute(
            """
            SELECT entity, MAX(severity) AS severity, SUM(score) AS score, COUNT(*) AS findings
            FROM anomalies
            GROUP BY entity
            ORDER BY score DESC, findings DESC, entity
            LIMIT 100
            """
        ).fetchall()]


def soc_priority(severity: str) -> str:
    return {"high": "CRITICAL", "medium": "WARNING", "low": "NOTICE"}.get(severity, "INFO")


def soc_events(db: Path) -> list[dict[str, Any]]:
    init_db(db)
    with connect(db) as con:
        rows = [dict(row) for row in con.execute(
            "SELECT * FROM normalized_events ORDER BY risk_score DESC, id DESC"
        ).fetchall()]
    events = []
    for row in rows:
        event_time = ts_to_text(row["event_time"]).replace(" UTC", "Z") if row["event_time"] else dt.datetime.now(dt.timezone.utc).isoformat()
        event = {
            "id": f"zeek-{row['id']}",
            "time": event_time,
            "source": "zeek",
            "priority": soc_priority(row["severity"]),
            "rule": f"Zeek {row['event_type']}",
            "output": row["detail"],
            "hostname": "zeek-log-import",
            "tags": ["zeek", "network", row["event_type"]],
            "output_fields": {
                "evt.source": "zeek",
                "evt.type": row["event_type"],
                "src.ip": row["src_ip"],
                "dst.ip": row["dst_ip"],
                "zeek.entity": row["entity"],
                "zeek.title": row["title"],
                "zeek.raw_ref": row["raw_ref"],
                "risk.score": row["risk_score"],
            },
        }
        events.append(event)
    return events


def export_soc(db: Path, target: Path) -> Path:
    target.parent.mkdir(parents=True, exist_ok=True)
    events = soc_events(db)
    target.write_text(json.dumps({"events": events}, ensure_ascii=False, indent=2), encoding="utf-8")
    audit(db, "export_soc", "normalized_events", {"target": str(target), "events": len(events)})
    return target


def push_soc(db: Path, url: str) -> dict[str, Any]:
    events = soc_events(db)
    payload = json.dumps({"events": events}, ensure_ascii=False).encode("utf-8")
    request = Request(url, data=payload, headers={"Content-Type": "application/json"}, method="POST")
    with urlopen(request, timeout=10) as response:
        body = response.read().decode("utf-8")
    try:
        result = json.loads(body)
    except json.JSONDecodeError:
        result = {"status": "ok", "body": body}
    audit(db, "push_soc", url, {"events": len(events), "result": result})
    return result


def export_report(db: Path, target: Path, kind: str = "all") -> Path:
    data = overview(db)
    graph = asset_graph(db)
    risks = risk_scores(db)
    target.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "generated_at": dt.datetime.now(dt.timezone.utc).isoformat(),
        "overview": data,
        "asset_graph": graph,
        "risk_scores": risks,
        "report_kind": kind,
    }
    if kind not in {"all", "daily", "anomaly", "asset"}:
        raise ValueError(f"Unsupported report kind: {kind}")
    if target.suffix.lower() == ".json":
        target.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    else:
        lines = [
            "# 码研工坊网络流量行为分析报告",
            "",
            f"Generated: {payload['generated_at']}",
            f"Report kind: {kind}",
            "",
        ]
        if kind in {"all", "daily"}:
            lines.extend(["", "## Traffic Daily Overview", ""])
            for key, value in data["counts"].items():
                lines.append(f"- {key}: {value}")
            lines.extend(["", "## Top Source IP", ""])
            lines.extend([f"- {row['value']}: {row['total']}" for row in data["top_src_ip"]] or ["- none"])
            lines.extend(["", "## Top Destination IP", ""])
            lines.extend([f"- {row['value']}: {row['total']}" for row in data["top_dst_ip"]] or ["- none"])
            lines.extend(["", "## Top Domains", ""])
            lines.extend([f"- {row['value']}: {row['total']}" for row in data["top_domain"]] or ["- none"])
        if kind in {"all", "anomaly"}:
            lines.extend(["", "## Risk Scores", "", "## Abnormal Connection Report", ""])
            lines.extend([f"- {row['entity']}: {row['score']} ({row['findings']} findings)" for row in risks] or ["- none"])
            lines.extend(["", "## Recent Anomalies", ""])
            lines.extend([f"- {row['category']} {row['severity']} {row['entity']}: {row['detail']}" for row in data["recent_anomalies"]] or ["- none"])
        if kind in {"all", "asset"}:
            lines.extend(["", "## Asset Communication Report", ""])
            lines.extend([f"- {row['src_ip']} -> {row['dst_ip']}: {row['connections']} connections, {row['bytes'] or 0} bytes" for row in graph[:30]] or ["- none"])
        target.write_text("\n".join(lines) + "\n", encoding="utf-8")
    audit(db, "export_report", "report", {"target": str(target), "kind": kind})
    return target


def init_demo_logs(env_root: Path = DEFAULT_ENV_ROOT) -> list[Path]:
    paths = ensure_env(env_root)
    sample_dir = paths["log_dir"] / "demo"
    sample_dir.mkdir(parents=True, exist_ok=True)
    samples = {
        "conn.log": """#separator \\x09
#set_separator	,
#empty_field	(empty)
#unset_field	-
#path	conn
#open	2026-05-31-00-00-00
#fields	ts	uid	id.orig_h	id.orig_p	id.resp_h	id.resp_p	proto	service	duration	orig_bytes	resp_bytes	conn_state	local_orig	local_resp	missed_bytes	history	orig_pkts	orig_ip_bytes	resp_pkts	resp_ip_bytes
#types	time	string	addr	port	addr	port	enum	string	interval	count	count	string	bool	bool	count	string	count	count	count	count
1772352001.100	C1	10.1.10.5	53533	93.184.216.34	443	tcp	ssl	32.1	932	4012	SF	-	-	0	ShADadFf	12	1200	11	4300
1772352010.200	C2	10.1.10.8	49152	203.0.113.45	4444	tcp	-	2400.5	120	80	S0	-	-	0	S	3	180	0	0
1772352020.300	C3	10.1.10.5	53534	8.8.8.8	53	udp	dns	0.04	70	180	SF	-	-	0	Dd	1	98	1	208
#close	2026-05-31-00-01-00
""",
        "dns.log": """#separator \\x09
#set_separator	,
#empty_field	(empty)
#unset_field	-
#path	dns
#fields	ts	uid	id.orig_h	id.orig_p	id.resp_h	id.resp_p	proto	trans_id	query	qclass	qclass_name	qtype	qtype_name	rcode	rcode_name	AA	TC	RD	RA	Z	answers	TTLs	rejected
#types	time	string	addr	port	addr	port	enum	count	string	count	string	count	string	count	string	bool	bool	bool	bool	count	vector[string]	vector[interval]	bool
1772352020.300	C3	10.1.10.5	53534	8.8.8.8	53	udp	1001	example.com	1	C_INTERNET	1	A	0	NOERROR	F	F	T	T	0	93.184.216.34	300.0	F
1772352040.000	C4	10.1.10.8	53000	8.8.8.8	53	udp	1002	aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.bad-domain.test	1	C_INTERNET	1	A	3	NXDOMAIN	F	F	T	T	0	-	-	F
#close	2026-05-31-00-01-00
""",
        "http.log": """#separator \\x09
#set_separator	,
#empty_field	(empty)
#unset_field	-
#path	http
#fields	ts	uid	id.orig_h	id.orig_p	id.resp_h	id.resp_p	trans_depth	method	host	uri	referrer	version	user_agent	request_body_len	response_body_len	status_code	status_msg	info_code	info_msg	tags	username	password	proxied	orig_fuids	orig_filenames	orig_mime_types	resp_fuids	resp_filenames	resp_mime_types
#types	time	string	addr	port	addr	port	count	string	string	string	string	string	string	count	count	count	string	count	string	set[enum]	string	string	set[string]	vector[string]	vector[string]	vector[string]	vector[string]	vector[string]	vector[string]
1772352030.000	H1	10.1.10.5	52000	93.184.216.34	80	1	GET	example.com	/index.html	-	1.1	Mozilla/5.0	0	1256	200	OK	-	-	-	-	-	-	-	-	-	-	-	-
1772352050.000	H2	10.1.10.8	52001	203.0.113.45	8080	1	GET	rare.internal.test	/admin	-	1.1	curl/8.0	0	50	500	Internal Server Error	-	-	-	-	-	-	-	-	-	-	-	-
#close	2026-05-31-00-01-00
""",
        "ssl.log": """#separator \\x09
#set_separator	,
#empty_field	(empty)
#unset_field	-
#path	ssl
#fields	ts	uid	id.orig_h	id.orig_p	id.resp_h	id.resp_p	version	cipher	curve	server_name	resumed	last_alert	next_protocol	established	cert_chain_fuids	client_cert_chain_fuids	subject	issuer	client_subject	client_issuer	validation_status
#types	time	string	addr	port	addr	port	string	string	string	string	bool	string	string	bool	vector[string]	vector[string]	string	string	string	string	string
1772352001.100	C1	10.1.10.5	53533	93.184.216.34	443	TLSv12	TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256	secp256r1	example.com	F	-	h2	T	F1	-	CN=example.com	CN=Example CA	-	-	ok
#close	2026-05-31-00-01-00
""",
        "notice.log": """#separator \\x09
#set_separator	,
#empty_field	(empty)
#unset_field	-
#path	notice
#fields	ts	uid	id.orig_h	id.orig_p	id.resp_h	id.resp_p	note	msg	sub	actions	email_dest	suppress_for	dropped
#types	time	string	addr	port	addr	port	string	string	string	set[enum]	set[string]	interval	bool
1772352060.000	N1	10.1.10.8	49152	203.0.113.45	4444	Scan::Port_Scan	Possible scan against uncommon service	203.0.113.45	Notice::ACTION_LOG	-	3600.0	F
#close	2026-05-31-00-01-00
""",
    }
    written = []
    for name, content in samples.items():
        path = sample_dir / name
        path.write_text(content, encoding="utf-8")
        written.append(path)
    return written


def html_page(title: str, body: str) -> bytes:
    nav = " ".join([
        '<a href="/">总览</a>',
        '<a href="/events">多日志查询</a>',
        *[f'<a href="/logs/{kind}">{kind}</a>' for kind in ("conn", "dns", "http", "ssl")],
        '<a href="/anomalies">异常</a>',
        '<a href="/assets">资产通信</a>',
        '<a href="/soc">normalized_event</a>',
        '<a href="/audit">审计</a>',
    ])
    doc = f"""<!doctype html>
<html lang="zh-CN">
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{html.escape(title)}</title>
<link rel="icon" href="data:,">
<style>
body {{ margin: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; background: #f4f7fb; color: #1f2937; }}
header {{ background: #14385f; color: white; padding: 16px 24px; }}
nav {{ display: flex; gap: 14px; flex-wrap: wrap; margin-top: 10px; }}
nav a {{ color: #dbeafe; text-decoration: none; font-size: 14px; }}
main {{ padding: 22px; max-width: 1280px; margin: 0 auto; }}
.grid {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 14px; }}
.card {{ background: white; border: 1px solid #d8e1ee; border-radius: 8px; padding: 14px; }}
.metric {{ font-size: 30px; font-weight: 700; color: #14385f; }}
table {{ width: 100%; border-collapse: collapse; background: white; border: 1px solid #d8e1ee; }}
th, td {{ border-bottom: 1px solid #e5edf6; padding: 8px; text-align: left; font-size: 13px; vertical-align: top; }}
th {{ background: #eaf1f8; color: #14385f; }}
form {{ display: flex; gap: 8px; flex-wrap: wrap; margin: 0 0 14px; }}
input, button, select {{ padding: 8px 10px; border: 1px solid #b9c7d8; border-radius: 6px; background: white; }}
button, .button {{ background: #1f5f99; color: white; text-decoration: none; border-color: #1f5f99; display: inline-block; }}
.bar {{ height: 10px; background: #2d7dbf; border-radius: 6px; }}
.muted {{ color: #667085; }}
.high {{ color: #b42318; font-weight: 700; }}
.medium {{ color: #b54708; font-weight: 700; }}
.low {{ color: #344054; font-weight: 700; }}
pre {{ white-space: pre-wrap; background: #0f172a; color: #e5e7eb; padding: 12px; border-radius: 8px; overflow: auto; }}
</style>
<header><h1>码研工坊网络流量行为分析平台</h1><nav>{nav}</nav></header>
<main>{body}</main>
</html>"""
    return doc.encode("utf-8")


def render_overview(db: Path) -> bytes:
    data = overview(db)
    cards = "".join(f'<div class="card"><div class="muted">{label}</div><div class="metric">{value}</div></div>' for label, value in [
        ("连接数", data["counts"]["connections"]),
        ("DNS 请求数", data["counts"]["dns"]),
        ("HTTP 访问数", data["counts"]["http"]),
        ("SSL/TLS 记录数", data["counts"]["ssl"]),
        ("异常事件数", data["counts"]["anomalies"]),
    ])
    def bars(rows: list[dict[str, Any]]) -> str:
        max_total = max([row["total"] for row in rows], default=1)
        return "".join(
            f'<div style="margin:8px 0"><strong>{html.escape(str(row["value"]))}</strong> '
            f'<span class="muted">{row["total"]}</span><div class="bar" style="width:{max(4, int(row["total"] / max_total * 100))}%"></div></div>'
            for row in rows
        ) or '<p class="muted">暂无数据</p>'
    body = f"""
<section class="grid">{cards}</section>
<section class="grid" style="margin-top:16px">
  <div class="card"><h2>Top 源 IP</h2>{bars(data["top_src_ip"])}</div>
  <div class="card"><h2>Top 目标 IP</h2>{bars(data["top_dst_ip"])}</div>
  <div class="card"><h2>Top 域名</h2>{bars(data["top_domain"])}</div>
</section>
<section style="margin-top:16px"><h2>高分异常</h2>{render_anomaly_table(data["recent_anomalies"])}</section>
"""
    return html_page("总览", body)


def render_anomaly_table(rows: list[dict[str, Any]]) -> str:
    if not rows:
        return '<p class="muted">暂无异常</p>'
    trs = "".join(
        f"<tr><td>{row['id']}</td><td>{html.escape(row['category'])}</td><td class=\"{row['severity']}\">{row['severity']}</td>"
        f"<td>{row['score']}</td><td>{html.escape(row['entity'])}</td><td>{html.escape(row['detail'])}</td>"
        f"<td>{ts_to_text(row['first_seen'])}</td></tr>"
        for row in rows
    )
    return f"<table><tr><th>ID</th><th>类型</th><th>级别</th><th>评分</th><th>对象</th><th>详情</th><th>时间</th></tr>{trs}</table>"


def render_logs(db: Path, log_type: str, params: dict[str, str]) -> bytes:
    rows, total = query_table(db, log_type, params)
    q = {key: value for key, value in params.items() if value}
    export_href = f"/export/{log_type}.csv?{urlencode(q)}"
    filter_form = f"""
<form>
  <input name="ip" value="{html.escape(params.get('ip', ''))}" placeholder="IP 搜索">
  <input name="start" value="{html.escape(params.get('start', ''))}" placeholder="开始 ts">
  <input name="end" value="{html.escape(params.get('end', ''))}" placeholder="结束 ts">
  <input name="query" value="{html.escape(params.get('query', ''))}" placeholder="DNS query">
  <input name="host" value="{html.escape(params.get('host', ''))}" placeholder="HTTP host">
  <button type="submit">筛选</button>
  <a class="button" href="{export_href}">导出 CSV</a>
</form>
"""
    if not rows:
        table = '<p class="muted">暂无数据</p>'
    else:
        columns = [key for key in rows[0].keys() if key not in {"import_id"}][:14]
        header = "".join(f"<th>{html.escape(col)}</th>" for col in columns) + "<th>详情</th>"
        body = ""
        for row in rows:
            cells = "".join(f"<td>{html.escape(ts_to_text(row[col]) if col == 'ts' else str(row[col] if row[col] is not None else ''))}</td>" for col in columns)
            body += f'<tr>{cells}<td><a href="/logs/{log_type}/{row["id"]}">查看</a></td></tr>'
        table = f"<table><tr>{header}</tr>{body}</table>"
    page = int(params.get("page") or 1)
    base_q = {key: value for key, value in params.items() if value and key != "page"}
    links = []
    if page > 1:
        links.append(f'<a href="/logs/{log_type}?{urlencode(base_q | {"page": str(page - 1)})}">上一页</a>')
    if page * 50 < total:
        links.append(f'<a href="/logs/{log_type}?{urlencode(base_q | {"page": str(page + 1)})}">下一页</a>')
    pager = f'<p class="muted">共 {total} 条，第 {page} 页 {" ".join(links)}</p>'
    return html_page(f"{log_type} logs", f"<h2>{log_type}.log</h2>{filter_form}{pager}{table}")


def render_events(db: Path, params: dict[str, str]) -> bytes:
    rows, total = query_events(db, params)
    filter_form = f"""
<form>
  <input name="ip" value="{html.escape(params.get('ip', ''))}" placeholder="IP">
  <input name="domain" value="{html.escape(params.get('domain', ''))}" placeholder="域名">
  <input name="proto" value="{html.escape(params.get('proto', ''))}" placeholder="协议">
  <input name="service" value="{html.escape(params.get('service', ''))}" placeholder="服务">
  <input name="status" value="{html.escape(params.get('status', ''))}" placeholder="状态">
  <input name="start" value="{html.escape(params.get('start', ''))}" placeholder="开始 ts">
  <input name="end" value="{html.escape(params.get('end', ''))}" placeholder="结束 ts">
  <button type="submit">筛选</button>
</form>
"""
    if not rows:
        table = '<p class="muted">暂无标准化事件</p>'
    else:
        body = "".join(
            f"<tr><td>{row['id']}</td><td>{html.escape(row['log_type'])}</td><td>{ts_to_text(row['ts'])}</td>"
            f"<td>{html.escape(str(row['src_ip'] or ''))}:{row['src_port'] or ''}</td>"
            f"<td>{html.escape(str(row['dst_ip'] or ''))}:{row['dst_port'] or ''}</td>"
            f"<td>{html.escape(str(row['proto'] or ''))}</td><td>{html.escape(str(row['service'] or ''))}</td>"
            f"<td>{html.escape(str(row['domain'] or ''))}</td><td>{html.escape(str(row['uri'] or ''))}</td>"
            f"<td>{html.escape(str(row['status'] or ''))}</td><td>{row['bytes'] or ''}</td></tr>"
            for row in rows
        )
        table = f"<table><tr><th>ID</th><th>日志</th><th>时间</th><th>源</th><th>目标</th><th>协议</th><th>服务</th><th>域名</th><th>URI</th><th>状态</th><th>字节</th></tr>{body}</table>"
    return html_page("标准化事件查询", f"<h2>多日志标准化查询</h2>{filter_form}<p class=\"muted\">共 {total} 条</p>{table}")


def render_detail(db: Path, log_type: str, row_id: int) -> bytes:
    init_db(db)
    with connect(db) as con:
        row = con.execute(f"SELECT * FROM {table_for(log_type)} WHERE id=?", (row_id,)).fetchone()
    if row is None:
        return html_page("Not found", "<h2>未找到记录</h2>")
    content = json.dumps(dict(row), ensure_ascii=False, indent=2, default=str)
    return html_page(f"{log_type} detail", f"<h2>{log_type}.log 详情 #{row_id}</h2><pre>{html.escape(content)}</pre>")


def render_anomalies(db: Path) -> bytes:
    init_db(db)
    with connect(db) as con:
        rows = [dict(row) for row in con.execute("SELECT * FROM anomalies ORDER BY score DESC, id DESC").fetchall()]
    return html_page("异常分析", f"<h2>异常分析</h2>{render_anomaly_table(rows)}")


def render_assets(db: Path) -> bytes:
    rows = asset_graph(db)
    trs = "".join(
        f"<tr><td>{html.escape(row['src_ip'])}</td><td>{html.escape(row['dst_ip'])}</td><td>{row['connections']}</td><td>{row['bytes'] or 0}</td><td>{ts_to_text(row['last_seen'])}</td></tr>"
        for row in rows
    )
    table = f"<table><tr><th>源资产</th><th>目标资产</th><th>连接数</th><th>字节数</th><th>最后通信</th></tr>{trs}</table>" if rows else '<p class="muted">暂无资产通信数据</p>'
    risks = risk_scores(db)
    risk_rows = "".join(f"<tr><td>{html.escape(row['entity'])}</td><td>{row['score']}</td><td>{row['findings']}</td></tr>" for row in risks)
    risk_table = f"<table><tr><th>对象</th><th>风险评分</th><th>发现数</th></tr>{risk_rows}</table>" if risks else '<p class="muted">暂无风险评分</p>'
    return html_page("资产通信", f"<h2>资产通信关系</h2>{table}<h2>风险评分</h2>{risk_table}")


def render_soc(db: Path) -> bytes:
    init_db(db)
    with connect(db) as con:
        rows = [dict(row) for row in con.execute("SELECT * FROM normalized_events ORDER BY risk_score DESC, id DESC LIMIT 200").fetchall()]
    if not rows:
        table = '<p class="muted">暂无 normalized_event</p>'
    else:
        body = "".join(
            f"<tr><td>{row['id']}</td><td>{ts_to_text(row['event_time'])}</td><td>{html.escape(row['event_type'])}</td><td class=\"{row['severity']}\">{row['severity']}</td><td>{row['risk_score']}</td><td>{html.escape(row['entity'] or '')}</td><td>{html.escape(row['detail'])}</td></tr>"
            for row in rows
        )
        table = f"<table><tr><th>ID</th><th>时间</th><th>类型</th><th>级别</th><th>评分</th><th>对象</th><th>详情</th></tr>{body}</table>"
    return html_page("SOC", f"<h2>统一 SOC normalized_event</h2>{table}")


def render_audit(db: Path) -> bytes:
    init_db(db)
    with connect(db) as con:
        rows = [dict(row) for row in con.execute("SELECT * FROM audit_logs ORDER BY id DESC LIMIT 200").fetchall()]
    if not rows:
        table = '<p class="muted">暂无审计记录</p>'
    else:
        body = "".join(
            f"<tr><td>{row['id']}</td><td>{html.escape(row['created_at'])}</td><td>{html.escape(row['action'])}</td><td>{html.escape(row['target'])}</td><td>{html.escape(row['detail'])}</td></tr>"
            for row in rows
        )
        table = f"<table><tr><th>ID</th><th>时间</th><th>动作</th><th>对象</th><th>详情</th></tr>{body}</table>"
    return html_page("审计", f"<h2>审计记录</h2>{table}")


class PlatformHandler(BaseHTTPRequestHandler):
    db = db_path(create=False)
    env_root = DEFAULT_ENV_ROOT

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        params = {key: values[-1] for key, values in parse_qs(parsed.query).items()}
        try:
            if parsed.path == "/":
                body = render_overview(self.db)
                self.send_html(body)
            elif parsed.path == "/events":
                self.send_html(render_events(self.db, params))
            elif parsed.path.startswith("/logs/"):
                parts = parsed.path.strip("/").split("/")
                log_type = parts[1]
                if log_type not in {"conn", "dns", "http", "ssl"}:
                    self.send_error(404)
                    return
                if len(parts) == 3:
                    self.send_html(render_detail(self.db, log_type, int(parts[2])))
                else:
                    self.send_html(render_logs(self.db, log_type, params))
            elif parsed.path == "/anomalies":
                self.send_html(render_anomalies(self.db))
            elif parsed.path == "/assets":
                self.send_html(render_assets(self.db))
            elif parsed.path == "/soc":
                self.send_html(render_soc(self.db))
            elif parsed.path == "/audit":
                self.send_html(render_audit(self.db))
            elif parsed.path.startswith("/export/") and parsed.path.endswith(".csv"):
                log_type = Path(parsed.path).stem
                target = ensure_env(self.env_root)["doc_dir"] / f"{log_type}-export.csv"
                export_csv(self.db, log_type, target, params)
                payload = target.read_bytes()
                self.send_response(200)
                self.send_header("Content-Type", "text/csv; charset=utf-8")
                self.send_header("Content-Disposition", f'attachment; filename="{target.name}"')
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)
            else:
                self.send_error(404)
        except Exception as exc:
            self.send_response(500)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.end_headers()
            self.wfile.write(str(exc).encode("utf-8"))

    def send_html(self, payload: bytes) -> None:
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, fmt: str, *args: Any) -> None:
        sys.stderr.write("[zeek-platform] " + fmt % args + "\n")


def serve(db: Path, host: str, port: int) -> None:
    init_db(db)
    PlatformHandler.db = db
    PlatformHandler.env_root = db.parents[2] if len(db.parents) > 2 else DEFAULT_ENV_ROOT
    httpd = ThreadingHTTPServer((host, port), PlatformHandler)
    print(f"Serving Zeek traffic platform at http://{host}:{port}")
    print(f"Database: {db}")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("Shutdown requested")
    finally:
        httpd.server_close()


def import_paths(db: Path, paths: list[Path], env_root: Path = DEFAULT_ENV_ROOT) -> list[tuple[str, int, Path]]:
    imported = []
    for path in paths:
        if path.is_dir():
            children = [child for child in sorted(path.iterdir()) if child.is_file() and child.name.lower().endswith(".log")]
            imported.extend(import_paths(db, children, env_root))
        else:
            try:
                log_type, rows = import_log(db, path, env_root=env_root)
                imported.append((log_type, rows, path))
            except Exception as exc:
                log_type = None
                try:
                    log_type = infer_log_type(path)
                except Exception:
                    pass
                record_import_failure(db, path, exc, log_type)
    return imported


def retry_failed_imports(db: Path, env_root: Path = DEFAULT_ENV_ROOT) -> list[tuple[str, int, Path]]:
    retried: list[tuple[str, int, Path]] = []
    for item in import_failures(db, unresolved_only=True):
        path = Path(item["source_path"])
        if not path.exists():
            record_import_failure(db, path, FileNotFoundError(path), item.get("log_type"))
            continue
        imported = import_paths(db, [path], env_root)
        retried.extend(imported)
        if imported:
            with connect(db) as con:
                con.execute("UPDATE import_failures SET resolved=1 WHERE id=?", (item["id"],))
                con.commit()
    return retried


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Zeek traffic behavior analysis platform")
    parser.add_argument("--env-root", default=str(DEFAULT_ENV_ROOT), help="Runtime Environment root")
    parser.add_argument("--db", default=None, help="SQLite database path")
    sub = parser.add_subparsers(dest="cmd", required=True)

    sub.add_parser("init-env", help="Create Environment folders and database")
    sub.add_parser("init-demo", help="Create demo Zeek logs under Environment and import them")

    p_import = sub.add_parser("import", help="Import Zeek .log files or directories")
    p_import.add_argument("paths", nargs="+")

    sub.add_parser("import-failures", help="Print unresolved import failures")
    sub.add_parser("retry-failed", help="Retry unresolved failed imports")

    p_analyze = sub.add_parser("analyze", help="Rebuild anomalies and normalized events")

    p_status = sub.add_parser("status", help="Print overview JSON")

    p_report = sub.add_parser("export-report", help="Export markdown or JSON report")
    p_report.add_argument("--out", default=None)
    p_report.add_argument("--kind", choices=["all", "daily", "anomaly", "asset"], default="all")

    p_export = sub.add_parser("export-csv", help="Export one log table to CSV")
    p_export.add_argument("log_type", choices=["conn", "dns", "http", "ssl"])
    p_export.add_argument("--out", default=None)
    p_export.add_argument("--ip", default="")
    p_export.add_argument("--start", default="")
    p_export.add_argument("--end", default="")

    p_soc = sub.add_parser("export-soc", help="Export normalized events as SOC import JSON")
    p_soc.add_argument("--out", default=None)

    p_push_soc = sub.add_parser("push-soc", help="POST normalized events to a SOC /api/import endpoint")
    p_push_soc.add_argument("--url", required=True)

    p_serve = sub.add_parser("serve", help="Run local web UI")
    p_serve.add_argument("--host", default="127.0.0.1")
    p_serve.add_argument("--port", type=int, default=18083)

    args = parser.parse_args(argv)
    env_root = Path(args.env_root)
    paths = ensure_env(env_root)
    db = Path(args.db) if args.db else paths["db_dir"] / "zeek_traffic.sqlite3"

    if args.cmd == "init-env":
        init_db(db)
        print(json.dumps({key: str(value) for key, value in paths.items()} | {"db": str(db)}, ensure_ascii=False, indent=2))
        return 0
    if args.cmd == "init-demo":
        init_db(db)
        samples = init_demo_logs(env_root)
        imported = import_paths(db, samples, env_root)
        print(f"Created demo logs in {samples[0].parent}")
        for log_type, rows, path in imported:
            print(f"Imported {rows:>4} rows from {path.name} as {log_type}")
        print(f"Anomalies: {overview(db)['counts']['anomalies']}")
        return 0
    if args.cmd == "import":
        init_db(db)
        imported = import_paths(db, [Path(path) for path in args.paths], env_root)
        for log_type, rows, path in imported:
            print(f"Imported {rows:>4} rows from {path} as {log_type}")
        failures = import_failures(db)
        if failures:
            print(f"Unresolved import failures: {len(failures)}", file=sys.stderr)
            return 1
        return 0
    if args.cmd == "import-failures":
        print(json.dumps(import_failures(db), ensure_ascii=False, indent=2))
        return 0
    if args.cmd == "retry-failed":
        retried = retry_failed_imports(db, env_root)
        for log_type, rows, path in retried:
            print(f"Imported {rows:>4} rows from {path} as {log_type}")
        failures = import_failures(db)
        if failures:
            print(f"Unresolved import failures: {len(failures)}", file=sys.stderr)
            return 1
        return 0
    if args.cmd == "analyze":
        print(f"Anomalies: {analyze(db)}")
        return 0
    if args.cmd == "status":
        print(json.dumps(overview(db), ensure_ascii=False, indent=2))
        return 0
    if args.cmd == "export-report":
        default_name = "zeek-traffic-report.md" if args.kind == "all" else f"zeek-{args.kind}-report.md"
        out = Path(args.out) if args.out else paths["doc_dir"] / default_name
        print(export_report(db, out, args.kind))
        return 0
    if args.cmd == "export-csv":
        out = Path(args.out) if args.out else paths["doc_dir"] / f"{args.log_type}-export.csv"
        filters = {"ip": args.ip, "start": args.start, "end": args.end}
        print(export_csv(db, args.log_type, out, filters))
        return 0
    if args.cmd == "export-soc":
        out = Path(args.out) if args.out else paths["doc_dir"] / "zeek-normalized-events.json"
        print(export_soc(db, out))
        return 0
    if args.cmd == "push-soc":
        print(json.dumps(push_soc(db, args.url), ensure_ascii=False, indent=2))
        return 0
    if args.cmd == "serve":
        serve(db, args.host, args.port)
        return 0
    return 2


if __name__ == "__main__":
    raise SystemExit(main())

"""HTTP API and static frontend for the local IDS alert console."""

from __future__ import annotations

import argparse
import json
import mimetypes
import sys
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any, Dict, Mapping, Optional
from urllib.parse import parse_qs, urlparse

from . import store

STATIC_DIR = Path(__file__).parent / "static"


def run_server(db_path: Optional[str], host: str, port: int) -> None:
    class Handler(IdsRequestHandler):
        database_path = db_path

    server = ThreadingHTTPServer((host, port), Handler)
    print(f"IDS Console listening on http://{host}:{port}")
    print(f"Database: {db_path or store.DEFAULT_DB_PATH}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nIDS Console stopped")


class IdsRequestHandler(BaseHTTPRequestHandler):
    database_path: Optional[str] = None

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        params = first_values(parse_qs(parsed.query))
        if parsed.path == "/":
            self.serve_static("index.html")
            return
        if parsed.path.startswith("/static/"):
            self.serve_static(parsed.path.removeprefix("/static/"))
            return
        if parsed.path == "/api/health":
            self.send_json({"ok": True, "db_path": str(self.database_path or store.DEFAULT_DB_PATH)})
            return
        with store.connect(self.database_path) as conn:
            if parsed.path == "/api/summary":
                self.send_json(store.summary(conn))
            elif parsed.path == "/api/alerts":
                self.send_json(store.list_alerts(conn, params))
            elif parsed.path.startswith("/api/alerts/"):
                alert_id = parse_int_path(parsed.path, "/api/alerts/")
                item = store.alert_detail(conn, alert_id) if alert_id else None
                if item is None:
                    self.send_error(HTTPStatus.NOT_FOUND, "Alert not found")
                else:
                    self.send_json(item)
            elif parsed.path == "/api/aggregation":
                self.send_json(store.aggregate_alerts(conn))
            elif parsed.path == "/api/import-logs":
                self.send_json({"items": store.import_logs(conn)})
            elif parsed.path == "/api/whitelist":
                self.send_json({"items": store.whitelist_entries(conn)})
            elif parsed.path == "/api/ip-analysis":
                self.send_json(store.ip_analysis(conn, params.get("ip")))
            elif parsed.path == "/api/timeline":
                self.send_json(store.timeline(conn, params))
            elif parsed.path == "/api/report":
                self.send_json(store.report(conn))
            elif parsed.path.startswith("/api/reports/"):
                kind = parsed.path.removeprefix("/api/reports/").strip("/") or "daily"
                self.send_json(store.report_by_kind(conn, kind))
            elif parsed.path == "/api/export.csv":
                self.send_csv(store.export_alerts_csv(conn, params), "ids-alerts.csv")
            else:
                self.send_error(HTTPStatus.NOT_FOUND, "Not found")

    def do_POST(self) -> None:
        parsed = urlparse(self.path)
        body = self.read_json()
        with store.connect(self.database_path) as conn:
            if parsed.path == "/api/import":
                eve_path = body.get("path")
                if not eve_path:
                    self.send_error(HTTPStatus.BAD_REQUEST, "path is required")
                    return
                self.send_json(store.import_path(conn, str(eve_path), bool(body.get("incremental", True))))
            elif parsed.path == "/api/import/retry-failed":
                self.send_json(store.retry_failed_imports(conn))
            elif parsed.path.endswith("/false-positive") and parsed.path.startswith("/api/alerts/"):
                alert_id = parse_int_path(parsed.path, "/api/alerts/", "/false-positive")
                ok = store.mark_false_positive(
                    conn,
                    alert_id,
                    bool(body.get("false_positive", True)),
                    body.get("reason"),
                )
                self.send_json({"ok": ok})
            elif parsed.path.endswith("/ignore") and parsed.path.startswith("/api/alerts/"):
                alert_id = parse_int_path(parsed.path, "/api/alerts/", "/ignore")
                ok = store.mark_ignored(
                    conn,
                    alert_id,
                    bool(body.get("ignored", True)),
                    body.get("reason"),
                )
                self.send_json({"ok": ok})
            elif parsed.path.endswith("/ticket") and parsed.path.startswith("/api/alerts/"):
                alert_id = parse_int_path(parsed.path, "/api/alerts/", "/ticket")
                ticket = store.create_ticket(conn, alert_id, body.get("assignee"), body.get("note"))
                if ticket is None:
                    self.send_error(HTTPStatus.NOT_FOUND, "Alert not found")
                else:
                    self.send_json(ticket, status=HTTPStatus.CREATED)
            elif parsed.path.startswith("/api/tickets/") and parsed.path.endswith("/status"):
                ticket_id = parse_int_path(parsed.path, "/api/tickets/", "/status")
                try:
                    ticket = store.update_ticket(
                        conn,
                        ticket_id,
                        str(body.get("status") or "open"),
                        body.get("review_conclusion"),
                        body.get("note"),
                    )
                except ValueError as exc:
                    self.send_error(HTTPStatus.BAD_REQUEST, str(exc))
                    return
                if ticket is None:
                    self.send_error(HTTPStatus.NOT_FOUND, "Ticket not found")
                else:
                    self.send_json(ticket)
            elif parsed.path == "/api/whitelist":
                try:
                    entry = store.add_whitelist(
                        conn,
                        str(body.get("kind") or ""),
                        str(body.get("value") or ""),
                        body.get("reason"),
                    )
                except ValueError as exc:
                    self.send_error(HTTPStatus.BAD_REQUEST, str(exc))
                    return
                self.send_json(entry, status=HTTPStatus.CREATED)
            else:
                self.send_error(HTTPStatus.NOT_FOUND, "Not found")

    def serve_static(self, relative_path: str) -> None:
        safe_path = Path(relative_path)
        if safe_path.is_absolute() or ".." in safe_path.parts:
            self.send_error(HTTPStatus.BAD_REQUEST, "Invalid path")
            return
        target = STATIC_DIR / safe_path
        if not target.exists() or not target.is_file():
            self.send_error(HTTPStatus.NOT_FOUND, "Static file not found")
            return
        content_type = mimetypes.guess_type(str(target))[0] or "application/octet-stream"
        data = target.read_bytes()
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def read_json(self) -> Dict[str, Any]:
        length = int(self.headers.get("Content-Length", "0"))
        if length == 0:
            return {}
        try:
            return json.loads(self.rfile.read(length).decode("utf-8"))
        except json.JSONDecodeError:
            return {}

    def send_json(self, payload: Any, status: HTTPStatus = HTTPStatus.OK) -> None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def send_csv(self, payload: str, filename: str) -> None:
        data = payload.encode("utf-8-sig")
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", "text/csv; charset=utf-8")
        self.send_header("Content-Disposition", f'attachment; filename="{filename}"')
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, format: str, *args: Any) -> None:
        sys.stderr.write("IDS Console: " + format % args + "\n")


def first_values(params: Mapping[str, Any]) -> Dict[str, str]:
    return {key: values[0] for key, values in params.items() if values}


def parse_int_path(path: str, prefix: str, suffix: str = "") -> Optional[int]:
    value = path.removeprefix(prefix)
    if suffix:
        value = value.removesuffix(suffix)
    try:
        return int(value.strip("/"))
    except ValueError:
        return None


def main(argv: Optional[list[str]] = None) -> int:
    parser = argparse.ArgumentParser(description="Local Suricata IDS alert console")
    parser.add_argument("--db", default=None, help=f"SQLite database path, default: {store.DEFAULT_DB_PATH}")
    subparsers = parser.add_subparsers(dest="command", required=True)

    import_parser = subparsers.add_parser("import", help="Import authorized Suricata eve.json file or directory")
    import_parser.add_argument("eve_json", help="Path to Suricata eve.json, json-lines file, or directory")
    import_parser.add_argument("--no-incremental", action="store_true", help="Import even if the file fingerprint was already seen")

    subparsers.add_parser("retry-failed", help="Retry failed imports")

    demo_parser = subparsers.add_parser("init-demo", help="Copy demo eve.json to Environment log directory")
    demo_parser.add_argument("--import", dest="import_demo", action="store_true", help="Import the demo after copying it")

    report_parser = subparsers.add_parser("report", help="Export IDS report")
    report_parser.add_argument("--kind", choices=["daily", "high", "rules"], default="daily")
    report_parser.add_argument("--out", default=None)

    serve_parser = subparsers.add_parser("serve", help="Serve local IDS console")
    serve_parser.add_argument("--host", default="127.0.0.1")
    serve_parser.add_argument("--port", type=int, default=8084)

    summary_parser = subparsers.add_parser("summary", help="Print JSON summary")
    summary_parser.set_defaults(command="summary")

    args = parser.parse_args(argv)
    if args.command == "import":
        with store.connect(args.db) as conn:
            print(json.dumps(store.import_path(conn, args.eve_json, not args.no_incremental), ensure_ascii=False, indent=2))
    elif args.command == "retry-failed":
        with store.connect(args.db) as conn:
            print(json.dumps(store.retry_failed_imports(conn), ensure_ascii=False, indent=2))
    elif args.command == "init-demo":
        example = Path(__file__).resolve().parents[1] / "examples" / "ids-console" / "demo-eve.json"
        demo_path = store.init_demo_eve(example)
        print(demo_path)
        if args.import_demo:
            with store.connect(args.db) as conn:
                print(json.dumps(store.import_path(conn, demo_path), ensure_ascii=False, indent=2))
    elif args.command == "report":
        with store.connect(args.db) as conn:
            output = args.out or str(store.DEFAULT_REPORT_DIR / f"ids-{args.kind}-report.md")
            print(store.export_report_file(conn, args.kind, output))
    elif args.command == "serve":
        run_server(args.db, args.host, args.port)
    elif args.command == "summary":
        with store.connect(args.db) as conn:
            print(json.dumps(store.summary(conn), ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

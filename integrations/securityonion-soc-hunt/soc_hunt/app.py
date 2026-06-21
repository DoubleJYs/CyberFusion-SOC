from __future__ import annotations

import json
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

from .auth import AuthConfig, Principal
from .constants import DEMO_ALERTS
from .storage import SocHuntStore


WEB_ROOT = Path(__file__).resolve().parents[1] / "web"


class SocHuntHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(WEB_ROOT), **kwargs)

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        if not parsed.path.startswith("/api/"):
            return super().do_GET()
        principal = self.require_role("viewer")
        if principal is None:
            return
        query = parse_qs(parsed.query)
        store = SocHuntStore()
        try:
            if parsed.path == "/api/summary":
                self.send_json(store.summary())
            elif parsed.path == "/api/alerts":
                self.send_json(
                    store.list_alerts(
                        query=query.get("query", [""])[0],
                        severity=query.get("severity", [""])[0],
                        source=query.get("source", [""])[0],
                        asset=query.get("asset", [""])[0],
                        rule=query.get("rule", [""])[0],
                        tags=query.get("tags", [""])[0],
                        status=query.get("status", [""])[0],
                        start=query.get("start", [""])[0],
                        end=query.get("end", [""])[0],
                    )
                )
            elif parsed.path.startswith("/api/alerts/"):
                alert = store.get_alert(parsed.path.rsplit("/", 1)[-1])
                self.send_json(alert or {}, 404 if alert is None else 200)
            elif parsed.path == "/api/cases":
                self.send_json(store.list_cases())
            elif parsed.path == "/api/hunts":
                self.send_json(store.list_hunt_tasks())
            elif parsed.path == "/api/audit":
                self.send_json(store.audit_logs())
            elif parsed.path == "/api/health":
                self.send_json(store.list_data_source_health())
            else:
                self.send_json({"error": "not found"}, 404)
        finally:
            store.close()

    def do_POST(self) -> None:
        parsed = urlparse(self.path)
        store = SocHuntStore()
        try:
            body = self.read_json()
            if parsed.path == "/api/import-demo":
                principal = self.require_role("analyst", store)
                if principal is None:
                    return
                actor = self.actor(body, principal, "demo")
                count = store.import_jsonl(DEMO_ALERTS, actor=actor)
                store.seed_demo_health(actor=actor)
                self.send_json({"imported": count})
            elif parsed.path == "/api/reset-demo":
                principal = self.require_role("admin", store)
                if principal is None:
                    return
                store.reset_demo(actor=self.actor(body, principal, "demo"))
                self.send_json({"reset": True})
            elif parsed.path == "/api/cases":
                principal = self.require_role("analyst", store)
                if principal is None:
                    return
                case_id = store.create_case(
                    title=body["title"],
                    alert_id=body.get("alert_id"),
                    summary=body.get("summary", ""),
                    assignee=body.get("assignee", ""),
                    actor=self.actor(body, principal, "analyst"),
                )
                self.send_json({"case_id": case_id}, 201)
            elif parsed.path.endswith("/records") and parsed.path.startswith("/api/cases/"):
                principal = self.require_role("analyst", store)
                if principal is None:
                    return
                case_id = int(parsed.path.split("/")[3])
                record_id = store.add_case_record(
                    case_id,
                    body.get("action", "analysis"),
                    body.get("note", ""),
                    self.actor(body, principal, body.get("analyst", "analyst")),
                )
                self.send_json({"record_id": record_id}, 201)
            elif parsed.path == "/api/hunts":
                principal = self.require_role("analyst", store)
                if principal is None:
                    return
                hunt_id = store.create_hunt_task(
                    body["title"],
                    body["query"],
                    owner=self.actor(body, principal, body.get("owner", "analyst")),
                    status=body.get("status", "planned"),
                    notes=body.get("notes", ""),
                    hypothesis=body.get("hypothesis", ""),
                    result=body.get("result", ""),
                    conclusion=body.get("conclusion", ""),
                    case_id=body.get("case_id"),
                )
                self.send_json({"hunt_id": hunt_id}, 201)
            elif parsed.path.endswith("/result") and parsed.path.startswith("/api/hunts/"):
                principal = self.require_role("analyst", store)
                if principal is None:
                    return
                hunt_id = int(parsed.path.split("/")[3])
                store.record_hunt_result(
                    hunt_id,
                    body.get("result", ""),
                    body.get("conclusion", ""),
                    body.get("status", "completed"),
                    self.actor(body, principal, "analyst"),
                )
                self.send_json({"hunt_id": hunt_id, "status": body.get("status", "completed")})
            elif parsed.path == "/api/reports":
                principal = self.require_role("analyst", store)
                if principal is None:
                    return
                report = store.export_case_report(int(body["case_id"]), actor=self.actor(body, principal, "analyst"))
                self.send_json({"report": str(report)})
            elif parsed.path == "/api/hunt-reports":
                principal = self.require_role("analyst", store)
                if principal is None:
                    return
                report = store.export_hunt_report(int(body["hunt_id"]), actor=self.actor(body, principal, "analyst"))
                self.send_json({"report": str(report)})
            elif parsed.path == "/api/summary-reports":
                principal = self.require_role("analyst", store)
                if principal is None:
                    return
                report = store.export_summary_report(body.get("period", "daily"), actor=self.actor(body, principal, "analyst"))
                self.send_json({"report": str(report)})
            elif parsed.path == "/api/backups":
                principal = self.require_role("admin", store)
                if principal is None:
                    return
                backup = store.backup_runtime(actor=self.actor(body, principal, "admin"))
                self.send_json({"backup": str(backup)})
            else:
                self.send_json({"error": "not found"}, 404)
        except Exception as exc:
            self.send_json({"error": str(exc)}, 400)
        finally:
            store.close()

    def do_PATCH(self) -> None:
        parsed = urlparse(self.path)
        store = SocHuntStore()
        try:
            body = self.read_json()
            if parsed.path.endswith("/status") and parsed.path.startswith("/api/cases/"):
                principal = self.require_role("lead", store)
                if principal is None:
                    return
                case_id = int(parsed.path.split("/")[3])
                store.update_case_status(case_id, body["status"], self.actor(body, principal, "lead"))
                self.send_json({"case_id": case_id, "status": body["status"]})
            elif parsed.path.endswith("/assign") and parsed.path.startswith("/api/cases/"):
                principal = self.require_role("lead", store)
                if principal is None:
                    return
                case_id = int(parsed.path.split("/")[3])
                store.assign_case(case_id, body["assignee"], self.actor(body, principal, "lead"))
                self.send_json({"case_id": case_id, "assignee": body["assignee"]})
            elif parsed.path.endswith("/review") and parsed.path.startswith("/api/cases/"):
                principal = self.require_role("lead", store)
                if principal is None:
                    return
                case_id = int(parsed.path.split("/")[3])
                store.review_case(
                    case_id,
                    body["reviewer"],
                    body.get("note", "Reviewed case."),
                    self.actor(body, principal, "lead"),
                )
                self.send_json({"case_id": case_id, "reviewer": body["reviewer"]})
            else:
                self.send_json({"error": "not found"}, 404)
        finally:
            store.close()

    def read_json(self) -> dict:
        length = int(self.headers.get("Content-Length", "0"))
        if length == 0:
            return {}
        return json.loads(self.rfile.read(length).decode("utf-8"))

    def require_role(self, role: str, store: SocHuntStore | None = None) -> Principal | None:
        auth = AuthConfig()
        principal = auth.authenticate(self.headers.get("Authorization"))
        if auth.authorize(principal, role):
            return principal
        actor = principal.actor if principal else "anonymous"
        if store is not None:
            store.audit(actor, "auth_denied", "api", self.path, f"required_role={role}")
        self.send_json({"error": "forbidden", "required_role": role}, 403)
        return None

    def actor(self, body: dict, principal: Principal, fallback: str) -> str:
        if principal.authenticated:
            return principal.actor
        return body.get("actor") or body.get("analyst") or fallback

    def send_json(self, payload: object, status: int = 200) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


def run_server(host: str = "127.0.0.1", port: int = 8765) -> None:
    server = ThreadingHTTPServer((host, port), SocHuntHandler)
    print(f"SOC Hunt listening on http://{host}:{port}")
    server.serve_forever()

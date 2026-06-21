from __future__ import annotations

import argparse
import contextlib
import hashlib
import html
import io
import json
import os
import sys
import uuid
import zipfile
from dataclasses import dataclass
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, urlencode, urlparse


PROJECT_NAME = "11-velociraptor"
DEFAULT_ENV_ROOT = Path(os.environ.get("IR_PLATFORM_ENV_ROOT", "/Users/zhangjiyan/Environment"))
SOURCE_ROOT = Path(__file__).resolve().parents[1]
TEMPLATES_PATH = Path(__file__).with_name("templates") / "task_templates.json"
DEMO_PATH = Path(__file__).with_name("demo") / "demo_collection.json"
CATEGORY_EVENT_TYPES = {
    "suspicious_process": "process",
    "network_connections": "network",
    "startup_items": "file",
    "file_hashes": "file",
    "browser_traces": "browser",
    "user_logins": "login",
    "alerts": "alert",
}
ROLE_PERMISSIONS = {
    "viewer": {
        "case:read",
        "task:read",
        "template:read",
        "result:read",
        "timeline:read",
        "evidence:read",
        "audit:read",
        "doctor:read",
        "web:serve",
    },
    "analyst": {
        "case:read",
        "case:create",
        "task:read",
        "task:create",
        "template:read",
        "result:read",
        "result:import",
        "timeline:read",
        "evidence:read",
        "dispatch:read",
        "dispatch:plan",
        "disposition:record",
        "report:generate",
        "chain:verify",
        "audit:read",
        "doctor:read",
        "web:serve",
    },
    "reviewer": {
        "case:read",
        "case:close",
        "task:read",
        "task:approve",
        "template:read",
        "result:read",
        "timeline:read",
        "evidence:read",
        "dispatch:read",
        "disposition:record",
        "report:generate",
        "chain:verify",
        "audit:read",
        "doctor:read",
        "web:serve",
    },
    "admin": {"*"},
}
DEFAULT_ROLE = os.environ.get("IR_PLATFORM_ROLE", "admin")


def utc_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def short_id(prefix: str) -> str:
    stamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    return f"{prefix}-{stamp}-{uuid.uuid4().hex[:6].upper()}"


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def csv_escape(value: str) -> str:
    if any(char in value for char in [",", '"', "\n"]):
        return '"' + value.replace('"', '""') + '"'
    return value


@dataclass(frozen=True)
class EnvPaths:
    root: Path
    database_dir: Path
    log_dir: Path
    upload_dir: Path
    docs_dir: Path

    @property
    def state_file(self) -> Path:
        return self.database_dir / "ir_state.json"

    @property
    def audit_log(self) -> Path:
        return self.log_dir / "audit.jsonl"


def build_paths(env_root: Path) -> EnvPaths:
    root = env_root.expanduser().resolve()
    return EnvPaths(
        root=root,
        database_dir=root / "02-databases" / PROJECT_NAME,
        log_dir=root / "11-logs" / PROJECT_NAME,
        upload_dir=root / "13-uploads" / PROJECT_NAME,
        docs_dir=root / "08-docs" / PROJECT_NAME,
    )


def ensure_paths(paths: EnvPaths) -> None:
    for directory in (paths.database_dir, paths.log_dir, paths.upload_dir, paths.docs_dir):
        directory.mkdir(parents=True, exist_ok=True)


def empty_state() -> dict[str, Any]:
    return {
        "schema": 2,
        "cases": [],
        "tasks": [],
        "approvals": [],
        "dispatch_records": [],
        "dispatch_plans": [],
        "results": {},
        "timeline": {},
        "evidence_packages": [],
        "evidence_records": [],
        "dispositions": [],
        "reports": [],
        "chain_verifications": [],
    }


def load_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp_path = path.with_suffix(path.suffix + ".tmp")
    with tmp_path.open("w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    tmp_path.replace(path)


def load_state(paths: EnvPaths) -> dict[str, Any]:
    ensure_paths(paths)
    if not paths.state_file.exists():
        state = empty_state()
        write_json(paths.state_file, state)
        return state
    state = load_json(paths.state_file)
    for key, value in empty_state().items():
        state.setdefault(key, value)
    state["schema"] = max(int(state.get("schema", 1)), 2)
    return state


def save_state(paths: EnvPaths, state: dict[str, Any]) -> None:
    write_json(paths.state_file, state)


def audit(paths: EnvPaths, event_type: str, details: dict[str, Any]) -> None:
    ensure_paths(paths)
    record = {"time": utc_now(), "type": event_type, **details}
    with paths.audit_log.open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(record, ensure_ascii=False, sort_keys=True) + "\n")


def load_templates() -> list[dict[str, Any]]:
    return load_json(TEMPLATES_PATH)["templates"]


def find_template(template_id: str) -> dict[str, Any]:
    for template in load_templates():
        if template["id"] == template_id:
            return template
    raise SystemExit(f"Unknown template: {template_id}")


def find_case(state: dict[str, Any], case_id: str) -> dict[str, Any]:
    for case in state["cases"]:
        if case["case_id"] == case_id:
            return case
    raise SystemExit(f"Unknown case: {case_id}")


def find_task(state: dict[str, Any], task_id: str) -> dict[str, Any]:
    for task in state["tasks"]:
        if task["id"] == task_id:
            return task
    raise SystemExit(f"Unknown task: {task_id}")


def latest_dispatch_plan(state: dict[str, Any], task_id: str) -> dict[str, Any] | None:
    plans = [item for item in state["dispatch_plans"] if item.get("task_id") == task_id]
    return plans[-1] if plans else None


def case_dispatch_plans(state: dict[str, Any], case_id: str) -> list[dict[str, Any]]:
    return [item for item in state["dispatch_plans"] if item.get("case_id") == case_id]


def case_tasks(state: dict[str, Any], case_id: str) -> list[dict[str, Any]]:
    return [task for task in state["tasks"] if task.get("case_id") == case_id]


def case_timeline(state: dict[str, Any], case_id: str) -> list[dict[str, Any]]:
    events: list[dict[str, Any]] = []
    for task in case_tasks(state, case_id):
        events.extend(state["timeline"].get(task["id"], []))
    return sorted(events, key=lambda item: item["time"])


def print_json(payload: Any) -> None:
    print(json.dumps(payload, ensure_ascii=False, indent=2))


def actor_role(args: argparse.Namespace) -> str:
    return getattr(args, "role", DEFAULT_ROLE)


def require_permission(role: str, permission: str) -> None:
    permissions = ROLE_PERMISSIONS.get(role)
    if not permissions:
        raise SystemExit(f"Unknown role: {role}. Expected one of: {', '.join(sorted(ROLE_PERMISSIONS))}")
    if "*" not in permissions and permission not in permissions:
        raise SystemExit(f"Role {role} is not allowed to perform {permission}.")


def require_args_permission(args: argparse.Namespace, permission: str) -> None:
    require_permission(actor_role(args), permission)


def html_escape(value: Any) -> str:
    return html.escape("" if value is None else str(value), quote=True)


def query_link(path: str, **params: str) -> str:
    return f"{path}?{urlencode(params)}"


def status_badge(status: str) -> str:
    normalized = status.lower().replace("_", "-")
    return f'<span class="status status-{html_escape(normalized)}">{html_escape(status)}</span>'


def render_page(title: str, body: str, active: str = "dashboard") -> str:
    nav = [
        ("dashboard", "/", "总览"),
        ("templates", "/templates", "任务模板"),
        ("audit", "/audit", "审计日志"),
    ]
    nav_html = "".join(
        f'<a class="{"active" if key == active else ""}" href="{href}">{label}</a>'
        for key, href, label in nav
    )
    return f"""<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>{html_escape(title)} - 码研工坊 IR</title>
  <style>
    :root {{
      color-scheme: light;
      --bg: #f4f7fb;
      --panel: #ffffff;
      --panel-strong: #eef4fb;
      --line: #d9e2ee;
      --text: #182536;
      --muted: #607086;
      --blue: #1f5eff;
      --teal: #0f8c8c;
      --red: #c73d48;
      --amber: #ad6b00;
      --green: #177245;
    }}
    * {{ box-sizing: border-box; }}
    body {{ margin: 0; background: var(--bg); color: var(--text); font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; font-size: 14px; }}
    header {{ display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 16px 28px; background: #102033; color: #fff; }}
    header h1 {{ margin: 0; font-size: 18px; font-weight: 700; letter-spacing: 0; }}
    header p {{ margin: 3px 0 0; color: #b9c8db; font-size: 12px; }}
    nav {{ display: flex; gap: 8px; }}
    nav a {{ color: #c8d7e8; text-decoration: none; padding: 8px 10px; border-radius: 6px; font-weight: 650; }}
    nav a.active, nav a:hover {{ color: #fff; background: #203854; }}
    main {{ max-width: 1480px; margin: 0 auto; padding: 22px 28px 42px; }}
    .grid {{ display: grid; gap: 16px; }}
    .grid-2 {{ grid-template-columns: minmax(0, 1fr) minmax(360px, 0.42fr); }}
    .grid-3 {{ grid-template-columns: repeat(3, minmax(0, 1fr)); }}
    section, .panel {{ background: var(--panel); border: 1px solid var(--line); border-radius: 8px; padding: 16px; }}
    section h2, .panel h2 {{ margin: 0 0 12px; font-size: 16px; }}
    h3 {{ margin: 18px 0 10px; font-size: 14px; color: #24364b; }}
    table {{ width: 100%; border-collapse: collapse; }}
    th, td {{ text-align: left; border-bottom: 1px solid var(--line); padding: 9px 8px; vertical-align: top; }}
    th {{ color: var(--muted); font-size: 12px; font-weight: 750; background: var(--panel-strong); }}
    td code, pre {{ font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }}
    pre {{ overflow: auto; max-height: 520px; padding: 12px; background: #0f1b2a; color: #d9e9ff; border-radius: 6px; line-height: 1.45; }}
    form {{ display: grid; gap: 10px; }}
    .form-row {{ display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }}
    label {{ display: grid; gap: 5px; color: var(--muted); font-size: 12px; font-weight: 700; }}
    input, select, textarea {{ width: 100%; border: 1px solid #c8d5e4; border-radius: 6px; padding: 8px 9px; font: inherit; color: var(--text); background: #fff; }}
    textarea {{ min-height: 72px; resize: vertical; }}
    button, .button {{ border: 0; border-radius: 6px; background: var(--blue); color: #fff; padding: 9px 12px; font-weight: 750; text-decoration: none; display: inline-flex; align-items: center; justify-content: center; cursor: pointer; }}
    .button.secondary, button.secondary {{ background: #e8eef6; color: #20344c; }}
    .actions {{ display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }}
    .metric {{ background: var(--panel); border: 1px solid var(--line); border-radius: 8px; padding: 14px; }}
    .metric strong {{ display: block; font-size: 24px; }}
    .metric span {{ color: var(--muted); font-size: 12px; font-weight: 700; }}
    .status {{ border-radius: 999px; padding: 3px 8px; font-size: 12px; font-weight: 750; background: #e8eef6; color: #26394f; white-space: nowrap; }}
    .status-reported, .status-result-imported {{ background: #e3f5ec; color: var(--green); }}
    .status-pending-approval, .status-waiting-approval {{ background: #fff2d8; color: var(--amber); }}
    .status-ready-for-review, .status-approved, .status-ready {{ background: #e3f1ff; color: #164da8; }}
    .severity-high, .severity-critical {{ color: var(--red); font-weight: 800; }}
    .severity-medium {{ color: var(--amber); font-weight: 800; }}
    .muted {{ color: var(--muted); }}
    .split-title {{ display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }}
    .tabs {{ display: flex; gap: 8px; flex-wrap: wrap; margin: 0 0 12px; }}
    .tabs a {{ padding: 7px 10px; border-radius: 6px; background: #e8eef6; color: #20344c; text-decoration: none; font-weight: 700; }}
    .tabs a.active {{ background: var(--blue); color: #fff; }}
    @media (max-width: 980px) {{ .grid-2, .grid-3, .form-row {{ grid-template-columns: 1fr; }} header {{ align-items: flex-start; flex-direction: column; }} }}
  </style>
</head>
<body>
  <header>
    <div><h1>码研工坊终端取证响应平台</h1><p>授权终端取证与应急响应案件工作台</p></div>
    <nav>{nav_html}</nav>
  </header>
  <main>{body}</main>
</body>
</html>"""


def render_dashboard_page(paths: EnvPaths, state: dict[str, Any]) -> str:
    cases = sorted(state["cases"], key=lambda item: item.get("created_at", ""), reverse=True)
    pending_tasks = [task for task in state["tasks"] if task.get("status") == "PENDING_APPROVAL"]
    reports = [item for item in state["reports"] if item.get("case_id")]
    metrics = f"""
    <div class="grid grid-3">
      <div class="metric"><strong>{len(state['cases'])}</strong><span>应急案件</span></div>
      <div class="metric"><strong>{len(pending_tasks)}</strong><span>待审批任务</span></div>
      <div class="metric"><strong>{len(state['evidence_records'])}</strong><span>证据链记录</span></div>
    </div>"""
    case_rows = "".join(
        "<tr>"
        f"<td><a href='{query_link('/case', case_id=case['case_id'])}'>{html_escape(case['case_id'])}</a></td>"
        f"<td>{html_escape(case['asset'])}</td>"
        f"<td>{html_escape(case['incident_type'])}</td>"
        f"<td class='severity-{html_escape(case['severity'])}'>{html_escape(case['severity'])}</td>"
        f"<td>{html_escape(case['owner'])}</td>"
        f"<td>{status_badge(case['status'])}</td>"
        "</tr>"
        for case in cases
    ) or "<tr><td colspan='6' class='muted'>暂无案件</td></tr>"
    template_options = "".join(f"<option value='{html_escape(t['id'])}'>{html_escape(t['name'])}</option>" for t in load_templates())
    body = f"""
    {metrics}
    <div class="grid grid-2" style="margin-top:16px">
      <section>
        <div class="split-title"><h2>案件列表</h2><a class="button secondary" href="/templates">查看模板</a></div>
        <table><thead><tr><th>案件编号</th><th>资产</th><th>事件类型</th><th>等级</th><th>负责人</th><th>状态</th></tr></thead><tbody>{case_rows}</tbody></table>
      </section>
      <section>
        <h2>创建应急案件</h2>
        <form method="post" action="/actions/create-case">
          <div class="form-row">
            <label>案件编号<input name="case_id" placeholder="留空自动生成"></label>
            <label>资产<input name="asset" required placeholder="endpoint-01"></label>
          </div>
          <div class="form-row">
            <label>事件类型<input name="incident_type" required placeholder="malware_triage"></label>
            <label>严重等级<select name="severity"><option>critical</option><option selected>high</option><option>medium</option><option>low</option></select></label>
          </div>
          <div class="form-row">
            <label>负责人<input name="owner" value="zhangjiyan"></label>
            <label>状态<input name="status" value="OPEN"></label>
          </div>
          <label>描述<textarea name="description" placeholder="授权范围、现象和处置目标"></textarea></label>
          <button type="submit">创建案件</button>
        </form>
        <h3>快速创建任务</h3>
        <form method="post" action="/actions/create-task">
          <div class="form-row">
            <label>案件编号<input name="case_id" required></label>
            <label>模板<select name="template">{template_options}</select></label>
          </div>
          <label>采集原因<textarea name="reason" required>授权终端应急取证采集</textarea></label>
          <button type="submit">创建待审批任务</button>
        </form>
      </section>
    </div>
    <section style="margin-top:16px">
      <h2>最近报告</h2>
      <table><thead><tr><th>案件</th><th>报告路径</th><th>SHA-256</th><th>生成时间</th></tr></thead><tbody>
      {''.join(f"<tr><td>{html_escape(r.get('case_id'))}</td><td><code>{html_escape(r.get('path'))}</code></td><td><code>{html_escape(r.get('sha256'))}</code></td><td>{html_escape(r.get('created_at'))}</td></tr>" for r in reports[-8:]) or "<tr><td colspan='4' class='muted'>暂无报告</td></tr>"}
      </tbody></table>
    </section>
    """
    return render_page("总览", body, "dashboard")


def render_case_page(paths: EnvPaths, state: dict[str, Any], case_id: str) -> str:
    case = find_case(state, case_id)
    tasks = case_tasks(state, case_id)
    timeline = case_timeline(state, case_id)
    evidence = [item for item in state["evidence_records"] if item.get("case_id") == case_id]
    dispositions = [item for item in state["dispositions"] if item.get("case_id") == case_id]
    reports = [item for item in state["reports"] if item.get("case_id") == case_id]
    verifications = [item for item in state["chain_verifications"] if item.get("case_id") == case_id]
    last_verification = verifications[-1] if verifications else None
    task_rows = "".join(
        "<tr>"
        f"<td><a href='{query_link('/task', task_id=task['id'])}'>{html_escape(task['id'])}</a></td>"
        f"<td>{html_escape(task['template_name'])}</td><td>{html_escape(task['asset'])}</td><td>{status_badge(task['status'])}</td>"
        f"<td>{html_escape(task.get('approved_by') or '-')}</td><td>{html_escape(task.get('approval_reason') or '-')}</td>"
        "</tr>"
        for task in tasks
    ) or "<tr><td colspan='6' class='muted'>暂无任务</td></tr>"
    timeline_rows = "".join(
        "<tr>"
        f"<td>{html_escape(event['time'])}</td><td>{html_escape(event['event_type'])}</td><td class='severity-{html_escape(event['severity'])}'>{html_escape(event['severity'])}</td>"
        f"<td>{html_escape(event['category'])}</td><td>{html_escape(event['summary'])}</td>"
        "</tr>"
        for event in timeline
    ) or "<tr><td colspan='5' class='muted'>暂无时间线</td></tr>"
    evidence_rows = "".join(
        "<tr>"
        f"<td>{html_escape(item['evidence_id'])}</td><td><code>{html_escape(item['hash'])}</code></td><td>{html_escape(item['source'])}</td>"
        f"<td>{html_escape(item['collector'])}</td><td>{html_escape(item['collected_at'])}</td><td><code>{html_escape(item['storage_path'])}</code></td>"
        "</tr>"
        for item in evidence
    ) or "<tr><td colspan='6' class='muted'>暂无证据</td></tr>"
    disposition_rows = "".join(
        "<tr>"
        f"<td>{html_escape(item['analysis_conclusion'])}</td><td>{html_escape(item['recommended_action'])}</td><td>{html_escape(item['reviewer'])}</td><td>{status_badge(item['status'])}</td>"
        "</tr>"
        for item in dispositions
    ) or "<tr><td colspan='4' class='muted'>暂无处置记录</td></tr>"
    report_rows = "".join(
        f"<tr><td><code>{html_escape(item['path'])}</code></td><td><code>{html_escape(item['sha256'])}</code></td><td>{html_escape(item['created_at'])}</td></tr>"
        for item in reports
    ) or "<tr><td colspan='3' class='muted'>暂无报告</td></tr>"
    verification_summary = "尚未校验证据链"
    if last_verification:
        verification_summary = f"{last_verification['status']} / checked={last_verification['checked']} / failed={last_verification['failed']}"
    template_options = "".join(f"<option value='{html_escape(t['id'])}'>{html_escape(t['name'])}</option>" for t in load_templates())
    body = f"""
    <section>
      <div class="split-title"><div><h2>{html_escape(case_id)}</h2><p class="muted">{html_escape(case['asset'])} / {html_escape(case['incident_type'])} / {html_escape(case['owner'])}</p></div>{status_badge(case['status'])}</div>
      <div class="actions">
        <form method="post" action="/actions/report"><input type="hidden" name="case_id" value="{html_escape(case_id)}"><button type="submit">导出取证报告</button></form>
        <form method="post" action="/actions/verify-chain"><input type="hidden" name="case_id" value="{html_escape(case_id)}"><button class="secondary" type="submit">校验证据链</button></form>
        <a class="button secondary" href="{query_link('/timeline', case_id=case_id)}">查看时间线</a>
      </div>
    </section>
    <div class="grid grid-2" style="margin-top:16px">
      <section>
        <h2>采集任务</h2>
        <table><thead><tr><th>任务</th><th>模板</th><th>资产</th><th>状态</th><th>审批人</th><th>审批原因</th></tr></thead><tbody>{task_rows}</tbody></table>
      </section>
      <section>
        <h2>创建采集任务</h2>
        <form method="post" action="/actions/create-task">
          <input type="hidden" name="case_id" value="{html_escape(case_id)}">
          <div class="form-row"><label>模板<select name="template">{template_options}</select></label><label>目标资产<input name="target" value="{html_escape(case['asset'])}"></label></div>
          <label>采集原因<textarea name="reason" required>授权终端应急取证采集</textarea></label>
          <button type="submit">创建待审批任务</button>
        </form>
      </section>
    </div>
    <section style="margin-top:16px"><h2>事件时间线</h2><table><thead><tr><th>时间</th><th>类型</th><th>等级</th><th>来源</th><th>摘要</th></tr></thead><tbody>{timeline_rows}</tbody></table></section>
    <section style="margin-top:16px"><h2>证据链</h2><table><thead><tr><th>证据编号</th><th>Hash</th><th>来源</th><th>采集人</th><th>时间</th><th>存储位置</th></tr></thead><tbody>{evidence_rows}</tbody></table></section>
    <div class="grid grid-2" style="margin-top:16px">
      <section><h2>处置记录</h2><table><thead><tr><th>分析结论</th><th>建议动作</th><th>复核</th><th>状态</th></tr></thead><tbody>{disposition_rows}</tbody></table></section>
      <section>
        <h2>新增处置记录</h2>
        <form method="post" action="/actions/dispose">
          <input type="hidden" name="case_id" value="{html_escape(case_id)}">
          <label>分析结论<textarea name="conclusion" required></textarea></label>
          <label>建议动作<textarea name="recommendation" required></textarea></label>
          <div class="form-row"><label>复核人<input name="reviewer" required value="{html_escape(case['owner'])}"></label><label>状态<select name="status"><option>READY_FOR_REVIEW</option><option>CLOSED</option><option>REPORTED</option></select></label></div>
          <button type="submit">记录处置</button>
        </form>
        <h3>复核关闭</h3>
        <form method="post" action="/actions/close-case">
          <input type="hidden" name="case_id" value="{html_escape(case_id)}">
          <div class="form-row"><label>复核人<input name="reviewer" required value="{html_escape(case['owner'])}"></label><label>关闭原因<input name="reason" required value="证据链校验通过，报告已导出，处置记录已复核"></label></div>
          <button class="secondary" type="submit">关闭案件</button>
        </form>
      </section>
    </div>
    <section style="margin-top:16px"><h2>链路校验</h2><p>{html_escape(verification_summary)}</p></section>
    <section style="margin-top:16px"><h2>取证报告</h2><table><thead><tr><th>路径</th><th>SHA-256</th><th>时间</th></tr></thead><tbody>{report_rows}</tbody></table></section>
    """
    return render_page(f"案件 {case_id}", body, "dashboard")


def render_task_page(paths: EnvPaths, state: dict[str, Any], task_id: str) -> str:
    task = find_task(state, task_id)
    result = state["results"].get(task_id)
    dispatch_plan = latest_dispatch_plan(state, task_id)
    table = result.get("table", []) if result else []
    result_rows = "".join(
        f"<tr><td>{html_escape(row.get('time') or '-')}</td><td class='severity-{html_escape(row.get('severity'))}'>{html_escape(row.get('severity'))}</td><td>{html_escape(row.get('category'))}</td><td>{html_escape(row.get('summary'))}</td></tr>"
        for row in table
    ) or "<tr><td colspan='4' class='muted'>暂无采集结果</td></tr>"
    raw_json = json.dumps(result.get("raw_json", {}) if result else {}, ensure_ascii=False, indent=2)
    attachments = result.get("attachments", []) if result else []
    attachment_rows = "".join(f"<tr><td><code>{html_escape(path)}</code></td></tr>" for path in attachments) or "<tr><td class='muted'>暂无附件</td></tr>"
    approve_form = ""
    if task.get("status") == "PENDING_APPROVAL":
        approve_form = f"""
        <section>
          <h2>审批采集任务</h2>
          <form method="post" action="/actions/approve-task">
            <input type="hidden" name="task_id" value="{html_escape(task_id)}">
            <div class="form-row"><label>审批人<input name="approver" required></label><label>审批原因<input name="reason" required></label></div>
            <button type="submit">审批通过</button>
          </form>
        </section>"""
    import_form = f"""
      <section>
        <h2>导入采集结果</h2>
        <form method="post" action="/actions/import-demo">
          <input type="hidden" name="task_id" value="{html_escape(task_id)}">
          <label>采集人<input name="collector" value="{html_escape(task.get('created_by'))}"></label>
          <button type="submit">导入 Demo 结果</button>
        </form>
      </section>"""
    dispatch_form = f"""
      <section>
        <h2>授权采集下发计划</h2>
        <form method="post" action="/actions/dispatch-plan">
          <input type="hidden" name="task_id" value="{html_escape(task_id)}">
          <label>操作者<input name="operator" value="{html_escape(task.get('created_by'))}"></label>
          <button type="submit">生成下发包</button>
        </form>
        <p class="muted">{'当前下发包: <code>' + html_escape(dispatch_plan.get('path')) + '</code>' if dispatch_plan else '尚未生成下发包'}</p>
      </section>"""
    body = f"""
    <section><div class="split-title"><div><h2>{html_escape(task_id)}</h2><p class="muted">{html_escape(task['template_name'])} / <a href="{query_link('/case', case_id=task['case_id'])}">{html_escape(task['case_id'])}</a></p></div>{status_badge(task['status'])}</div></section>
    <div class="grid grid-2" style="margin-top:16px">{approve_form}{dispatch_form}{import_form}</div>
    <section style="margin-top:16px"><h2>结果表格</h2><table><thead><tr><th>时间</th><th>等级</th><th>分类</th><th>摘要</th></tr></thead><tbody>{result_rows}</tbody></table></section>
    <section style="margin-top:16px"><h2>附件</h2><table><tbody>{attachment_rows}</tbody></table></section>
    <section style="margin-top:16px"><h2>原始 JSON</h2><pre>{html_escape(raw_json)}</pre></section>
    """
    return render_page(f"任务 {task_id}", body, "dashboard")


def render_templates_page() -> str:
    rows = "".join(
        "<tr>"
        f"<td>{html_escape(item['id'])}</td><td>{html_escape(item['name'])}</td><td>{html_escape(item['priority'])}</td><td>{html_escape(item['approval_required'])}</td>"
        f"<td>{html_escape(', '.join(item['velociraptor_artifacts']))}</td><td>{html_escape(item['safety_scope'])}</td>"
        "</tr>"
        for item in load_templates()
    )
    body = f"<section><h2>授权采集模板</h2><table><thead><tr><th>ID</th><th>名称</th><th>优先级</th><th>需审批</th><th>采集工件</th><th>安全范围</th></tr></thead><tbody>{rows}</tbody></table></section>"
    return render_page("任务模板", body, "templates")


def read_audit_records(paths: EnvPaths, limit: int = 120) -> list[dict[str, Any]]:
    if not paths.audit_log.exists():
        return []
    lines = paths.audit_log.read_text(encoding="utf-8").splitlines()[-limit:]
    records = []
    for line in lines:
        try:
            records.append(json.loads(line))
        except json.JSONDecodeError:
            records.append({"time": "-", "type": "invalid", "raw": line})
    return records


def render_audit_page(paths: EnvPaths) -> str:
    rows = "".join(
        f"<tr><td>{html_escape(item.get('time'))}</td><td>{html_escape(item.get('type'))}</td><td><code>{html_escape(json.dumps(item, ensure_ascii=False))}</code></td></tr>"
        for item in reversed(read_audit_records(paths))
    ) or "<tr><td colspan='3' class='muted'>暂无审计日志</td></tr>"
    body = f"<section><h2>审计日志</h2><table><thead><tr><th>时间</th><th>类型</th><th>详情</th></tr></thead><tbody>{rows}</tbody></table></section>"
    return render_page("审计日志", body, "audit")


def capture_cli_action(func: Any, args: argparse.Namespace) -> None:
    with contextlib.redirect_stdout(io.StringIO()):
        func(args)


def redirect_response(handler: BaseHTTPRequestHandler, location: str) -> None:
    handler.send_response(303)
    handler.send_header("Location", location)
    handler.send_header("Content-Length", "0")
    handler.end_headers()


def send_html(handler: BaseHTTPRequestHandler, html_body: str, status: int = 200) -> None:
    payload = html_body.encode("utf-8")
    handler.send_response(status)
    handler.send_header("Content-Type", "text/html; charset=utf-8")
    handler.send_header("Content-Length", str(len(payload)))
    handler.end_headers()
    handler.wfile.write(payload)


class IRConsoleHandler(BaseHTTPRequestHandler):
    server_version = "IRConsole/0.1"

    def do_GET(self) -> None:
        paths: EnvPaths = self.server.ir_paths  # type: ignore[attr-defined]
        parsed = urlparse(self.path)
        query = parse_qs(parsed.query)
        try:
            state = load_state(paths)
            if parsed.path == "/":
                send_html(self, render_dashboard_page(paths, state))
            elif parsed.path == "/case":
                send_html(self, render_case_page(paths, state, query.get("case_id", [""])[0]))
            elif parsed.path == "/task":
                send_html(self, render_task_page(paths, state, query.get("task_id", [""])[0]))
            elif parsed.path == "/templates":
                send_html(self, render_templates_page())
            elif parsed.path == "/audit":
                send_html(self, render_audit_page(paths))
            elif parsed.path == "/health":
                payload = b"ok"
                self.send_response(200)
                self.send_header("Content-Type", "text/plain; charset=utf-8")
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)
            else:
                send_html(self, render_page("Not Found", "<section><h2>页面不存在</h2></section>"), 404)
        except Exception as exc:
            send_html(self, render_page("Error", f"<section><h2>请求失败</h2><pre>{html_escape(exc)}</pre></section>"), 500)

    def do_POST(self) -> None:
        paths: EnvPaths = self.server.ir_paths  # type: ignore[attr-defined]
        role: str = self.server.ir_role  # type: ignore[attr-defined]
        length = int(self.headers.get("Content-Length", "0"))
        form = {key: values[0] for key, values in parse_qs(self.rfile.read(length).decode("utf-8")).items()}
        try:
            if self.path == "/actions/create-case":
                require_permission(role, "case:create")
                case_id = form.get("case_id") or None
                capture_cli_action(cmd_create_case, argparse.Namespace(env_root=str(paths.root), role=role, case_id=case_id, asset=form["asset"], incident_type=form["incident_type"], severity=form["severity"], owner=form.get("owner", "zhangjiyan"), status=form.get("status", "OPEN"), description=form.get("description", "")))
                redirect_response(self, query_link("/case", case_id=case_id or load_state(paths)["cases"][-1]["case_id"]))
            elif self.path == "/actions/create-task":
                require_permission(role, "task:create")
                capture_cli_action(cmd_create_task, argparse.Namespace(env_root=str(paths.root), role=role, case_id=form["case_id"], template=form["template"], target=form.get("target") or None, reason=form["reason"], operator=form.get("operator", "zhangjiyan"), approved_by=None, approval_reason=None))
                redirect_response(self, query_link("/case", case_id=form["case_id"]))
            elif self.path == "/actions/approve-task":
                require_permission(role, "task:approve")
                capture_cli_action(cmd_approve_task, argparse.Namespace(env_root=str(paths.root), role=role, task_id=form["task_id"], approver=form["approver"], reason=form["reason"], force=False))
                task = find_task(load_state(paths), form["task_id"])
                redirect_response(self, query_link("/task", task_id=task["id"]))
            elif self.path == "/actions/import-demo":
                require_permission(role, "result:import")
                capture_cli_action(cmd_import_demo, argparse.Namespace(env_root=str(paths.root), role=role, task_id=form["task_id"], collector=form.get("collector", "zhangjiyan"), allow_unapproved=False))
                redirect_response(self, query_link("/task", task_id=form["task_id"]))
            elif self.path == "/actions/dispatch-plan":
                require_permission(role, "dispatch:plan")
                capture_cli_action(cmd_dispatch_plan, argparse.Namespace(env_root=str(paths.root), role=role, task_id=form["task_id"], operator=form.get("operator", "zhangjiyan"), mode="dry-run"))
                redirect_response(self, query_link("/task", task_id=form["task_id"]))
            elif self.path == "/actions/dispose":
                require_permission(role, "disposition:record")
                capture_cli_action(cmd_dispose, argparse.Namespace(env_root=str(paths.root), role=role, case_id=form.get("case_id") or None, task_id=form.get("task_id") or None, conclusion=form.get("conclusion"), recommendation=form.get("recommendation"), reviewer=form["reviewer"], owner=form.get("owner", "zhangjiyan"), status=form.get("status", "READY_FOR_REVIEW"), action=None, note=None))
                redirect_response(self, query_link("/case", case_id=form.get("case_id") or find_task(load_state(paths), form["task_id"])["case_id"]))
            elif self.path == "/actions/report":
                require_permission(role, "report:generate")
                capture_cli_action(cmd_report, argparse.Namespace(env_root=str(paths.root), role=role, case_id=form.get("case_id") or None, task_id=form.get("task_id") or None))
                redirect_response(self, query_link("/case", case_id=form.get("case_id") or find_task(load_state(paths), form["task_id"])["case_id"]))
            elif self.path == "/actions/verify-chain":
                require_permission(role, "chain:verify")
                capture_cli_action(cmd_verify_chain, argparse.Namespace(env_root=str(paths.root), role=role, case_id=form["case_id"], format="text"))
                redirect_response(self, query_link("/case", case_id=form["case_id"]))
            elif self.path == "/actions/close-case":
                require_permission(role, "case:close")
                capture_cli_action(cmd_close_case, argparse.Namespace(env_root=str(paths.root), role=role, case_id=form["case_id"], reviewer=form["reviewer"], reason=form["reason"]))
                redirect_response(self, query_link("/case", case_id=form["case_id"]))
            else:
                send_html(self, render_page("Not Found", "<section><h2>动作不存在</h2></section>"), 404)
        except Exception as exc:
            send_html(self, render_page("Action Error", f"<section><h2>动作失败</h2><pre>{html_escape(exc)}</pre></section>"), 400)

    def log_message(self, format: str, *args: Any) -> None:
        sys.stderr.write("ir-console: " + format % args + "\n")


def cmd_init(args: argparse.Namespace) -> None:
    require_args_permission(args, "platform:init")
    paths = build_paths(Path(args.env_root))
    ensure_paths(paths)
    state = load_state(paths)
    save_state(paths, state)
    audit(paths, "platform.initialized", {"operator": args.operator, "role": actor_role(args)})
    print_json(
        {
            "status": "initialized",
            "schema": state["schema"],
            "database": str(paths.state_file),
            "logs": str(paths.log_dir),
            "uploads": str(paths.upload_dir),
            "docs": str(paths.docs_dir),
        }
    )


def cmd_doctor(args: argparse.Namespace) -> None:
    require_args_permission(args, "doctor:read")
    paths = build_paths(Path(args.env_root))
    checks = []
    for label, path in (
        ("database_dir", paths.database_dir),
        ("log_dir", paths.log_dir),
        ("upload_dir", paths.upload_dir),
        ("docs_dir", paths.docs_dir),
    ):
        checks.append({"name": label, "path": str(path), "exists": path.exists(), "inside_source": SOURCE_ROOT in path.parents})
    print_json(
        {
            "source_root": str(SOURCE_ROOT),
            "env_root": str(paths.root),
            "checks": checks,
            "policy": "Runtime data must stay under Environment, not in the source tree.",
        }
    )


def cmd_create_case(args: argparse.Namespace) -> None:
    require_args_permission(args, "case:create")
    paths = build_paths(Path(args.env_root))
    state = load_state(paths)
    case_id = args.case_id or short_id("CASE")
    if any(case["case_id"] == case_id for case in state["cases"]):
        raise SystemExit(f"Case already exists: {case_id}")
    case = {
        "case_id": case_id,
        "asset": args.asset,
        "incident_type": args.incident_type,
        "severity": args.severity,
        "owner": args.owner,
        "status": args.status,
        "created_at": utc_now(),
        "updated_at": utc_now(),
        "description": args.description,
    }
    state["cases"].append(case)
    save_state(paths, state)
    audit(paths, "case.created", {"case_id": case_id, "owner": args.owner, "severity": args.severity, "role": actor_role(args)})
    print_json(case)


def cmd_cases(args: argparse.Namespace) -> None:
    require_args_permission(args, "case:read")
    paths = build_paths(Path(args.env_root))
    state = load_state(paths)
    cases = sorted(state["cases"], key=lambda item: item["created_at"], reverse=True)
    if args.format == "json":
        print_json(cases)
        return
    for case in cases:
        print(f"{case['case_id']}  {case['status']}  {case['severity']}  {case['asset']}  {case['incident_type']}  owner={case['owner']}")


def cmd_templates(args: argparse.Namespace) -> None:
    require_args_permission(args, "template:read")
    templates = load_templates()
    if args.format == "json":
        print_json(templates)
        return
    for template in templates:
        artifacts = ", ".join(template["velociraptor_artifacts"])
        sensitivity = "sensitive" if template["approval_required"] else "standard"
        print(f"{template['id']}: {template['name']} [{template['priority']}, {sensitivity}]")
        print(f"  artifacts: {artifacts}")


def cmd_create_task(args: argparse.Namespace) -> None:
    require_args_permission(args, "task:create")
    paths = build_paths(Path(args.env_root))
    state = load_state(paths)
    case = find_case(state, args.case_id)
    template = find_template(args.template)
    task_id = short_id("IR")
    approval_required = bool(template["approval_required"])
    task = {
        "id": task_id,
        "case_id": case["case_id"],
        "asset": args.target or case["asset"],
        "template_id": template["id"],
        "template_name": template["name"],
        "velociraptor_artifacts": template["velociraptor_artifacts"],
        "reason": args.reason,
        "created_by": args.operator,
        "created_at": utc_now(),
        "approval_required": approval_required,
        "approved_by": None,
        "approved_at": None,
        "approval_reason": None,
        "status": "PENDING_APPROVAL" if approval_required else "READY",
        "safety_scope": template["safety_scope"],
    }
    state["tasks"].append(task)
    state["dispatch_records"].append(
        {
            "id": f"DISPATCH-{uuid.uuid4().hex[:8].upper()}",
            "case_id": case["case_id"],
            "task_id": task_id,
            "mode": "velociraptor_artifact_plan",
            "status": "WAITING_APPROVAL" if approval_required else "READY",
            "artifacts": template["velociraptor_artifacts"],
            "created_at": utc_now(),
            "operator": args.operator,
            "note": "Planned authorized Velociraptor artifact dispatch.",
        }
    )
    case["status"] = "TASK_PENDING_APPROVAL" if approval_required else "TASK_READY"
    case["updated_at"] = utc_now()
    save_state(paths, state)
    audit(paths, "task.created", {"case_id": case["case_id"], "task_id": task_id, "template_id": template["id"], "operator": args.operator, "role": actor_role(args)})
    if args.approved_by:
        approve_task(paths, state, task, args.approved_by, args.approval_reason or args.reason, actor_role(args))
    print_json(task)


def approve_task(paths: EnvPaths, state: dict[str, Any], task: dict[str, Any], approver: str, reason: str, role: str = "admin") -> None:
    task["approved_by"] = approver
    task["approved_at"] = utc_now()
    task["approval_reason"] = reason
    task["status"] = "APPROVED"
    approval = {
        "id": f"APPROVAL-{uuid.uuid4().hex[:8].upper()}",
        "case_id": task["case_id"],
        "task_id": task["id"],
        "approver": approver,
        "reason": reason,
        "created_at": task["approved_at"],
    }
    state["approvals"].append(approval)
    for record in state["dispatch_records"]:
        if record["task_id"] == task["id"]:
            record["status"] = "READY"
            record["updated_at"] = utc_now()
    case = find_case(state, task["case_id"])
    case["status"] = "TASK_APPROVED"
    case["updated_at"] = utc_now()
    save_state(paths, state)
    audit(paths, "task.approved", {"case_id": task["case_id"], "task_id": task["id"], "approver": approver, "role": role})


def cmd_approve_task(args: argparse.Namespace) -> None:
    require_args_permission(args, "task:approve")
    paths = build_paths(Path(args.env_root))
    state = load_state(paths)
    task = find_task(state, args.task_id)
    if task["status"] not in {"PENDING_APPROVAL", "DRAFT", "READY"} and not args.force:
        raise SystemExit(f"Task is not waiting for approval: {task['status']}")
    approve_task(paths, state, task, args.approver, args.reason, actor_role(args))
    print_json(task)


def cmd_tasks(args: argparse.Namespace) -> None:
    require_args_permission(args, "task:read")
    paths = build_paths(Path(args.env_root))
    state = load_state(paths)
    tasks = state["tasks"]
    if args.case_id:
        tasks = [task for task in tasks if task["case_id"] == args.case_id]
    tasks = sorted(tasks, key=lambda item: item["created_at"], reverse=True)
    if args.format == "json":
        print_json(tasks)
        return
    for task in tasks:
        print(f"{task['id']}  {task['status']}  {task['case_id']}  {task['asset']}  {task['template_id']}")


def cmd_dispatches(args: argparse.Namespace) -> None:
    require_args_permission(args, "dispatch:read")
    paths = build_paths(Path(args.env_root))
    state = load_state(paths)
    records = state["dispatch_records"]
    if args.case_id:
        records = [item for item in records if item.get("case_id") == args.case_id]
    if args.task_id:
        records = [item for item in records if item["task_id"] == args.task_id]
    if args.format == "json":
        print_json(records)
        return
    for record in records:
        artifacts = ", ".join(record["artifacts"])
        print(f"{record['id']}  {record['status']}  {record['case_id']}  {record['task_id']}  {record['mode']}")
        print(f"  artifacts: {artifacts}")


def build_dispatch_payload(task: dict[str, Any], template: dict[str, Any], operator: str, mode: str) -> dict[str, Any]:
    return {
        "schema": 1,
        "mode": mode,
        "created_at": utc_now(),
        "operator": operator,
        "case_id": task["case_id"],
        "task_id": task["id"],
        "asset": task["asset"],
        "approval": {
            "required": task["approval_required"],
            "approved_by": task.get("approved_by"),
            "approved_at": task.get("approved_at"),
            "reason": task.get("approval_reason"),
        },
        "velociraptor_collection_request": {
            "client_id_or_hostname": task["asset"],
            "artifacts": [
                {
                    "name": artifact_name,
                    "parameters": template.get("default_parameters", {}),
                }
                for artifact_name in task["velociraptor_artifacts"]
            ],
            "max_rows": template.get("default_parameters", {}).get("max_rows", 5000),
            "comment": f"{task['case_id']} {task['id']} {task['reason']}",
        },
        "safety_scope": task["safety_scope"],
        "note": "Dry-run plans are auditable dispatch packages. Configure a real Velociraptor API client outside source before live execution.",
    }


def write_dispatch_plan(paths: EnvPaths, state: dict[str, Any], task: dict[str, Any], operator: str, mode: str, role: str = "admin") -> dict[str, Any]:
    if task["approval_required"] and not task.get("approved_by"):
        raise SystemExit("Sensitive collection task must be approved before generating a dispatch plan.")
    template = find_template(task["template_id"])
    payload = build_dispatch_payload(task, template, operator, mode)
    dispatch_dir = paths.upload_dir / "dispatch" / task["case_id"]
    dispatch_dir.mkdir(parents=True, exist_ok=True)
    plan_path = dispatch_dir / f"{task['id']}-velociraptor-dispatch.json"
    write_json(plan_path, payload)
    plan = {
        "id": f"PLAN-{uuid.uuid4().hex[:8].upper()}",
        "case_id": task["case_id"],
        "task_id": task["id"],
        "mode": mode,
        "operator": operator,
        "path": str(plan_path),
        "sha256": sha256_file(plan_path),
        "created_at": payload["created_at"],
        "status": "DRY_RUN_READY" if mode == "dry-run" else "API_READY",
    }
    state["dispatch_plans"] = [item for item in state["dispatch_plans"] if item.get("task_id") != task["id"]]
    state["dispatch_plans"].append(plan)
    for record in state["dispatch_records"]:
        if record.get("task_id") == task["id"]:
            record["status"] = "DISPATCH_PLAN_READY"
            record["updated_at"] = utc_now()
            record["plan_path"] = str(plan_path)
            record["plan_sha256"] = plan["sha256"]
    if task["status"] == "APPROVED":
        task["status"] = "DISPATCH_PLAN_READY"
    save_state(paths, state)
    audit(paths, "dispatch.plan.generated", {"case_id": task["case_id"], "task_id": task["id"], "path": str(plan_path), "sha256": plan["sha256"], "operator": operator, "role": role})
    return plan


def cmd_dispatch_plan(args: argparse.Namespace) -> None:
    require_args_permission(args, "dispatch:plan")
    paths = build_paths(Path(args.env_root))
    state = load_state(paths)
    task = find_task(state, args.task_id)
    plan = write_dispatch_plan(paths, state, task, args.operator, args.mode, actor_role(args))
    print_json(plan)


def build_timeline(collection: dict[str, Any], task: dict[str, Any]) -> list[dict[str, Any]]:
    timeline: list[dict[str, Any]] = []
    for category, rows in collection["results"].items():
        event_type = CATEGORY_EVENT_TYPES.get(category, category.split("_")[0])
        for row in rows:
            event_time = row.get("time") or row.get("first_seen") or row.get("last_seen") or collection["collected_at"]
            timeline.append(
                {
                    "time": event_time,
                    "case_id": task["case_id"],
                    "task_id": task["id"],
                    "asset": task["asset"],
                    "event_type": event_type,
                    "category": category,
                    "severity": row.get("severity", "info"),
                    "summary": row.get("summary") or row.get("name") or row.get("path") or str(row),
                    "source_id": row.get("id"),
                    "raw": row,
                }
            )
    return sorted(timeline, key=lambda item: item["time"])


def summarize_results(results: dict[str, list[dict[str, Any]]]) -> dict[str, Any]:
    severity_order = {"critical": 4, "high": 3, "medium": 2, "low": 1, "info": 0}
    counts = {name: len(rows) for name, rows in results.items()}
    max_severity = "info"
    for rows in results.values():
        for row in rows:
            severity = row.get("severity", "info")
            if severity_order.get(severity, 0) > severity_order.get(max_severity, 0):
                max_severity = severity
    return {"counts": counts, "max_severity": max_severity, "total_rows": sum(counts.values())}


def create_evidence_package(paths: EnvPaths, task: dict[str, Any], collection: dict[str, Any], timeline: list[dict[str, Any]], collector: str) -> dict[str, Any]:
    evidence_id = f"EVD-{uuid.uuid4().hex[:10].upper()}"
    evidence_dir = paths.upload_dir / "evidence" / task["case_id"]
    evidence_dir.mkdir(parents=True, exist_ok=True)
    package_path = evidence_dir / f"{evidence_id}-{task['id']}.zip"
    manifest = {
        "evidence_id": evidence_id,
        "case_id": task["case_id"],
        "task_id": task["id"],
        "asset": task["asset"],
        "collector": collector,
        "created_at": utc_now(),
        "source": "synthetic demo import",
        "authorized_scope": task["safety_scope"],
        "files": ["collection.json", "timeline.csv", "manifest.json"],
    }
    with zipfile.ZipFile(package_path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("collection.json", json.dumps(collection, ensure_ascii=False, indent=2))
        archive.writestr("manifest.json", json.dumps(manifest, ensure_ascii=False, indent=2))
        rows = ["time,event_type,category,severity,summary"]
        for event in timeline:
            row = [event["time"], event["event_type"], event["category"], event["severity"], event["summary"].replace("\n", " ")]
            rows.append(",".join(csv_escape(value) for value in row))
        archive.writestr("timeline.csv", "\n".join(rows) + "\n")
    package_hash = sha256_file(package_path)
    return {
        "evidence_id": evidence_id,
        "case_id": task["case_id"],
        "task_id": task["id"],
        "hash": package_hash,
        "hash_algorithm": "sha256",
        "source": "demo_collection",
        "collector": collector,
        "collected_at": manifest["created_at"],
        "storage_path": str(package_path),
        "manifest": manifest,
    }


def cmd_import_demo(args: argparse.Namespace) -> None:
    require_args_permission(args, "result:import")
    paths = build_paths(Path(args.env_root))
    state = load_state(paths)
    task = find_task(state, args.task_id)
    if task["approval_required"] and not task.get("approved_by") and not args.allow_unapproved:
        raise SystemExit("Sensitive collection task must be approved before import or execution.")
    collection = load_json(DEMO_PATH)
    timeline = build_timeline(collection, task)
    evidence = create_evidence_package(paths, task, collection, timeline, args.collector)
    summary = summarize_results(collection["results"])
    state["results"][task["id"]] = {
        "case_id": task["case_id"],
        "task_id": task["id"],
        "asset": task["asset"],
        "imported_at": utc_now(),
        "source": str(DEMO_PATH),
        "summary": summary,
        "table": flatten_results(collection["results"]),
        "raw_json": collection,
        "attachments": [evidence["storage_path"]],
        "evidence_summary": {
            "evidence_id": evidence["evidence_id"],
            "hash": evidence["hash"],
            "storage_path": evidence["storage_path"],
        },
    }
    state["timeline"][task["id"]] = timeline
    state["evidence_packages"] = [item for item in state["evidence_packages"] if item["task_id"] != task["id"]]
    state["evidence_packages"].append(evidence)
    state["evidence_records"] = [item for item in state["evidence_records"] if item["task_id"] != task["id"]]
    state["evidence_records"].append({k: evidence[k] for k in ("evidence_id", "case_id", "task_id", "hash", "hash_algorithm", "source", "collector", "collected_at", "storage_path")})
    for record in state["dispatch_records"]:
        if record["task_id"] == task["id"]:
            record["status"] = "RESULT_IMPORTED"
            record["updated_at"] = utc_now()
    task["status"] = "RESULT_IMPORTED"
    case = find_case(state, task["case_id"])
    case["status"] = "RESULT_IMPORTED"
    case["updated_at"] = utc_now()
    save_state(paths, state)
    audit(paths, "result.imported", {"case_id": task["case_id"], "task_id": task["id"], "events": len(timeline), "evidence_id": evidence["evidence_id"], "role": actor_role(args)})
    print_json({"case_id": task["case_id"], "task_id": task["id"], "rows": summary["total_rows"], "evidence": evidence})


def flatten_results(results: dict[str, list[dict[str, Any]]]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for category, items in results.items():
        for item in items:
            rows.append(
                {
                    "category": category,
                    "time": item.get("time") or item.get("first_seen") or item.get("last_seen"),
                    "severity": item.get("severity", "info"),
                    "summary": item.get("summary") or item.get("name") or item.get("path"),
                    "raw": item,
                }
            )
    return sorted(rows, key=lambda item: item["time"] or "")


def cmd_results(args: argparse.Namespace) -> None:
    require_args_permission(args, "result:read")
    paths = build_paths(Path(args.env_root))
    state = load_state(paths)
    task = find_task(state, args.task_id)
    result = state["results"].get(task["id"])
    if not result:
        raise SystemExit(f"No results for task: {task['id']}")
    if args.format == "json" or args.view == "raw":
        print_json(result["raw_json"] if args.view == "raw" else result)
        return
    if args.view == "attachments":
        for attachment in result["attachments"]:
            print(attachment)
        return
    if args.view == "evidence":
        print_json(result["evidence_summary"])
        return
    if args.view == "summary":
        print_json(result["summary"])
        return
    for row in result["table"]:
        print(f"{row['time'] or '-'}  {row['severity']:8}  {row['category']:20}  {row['summary']}")


def cmd_timeline(args: argparse.Namespace) -> None:
    require_args_permission(args, "timeline:read")
    paths = build_paths(Path(args.env_root))
    state = load_state(paths)
    if args.case_id:
        timeline = case_timeline(state, args.case_id)
    else:
        task = find_task(state, args.task_id)
        timeline = state["timeline"].get(task["id"], [])
    if not timeline:
        raise SystemExit("No timeline events found.")
    if args.format == "json":
        print_json(timeline)
        return
    for event in timeline:
        print(f"{event['time']}  {event['severity']:8}  {event['event_type']:8}  {event['category']:20}  {event['summary']}")


def cmd_evidence(args: argparse.Namespace) -> None:
    require_args_permission(args, "evidence:read")
    paths = build_paths(Path(args.env_root))
    state = load_state(paths)
    records = state["evidence_records"]
    if args.case_id:
        records = [item for item in records if item.get("case_id") == args.case_id]
    if args.task_id:
        records = [item for item in records if item.get("task_id") == args.task_id]
    if args.format == "json":
        print_json(records)
        return
    for record in records:
        print(f"{record['evidence_id']}  {record['hash_algorithm']}:{record['hash']}")
        print(f"  case={record['case_id']} task={record['task_id']} collector={record['collector']} time={record['collected_at']}")
        print(f"  storage={record['storage_path']}")


def cmd_dispose(args: argparse.Namespace) -> None:
    require_args_permission(args, "disposition:record")
    paths = build_paths(Path(args.env_root))
    state = load_state(paths)
    case_id = args.case_id
    if args.task_id:
        case_id = find_task(state, args.task_id)["case_id"]
    if not case_id:
        raise SystemExit("Provide --case-id or --task-id.")
    case = find_case(state, case_id)
    record = {
        "id": f"DISPOSITION-{uuid.uuid4().hex[:8].upper()}",
        "case_id": case_id,
        "task_id": args.task_id,
        "analysis_conclusion": args.conclusion or args.note,
        "recommended_action": args.recommendation or args.action,
        "reviewer": args.reviewer,
        "owner": args.owner,
        "status": args.status,
        "created_at": utc_now(),
    }
    state["dispositions"].append(record)
    case["status"] = args.status
    case["updated_at"] = utc_now()
    save_state(paths, state)
    audit(paths, "disposition.recorded", {"case_id": case_id, "task_id": args.task_id, "status": args.status, "reviewer": args.reviewer, "role": actor_role(args)})
    print_json(record)


def verify_file_record(record_type: str, record_id: str, path_value: str, expected_hash: str) -> dict[str, Any]:
    path = Path(path_value)
    exists = path.exists()
    actual_hash = sha256_file(path) if exists else None
    return {
        "type": record_type,
        "id": record_id,
        "path": path_value,
        "exists": exists,
        "expected_sha256": expected_hash,
        "actual_sha256": actual_hash,
        "ok": exists and actual_hash == expected_hash,
    }


def verify_case_chain(paths: EnvPaths, state: dict[str, Any], case: dict[str, Any], role: str = "admin") -> dict[str, Any]:
    case_id = case["case_id"]
    checks: list[dict[str, Any]] = []
    for item in state["evidence_records"]:
        if item.get("case_id") == case_id:
            checks.append(verify_file_record("evidence", item["evidence_id"], item["storage_path"], item["hash"]))
    for item in state["reports"]:
        if item.get("case_id") == case_id:
            checks.append(verify_file_record("report", case_id, item["path"], item["sha256"]))
    for item in case_dispatch_plans(state, case_id):
        checks.append(verify_file_record("dispatch_plan", item["id"], item["path"], item["sha256"]))
    failed = [item for item in checks if not item["ok"]]
    verification = {
        "id": f"VERIFY-{uuid.uuid4().hex[:8].upper()}",
        "case_id": case_id,
        "created_at": utc_now(),
        "status": "PASS" if checks and not failed else "FAIL",
        "checked": len(checks),
        "failed": len(failed),
        "checks": checks,
    }
    state["chain_verifications"].append(verification)
    save_state(paths, state)
    audit(paths, "chain.verified", {"case_id": case_id, "status": verification["status"], "checked": verification["checked"], "failed": verification["failed"], "role": role})
    return verification


def cmd_verify_chain(args: argparse.Namespace) -> None:
    require_args_permission(args, "chain:verify")
    paths = build_paths(Path(args.env_root))
    state = load_state(paths)
    case = find_case(state, args.case_id)
    verification = verify_case_chain(paths, state, case, actor_role(args))
    if args.format == "json":
        print_json(verification)
        return
    print(f"{verification['case_id']} {verification['status']} checked={verification['checked']} failed={verification['failed']}")
    for item in verification["checks"]:
        print(f"- {item['type']} {item['id']} ok={item['ok']} path={item['path']}")


def cmd_close_case(args: argparse.Namespace) -> None:
    require_args_permission(args, "case:close")
    paths = build_paths(Path(args.env_root))
    state = load_state(paths)
    case = find_case(state, args.case_id)
    evidence = [item for item in state["evidence_records"] if item.get("case_id") == args.case_id]
    reports = [item for item in state["reports"] if item.get("case_id") == args.case_id]
    dispositions = [item for item in state["dispositions"] if item.get("case_id") == args.case_id]
    if not evidence:
        raise SystemExit("Case cannot close without evidence records.")
    if not reports:
        raise SystemExit("Case cannot close without a forensic report.")
    if not dispositions:
        raise SystemExit("Case cannot close without a disposition record.")
    verification = verify_case_chain(paths, state, case, actor_role(args))
    if verification["status"] != "PASS":
        raise SystemExit("Case cannot close because evidence chain verification failed.")
    closure = {
        "id": f"CLOSURE-{uuid.uuid4().hex[:8].upper()}",
        "case_id": args.case_id,
        "task_id": None,
        "analysis_conclusion": "Case reviewed and closed.",
        "recommended_action": args.reason,
        "reviewer": args.reviewer,
        "owner": case["owner"],
        "status": "CLOSED",
        "created_at": utc_now(),
        "verification_id": verification["id"],
    }
    state["dispositions"].append(closure)
    case["status"] = "CLOSED"
    case["closed_by"] = args.reviewer
    case["closed_at"] = closure["created_at"]
    case["close_reason"] = args.reason
    case["updated_at"] = closure["created_at"]
    save_state(paths, state)
    audit(paths, "case.closed", {"case_id": args.case_id, "reviewer": args.reviewer, "verification_id": verification["id"], "role": actor_role(args)})
    print_json({"case": case, "closure": closure, "verification": verification})


def generate_case_report(paths: EnvPaths, state: dict[str, Any], case: dict[str, Any]) -> dict[str, Any]:
    tasks = case_tasks(state, case["case_id"])
    timeline = case_timeline(state, case["case_id"])
    evidence = [item for item in state["evidence_records"] if item.get("case_id") == case["case_id"]]
    dispositions = [item for item in state["dispositions"] if item.get("case_id") == case["case_id"]]
    if not tasks:
        raise SystemExit(f"Case has no tasks: {case['case_id']}")
    if not timeline:
        raise SystemExit(f"Case has no timeline: {case['case_id']}")
    report_dir = paths.docs_dir / "reports" / case["case_id"]
    report_dir.mkdir(parents=True, exist_ok=True)
    report_path = report_dir / f"{case['case_id']}-forensic-report.md"
    lines = [
        f"# Forensic Report: {case['case_id']}",
        "",
        "## Case Overview",
        f"- Asset: {case['asset']}",
        f"- Incident type: {case['incident_type']}",
        f"- Severity: {case['severity']}",
        f"- Owner: {case['owner']}",
        f"- Status: {case['status']}",
        f"- Description: {case['description']}",
        "",
        "## Collection Tasks",
    ]
    for task in tasks:
        lines.append(f"- {task['id']} {task['status']} {task['template_name']} approved_by={task.get('approved_by') or '-'} reason={task['reason']}")
    lines.extend(["", "## Evidence List"])
    for item in evidence:
        lines.append(f"- {item['evidence_id']} {item['hash_algorithm']}:{item['hash']}")
        lines.append(f"  - source: {item['source']}")
        lines.append(f"  - collector: {item['collector']}")
        lines.append(f"  - collected_at: {item['collected_at']}")
        lines.append(f"  - storage: {item['storage_path']}")
    lines.extend(["", "## Timeline"])
    for event in timeline:
        lines.append(f"- {event['time']} [{event['severity']}] {event['event_type']}/{event['category']}: {event['summary']}")
    lines.extend(["", "## Conclusion And Remediation"])
    if dispositions:
        for item in dispositions:
            lines.append(f"- Conclusion: {item['analysis_conclusion']}")
            lines.append(f"- Recommended action: {item['recommended_action']}")
            lines.append(f"- Reviewer: {item['reviewer']}")
            lines.append(f"- Disposition status: {item['status']}")
    else:
        lines.append("- No disposition record yet.")
    lines.extend(
        [
            "",
            "## Audit And Safety Notes",
            "- Sensitive collection tasks require explicit approval before execution.",
            "- This platform wrapper stores runtime records, uploads, evidence packages, logs, and reports under Environment.",
            "- It does not implement unauthorized collection, credential theft, privacy theft, exploitation, or lateral movement.",
            "",
        ]
    )
    report_path.write_text("\n".join(lines), encoding="utf-8")
    return {"case_id": case["case_id"], "path": str(report_path), "sha256": sha256_file(report_path), "created_at": utc_now()}


def cmd_report(args: argparse.Namespace) -> None:
    require_args_permission(args, "report:generate")
    paths = build_paths(Path(args.env_root))
    state = load_state(paths)
    if args.case_id:
        case = find_case(state, args.case_id)
    else:
        case = find_case(state, find_task(state, args.task_id)["case_id"])
    case["status"] = "REPORTED" if case["status"] != "CLOSED" else case["status"]
    case["updated_at"] = utc_now()
    report = generate_case_report(paths, state, case)
    state["reports"] = [item for item in state["reports"] if item.get("case_id") != case["case_id"]]
    state["reports"].append(report)
    save_state(paths, state)
    audit(paths, "report.generated", {"case_id": case["case_id"], "report": report["path"], "sha256": report["sha256"], "role": actor_role(args)})
    print_json(report)


def cmd_run_demo(args: argparse.Namespace) -> None:
    require_args_permission(args, "demo:run")
    paths = build_paths(Path(args.env_root))
    ensure_paths(paths)
    case_args = argparse.Namespace(
        env_root=args.env_root,
        case_id=args.case_id,
        asset=args.asset,
        incident_type="malware_triage",
        severity="high",
        owner=args.operator,
        status="OPEN",
        description="Production closed-loop demo for authorized endpoint DFIR.",
    )
    cmd_create_case(case_args)
    task_args = argparse.Namespace(
        env_root=args.env_root,
        case_id=args.case_id,
        template="ir_triage",
        target=args.asset,
        reason="Authorized baseline endpoint triage for case validation.",
        operator=args.operator,
        approved_by=None,
        approval_reason=None,
    )
    before = load_state(paths)
    before_ids = {task["id"] for task in before["tasks"]}
    cmd_create_task(task_args)
    state = load_state(paths)
    task = next(task for task in state["tasks"] if task["id"] not in before_ids)
    approve_args = argparse.Namespace(env_root=args.env_root, task_id=task["id"], approver=args.approved_by, reason="Validated business approval for demo sensitive collection.", force=False)
    cmd_approve_task(approve_args)
    dispatch_args = argparse.Namespace(env_root=args.env_root, task_id=task["id"], operator=args.operator, mode="dry-run")
    cmd_dispatch_plan(dispatch_args)
    import_args = argparse.Namespace(env_root=args.env_root, task_id=task["id"], collector=args.operator, allow_unapproved=False)
    cmd_import_demo(import_args)
    dispose_args = argparse.Namespace(
        env_root=args.env_root,
        case_id=args.case_id,
        task_id=task["id"],
        conclusion="Suspicious process chain with correlated outbound connection and persistence indicator in synthetic lab data.",
        recommendation="Preserve evidence, isolate the endpoint in a lab VLAN, remove unauthorized startup entry after approval, and rotate exposed user credentials if confirmed.",
        reviewer=args.approved_by,
        owner=args.operator,
        status="READY_FOR_REVIEW",
        action=None,
        note=None,
    )
    cmd_dispose(dispose_args)
    report_args = argparse.Namespace(env_root=args.env_root, case_id=args.case_id, task_id=None)
    cmd_report(report_args)
    final_state = load_state(paths)
    print_json(
        {
            "closed_loop": "ok",
            "case": find_case(final_state, args.case_id),
            "tasks": case_tasks(final_state, args.case_id),
            "evidence": [item for item in final_state["evidence_records"] if item.get("case_id") == args.case_id],
            "reports": [item for item in final_state["reports"] if item.get("case_id") == args.case_id],
        }
    )


def cmd_serve_web(args: argparse.Namespace) -> None:
    require_args_permission(args, "web:serve")
    paths = build_paths(Path(args.env_root))
    ensure_paths(paths)
    load_state(paths)
    server = ThreadingHTTPServer((args.host, args.port), IRConsoleHandler)
    server.ir_paths = paths  # type: ignore[attr-defined]
    server.ir_role = actor_role(args)  # type: ignore[attr-defined]
    print(f"IR console listening on http://{args.host}:{args.port}")
    print(f"Console role: {actor_role(args)}")
    print(f"Runtime state: {paths.state_file}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Authorized endpoint forensics and IR management wrapper for Velociraptor.")
    parser.add_argument("--env-root", default=str(DEFAULT_ENV_ROOT), help="Environment root for runtime data.")
    parser.add_argument("--role", choices=sorted(ROLE_PERMISSIONS), default=DEFAULT_ROLE, help="Execution role for local permission checks.")
    subparsers = parser.add_subparsers(dest="command", required=True)

    init_parser = subparsers.add_parser("init", help="Create Environment runtime directories and state store.")
    init_parser.add_argument("--operator", default="zhangjiyan")
    init_parser.set_defaults(func=cmd_init)

    doctor_parser = subparsers.add_parser("doctor", help="Show runtime path policy checks.")
    doctor_parser.set_defaults(func=cmd_doctor)

    case_parser = subparsers.add_parser("create-case", help="Create an emergency incident case.")
    case_parser.add_argument("--case-id")
    case_parser.add_argument("--asset", required=True)
    case_parser.add_argument("--incident-type", required=True)
    case_parser.add_argument("--severity", choices=["critical", "high", "medium", "low"], required=True)
    case_parser.add_argument("--owner", default="zhangjiyan")
    case_parser.add_argument("--status", default="OPEN")
    case_parser.add_argument("--description", default="")
    case_parser.set_defaults(func=cmd_create_case)

    cases_parser = subparsers.add_parser("cases", help="List emergency incident cases.")
    cases_parser.add_argument("--format", choices=["text", "json"], default="text")
    cases_parser.set_defaults(func=cmd_cases)

    templates_parser = subparsers.add_parser("templates", help="List authorized collection templates.")
    templates_parser.add_argument("--format", choices=["text", "json"], default="text")
    templates_parser.set_defaults(func=cmd_templates)

    create_parser = subparsers.add_parser("create-task", help="Create a collection task under an existing case.")
    create_parser.add_argument("--case-id", required=True)
    create_parser.add_argument("--template", required=True)
    create_parser.add_argument("--target")
    create_parser.add_argument("--reason", required=True)
    create_parser.add_argument("--operator", default="zhangjiyan")
    create_parser.add_argument("--approved-by")
    create_parser.add_argument("--approval-reason")
    create_parser.set_defaults(func=cmd_create_task)

    approve_parser = subparsers.add_parser("approve-task", help="Approve a sensitive collection task.")
    approve_parser.add_argument("--task-id", required=True)
    approve_parser.add_argument("--approver", required=True)
    approve_parser.add_argument("--reason", required=True)
    approve_parser.add_argument("--force", action="store_true")
    approve_parser.set_defaults(func=cmd_approve_task)

    tasks_parser = subparsers.add_parser("tasks", help="List task records.")
    tasks_parser.add_argument("--case-id")
    tasks_parser.add_argument("--format", choices=["text", "json"], default="text")
    tasks_parser.set_defaults(func=cmd_tasks)

    dispatches_parser = subparsers.add_parser("dispatches", help="List planned task dispatch records.")
    dispatches_parser.add_argument("--case-id")
    dispatches_parser.add_argument("--task-id")
    dispatches_parser.add_argument("--format", choices=["text", "json"], default="text")
    dispatches_parser.set_defaults(func=cmd_dispatches)

    dispatch_plan_parser = subparsers.add_parser("dispatch-plan", help="Generate an auditable Velociraptor collection request for an approved task.")
    dispatch_plan_parser.add_argument("--task-id", required=True)
    dispatch_plan_parser.add_argument("--operator", default="zhangjiyan")
    dispatch_plan_parser.add_argument("--mode", choices=["dry-run", "api"], default="dry-run")
    dispatch_plan_parser.set_defaults(func=cmd_dispatch_plan)

    import_parser = subparsers.add_parser("import-demo", help="Import synthetic demo collection results for an approved task.")
    import_parser.add_argument("--task-id", required=True)
    import_parser.add_argument("--collector", default="zhangjiyan")
    import_parser.add_argument("--allow-unapproved", action="store_true")
    import_parser.set_defaults(func=cmd_import_demo)

    results_parser = subparsers.add_parser("results", help="Show collection result views.")
    results_parser.add_argument("--task-id", required=True)
    results_parser.add_argument("--view", choices=["table", "raw", "attachments", "evidence", "summary"], default="table")
    results_parser.add_argument("--format", choices=["text", "json"], default="text")
    results_parser.set_defaults(func=cmd_results)

    timeline_parser = subparsers.add_parser("timeline", help="Show task or case event timeline.")
    target = timeline_parser.add_mutually_exclusive_group(required=True)
    target.add_argument("--task-id")
    target.add_argument("--case-id")
    timeline_parser.add_argument("--format", choices=["text", "json"], default="text")
    timeline_parser.set_defaults(func=cmd_timeline)

    evidence_parser = subparsers.add_parser("evidence", help="Show evidence chain records.")
    evidence_target = evidence_parser.add_mutually_exclusive_group(required=True)
    evidence_target.add_argument("--task-id")
    evidence_target.add_argument("--case-id")
    evidence_parser.add_argument("--format", choices=["text", "json"], default="text")
    evidence_parser.set_defaults(func=cmd_evidence)

    dispose_parser = subparsers.add_parser("dispose", help="Record analysis, recommendation, review, and case status.")
    dispose_target = dispose_parser.add_mutually_exclusive_group(required=True)
    dispose_target.add_argument("--case-id")
    dispose_target.add_argument("--task-id")
    dispose_parser.add_argument("--conclusion")
    dispose_parser.add_argument("--recommendation")
    dispose_parser.add_argument("--reviewer", required=True)
    dispose_parser.add_argument("--owner", default="zhangjiyan")
    dispose_parser.add_argument("--status", default="READY_FOR_REVIEW")
    dispose_parser.add_argument("--action")
    dispose_parser.add_argument("--note")
    dispose_parser.set_defaults(func=cmd_dispose)

    verify_parser = subparsers.add_parser("verify-chain", help="Verify evidence, dispatch plan, and report hashes for a case.")
    verify_parser.add_argument("--case-id", required=True)
    verify_parser.add_argument("--format", choices=["text", "json"], default="text")
    verify_parser.set_defaults(func=cmd_verify_chain)

    close_parser = subparsers.add_parser("close-case", help="Close a case after evidence, disposition, report, and chain verification pass.")
    close_parser.add_argument("--case-id", required=True)
    close_parser.add_argument("--reviewer", required=True)
    close_parser.add_argument("--reason", required=True)
    close_parser.set_defaults(func=cmd_close_case)

    report_parser = subparsers.add_parser("report", help="Generate a Markdown forensic report.")
    report_target = report_parser.add_mutually_exclusive_group(required=True)
    report_target.add_argument("--case-id")
    report_target.add_argument("--task-id")
    report_parser.set_defaults(func=cmd_report)

    demo_parser = subparsers.add_parser("run-demo", help="Run the full production loop: case, approval, results, timeline, report.")
    demo_parser.add_argument("--case-id", default=f"CASE-DEMO-{datetime.now(timezone.utc).strftime('%Y%m%d%H%M%S')}")
    demo_parser.add_argument("--asset", default="demo-endpoint-01")
    demo_parser.add_argument("--operator", default="zhangjiyan")
    demo_parser.add_argument("--approved-by", default="demo-approver")
    demo_parser.set_defaults(func=cmd_run_demo)

    web_parser = subparsers.add_parser("serve-web", help="Start the local IR management web console.")
    web_parser.add_argument("--host", default="127.0.0.1")
    web_parser.add_argument("--port", type=int, default=8765)
    web_parser.set_defaults(func=cmd_serve_web)
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    args.func(args)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

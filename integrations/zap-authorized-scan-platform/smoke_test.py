#!/usr/bin/env python3
"""End-to-end smoke test for the authorized scan platform."""

from __future__ import annotations

import http.client
import json
import socket
import subprocess
import sys
import tempfile
import time
from pathlib import Path


ROOT = Path(__file__).resolve().parent


def free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


def request(port: int, method: str, path: str, payload: dict | None = None) -> tuple[int, dict]:
    body = json.dumps(payload or {}, ensure_ascii=False).encode("utf-8") if payload is not None else None
    conn = http.client.HTTPConnection("127.0.0.1", port, timeout=5)
    headers = {"Content-Type": "application/json"} if body is not None else {}
    conn.request(method, path, body=body, headers=headers)
    response = conn.getresponse()
    raw = response.read()
    conn.close()
    data = json.loads(raw.decode("utf-8")) if raw else {}
    return response.status, data


def wait_ready(port: int) -> None:
    deadline = time.time() + 8
    while time.time() < deadline:
        try:
            status, _ = request(port, "GET", "/api/health")
            if status == 200:
                return
        except OSError:
            time.sleep(0.15)
    raise RuntimeError("server did not become ready")


def assert_status(actual: int, expected: int, payload: dict) -> None:
    if actual != expected:
        raise AssertionError(f"expected HTTP {expected}, got {actual}: {payload}")


def post(port: int, path: str, payload: dict) -> dict:
    status, data = request(port, "POST", path, payload)
    assert_status(status, 201 if path in {"/api/targets", "/api/tasks"} or "/export" in path or "/retest" in path and path.startswith("/api/tasks/") or "/ci-gate" in path else 200, data)
    return data


def main() -> int:
    port = free_port()
    with tempfile.TemporaryDirectory(prefix="zap-platform-") as env_root:
        process = subprocess.Popen(
            [
                sys.executable,
                str(ROOT / "app.py"),
                "--host",
                "127.0.0.1",
                "--port",
                str(port),
                "--env-root",
                env_root,
            ],
            cwd=ROOT,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
        )
        try:
            wait_ready(port)

            status, payload = request(
                port,
                "POST",
                "/api/targets",
                {
                    "name": "未授权目标",
                    "base_url": "http://127.0.0.1:8080",
                    "scope_prefix": "http://127.0.0.1:8080",
                    "owner": "qa",
                    "authorization_note": "missing confirmation",
                    "authorization_confirmed": False,
                    "valid_until": "2099-12-31",
                },
            )
            assert_status(status, 400, payload)

            status, payload = request(
                port,
                "POST",
                "/api/targets",
                {
                    "name": "内部测试门户",
                    "base_url": "http://127.0.0.1:8080",
                    "scope_prefix": "http://127.0.0.1:8080/app",
                    "owner": "security-team",
                    "authorization_note": "AUTH-2026-001 testing environment approval",
                    "authorization_confirmed": True,
                    "valid_from": "2026-01-01",
                    "valid_until": "2099-12-31",
                    "allowlist": ["http://127.0.0.1:8080/app*"],
                    "blocklist": ["http://127.0.0.1:8080/app/private*"],
                },
            )
            assert_status(status, 201, payload)
            target_id = payload["target"]["id"]
            if not payload["target"]["allowlist"] or not payload["target"]["valid_until"]:
                raise AssertionError("target authorization metadata missing")

            status, payload = request(
                port,
                "POST",
                "/api/tasks",
                {
                    "target_id": target_id,
                    "name": "危险动作扫描",
                    "scan_type": "full",
                    "dangerous_actions_enabled": True,
                    "valid_until": "2099-12-31",
                },
            )
            assert_status(status, 400, payload)

            status, payload = request(
                port,
                "POST",
                "/api/tasks",
                {
                    "target_id": target_id,
                    "name": "排队扫描",
                    "scan_type": "baseline",
                    "queue_only": True,
                    "timeout_seconds": 120,
                    "rate_limit_per_min": 10,
                },
            )
            assert_status(status, 201, payload)
            queued_id = payload["task"]["id"]
            status, payload = request(port, "POST", f"/api/tasks/{queued_id}/cancel", {"reason": "验收取消排队任务"})
            assert_status(status, 200, payload)
            if payload["task"]["status"] != "canceled":
                raise AssertionError("queued task was not canceled")

            status, payload = request(
                port,
                "POST",
                "/api/tasks",
                {
                    "target_id": target_id,
                    "name": "授权 API 扫描",
                    "scan_type": "api",
                    "policy": "api-readonly",
                    "risk_gate": "Medium",
                },
            )
            assert_status(status, 201, payload)
            if payload["task"]["scan_type"] != "api":
                raise AssertionError("api scan type was not accepted")

            status, payload = request(
                port,
                "POST",
                "/api/tasks",
                {
                    "target_id": target_id,
                    "name": "授权 full 扫描",
                    "scan_type": "full",
                    "policy": "standard-readonly",
                    "risk_gate": "High",
                    "timeout_seconds": 300,
                    "rate_limit_per_min": 30,
                },
            )
            assert_status(status, 201, payload)
            task = payload["task"]
            task_id = task["id"]
            vulnerabilities = task["vulnerabilities"]
            if len(vulnerabilities) < 5:
                raise AssertionError("expected full scan demo vulnerabilities")
            if not all(vuln.get("cwe") and vuln.get("wasc") and vuln.get("fingerprint") for vuln in vulnerabilities):
                raise AssertionError("vulnerability center fields missing")

            status, payload = request(port, "POST", f"/api/tasks/{task_id}/ci-gate", {"threshold": "High"})
            assert_status(status, 201, payload)
            if payload["ci_gate"]["status"] != "failed":
                raise AssertionError("CI gate should fail on active High+ findings")

            first_vuln = next(vuln for vuln in vulnerabilities if vuln["risk"] == "High")
            status, payload = request(
                port,
                "POST",
                f"/api/vulnerabilities/{first_vuln['id']}/mark-fixed",
                {"note": "已补充 CSP 策略"},
            )
            assert_status(status, 200, payload)
            if payload["vulnerability"]["status"] != "fixed_pending_retest":
                raise AssertionError("vulnerability was not moved to fixed_pending_retest")

            status, payload = request(port, "POST", f"/api/tasks/{task_id}/retest", {})
            assert_status(status, 201, payload)
            retest_task = payload["task"]
            comparison = retest_task["comparison"]
            if comparison["fixed_count"] < 1 or comparison["carried_count"] < 1:
                raise AssertionError("comparison did not identify fixed and carried vulnerabilities")

            status, detail = request(port, "GET", f"/api/tasks/{task_id}")
            assert_status(status, 200, detail)
            fixed_original = [
                vuln for vuln in detail["task"]["vulnerabilities"]
                if vuln["id"] == first_vuln["id"]
            ][0]
            if fixed_original["status"] != "retest_passed":
                raise AssertionError("original vulnerability was not marked retest_passed")

            status, payload = request(
                port,
                "POST",
                f"/api/vulnerabilities/{first_vuln['id']}/close",
                {"closure_reason": "复测通过，整改关闭"},
            )
            assert_status(status, 200, payload)
            if payload["vulnerability"]["status"] != "closed":
                raise AssertionError("vulnerability was not closed after retest")

            status, payload = request(
                port,
                "POST",
                f"/api/tasks/{task_id}/export",
                {"report_type": "scan"},
            )
            assert_status(status, 201, payload)
            if payload["report"]["report_type"] != "scan":
                raise AssertionError("scan report was not exported")

            status, payload = request(
                port,
                "POST",
                f"/api/tasks/{retest_task['id']}/export",
                {"report_type": "retest"},
            )
            assert_status(status, 201, payload)
            if payload["report"]["report_type"] != "retest":
                raise AssertionError("retest report was not exported")

            status, payload = request(
                port,
                "POST",
                f"/api/tasks/{task_id}/export",
                {"report_type": "remediation"},
            )
            assert_status(status, 201, payload)
            report_path = Path(payload["report"]["path"])
            if payload["report"]["report_type"] != "remediation":
                raise AssertionError("wrong report type")
            if not report_path.exists() or not report_path.is_file():
                raise AssertionError("report file was not written")
            if not str(report_path.resolve()).startswith(str(Path(env_root).resolve())):
                raise AssertionError("report was not written under env root")

            print("smoke test passed")
            print(f"env_root={env_root}")
            print(f"task_id={task_id}")
            print(f"retest_task_id={retest_task['id']}")
            print(f"report_path={report_path}")
            return 0
        finally:
            process.terminate()
            try:
                process.wait(timeout=4)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=4)
            if process.returncode not in (0, -15, None):
                output = process.stdout.read() if process.stdout else ""
                print(output)


if __name__ == "__main__":
    raise SystemExit(main())

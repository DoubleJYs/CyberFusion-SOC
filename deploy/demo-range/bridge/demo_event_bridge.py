#!/usr/bin/env python3
"""CyberFusion Demo Range event bridge.

Reads offline WAF/ZAP/Trivy outputs and converts them to the existing
CyberFusion import API shape. The default mode is dry-run.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any


DEFAULT_BATCH_ID = "DEMO-RANGE-DOCKER-LOCAL"
SAFE_SOURCE_IP = "203.0.113.120"
SAFE_ASSET_IP = "10.20.1.15"


def main() -> int:
    parser = argparse.ArgumentParser(description="Convert Demo Range outputs to CyberFusion import payloads.")
    parser.add_argument("--dry-run", action="store_true", help="print payloads and do not call CyberFusion")
    parser.add_argument("--send", action="store_true", help="send payloads to CyberFusion import API")
    parser.add_argument("--batch-id", default=os.getenv("DEMO_BATCH_ID", DEFAULT_BATCH_ID))
    parser.add_argument("--api-base", default=os.getenv("CYBERFUSION_API_BASE", "http://host.docker.internal:18080/api"))
    args = parser.parse_args()

    dry_run = args.dry_run or not args.send or env_bool("BRIDGE_DRY_RUN", True)
    batch_id = clean_batch_id(args.batch_id)
    payloads = build_payloads(batch_id)

    print(json.dumps({
        "mode": "dry-run" if dry_run else "send",
        "batchId": batch_id,
        "payloadCount": len(payloads),
        "sources": [payload["sourceType"] for payload in payloads],
    }, ensure_ascii=False))

    for payload in payloads:
        preview = payload.copy()
        preview["contentPreview"] = preview.pop("content")[:1200]
        print(json.dumps(preview, ensure_ascii=False, indent=2))
        if not dry_run:
            send_payload(args.api_base, payload)

    return 0


def build_payloads(batch_id: str) -> list[dict[str, Any]]:
    return [
        {"sourceType": "waf", "content": waf_content(batch_id), "linkAlerts": True},
        {"sourceType": "zap", "content": json.dumps(zap_report(batch_id), ensure_ascii=False), "linkAlerts": True},
        {"sourceType": "trivy", "content": json.dumps(trivy_report(batch_id), ensure_ascii=False), "linkAlerts": True},
    ]


def waf_content(batch_id: str) -> str:
    path = Path(os.getenv("WAF_LOG_PATH", "/runtime/logs/waf/audit.log"))
    portal_path = Path(os.getenv("DEMO_PORTAL_LOG_PATH", "/runtime/logs/demo-target/demo-events.jsonl"))
    records = json_records(path) + json_records(portal_path)
    if not records:
        records = [{
            "sourceType": "waf",
            "eventType": "waf_block",
            "severity": "high",
            "assetIp": SAFE_ASSET_IP,
            "targetUrl": "http://demo-range.local/admin",
            "httpMethod": "GET",
            "httpStatus": 403,
            "action": "block",
            "ruleId": "WAF-DEMO-RANGE-1001",
            "ruleName": "Demo Range WAF policy blocked restricted route",
            "engine": "ModSecurity CRS demo gateway",
            "requestId": f"{batch_id}-waf-0001",
            "demoCaseId": "access-control-risk",
            "batchId": batch_id,
            "evidenceSummary": "WAF gateway produced offline metadata for SOC evidence import.",
            "timestamp": "2026-06-18T10:00:00+08:00",
            "sourceIp": SAFE_SOURCE_IP,
        }]
    converted = [normalize_waf_record(item, batch_id, index) for index, item in enumerate(records, start=1)]
    return "\n".join(json.dumps(item, ensure_ascii=False) for item in converted)


def zap_report(batch_id: str) -> dict[str, Any]:
    path = Path(os.getenv("ZAP_REPORT_PATH", "/runtime/zap/zap-baseline.json"))
    parsed = read_json(path)
    if parsed:
        return stamp_batch(parsed, batch_id)
    return {
        "site": [{
            "@name": "http://waf-gateway:8080",
            "alerts": [{
                "pluginid": "10021",
                "name": "Demo baseline header review",
                "riskdesc": "Medium",
                "url": "http://waf-gateway:8080/login",
                "batchId": batch_id,
                "demoCaseId": "input-validation-risk",
                "evidenceSummary": "ZAP baseline-style report is converted without running active scans.",
            }],
        }],
    }


def trivy_report(batch_id: str) -> dict[str, Any]:
    path = Path(os.getenv("TRIVY_REPORT_PATH", "/runtime/trivy/trivy-demo-enterprise-portal.json"))
    parsed = read_json(path)
    if parsed:
        return stamp_batch(parsed, batch_id)
    return {
        "Results": [{
            "Target": f"demo-target/{batch_id}",
            "Vulnerabilities": [{
                "VulnerabilityID": "CVE-2026-DEMO-RANGE-0002",
                "PkgName": "demo-range-container-lib",
                "InstalledVersion": "1.0.0",
                "FixedVersion": "1.0.1",
                "Severity": "HIGH",
                "Title": "Demo dependency risk for Docker Demo Range validation",
                "batchId": batch_id,
                "demoCaseId": "dependency-vulnerability",
            }],
        }],
    }


def normalize_waf_record(item: dict[str, Any], batch_id: str, index: int) -> dict[str, Any]:
    message = str(item.get("message") or item.get("transaction", {}).get("messages", [{}])[0].get("message", "WAF demo event"))
    request = item.get("request") if isinstance(item.get("request"), dict) else {}
    response = item.get("response") if isinstance(item.get("response"), dict) else {}
    return {
        "sourceType": "waf",
        "eventType": str(item.get("eventType") or item.get("event_type") or "waf_detect"),
        "severity": str(item.get("severity") or "medium").lower(),
        "assetIp": str(item.get("assetIp") or item.get("asset_ip") or SAFE_ASSET_IP),
        "targetUrl": str(item.get("targetUrl") or request.get("uri") or "http://demo-range.local/"),
        "httpMethod": str(item.get("httpMethod") or request.get("method") or "GET"),
        "httpStatus": int(item.get("httpStatus") or response.get("status") or 200),
        "action": str(item.get("action") or "detect"),
        "ruleId": str(item.get("ruleId") or item.get("rule_id") or f"WAF-DEMO-RANGE-{index:04d}"),
        "ruleName": str(item.get("ruleName") or item.get("rule_name") or message),
        "engine": str(item.get("engine") or "ModSecurity CRS demo gateway"),
        "requestId": str(item.get("requestId") or item.get("unique_id") or f"{batch_id}-waf-{index:04d}"),
        "demoCaseId": str(item.get("demoCaseId") or item.get("demo_case_id") or "waf-gateway"),
        "batchId": batch_id,
        "evidenceSummary": str(item.get("evidenceSummary") or item.get("evidence_summary") or message),
        "timestamp": str(item.get("timestamp") or item.get("time_stamp") or "2026-06-18T10:00:00+08:00"),
        "sourceIp": str(item.get("sourceIp") or item.get("src_ip") or SAFE_SOURCE_IP),
    }


def json_records(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    records: list[dict[str, Any]] = []
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = line.strip()
        if not line or not line.startswith("{"):
            continue
        try:
            value = json.loads(line)
        except json.JSONDecodeError:
            continue
        if isinstance(value, dict):
            records.append(value)
    return records


def read_json(path: Path) -> Any:
    if not path.exists():
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8", errors="replace"))
    except json.JSONDecodeError:
        return None


def stamp_batch(value: Any, batch_id: str) -> Any:
    if isinstance(value, dict):
        stamped = {key: stamp_batch(item, batch_id) for key, item in value.items()}
        stamped.setdefault("batchId", batch_id)
        return stamped
    if isinstance(value, list):
        return [stamp_batch(item, batch_id) for item in value]
    return value


def send_payload(api_base: str, payload: dict[str, Any]) -> None:
    url = api_base.rstrip("/") + "/soc/external-events/cyberfusion/import"
    body = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(url, data=body, method="POST")
    request.add_header("Content-Type", "application/json")
    token = os.getenv("CYBERFUSION_API_TOKEN", "").strip()
    if token:
        request.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            print(response.read().decode("utf-8", errors="replace"))
    except urllib.error.URLError as exc:
        raise SystemExit(f"failed to import payload to {url}: {exc}") from exc


def clean_batch_id(value: str) -> str:
    cleaned = "".join(ch for ch in value if ch.isalnum() or ch in "_.:-")
    return cleaned[:80] or DEFAULT_BATCH_ID


def env_bool(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


if __name__ == "__main__":
    sys.exit(main())

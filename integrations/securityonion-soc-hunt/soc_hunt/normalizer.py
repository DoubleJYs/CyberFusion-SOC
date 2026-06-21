from __future__ import annotations

import json
from hashlib import sha256
from datetime import datetime, timezone
from typing import Any


SEVERITY_ORDER = {"low": 1, "medium": 2, "high": 3, "critical": 4}
VALID_STATUSES = {"new", "triaged", "in_case", "closed"}


def utc_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def normalize_alert(event: dict[str, Any]) -> dict[str, Any]:
    source = str(event.get("source") or event.get("event.module") or "unknown").lower()
    raw = event.get("raw_event", event)
    raw_alert = raw.get("alert") if isinstance(raw, dict) and isinstance(raw.get("alert"), dict) else {}
    severity = str(
        event.get("severity")
        or event.get("alert", {}).get("severity")
        or raw_alert.get("severity")
        or event.get("event", {}).get("severity")
        or "medium"
    ).lower()
    if severity not in SEVERITY_ORDER:
        severity = map_numeric_severity(severity)

    normalized = {
        "id": str(event.get("id") or event.get("event_id") or stable_id(event)),
        "source": source,
        "event_type": str(event.get("event_type") or event.get("event", {}).get("kind") or "alert"),
        "severity": severity,
        "src_ip": pick(event, "src_ip", "source.ip", "source", "ip"),
        "dst_ip": pick(event, "dst_ip", "destination.ip", "destination", "ip"),
        "asset": str(event.get("asset") or event.get("host", {}).get("name") or event.get("observer", {}).get("name") or ""),
        "rule": str(event.get("rule") or event.get("alert", {}).get("signature") or raw_alert.get("signature") or event.get("rule", {}).get("name") or ""),
        "tags": normalize_tags(event.get("tags") or event.get("event", {}).get("category") or event.get("alert", {}).get("category") or raw_alert.get("category")),
        "raw_event": raw if isinstance(raw, str) else json.dumps(raw, ensure_ascii=False, sort_keys=True),
        "status": str(event.get("status") or "new").lower(),
        "created_at": str(event.get("created_at") or event.get("@timestamp") or utc_now()),
    }
    if normalized["status"] not in VALID_STATUSES:
        normalized["status"] = "new"
    return normalized


def pick(event: dict[str, Any], direct: str, dotted: str, parent: str, child: str) -> str:
    if event.get(direct):
        return str(event[direct])
    current: Any = event
    for part in dotted.split("."):
        if not isinstance(current, dict):
            current = None
            break
        current = current.get(part)
    if current:
        return str(current)
    nested = event.get(parent)
    if isinstance(nested, dict) and nested.get(child):
        return str(nested[child])
    return ""


def map_numeric_severity(value: str) -> str:
    try:
        number = int(value)
    except ValueError:
        return "medium"
    if number >= 4:
        return "critical"
    if number == 3:
        return "high"
    if number == 2:
        return "medium"
    return "low"


def normalize_tags(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, list):
        return ",".join(str(item).strip() for item in value if str(item).strip())
    return str(value).strip()


def stable_id(event: dict[str, Any]) -> str:
    payload = json.dumps(event, ensure_ascii=False, sort_keys=True)
    return f"alert-{sha256(payload.encode('utf-8')).hexdigest()[:16]}"

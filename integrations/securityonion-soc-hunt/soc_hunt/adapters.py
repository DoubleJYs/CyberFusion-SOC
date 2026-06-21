from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Iterable


def load_events(path: Path, source: str = "auto") -> list[dict[str, Any]]:
    events: list[dict[str, Any]] = []
    for raw in read_json_lines(path):
        detected = detect_source(raw, source)
        if detected == "suricata":
            events.append(from_suricata(raw))
        elif detected == "zeek":
            events.append(from_zeek(raw))
        else:
            events.append(raw)
    return events


def read_json_lines(path: Path) -> Iterable[dict[str, Any]]:
    with path.open("r", encoding="utf-8") as handle:
        for line_no, line in enumerate(handle, start=1):
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            try:
                payload = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError(f"{path}:{line_no} is not valid JSONL: {exc}") from exc
            if not isinstance(payload, dict):
                raise ValueError(f"{path}:{line_no} must contain a JSON object")
            yield payload


def detect_source(event: dict[str, Any], requested: str) -> str:
    requested = requested.lower()
    if requested in {"zeek", "suricata", "generic"}:
        return requested
    if event.get("event_type") in {"alert", "dns", "http", "tls", "flow"} and (
        "src_ip" in event or "dest_ip" in event or "alert" in event
    ):
        return "suricata"
    if "uid" in event or "id.orig_h" in event or "id.resp_h" in event:
        return "zeek"
    if str(event.get("source", "")).lower() in {"zeek", "suricata"}:
        return str(event["source"]).lower()
    return "generic"


def from_suricata(event: dict[str, Any]) -> dict[str, Any]:
    alert = event.get("alert") if isinstance(event.get("alert"), dict) else {}
    signature = alert.get("signature") or event.get("rule") or event.get("event_type") or "Suricata event"
    return {
        "id": event.get("id") or event.get("flow_id") or event.get("event_id"),
        "source": "suricata",
        "event_type": f"suricata_{event.get('event_type', 'event')}",
        "severity": alert.get("severity") or event.get("severity") or "medium",
        "src_ip": event.get("src_ip") or event.get("source", {}).get("ip", ""),
        "dst_ip": event.get("dest_ip") or event.get("dst_ip") or event.get("destination", {}).get("ip", ""),
        "asset": event.get("host") or event.get("hostname") or event.get("sensor_name") or event.get("in_iface") or "",
        "rule": signature,
        "tags": alert.get("category") or event.get("tags", ""),
        "raw_event": event,
        "status": event.get("status", "new"),
        "created_at": event.get("timestamp") or event.get("@timestamp"),
    }


def from_zeek(event: dict[str, Any]) -> dict[str, Any]:
    zeek_type = infer_zeek_type(event)
    src_ip = event.get("id.orig_h") or event.get("src_ip") or event.get("source", {}).get("ip", "")
    dst_ip = event.get("id.resp_h") or event.get("dst_ip") or event.get("destination", {}).get("ip", "")
    return {
        "id": event.get("id") or event.get("uid") or event.get("event_id"),
        "source": "zeek",
        "event_type": f"zeek_{zeek_type}",
        "severity": event.get("severity") or severity_for_zeek(event, zeek_type),
        "src_ip": src_ip,
        "dst_ip": dst_ip,
        "asset": event.get("asset") or event.get("host_name") or event.get("observer", {}).get("name", "") or "",
        "rule": event.get("rule") or rule_for_zeek(event, zeek_type),
        "tags": event.get("tags") or zeek_type,
        "raw_event": event,
        "status": event.get("status", "new"),
        "created_at": event.get("@timestamp") or event.get("ts") or event.get("timestamp"),
    }


def infer_zeek_type(event: dict[str, Any]) -> str:
    if "query" in event or "qtype_name" in event:
        return "dns"
    if "method" in event or "uri" in event or "host" in event:
        return "http"
    if "server_name" in event or "ja3" in event:
        return "tls"
    if "conn_state" in event:
        return "conn"
    return str(event.get("event_type") or "event")


def severity_for_zeek(event: dict[str, Any], zeek_type: str) -> str:
    if zeek_type == "dns" and (event.get("rcode_name") == "NXDOMAIN" or event.get("rejected")):
        return "medium"
    if zeek_type == "http" and int(event.get("request_body_len") or 0) >= 5_000_000:
        return "high"
    return "medium"


def rule_for_zeek(event: dict[str, Any], zeek_type: str) -> str:
    if zeek_type == "dns":
        return f"Zeek DNS query {event.get('query', '')}".strip()
    if zeek_type == "http":
        return f"Zeek HTTP {event.get('method', '')} {event.get('host', '')}{event.get('uri', '')}".strip()
    if zeek_type == "conn":
        return f"Zeek conn {event.get('proto', '')} {event.get('service', '')}".strip()
    return f"Zeek {zeek_type} event"

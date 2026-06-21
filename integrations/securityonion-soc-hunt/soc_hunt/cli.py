from __future__ import annotations

import argparse
import json
from pathlib import Path

from .constants import DEMO_ALERTS
from .storage import SocHuntStore


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="SOC Hunt overlay CLI")
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("init", help="Create runtime directories and database schema")
    sub.add_parser("import-demo", help="Import bundled Zeek/Suricata demo alerts")
    sub.add_parser("reset-demo", help="Clear demo alerts, cases, hunts, health, audit, and reports")

    import_file = sub.add_parser("import-file", help="Import Zeek, Suricata EVE, or generic JSONL events")
    import_file.add_argument("--path", required=True)
    import_file.add_argument("--source", choices=["auto", "zeek", "suricata", "generic"], default="auto")
    import_file.add_argument("--actor", default="analyst")

    alerts = sub.add_parser("alerts", help="List normalized alerts")
    alerts.add_argument("--query", default="")
    alerts.add_argument("--severity", default="")
    alerts.add_argument("--source", default="")
    alerts.add_argument("--asset", default="")
    alerts.add_argument("--rule", default="")
    alerts.add_argument("--tags", default="")
    alerts.add_argument("--status", default="")
    alerts.add_argument("--start", default="")
    alerts.add_argument("--end", default="")

    case = sub.add_parser("create-case", help="Create case from an alert")
    case.add_argument("--alert-id")
    case.add_argument("--title", required=True)
    case.add_argument("--summary", default="")
    case.add_argument("--assignee", default="")
    case.add_argument("--actor", default="analyst")

    record = sub.add_parser("add-record", help="Add handling record to a case")
    record.add_argument("--case-id", type=int, required=True)
    record.add_argument("--action", required=True)
    record.add_argument("--note", required=True)
    record.add_argument("--analyst", default="analyst")

    status = sub.add_parser("set-status", help="Move case workflow status")
    status.add_argument("--case-id", type=int, required=True)
    status.add_argument("--status", required=True)
    status.add_argument("--actor", default="analyst")

    assign = sub.add_parser("assign-case", help="Assign a case to an analyst")
    assign.add_argument("--case-id", type=int, required=True)
    assign.add_argument("--assignee", required=True)
    assign.add_argument("--actor", default="lead")

    review = sub.add_parser("review-case", help="Record lead review for a case")
    review.add_argument("--case-id", type=int, required=True)
    review.add_argument("--reviewer", required=True)
    review.add_argument("--note", required=True)
    review.add_argument("--actor", default="lead")

    report = sub.add_parser("export-report", help="Export a markdown case report")
    report.add_argument("--case-id", type=int, required=True)
    report.add_argument("--actor", default="analyst")

    hunt = sub.add_parser("create-hunt", help="Record a threat-hunting task")
    hunt.add_argument("--title", required=True)
    hunt.add_argument("--query", required=True)
    hunt.add_argument("--owner", default="analyst")
    hunt.add_argument("--notes", default="")
    hunt.add_argument("--hypothesis", default="")
    hunt.add_argument("--result", default="")
    hunt.add_argument("--conclusion", default="")
    hunt.add_argument("--case-id", type=int)

    hunt_result = sub.add_parser("record-hunt-result", help="Record hunt result and conclusion")
    hunt_result.add_argument("--hunt-id", type=int, required=True)
    hunt_result.add_argument("--result", required=True)
    hunt_result.add_argument("--conclusion", required=True)
    hunt_result.add_argument("--status", default="completed")
    hunt_result.add_argument("--actor", default="analyst")

    hunt_report = sub.add_parser("export-hunt-report", help="Export a markdown hunt report")
    hunt_report.add_argument("--hunt-id", type=int, required=True)
    hunt_report.add_argument("--actor", default="analyst")

    summary_report = sub.add_parser("export-summary-report", help="Export daily or weekly markdown SOC summary")
    summary_report.add_argument("--period", choices=["daily", "weekly"], default="daily")
    summary_report.add_argument("--actor", default="analyst")

    health = sub.add_parser("health", help="List data source health rows")
    health.set_defaults(command="health")

    backup = sub.add_parser("backup", help="Backup runtime database and reports")
    backup.add_argument("--actor", default="admin")

    restore = sub.add_parser("restore", help="Restore runtime database and reports from a backup directory")
    restore.add_argument("--path", required=True)
    restore.add_argument("--actor", default="admin")


    serve = sub.add_parser("serve", help="Run local SOC Hunt API and web UI")
    serve.add_argument("--host", default="127.0.0.1")
    serve.add_argument("--port", type=int, default=8765)
    return parser


def main() -> None:
    args = build_parser().parse_args()
    if args.command == "serve":
        from .app import run_server

        run_server(args.host, args.port)
        return

    store = SocHuntStore()
    try:
        if args.command == "init":
            print(json.dumps({"database": str(store.paths.db_path)}, ensure_ascii=False))
        elif args.command == "import-demo":
            count = store.import_jsonl(DEMO_ALERTS, actor="demo")
            store.seed_demo_health(actor="demo")
            print(json.dumps({"imported": count}, ensure_ascii=False))
        elif args.command == "reset-demo":
            store.reset_demo(actor="demo")
            print(json.dumps({"reset": True}, ensure_ascii=False))
        elif args.command == "import-file":
            count = store.import_event_file(Path(args.path), source=args.source, actor=args.actor)
            print(json.dumps({"imported": count, "source": args.source}, ensure_ascii=False))
        elif args.command == "alerts":
            print(
                json.dumps(
                    store.list_alerts(
                        query=args.query,
                        severity=args.severity,
                        source=args.source,
                        asset=args.asset,
                        rule=args.rule,
                        tags=args.tags,
                        status=args.status,
                        start=args.start,
                        end=args.end,
                    ),
                    ensure_ascii=False,
                    indent=2,
                )
            )
        elif args.command == "create-case":
            case_id = store.create_case(
                args.title,
                args.alert_id,
                args.summary,
                assignee=args.assignee,
                actor=args.actor,
            )
            print(json.dumps({"case_id": case_id}, ensure_ascii=False))
        elif args.command == "add-record":
            record_id = store.add_case_record(args.case_id, args.action, args.note, args.analyst)
            print(json.dumps({"record_id": record_id}, ensure_ascii=False))
        elif args.command == "set-status":
            store.update_case_status(args.case_id, args.status, args.actor)
            print(json.dumps({"case_id": args.case_id, "status": args.status}, ensure_ascii=False))
        elif args.command == "assign-case":
            store.assign_case(args.case_id, args.assignee, args.actor)
            print(json.dumps({"case_id": args.case_id, "assignee": args.assignee}, ensure_ascii=False))
        elif args.command == "review-case":
            store.review_case(args.case_id, args.reviewer, args.note, args.actor)
            print(json.dumps({"case_id": args.case_id, "reviewer": args.reviewer}, ensure_ascii=False))
        elif args.command == "export-report":
            path = store.export_case_report(args.case_id, actor=args.actor)
            print(json.dumps({"report": str(path)}, ensure_ascii=False))
        elif args.command == "create-hunt":
            hunt_id = store.create_hunt_task(
                args.title,
                args.query,
                args.owner,
                notes=args.notes,
                hypothesis=args.hypothesis,
                result=args.result,
                conclusion=args.conclusion,
                case_id=args.case_id,
            )
            print(json.dumps({"hunt_id": hunt_id}, ensure_ascii=False))
        elif args.command == "record-hunt-result":
            store.record_hunt_result(args.hunt_id, args.result, args.conclusion, args.status, args.actor)
            print(json.dumps({"hunt_id": args.hunt_id, "status": args.status}, ensure_ascii=False))
        elif args.command == "export-hunt-report":
            path = store.export_hunt_report(args.hunt_id, actor=args.actor)
            print(json.dumps({"report": str(path)}, ensure_ascii=False))
        elif args.command == "export-summary-report":
            path = store.export_summary_report(args.period, actor=args.actor)
            print(json.dumps({"report": str(path)}, ensure_ascii=False))
        elif args.command == "health":
            print(json.dumps(store.list_data_source_health(), ensure_ascii=False, indent=2))
        elif args.command == "backup":
            path = store.backup_runtime(actor=args.actor)
            print(json.dumps({"backup": str(path)}, ensure_ascii=False))
        elif args.command == "restore":
            store.restore_runtime(Path(args.path), actor=args.actor)
            print(json.dumps({"restored": str(Path(args.path))}, ensure_ascii=False))
    finally:
        store.close()


if __name__ == "__main__":
    main()

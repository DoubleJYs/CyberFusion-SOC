from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from soc_hunt.constants import DEMO_ALERTS
from soc_hunt.paths import RuntimePaths
from soc_hunt.storage import SocHuntStore

SAMPLES = Path(__file__).resolve().parents[1] / "data"


class SocHuntFlowTest(unittest.TestCase):
    def test_demo_alert_to_case_to_report_flow(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            store = SocHuntStore(RuntimePaths(Path(tmp)))
            try:
                imported = store.import_jsonl(DEMO_ALERTS, actor="test")
                store.seed_demo_health(actor="test")
                self.assertEqual(imported, 5)

                alerts = store.list_alerts(query="finance-api-01")
                self.assertGreaterEqual(len(alerts), 2)
                self.assertEqual(len(store.list_alerts(source="suricata")), 3)
                self.assertEqual(len(store.list_alerts(asset="finance-api-01", tags="Trojan")), 1)
                alert = alerts[0]
                self.assertIn(alert["source"], {"suricata", "zeek"})
                self.assertIn(alert["severity"], {"critical", "high", "medium", "low"})

                case_id = store.create_case(
                    title="Finance API beacon investigation",
                    alert_id=alert["id"],
                    summary="Confirm C2 activity and affected host scope.",
                    assignee="tester",
                    actor="tester",
                )
                store.assign_case(case_id, "lead-queue", "tester")
                store.add_case_record(case_id, "analysis", "Correlated DNS and IDS alerts.", "tester")
                store.review_case(case_id, "lead", "Evidence and containment plan reviewed.", "tester")
                store.update_case_status(case_id, "contained", "tester")
                hunt_id = store.create_hunt_task(
                    "Find related DNS beacons",
                    'source:zeek asset:"finance-api-01"',
                    owner="tester",
                    hypothesis="Compromised host may have related DNS beacons.",
                    result="Found one high entropy DNS alert.",
                    conclusion="Associate DNS signal with the case.",
                    case_id=case_id,
                )
                store.record_hunt_result(
                    hunt_id,
                    "No additional affected assets found.",
                    "Scope limited to finance-api-01.",
                    actor="tester",
                )
                hunt_report = store.export_hunt_report(hunt_id, actor="tester")
                summary_report = store.export_summary_report("daily", actor="tester")
                store.update_case_status(case_id, "closed", "tester")
                store.update_case_status(case_id, "archived", "tester")
                report = store.export_case_report(case_id, actor="tester")
                backup = store.backup_runtime(actor="tester")
                store.reset_demo(actor="tester")
                self.assertEqual(store.summary()["alerts_total"], 0)
                store.restore_runtime(backup, actor="tester")

                case = store.get_case(case_id)
                self.assertIsNotNone(case)
                self.assertEqual(case["status"], "archived")
                self.assertEqual(case["assignee"], "lead-queue")
                self.assertEqual(case["reviewer"], "lead")
                self.assertGreaterEqual(len(case["records"]), 7)
                self.assertEqual(hunt_id, 1)
                self.assertTrue(report.exists())
                self.assertTrue(hunt_report.exists())
                self.assertTrue(summary_report.exists())
                self.assertTrue((backup / "soc_hunt.sqlite3").exists())
                self.assertIn("SOC Case Report", report.read_text(encoding="utf-8"))
                self.assertIn("SOC Hunt Report", hunt_report.read_text(encoding="utf-8"))
                self.assertGreaterEqual(len(store.list_data_source_health()), 4)
                self.assertGreaterEqual(len(store.audit_logs()), 6)
            finally:
                store.close()

    def test_zeek_and_suricata_import_adapters(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            store = SocHuntStore(RuntimePaths(Path(tmp)))
            try:
                zeek_count = store.import_event_file(SAMPLES / "zeek-sample.jsonl", source="zeek", actor="test")
                suricata_count = store.import_event_file(
                    SAMPLES / "suricata-eve-sample.jsonl",
                    source="suricata",
                    actor="test",
                )

                self.assertEqual(zeek_count, 2)
                self.assertEqual(suricata_count, 2)
                zeek_alerts = store.list_alerts(query="Zeek")
                suricata_alerts = store.list_alerts(query="ET MALWARE")
                self.assertEqual(len(zeek_alerts), 2)
                self.assertEqual(suricata_alerts[0]["source"], "suricata")
                self.assertEqual(suricata_alerts[0]["dst_ip"], "10.20.8.15")
                self.assertEqual(
                    set(zeek_alerts[0].keys()) & {
                        "source",
                        "event_type",
                        "severity",
                        "src_ip",
                        "dst_ip",
                        "asset",
                        "rule",
                        "raw_event",
                        "status",
                    },
                    {
                        "source",
                        "event_type",
                        "severity",
                        "src_ip",
                        "dst_ip",
                        "asset",
                        "rule",
                        "raw_event",
                        "status",
                    },
                )
            finally:
                store.close()


if __name__ == "__main__":
    unittest.main()

import sqlite3
import tempfile
import unittest
import json
from pathlib import Path

import zeek_traffic_platform as platform


class PlatformTests(unittest.TestCase):
    def test_demo_import_overview_and_report(self):
        with tempfile.TemporaryDirectory() as tmp:
            env_root = Path(tmp)
            db = env_root / "02-databases" / "03-zeek" / "test.sqlite3"
            samples = platform.init_demo_logs(env_root)

            imported = platform.import_paths(db, samples, env_root)
            self.assertEqual(len(imported), 5)

            data = platform.overview(db)
            self.assertEqual(data["counts"]["connections"], 3)
            self.assertEqual(data["counts"]["dns"], 2)
            self.assertEqual(data["counts"]["http"], 2)
            self.assertEqual(data["counts"]["ssl"], 1)
            self.assertGreaterEqual(data["counts"]["anomalies"], 1)
            self.assertEqual(data["top_src_ip"][0]["value"], "10.1.10.5")
            self.assertEqual(data["top_domain"][0]["value"], "example.com")

            second_import = platform.import_paths(db, samples, env_root)
            self.assertEqual(sum(rows for _, rows, _ in second_import), 0)

            rows, total = platform.query_table(db, "conn", {"ip": "10.1.10.8"})
            self.assertEqual(total, 1)
            self.assertEqual(rows[0]["dst_port"], 4444)

            events, event_total = platform.query_events(db, {"domain": "example.com"})
            self.assertGreaterEqual(event_total, 3)
            self.assertTrue({"ts", "uid", "src_ip", "src_port", "dst_ip", "dst_port", "proto", "service", "duration", "bytes", "domain", "uri", "status"}.issubset(events[0].keys()))

            report = platform.export_report(db, env_root / "08-docs" / "03-zeek" / "report.md")
            self.assertTrue(report.exists())
            self.assertIn("Risk Scores", report.read_text(encoding="utf-8"))
            daily = platform.export_report(db, env_root / "08-docs" / "03-zeek" / "daily.md", kind="daily")
            anomaly = platform.export_report(db, env_root / "08-docs" / "03-zeek" / "anomaly.md", kind="anomaly")
            asset = platform.export_report(db, env_root / "08-docs" / "03-zeek" / "asset.md", kind="asset")
            self.assertIn("Traffic Daily Overview", daily.read_text(encoding="utf-8"))
            self.assertIn("Abnormal Connection Report", anomaly.read_text(encoding="utf-8"))
            self.assertIn("Asset Communication Report", asset.read_text(encoding="utf-8"))

            with sqlite3.connect(db) as con:
                normalized_count = con.execute("SELECT COUNT(*) FROM normalized_events").fetchone()[0]
                audit_count = con.execute("SELECT COUNT(*) FROM audit_logs").fetchone()[0]
            self.assertGreaterEqual(normalized_count, 1)
            self.assertGreaterEqual(audit_count, 1)

            soc_path = platform.export_soc(db, env_root / "08-docs" / "03-zeek" / "soc.json")
            soc_payload = json.loads(soc_path.read_text(encoding="utf-8"))
            self.assertIn("events", soc_payload)
            self.assertEqual(soc_payload["events"][0]["source"], "zeek")
            self.assertIn("output_fields", soc_payload["events"][0])

    def test_import_failures_are_traceable_and_retryable(self):
        with tempfile.TemporaryDirectory() as tmp:
            env_root = Path(tmp)
            db = env_root / "02-databases" / "03-zeek" / "test.sqlite3"
            bad_log = env_root / "conn.bad.log"
            bad_log.write_text("this is not a Zeek log\n", encoding="utf-8")

            imported = platform.import_paths(db, [bad_log], env_root)
            self.assertEqual(imported, [])
            failures = platform.import_failures(db)
            self.assertEqual(len(failures), 1)
            self.assertIn("missing #fields", failures[0]["error"])

            bad_log.write_text(
                "\n".join([
                    "#separator \\x09",
                    "#fields\tts\tuid\tid.orig_h\tid.orig_p\tid.resp_h\tid.resp_p\tproto\tservice\tduration\torig_bytes\tresp_bytes\tconn_state",
                    "1772352070.000\tR1\t10.1.10.9\t55111\t198.51.100.5\t443\ttcp\tssl\t1.0\t100\t200\tSF",
                    "",
                ]),
                encoding="utf-8",
            )
            retried = platform.retry_failed_imports(db, env_root)
            self.assertEqual(len(retried), 1)
            self.assertEqual(platform.import_failures(db), [])


if __name__ == "__main__":
    unittest.main()

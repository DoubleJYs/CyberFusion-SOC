import json
import tempfile
import unittest
from pathlib import Path

from ir_platform.__main__ import build_paths, load_state, main, render_case_page, render_dashboard_page, render_task_page


class IRPlatformCLITest(unittest.TestCase):
    def test_production_closed_loop_writes_runtime_data_to_env_root(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            env_root = Path(tmpdir)
            case_id = "CASE-UNIT-001"
            main(["--env-root", str(env_root), "run-demo", "--case-id", case_id])

            state_path = env_root / "02-databases" / "11-velociraptor" / "ir_state.json"
            audit_path = env_root / "11-logs" / "11-velociraptor" / "audit.jsonl"
            evidence_dir = env_root / "13-uploads" / "11-velociraptor" / "evidence" / case_id
            report_dir = env_root / "08-docs" / "11-velociraptor" / "reports" / case_id

            self.assertTrue(state_path.exists())
            self.assertTrue(audit_path.exists())
            self.assertEqual(len(list(evidence_dir.glob("*.zip"))), 1)
            self.assertEqual(len(list(report_dir.glob("*-forensic-report.md"))), 1)

            state = json.loads(state_path.read_text(encoding="utf-8"))
            self.assertEqual(state["schema"], 2)
            self.assertEqual(state["cases"][0]["case_id"], case_id)
            self.assertEqual(state["cases"][0]["status"], "REPORTED")
            self.assertEqual(len(state["tasks"]), 1)
            self.assertEqual(len(state["approvals"]), 1)
            task_id = state["tasks"][0]["id"]
            self.assertIn(task_id, state["results"])
            self.assertIn(task_id, state["timeline"])
            self.assertGreaterEqual(len(state["timeline"][task_id]), 8)
            self.assertEqual(len(state["dispatch_plans"]), 1)
            self.assertTrue(Path(state["dispatch_plans"][0]["path"]).exists())
            self.assertEqual(state["dispatch_records"][0]["status"], "RESULT_IMPORTED")
            self.assertEqual(len(state["evidence_records"]), 1)
            self.assertEqual(len(state["dispositions"]), 1)

            paths = build_paths(env_root)
            dashboard = render_dashboard_page(paths, state)
            case_page = render_case_page(paths, state, case_id)
            task_page = render_task_page(paths, state, task_id)
            self.assertIn("Velociraptor IR 管理平台", dashboard)
            self.assertIn("案件列表", dashboard)
            self.assertIn("证据链", case_page)
            self.assertIn("结果表格", task_page)

            main(["--env-root", str(env_root), "verify-chain", "--case-id", case_id])
            main(
                [
                    "--env-root",
                    str(env_root),
                    "close-case",
                    "--case-id",
                    case_id,
                    "--reviewer",
                    "unit-reviewer",
                    "--reason",
                    "unit test closure after report and chain verification",
                ]
            )
            closed_state = json.loads(state_path.read_text(encoding="utf-8"))
            self.assertEqual(closed_state["cases"][0]["status"], "CLOSED")
            self.assertGreaterEqual(len(closed_state["chain_verifications"]), 2)
            self.assertEqual(closed_state["chain_verifications"][-1]["status"], "PASS")

    def test_sensitive_task_blocks_import_until_approved(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            env_root = Path(tmpdir)
            main(
                [
                    "--env-root",
                    str(env_root),
                    "create-case",
                    "--case-id",
                    "CASE-TEST",
                    "--asset",
                    "endpoint-01",
                    "--incident-type",
                    "malware",
                    "--severity",
                    "high",
                ]
            )
            main(
                [
                    "--env-root",
                    str(env_root),
                    "create-task",
                    "--template",
                    "suspicious_process",
                    "--case-id",
                    "CASE-TEST",
                    "--reason",
                    "unit test",
                ]
            )
            state_path = env_root / "02-databases" / "11-velociraptor" / "ir_state.json"
            pending_state = json.loads(state_path.read_text(encoding="utf-8"))
            task_id = pending_state["tasks"][0]["id"]
            self.assertIn("审批采集任务", render_task_page(build_paths(env_root), pending_state, task_id))
            with self.assertRaises(SystemExit):
                main(["--env-root", str(env_root), "dispatch-plan", "--task-id", task_id])
            with self.assertRaises(SystemExit):
                main(["--env-root", str(env_root), "import-demo", "--task-id", task_id])

            main(
                [
                    "--env-root",
                    str(env_root),
                    "approve-task",
                    "--task-id",
                    task_id,
                    "--approver",
                    "unit-reviewer",
                    "--reason",
                    "authorized unit test approval",
                ]
            )
            main(["--env-root", str(env_root), "dispatch-plan", "--task-id", task_id, "--operator", "unit-dispatcher"])
            main(["--env-root", str(env_root), "import-demo", "--task-id", task_id, "--collector", "unit-collector"])
            state = json.loads(state_path.read_text(encoding="utf-8"))
            self.assertEqual(state["tasks"][0]["approved_by"], "unit-reviewer")
            self.assertEqual(state["evidence_records"][0]["collector"], "unit-collector")
            self.assertEqual(state["dispatch_plans"][0]["operator"], "unit-dispatcher")

    def test_role_isolation_blocks_analyst_approval(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            env_root = Path(tmpdir)
            main(
                [
                    "--env-root",
                    str(env_root),
                    "--role",
                    "analyst",
                    "create-case",
                    "--case-id",
                    "CASE-RBAC",
                    "--asset",
                    "endpoint-02",
                    "--incident-type",
                    "malware",
                    "--severity",
                    "medium",
                ]
            )
            main(
                [
                    "--env-root",
                    str(env_root),
                    "--role",
                    "analyst",
                    "create-task",
                    "--template",
                    "network_connections",
                    "--case-id",
                    "CASE-RBAC",
                    "--reason",
                    "authorized network triage",
                ]
            )
            state_path = env_root / "02-databases" / "11-velociraptor" / "ir_state.json"
            task_id = json.loads(state_path.read_text(encoding="utf-8"))["tasks"][0]["id"]
            with self.assertRaises(SystemExit):
                main(
                    [
                        "--env-root",
                        str(env_root),
                        "--role",
                        "analyst",
                        "approve-task",
                        "--task-id",
                        task_id,
                        "--approver",
                        "unit-analyst",
                        "--reason",
                        "should not approve",
                    ]
                )
            main(
                [
                    "--env-root",
                    str(env_root),
                    "--role",
                    "reviewer",
                    "approve-task",
                    "--task-id",
                    task_id,
                    "--approver",
                    "unit-reviewer",
                    "--reason",
                    "role separated approval",
                ]
            )
            state = json.loads(state_path.read_text(encoding="utf-8"))
            self.assertEqual(state["tasks"][0]["approved_by"], "unit-reviewer")


if __name__ == "__main__":
    unittest.main()

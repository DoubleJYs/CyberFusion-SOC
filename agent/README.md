# CyberFusion Host Agent

This directory contains the self-developed Host Agent. The first implementation is intentionally small: it proves that macOS and Windows use the same protocol, schema, HTTP client, and fixture acceptance path before deep OS collectors are added.

## Current Scope

- Shared Go schema for `Host Agent Ingest v1`.
- Shared upload client using `X-CyberFusion-Agent-Token`.
- Local pending queue, eventUid dedupe state, pending-queue dedupe guard, rate-limited flush, post-flush heartbeat stats, and runtime log files outside the source tree.
- One-shot mode for smoke tests and daemon mode for Windows Service / macOS launchd (`--once=false --interval 60s`).
- macOS and Windows fixture snapshots.
- Current-host asset collection using Go standard library network interfaces.
- macOS listening port, process summary, launchd startup item summary, bounded system log summary, firewall, remote login, and watched-file permission metadata.
- Windows EventLog summaries for Security/System/Application, PowerShell Operational, Defender Operational, optional Sysmon, patch summary, service summary, listening port summary, startup summary, Defender service baseline, and firewall baseline.
- Optional FIM hash metadata for one configured file path.
- No long-running PowerShell collector, no remote command execution, no file body upload.

## Local Dry Run

```bash
cd agent
go test ./...
go run ./cmd/cyberfusion-agent --mode fixture --fixture-os all --dry-run
go run ./cmd/cyberfusion-agent --mode collect --dry-run --fim-path ./README.md
```

## Daemon Mode

Service wrappers should run the same binary in bounded-interval daemon mode. Upload failures do not stop the process; pending JSON operations remain in the local runtime queue and are retried on the next cycle. If all dedupe keys in a new asset/event/FIM/baseline operation already exist in the pending queue, the duplicate operation is skipped locally instead of growing the outage queue. After a successful flush, the agent sends a second heartbeat with the current queue depth and sent/failed counters so the platform can show replay status.

```bash
go run ./cmd/cyberfusion-agent \
  --config-file /path/to/agent.env \
  --mode collect \
  --os-type macos \
  --once=false \
  --interval 60s
```

For automated smoke tests, use `--max-cycles 2 --interval 1s` so the daemon loop exits after a fixed number of cycles.

End-to-end platform smoke from the repository root:

```bash
scripts/smoke/host-agent-go-smoke.sh
scripts/smoke/host-agent-queue-replay-smoke.sh
scripts/smoke/host-agent-fixture-residue-gate.sh
scripts/smoke/host-agent-uninstall-smoke.sh
scripts/smoke/host-agent-mac-collect-smoke.sh
scripts/smoke/host-agent-resource-smoke.sh
scripts/smoke/host-agent-package-smoke.sh
```

Fixture smoke data is temporary validation data. The smoke scripts clean it by default, and the residue gate fails if fixture agents, TEST-NET assets, smoke batches, alerts, events, or incidents are still visible through SOC APIs. Set `CYBERFUSION_KEEP_SMOKE_DATA=1` only when you intentionally need a failed smoke run to leave debugging evidence.

Resource smoke builds the real Agent binary first, verifies fixture dry-run, then checks Go runtime memory stats for a bounded daemon outage loop. It does not replace OS RSS, Windows working-set, or long-idle CPU measurement under launchd or Windows Service.

macOS launchd packaging smoke without installing a real launchd job:

```bash
export CYBERFUSION_ENV_ROOT=/private/tmp/cyberfusion-agent-install-check
export CYBERFUSION_AGENT_ID=macos-install-check
export CYBERFUSION_ADMIN_ACCESS_TOKEN="replace-with-local-admin-access-token"
export CYBERFUSION_SKIP_LAUNCHD_INSTALL=1
scripts/mac/install-agent.sh
CYBERFUSION_AGENT_UPLOAD_ONCE=1 scripts/mac/verify-agent.sh
scripts/mac/uninstall-agent.sh
```

## Build Zip Packages

Package outputs are written outside the source tree under `CYBERFUSION_ENV_ROOT/packages/agent`. The zip packages contain only the Agent binary, install/verify/uninstall scripts, docs, and a manifest. They must not include `agent.env`, tokens, runtime logs, or pending queue data.

```bash
export CYBERFUSION_ENV_ROOT="$HOME/Environment/cyberfusion-platform"
export CYBERFUSION_AGENT_VERSION="0.1.0-dev"
scripts/mac/package-agent.sh
scripts/smoke/host-agent-package-smoke.sh
```

macOS package install from an unpacked zip:

```bash
export CYBERFUSION_AGENT_TOKEN="replace-with-local-token"
export CYBERFUSION_AGENT_BINARY_PATH="$PWD/bin/cyberfusion-agent"
scripts/mac/install-agent.sh
```

Windows package install from an unpacked zip:

```powershell
$env:CYBERFUSION_AGENT_TOKEN = "replace-with-local-token"
.\scripts\win\install-agent.ps1 -AgentId "windows-dev-agent" -BinaryPath ".\bin\cyberfusion-agent.exe"
```

The Windows zip proves cross-build and package layout on macOS. It does not replace real Windows validation of EventLog, Defender, Sysmon, Windows Service startup, reboot recovery, or Docker-backend outage recovery.

## Upload With an Existing Agent Token

Register the agent from the platform first, then keep the token in the local environment or protected config. Do not commit it.

```bash
cd agent
export CYBERFUSION_AGENT_TOKEN="replace-with-local-token"
go run ./cmd/cyberfusion-agent \
  --api-base-url http://127.0.0.1:18080/api \
  --agent-id macos-dev-agent \
  --runtime-dir /private/tmp/cyberfusion-agent-macos-dev-agent \
  --mode collect \
  --fim-path ./README.md
```

Windows PowerShell:

```powershell
$env:CYBERFUSION_AGENT_TOKEN = "replace-with-local-token"
.\scripts\win\install-agent.ps1 -AgentId "windows-dev-agent"
.\scripts\win\start-agent.ps1 -AgentId "windows-dev-agent"
.\scripts\win\verify-agent.ps1 -AgentId "windows-dev-agent" -UploadOnce
.\scripts\win\uninstall-agent.ps1 -AgentId "windows-dev-agent"
```

Uninstall scripts remove the service wrapper, binary, and local token config. They do not call the platform database. Runtime queue data is preserved by default so an interrupted upgrade does not lose pending events; use explicit purge/remove flags only when local evidence and queued uploads are no longer needed.

## Next Collector Work

- macOS: tighten launchd permissions and add targeted login/audit normalization where useful.
- Windows: replace early command-based collectors with Windows API readers where useful, then add MSI or signed installer on top of the current zip package.
- Frontend: keep Host Agent workbench and real-data health cards aligned with the ingest model.
- Installers: harden launchd, Windows Service upgrade flows, and protected credential storage.

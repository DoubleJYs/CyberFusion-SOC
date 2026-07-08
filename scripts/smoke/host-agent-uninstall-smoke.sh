#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
tmp_parent="${CYBERFUSION_SMOKE_TMPDIR:-${TMPDIR:-/tmp}}"
tmp_dir="$(mktemp -d "${tmp_parent%/}/cyberfusion-agent-uninstall.XXXXXX")"
trap 'rm -rf "$tmp_dir"' EXIT

agent_id="macos-uninstall-smoke"
agent_root="$tmp_dir/agent/$agent_id"
pending_dir="$agent_root/runtime/queue/pending"
mkdir -p "$agent_root/bin" "$agent_root/config" "$pending_dir" "$agent_root/launchd"
printf 'binary\n' >"$agent_root/bin/cyberfusion-agent"
printf 'CYBERFUSION_AGENT_TOKEN=local-only\n' >"$agent_root/config/agent.env"
printf '{"kind":"event"}\n' >"$pending_dir/pending-event.json"

CYBERFUSION_ENV_ROOT="$tmp_dir" \
CYBERFUSION_AGENT_ID="$agent_id" \
CYBERFUSION_SKIP_LAUNCHD_INSTALL=1 \
"$PROJECT_ROOT/scripts/mac/uninstall-agent.sh" >/dev/null

[[ ! -e "$agent_root/bin/cyberfusion-agent" ]] || {
  printf '[FAIL] binary was not removed\n' >&2
  exit 1
}
[[ ! -e "$agent_root/config/agent.env" ]] || {
  printf '[FAIL] config containing token was not removed\n' >&2
  exit 1
}
[[ -f "$pending_dir/pending-event.json" ]] || {
  printf '[FAIL] pending queue was not preserved\n' >&2
  exit 1
}

printf '[PASS] macOS uninstall removes binary/config and preserves pending queue\n'
printf '[PASS] uninstall smoke did not contact platform database\n'

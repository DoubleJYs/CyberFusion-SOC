#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
ENV_ROOT="${CYBERFUSION_ENV_ROOT:-${HOME}/Environment/cyberfusion-platform}"
AGENT_ID="${CYBERFUSION_AGENT_ID:-macos-host-agent}"
LAUNCHD_SCOPE="${CYBERFUSION_AGENT_LAUNCHD_SCOPE:-user}"
FOREGROUND="${CYBERFUSION_AGENT_FOREGROUND:-0}"
INTERVAL="${CYBERFUSION_AGENT_INTERVAL:-60s}"

safe_agent_id="$(printf '%s' "$AGENT_ID" | tr '[:upper:]' '[:lower:]' | tr -c 'a-z0-9_-' '-')"
label="com.cyberfusion.host-agent.${safe_agent_id}"
agent_root="${ENV_ROOT}/agent/${AGENT_ID}"
binary_path="${agent_root}/bin/cyberfusion-agent"
config_file="${agent_root}/config/agent.env"

if [[ ! -x "$binary_path" ]]; then
  printf 'ERROR: Agent binary not found: %s. Run scripts/mac/install-agent.sh first.\n' "$binary_path" >&2
  exit 1
fi
if [[ ! -f "$config_file" ]]; then
  printf 'ERROR: Agent config not found: %s. Run scripts/mac/install-agent.sh first.\n' "$config_file" >&2
  exit 1
fi

if [[ "$FOREGROUND" == "1" ]]; then
  exec "$binary_path" --config-file "$config_file" --mode collect --os-type macos --once=false --interval "$INTERVAL"
fi

if [[ "$LAUNCHD_SCOPE" == "system" ]]; then
  sudo launchctl kickstart -k "system/${label}"
else
  launchctl kickstart -k "gui/$(id -u)/${label}"
fi
printf 'Started launchd job: %s\n' "$label"

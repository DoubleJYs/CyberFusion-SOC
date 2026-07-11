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
  target="/Library/LaunchDaemons/${label}.plist"
  if ! sudo launchctl kickstart -k "system/${label}" >/dev/null 2>&1; then
    if [[ ! -f "$target" ]]; then
      printf 'ERROR: Launchd plist not installed: %s. Run scripts/mac/install-agent.sh first.\n' "$target" >&2
      exit 1
    fi
    sudo launchctl bootstrap system "$target" >/dev/null 2>&1 || true
    sudo launchctl kickstart -k "system/${label}"
  fi
else
  target="${HOME}/Library/LaunchAgents/${label}.plist"
  if ! launchctl kickstart -k "gui/$(id -u)/${label}" >/dev/null 2>&1; then
    if [[ ! -f "$target" ]]; then
      printf 'ERROR: Launchd plist not installed: %s. Run scripts/mac/install-agent.sh first.\n' "$target" >&2
      exit 1
    fi
    launchctl bootstrap "gui/$(id -u)" "$target" >/dev/null 2>&1 || true
    launchctl kickstart -k "gui/$(id -u)/${label}"
  fi
fi
printf 'Started launchd job: %s\n' "$label"

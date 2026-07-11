#!/usr/bin/env bash
set -euo pipefail

ENV_ROOT="${CYBERFUSION_ENV_ROOT:-${HOME}/Environment/cyberfusion-platform}"
AGENT_ID="${CYBERFUSION_AGENT_ID:-macos-host-agent}"
LAUNCHD_SCOPE="${CYBERFUSION_AGENT_LAUNCHD_SCOPE:-user}"

safe_agent_id="$(printf '%s' "$AGENT_ID" | tr '[:upper:]' '[:lower:]' | tr -c 'a-z0-9_-' '-')"
label="com.cyberfusion.host-agent.${safe_agent_id}"
agent_root="${ENV_ROOT}/agent/${AGENT_ID}"
launchd_dir="${agent_root}/launchd"
generated_plist="${launchd_dir}/${label}.plist"

if [[ "$LAUNCHD_SCOPE" == "system" ]]; then
  target="/Library/LaunchDaemons/${label}.plist"
  sudo launchctl bootout system "$target" >/dev/null 2>&1 || true
else
  target="${HOME}/Library/LaunchAgents/${label}.plist"
  launchctl bootout "gui/$(id -u)" "$target" >/dev/null 2>&1 || true
fi

if [[ ! -f "$target" && ! -f "$generated_plist" ]]; then
  printf 'Launchd plist not found for Agent: %s\n' "$AGENT_ID"
else
  printf 'Stopped launchd job: %s\n' "$label"
fi
printf 'Agent install files and runtime queue were preserved.\n'

#!/usr/bin/env bash
set -euo pipefail

ENV_ROOT="${CYBERFUSION_ENV_ROOT:-${HOME}/Environment/cyberfusion-platform}"
AGENT_ID="${CYBERFUSION_AGENT_ID:-macos-host-agent}"
LAUNCHD_SCOPE="${CYBERFUSION_AGENT_LAUNCHD_SCOPE:-user}"
KEEP_QUEUE="${CYBERFUSION_AGENT_KEEP_QUEUE:-1}"
PURGE_LOCAL_STATE="${CYBERFUSION_AGENT_PURGE_LOCAL_STATE:-0}"

safe_agent_id="$(printf '%s' "$AGENT_ID" | tr '[:upper:]' '[:lower:]' | tr -c 'a-z0-9_-' '-')"
label="com.cyberfusion.host-agent.${safe_agent_id}"
agent_root="${ENV_ROOT}/agent/${AGENT_ID}"
bin_dir="${agent_root}/bin"
config_dir="${agent_root}/config"
runtime_dir="${agent_root}/runtime"
launchd_dir="${agent_root}/launchd"
generated_plist="${launchd_dir}/${label}.plist"

if [[ "$LAUNCHD_SCOPE" == "system" ]]; then
  target="/Library/LaunchDaemons/${label}.plist"
  sudo launchctl bootout system "$target" >/dev/null 2>&1 || true
  if [[ -f "$target" ]]; then
    sudo rm -f "$target"
  fi
else
  target="${HOME}/Library/LaunchAgents/${label}.plist"
  launchctl bootout "gui/$(id -u)" "$target" >/dev/null 2>&1 || true
  rm -f "$target"
fi

rm -f "$generated_plist"
rm -rf "$bin_dir"

if [[ "$PURGE_LOCAL_STATE" == "1" ]]; then
  rm -rf "$agent_root"
  printf 'Purged local Agent state: %s\n' "$agent_root"
else
  rm -rf "$config_dir"
  if [[ "$KEEP_QUEUE" != "1" ]]; then
    rm -rf "$runtime_dir"
    printf 'Removed Agent runtime directory: %s\n' "$runtime_dir"
  else
    mkdir -p "$runtime_dir"
    printf 'Preserved Agent runtime and pending queue: %s\n' "$runtime_dir"
  fi
fi

printf 'Uninstalled launchd job: %s\n' "$label"
printf 'Platform database was not contacted or modified by this script.\n'

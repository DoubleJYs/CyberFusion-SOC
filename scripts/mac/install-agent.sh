#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
ENV_ROOT="${CYBERFUSION_ENV_ROOT:-${HOME}/Environment/cyberfusion-platform}"
API_BASE_URL="${CYBERFUSION_API_BASE:-http://127.0.0.1:18080/api}"
AGENT_ID="${CYBERFUSION_AGENT_ID:-macos-host-agent}"
AGENT_TOKEN="${CYBERFUSION_AGENT_TOKEN:-}"
ADMIN_ACCESS_TOKEN="${CYBERFUSION_ADMIN_ACCESS_TOKEN:-}"
FIM_PATH="${CYBERFUSION_AGENT_FIM_PATH:-}"
PACKAGED_BINARY_PATH="${CYBERFUSION_AGENT_BINARY_PATH:-}"
LAUNCHD_SCOPE="${CYBERFUSION_AGENT_LAUNCHD_SCOPE:-user}"
SKIP_LAUNCHD_INSTALL="${CYBERFUSION_SKIP_LAUNCHD_INSTALL:-0}"
INTERVAL="${CYBERFUSION_AGENT_INTERVAL:-60s}"

safe_agent_id="$(printf '%s' "$AGENT_ID" | tr '[:upper:]' '[:lower:]' | tr -c 'a-z0-9_-' '-')"
label="com.cyberfusion.host-agent.${safe_agent_id}"
agent_root="${ENV_ROOT}/agent/${AGENT_ID}"
bin_dir="${agent_root}/bin"
config_dir="${agent_root}/config"
runtime_dir="${agent_root}/runtime"
launchd_dir="${agent_root}/launchd"
binary_path="${bin_dir}/cyberfusion-agent"
config_file="${config_dir}/agent.env"
plist_file="${launchd_dir}/${label}.plist"

mkdir -p "$bin_dir" "$config_dir" "$runtime_dir" "${runtime_dir}/logs" "$launchd_dir" "${ENV_ROOT}/caches/go-build"

if [[ -z "$AGENT_TOKEN" && -n "$ADMIN_ACCESS_TOKEN" ]]; then
  hostname_value="$(hostname 2>/dev/null || printf 'macos-host')"
  register_payload="${runtime_dir}/register-payload.json"
  printf '{"agentId":"%s","agentName":"%s","hostname":"%s","osType":"macos","osVersion":"runtime","architecture":"auto","agentVersion":"0.1.0-dev","labels":{"install":"macos-launchd","agent":"go"}}' \
    "$AGENT_ID" "$hostname_value" "$hostname_value" >"$register_payload"
  response="$(
    curl -sS -X POST "${API_BASE_URL%/}/soc/agents/register" \
      -H "Authorization: Bearer ${ADMIN_ACCESS_TOKEN}" \
      -H "Content-Type: application/json" \
      --data @"$register_payload"
  )"
  AGENT_TOKEN="$(printf '%s' "$response" | python3 -c 'import json,sys; d=json.load(sys.stdin); print((d.get("data") or {}).get("agentToken") or "")')"
fi

if [[ -z "$AGENT_TOKEN" ]]; then
  printf 'ERROR: Agent token is required. Set CYBERFUSION_AGENT_TOKEN or CYBERFUSION_ADMIN_ACCESS_TOKEN.\n' >&2
  exit 1
fi

if [[ -n "$PACKAGED_BINARY_PATH" ]]; then
  if [[ ! -f "$PACKAGED_BINARY_PATH" ]]; then
    printf 'ERROR: Packaged Agent binary not found: %s\n' "$PACKAGED_BINARY_PATH" >&2
    exit 1
  fi
  if [[ "$(cd "$(dirname "$PACKAGED_BINARY_PATH")" && pwd -P)/$(basename "$PACKAGED_BINARY_PATH")" != "$(cd "$bin_dir" && pwd -P)/$(basename "$binary_path")" ]]; then
    cp "$PACKAGED_BINARY_PATH" "$binary_path"
  fi
else
  (
    cd "${PROJECT_ROOT}/agent"
    GOCACHE="${GOCACHE:-${ENV_ROOT}/caches/go-build}" go build -o "$binary_path" ./cmd/cyberfusion-agent
  )
fi
chmod 700 "$binary_path"

cat >"$config_file" <<EOF
CYBERFUSION_API_BASE=${API_BASE_URL}
CYBERFUSION_AGENT_ID=${AGENT_ID}
CYBERFUSION_AGENT_TOKEN=${AGENT_TOKEN}
CYBERFUSION_AGENT_RUNTIME_DIR=${runtime_dir}
CYBERFUSION_AGENT_FIM_PATH=${FIM_PATH}
CYBERFUSION_AGENT_INTERVAL=${INTERVAL}
EOF
chmod 600 "$config_file"

cat >"$plist_file" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>${label}</string>
  <key>ProgramArguments</key>
  <array>
    <string>${binary_path}</string>
    <string>--config-file</string>
    <string>${config_file}</string>
    <string>--mode</string>
    <string>collect</string>
    <string>--os-type</string>
    <string>macos</string>
    <string>--once=false</string>
    <string>--interval</string>
    <string>${INTERVAL}</string>
  </array>
  <key>WorkingDirectory</key>
  <string>${agent_root}</string>
  <key>RunAtLoad</key>
  <true/>
  <key>KeepAlive</key>
  <true/>
  <key>StandardOutPath</key>
  <string>${runtime_dir}/logs/stdout.log</string>
  <key>StandardErrorPath</key>
  <string>${runtime_dir}/logs/stderr.log</string>
</dict>
</plist>
EOF
chmod 600 "$plist_file"

if [[ "$SKIP_LAUNCHD_INSTALL" != "1" ]]; then
  if [[ "$LAUNCHD_SCOPE" == "system" ]]; then
    target="/Library/LaunchDaemons/${label}.plist"
    sudo cp "$plist_file" "$target"
    sudo chown root:wheel "$target"
    sudo chmod 644 "$target"
    sudo launchctl bootout system "$target" >/dev/null 2>&1 || true
    sudo launchctl bootstrap system "$target"
  else
    target="${HOME}/Library/LaunchAgents/${label}.plist"
    mkdir -p "${HOME}/Library/LaunchAgents"
    cp "$plist_file" "$target"
    chmod 600 "$target"
    launchctl bootout "gui/$(id -u)" "$target" >/dev/null 2>&1 || true
    launchctl bootstrap "gui/$(id -u)" "$target"
  fi
  printf 'Installed launchd job: %s\n' "$label"
else
  printf 'Launchd install skipped. Plist generated at: %s\n' "$plist_file"
fi

printf 'Agent binary: %s\n' "$binary_path"
printf 'Agent config: %s\n' "$config_file"
printf 'Agent runtime: %s\n' "$runtime_dir"
printf 'Token stored only in local runtime config, not in source.\n'

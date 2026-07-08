#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
ENV_ROOT="${CYBERFUSION_ENV_ROOT:-${HOME}/Environment/cyberfusion-platform}"
API_BASE_URL="${CYBERFUSION_API_BASE:-http://127.0.0.1:18080/api}"
AGENT_ID="${CYBERFUSION_AGENT_ID:-macos-host-agent}"
UPLOAD_ONCE="${CYBERFUSION_AGENT_UPLOAD_ONCE:-0}"

agent_root="${ENV_ROOT}/agent/${AGENT_ID}"
binary_path="${agent_root}/bin/cyberfusion-agent"
config_file="${agent_root}/config/agent.env"
runtime_dir="${agent_root}/runtime"

if [[ ! -x "$binary_path" ]]; then
  printf 'ERROR: Missing agent binary: %s\n' "$binary_path" >&2
  exit 1
fi
if [[ ! -f "$config_file" ]]; then
  printf 'ERROR: Missing agent config: %s\n' "$config_file" >&2
  exit 1
fi
if ! grep -q '^CYBERFUSION_AGENT_TOKEN=' "$config_file"; then
  printf 'ERROR: Agent config does not contain CYBERFUSION_AGENT_TOKEN.\n' >&2
  exit 1
fi

health="$(curl -sS "${API_BASE_URL%/}/health")"
code="$(printf '%s' "$health" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("code") or "")')"
if [[ "$code" != "SUCCESS" ]]; then
  printf 'ERROR: Platform API health is not SUCCESS at %s/health\n' "${API_BASE_URL%/}" >&2
  exit 1
fi
printf '[PASS] Platform API health is SUCCESS\n'

if [[ "$UPLOAD_ONCE" == "1" ]]; then
  "$binary_path" --config-file "$config_file" --mode collect --os-type macos --once=true
  printf '[PASS] Agent one-shot upload completed\n'
fi

printf '[PASS] Agent binary: %s\n' "$binary_path"
printf '[PASS] Agent config exists and token was not printed\n'
printf '[PASS] Agent runtime: %s\n' "$runtime_dir"

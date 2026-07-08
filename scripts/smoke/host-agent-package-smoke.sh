#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
tmp_parent="${CYBERFUSION_SMOKE_TMPDIR:-${TMPDIR:-/tmp}}"
tmp_dir="$(mktemp -d "${tmp_parent%/}/cyberfusion-agent-package.XXXXXX")"
trap 'rm -rf "$tmp_dir"' EXIT

command -v unzip >/dev/null 2>&1 || {
  printf '[FAIL] unzip is required for package smoke\n' >&2
  exit 1
}

version="package-smoke"
CYBERFUSION_ENV_ROOT="$tmp_dir/env" \
CYBERFUSION_AGENT_VERSION="$version" \
CYBERFUSION_AGENT_TARGETS="darwin/arm64 windows/amd64" \
"$PROJECT_ROOT/scripts/mac/package-agent.sh" >/dev/null

package_root="$tmp_dir/env/packages/agent"
mac_zip="$package_root/cyberfusion-agent-darwin-arm64-${version}.zip"
win_zip="$package_root/cyberfusion-agent-windows-amd64-${version}.zip"

[[ -s "$mac_zip" ]] || {
  printf '[FAIL] macOS Agent package missing: %s\n' "$mac_zip" >&2
  exit 1
}
[[ -s "$win_zip" ]] || {
  printf '[FAIL] Windows Agent package missing: %s\n' "$win_zip" >&2
  exit 1
}
[[ -s "${mac_zip}.sha256" && -s "${win_zip}.sha256" ]] || {
  printf '[FAIL] package checksum file missing\n' >&2
  exit 1
}

unzip -l "$mac_zip" >"$tmp_dir/mac-list.txt"
unzip -l "$win_zip" >"$tmp_dir/win-list.txt"

require_entry() {
  local list_file="$1"
  local pattern="$2"
  if ! grep -F "$pattern" "$list_file" >/dev/null; then
    printf '[FAIL] package missing entry: %s\n' "$pattern" >&2
    exit 1
  fi
}

reject_entry() {
  local list_file="$1"
  local pattern="$2"
  if grep -F "$pattern" "$list_file" >/dev/null; then
    printf '[FAIL] package should not contain entry: %s\n' "$pattern" >&2
    exit 1
  fi
}

require_entry "$tmp_dir/mac-list.txt" "bin/cyberfusion-agent"
require_entry "$tmp_dir/mac-list.txt" "scripts/mac/install-agent.sh"
require_entry "$tmp_dir/mac-list.txt" "scripts/mac/uninstall-agent.sh"
require_entry "$tmp_dir/mac-list.txt" "README-INSTALL.md"
require_entry "$tmp_dir/mac-list.txt" "MANIFEST.txt"

require_entry "$tmp_dir/win-list.txt" "bin/cyberfusion-agent.exe"
require_entry "$tmp_dir/win-list.txt" "scripts/win/install-agent.ps1"
require_entry "$tmp_dir/win-list.txt" "scripts/win/runtime-paths.ps1"
require_entry "$tmp_dir/win-list.txt" "scripts/win/uninstall-agent.ps1"
require_entry "$tmp_dir/win-list.txt" "README-INSTALL.md"
require_entry "$tmp_dir/win-list.txt" "MANIFEST.txt"

reject_entry "$tmp_dir/mac-list.txt" "agent.env"
reject_entry "$tmp_dir/mac-list.txt" "/runtime/"
reject_entry "$tmp_dir/mac-list.txt" "/queue/"
reject_entry "$tmp_dir/win-list.txt" "agent.env"
reject_entry "$tmp_dir/win-list.txt" "/runtime/"
reject_entry "$tmp_dir/win-list.txt" "/queue/"

unzip -p "$mac_zip" "cyberfusion-agent-darwin-arm64-${version}/MANIFEST.txt" >"$tmp_dir/mac-manifest.txt"
unzip -p "$win_zip" "cyberfusion-agent-windows-amd64-${version}/MANIFEST.txt" >"$tmp_dir/win-manifest.txt"
grep -F "containsToken=false" "$tmp_dir/mac-manifest.txt" >/dev/null
grep -F "containsRuntimeQueue=false" "$tmp_dir/mac-manifest.txt" >/dev/null
grep -F "containsToken=false" "$tmp_dir/win-manifest.txt" >/dev/null
grep -F "containsRuntimeQueue=false" "$tmp_dir/win-manifest.txt" >/dev/null

printf '[PASS] Agent package smoke created macOS and Windows zip packages without token config or runtime queue\n'

#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
ENV_ROOT="${CYBERFUSION_ENV_ROOT:-${HOME}/Environment/cyberfusion-platform}"
VERSION="${CYBERFUSION_AGENT_VERSION:-0.1.0-dev}"
TARGETS="${CYBERFUSION_AGENT_TARGETS:-darwin/arm64 windows/amd64}"
PACKAGE_ROOT="${CYBERFUSION_AGENT_PACKAGE_ROOT:-${ENV_ROOT}/packages/agent}"
STAGING_ROOT="${CYBERFUSION_AGENT_PACKAGE_STAGING:-${ENV_ROOT}/package-staging/agent}"
GOCACHE="${GOCACHE:-${ENV_ROOT}/caches/go-build}"

abs_existing_dir() {
  mkdir -p "$1"
  (cd "$1" && pwd -P)
}

project_abs="$(cd "$PROJECT_ROOT" && pwd -P)"
package_abs="$(abs_existing_dir "$PACKAGE_ROOT")"
staging_abs="$(abs_existing_dir "$STAGING_ROOT")"

case "$package_abs" in
  "$project_abs"|"$project_abs"/*)
    printf 'ERROR: Package root must stay outside source project: %s\n' "$package_abs" >&2
    exit 1
    ;;
esac
case "$staging_abs" in
  "$project_abs"|"$project_abs"/*)
    printf 'ERROR: Package staging root must stay outside source project: %s\n' "$staging_abs" >&2
    exit 1
    ;;
esac

command -v zip >/dev/null 2>&1 || {
  printf 'ERROR: zip is required to create Agent packages.\n' >&2
  exit 1
}
command -v shasum >/dev/null 2>&1 || {
  printf 'ERROR: shasum is required to create package checksums.\n' >&2
  exit 1
}

mkdir -p "$GOCACHE"
created_at="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

write_install_readme() {
  local target_os="$1"
  local target_arch="$2"
  local out_file="$3"
  if [[ "$target_os" == "windows" ]]; then
    cat >"$out_file" <<EOF
# CyberFusion Host Agent ${VERSION} (${target_os}/${target_arch})

This zip contains the CyberFusion self-developed Host Agent binary and Windows service helper scripts.

Install from an elevated PowerShell session after CyberFusion backend is reachable:

\`\`\`powershell
\$env:CYBERFUSION_AGENT_TOKEN = "replace-with-local-agent-token"
.\scripts\win\install-agent.ps1 -AgentId "windows-host-agent" -BinaryPath ".\bin\cyberfusion-agent.exe"
.\scripts\win\start-agent.ps1 -AgentId "windows-host-agent"
.\scripts\win\verify-agent.ps1 -AgentId "windows-host-agent" -UploadOnce
\`\`\`

Use \`-AdminAccessToken\` instead of \`CYBERFUSION_AGENT_TOKEN\` only when registering the Agent during installation. Do not commit generated config files or tokens. Runtime data and pending queue are created under \`CYBERFUSION_ENV_ROOT\agent\<AgentId>\`.
EOF
  else
    cat >"$out_file" <<EOF
# CyberFusion Host Agent ${VERSION} (${target_os}/${target_arch})

This zip contains the CyberFusion self-developed Host Agent binary and macOS launchd helper scripts.

Install after CyberFusion backend is reachable:

\`\`\`bash
export CYBERFUSION_AGENT_TOKEN="replace-with-local-agent-token"
export CYBERFUSION_AGENT_BINARY_PATH="\$PWD/bin/cyberfusion-agent"
scripts/mac/install-agent.sh
scripts/mac/start-agent.sh
CYBERFUSION_AGENT_UPLOAD_ONCE=1 scripts/mac/verify-agent.sh
\`\`\`

Use \`CYBERFUSION_ADMIN_ACCESS_TOKEN\` instead of \`CYBERFUSION_AGENT_TOKEN\` only when registering the Agent during installation. Do not commit generated config files or tokens. Runtime data and pending queue are created under \`CYBERFUSION_ENV_ROOT/agent/<AgentId>\`.
EOF
  fi
}

copy_common_docs() {
  local package_dir="$1"
  mkdir -p "$package_dir/docs"
  cp "$PROJECT_ROOT/agent/README.md" "$package_dir/docs/agent-readme.md"
  cp "$PROJECT_ROOT/docs/host-agent-mac-windows-plan.md" "$package_dir/docs/host-agent-mac-windows-plan.md"
}

create_package() {
  local target="$1"
  local target_os="${target%%/*}"
  local target_arch="${target##*/}"
  local binary_name="cyberfusion-agent"
  if [[ "$target_os" == "windows" ]]; then
    binary_name="cyberfusion-agent.exe"
  fi

  local package_name="cyberfusion-agent-${target_os}-${target_arch}-${VERSION}"
  local stage_dir="${staging_abs}/${package_name}"
  local zip_path="${package_abs}/${package_name}.zip"
  rm -rf "$stage_dir" "$zip_path" "${zip_path}.sha256"
  mkdir -p "$stage_dir/bin"

  (
    cd "$PROJECT_ROOT/agent"
    GOOS="$target_os" GOARCH="$target_arch" GOCACHE="$GOCACHE" go build -o "$stage_dir/bin/$binary_name" ./cmd/cyberfusion-agent
  )

  if [[ "$target_os" == "windows" ]]; then
    mkdir -p "$stage_dir/scripts/win"
    cp "$PROJECT_ROOT/scripts/win/install-agent.ps1" "$stage_dir/scripts/win/"
    cp "$PROJECT_ROOT/scripts/win/start-agent.ps1" "$stage_dir/scripts/win/"
    cp "$PROJECT_ROOT/scripts/win/verify-agent.ps1" "$stage_dir/scripts/win/"
    cp "$PROJECT_ROOT/scripts/win/uninstall-agent.ps1" "$stage_dir/scripts/win/"
    cp "$PROJECT_ROOT/scripts/win/runtime-paths.ps1" "$stage_dir/scripts/win/"
  else
    mkdir -p "$stage_dir/scripts/mac"
    cp "$PROJECT_ROOT/scripts/mac/install-agent.sh" "$stage_dir/scripts/mac/"
    cp "$PROJECT_ROOT/scripts/mac/start-agent.sh" "$stage_dir/scripts/mac/"
    cp "$PROJECT_ROOT/scripts/mac/verify-agent.sh" "$stage_dir/scripts/mac/"
    cp "$PROJECT_ROOT/scripts/mac/uninstall-agent.sh" "$stage_dir/scripts/mac/"
    chmod +x "$stage_dir/scripts/mac/"*.sh
  fi

  copy_common_docs "$stage_dir"
  write_install_readme "$target_os" "$target_arch" "$stage_dir/README-INSTALL.md"

  local binary_sha
  binary_sha="$(shasum -a 256 "$stage_dir/bin/$binary_name" | awk '{print $1}')"
  cat >"$stage_dir/MANIFEST.txt" <<EOF
name=${package_name}
version=${VERSION}
target=${target_os}/${target_arch}
createdAt=${created_at}
binary=bin/${binary_name}
binarySha256=${binary_sha}
containsToken=false
containsRuntimeQueue=false
EOF

  (
    cd "$staging_abs"
    zip -qr "$zip_path" "$package_name"
  )
  shasum -a 256 "$zip_path" >"${zip_path}.sha256"
  printf '[PASS] Created Agent package: %s\n' "$zip_path"
}

for target in $TARGETS; do
  case "$target" in
    */*) create_package "$target" ;;
    *)
      printf 'ERROR: Invalid target %s. Use GOOS/GOARCH format.\n' "$target" >&2
      exit 1
      ;;
  esac
done

printf '[PASS] Agent packages written under: %s\n' "$package_abs"

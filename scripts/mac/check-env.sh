#!/usr/bin/env bash
set -u

status=0

check_command() {
  local label="$1"
  shift
  local executable="$1"
  shift

  printf '\n[%s]\n' "$label"
  if ! command -v "$executable" >/dev/null 2>&1; then
    printf 'MISSING: %s\n' "$executable"
    status=1
    return
  fi

  if ! "$@"; then
    status=1
  fi
}

check_command "Java" java java -version
check_command "Maven" mvn mvn -v
check_command "Node.js" node node -v
check_command "pnpm" pnpm pnpm -v
check_command "Docker" docker docker --version
check_command "Docker Compose" docker docker compose version

exit "$status"

#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

if [ "$(basename "${PROJECT_ROOT}")" != "00-cyberfusion-platform" ]; then
  echo "Refusing to clean: unexpected project root ${PROJECT_ROOT}" >&2
  exit 1
fi

for required in "backend/pom.xml" "frontend/package.json" "frontend/pnpm-lock.yaml" ".env.example" "deploy/docker-compose.yml"; do
  if [ ! -e "${PROJECT_ROOT}/${required}" ]; then
    echo "Refusing to clean: missing ${required}" >&2
    exit 1
  fi
done

remove_path() {
  local relative_path="$1"
  local target="${PROJECT_ROOT}/${relative_path}"

  if [ -e "${target}" ]; then
    echo "Removing ${relative_path}"
    rm -rf "${target}"
  fi
}

remove_path "backend/target"
remove_path "frontend/dist"
remove_path "frontend/test-results"
remove_path "logs"
remove_path "tmp"

while IFS= read -r ds_store_file; do
  echo "Removing ${ds_store_file#${PROJECT_ROOT}/}"
  rm -f "${ds_store_file}"
done < <(find "${PROJECT_ROOT}" -name ".DS_Store" -type f)

if [ "${1:-}" = "--include-node-modules" ]; then
  echo "frontend/node_modules is the frontend dependency directory and can be restored with pnpm install."
  printf "Delete frontend/node_modules? Type yes to continue: "
  read -r answer
  if [ "${answer}" = "yes" ]; then
    remove_path "frontend/node_modules"
  else
    echo "Skipped frontend/node_modules"
  fi
else
  echo "Skipped frontend/node_modules. Pass --include-node-modules to remove it."
fi

echo "Generated cleanup completed."

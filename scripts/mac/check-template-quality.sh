#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
status=0

if [ "$(basename "${PROJECT_ROOT}")" != "00-cyberfusion-platform" ]; then
  echo "FAIL unexpected project root: ${PROJECT_ROOT}" >&2
  exit 1
fi

check_exists() {
  local relative_path="$1"
  if [ -e "${PROJECT_ROOT}/${relative_path}" ]; then
    echo "OK exists: ${relative_path}"
  else
    echo "FAIL missing: ${relative_path}"
    status=1
  fi
}

check_absent() {
  local relative_path="$1"
  if [ -e "${PROJECT_ROOT}/${relative_path}" ]; then
    echo "FAIL generated/local artifact exists: ${relative_path}"
    status=1
  else
    echo "OK absent: ${relative_path}"
  fi
}

check_exists "backend/pom.xml"
check_exists "frontend/package.json"
check_exists "frontend/pnpm-lock.yaml"
check_exists "sql/schema.sql"
check_exists "sql/data.sql"
check_exists "deploy/docker-compose.yml"
check_exists "README.md"
check_exists ".env.example"
check_exists ".gitignore"
check_exists ".gitattributes"

check_absent "backend/target"
check_absent "frontend/node_modules"
check_absent "frontend/dist"
check_absent "frontend/test-results"
check_absent "coverage"
check_absent "frontend/coverage"
check_absent "outputs"
check_absent ".env"
check_absent ".DS_Store"
check_absent "frontend/.DS_Store"

if find "${PROJECT_ROOT}" -name ".DS_Store" -print -quit | grep -q .; then
  echo "FAIL .DS_Store found under project"
  status=1
else
  echo "OK no .DS_Store found under project"
fi

secret_pattern="BEGIN .*PRIVATE ""KEY|ghp_""[A-Za-z0-9_]+|sk-""[A-Za-z0-9]+|AKIA""[0-9A-Z]{16}|xoxb-""[A-Za-z0-9-]+"
secret_hits="$(grep -RInE \
  --exclude-dir=node_modules \
  --exclude-dir=target \
  --exclude-dir=dist \
  --exclude-dir=.git \
  --exclude-dir=test-results \
  --exclude-dir=outputs \
  "${secret_pattern}" \
  "${PROJECT_ROOT}" 2>/dev/null || true)"
if [ -n "${secret_hits}" ]; then
  echo "FAIL high-risk secret pattern found"
  printf '%s\n' "${secret_hits}"
  status=1
else
  echo "OK no high-risk secret pattern found"
fi

bad_terms_pattern="代""做|代""写|包""过|替""交|保证""通过|不用学也能""交|论文""代""写"
compliance_hits="$(grep -RInE \
  --exclude-dir=node_modules \
  --exclude-dir=target \
  --exclude-dir=dist \
  --exclude-dir=.git \
  --exclude-dir=test-results \
  --exclude-dir=outputs \
  "${bad_terms_pattern}" \
  "${PROJECT_ROOT}" 2>/dev/null || true)"
if [ -n "${compliance_hits}" ]; then
  echo "FAIL prohibited compliance text found"
  printf '%s\n' "${compliance_hits}"
  status=1
else
  echo "OK no prohibited compliance text found"
fi

exit "${status}"

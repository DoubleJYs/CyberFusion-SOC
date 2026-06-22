#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
DEPLOY_DIR="${PROJECT_ROOT}/deploy"
BACKEND_DIR="${PROJECT_ROOT}/backend"
FRONTEND_DIR="${PROJECT_ROOT}/frontend"
ENV_ROOT="${CYBERFUSION_ENV_ROOT:-${HOME}/Environment/cyberfusion-platform}"
SERVER_PORT="${SERVER_PORT:-18080}"
FRONTEND_PORT="${FRONTEND_PORT:-5174}"
DB_PORT="${DB_PORT:-3306}"
REDIS_PORT="${REDIS_PORT:-6379}"
ADMINER_PORT="${ADMINER_PORT:-8081}"

mkdir -p \
  "${ENV_ROOT}/uploads" \
  "${ENV_ROOT}/logs/backend" \
  "${ENV_ROOT}/backups" \
  "${ENV_ROOT}/local-vm"

export CYBERFUSION_ENV_ROOT="${ENV_ROOT}"
export APP_UPLOAD_BASE_DIR="${APP_UPLOAD_BASE_DIR:-${ENV_ROOT}/uploads}"
export LOGGING_FILE_PATH="${LOGGING_FILE_PATH:-${ENV_ROOT}/logs/backend}"
export SERVER_PORT DB_PORT REDIS_PORT ADMINER_PORT

if [ "${CYBERFUSION_SKIP_COMPAT_CHECK:-0}" != "1" ]; then
  "${SCRIPT_DIR}/check-env.sh"
  "${SCRIPT_DIR}/local-vm-compat-check.sh"
fi

if [ "${CYBERFUSION_SKIP_DOCKER:-0}" != "1" ]; then
  if [ -z "${DB_PASSWORD:-}" ]; then
    printf 'Set DB_PASSWORD before starting Docker services. Do not store real passwords in source files.\n' >&2
    exit 1
  fi
  (
    cd "${DEPLOY_DIR}"
    docker compose config >/dev/null
    docker compose up -d
  )
fi

cleanup() {
  if [ -n "${BACKEND_PID:-}" ]; then kill "${BACKEND_PID}" 2>/dev/null || true; fi
  if [ -n "${FRONTEND_PID:-}" ]; then kill "${FRONTEND_PID}" 2>/dev/null || true; fi
}
trap cleanup INT TERM EXIT

(
  cd "${BACKEND_DIR}"
  mvn spring-boot:run
) &
BACKEND_PID="$!"

(
  cd "${FRONTEND_DIR}"
  pnpm install --frozen-lockfile
  VITE_API_PROXY_TARGET="http://127.0.0.1:${SERVER_PORT}" pnpm dev --port "${FRONTEND_PORT}"
) &
FRONTEND_PID="$!"

printf 'CyberFusion backend:  http://127.0.0.1:%s/api\n' "${SERVER_PORT}"
printf 'CyberFusion frontend: http://127.0.0.1:%s/\n' "${FRONTEND_PORT}"
printf 'Runtime root:         %s\n' "${ENV_ROOT}"

while kill -0 "${BACKEND_PID}" 2>/dev/null && kill -0 "${FRONTEND_PID}" 2>/dev/null; do
  sleep 2
done

printf 'One CyberFusion dev process exited. Stopping the remaining process.\n' >&2
exit 1

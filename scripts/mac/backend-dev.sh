#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
ENV_ROOT="${CYBERFUSION_ENV_ROOT:-${HOME}/Environment/cyberfusion-platform}"

mkdir -p "${ENV_ROOT}/uploads" "${ENV_ROOT}/logs/backend"
export CYBERFUSION_ENV_ROOT="${ENV_ROOT}"
export APP_UPLOAD_BASE_DIR="${APP_UPLOAD_BASE_DIR:-${ENV_ROOT}/uploads}"
export LOGGING_FILE_PATH="${LOGGING_FILE_PATH:-${ENV_ROOT}/logs/backend}"
export SERVER_PORT="${SERVER_PORT:-18080}"

cd "${PROJECT_ROOT}/backend"
mvn spring-boot:run

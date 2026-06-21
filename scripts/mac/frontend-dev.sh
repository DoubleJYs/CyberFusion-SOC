#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
SERVER_PORT="${SERVER_PORT:-18080}"
FRONTEND_PORT="${FRONTEND_PORT:-5174}"

cd "${PROJECT_ROOT}/frontend"
pnpm install --frozen-lockfile
VITE_API_PROXY_TARGET="${VITE_API_PROXY_TARGET:-http://127.0.0.1:${SERVER_PORT}}" pnpm dev -- --host 127.0.0.1 --port "${FRONTEND_PORT}"

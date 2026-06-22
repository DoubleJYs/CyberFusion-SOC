#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
COMPOSE_FILE="${PROJECT_ROOT}/deploy/docker-compose.yml"
ENV_ROOT="${CYBERFUSION_ENV_ROOT:-${HOME}/Environment/cyberfusion-platform}"
BACKUP_ROOT="${CYBERFUSION_BACKUP_ROOT:-${ENV_ROOT}/backups/runtime}"
DB_NAME="${DB_NAME:-cyberfusion_soc}"

if [[ -z "${DB_PASSWORD:-}" ]]; then
  printf 'ERROR: DB_PASSWORD is required and must come from your local environment.\n' >&2
  exit 1
fi

case "${BACKUP_ROOT}" in
  "${PROJECT_ROOT}"/*)
    printf 'ERROR: backup root must not be under the source project: %s\n' "${BACKUP_ROOT}" >&2
    exit 1
    ;;
esac

timestamp="$(date +%Y%m%d-%H%M%S)"
backup_dir="${BACKUP_ROOT}/${timestamp}"
mkdir -p "${backup_dir}"

printf 'Creating MySQL backup in %s\n' "${backup_dir}"
MYSQL_PWD="${DB_PASSWORD}" DB_PASSWORD="${DB_PASSWORD}" DB_NAME="${DB_NAME}" docker compose -f "${COMPOSE_FILE}" exec -T -e MYSQL_PWD mysql \
  sh -c 'mysqldump --single-transaction --routines --events --default-character-set=utf8mb4 -uroot "$MYSQL_DATABASE"' \
  > "${backup_dir}/mysql-${DB_NAME}.sql"

if command -v gzip >/dev/null 2>&1; then
  gzip -f "${backup_dir}/mysql-${DB_NAME}.sql"
fi

printf 'Creating Redis snapshot in %s\n' "${backup_dir}"
DB_PASSWORD="${DB_PASSWORD}" DB_NAME="${DB_NAME}" docker compose -f "${COMPOSE_FILE}" exec -T redis redis-cli SAVE >/dev/null
DB_PASSWORD="${DB_PASSWORD}" DB_NAME="${DB_NAME}" docker compose -f "${COMPOSE_FILE}" cp redis:/data/dump.rdb "${backup_dir}/redis-dump.rdb" >/dev/null

cat > "${backup_dir}/manifest.txt" <<EOF
project=cyberfusion-platform
created_at=${timestamp}
compose_file=${COMPOSE_FILE}
database=${DB_NAME}
contains=mysql,redis
notes=No source code, passwords, tokens, certificates, or private keys are stored in this manifest.
EOF

printf 'Backup completed: %s\n' "${backup_dir}"

#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
COMPOSE_FILE="${PROJECT_ROOT}/deploy/docker-compose.yml"
DB_NAME="${DB_NAME:-cyberfusion_soc}"
BACKUP_DIR="${1:-}"

if [[ -z "${BACKUP_DIR}" ]]; then
  printf 'Usage: DB_PASSWORD=... RESTORE_CONFIRM=YES %s /path/to/backup-dir\n' "$0" >&2
  exit 1
fi

if [[ -z "${DB_PASSWORD:-}" ]]; then
  printf 'ERROR: DB_PASSWORD is required and must come from your local environment.\n' >&2
  exit 1
fi

if [[ "${RESTORE_CONFIRM:-}" != "YES" ]]; then
  printf 'ERROR: set RESTORE_CONFIRM=YES to acknowledge that restore can overwrite runtime data.\n' >&2
  exit 1
fi

if [[ ! -d "${BACKUP_DIR}" ]]; then
  printf 'ERROR: backup directory does not exist: %s\n' "${BACKUP_DIR}" >&2
  exit 1
fi

mysql_dump="${BACKUP_DIR}/mysql-${DB_NAME}.sql"
mysql_dump_gz="${mysql_dump}.gz"

if [[ -f "${mysql_dump_gz}" ]]; then
  printf 'Restoring MySQL from %s\n' "${mysql_dump_gz}"
  gzip -dc "${mysql_dump_gz}" | DB_PASSWORD="${DB_PASSWORD}" DB_NAME="${DB_NAME}" docker compose -f "${COMPOSE_FILE}" exec -T mysql \
    sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --default-character-set=utf8mb4 -uroot "$MYSQL_DATABASE"'
elif [[ -f "${mysql_dump}" ]]; then
  printf 'Restoring MySQL from %s\n' "${mysql_dump}"
  DB_PASSWORD="${DB_PASSWORD}" DB_NAME="${DB_NAME}" docker compose -f "${COMPOSE_FILE}" exec -T mysql \
    sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --default-character-set=utf8mb4 -uroot "$MYSQL_DATABASE"' < "${mysql_dump}"
else
  printf 'ERROR: MySQL dump not found for database %s in %s\n' "${DB_NAME}" "${BACKUP_DIR}" >&2
  exit 1
fi

redis_dump="${BACKUP_DIR}/redis-dump.rdb"
if [[ "${RESTORE_REDIS:-false}" == "true" && -f "${redis_dump}" ]]; then
  printf 'Restoring Redis from %s\n' "${redis_dump}"
  DB_PASSWORD="${DB_PASSWORD}" DB_NAME="${DB_NAME}" docker compose -f "${COMPOSE_FILE}" stop redis >/dev/null
  DB_PASSWORD="${DB_PASSWORD}" DB_NAME="${DB_NAME}" docker compose -f "${COMPOSE_FILE}" cp "${redis_dump}" redis:/data/dump.rdb >/dev/null
  DB_PASSWORD="${DB_PASSWORD}" DB_NAME="${DB_NAME}" docker compose -f "${COMPOSE_FILE}" start redis >/dev/null
else
  printf 'Redis restore skipped. Set RESTORE_REDIS=true to restore redis-dump.rdb.\n'
fi

printf 'Restore completed from: %s\n' "${BACKUP_DIR}"

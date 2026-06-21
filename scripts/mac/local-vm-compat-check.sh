#!/usr/bin/env bash
set -u

status=0
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

check_command() {
  local label="$1"
  shift
  local required="$1"
  shift
  local command_name="$1"

  printf '\n[%s]\n' "$label"
  if ! command -v "$command_name" >/dev/null 2>&1; then
    if [ "$required" = "required" ]; then
      printf 'MISSING: %s\n' "$command_name"
      status=1
    else
      printf 'WARN: %s is unavailable; local VM telemetry will use fallback output.\n' "$command_name"
    fi
    return
  fi
  if ! "$@"; then
    if [ "$required" = "required" ]; then
      status=1
    else
      printf 'WARN: %s could not run in this environment; local VM telemetry will use fallback output.\n' "$command_name"
    fi
  fi
}

check_file() {
  local label="$1"
  local path="$2"

  printf '\n[%s]\n' "$label"
  if [ -f "$path" ]; then
    printf 'OK: %s\n' "$path"
  else
    printf 'MISSING: %s\n' "$path"
    status=1
  fi
}

check_command "Java runtime" required java -version
check_command "Node.js" required node -v
check_command "pnpm" required pnpm -v
check_command "Hostname observation" optional hostname
check_command "Identity observation" optional id
check_command "Process observation" optional ps -axo pid,comm

if command -v lsof >/dev/null 2>&1; then
  check_command "Network observation" optional lsof -i -n -P
elif command -v netstat >/dev/null 2>&1; then
  check_command "Network observation" optional netstat -anv
else
  printf '\n[Network observation]\nWARN: lsof or netstat is unavailable; local VM telemetry will use fallback output.\n'
fi

if command -v launchctl >/dev/null 2>&1; then
  check_command "Startup observation" optional launchctl list
else
  printf '\n[Startup observation]\nWARN: launchctl is unavailable; local VM telemetry will use fallback output.\n'
fi

check_file "Frontend local VM page" "$project_root/frontend/src/views/client/ClientLocalRangeView.vue"
check_file "Frontend device context" "$project_root/frontend/src/composables/useClientDeviceContext.ts"
check_file "Frontend data report page" "$project_root/frontend/src/views/client/ClientDataReportView.vue"
check_file "Frontend operations page" "$project_root/frontend/src/views/client/ClientOperationsView.vue"
check_file "Frontend client router" "$project_root/frontend/src/router/index.ts"
check_file "Frontend client layout" "$project_root/frontend/src/layouts/ClientLayout.vue"
check_file "Frontend runtime compatibility" "$project_root/frontend/src/composables/useClientRuntimeCompatibility.ts"
check_file "Backend runtime compatibility API" "$project_root/backend/src/main/java/com/zhangjiyan/template/soc/client/ClientRuntimeController.java"
check_file "Backend runtime compatibility test" "$project_root/backend/src/test/java/com/zhangjiyan/template/soc/client/ClientRuntimeControllerTest.java"
check_file "Backend SOC service" "$project_root/backend/src/main/java/com/zhangjiyan/template/soc/SocOperationService.java"
check_file "Environment template" "$project_root/.env.example"
check_file "macOS one-click dev startup" "$project_root/scripts/mac/run-dev.sh"
check_file "Windows one-click dev startup" "$project_root/scripts/win/run-dev.ps1"

printf '\n[Runtime boundary]\n'
printf 'Use Environment for runtime data, not source: %s\n' "${CYBERFUSION_ENV_ROOT:-${HOME}/Environment/cyberfusion-platform}"

exit "$status"

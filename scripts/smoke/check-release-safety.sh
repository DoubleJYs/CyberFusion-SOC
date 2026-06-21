#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

pass_count=0

pass() {
  pass_count=$((pass_count + 1))
  printf '[PASS] %s\n' "$1"
}

fail() {
  printf '[FAIL] %s\n' "$1" >&2
  exit 1
}

require_tool() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing required tool: $1"
}

require_tool rg
require_tool find

printf '== CyberFusion release safety checks ==\n'

if find . -name '.env' \
  -not -path './frontend/node_modules/*' \
  -not -path './backend/target/*' \
  -not -path './frontend/dist/*' \
  -print | grep -q .; then
  find . -name '.env' -print
  fail ".env file found in source tree"
fi
pass "no .env file in source tree"

if rg -n "BEGIN (RSA |EC |OPENSSH |DSA )?PRIVATE KEY|AKIA[0-9A-Z]{16}|ghp_[A-Za-z0-9_]{20,}|sk-(proj|live|test)-[A-Za-z0-9_-]{20,}|xox[baprs]-[A-Za-z0-9-]{20,}" \
  --glob '!frontend/node_modules/**' \
  --glob '!frontend/dist/**' \
  --glob '!backend/target/**' \
  --glob '!docs/test-report.md' \
  --glob '!docs/final-report.md' \
  --glob '!scripts/smoke/check-release-safety.sh' \
  .; then
  fail "high-confidence secret pattern found"
fi
pass "no high-confidence token/private-key patterns"

if find . \( -name '*.sqlite' -o -name '*.sqlite3' -o -name '*.db' -o -name '*.mv.db' -o -name '*.ibd' -o -name '*.log' \) \
  -not -path './frontend/node_modules/*' \
  -not -path './frontend/dist/*' \
  -not -path './backend/target/*' \
  -not -path './docs/screenshots/*' \
  -print | grep -q .; then
  find . \( -name '*.sqlite' -o -name '*.sqlite3' -o -name '*.db' -o -name '*.mv.db' -o -name '*.ibd' -o -name '*.log' \) -print
  fail "runtime database or log file found in source tree"
fi
pass "no runtime database or log files in source tree"

if find . -type f -size +25M \
  -not -path './frontend/node_modules/*' \
  -not -path './frontend/dist/*' \
  -not -path './backend/target/*' \
  -not -path './docs/screenshots/*' \
  -print | grep -q .; then
  find . -type f -size +25M -print
  fail "large source artifact found"
fi
pass "no unexpected large source artifact over 25MB"

if rg -n "shell -c|bash -c|sh -c|cmd /c|powershell -Command|ScriptEngine|GroovyShell|SpelExpressionParser" \
  backend/src/main/java/com/zhangjiyan/template/soc/SocOperationService.java \
  backend/src/main/java/com/zhangjiyan/template/soc/policy/LocalCheckPolicyService.java \
  backend/src/main/java/com/zhangjiyan/template/soc/policy/adapter/EventAdapterPolicyService.java \
  backend/src/main/java/com/zhangjiyan/template/soc/playbook/ResponsePlaybookService.java; then
  fail "unsafe shell/script execution pattern found in local check or adapter policy code"
fi
pass "local check, adapter policy, and playbook code do not use shell/script execution"

rg -n "new ProcessBuilder\\(argv\\)" backend/src/main/java/com/zhangjiyan/template/soc/SocOperationService.java >/dev/null \
  || fail "local terminal execution is not using argv ProcessBuilder"
pass "local terminal execution uses argv ProcessBuilder"

rg -n "FORBIDDEN_EXECUTABLES|SHELL_METACHARS|commandArgvJson 必须是 JSON 字符串数组" backend/src/main/java/com/zhangjiyan/template/soc/policy/LocalCheckPolicyService.java >/dev/null \
  || fail "local check policy safety validation was not found"
pass "local check policy safety validation is present"

rg -n "TRANSFORMS|FIELD_PATH|alert_name_template|dedup_key_fields_json" backend/src/main/java/com/zhangjiyan/template/soc/policy/adapter/EventAdapterPolicyService.java >/dev/null \
  || fail "adapter mapping validation was not found"
pass "adapter mapping validation is present"

rg -n "FORBIDDEN_TEXT|FORBIDDEN_METACHARS|只生成处置建议和任务清单" backend/src/main/java/com/zhangjiyan/template/soc/playbook/ResponsePlaybookService.java >/dev/null \
  || fail "response playbook safety validation was not found"
pass "response playbook safety validation is present"

rg -n "send_mode VARCHAR\\(32\\) NOT NULL DEFAULT 'dry_run'" sql/schema.sql >/dev/null \
  || fail "notification schema does not default to dry_run"
rg -n "'dry_run'" sql/data.sql backend/src/main/java/com/zhangjiyan/template/soc/notification/SocNotificationService.java >/dev/null \
  || fail "dry-run notification seed/service behavior not found"
pass "notification remains dry-run by default"

printf 'Safety checks passed: %s\n' "$pass_count"

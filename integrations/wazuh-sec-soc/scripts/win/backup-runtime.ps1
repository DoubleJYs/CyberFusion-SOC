$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path (Join-Path $ScriptDir "..\..")
$ComposeFile = Join-Path $ProjectRoot "deploy\docker-compose.yml"
$EnvRoot = if ($env:SEC_WAZUH_SOC_ENV_ROOT) { $env:SEC_WAZUH_SOC_ENV_ROOT } else { "/Users/zhangjiyan/Environment/sec-wazuh-soc" }
$BackupRoot = if ($env:SEC_WAZUH_SOC_BACKUP_ROOT) { $env:SEC_WAZUH_SOC_BACKUP_ROOT } else { Join-Path $EnvRoot "backups/runtime" }
$DbName = if ($env:DB_NAME) { $env:DB_NAME } else { "sec_wazuh_soc" }

if (-not $env:DB_PASSWORD) {
    throw "DB_PASSWORD is required and must come from your local environment."
}

if ($BackupRoot -like "/Users/zhangjiyan/Programs/*") {
    throw "Backup root must not be under /Users/zhangjiyan/Programs: $BackupRoot"
}

$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$BackupDir = Join-Path $BackupRoot $Timestamp
New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null

Write-Host "Creating MySQL backup in $BackupDir"
$DumpArgs = @("compose", "-f", $ComposeFile, "exec", "-T", "mysql", "sh", "-c", 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump --single-transaction --routines --events --default-character-set=utf8mb4 -uroot "$MYSQL_DATABASE"')
$DumpContent = & docker @DumpArgs
if ($LASTEXITCODE -ne 0) {
    throw "mysqldump failed with exit code $LASTEXITCODE"
}
$DumpPath = Join-Path $BackupDir "mysql-$DbName.sql"
$DumpContent | Set-Content -Path $DumpPath -Encoding UTF8

Write-Host "Creating Redis snapshot in $BackupDir"
& docker compose -f $ComposeFile exec -T redis redis-cli SAVE | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "redis SAVE failed with exit code $LASTEXITCODE"
}
& docker compose -f $ComposeFile cp redis:/data/dump.rdb (Join-Path $BackupDir "redis-dump.rdb") | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "redis dump copy failed with exit code $LASTEXITCODE"
}

$Manifest = @(
    "project=sec-wazuh-soc",
    "created_at=$Timestamp",
    "compose_file=$ComposeFile",
    "database=$DbName",
    "contains=mysql,redis",
    "notes=No source code, passwords, tokens, certificates, or private keys are stored in this manifest."
)
$Manifest | Set-Content -Path (Join-Path $BackupDir "manifest.txt") -Encoding UTF8

Write-Host "Backup completed: $BackupDir"

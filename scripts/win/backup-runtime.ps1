$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = (Resolve-Path (Join-Path $ScriptDir "..\..")).Path
$ComposeFile = Join-Path $ProjectRoot "deploy\docker-compose.yml"
$DefaultEnvRoot = "D:\CyberFusion\Environment\cyberfusion-platform"
$EnvRoot = if ($env:CYBERFUSION_ENV_ROOT) { $env:CYBERFUSION_ENV_ROOT } else { $DefaultEnvRoot }
$BackupRoot = if ($env:CYBERFUSION_BACKUP_ROOT) { $env:CYBERFUSION_BACKUP_ROOT } else { Join-Path $EnvRoot "backups/runtime" }
$DbName = if ($env:DB_NAME) { $env:DB_NAME } else { "cyberfusion_soc" }

if (-not $env:DB_PASSWORD) {
    throw "DB_PASSWORD is required and must come from your local environment."
}

$ProjectRootPattern = "$ProjectRoot*"
if ($BackupRoot -like $ProjectRootPattern) {
    throw "Backup root must not be under the source project: $BackupRoot"
}

$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$BackupDir = Join-Path $BackupRoot $Timestamp
New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null

Write-Host "Creating MySQL backup in $BackupDir"
$previousMysqlPwd = $env:MYSQL_PWD
try {
    $env:MYSQL_PWD = $env:DB_PASSWORD
    $DumpArgs = @("compose", "-f", $ComposeFile, "exec", "-T", "-e", "MYSQL_PWD", "mysql", "sh", "-c", 'mysqldump --single-transaction --routines --events --default-character-set=utf8mb4 -uroot "$MYSQL_DATABASE"')
    $DumpContent = & docker @DumpArgs
    if ($LASTEXITCODE -ne 0) {
        throw "mysqldump failed with exit code $LASTEXITCODE"
    }
} finally {
    if ($null -eq $previousMysqlPwd) {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    } else {
        $env:MYSQL_PWD = $previousMysqlPwd
    }
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
    "project=cyberfusion-platform",
    "created_at=$Timestamp",
    "compose_file=$ComposeFile",
    "database=$DbName",
    "contains=mysql,redis",
    "notes=No source code, passwords, tokens, certificates, or private keys are stored in this manifest."
)
$Manifest | Set-Content -Path (Join-Path $BackupDir "manifest.txt") -Encoding UTF8

Write-Host "Backup completed: $BackupDir"

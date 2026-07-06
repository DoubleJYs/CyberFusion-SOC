param(
    [string]$DbHost = $(if ($env:DB_HOST) { $env:DB_HOST } else { "127.0.0.1" }),
    [int]$DbPort = $(if ($env:DB_PORT) { [int]$env:DB_PORT } else { 3306 }),
    [string]$DbName = $(if ($env:DB_NAME) { $env:DB_NAME } else { "cyberfusion_soc" }),
    [string]$DbUsername = $(if ($env:DB_USERNAME) { $env:DB_USERNAME } else { "root" }),
    [string]$DbPassword = $env:DB_PASSWORD,
    [string]$EnvRoot = $(if ($env:CYBERFUSION_ENV_ROOT) { $env:CYBERFUSION_ENV_ROOT } else { "D:\CyberFusion\Environment\cyberfusion-platform" }),
    [string]$BackupRoot = $(if ($env:CYBERFUSION_BACKUP_ROOT) { $env:CYBERFUSION_BACKUP_ROOT } else { "" }),
    [string]$RedisDumpPath = $env:REDIS_DUMP_PATH
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = (Resolve-Path (Join-Path $ScriptDir "..\..")).Path
if ([string]::IsNullOrWhiteSpace($BackupRoot)) {
    $BackupRoot = Join-Path $EnvRoot "backups\runtime"
}

function Assert-Command {
    param([string]$Command)
    if (-not (Get-Command $Command -ErrorAction SilentlyContinue)) {
        throw "Missing command: $Command. Install MySQL 8 client tools and add the bin directory to PATH."
    }
}

if ([string]::IsNullOrWhiteSpace($DbPassword)) {
    throw "DB_PASSWORD is required and must come from your local environment."
}

if ($BackupRoot -like "$ProjectRoot*") {
    throw "Backup root must not be under the source project: $BackupRoot"
}

Assert-Command "mysqldump"

$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$BackupDir = Join-Path $BackupRoot $Timestamp
New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null

Write-Host "Creating MySQL backup in $BackupDir"
$previousMysqlPwd = $env:MYSQL_PWD
try {
    $env:MYSQL_PWD = $DbPassword
    $DumpPath = Join-Path $BackupDir "mysql-$DbName.sql"
    & mysqldump --single-transaction --routines --events --default-character-set=utf8mb4 "-h$DbHost" "-P$DbPort" "-u$DbUsername" $DbName | Set-Content -Path $DumpPath -Encoding UTF8
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

if (-not [string]::IsNullOrWhiteSpace($RedisDumpPath)) {
    if (-not (Test-Path $RedisDumpPath -PathType Leaf)) {
        throw "REDIS_DUMP_PATH was set but does not point to a file: $RedisDumpPath"
    }
    Copy-Item -Path $RedisDumpPath -Destination (Join-Path $BackupDir "redis-dump.rdb") -Force
    Write-Host "Copied Redis dump from $RedisDumpPath"
} else {
    Write-Host "Redis dump skipped. Set REDIS_DUMP_PATH to the local Redis dump.rdb path if Redis persistence must be backed up."
}

$Contains = "mysql"
if (-not [string]::IsNullOrWhiteSpace($RedisDumpPath)) {
    $Contains = "mysql,redis"
}

$Manifest = @(
    "project=cyberfusion-platform",
    "created_at=$Timestamp",
    "mode=windows-no-docker",
    "database=$DbName",
    "db_host=$DbHost",
    "db_port=$DbPort",
    "contains=$Contains",
    "notes=No source code, passwords, tokens, certificates, or private keys are stored in this manifest."
)
$Manifest | Set-Content -Path (Join-Path $BackupDir "manifest.txt") -Encoding UTF8

Write-Host "Backup completed: $BackupDir"

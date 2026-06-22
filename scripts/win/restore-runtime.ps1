param(
    [Parameter(Mandatory = $true)]
    [string]$BackupDir,
    [switch]$ConfirmRestore,
    [switch]$RestoreRedis
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path (Join-Path $ScriptDir "..\..")
$ComposeFile = Join-Path $ProjectRoot "deploy\docker-compose.yml"
$DbName = if ($env:DB_NAME) { $env:DB_NAME } else { "cyberfusion_soc" }

if (-not $env:DB_PASSWORD) {
    throw "DB_PASSWORD is required and must come from your local environment."
}

if (-not $ConfirmRestore) {
    throw "Pass -ConfirmRestore to acknowledge that restore can overwrite runtime data."
}

if (-not (Test-Path $BackupDir -PathType Container)) {
    throw "Backup directory does not exist: $BackupDir"
}

$DumpPath = Join-Path $BackupDir "mysql-$DbName.sql"
$DumpGzPath = "$DumpPath.gz"

if (Test-Path $DumpGzPath -PathType Leaf) {
    throw "Compressed dumps are not restored by the PowerShell script. Decompress $DumpGzPath first or use scripts/mac/restore-runtime.sh on macOS/Linux."
}

if (-not (Test-Path $DumpPath -PathType Leaf)) {
    throw "MySQL dump not found for database $DbName in $BackupDir"
}

Write-Host "Restoring MySQL from $DumpPath"
$previousMysqlPwd = $env:MYSQL_PWD
try {
    $env:MYSQL_PWD = $env:DB_PASSWORD
    Get-Content -Path $DumpPath -Raw | docker compose -f $ComposeFile exec -T -e MYSQL_PWD mysql sh -c 'mysql --default-character-set=utf8mb4 -uroot "$MYSQL_DATABASE"'
    if ($LASTEXITCODE -ne 0) {
        throw "mysql restore failed with exit code $LASTEXITCODE"
    }
} finally {
    if ($null -eq $previousMysqlPwd) {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    } else {
        $env:MYSQL_PWD = $previousMysqlPwd
    }
}

$RedisDump = Join-Path $BackupDir "redis-dump.rdb"
if ($RestoreRedis -and (Test-Path $RedisDump -PathType Leaf)) {
    Write-Host "Restoring Redis from $RedisDump"
    & docker compose -f $ComposeFile stop redis | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "redis stop failed with exit code $LASTEXITCODE"
    }
    & docker compose -f $ComposeFile cp $RedisDump redis:/data/dump.rdb | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "redis dump copy failed with exit code $LASTEXITCODE"
    }
    & docker compose -f $ComposeFile start redis | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "redis start failed with exit code $LASTEXITCODE"
    }
} else {
    Write-Host "Redis restore skipped. Pass -RestoreRedis to restore redis-dump.rdb."
}

Write-Host "Restore completed from: $BackupDir"

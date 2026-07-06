param(
    [Parameter(Mandatory = $true)]
    [string]$BackupDir,
    [switch]$ConfirmRestore,
    [switch]$RestoreRedis,
    [string]$RedisTargetDumpPath = $env:REDIS_DUMP_PATH,
    [string]$DbHost = $(if ($env:DB_HOST) { $env:DB_HOST } else { "127.0.0.1" }),
    [int]$DbPort = $(if ($env:DB_PORT) { [int]$env:DB_PORT } else { 3306 }),
    [string]$DbName = $(if ($env:DB_NAME) { $env:DB_NAME } else { "cyberfusion_soc" }),
    [string]$DbUsername = $(if ($env:DB_USERNAME) { $env:DB_USERNAME } else { "root" }),
    [string]$DbPassword = $env:DB_PASSWORD
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = (Resolve-Path (Join-Path $ScriptDir "..\..")).Path
if ($ProjectRoot -match "^[A-Za-z]:") {
    $ProjectDrive = $ProjectRoot.Substring(0, 1).ToUpperInvariant()
    if ($ProjectDrive -ne "D") {
        throw "Windows no-Docker mode requires the project under D:\CyberFusion, not $ProjectRoot. Move 00-cyberfusion-platform to D: before restoring runtime data."
    }
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

if (-not $ConfirmRestore) {
    throw "Pass -ConfirmRestore to acknowledge that restore can overwrite runtime data."
}

if (-not (Test-Path $BackupDir -PathType Container)) {
    throw "Backup directory does not exist: $BackupDir"
}

Assert-Command "mysql"

$DumpPath = Join-Path $BackupDir "mysql-$DbName.sql"
$DumpGzPath = "$DumpPath.gz"

if (Test-Path $DumpGzPath -PathType Leaf) {
    throw "Compressed dumps are not restored by this script. Decompress $DumpGzPath first."
}

if (-not (Test-Path $DumpPath -PathType Leaf)) {
    throw "MySQL dump not found for database $DbName in $BackupDir"
}

Write-Host "Restoring MySQL from $DumpPath"
$previousMysqlPwd = $env:MYSQL_PWD
try {
    $env:MYSQL_PWD = $DbPassword
    Get-Content -Path $DumpPath -Raw | & mysql --default-character-set=utf8mb4 "-h$DbHost" "-P$DbPort" "-u$DbUsername" $DbName
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
if ($RestoreRedis) {
    if (-not (Test-Path $RedisDump -PathType Leaf)) {
        throw "Redis restore requested, but redis-dump.rdb was not found in $BackupDir"
    }
    if ([string]::IsNullOrWhiteSpace($RedisTargetDumpPath)) {
        throw "Redis restore requested. Set REDIS_DUMP_PATH or pass -RedisTargetDumpPath to the local Redis dump.rdb location, then stop Redis before copying."
    }
    Copy-Item -Path $RedisDump -Destination $RedisTargetDumpPath -Force
    Write-Host "Copied Redis dump to $RedisTargetDumpPath. Restart the Redis service manually so it loads the file."
} else {
    Write-Host "Redis restore skipped. Pass -RestoreRedis and provide REDIS_DUMP_PATH only after stopping Redis."
}

Write-Host "Restore completed from: $BackupDir"

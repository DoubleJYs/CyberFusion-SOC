param(
    [string]$DbHost = $(if ([string]::IsNullOrWhiteSpace($env:DB_HOST)) { "127.0.0.1" } else { $env:DB_HOST }),
    [int]$DbPort = $(if ([string]::IsNullOrWhiteSpace($env:DB_PORT)) { 3306 } else { [int]$env:DB_PORT }),
    [string]$DbName = $(if ([string]::IsNullOrWhiteSpace($env:DB_NAME)) { "cyberfusion_soc" } else { $env:DB_NAME }),
    [string]$DbUsername = $(if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME)) { "root" } else { $env:DB_USERNAME }),
    [string]$DbPassword = $env:DB_PASSWORD,
    [switch]$SkipSeedData,
    [switch]$SkipLatestPatch
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path (Join-Path $ScriptDir "..\..")
$SchemaSql = Join-Path $ProjectRoot "sql\schema.sql"
$DataSql = Join-Path $ProjectRoot "sql\data.sql"
$LatestPatchSql = Join-Path $ProjectRoot "scripts\sql\apply-latest-menu-and-policy-seed.sql"

function Assert-Command {
    param([string]$Command)
    if (-not (Get-Command $Command -ErrorAction SilentlyContinue)) {
        throw "Missing command: $Command. Install MySQL 8 client and make sure mysql.exe is in PATH."
    }
}

function Invoke-MySqlFile {
    param(
        [string]$SqlPath,
        [string]$Database = ""
    )

    if (-not (Test-Path $SqlPath -PathType Leaf)) {
        throw "SQL file not found: $SqlPath"
    }

    Write-Host "Applying SQL: $SqlPath"
    $previousMysqlPwd = $env:MYSQL_PWD
    try {
        $env:MYSQL_PWD = $DbPassword
        $mysqlArgs = @("--default-character-set=utf8mb4", "-h", $DbHost, "-P", "$DbPort", "-u", $DbUsername)
        if (-not [string]::IsNullOrWhiteSpace($Database)) {
            $mysqlArgs += $Database
        }

        Get-Content -Path $SqlPath -Raw | & mysql @mysqlArgs
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to apply SQL file: $SqlPath"
        }
    } finally {
        if ($null -eq $previousMysqlPwd) {
            Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
        } else {
            $env:MYSQL_PWD = $previousMysqlPwd
        }
    }
}

if ([string]::IsNullOrWhiteSpace($DbPassword)) {
    throw "Set DB_PASSWORD in the local environment or pass -DbPassword. Do not store real passwords in source files."
}

if ($DbName -ne "cyberfusion_soc") {
    throw "The bundled SQL files currently create and seed the fixed database name cyberfusion_soc. Use -DbName cyberfusion_soc for initialization, or pass -SkipDbInit to run against a database you prepared yourself."
}

Assert-Command "mysql"

Invoke-MySqlFile -SqlPath $SchemaSql

if (-not $SkipSeedData) {
    Invoke-MySqlFile -SqlPath $DataSql -Database $DbName
}

if (-not $SkipLatestPatch) {
    Invoke-MySqlFile -SqlPath $LatestPatchSql -Database $DbName
}

Write-Host "Local database is ready: $DbUsername@$DbHost`:$DbPort/$DbName"

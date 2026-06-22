param(
    [string]$DbPort = "3306",
    [string]$RedisPort = "6379",
    [string]$AdminerPort = "8081",
    [string]$ServerPort = "18080",
    [string]$FrontendPort = "5174",
    [string]$DbPassword = $env:DB_PASSWORD,
    [string]$EnvRoot = "",
    [switch]$SkipCompatCheck,
    [switch]$SkipDbInit
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path (Join-Path $ScriptDir "..\..")
$DeployDir = Join-Path $ProjectRoot "deploy"
$BackendDir = Join-Path $ProjectRoot "backend"
$FrontendDir = Join-Path $ProjectRoot "frontend"
$SchemaSql = Join-Path $ProjectRoot "sql\schema.sql"
$DataSql = Join-Path $ProjectRoot "sql\data.sql"

if ([string]::IsNullOrWhiteSpace($EnvRoot)) {
    $EnvRoot = if ([string]::IsNullOrWhiteSpace($env:CYBERFUSION_ENV_ROOT)) { Join-Path $env:USERPROFILE "Environment\cyberfusion-platform" } else { $env:CYBERFUSION_ENV_ROOT }
}
$env:CYBERFUSION_ENV_ROOT = $EnvRoot
$env:APP_UPLOAD_BASE_DIR = if ([string]::IsNullOrWhiteSpace($env:APP_UPLOAD_BASE_DIR)) { Join-Path $EnvRoot "uploads" } else { $env:APP_UPLOAD_BASE_DIR }
$env:LOGGING_FILE_PATH = if ([string]::IsNullOrWhiteSpace($env:LOGGING_FILE_PATH)) { Join-Path $EnvRoot "logs\backend" } else { $env:LOGGING_FILE_PATH }
New-Item -ItemType Directory -Force -Path $env:APP_UPLOAD_BASE_DIR, $env:LOGGING_FILE_PATH, (Join-Path $EnvRoot "backups"), (Join-Path $EnvRoot "local-vm") | Out-Null

function Assert-Command {
    param([string]$Command)
    if (-not (Get-Command $Command -ErrorAction SilentlyContinue)) {
        throw "Missing command: $Command"
    }
}

function Wait-MySql {
    Write-Host "Waiting for MySQL..."
    $previousMysqlPwd = $env:MYSQL_PWD
    for ($i = 1; $i -le 40; $i++) {
        try {
            $env:MYSQL_PWD = $DbPassword
            docker compose exec -T -e MYSQL_PWD mysql mysqladmin ping -h 127.0.0.1 -uroot *> $null
            if ($LASTEXITCODE -eq 0) {
                Write-Host "MySQL is ready."
                return
            }
        } finally {
            if ($null -eq $previousMysqlPwd) {
                Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
            } else {
                $env:MYSQL_PWD = $previousMysqlPwd
            }
        }
        Start-Sleep -Seconds 2
    }
    throw "MySQL did not become ready in time."
}

function Invoke-ComposeMySqlFile {
    param(
        [string]$SqlPath,
        [string]$Database = ""
    )
    $previousMysqlPwd = $env:MYSQL_PWD
    try {
        $env:MYSQL_PWD = $DbPassword
        $args = @("compose", "exec", "-T", "-e", "MYSQL_PWD", "mysql", "mysql", "--default-character-set=utf8mb4", "-uroot")
        if (-not [string]::IsNullOrWhiteSpace($Database)) {
            $args += $Database
        }
        Get-Content -Path $SqlPath -Raw | docker @args
        if ($LASTEXITCODE -ne 0) {
            throw "$SqlPath import failed."
        }
    } finally {
        if ($null -eq $previousMysqlPwd) {
            Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
        } else {
            $env:MYSQL_PWD = $previousMysqlPwd
        }
    }
}

function Wait-Http {
    param(
        [string]$Url,
        [string]$Name,
        [int]$Retries = 60
    )

    Write-Host "Waiting for $Name..."
    for ($i = 1; $i -le $Retries; $i++) {
        try {
            Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 2 *> $null
            Write-Host "$Name is ready."
            return
        } catch {
            Start-Sleep -Seconds 2
        }
    }
    Write-Host "Timed out waiting for $Name. The process windows are still open; check their logs."
}

Assert-Command "java"
Assert-Command "mvn"
Assert-Command "node"
Assert-Command "pnpm"
Assert-Command "docker"

if (-not $SkipCompatCheck) {
    & (Join-Path $ScriptDir "check-env.ps1")
    if ($LASTEXITCODE -ne 0) { throw "Environment check failed." }
    & (Join-Path $ScriptDir "local-vm-compat-check.ps1")
    if ($LASTEXITCODE -ne 0) { throw "Local VM compatibility check failed." }
}

if ([string]::IsNullOrWhiteSpace($DbPassword)) {
    throw "Set DB_PASSWORD in the local environment or pass -DbPassword. Do not store real passwords in source files."
}

$env:DB_PORT = $DbPort
$env:REDIS_PORT = $RedisPort
$env:ADMINER_PORT = $AdminerPort
$env:DB_PASSWORD = $DbPassword

Push-Location $DeployDir
try {
    docker compose config | Out-Null
    docker compose up -d
    Wait-MySql

    if (-not $SkipDbInit) {
        Write-Host "Importing schema.sql and data.sql..."
        Invoke-ComposeMySqlFile -SqlPath $SchemaSql
        Invoke-ComposeMySqlFile -SqlPath $DataSql -Database "cyberfusion_soc"
    }
} finally {
    Pop-Location
}

$BackendCommand = @"
`$env:SERVER_PORT = '$ServerPort'
`$env:DB_PORT = '$DbPort'
`$env:REDIS_PORT = '$RedisPort'
`$env:DB_PASSWORD = '$DbPassword'
`$env:CYBERFUSION_ENV_ROOT = '$EnvRoot'
`$env:APP_UPLOAD_BASE_DIR = '$env:APP_UPLOAD_BASE_DIR'
`$env:LOGGING_FILE_PATH = '$env:LOGGING_FILE_PATH'
Set-Location '$BackendDir'
mvn spring-boot:run
"@

$FrontendCommand = @"
`$env:VITE_API_PROXY_TARGET = 'http://127.0.0.1:$ServerPort'
Set-Location '$FrontendDir'
pnpm install --frozen-lockfile
pnpm dev --port $FrontendPort
"@

Start-Process powershell.exe -ArgumentList @("-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $BackendCommand)
Wait-Http -Url "http://127.0.0.1:$ServerPort/api/v3/api-docs" -Name "Backend API"
Start-Process powershell.exe -ArgumentList @("-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $FrontendCommand)

$FrontendUrl = "http://127.0.0.1:$FrontendPort/"
Wait-Http -Url $FrontendUrl -Name "Frontend"
Write-Host "Frontend: $FrontendUrl"
Write-Host "Backend API: http://127.0.0.1:$ServerPort/api"
Write-Host "Adminer: http://127.0.0.1:$AdminerPort"
Write-Host "Runtime root: $EnvRoot"
Start-Process $FrontendUrl

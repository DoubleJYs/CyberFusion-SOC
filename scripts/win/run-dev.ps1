param(
    [string]$DbHost = $(if ([string]::IsNullOrWhiteSpace($env:DB_HOST)) { "127.0.0.1" } else { $env:DB_HOST }),
    [string]$DbPort = $(if ([string]::IsNullOrWhiteSpace($env:DB_PORT)) { "3306" } else { $env:DB_PORT }),
    [string]$DbName = $(if ([string]::IsNullOrWhiteSpace($env:DB_NAME)) { "cyberfusion_soc" } else { $env:DB_NAME }),
    [string]$DbUsername = $(if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME)) { "root" } else { $env:DB_USERNAME }),
    [string]$RedisPort = $(if ([string]::IsNullOrWhiteSpace($env:REDIS_PORT)) { "6379" } else { $env:REDIS_PORT }),
    [string]$RedisHost = $(if ([string]::IsNullOrWhiteSpace($env:REDIS_HOST)) { "127.0.0.1" } else { $env:REDIS_HOST }),
    [string]$ServerPort = $(if ([string]::IsNullOrWhiteSpace($env:SERVER_PORT)) { "18080" } else { $env:SERVER_PORT }),
    [string]$FrontendPort = $(if ([string]::IsNullOrWhiteSpace($env:FRONTEND_PORT)) { "5174" } else { $env:FRONTEND_PORT }),
    [string]$DbPassword = $env:DB_PASSWORD,
    [string]$EnvRoot = "",
    [switch]$SkipCompatCheck,
    [switch]$SkipDbInit,
    [switch]$SkipBrowserOpen
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path (Join-Path $ScriptDir "..\..")
$BackendDir = Join-Path $ProjectRoot "backend"
$FrontendDir = Join-Path $ProjectRoot "frontend"
$DefaultEnvRoot = "D:\CyberFusion\Environment\cyberfusion-platform"

function Assert-ProjectOnDataDrive {
    param([string]$PathValue)
    if ($PathValue -match "^[A-Za-z]:") {
        $Drive = $PathValue.Substring(0, 1).ToUpperInvariant()
        if ($Drive -ne "D") {
            throw "Windows no-Docker mode requires the project under D:\CyberFusion, not $PathValue. Move 00-cyberfusion-platform to D: before starting."
        }
    }
}

function Assert-DriveRoot {
    param([string]$PathValue)
    if ($PathValue -match "^[A-Za-z]:") {
        $DriveRoot = "$($PathValue.Substring(0, 2))\"
        if (-not (Test-Path $DriveRoot)) {
            throw "Required Windows drive not found: $DriveRoot. Put CyberFusion on D: or pass -EnvRoot to another non-source runtime path."
        }
    }
}

Assert-ProjectOnDataDrive -PathValue $ProjectRoot.Path
if ([string]::IsNullOrWhiteSpace($EnvRoot)) {
    $EnvRoot = if ([string]::IsNullOrWhiteSpace($env:CYBERFUSION_ENV_ROOT)) { $DefaultEnvRoot } else { $env:CYBERFUSION_ENV_ROOT }
}
Assert-DriveRoot -PathValue $EnvRoot
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

function Test-TcpPort {
    param(
        [string]$HostName,
        [int]$Port,
        [int]$TimeoutMs = 1500
    )

    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $connect = $client.BeginConnect($HostName, $Port, $null, $null)
        if (-not $connect.AsyncWaitHandle.WaitOne($TimeoutMs, $false)) {
            return $false
        }
        $client.EndConnect($connect)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Assert-TcpPort {
    param(
        [string]$Name,
        [string]$HostName,
        [int]$Port
    )

    if (-not (Test-TcpPort -HostName $HostName -Port $Port)) {
        throw "$Name is not reachable at $HostName`:$Port. Start the local service first; Windows no-Docker mode does not start MySQL or Redis for you."
    }
    Write-Host "$Name is reachable at $HostName`:$Port"
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

if (-not $SkipCompatCheck) {
    & (Join-Path $ScriptDir "check-env.ps1")
    if ($LASTEXITCODE -ne 0) { throw "Environment check failed." }
    & (Join-Path $ScriptDir "local-vm-compat-check.ps1")
    if ($LASTEXITCODE -ne 0) { throw "Local VM compatibility check failed." }
}

if ([string]::IsNullOrWhiteSpace($DbPassword)) {
    throw "Set DB_PASSWORD in the local environment or pass -DbPassword. Do not store real passwords in source files."
}

$env:DB_HOST = $DbHost
$env:DB_PORT = $DbPort
$env:DB_NAME = $DbName
$env:DB_USERNAME = $DbUsername
$env:REDIS_HOST = $RedisHost
$env:REDIS_PORT = $RedisPort
$env:DB_PASSWORD = $DbPassword
$env:SERVER_PORT = $ServerPort
$env:FRONTEND_PORT = $FrontendPort
$env:VITE_API_PROXY_TARGET = "http://127.0.0.1:$ServerPort"

Assert-TcpPort -Name "MySQL" -HostName $DbHost -Port ([int]$DbPort)
Assert-TcpPort -Name "Redis" -HostName $RedisHost -Port ([int]$RedisPort)

if (-not $SkipDbInit) {
    & (Join-Path $ScriptDir "init-local-db.ps1") -DbHost $DbHost -DbPort ([int]$DbPort) -DbName $DbName -DbUsername $DbUsername -DbPassword $DbPassword
    if ($LASTEXITCODE -ne 0) { throw "Local database initialization failed." }
}

$BackendCommand = @"
Set-Location '$BackendDir'
mvn spring-boot:run
"@

$FrontendCommand = @"
Set-Location '$FrontendDir'
pnpm install --frozen-lockfile
pnpm dev --port $FrontendPort
"@

Start-Process powershell.exe -ArgumentList @("-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $BackendCommand)
Wait-Http -Url "http://127.0.0.1:$ServerPort/api/health" -Name "Backend health"
Start-Process powershell.exe -ArgumentList @("-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $FrontendCommand)

$FrontendUrl = "http://127.0.0.1:$FrontendPort/"
Wait-Http -Url $FrontendUrl -Name "Frontend"
Write-Host "Frontend: $FrontendUrl"
Write-Host "Backend API: http://127.0.0.1:$ServerPort/api"
Write-Host "Runtime root: $EnvRoot"
if (-not $SkipBrowserOpen) {
    Start-Process $FrontendUrl
}

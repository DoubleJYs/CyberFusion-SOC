param(
    [string]$DbHost = $(if ([string]::IsNullOrWhiteSpace($env:DB_HOST)) { "127.0.0.1" } else { $env:DB_HOST }),
    [int]$DbPort = $(if ([string]::IsNullOrWhiteSpace($env:DB_PORT)) { 3306 } else { [int]$env:DB_PORT }),
    [string]$DbName = $(if ([string]::IsNullOrWhiteSpace($env:DB_NAME)) { "cyberfusion_soc" } else { $env:DB_NAME }),
    [string]$DbUsername = $(if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME)) { "root" } else { $env:DB_USERNAME }),
    [string]$DbPassword = $env:DB_PASSWORD,
    [string]$RedisHost = $(if ([string]::IsNullOrWhiteSpace($env:REDIS_HOST)) { "127.0.0.1" } else { $env:REDIS_HOST }),
    [int]$RedisPort = $(if ([string]::IsNullOrWhiteSpace($env:REDIS_PORT)) { 6379 } else { [int]$env:REDIS_PORT }),
    [string]$ServerPort = $(if ([string]::IsNullOrWhiteSpace($env:SERVER_PORT)) { "18080" } else { $env:SERVER_PORT }),
    [string]$FrontendPort = $(if ([string]::IsNullOrWhiteSpace($env:FRONTEND_PORT)) { "5174" } else { $env:FRONTEND_PORT }),
    [string]$EnvRoot = $(if ([string]::IsNullOrWhiteSpace($env:CYBERFUSION_ENV_ROOT)) { "D:\CyberFusion\Environment\cyberfusion-platform" } else { $env:CYBERFUSION_ENV_ROOT }),
    [switch]$SkipDbInit,
    [switch]$SkipBuild,
    [switch]$SkipBrowserOpen
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path (Join-Path $ScriptDir "..\..")

function Assert-DDrivePath {
    param(
        [string]$Label,
        [string]$PathValue
    )

    if ($PathValue -notmatch "^[A-Za-z]:") {
        throw "$Label must use an absolute D: path, not $PathValue."
    }
    $Drive = $PathValue.Substring(0, 1).ToUpperInvariant()
    if ($Drive -ne "D") {
        throw "$Label must stay on D: under D:\CyberFusion, not $PathValue."
    }
}

if ([string]::IsNullOrWhiteSpace($DbPassword)) {
    $SecurePassword = Read-Host "Enter local MySQL password for $DbUsername@$DbHost`:$DbPort" -AsSecureString
    $PasswordPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecurePassword)
    try {
        $DbPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($PasswordPtr)
    } finally {
        if ($PasswordPtr -ne [IntPtr]::Zero) {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($PasswordPtr)
        }
    }
}

if ([string]::IsNullOrWhiteSpace($DbPassword)) {
    throw "DB_PASSWORD is required. Provide -DbPassword or enter it when prompted."
}

Assert-DDrivePath -Label "Project root" -PathValue $ProjectRoot.Path
Assert-DDrivePath -Label "Runtime root" -PathValue $EnvRoot

$env:DB_HOST = $DbHost
$env:DB_PORT = "$DbPort"
$env:DB_NAME = $DbName
$env:DB_USERNAME = $DbUsername
$env:DB_PASSWORD = $DbPassword
$env:REDIS_HOST = $RedisHost
$env:REDIS_PORT = "$RedisPort"
$env:SERVER_PORT = $ServerPort
$env:FRONTEND_PORT = $FrontendPort
$env:CYBERFUSION_ENV_ROOT = $EnvRoot

& (Join-Path $ScriptDir "prepare-d-drive.ps1") -EnvRoot $EnvRoot -DbHost $DbHost -DbPort $DbPort -RedisHost $RedisHost -RedisPort $RedisPort

$PreStartArgs = @("-PreStart")
if ($SkipDbInit) { $PreStartArgs += "-SkipDbInit" }
if ($SkipBuild) { $PreStartArgs += "-SkipBuild" }
& (Join-Path $ScriptDir "verify-no-docker.ps1") @PreStartArgs

$RunArgs = @(
    "-DbHost", $DbHost,
    "-DbPort", "$DbPort",
    "-DbName", $DbName,
    "-DbUsername", $DbUsername,
    "-RedisHost", $RedisHost,
    "-RedisPort", "$RedisPort",
    "-ServerPort", $ServerPort,
    "-FrontendPort", $FrontendPort,
    "-EnvRoot", $EnvRoot
)
if ($SkipDbInit) { $RunArgs += "-SkipDbInit" }
if ($SkipBrowserOpen) { $RunArgs += "-SkipBrowserOpen" }
& (Join-Path $ScriptDir "run-dev.ps1") @RunArgs

& (Join-Path $ScriptDir "verify-no-docker.ps1") -PostStart

Write-Host ""
Write-Host "CyberFusion Windows no-Docker startup completed."

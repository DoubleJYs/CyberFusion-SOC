$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path (Join-Path $ScriptDir "..\..")
$DefaultEnvRoot = "D:\CyberFusion\Environment\cyberfusion-platform"
$EnvRoot = if ([string]::IsNullOrWhiteSpace($env:CYBERFUSION_ENV_ROOT)) { $DefaultEnvRoot } else { $env:CYBERFUSION_ENV_ROOT }

if ($ProjectRoot.Path -match "^[A-Za-z]:") {
    $ProjectDrive = $ProjectRoot.Path.Substring(0, 1).ToUpperInvariant()
    if ($ProjectDrive -ne "D") {
        throw "Windows no-Docker mode requires the project under D:\CyberFusion, not $($ProjectRoot.Path). Move 00-cyberfusion-platform to D: before starting."
    }
}

if ($EnvRoot -match "^[A-Za-z]:") {
    $DriveRoot = "$($EnvRoot.Substring(0, 2))\"
    if (-not (Test-Path $DriveRoot)) {
        throw "Required Windows drive not found: $DriveRoot. Put CyberFusion on D: or set CYBERFUSION_ENV_ROOT to another non-source runtime path."
    }
}

$env:CYBERFUSION_ENV_ROOT = $EnvRoot
$env:APP_UPLOAD_BASE_DIR = if ([string]::IsNullOrWhiteSpace($env:APP_UPLOAD_BASE_DIR)) { Join-Path $EnvRoot "uploads" } else { $env:APP_UPLOAD_BASE_DIR }
$env:LOGGING_FILE_PATH = if ([string]::IsNullOrWhiteSpace($env:LOGGING_FILE_PATH)) { Join-Path $EnvRoot "logs\backend" } else { $env:LOGGING_FILE_PATH }
$env:SERVER_PORT = if ([string]::IsNullOrWhiteSpace($env:SERVER_PORT)) { "18080" } else { $env:SERVER_PORT }
New-Item -ItemType Directory -Force -Path $env:APP_UPLOAD_BASE_DIR, $env:LOGGING_FILE_PATH | Out-Null

Set-Location (Join-Path $ProjectRoot "backend")
mvn spring-boot:run

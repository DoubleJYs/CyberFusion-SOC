$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path (Join-Path $ScriptDir "..\..")
$EnvRoot = if ([string]::IsNullOrWhiteSpace($env:CYBERFUSION_ENV_ROOT)) { Join-Path $env:USERPROFILE "Environment\cyberfusion-platform" } else { $env:CYBERFUSION_ENV_ROOT }

$env:CYBERFUSION_ENV_ROOT = $EnvRoot
$env:APP_UPLOAD_BASE_DIR = if ([string]::IsNullOrWhiteSpace($env:APP_UPLOAD_BASE_DIR)) { Join-Path $EnvRoot "uploads" } else { $env:APP_UPLOAD_BASE_DIR }
$env:LOGGING_FILE_PATH = if ([string]::IsNullOrWhiteSpace($env:LOGGING_FILE_PATH)) { Join-Path $EnvRoot "logs\backend" } else { $env:LOGGING_FILE_PATH }
$env:SERVER_PORT = if ([string]::IsNullOrWhiteSpace($env:SERVER_PORT)) { "18080" } else { $env:SERVER_PORT }
New-Item -ItemType Directory -Force -Path $env:APP_UPLOAD_BASE_DIR, $env:LOGGING_FILE_PATH | Out-Null

Set-Location (Join-Path $ProjectRoot "backend")
mvn spring-boot:run

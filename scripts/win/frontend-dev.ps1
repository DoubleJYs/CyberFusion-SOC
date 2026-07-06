$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptDir "runtime-paths.ps1")

$ProjectRoot = (Resolve-Path (Join-Path $ScriptDir "..\..")).Path
$EnvRoot = Resolve-CyberFusionEnvRoot -ProjectRoot $ProjectRoot
Set-CyberFusionRuntimeEnvironment -ProjectRoot $ProjectRoot -EnvRoot $EnvRoot
Initialize-CyberFusionRuntimeDirectories -EnvRoot $EnvRoot

$ServerPort = if ([string]::IsNullOrWhiteSpace($env:SERVER_PORT)) { "18080" } else { $env:SERVER_PORT }
$FrontendPort = if ([string]::IsNullOrWhiteSpace($env:FRONTEND_PORT)) { "5174" } else { $env:FRONTEND_PORT }
$env:VITE_API_PROXY_TARGET = if ([string]::IsNullOrWhiteSpace($env:VITE_API_PROXY_TARGET)) { "http://127.0.0.1:$ServerPort" } else { $env:VITE_API_PROXY_TARGET }

Set-Location (Join-Path $ProjectRoot "frontend")
pnpm install --frozen-lockfile --store-dir "$env:PNPM_STORE_DIR"
pnpm dev --port $FrontendPort

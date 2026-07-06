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

Assert-DDrivePath -Label "Runtime root" -PathValue $EnvRoot
$env:CYBERFUSION_ENV_ROOT = $EnvRoot
$env:PNPM_STORE_DIR = if ([string]::IsNullOrWhiteSpace($env:PNPM_STORE_DIR)) { Join-Path $EnvRoot "caches\pnpm-store" } else { $env:PNPM_STORE_DIR }
$env:npm_config_cache = if ([string]::IsNullOrWhiteSpace($env:npm_config_cache)) { Join-Path $EnvRoot "caches\npm" } else { $env:npm_config_cache }
Assert-DDrivePath -Label "pnpm store" -PathValue $env:PNPM_STORE_DIR
Assert-DDrivePath -Label "npm cache" -PathValue $env:npm_config_cache
New-Item -ItemType Directory -Force -Path $env:PNPM_STORE_DIR, $env:npm_config_cache | Out-Null

$ServerPort = if ([string]::IsNullOrWhiteSpace($env:SERVER_PORT)) { "18080" } else { $env:SERVER_PORT }
$FrontendPort = if ([string]::IsNullOrWhiteSpace($env:FRONTEND_PORT)) { "5174" } else { $env:FRONTEND_PORT }
$env:VITE_API_PROXY_TARGET = if ([string]::IsNullOrWhiteSpace($env:VITE_API_PROXY_TARGET)) { "http://127.0.0.1:$ServerPort" } else { $env:VITE_API_PROXY_TARGET }

Set-Location (Join-Path $ProjectRoot "frontend")
pnpm install --frozen-lockfile --store-dir "$env:PNPM_STORE_DIR"
pnpm dev --port $FrontendPort

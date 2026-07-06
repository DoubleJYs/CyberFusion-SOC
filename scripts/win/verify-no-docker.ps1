param(
    [string]$BaseUrl = $(if ($env:CYBERFUSION_FRONTEND_URL) { $env:CYBERFUSION_FRONTEND_URL } else { "http://127.0.0.1:5174" }),
    [string]$ApiBaseUrl = $(if ($env:CYBERFUSION_API_BASE) { $env:CYBERFUSION_API_BASE } else { "http://127.0.0.1:18080/api" }),
    [switch]$SkipBuild,
    [switch]$SkipDbInit,
    [switch]$SkipDoctor,
    [switch]$SkipLocalVmCheck
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path (Join-Path $ScriptDir "..\..")

function Invoke-Step {
    param(
        [string]$Name,
        [scriptblock]$Action
    )

    Write-Host ""
    Write-Host "== $Name =="
    $global:LASTEXITCODE = 0
    & $Action
    if (-not $?) {
        throw "$Name failed."
    }
    if ($global:LASTEXITCODE -ne 0) {
        throw "$Name failed with exit code $global:LASTEXITCODE"
    }
}

if ($ProjectRoot.Path -match "^[A-Za-z]:") {
    $ProjectDrive = $ProjectRoot.Path.Substring(0, 1).ToUpperInvariant()
    if ($ProjectDrive -ne "D") {
        throw "Windows no-Docker mode requires the project under D:\CyberFusion, not $($ProjectRoot.Path). Move 00-cyberfusion-platform to D: before verification."
    }
}

if ([string]::IsNullOrWhiteSpace($env:CYBERFUSION_ENV_ROOT)) {
    $env:CYBERFUSION_ENV_ROOT = "D:\CyberFusion\Environment\cyberfusion-platform"
}
if ($env:CYBERFUSION_ENV_ROOT -match "^[A-Za-z]:") {
    $EnvDrive = $env:CYBERFUSION_ENV_ROOT.Substring(0, 1).ToUpperInvariant()
    if ($EnvDrive -ne "D") {
        throw "Windows no-Docker runtime data must stay on D: under D:\CyberFusion, not $env:CYBERFUSION_ENV_ROOT."
    }
}

Invoke-Step "Environment check without Docker" {
    & (Join-Path $ScriptDir "check-env.ps1")
}

if (-not $SkipLocalVmCheck) {
    Invoke-Step "Local VM compatibility check" {
        & (Join-Path $ScriptDir "local-vm-compat-check.ps1")
    }
}

if (-not $SkipDbInit) {
    Invoke-Step "Local MySQL schema and seed refresh" {
        & (Join-Path $ScriptDir "init-local-db.ps1")
    }
}

if (-not $SkipBuild) {
    Invoke-Step "Backend and frontend build" {
        & (Join-Path $ScriptDir "build-all.ps1")
    }
}

if (-not $SkipDoctor) {
    Invoke-Step "Runtime doctor" {
        & (Join-Path $ScriptDir "dev-doctor.ps1") -BaseUrl $BaseUrl -ApiBaseUrl $ApiBaseUrl
    }
} else {
    Write-Host ""
    Write-Host "Runtime doctor skipped. After run-dev.ps1 starts the app, run:"
    Write-Host "  .\scripts\win\verify-no-docker.ps1 -SkipBuild -SkipDbInit"
}

Write-Host ""
Write-Host "Windows no-Docker verification finished."

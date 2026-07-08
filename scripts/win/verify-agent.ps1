param(
    [string]$EnvRoot = "",
    [string]$ApiBaseUrl = $(if ($env:CYBERFUSION_API_BASE) { $env:CYBERFUSION_API_BASE } else { "http://127.0.0.1:18080/api" }),
    [string]$AgentId = $(if ($env:CYBERFUSION_AGENT_ID) { $env:CYBERFUSION_AGENT_ID } else { "windows-host-agent" }),
    [string]$ServiceName = "CyberFusionHostAgent",
    [switch]$UploadOnce
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptDir "runtime-paths.ps1")

$ProjectRoot = (Resolve-Path (Join-Path $ScriptDir "..\..")).Path
$ResolvedEnvRoot = Resolve-CyberFusionEnvRoot -ProjectRoot $ProjectRoot -EnvRoot $EnvRoot
Set-CyberFusionRuntimeEnvironment -ProjectRoot $ProjectRoot -EnvRoot $ResolvedEnvRoot

$AgentRoot = Join-Path $ResolvedEnvRoot "agent\$AgentId"
$BinaryPath = Join-Path $AgentRoot "bin\cyberfusion-agent.exe"
$ConfigFile = Join-Path $AgentRoot "config\agent.env"
$RuntimeDir = Join-Path $AgentRoot "runtime"

if (-not (Test-Path $BinaryPath)) {
    throw "Missing agent binary: $BinaryPath"
}
if (-not (Test-Path $ConfigFile)) {
    throw "Missing agent config: $ConfigFile"
}
if ((Get-Content -Raw -Path $ConfigFile) -notmatch "CYBERFUSION_AGENT_TOKEN=") {
    throw "Agent config does not contain CYBERFUSION_AGENT_TOKEN."
}

try {
    $Health = Invoke-RestMethod -Method Get -Uri "$ApiBaseUrl/health"
    if ($Health.code -ne "SUCCESS") {
        throw "Health response code is $($Health.code)"
    }
    Write-Host "[PASS] Platform API health is SUCCESS"
} catch {
    throw "Platform API is not reachable at $ApiBaseUrl/health. Start Docker/local backend first. $_"
}

$Service = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
if ($Service) {
    Write-Host "[PASS] Service $ServiceName status: $($Service.Status)"
} else {
    Write-Host "[WARN] Service $ServiceName is not installed. Foreground verification is still possible."
}

if ($UploadOnce) {
    & $BinaryPath --config-file $ConfigFile --mode collect --os-type windows
    if ($LASTEXITCODE -ne 0) {
        throw "Agent upload exited with code $LASTEXITCODE"
    }
    Write-Host "[PASS] Agent one-shot upload completed"
}

Write-Host "[PASS] Agent binary: $BinaryPath"
Write-Host "[PASS] Agent config exists and token was not printed"
Write-Host "[PASS] Agent runtime: $RuntimeDir"

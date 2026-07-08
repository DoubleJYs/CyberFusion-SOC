param(
    [string]$EnvRoot = "",
    [string]$AgentId = $(if ($env:CYBERFUSION_AGENT_ID) { $env:CYBERFUSION_AGENT_ID } else { "windows-host-agent" }),
    [string]$ServiceName = "CyberFusionHostAgent",
    [switch]$Foreground
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

if (-not (Test-Path $BinaryPath)) {
    throw "Agent binary not found: $BinaryPath. Run scripts\win\install-agent.ps1 first."
}
if (-not (Test-Path $ConfigFile)) {
    throw "Agent config not found: $ConfigFile. Run scripts\win\install-agent.ps1 first."
}

if ($Foreground) {
    & $BinaryPath --config-file $ConfigFile --mode collect --os-type windows --once=false --interval 60s
    exit $LASTEXITCODE
}

$Service = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
if (-not $Service) {
    throw "Service $ServiceName is not installed. Run scripts\win\install-agent.ps1 or use -Foreground."
}

Start-Service -Name $ServiceName
Write-Host "Started Windows service: $ServiceName"

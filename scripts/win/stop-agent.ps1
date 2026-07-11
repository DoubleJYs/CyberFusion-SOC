param(
    [string]$EnvRoot = "",
    [string]$AgentId = $(if ($env:CYBERFUSION_AGENT_ID) { $env:CYBERFUSION_AGENT_ID } else { "windows-host-agent" }),
    [string]$ServiceName = "CyberFusionHostAgent"
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptDir "runtime-paths.ps1")

$ProjectRoot = (Resolve-Path (Join-Path $ScriptDir "..\..")).Path
$ResolvedEnvRoot = Resolve-CyberFusionEnvRoot -ProjectRoot $ProjectRoot -EnvRoot $EnvRoot
Set-CyberFusionRuntimeEnvironment -ProjectRoot $ProjectRoot -EnvRoot $ResolvedEnvRoot

$AgentRoot = Join-Path $ResolvedEnvRoot "agent\$AgentId"
$ConfigFile = Join-Path $AgentRoot "config\agent.env"

if (-not (Test-Path $ConfigFile)) {
    throw "Agent config not found: $ConfigFile. Run scripts\win\install-agent.ps1 first."
}

$Service = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
if (-not $Service) {
    throw "Service $ServiceName is not installed. Run scripts\win\install-agent.ps1 first."
}

if ($Service.Status -ne "Stopped") {
    Stop-Service -Name $ServiceName -ErrorAction Stop
}

Write-Host "Stopped Windows service: $ServiceName"
Write-Host "Agent install files and runtime queue were preserved."

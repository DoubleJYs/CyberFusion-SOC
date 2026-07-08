param(
    [string]$EnvRoot = "",
    [string]$AgentId = $(if ($env:CYBERFUSION_AGENT_ID) { $env:CYBERFUSION_AGENT_ID } else { "windows-host-agent" }),
    [string]$ServiceName = "CyberFusionHostAgent",
    [switch]$RemoveRuntime,
    [switch]$PurgeLocalState
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptDir "runtime-paths.ps1")

$ProjectRoot = (Resolve-Path (Join-Path $ScriptDir "..\..")).Path
$ResolvedEnvRoot = Resolve-CyberFusionEnvRoot -ProjectRoot $ProjectRoot -EnvRoot $EnvRoot
Set-CyberFusionRuntimeEnvironment -ProjectRoot $ProjectRoot -EnvRoot $ResolvedEnvRoot

$AgentRoot = Join-Path $ResolvedEnvRoot "agent\$AgentId"
$BinDir = Join-Path $AgentRoot "bin"
$ConfigDir = Join-Path $AgentRoot "config"
$RuntimeDir = Join-Path $AgentRoot "runtime"

$Service = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
if ($Service) {
    if ($Service.Status -ne "Stopped") {
        Stop-Service -Name $ServiceName -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 2
    }
    sc.exe delete $ServiceName | Out-Null
    Write-Host "Removed Windows service: $ServiceName"
} else {
    Write-Host "Windows service not found: $ServiceName"
}

if (Test-Path $BinDir) {
    Remove-Item -Recurse -Force -Path $BinDir
}

if ($PurgeLocalState) {
    if (Test-Path $AgentRoot) {
        Remove-Item -Recurse -Force -Path $AgentRoot
    }
    Write-Host "Purged local Agent state: $AgentRoot"
} else {
    if (Test-Path $ConfigDir) {
        Remove-Item -Recurse -Force -Path $ConfigDir
    }
    if ($RemoveRuntime) {
        if (Test-Path $RuntimeDir) {
            Remove-Item -Recurse -Force -Path $RuntimeDir
        }
        Write-Host "Removed Agent runtime directory: $RuntimeDir"
    } else {
        New-Item -ItemType Directory -Force -Path $RuntimeDir | Out-Null
        Write-Host "Preserved Agent runtime and pending queue: $RuntimeDir"
    }
}

Write-Host "Platform database was not contacted or modified by this script."

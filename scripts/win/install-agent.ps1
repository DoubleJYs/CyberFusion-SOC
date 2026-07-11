param(
    [string]$EnvRoot = "",
    [string]$ApiBaseUrl = $(if ($env:CYBERFUSION_API_BASE) { $env:CYBERFUSION_API_BASE } else { "http://127.0.0.1:18080/api" }),
    [string]$AgentId = $(if ($env:CYBERFUSION_AGENT_ID) { $env:CYBERFUSION_AGENT_ID } else { "windows-host-agent" }),
    [string]$AgentToken = $env:CYBERFUSION_AGENT_TOKEN,
    [string]$AdminAccessToken = $env:CYBERFUSION_ADMIN_ACCESS_TOKEN,
    [string]$ServiceName = "CyberFusionHostAgent",
    [string]$FimPath = "",
    [string]$AgentVersion = $(if ($env:CYBERFUSION_AGENT_VERSION) { $env:CYBERFUSION_AGENT_VERSION } else { "0.1.0-dev" }),
    [string]$AgentProfile = $(if ($env:CYBERFUSION_AGENT_PROFILE) { $env:CYBERFUSION_AGENT_PROFILE } else { "full" }),
    [string]$BinaryPath = "",
    [switch]$SkipServiceInstall
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptDir "runtime-paths.ps1")

$ProjectRoot = (Resolve-Path (Join-Path $ScriptDir "..\..")).Path
$ResolvedEnvRoot = Resolve-CyberFusionEnvRoot -ProjectRoot $ProjectRoot -EnvRoot $EnvRoot
Set-CyberFusionRuntimeEnvironment -ProjectRoot $ProjectRoot -EnvRoot $ResolvedEnvRoot
Initialize-CyberFusionRuntimeDirectories -EnvRoot $ResolvedEnvRoot

$AgentRoot = Join-Path $ResolvedEnvRoot "agent\$AgentId"
$BinDir = Join-Path $AgentRoot "bin"
$ConfigDir = Join-Path $AgentRoot "config"
$RuntimeDir = Join-Path $AgentRoot "runtime"
New-Item -ItemType Directory -Force -Path $BinDir, $ConfigDir, $RuntimeDir | Out-Null

if ([string]::IsNullOrWhiteSpace($AgentToken) -and -not [string]::IsNullOrWhiteSpace($AdminAccessToken)) {
    $Hostname = $env:COMPUTERNAME
    $RegisterBody = @{
        agentId = $AgentId
        agentName = $Hostname
        hostname = $Hostname
        osType = "windows"
        osVersion = [System.Environment]::OSVersion.VersionString
        architecture = $env:PROCESSOR_ARCHITECTURE
        agentVersion = $AgentVersion
        labels = @{
            install = "windows-service"
            agent = "go"
        }
    } | ConvertTo-Json -Depth 5
    $Response = Invoke-RestMethod -Method Post -Uri "$ApiBaseUrl/soc/agents/register" -Headers @{ Authorization = "Bearer $AdminAccessToken" } -ContentType "application/json" -Body $RegisterBody
    if ($Response.code -ne "SUCCESS" -or -not $Response.data.agentToken) {
        throw "Agent registration failed through $ApiBaseUrl/soc/agents/register"
    }
    $AgentToken = $Response.data.agentToken
}

if ([string]::IsNullOrWhiteSpace($AgentToken)) {
    throw "Agent token is required. Set CYBERFUSION_AGENT_TOKEN, pass -AgentToken, or pass -AdminAccessToken to register."
}

if ([string]::IsNullOrWhiteSpace($BinaryPath)) {
    $AgentSourceRoot = Join-Path $ProjectRoot "agent"
    $BinaryPath = Join-Path $BinDir "cyberfusion-agent.exe"
    Push-Location $AgentSourceRoot
    $PreviousGoos = $env:GOOS
    $PreviousGoarch = $env:GOARCH
    try {
        if (-not $env:GOCACHE) {
            $env:GOCACHE = Join-Path $ResolvedEnvRoot "caches\go-build"
        }
        $env:GOOS = "windows"
        $env:GOARCH = "amd64"
        go build -o $BinaryPath ./cmd/cyberfusion-agent
    }
    finally {
        if ($null -eq $PreviousGoos) { Remove-Item Env:\GOOS -ErrorAction SilentlyContinue } else { $env:GOOS = $PreviousGoos }
        if ($null -eq $PreviousGoarch) { Remove-Item Env:\GOARCH -ErrorAction SilentlyContinue } else { $env:GOARCH = $PreviousGoarch }
        Pop-Location
    }
} else {
    Copy-Item -Force -Path $BinaryPath -Destination (Join-Path $BinDir "cyberfusion-agent.exe")
    $BinaryPath = Join-Path $BinDir "cyberfusion-agent.exe"
}

$ConfigFile = Join-Path $ConfigDir "agent.env"
@(
    "CYBERFUSION_API_BASE=$ApiBaseUrl"
    "CYBERFUSION_AGENT_ID=$AgentId"
    "CYBERFUSION_AGENT_TOKEN=$AgentToken"
    "CYBERFUSION_AGENT_RUNTIME_DIR=$RuntimeDir"
    "CYBERFUSION_AGENT_FIM_PATH=$FimPath"
    "CYBERFUSION_AGENT_INTERVAL=60s"
    "CYBERFUSION_AGENT_VERSION=$AgentVersion"
    "CYBERFUSION_AGENT_PROFILE=$AgentProfile"
) | Set-Content -Path $ConfigFile -Encoding UTF8

try {
    $CurrentUserAcl = "$($env:USERNAME):(OI)(CI)F"
    icacls $AgentRoot /inheritance:r /grant:r "SYSTEM:(OI)(CI)F" "Administrators:(OI)(CI)F" $CurrentUserAcl | Out-Null
} catch {
    Write-Warning "Could not tighten ACLs on $AgentRoot. Review permissions manually."
}

if (-not $SkipServiceInstall) {
    $Existing = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
    if ($Existing) {
        Stop-Service -Name $ServiceName -ErrorAction SilentlyContinue
        sc.exe delete $ServiceName | Out-Null
        Start-Sleep -Seconds 2
    }
    $BinPath = "`"$BinaryPath`" --config-file `"$ConfigFile`" --mode collect --os-type windows --once=false --interval 60s"
    sc.exe create $ServiceName binPath= $BinPath start= auto DisplayName= "CyberFusion Host Agent" | Out-Null
    sc.exe description $ServiceName "CyberFusion self-developed Windows Host Agent. Platform services remain managed by Docker or local startup scripts." | Out-Null
    Write-Host "Installed Windows service: $ServiceName"
} else {
    Write-Host "Service install skipped."
}

Write-Host "Agent binary: $BinaryPath"
Write-Host "Agent config: $ConfigFile"
Write-Host "Agent runtime: $RuntimeDir"
Write-Host "Token stored only in the local runtime config file, not in source."

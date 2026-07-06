param(
    [string]$EnvRoot = $(if ([string]::IsNullOrWhiteSpace($env:CYBERFUSION_ENV_ROOT)) { "D:\CyberFusion\Environment\cyberfusion-platform" } else { $env:CYBERFUSION_ENV_ROOT }),
    [string]$EvidenceRoot = "",
    [string]$BaseUrl = $(if ([string]::IsNullOrWhiteSpace($env:CYBERFUSION_FRONTEND_URL)) { "http://127.0.0.1:5174" } else { $env:CYBERFUSION_FRONTEND_URL }),
    [string]$ApiBaseUrl = $(if ([string]::IsNullOrWhiteSpace($env:CYBERFUSION_API_BASE)) { "http://127.0.0.1:18080/api" } else { $env:CYBERFUSION_API_BASE }),
    [switch]$SkipStart,
    [switch]$SkipDbInit,
    [switch]$SkipBuild,
    [switch]$OpenBrowser
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path (Join-Path $ScriptDir "..\..")
$StepResults = New-Object System.Collections.Generic.List[object]

function Assert-DDrivePath {
    param(
        [string]$Label,
        [string]$PathValue
    )
    if ([string]::IsNullOrWhiteSpace($PathValue)) {
        throw "$Label must be set to an absolute D: path."
    }
    if ($PathValue -notmatch "^[A-Za-z]:") {
        throw "$Label must use an absolute D: path, not $PathValue."
    }
    if ($PathValue.Substring(0, 1).ToUpperInvariant() -ne "D") {
        throw "$Label must stay on D: under D:\CyberFusion, not $PathValue."
    }
}

function Invoke-EvidenceStep {
    param(
        [string]$Name,
        [scriptblock]$Action
    )
    Write-Host ""
    Write-Host "== $Name =="
    $started = Get-Date
    try {
        $global:LASTEXITCODE = 0
        & $Action
        if ($LASTEXITCODE -ne $null -and $LASTEXITCODE -ne 0) {
            throw "$Name exited with code $LASTEXITCODE"
        }
        $StepResults.Add([ordered]@{
            name = $Name
            status = "PASS"
            startedAt = $started.ToString("o")
            finishedAt = (Get-Date).ToString("o")
        })
    } catch {
        $StepResults.Add([ordered]@{
            name = $Name
            status = "FAIL"
            startedAt = $started.ToString("o")
            finishedAt = (Get-Date).ToString("o")
            error = $_.Exception.Message
        })
        throw
    }
}

function Get-CommandOutput {
    param(
        [string]$Command,
        [string[]]$Arguments = @()
    )
    if (-not (Get-Command $Command -ErrorAction SilentlyContinue)) {
        return "MISSING: $Command"
    }
    return ((& $Command @Arguments 2>&1) | Out-String).Trim()
}

Assert-DDrivePath -Label "Project root" -PathValue $ProjectRoot.Path
Assert-DDrivePath -Label "Runtime root" -PathValue $EnvRoot
$env:CYBERFUSION_ENV_ROOT = $EnvRoot
$env:CYBERFUSION_FRONTEND_URL = $BaseUrl
$env:CYBERFUSION_API_BASE = $ApiBaseUrl

if ([string]::IsNullOrWhiteSpace($EvidenceRoot)) {
    $EvidenceRoot = Join-Path $EnvRoot "validation"
}
Assert-DDrivePath -Label "Evidence root" -PathValue $EvidenceRoot

$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$EvidenceDir = Join-Path $EvidenceRoot $Timestamp
New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null
$TranscriptPath = Join-Path $EvidenceDir "transcript.txt"
$SummaryPath = Join-Path $EvidenceDir "summary.json"
$Facts = [ordered]@{}
$OverallError = $null

Start-Transcript -Path $TranscriptPath -Force | Out-Null
try {
    Write-Host "CyberFusion Windows no-Docker evidence collection"
    Write-Host "Evidence directory: $EvidenceDir"
    Write-Host "Passwords are not written by this script. Set DB_PASSWORD in the local PowerShell session before running if startup or SQL checks are required."

    $Facts = [ordered]@{
        createdAt = (Get-Date).ToString("o")
        projectRoot = $ProjectRoot.Path
        envRoot = $EnvRoot
        evidenceDir = $EvidenceDir
        baseUrl = $BaseUrl
        apiBaseUrl = $ApiBaseUrl
        computerName = $env:COMPUTERNAME
        userName = $env:USERNAME
        powershellVersion = $PSVersionTable.PSVersion.ToString()
        gitHead = Get-CommandOutput -Command "git" -Arguments @("rev-parse", "--short", "HEAD")
        gitStatus = Get-CommandOutput -Command "git" -Arguments @("status", "--short", "--branch")
        java = Get-CommandOutput -Command "java" -Arguments @("-version")
        maven = Get-CommandOutput -Command "mvn" -Arguments @("-v")
        node = Get-CommandOutput -Command "node" -Arguments @("-v")
        pnpm = Get-CommandOutput -Command "pnpm" -Arguments @("-v")
        mysql = Get-CommandOutput -Command "mysql" -Arguments @("--version")
        mysqldump = Get-CommandOutput -Command "mysqldump" -Arguments @("--version")
    }

    Invoke-EvidenceStep -Name "Environment preflight" -Action {
        & (Join-Path $ScriptDir "check-env.ps1")
    }

    Invoke-EvidenceStep -Name "D drive preparation and service reachability" -Action {
        & (Join-Path $ScriptDir "prepare-d-drive.ps1") -EnvRoot $EnvRoot
    }

    if ($SkipStart) {
        Invoke-EvidenceStep -Name "Post-start verification of already running app" -Action {
            & (Join-Path $ScriptDir "verify-no-docker.ps1") -PostStart -BaseUrl $BaseUrl -ApiBaseUrl $ApiBaseUrl
        }
    } else {
        Invoke-EvidenceStep -Name "One-command no-Docker startup and verification" -Action {
            $StartArgs = @("-EnvRoot", $EnvRoot, "-SkipBrowserOpen")
            if ($SkipDbInit) { $StartArgs += "-SkipDbInit" }
            if ($SkipBuild) { $StartArgs += "-SkipBuild" }
            if ($OpenBrowser) {
                $StartArgs = @("-EnvRoot", $EnvRoot)
                if ($SkipDbInit) { $StartArgs += "-SkipDbInit" }
                if ($SkipBuild) { $StartArgs += "-SkipBuild" }
            }
            & (Join-Path $ScriptDir "start-no-docker.ps1") @StartArgs
        }
    }
} catch {
    $OverallError = $_.Exception.Message
    Write-Host ""
    Write-Host "Evidence collection failed: $OverallError"
} finally {
    $FailedSteps = ($StepResults | Where-Object { $_.status -eq "FAIL" }).Count
    $Summary = [ordered]@{
        status = if ($FailedSteps -eq 0 -and [string]::IsNullOrWhiteSpace($OverallError)) { "PASS" } else { "FAIL" }
        error = $OverallError
        facts = $Facts
        steps = $StepResults
        transcript = $TranscriptPath
    }
    $Summary | ConvertTo-Json -Depth 6 | Set-Content -Path $SummaryPath -Encoding UTF8
    Write-Host ""
    Write-Host "Evidence summary: $SummaryPath"
    Write-Host "Transcript: $TranscriptPath"
    Stop-Transcript | Out-Null
}

if (-not [string]::IsNullOrWhiteSpace($OverallError)) {
    exit 1
}

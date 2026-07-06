param(
    [string]$EnvRoot = $(if ([string]::IsNullOrWhiteSpace($env:CYBERFUSION_ENV_ROOT)) { "D:\CyberFusion\Environment\cyberfusion-platform" } else { $env:CYBERFUSION_ENV_ROOT }),
    [string]$DbHost = $(if ([string]::IsNullOrWhiteSpace($env:DB_HOST)) { "127.0.0.1" } else { $env:DB_HOST }),
    [int]$DbPort = $(if ([string]::IsNullOrWhiteSpace($env:DB_PORT)) { 3306 } else { [int]$env:DB_PORT }),
    [string]$RedisHost = $(if ([string]::IsNullOrWhiteSpace($env:REDIS_HOST)) { "127.0.0.1" } else { $env:REDIS_HOST }),
    [int]$RedisPort = $(if ([string]::IsNullOrWhiteSpace($env:REDIS_PORT)) { 6379 } else { [int]$env:REDIS_PORT }),
    [switch]$SkipServiceCheck
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path (Join-Path $ScriptDir "..\..")
$BaseRoot = "D:\CyberFusion"

function Assert-DDrivePath {
    param(
        [string]$Label,
        [string]$PathValue
    )

    if ($PathValue -notmatch "^[A-Za-z]:") {
        throw "$Label must use an absolute D: path, not $PathValue."
    }
    if ($PathValue -match "^[A-Za-z]:") {
        $Drive = $PathValue.Substring(0, 1).ToUpperInvariant()
        if ($Drive -ne "D") {
            throw "$Label must be on D: under D:\CyberFusion, not $PathValue."
        }
    }
}

function Test-TcpPort {
    param(
        [string]$HostName,
        [int]$Port,
        [int]$TimeoutMs = 1500
    )

    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $connect = $client.BeginConnect($HostName, $Port, $null, $null)
        if (-not $connect.AsyncWaitHandle.WaitOne($TimeoutMs, $false)) {
            return $false
        }
        $client.EndConnect($connect)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Assert-ServicePort {
    param(
        [string]$Name,
        [string]$HostName,
        [int]$Port
    )

    if (-not (Test-TcpPort -HostName $HostName -Port $Port)) {
        throw "$Name is not reachable at $HostName`:$Port. Start the Windows service first; this project does not start Docker or local databases for you."
    }
    Write-Host "OK $Name reachable at $HostName`:$Port"
}

Assert-DDrivePath -Label "Project root" -PathValue $ProjectRoot.Path
Assert-DDrivePath -Label "Runtime root" -PathValue $EnvRoot

if (-not (Test-Path "D:\")) {
    throw "D: drive was not found. Create or attach a D: drive before running CyberFusion on Windows."
}

$RequiredDirs = @(
    $BaseRoot,
    $EnvRoot,
    (Join-Path $EnvRoot "uploads"),
    (Join-Path $EnvRoot "logs\backend"),
    (Join-Path $EnvRoot "backups\runtime"),
    (Join-Path $EnvRoot "local-vm"),
    (Join-Path $EnvRoot "caches\maven-repository"),
    (Join-Path $EnvRoot "caches\pnpm-store"),
    (Join-Path $EnvRoot "caches\npm"),
    (Join-Path $EnvRoot "tmp"),
    (Join-Path $EnvRoot "packages"),
    (Join-Path $EnvRoot "package-staging")
)

foreach ($Dir in $RequiredDirs) {
    New-Item -ItemType Directory -Force -Path $Dir | Out-Null
    Write-Host "OK directory: $Dir"
}

if (-not $SkipServiceCheck) {
    Assert-ServicePort -Name "MySQL" -HostName $DbHost -Port $DbPort
    Assert-ServicePort -Name "Redis" -HostName $RedisHost -Port $RedisPort
} else {
    Write-Host "Service port checks skipped."
}

Write-Host ""
Write-Host "Set this in the current PowerShell session before startup:"
Write-Host "  `$env:CYBERFUSION_ENV_ROOT = `"$EnvRoot`""
Write-Host "D drive preparation finished."

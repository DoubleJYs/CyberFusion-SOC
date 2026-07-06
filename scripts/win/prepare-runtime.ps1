param(
    [string]$EnvRoot = "",
    [string]$DbHost = $(if ([string]::IsNullOrWhiteSpace($env:DB_HOST)) { "127.0.0.1" } else { $env:DB_HOST }),
    [int]$DbPort = $(if ([string]::IsNullOrWhiteSpace($env:DB_PORT)) { 3306 } else { [int]$env:DB_PORT }),
    [string]$RedisHost = $(if ([string]::IsNullOrWhiteSpace($env:REDIS_HOST)) { "127.0.0.1" } else { $env:REDIS_HOST }),
    [int]$RedisPort = $(if ([string]::IsNullOrWhiteSpace($env:REDIS_PORT)) { 6379 } else { [int]$env:REDIS_PORT }),
    [switch]$SkipServiceCheck
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptDir "runtime-paths.ps1")

$ProjectRoot = (Resolve-Path (Join-Path $ScriptDir "..\..")).Path
$ResolvedEnvRoot = Resolve-CyberFusionEnvRoot -ProjectRoot $ProjectRoot -EnvRoot $EnvRoot
Set-CyberFusionRuntimeEnvironment -ProjectRoot $ProjectRoot -EnvRoot $ResolvedEnvRoot
Initialize-CyberFusionRuntimeDirectories -EnvRoot $ResolvedEnvRoot

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

if (-not $SkipServiceCheck) {
    Assert-ServicePort -Name "MySQL" -HostName $DbHost -Port $DbPort
    Assert-ServicePort -Name "Redis" -HostName $RedisHost -Port $RedisPort
} else {
    Write-Host "Service port checks skipped."
}

Write-Host ""
Write-Host "Set this in the current PowerShell session before startup:"
Write-Host "  `$env:CYBERFUSION_ENV_ROOT = `"$ResolvedEnvRoot`""
Write-Host "Runtime preparation finished."

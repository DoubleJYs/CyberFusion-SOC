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

Write-Host "prepare-d-drive.ps1 is kept for compatibility. Use prepare-runtime.ps1 for new Windows setups."

$ArgsForPrepare = @(
    "-EnvRoot", $EnvRoot,
    "-DbHost", $DbHost,
    "-DbPort", "$DbPort",
    "-RedisHost", $RedisHost,
    "-RedisPort", "$RedisPort"
)
if ($SkipServiceCheck) {
    $ArgsForPrepare += "-SkipServiceCheck"
}

& (Join-Path $ScriptDir "prepare-runtime.ps1") @ArgsForPrepare

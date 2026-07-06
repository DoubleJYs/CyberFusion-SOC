param(
    [switch]$SkipDatabaseClient
)

$ErrorActionPreference = "Continue"
$Failed = $false

function Invoke-EnvCheck {
    param(
        [string]$Name,
        [string]$Command,
        [string[]]$Arguments = @()
    )

    Write-Host ""
    Write-Host "[$Name]"

    if (-not (Get-Command $Command -ErrorAction SilentlyContinue)) {
        Write-Host "MISSING: $Command"
        $script:Failed = $true
        return
    }

    & $Command @Arguments
    if ($LASTEXITCODE -ne $null -and $LASTEXITCODE -ne 0) {
        $script:Failed = $true
    }
}

function Invoke-VersionCheck {
    param(
        [string]$Name,
        [string]$Command,
        [string[]]$Arguments,
        [int]$MinMajor,
        [int]$MinMinor = 0
    )

    Write-Host ""
    Write-Host "[$Name]"

    if (-not (Get-Command $Command -ErrorAction SilentlyContinue)) {
        Write-Host "MISSING: $Command"
        $script:Failed = $true
        return
    }

    $output = & $Command @Arguments 2>&1
    $exitCode = $LASTEXITCODE
    $text = ($output | Out-String).Trim()
    if (-not [string]::IsNullOrWhiteSpace($text)) {
        Write-Host $text
    }
    if ($exitCode -ne $null -and $exitCode -ne 0) {
        $script:Failed = $true
        return
    }

    $match = [regex]::Match($text, "(\d+)(?:\.(\d+))?")
    if (-not $match.Success) {
        Write-Host "FAILED: could not parse $Name version."
        $script:Failed = $true
        return
    }

    $major = [int]$match.Groups[1].Value
    $minor = if ($match.Groups[2].Success) { [int]$match.Groups[2].Value } else { 0 }
    if ($major -lt $MinMajor -or ($major -eq $MinMajor -and $minor -lt $MinMinor)) {
        Write-Host "FAILED: $Name must be >= $MinMajor.$MinMinor, found $major.$minor."
        $script:Failed = $true
        return
    }

    Write-Host "OK: $Name version $major.$minor meets >= $MinMajor.$MinMinor"
}

Invoke-VersionCheck -Name "Java" -Command "java" -Arguments @("-version") -MinMajor 21
Invoke-VersionCheck -Name "Maven" -Command "mvn" -Arguments @("-v") -MinMajor 3 -MinMinor 9
Invoke-VersionCheck -Name "Node.js" -Command "node" -Arguments @("-v") -MinMajor 20
Invoke-VersionCheck -Name "pnpm" -Command "pnpm" -Arguments @("-v") -MinMajor 11

if (-not $SkipDatabaseClient) {
    Invoke-EnvCheck -Name "MySQL Client" -Command "mysql" -Arguments @("--version")
    Invoke-EnvCheck -Name "MySQL Dump" -Command "mysqldump" -Arguments @("--version")
}

if ($Failed) {
    exit 1
}

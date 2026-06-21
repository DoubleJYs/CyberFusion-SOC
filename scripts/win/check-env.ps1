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

Invoke-EnvCheck -Name "Java" -Command "java" -Arguments @("-version")
Invoke-EnvCheck -Name "Maven" -Command "mvn" -Arguments @("-v")
Invoke-EnvCheck -Name "Node.js" -Command "node" -Arguments @("-v")
Invoke-EnvCheck -Name "pnpm" -Command "pnpm" -Arguments @("-v")
Invoke-EnvCheck -Name "Docker" -Command "docker" -Arguments @("--version")
Invoke-EnvCheck -Name "Docker Compose" -Command "docker" -Arguments @("compose", "version")

if ($Failed) {
    exit 1
}

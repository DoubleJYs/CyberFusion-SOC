$ErrorActionPreference = "Continue"
$Failed = $false

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path (Join-Path $ScriptDir "..\..")

function Invoke-CompatCheck {
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

function Test-ProjectFile {
    param(
        [string]$Name,
        [string]$RelativePath
    )

    Write-Host ""
    Write-Host "[$Name]"
    $Path = Join-Path $ProjectRoot $RelativePath
    if (Test-Path $Path -PathType Leaf) {
        Write-Host "OK: $Path"
    } else {
        Write-Host "MISSING: $Path"
        $script:Failed = $true
    }
}

Invoke-CompatCheck -Name "Java runtime" -Command "java" -Arguments @("-version")
Invoke-CompatCheck -Name "Node.js" -Command "node" -Arguments @("-v")
Invoke-CompatCheck -Name "pnpm" -Command "pnpm" -Arguments @("-v")
Invoke-CompatCheck -Name "Hostname observation" -Command "cmd.exe" -Arguments @("/c", "hostname")
Invoke-CompatCheck -Name "Identity observation" -Command "cmd.exe" -Arguments @("/c", "whoami /groups")
Invoke-CompatCheck -Name "Network observation" -Command "cmd.exe" -Arguments @("/c", "netstat -ano")
Invoke-CompatCheck -Name "Process observation" -Command "cmd.exe" -Arguments @("/c", "tasklist /fo table")
Invoke-CompatCheck -Name "Startup observation" -Command "cmd.exe" -Arguments @("/c", "reg query HKCU\Software\Microsoft\Windows\CurrentVersion\Run")

Test-ProjectFile -Name "Frontend local VM page" -RelativePath "frontend\src\views\client\ClientLocalRangeView.vue"
Test-ProjectFile -Name "Frontend device context" -RelativePath "frontend\src\composables\useClientDeviceContext.ts"
Test-ProjectFile -Name "Frontend data report page" -RelativePath "frontend\src\views\client\ClientDataReportView.vue"
Test-ProjectFile -Name "Frontend operations page" -RelativePath "frontend\src\views\client\ClientOperationsView.vue"
Test-ProjectFile -Name "Frontend client router" -RelativePath "frontend\src\router\index.ts"
Test-ProjectFile -Name "Frontend client layout" -RelativePath "frontend\src\layouts\ClientLayout.vue"
Test-ProjectFile -Name "Frontend runtime compatibility" -RelativePath "frontend\src\composables\useClientRuntimeCompatibility.ts"
Test-ProjectFile -Name "Backend runtime compatibility API" -RelativePath "backend\src\main\java\com\zhangjiyan\template\soc\client\ClientRuntimeController.java"
Test-ProjectFile -Name "Backend runtime compatibility test" -RelativePath "backend\src\test\java\com\zhangjiyan\template\soc\client\ClientRuntimeControllerTest.java"
Test-ProjectFile -Name "Backend SOC service" -RelativePath "backend\src\main\java\com\zhangjiyan\template\soc\SocOperationService.java"
Test-ProjectFile -Name "Environment template" -RelativePath ".env.example"
Test-ProjectFile -Name "macOS one-click dev startup" -RelativePath "scripts\mac\run-dev.sh"
Test-ProjectFile -Name "Windows one-click dev startup" -RelativePath "scripts\win\run-dev.ps1"

Write-Host ""
Write-Host "[Runtime boundary]"
if ([string]::IsNullOrWhiteSpace($env:CYBERFUSION_ENV_ROOT)) {
    Write-Host "Use an Environment runtime root outside the source tree, for example C:\CyberFusion\Environment\cyberfusion-platform"
} else {
    Write-Host "CYBERFUSION_ENV_ROOT=$env:CYBERFUSION_ENV_ROOT"
}

if ($Failed) {
    exit 1
}

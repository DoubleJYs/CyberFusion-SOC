$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path (Join-Path $ScriptDir "..\..")

if ((Split-Path -Leaf $ProjectRoot) -ne "00-cyberfusion-platform") {
    throw "Refusing to clean: unexpected project root $ProjectRoot"
}

$Required = @(
    "backend\pom.xml",
    "frontend\package.json",
    "frontend\pnpm-lock.yaml",
    ".env.example",
    "deploy\docker-compose.yml"
)

foreach ($Item in $Required) {
    if (-not (Test-Path (Join-Path $ProjectRoot $Item))) {
        throw "Refusing to clean: missing $Item"
    }
}

function Remove-GeneratedPath {
    param([string]$RelativePath)

    $Target = Join-Path $ProjectRoot $RelativePath
    if (Test-Path $Target) {
        Write-Host "Removing $RelativePath"
        Remove-Item -LiteralPath $Target -Recurse -Force
    }
}

Remove-GeneratedPath "backend\target"
Remove-GeneratedPath "frontend\dist"
Remove-GeneratedPath "frontend\test-results"
Remove-GeneratedPath "logs"
Remove-GeneratedPath "tmp"

Get-ChildItem -LiteralPath $ProjectRoot -Recurse -Force -Filter ".DS_Store" -ErrorAction SilentlyContinue |
    ForEach-Object {
        Write-Host "Removing $($_.FullName.Substring($ProjectRoot.Path.Length + 1))"
        Remove-Item -LiteralPath $_.FullName -Force
    }

if ($args -contains "--include-node-modules") {
    Write-Host "frontend\node_modules is the frontend dependency directory and can be restored with pnpm install."
    $Answer = Read-Host "Delete frontend\node_modules? Type yes to continue"
    if ($Answer -eq "yes") {
        Remove-GeneratedPath "frontend\node_modules"
    } else {
        Write-Host "Skipped frontend\node_modules"
    }
} else {
    Write-Host "Skipped frontend\node_modules. Pass --include-node-modules to remove it."
}

Write-Host "Generated cleanup completed."

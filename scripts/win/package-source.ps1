param(
    [string]$OutputZip
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path (Join-Path $ScriptDir "..\..")
$ProjectName = Split-Path -Leaf $ProjectRoot

if ([string]::IsNullOrWhiteSpace($OutputZip)) {
    $OutputZip = Join-Path (Split-Path -Parent $ProjectRoot) "$ProjectName-source.zip"
}
$OutputDir = Split-Path -Parent $OutputZip
if (-not [string]::IsNullOrWhiteSpace($OutputDir) -and -not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir | Out-Null
}

$Required = @(
    "backend\pom.xml",
    "frontend\package.json",
    "frontend\pnpm-lock.yaml",
    "sql\schema.sql",
    "sql\data.sql",
    "deploy\docker-compose.yml",
    "scripts\win\run-dev.ps1",
    "scripts\win\init-local-db.ps1",
    "scripts\win\verify-no-docker.ps1",
    "docs\windows-no-docker.md",
    ".env.example",
    "README.md"
)

foreach ($Item in $Required) {
    if (-not (Test-Path (Join-Path $ProjectRoot $Item))) {
        throw "Missing required source asset: $Item"
    }
}

$StageRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("cyberfusion-platform-source-" + [System.Guid]::NewGuid().ToString("N"))
$StageProject = Join-Path $StageRoot $ProjectName

New-Item -ItemType Directory -Path $StageProject | Out-Null

$RobocopyArgs = @(
    $ProjectRoot,
    $StageProject,
    "/E",
    "/XD",
    ".git",
    ".idea",
    ".vscode",
    "target",
    "node_modules",
    "dist",
    "coverage",
    "test-results",
    "playwright-report",
    ".cache",
    ".vite",
    "mysql-data",
    "postgres-data",
    "redis-data",
    "mongo-data",
    "volumes",
    "docker-volume",
    "docker-volumes",
    "backend\target",
    "frontend\node_modules",
    "frontend\dist",
    "frontend\coverage",
    "frontend\test-results",
    "frontend\playwright-report",
    "coverage",
    "playwright-report",
    "outputs",
    "logs",
    "tmp",
    "/XF",
    ".env",
    ".env.*",
    ".env.local",
    ".env.development",
    ".env.production",
    "*.log",
    "*.tmp",
    "*.temp",
    "*.sqlite",
    "*.sqlite3",
    "*.db",
    "*.ibd",
    "*.frm",
    ".DS_Store",
    "Thumbs.db"
)

robocopy @RobocopyArgs | Out-Host
if ($LASTEXITCODE -gt 7) {
    throw "robocopy failed with exit code $LASTEXITCODE"
}

Copy-Item -LiteralPath (Join-Path $ProjectRoot ".env.example") -Destination (Join-Path $StageProject ".env.example") -Force

$GeneratedDirs = @(
    "backend\target",
    "frontend\node_modules",
    "frontend\dist",
    "frontend\coverage",
    "frontend\test-results",
    "frontend\playwright-report",
    "coverage",
    "playwright-report",
    "outputs",
    "logs",
    "tmp"
)

foreach ($Dir in $GeneratedDirs) {
    $Target = Join-Path $StageProject $Dir
    if (Test-Path $Target) {
        Remove-Item -LiteralPath $Target -Recurse -Force
    }
}

Get-ChildItem -LiteralPath $StageProject -Recurse -Force -Filter ".DS_Store" -ErrorAction SilentlyContinue |
    Remove-Item -Force
Get-ChildItem -LiteralPath $StageProject -Recurse -Force -Filter "*.log" -ErrorAction SilentlyContinue |
    Remove-Item -Force

if (Test-Path $OutputZip) {
    Remove-Item -LiteralPath $OutputZip -Force
}

Compress-Archive -Path $StageProject -DestinationPath $OutputZip -Force
Remove-Item -LiteralPath $StageRoot -Recurse -Force

Write-Host "Created source package: $OutputZip"
Write-Host "Windows quick start after unzip to D:\CyberFusion\00-cyberfusion-platform:"
Write-Host "  powershell -ExecutionPolicy Bypass -File scripts\win\run-dev.ps1"

param(
    [string]$OutputZip,
    [string]$EnvRoot = ""
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptDir "runtime-paths.ps1")

$ProjectRoot = (Resolve-Path (Join-Path $ScriptDir "..\..")).Path
$ProjectName = Split-Path -Leaf $ProjectRoot
$EnvRoot = Resolve-CyberFusionEnvRoot -ProjectRoot $ProjectRoot -EnvRoot $EnvRoot
Assert-CyberFusionRuntimePath -Label "Environment root" -PathValue $EnvRoot -ProjectRoot $ProjectRoot

if ([string]::IsNullOrWhiteSpace($OutputZip)) {
    $OutputZip = Join-Path (Join-Path $EnvRoot "packages") "$ProjectName-source.zip"
}
Assert-CyberFusionRuntimePath -Label "Output zip" -PathValue $OutputZip -ProjectRoot $ProjectRoot
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
    "scripts\win\start-no-docker.ps1",
    "scripts\win\run-dev.ps1",
    "scripts\win\init-local-db.ps1",
    "scripts\win\prepare-runtime.ps1",
    "scripts\win\runtime-paths.ps1",
    "scripts\win\verify-no-docker.ps1",
    "scripts\win\collect-windows-evidence.ps1",
    "docs\windows-no-docker.md",
    ".env.example",
    "README.md"
)

foreach ($Item in $Required) {
    if (-not (Test-Path (Join-Path $ProjectRoot $Item))) {
        throw "Missing required source asset: $Item"
    }
}

$StageParent = Join-Path $EnvRoot "package-staging"
New-Item -ItemType Directory -Path $StageParent -Force | Out-Null
$StageRoot = Join-Path $StageParent ("cyberfusion-platform-source-" + [System.Guid]::NewGuid().ToString("N"))
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
    "packages",
    "package-staging",
    "validation",
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
    "tmp",
    "packages",
    "package-staging",
    "validation"
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
Write-Host "Windows quick start after unzip to your chosen project folder:"
Write-Host "  powershell -ExecutionPolicy Bypass -File scripts\win\start-no-docker.ps1"
Write-Host "Windows evidence collection after setting DB_PASSWORD:"
Write-Host "  powershell -ExecutionPolicy Bypass -File scripts\win\collect-windows-evidence.ps1"
Write-Host "Manual phased startup:"
Write-Host "  powershell -ExecutionPolicy Bypass -File scripts\win\prepare-runtime.ps1"
Write-Host "  powershell -ExecutionPolicy Bypass -File scripts\win\verify-no-docker.ps1 -PreStart"
Write-Host "  powershell -ExecutionPolicy Bypass -File scripts\win\run-dev.ps1"
Write-Host "  powershell -ExecutionPolicy Bypass -File scripts\win\verify-no-docker.ps1 -PostStart"

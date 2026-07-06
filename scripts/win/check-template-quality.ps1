$ErrorActionPreference = "Continue"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path (Join-Path $ScriptDir "..\..")
$Failed = $false

if ((Split-Path -Leaf $ProjectRoot) -ne "00-cyberfusion-platform") {
    Write-Host "FAIL unexpected project root: $ProjectRoot"
    exit 1
}

function Test-RequiredPath {
    param([string]$RelativePath)

    if (Test-Path (Join-Path $ProjectRoot $RelativePath)) {
        Write-Host "OK exists: $RelativePath"
    } else {
        Write-Host "FAIL missing: $RelativePath"
        $script:Failed = $true
    }
}

function Test-AbsentPath {
    param([string]$RelativePath)

    if (Test-Path (Join-Path $ProjectRoot $RelativePath)) {
        Write-Host "FAIL generated/local artifact exists: $RelativePath"
        $script:Failed = $true
    } else {
        Write-Host "OK absent: $RelativePath"
    }
}

Test-RequiredPath "backend\pom.xml"
Test-RequiredPath "frontend\package.json"
Test-RequiredPath "frontend\pnpm-lock.yaml"
Test-RequiredPath "sql\schema.sql"
Test-RequiredPath "sql\data.sql"
Test-RequiredPath "deploy\docker-compose.yml"
Test-RequiredPath "docs\windows-no-docker.md"
Test-RequiredPath "scripts\win\run-dev.ps1"
Test-RequiredPath "scripts\win\start-no-docker.ps1"
Test-RequiredPath "scripts\win\init-local-db.ps1"
Test-RequiredPath "scripts\win\prepare-d-drive.ps1"
Test-RequiredPath "scripts\win\verify-no-docker.ps1"
Test-RequiredPath "scripts\win\collect-windows-evidence.ps1"
Test-RequiredPath "README.md"
Test-RequiredPath ".env.example"
Test-RequiredPath ".gitignore"
Test-RequiredPath ".gitattributes"

Test-AbsentPath "backend\target"
Test-AbsentPath "frontend\node_modules"
Test-AbsentPath "frontend\dist"
Test-AbsentPath "frontend\test-results"
Test-AbsentPath "coverage"
Test-AbsentPath "frontend\coverage"
Test-AbsentPath "outputs"
Test-AbsentPath "logs"
Test-AbsentPath "tmp"
Test-AbsentPath "packages"
Test-AbsentPath "package-staging"
Test-AbsentPath "validation"
Test-AbsentPath ".env"
Test-AbsentPath ".DS_Store"
Test-AbsentPath "frontend\.DS_Store"

$DsStore = Get-ChildItem -LiteralPath $ProjectRoot -Recurse -Force -Filter ".DS_Store" -ErrorAction SilentlyContinue
if ($DsStore) {
    Write-Host "FAIL .DS_Store found under project"
    $Failed = $true
} else {
    Write-Host "OK no .DS_Store found under project"
}

$ExcludeDirs = "\\(node_modules|target|dist|\.git|test-results|outputs|logs|tmp|packages|package-staging|validation)\\"
$Files = Get-ChildItem -LiteralPath $ProjectRoot -Recurse -File -Force -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -notmatch $ExcludeDirs }

$SecretTerms = @(
    "BEGIN .*PRIVATE " + "KEY",
    "ghp_" + "[A-Za-z0-9_]+",
    "sk-" + "[A-Za-z0-9]+",
    "AKIA" + "[0-9A-Z]{16}",
    "xoxb-" + "[A-Za-z0-9-]+"
)
$SecretPattern = $SecretTerms -join "|"
$ComplianceTerms = @("代" + "做", "代" + "写", "包" + "过", "替" + "交", "保证" + "通过", "不用学也能" + "交", "论文" + "代" + "写")
$CompliancePattern = $ComplianceTerms -join "|"

$SecretHits = $Files | Select-String -Pattern $SecretPattern -ErrorAction SilentlyContinue
if ($SecretHits) {
    Write-Host "FAIL high-risk secret pattern found"
    $SecretHits
    $Failed = $true
} else {
    Write-Host "OK no high-risk secret pattern found"
}

$ComplianceHits = $Files | Select-String -Pattern $CompliancePattern -ErrorAction SilentlyContinue
if ($ComplianceHits) {
    Write-Host "FAIL prohibited compliance text found"
    $ComplianceHits
    $Failed = $true
} else {
    Write-Host "OK no prohibited compliance text found"
}

if ($Failed) {
    exit 1
}

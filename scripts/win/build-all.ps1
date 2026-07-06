$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path (Join-Path $ScriptDir "..\..")
$DefaultEnvRoot = "D:\CyberFusion\Environment\cyberfusion-platform"
$EnvRoot = if ([string]::IsNullOrWhiteSpace($env:CYBERFUSION_ENV_ROOT)) { $DefaultEnvRoot } else { $env:CYBERFUSION_ENV_ROOT }

if ($ProjectRoot.Path -match "^[A-Za-z]:") {
    $ProjectDrive = $ProjectRoot.Path.Substring(0, 1).ToUpperInvariant()
    if ($ProjectDrive -ne "D") {
        throw "Windows no-Docker mode requires the project under D:\CyberFusion, not $($ProjectRoot.Path). Move 00-cyberfusion-platform to D: before building."
    }
}

function Assert-DDrivePath {
    param(
        [string]$Label,
        [string]$PathValue
    )
    if ($PathValue -notmatch "^[A-Za-z]:") {
        throw "$Label must use an absolute D: path, not $PathValue."
    }
    $Drive = $PathValue.Substring(0, 1).ToUpperInvariant()
    if ($Drive -ne "D") {
        throw "$Label must stay on D: under D:\CyberFusion, not $PathValue."
    }
}

Assert-DDrivePath -Label "Runtime root" -PathValue $EnvRoot
$env:CYBERFUSION_ENV_ROOT = $EnvRoot
$env:MAVEN_REPO_LOCAL = if ([string]::IsNullOrWhiteSpace($env:MAVEN_REPO_LOCAL)) { Join-Path $EnvRoot "caches\maven-repository" } else { $env:MAVEN_REPO_LOCAL }
$env:PNPM_STORE_DIR = if ([string]::IsNullOrWhiteSpace($env:PNPM_STORE_DIR)) { Join-Path $EnvRoot "caches\pnpm-store" } else { $env:PNPM_STORE_DIR }
$env:npm_config_cache = if ([string]::IsNullOrWhiteSpace($env:npm_config_cache)) { Join-Path $EnvRoot "caches\npm" } else { $env:npm_config_cache }
$env:CYBERFUSION_TEMP_DIR = if ([string]::IsNullOrWhiteSpace($env:CYBERFUSION_TEMP_DIR)) { Join-Path $EnvRoot "tmp" } else { $env:CYBERFUSION_TEMP_DIR }
Assert-DDrivePath -Label "Maven repository" -PathValue $env:MAVEN_REPO_LOCAL
Assert-DDrivePath -Label "pnpm store" -PathValue $env:PNPM_STORE_DIR
Assert-DDrivePath -Label "npm cache" -PathValue $env:npm_config_cache
Assert-DDrivePath -Label "Temp directory" -PathValue $env:CYBERFUSION_TEMP_DIR
New-Item -ItemType Directory -Force -Path $env:MAVEN_REPO_LOCAL, $env:PNPM_STORE_DIR, $env:npm_config_cache, $env:CYBERFUSION_TEMP_DIR | Out-Null
$env:TEMP = $env:CYBERFUSION_TEMP_DIR
$env:TMP = $env:CYBERFUSION_TEMP_DIR
if ([string]::IsNullOrWhiteSpace($env:JAVA_TOOL_OPTIONS)) {
    $env:JAVA_TOOL_OPTIONS = "-Djava.io.tmpdir=$env:CYBERFUSION_TEMP_DIR"
} elseif ($env:JAVA_TOOL_OPTIONS -notmatch "java\.io\.tmpdir") {
    $env:JAVA_TOOL_OPTIONS = "$env:JAVA_TOOL_OPTIONS -Djava.io.tmpdir=$env:CYBERFUSION_TEMP_DIR"
}

Set-Location (Join-Path $ProjectRoot "backend")
mvn "-Dmaven.repo.local=$env:MAVEN_REPO_LOCAL" -q -DskipTests package

Set-Location (Join-Path $ProjectRoot "frontend")
pnpm install --frozen-lockfile --store-dir "$env:PNPM_STORE_DIR"
pnpm build

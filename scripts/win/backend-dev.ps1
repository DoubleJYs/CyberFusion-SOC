$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path (Join-Path $ScriptDir "..\..")
$DefaultEnvRoot = "D:\CyberFusion\Environment\cyberfusion-platform"
$EnvRoot = if ([string]::IsNullOrWhiteSpace($env:CYBERFUSION_ENV_ROOT)) { $DefaultEnvRoot } else { $env:CYBERFUSION_ENV_ROOT }

if ($ProjectRoot.Path -match "^[A-Za-z]:") {
    $ProjectDrive = $ProjectRoot.Path.Substring(0, 1).ToUpperInvariant()
    if ($ProjectDrive -ne "D") {
        throw "Windows no-Docker mode requires the project under D:\CyberFusion, not $($ProjectRoot.Path). Move 00-cyberfusion-platform to D: before starting."
    }
}

if ($EnvRoot -notmatch "^[A-Za-z]:") {
    throw "Windows no-Docker runtime data must use an absolute D: path, not $EnvRoot."
}
$EnvDrive = $EnvRoot.Substring(0, 1).ToUpperInvariant()
if ($EnvDrive -ne "D") {
    throw "Windows no-Docker runtime data must stay on D: under D:\CyberFusion, not $EnvRoot."
}
$DriveRoot = "$($EnvRoot.Substring(0, 2))\"
if (-not (Test-Path $DriveRoot)) {
    throw "Required Windows drive not found: $DriveRoot. Put CyberFusion on D: before starting."
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

$env:CYBERFUSION_ENV_ROOT = $EnvRoot
$env:APP_UPLOAD_BASE_DIR = if ([string]::IsNullOrWhiteSpace($env:APP_UPLOAD_BASE_DIR)) { Join-Path $EnvRoot "uploads" } else { $env:APP_UPLOAD_BASE_DIR }
$env:LOGGING_FILE_PATH = if ([string]::IsNullOrWhiteSpace($env:LOGGING_FILE_PATH)) { Join-Path $EnvRoot "logs\backend" } else { $env:LOGGING_FILE_PATH }
$env:MAVEN_REPO_LOCAL = if ([string]::IsNullOrWhiteSpace($env:MAVEN_REPO_LOCAL)) { Join-Path $EnvRoot "caches\maven-repository" } else { $env:MAVEN_REPO_LOCAL }
$env:CYBERFUSION_TEMP_DIR = if ([string]::IsNullOrWhiteSpace($env:CYBERFUSION_TEMP_DIR)) { Join-Path $EnvRoot "tmp" } else { $env:CYBERFUSION_TEMP_DIR }
$env:SERVER_PORT = if ([string]::IsNullOrWhiteSpace($env:SERVER_PORT)) { "18080" } else { $env:SERVER_PORT }
Assert-DDrivePath -Label "Upload directory" -PathValue $env:APP_UPLOAD_BASE_DIR
Assert-DDrivePath -Label "Log directory" -PathValue $env:LOGGING_FILE_PATH
Assert-DDrivePath -Label "Maven repository" -PathValue $env:MAVEN_REPO_LOCAL
Assert-DDrivePath -Label "Temp directory" -PathValue $env:CYBERFUSION_TEMP_DIR
New-Item -ItemType Directory -Force -Path $env:APP_UPLOAD_BASE_DIR, $env:LOGGING_FILE_PATH, $env:MAVEN_REPO_LOCAL, $env:CYBERFUSION_TEMP_DIR | Out-Null
$env:TEMP = $env:CYBERFUSION_TEMP_DIR
$env:TMP = $env:CYBERFUSION_TEMP_DIR
if ([string]::IsNullOrWhiteSpace($env:JAVA_TOOL_OPTIONS)) {
    $env:JAVA_TOOL_OPTIONS = "-Djava.io.tmpdir=$env:CYBERFUSION_TEMP_DIR"
} elseif ($env:JAVA_TOOL_OPTIONS -notmatch "java\.io\.tmpdir") {
    $env:JAVA_TOOL_OPTIONS = "$env:JAVA_TOOL_OPTIONS -Djava.io.tmpdir=$env:CYBERFUSION_TEMP_DIR"
}

Set-Location (Join-Path $ProjectRoot "backend")
mvn "-Dmaven.repo.local=$env:MAVEN_REPO_LOCAL" spring-boot:run

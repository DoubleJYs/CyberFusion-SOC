$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptDir "runtime-paths.ps1")

$ProjectRoot = (Resolve-Path (Join-Path $ScriptDir "..\..")).Path
$EnvRoot = Resolve-CyberFusionEnvRoot -ProjectRoot $ProjectRoot
Set-CyberFusionRuntimeEnvironment -ProjectRoot $ProjectRoot -EnvRoot $EnvRoot
Initialize-CyberFusionRuntimeDirectories -EnvRoot $EnvRoot
$env:SERVER_PORT = if ([string]::IsNullOrWhiteSpace($env:SERVER_PORT)) { "18080" } else { $env:SERVER_PORT }

Set-Location (Join-Path $ProjectRoot "backend")
mvn "-Dmaven.repo.local=$env:MAVEN_REPO_LOCAL" spring-boot:run

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptDir "runtime-paths.ps1")

$ProjectRoot = (Resolve-Path (Join-Path $ScriptDir "..\..")).Path
$EnvRoot = Resolve-CyberFusionEnvRoot -ProjectRoot $ProjectRoot
Set-CyberFusionRuntimeEnvironment -ProjectRoot $ProjectRoot -EnvRoot $EnvRoot
Initialize-CyberFusionRuntimeDirectories -EnvRoot $EnvRoot

Set-Location (Join-Path $ProjectRoot "backend")
mvn "-Dmaven.repo.local=$env:MAVEN_REPO_LOCAL" -q -DskipTests package

Set-Location (Join-Path $ProjectRoot "frontend")
pnpm install --frozen-lockfile --store-dir "$env:PNPM_STORE_DIR"
pnpm build

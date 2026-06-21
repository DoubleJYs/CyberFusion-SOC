$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path (Join-Path $ScriptDir "..\..")

Set-Location (Join-Path $ProjectRoot "backend")
mvn -q -DskipTests package

Set-Location (Join-Path $ProjectRoot "frontend")
pnpm install
pnpm build

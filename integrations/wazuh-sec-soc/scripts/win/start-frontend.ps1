$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$FrontendScript = Join-Path $ScriptDir "frontend-dev.ps1"

& $FrontendScript

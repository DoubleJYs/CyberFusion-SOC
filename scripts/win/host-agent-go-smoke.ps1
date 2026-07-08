param(
    [string]$ApiBaseUrl = $(if ($env:CYBERFUSION_API_BASE) { $env:CYBERFUSION_API_BASE } else { "http://127.0.0.1:18080/api" }),
    [string]$AgentId = $(if ($env:CYBERFUSION_AGENT_ID) { $env:CYBERFUSION_AGENT_ID } else { "windows-dev-agent" }),
    [string]$AgentToken = $env:CYBERFUSION_AGENT_TOKEN,
    [switch]$Upload
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$AgentRoot = Join-Path $ProjectRoot "agent"
if (-not $env:GOCACHE) {
    $env:GOCACHE = Join-Path $env:TEMP "cyberfusion-agent-go-build"
}

Push-Location $AgentRoot
try {
    go test ./...
    go run ./cmd/cyberfusion-agent --mode fixture --fixture-os all --dry-run | Out-Null
    Write-Host "[PASS] Go agent builds and renders macOS/Windows fixtures"

    if ($Upload) {
        if (-not $AgentToken) {
            throw "CYBERFUSION_AGENT_TOKEN or -AgentToken is required when -Upload is set"
        }
        $RuntimeDir = Join-Path $env:TEMP ("cyberfusion-agent-" + $AgentId)
        go run ./cmd/cyberfusion-agent `
            --mode fixture `
            --os-type windows `
            --agent-id $AgentId `
            --runtime-dir $RuntimeDir `
            --api-base-url $ApiBaseUrl
        Write-Host "[PASS] Windows fixture uploaded through Go agent"
    } else {
        Write-Host "[INFO] Upload skipped. Pass -Upload with CYBERFUSION_AGENT_TOKEN to send data to the platform."
    }
}
finally {
    Pop-Location
}

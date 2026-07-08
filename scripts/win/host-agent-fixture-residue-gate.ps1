param(
    [string]$ApiBaseUrl = $(if ($env:CYBERFUSION_API_BASE) { $env:CYBERFUSION_API_BASE } else { "http://127.0.0.1:18080/api" }),
    [string]$AdminUser = $(if ($env:CYBERFUSION_ADMIN_USER) { $env:CYBERFUSION_ADMIN_USER } else { "admin" }),
    [string]$AdminPassword = $(if ($env:CYBERFUSION_ADMIN_PASSWORD) { $env:CYBERFUSION_ADMIN_PASSWORD } elseif ($env:CYBERFUSION_DEMO_PASSWORD) { $env:CYBERFUSION_DEMO_PASSWORD } else { "Admin@123456" }),
    [switch]$ClearFirst
)

$ErrorActionPreference = "Stop"

function Invoke-CyberFusionApi {
    param(
        [string]$Method,
        [string]$Path,
        [string]$Token = "",
        [object]$Body = $null
    )

    $Headers = @{}
    if (-not [string]::IsNullOrWhiteSpace($Token)) {
        $Headers["Authorization"] = "Bearer $Token"
    }
    $Uri = "$($ApiBaseUrl.TrimEnd('/'))$Path"
    if ($null -ne $Body) {
        $JsonBody = $Body | ConvertTo-Json -Depth 20 -Compress
        return Invoke-RestMethod -Method $Method -Uri $Uri -Headers $Headers -ContentType "application/json" -Body $JsonBody
    }
    return Invoke-RestMethod -Method $Method -Uri $Uri -Headers $Headers
}

function Assert-Success {
    param(
        [object]$Response,
        [string]$Label
    )
    if ($Response.code -ne "SUCCESS") {
        throw "$Label returned code $($Response.code): $($Response.message)"
    }
    Write-Host "[PASS] $Label"
}

function Get-ApiTotal {
    param([object]$Data)
    if ($null -eq $Data) {
        return 0
    }
    if ($Data.PSObject.Properties.Name -contains "total") {
        return [int]$Data.total
    }
    if ($Data -is [System.Array]) {
        return $Data.Count
    }
    if ($Data.PSObject.Properties.Name -contains "records") {
        return @($Data.records).Count
    }
    return 0
}

function Assert-NoKeywordRows {
    param(
        [string]$Label,
        [string]$Path,
        [string]$Token
    )
    $Response = Invoke-CyberFusionApi -Method "GET" -Path $Path -Token $Token
    Assert-Success -Response $Response -Label "$Label lookup"
    $Total = Get-ApiTotal -Data $Response.data
    if ($Total -ne 0) {
        throw "$Label fixture residue is still visible: total=$Total"
    }
    Write-Host "[PASS] $Label has no fixture residue"
}

$Login = Invoke-CyberFusionApi -Method "POST" -Path "/auth/login" -Body @{
    username = $AdminUser
    password = $AdminPassword
}
Assert-Success -Response $Login -Label "admin login"
$Token = $Login.data.accessToken
if ([string]::IsNullOrWhiteSpace($Token)) {
    throw "Admin access token is empty"
}

if ($ClearFirst) {
    $Cleanup = Invoke-CyberFusionApi -Method "DELETE" -Path "/soc/demo-range/demo-data" -Token $Token
    Assert-Success -Response $Cleanup -Label "fixture/demo cleanup"
}

$Status = Invoke-CyberFusionApi -Method "GET" -Path "/soc/demo-range/demo-data/status" -Token $Token
Assert-Success -Response $Status -Label "demo data status"
if ($Status.data.hasDemoData) {
    throw "demo-data status still reports fixture/demo rows"
}
Write-Host "[PASS] demo-data status reports no fixture/demo data"

$AgentsResponse = Invoke-CyberFusionApi -Method "GET" -Path "/soc/agents?pageNum=1&pageSize=500" -Token $Token
Assert-Success -Response $AgentsResponse -Label "agent list"
$AgentRecords = @()
if ($AgentsResponse.data -is [System.Array]) {
    $AgentRecords = @($AgentsResponse.data)
} elseif ($AgentsResponse.data.PSObject.Properties.Name -contains "records") {
    $AgentRecords = @($AgentsResponse.data.records)
}

$FixtureHosts = @("mac-dev-host", "win-docker-host", "mac-incident-host", "win-incident-host")
$BadAgents = @()
foreach ($Item in $AgentRecords) {
    $AgentId = [string]$Item.agentId
    $Hostname = [string]$Item.hostname
    $Blob = $Item | ConvertTo-Json -Depth 20 -Compress
    if (
        $AgentId.StartsWith("incident-") `
        -or $AgentId.Contains("fixture-agent") `
        -or $AgentId.StartsWith("queue-replay-macos-agent-") `
        -or ($FixtureHosts -contains $Hostname) `
        -or $Blob.Contains("192.0.2.") `
        -or $Blob.Contains("198.18.") `
        -or $Blob.Contains("198.19.") `
        -or $Blob.Contains("incident-chain") `
        -or $Blob.Contains("queue-replay") `
        -or $Blob.Contains('"fixture":"true"') `
        -or $Blob.Contains('"fixture": "true"')
    ) {
        $BadAgents += [ordered]@{
            agentId = $AgentId
            hostname = $Hostname
            osType = $Item.osType
        }
    }
}
if ($BadAgents.Count -gt 0) {
    throw "Host Agent fixture records remain: $($BadAgents | ConvertTo-Json -Depth 10 -Compress)"
}
Write-Host "[PASS] agent list has no Host Agent fixture residue"

$Keywords = @(
    "win-incident-host",
    "mac-incident-host",
    "win-docker-host",
    "mac-dev-host",
    "HOST-AGENT-INCIDENT-SMOKE",
    "192.0.2",
    "198.18",
    "198.19"
)

foreach ($Keyword in $Keywords) {
    Assert-NoKeywordRows -Label "incident keyword $Keyword" -Path "/soc/incidents?pageNum=1&pageSize=20&keyword=$Keyword" -Token $Token
    Assert-NoKeywordRows -Label "alert keyword $Keyword" -Path "/soc/alerts?pageNum=1&pageSize=20&keyword=$Keyword" -Token $Token
    Assert-NoKeywordRows -Label "external-event keyword $Keyword" -Path "/soc/external-events?pageNum=1&pageSize=20&keyword=$Keyword" -Token $Token
    Assert-NoKeywordRows -Label "asset keyword $Keyword" -Path "/soc/assets?pageNum=1&pageSize=20&keyword=$Keyword" -Token $Token
}

Write-Host "[SUMMARY] Host Agent fixture residue gate passed"

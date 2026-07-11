param(
  [string]$BaseUrl = $(if ($env:CYBERFUSION_FRONTEND_URL) { $env:CYBERFUSION_FRONTEND_URL } else { "http://127.0.0.1:5174" }),
  [string]$ApiBaseUrl = $(if ($env:CYBERFUSION_API_BASE) { $env:CYBERFUSION_API_BASE } else { "http://127.0.0.1:18080/api" }),
  [string]$DbHost = $(if ($env:DB_HOST) { $env:DB_HOST } else { "127.0.0.1" }),
  [string]$DbPort = $(if ($env:DB_PORT) { $env:DB_PORT } else { "3306" }),
  [string]$DbName = $(if ($env:DB_NAME) { $env:DB_NAME } else { "cyberfusion_soc" }),
  [string]$DbUsername = $(if ($env:DB_USERNAME) { $env:DB_USERNAME } else { "root" }),
  [string]$DbPassword = $env:DB_PASSWORD,
  [string]$AdminUser = $(if ($env:CYBERFUSION_ADMIN_USER) { $env:CYBERFUSION_ADMIN_USER } else { "admin" }),
  [string]$EmployeeUser = $(if ($env:CYBERFUSION_EMPLOYEE_USER) { $env:CYBERFUSION_EMPLOYEE_USER } else { "operator" })
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptDir "runtime-paths.ps1")

$ProjectRoot = (Resolve-Path (Join-Path $ScriptDir "..\..")).Path
$env:CYBERFUSION_ENV_ROOT = Resolve-CyberFusionEnvRoot -ProjectRoot $ProjectRoot -EnvRoot $env:CYBERFUSION_ENV_ROOT
Assert-CyberFusionRuntimePath -Label "CYBERFUSION_ENV_ROOT" -PathValue $env:CYBERFUSION_ENV_ROOT -ProjectRoot $ProjectRoot

$DemoPassword = if ($env:CYBERFUSION_DEMO_PASSWORD) { $env:CYBERFUSION_DEMO_PASSWORD } else { "Admin@123456" }
$AdminPassword = if ($env:CYBERFUSION_ADMIN_PASSWORD) { $env:CYBERFUSION_ADMIN_PASSWORD } else { $DemoPassword }
$EmployeePassword = if ($env:CYBERFUSION_EMPLOYEE_PASSWORD) { $env:CYBERFUSION_EMPLOYEE_PASSWORD } else { $DemoPassword }

$PassCount = 0
$WarnCount = 0
$FailCount = 0

function Pass($Message) {
  $script:PassCount += 1
  Write-Host "[PASS] $Message"
}

function Warn($Message) {
  $script:WarnCount += 1
  Write-Host "[WARN] $Message"
}

function Fail($Message) {
  $script:FailCount += 1
  Write-Host "[FAIL] $Message"
}

function Get-PortFromUrl($Url) {
  $uri = [Uri]$Url
  if ($uri.Port -gt 0) {
    return $uri.Port
  }
  if ($uri.Scheme -eq "https") {
    return 443
  }
  return 80
}

function Check-Port($Label, $Port) {
  $listeners = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
  if ($listeners) {
    $pidValue = $listeners[0].OwningProcess
    $process = Get-Process -Id $pidValue -ErrorAction SilentlyContinue
    Pass "$Label listens on port $Port (pid=$pidValue process=$($process.ProcessName))"
    if ($Label -eq "backend" -and $process) {
      Write-Host "[INFO] backend process startTime=$($process.StartTime) path=$($process.Path)"
    }
  } else {
    Fail "$Label is not listening on port $Port"
  }
}

function Check-Http($Label, $Url) {
  try {
    $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
    if ($response.StatusCode -eq 200) {
      Pass "$Label returned HTTP 200"
    } else {
      Fail "$Label returned HTTP $($response.StatusCode)"
    }
  } catch {
    Fail "$Label request failed: $($_.Exception.Message)"
  }
}

function Invoke-Api($Method, $Path, $Token = "", $Body = $null) {
  $headers = @{}
  if (-not [string]::IsNullOrWhiteSpace($Token)) {
    $headers["Authorization"] = "Bearer $Token"
  }
  $uri = $ApiBaseUrl.TrimEnd("/") + $Path
  try {
    if ($null -eq $Body) {
      $response = Invoke-WebRequest -Uri $uri -Method $Method -Headers $headers -UseBasicParsing -TimeoutSec 8
    } else {
      $headers["Content-Type"] = "application/json"
      $response = Invoke-WebRequest -Uri $uri -Method $Method -Headers $headers -Body $Body -UseBasicParsing -TimeoutSec 8
    }
    return @{ Status = [int]$response.StatusCode; Body = $response.Content }
  } catch {
    $status = 0
    $content = ""
    if ($_.Exception.Response) {
      $status = [int]$_.Exception.Response.StatusCode
      try {
        $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
        $content = $reader.ReadToEnd()
      } catch {
        $content = $_.Exception.Message
      }
    }
    return @{ Status = $status; Body = $content; Error = $_.Exception.Message }
  }
}

function Login-User($Username, $Password) {
  $candidates = @($Password)
  if ($Username -eq $AdminUser -and -not $env:CYBERFUSION_ADMIN_PASSWORD) {
    $candidates += "admin123"
  }
  foreach ($candidate in $candidates) {
    $payload = @{ username = $Username; password = $candidate } | ConvertTo-Json -Compress
    $result = Invoke-Api "POST" "/auth/login" "" $payload
    if ($result.Status -eq 200) {
      try {
        $body = $result.Body | ConvertFrom-Json
        $token = $body.data.accessToken
        if (-not [string]::IsNullOrWhiteSpace($token)) {
          return $token
        }
      } catch {
      }
    }
  }
  return ""
}

function Check-FrontendProxy($BaseUrl, $ApiBaseUrl) {
  $backendPort = Get-PortFromUrl $ApiBaseUrl
  $url = $BaseUrl.TrimEnd("/") + "/api/health"
  try {
    $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 8
    $body = $response.Content | ConvertFrom-Json
    if ($response.StatusCode -eq 200 -and $body.data.status -eq "UP") {
      Pass "frontend proxy /api/health reaches backend on port $backendPort"
    } else {
      Fail "frontend proxy /api/health did not report backend UP"
    }
  } catch {
    Fail "frontend proxy /api/health failed: $($_.Exception.Message)"
  }
}

function Check-Health($ApiBaseUrl) {
  $url = $ApiBaseUrl.TrimEnd("/") + "/health"
  try {
    $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 8
    $body = $response.Content | ConvertFrom-Json
    $data = $body.data
    Write-Host "[INFO] health status=$($data.status) version=$($data.version) profile=$($data.profile)"
    foreach ($key in @("database", "schema", "seed", "redis")) {
      $item = $data.dependencies.$key
      Write-Host "[INFO] health ${key}: $($item.status) $($item.message)"
    }
    if ($data.status -eq "UP") {
      Pass "/api/health reports UP"
    } else {
      Fail "/api/health reports DOWN"
    }
  } catch {
    Fail "/api/health request failed: $($_.Exception.Message)"
  }
}

function Test-MenuPath($Menus, $Target) {
  foreach ($item in $Menus) {
    if ($item.path -eq $Target) {
      return $true
    }
    if ($item.children -and (Test-MenuPath $item.children $Target)) {
      return $true
    }
  }
  return $false
}

function Check-RuntimePermissions() {
  $adminToken = Login-User $AdminUser $AdminPassword
  if ([string]::IsNullOrWhiteSpace($adminToken)) {
    Fail "admin login failed; check current demo password and seed"
    return
  }
  Pass "admin login succeeded"
  $adminMe = Invoke-Api "GET" "/auth/me" $adminToken
  if ($adminMe.Status -ne 200) {
    Fail "admin /auth/me returned $($adminMe.Status)"
  } else {
    $body = $adminMe.Body | ConvertFrom-Json
    $missingMenus = @()
    foreach ($path in @("/soc/policies", "/soc/incidents", "/soc/reports")) {
      if (-not (Test-MenuPath $body.data.menus $path)) {
        $missingMenus += $path
      }
    }
    if ($missingMenus.Count -eq 0) {
      Pass "admin /auth/me includes latest SOC menus"
    } else {
      Fail "admin /auth/me is missing latest SOC menus: $($missingMenus -join ', ')"
    }
    $permissions = @($body.data.permissions)
    $missingPermissions = @()
    foreach ($permission in @("soc:policy:list", "soc:incident:list", "soc:correlation-rule:list")) {
      if ($permissions -notcontains $permission) {
        $missingPermissions += $permission
      }
    }
    if ($missingPermissions.Count -eq 0) {
      Pass "admin /auth/me includes latest policy and incident permissions"
    } else {
      Fail "admin /auth/me is missing latest permissions: $($missingPermissions -join ', ')"
    }
  }

  $employeeToken = Login-User $EmployeeUser $EmployeePassword
  if ([string]::IsNullOrWhiteSpace($employeeToken)) {
    Fail "employee login failed; check current demo password and seed"
    return
  }
  Pass "employee login succeeded"
  foreach ($path in @("/soc/policies/local-check-commands", "/soc/incidents?pageNum=1&pageSize=1", "/soc/correlation-rules?pageNum=1&pageSize=1")) {
    $result = Invoke-Api "GET" $path $employeeToken
    if ($result.Status -eq 403) {
      Pass "employee denied $path"
    } else {
      Fail "employee access boundary failed for ${path}: got $($result.Status)"
    }
  }
  $catalog = Invoke-Api "GET" "/client/local-terminal/commands?os=Linux" $employeeToken
  if ($catalog.Status -eq 200) {
    Pass "employee can read active local-check commands only"
  } else {
    Fail "employee local-check command catalog returned $($catalog.Status)"
  }
}

function Invoke-MySqlQuery {
  param(
    [string]$Query
  )

  if ([string]::IsNullOrWhiteSpace($DbPassword)) {
    throw "DB_PASSWORD is not set; SQL authentication check is blocked. Export DB_PASSWORD outside source before applying SQL or running live smoke."
  }

  $localMysql = Get-Command mysql -ErrorAction SilentlyContinue
  $previousMysqlPwd = $env:MYSQL_PWD
  try {
    $env:MYSQL_PWD = $DbPassword
    if ($localMysql) {
      return $Query | & mysql --default-character-set=utf8mb4 "-h$DbHost" "-P$DbPort" "-u$DbUsername" --batch --skip-column-names $DbName 2>&1
    }

    throw "Local mysql client is required for Windows no-Docker diagnostics. Install MySQL 8 client tools and add mysql.exe to PATH."
  } finally {
    if ($null -eq $previousMysqlPwd) {
      Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    } else {
      $env:MYSQL_PWD = $previousMysqlPwd
    }
  }
}

function Check-MysqlSeed {
  $query = @"
SELECT 'tables', COUNT(*) FROM information_schema.tables
 WHERE table_schema = DATABASE()
   AND table_name IN ('sys_user','sys_menu','sys_role_menu','soc_asset','soc_external_event','soc_alert','soc_ticket','soc_report','soc_local_check_command','soc_detection_rule_policy','soc_event_adapter_profile','soc_correlation_rule','soc_incident_cluster','soc_incident_evidence');
SELECT 'menus', COUNT(*) FROM sys_menu WHERE path IN ('/soc/policies','/soc/incidents','/soc/reports') AND status = 1;
SELECT 'permissions', COUNT(*) FROM sys_menu WHERE permission IN ('soc:policy:list','soc:incident:list','soc:correlation-rule:list') AND status = 1;
SELECT 'admin', COUNT(*) FROM sys_user WHERE username = 'admin' AND status = 1;
"@
  try {
    $output = Invoke-MySqlQuery -Query $query
  } catch {
    Fail "MySQL SQL query failed. Verify mysql.exe is in PATH and DB_PASSWORD matches the configured Windows MySQL service. $($_.Exception.Message)"
    return
  }
  if ($LASTEXITCODE -ne 0) {
    Fail "MySQL SQL query failed. Verify mysql.exe is in PATH and DB_PASSWORD matches the configured Windows MySQL service."
    return
  }
  $rows = @{}
  foreach ($line in $output) {
    Write-Host "[INFO] mysql $line"
    $parts = $line -split "`t"
    if ($parts.Length -eq 2) {
      $rows[$parts[0]] = [int]$parts[1]
    }
  }
  if (($rows["tables"] -ge 13) -and ($rows["menus"] -ge 3) -and ($rows["permissions"] -ge 3) -and ($rows["admin"] -ge 1)) {
    Pass "Windows MySQL key tables and seed rows are present"
  } else {
    Fail "Windows MySQL key tables or seed rows are incomplete"
  }
}

Write-Host "CyberFusion dev doctor"
Write-Host "Frontend URL: $BaseUrl"
Write-Host "Backend API: $ApiBaseUrl"
Write-Host "MySQL: $DbUsername@$DbHost`:$DbPort/$DbName"
Write-Host ""

Check-Port "frontend" (Get-PortFromUrl $BaseUrl)
Check-Port "backend" (Get-PortFromUrl $ApiBaseUrl)
Check-Http "frontend shell" $BaseUrl
Check-FrontendProxy $BaseUrl $ApiBaseUrl
Check-Health $ApiBaseUrl
Check-MysqlSeed
Check-RuntimePermissions

Write-Host ""
Write-Host "Summary: PASS=$PassCount WARN=$WarnCount FAIL=$FailCount"
if ($FailCount -gt 0) {
  exit 1
}

$ErrorActionPreference = "Stop"

function Get-CyberFusionDefaultEnvRoot {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ProjectRoot
    )

    $ProjectParent = Split-Path -Parent $ProjectRoot
    return (Join-Path $ProjectParent "Environment\cyberfusion-platform")
}

function Resolve-CyberFusionEnvRoot {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ProjectRoot,
        [string]$EnvRoot = ""
    )

    if (-not [string]::IsNullOrWhiteSpace($EnvRoot)) {
        return $EnvRoot
    }
    if (-not [string]::IsNullOrWhiteSpace($env:CYBERFUSION_ENV_ROOT)) {
        return $env:CYBERFUSION_ENV_ROOT
    }
    return Get-CyberFusionDefaultEnvRoot -ProjectRoot $ProjectRoot
}

function Assert-CyberFusionAbsolutePath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Label,
        [Parameter(Mandatory = $true)]
        [string]$PathValue
    )

    if ([string]::IsNullOrWhiteSpace($PathValue)) {
        throw "$Label must be set to an absolute Windows path."
    }
    if ($PathValue -notmatch "^[A-Za-z]:[\\/]" -and $PathValue -notmatch "^\\\\") {
        throw "$Label must use an absolute Windows path, not $PathValue."
    }
}

function Convert-CyberFusionComparablePath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$PathValue
    )

    return ([System.IO.Path]::GetFullPath($PathValue).TrimEnd("\", "/").ToLowerInvariant())
}

function Assert-CyberFusionOutsideProject {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Label,
        [Parameter(Mandatory = $true)]
        [string]$PathValue,
        [Parameter(Mandatory = $true)]
        [string]$ProjectRoot
    )

    $ComparablePath = Convert-CyberFusionComparablePath -PathValue $PathValue
    $ComparableProjectRoot = Convert-CyberFusionComparablePath -PathValue $ProjectRoot
    if ($ComparablePath -eq $ComparableProjectRoot -or $ComparablePath.StartsWith("$ComparableProjectRoot\")) {
        throw "$Label must not be inside the source project: $PathValue"
    }
}

function Assert-CyberFusionRuntimePath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Label,
        [Parameter(Mandatory = $true)]
        [string]$PathValue,
        [Parameter(Mandatory = $true)]
        [string]$ProjectRoot
    )

    Assert-CyberFusionAbsolutePath -Label $Label -PathValue $PathValue
    Assert-CyberFusionOutsideProject -Label $Label -PathValue $PathValue -ProjectRoot $ProjectRoot
}

function Set-CyberFusionRuntimeEnvironment {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ProjectRoot,
        [Parameter(Mandatory = $true)]
        [string]$EnvRoot
    )

    Assert-CyberFusionRuntimePath -Label "Runtime root" -PathValue $EnvRoot -ProjectRoot $ProjectRoot
    $env:CYBERFUSION_ENV_ROOT = $EnvRoot
    $env:APP_UPLOAD_BASE_DIR = if ([string]::IsNullOrWhiteSpace($env:APP_UPLOAD_BASE_DIR)) { Join-Path $EnvRoot "uploads" } else { $env:APP_UPLOAD_BASE_DIR }
    $env:LOGGING_FILE_PATH = if ([string]::IsNullOrWhiteSpace($env:LOGGING_FILE_PATH)) { Join-Path $EnvRoot "logs\backend" } else { $env:LOGGING_FILE_PATH }
    $env:MAVEN_REPO_LOCAL = if ([string]::IsNullOrWhiteSpace($env:MAVEN_REPO_LOCAL)) { Join-Path $EnvRoot "caches\maven-repository" } else { $env:MAVEN_REPO_LOCAL }
    $env:PNPM_STORE_DIR = if ([string]::IsNullOrWhiteSpace($env:PNPM_STORE_DIR)) { Join-Path $EnvRoot "caches\pnpm-store" } else { $env:PNPM_STORE_DIR }
    $env:npm_config_cache = if ([string]::IsNullOrWhiteSpace($env:npm_config_cache)) { Join-Path $EnvRoot "caches\npm" } else { $env:npm_config_cache }
    $env:CYBERFUSION_TEMP_DIR = if ([string]::IsNullOrWhiteSpace($env:CYBERFUSION_TEMP_DIR)) { Join-Path $EnvRoot "tmp" } else { $env:CYBERFUSION_TEMP_DIR }

    Assert-CyberFusionRuntimePath -Label "Upload directory" -PathValue $env:APP_UPLOAD_BASE_DIR -ProjectRoot $ProjectRoot
    Assert-CyberFusionRuntimePath -Label "Log directory" -PathValue $env:LOGGING_FILE_PATH -ProjectRoot $ProjectRoot
    Assert-CyberFusionRuntimePath -Label "Maven repository" -PathValue $env:MAVEN_REPO_LOCAL -ProjectRoot $ProjectRoot
    Assert-CyberFusionRuntimePath -Label "pnpm store" -PathValue $env:PNPM_STORE_DIR -ProjectRoot $ProjectRoot
    Assert-CyberFusionRuntimePath -Label "npm cache" -PathValue $env:npm_config_cache -ProjectRoot $ProjectRoot
    Assert-CyberFusionRuntimePath -Label "Temp directory" -PathValue $env:CYBERFUSION_TEMP_DIR -ProjectRoot $ProjectRoot

    $env:TEMP = $env:CYBERFUSION_TEMP_DIR
    $env:TMP = $env:CYBERFUSION_TEMP_DIR
    if ([string]::IsNullOrWhiteSpace($env:JAVA_TOOL_OPTIONS)) {
        $env:JAVA_TOOL_OPTIONS = "-Djava.io.tmpdir=$env:CYBERFUSION_TEMP_DIR"
    } elseif ($env:JAVA_TOOL_OPTIONS -notmatch "java\.io\.tmpdir") {
        $env:JAVA_TOOL_OPTIONS = "$env:JAVA_TOOL_OPTIONS -Djava.io.tmpdir=$env:CYBERFUSION_TEMP_DIR"
    }
}

function Initialize-CyberFusionRuntimeDirectories {
    param(
        [Parameter(Mandatory = $true)]
        [string]$EnvRoot
    )

    $RequiredDirs = @(
        $EnvRoot,
        (Join-Path $EnvRoot "uploads"),
        (Join-Path $EnvRoot "logs\backend"),
        (Join-Path $EnvRoot "backups\runtime"),
        (Join-Path $EnvRoot "local-vm"),
        (Join-Path $EnvRoot "caches\maven-repository"),
        (Join-Path $EnvRoot "caches\pnpm-store"),
        (Join-Path $EnvRoot "caches\npm"),
        (Join-Path $EnvRoot "tmp"),
        (Join-Path $EnvRoot "packages"),
        (Join-Path $EnvRoot "package-staging"),
        (Join-Path $EnvRoot "validation")
    )

    foreach ($Dir in $RequiredDirs) {
        New-Item -ItemType Directory -Force -Path $Dir | Out-Null
        Write-Host "OK directory: $Dir"
    }
}

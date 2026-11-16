param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    if (!$env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle' }
    $sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
    if (!$env:ANDROID_HOME) { $env:ANDROID_HOME = $sdkRoot }
    if (!$SkipBuild) {
        & ./gradlew.bat testDebugUnitTest lintDebug assembleRelease
        if ($LASTEXITCODE -ne 0) { throw 'Tests, Android checks, or the release build failed.' }
    }
    $metadata = Get-Content -Raw app/build/outputs/apk/release/output-metadata.json | ConvertFrom-Json
    $apkName = $metadata.elements[0].outputFile
    if ($apkName -match 'unsigned') { throw 'Release is unsigned. Configure .signing/keystore.properties or TIDYRA_SIGNING_PROPERTIES.' }
    $apkPath = Join-Path $projectRoot "app/build/outputs/apk/release/$apkName"
    $buildTools = Get-ChildItem (Join-Path $sdkRoot 'build-tools') -Directory |
        Where-Object { Test-Path (Join-Path $_.FullName 'apksigner.bat') } | Sort-Object Name -Descending | Select-Object -First 1
    if (!$buildTools) { throw 'Android build tools with apksigner were not found.' }
    & (Join-Path $buildTools.FullName 'apksigner.bat') verify --verbose --print-certs $apkPath
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    $hash = Get-FileHash -Algorithm SHA256 -LiteralPath $apkPath
    Write-Output "Verified signed APK: $apkPath"
    Write-Output "SHA-256: $($hash.Hash.ToLowerInvariant())"
} finally { Pop-Location }

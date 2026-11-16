param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    & "$PSScriptRoot/verify-release.ps1" -SkipBuild:$SkipBuild
    $metadata = Get-Content -Raw app/build/outputs/apk/release/output-metadata.json | ConvertFrom-Json
    $version = $metadata.elements[0].versionName
    if ($version -notmatch '^\d+\.\d+\.\d+$') { throw 'Expected a production release version.' }
    $outputRoot = Join-Path $projectRoot 'build/release'
    New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
    $apkName = "Tidyra-$version.apk"
    Copy-Item -LiteralPath "app/build/outputs/apk/release/$($metadata.elements[0].outputFile)" -Destination (Join-Path $outputRoot $apkName)
    Copy-Item -LiteralPath RELEASE.md -Destination (Join-Path $outputRoot 'RELEASE.md')

    # Include the actual working source, including uncommitted/new source files.
    # Git's ignored files plus an explicit denylist keep keys and phone data out.
    $paths = @(& git -c core.quotepath=false ls-files --cached --others --exclude-standard) | Sort-Object -Unique
    if ($LASTEXITCODE -ne 0) { throw 'Could not enumerate release sources.' }
    $sourceName = "Tidyra-$version-source.zip"
    Add-Type -AssemblyName System.IO.Compression
    $stream = [IO.File]::Open((Join-Path $outputRoot $sourceName), [IO.FileMode]::Create)
    $archive = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($relative in $paths) {
            if ($relative -match '(^|/)(\.git|\.signing|build|phone-backup)(/|$)|(^|/)(local|keystore)\.properties$|\.(p12|jks|keystore|apk|aab)$') {
                throw "Unexpected private/generated path in source list: $relative"
            }
            $absolute = [IO.Path]::GetFullPath((Join-Path $projectRoot $relative))
            if (!$absolute.StartsWith($projectRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
                throw 'A source path escaped the project.'
            }
            if (!(Test-Path -LiteralPath $absolute -PathType Leaf)) { continue }
            $entry = $archive.CreateEntry($relative.Replace('\', '/'), [IO.Compression.CompressionLevel]::Optimal)
            $inputStream = [IO.File]::OpenRead($absolute)
            $entryStream = $entry.Open()
            try { $inputStream.CopyTo($entryStream) } finally { $entryStream.Dispose(); $inputStream.Dispose() }
        }
    } finally { $archive.Dispose(); $stream.Dispose() }
    @($apkName, $sourceName, 'RELEASE.md') | ForEach-Object {
        $hash = Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $outputRoot $_)
        "$($hash.Hash.ToLowerInvariant())  $_"
    } | Set-Content -LiteralPath (Join-Path $outputRoot 'SHA256SUMS.txt') -Encoding ascii
    Write-Output "Release package: $outputRoot"
} finally { Pop-Location }

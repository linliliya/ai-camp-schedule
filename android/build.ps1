param([switch]$SkipChecks)
$ErrorActionPreference = 'Stop'
$projectDir = $PSScriptRoot
$repoDir = Split-Path -Parent $projectDir
$signingDir = Join-Path $env:USERPROFILE '.android\camp-schedule-signing'
$signingConfig = Join-Path $projectDir 'signing.properties'
$credentialsPath = Join-Path $signingDir 'credentials.json'
$keyPath = Join-Path $signingDir 'camp-schedule.p12'

if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    throw 'Set JAVA_HOME to an installed JDK 17 or 21 before building.'
}
if (-not $env:ANDROID_HOME -and -not $env:ANDROID_SDK_ROOT -and -not (Test-Path (Join-Path $projectDir 'local.properties'))) {
    throw 'Set ANDROID_HOME to the Android SDK, or provide android/local.properties.'
}
Push-Location $repoDir
try {
    & node scripts/export-android-data.js
    if ($LASTEXITCODE -ne 0) { throw 'SOP export failed.' }
    if (-not $SkipChecks) {
        & npm test
        if ($LASTEXITCODE -ne 0) { throw 'Website tests failed.' }
    }
    if (-not (Test-Path -LiteralPath $signingConfig)) {
        New-Item -ItemType Directory -Force -Path $signingDir | Out-Null
        if (-not (Test-Path -LiteralPath $credentialsPath)) {
            if (Test-Path -LiteralPath $keyPath) { throw 'Existing signing key found without credentials; restore credentials.json rather than replacing the key.' }
            $taskPassword = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
            & (Join-Path $env:JAVA_HOME 'bin\keytool.exe') -genkeypair -keystore $keyPath -storetype PKCS12 -alias camp-schedule -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Camp Schedule Personal,O=Aurange,C=CN' -storepass $taskPassword -keypass $taskPassword
            if ($LASTEXITCODE -ne 0) { throw 'Signing key generation failed.' }
            @{password=$taskPassword} | ConvertTo-Json | Set-Content -LiteralPath $credentialsPath -Encoding utf8
        }
        $credentials = Get-Content -Raw -LiteralPath $credentialsPath | ConvertFrom-Json
        $keyPropertyPath = $keyPath.Replace('\', '/')
        $lines = @("storeFile=$keyPropertyPath", "storePassword=$($credentials.password)", 'keyAlias=camp-schedule', "keyPassword=$($credentials.password)")
        [IO.File]::WriteAllLines($signingConfig, $lines, [Text.UTF8Encoding]::new($false))
    }
    [string[]]$tasks = if ($SkipChecks) { @('assembleRelease') } else { @('testDebugUnitTest', 'lintDebug', 'assembleRelease') }
    & (Join-Path $projectDir 'gradlew.bat') -p $projectDir @tasks --console plain
    if ($LASTEXITCODE -ne 0) { throw 'Android verification/build failed.' }
    $outputDir = Join-Path $repoDir 'artifacts\android'
    New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
    $releaseMetadata = Get-Content -Raw -LiteralPath (Join-Path $projectDir 'app\build\outputs\apk\release\output-metadata.json') | ConvertFrom-Json
    $apkName = "camp-schedule-$($releaseMetadata.elements[0].versionName).apk"
    $apk = Join-Path $outputDir $apkName
    Copy-Item -LiteralPath (Join-Path $projectDir 'app\build\outputs\apk\release\app-release.apk') -Destination $apk -Force
    $hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $apkName" | Set-Content -LiteralPath (Join-Path $outputDir 'SHA256SUMS.txt') -Encoding ascii
    Write-Output "APK: $apk"
    Write-Output "SHA-256: $hash"
} finally { Pop-Location }

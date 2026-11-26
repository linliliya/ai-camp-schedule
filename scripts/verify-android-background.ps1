param([string]$Device = 'emulator-5554')
$ErrorActionPreference = 'Stop'
$repoDir = Split-Path -Parent $PSScriptRoot
$outputDir = Join-Path $repoDir 'artifacts\android'
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
$qemu = (& adb -s $Device shell getprop ro.kernel.qemu).Trim()
if ($qemu -ne '1') { throw 'This fixture-based check is for an Android emulator only. It clears the test app account.' }
'RUNNING: background verification has not completed.' | Set-Content -LiteralPath (Join-Path $outputDir 'background-check.txt') -Encoding utf8
function AdbChecked([string[]]$Arguments) {
    & adb -s $Device @Arguments
    if ($LASTEXITCODE -ne 0) { throw "ADB step failed: $($Arguments[0])" }
}
AdbChecked @('install','-r', (Join-Path $repoDir 'android\app\build\outputs\apk\debug\app-debug.apk'))
AdbChecked @('install','-r', (Join-Path $repoDir 'android\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'))
$runner = 'cn.aurange.schedule.test/androidx.test.runner.AndroidJUnitRunner'
# Isolate fake sessions from install/permission restore broadcasts that schedule real cloud sync.
$isolate = & adb -s $Device shell am instrument -w -r -e class 'cn.aurange.schedule.SystemIntegrationTest#isolateBackgroundFixture' $runner
if (($isolate -join "`n") -notmatch 'OK \(1 test\)') { throw 'Could not isolate background fixture.' }
try {
    AdbChecked @('shell','pm','grant','cn.aurange.schedule','android.permission.POST_NOTIFICATIONS')
    AdbChecked @('shell','appops','set','cn.aurange.schedule','SCHEDULE_EXACT_ALARM','allow')
    $core = @('encryptedSessionAndCacheRoundTripAndLogoutClearsReminders','snapshotReadersFromAnotherStoreCannotDiscardAnAcknowledgedWrite','completedTaskIsSuppressedAtDeliveryEvenWhenOldAlarmStillExists','pendingNotificationRoutesToRealActivity') |
        ForEach-Object { "cn.aurange.schedule.SystemIntegrationTest#$_" }
    $classes = ($core + 'cn.aurange.schedule.CloudIntegrationTest') -join ','
    $coreResult = & adb -s $Device shell am instrument -w -r -e class $classes $runner
    $coreResult | Set-Content -LiteralPath (Join-Path $outputDir 'device-tests.txt') -Encoding utf8
    if (($coreResult -join "`n") -notmatch 'OK \(10 tests\)') { throw "Device tests failed: $($coreResult -join "`n")" }

    $prepare = & adb -s $Device shell am instrument -w -r -e class 'cn.aurange.schedule.SystemIntegrationTest#prepareBackgroundAlarm' $runner
    if (($prepare -join "`n") -notmatch 'OK \(1 test\)') { throw 'Could not prepare the background alarm fixture.' }
    # Kill the app process without force-stop: force-stop intentionally cancels Android alarms.
    AdbChecked @('shell','am','kill','cn.aurange.schedule')
    $processAfterKill = (& adb -s $Device shell pidof cn.aurange.schedule) -join ''
    if ($processAfterKill.Trim()) { throw 'The app process did not exit before the background check.' }
    $deadline = (Get-Date).AddSeconds(45)
    $delivered = $false
    while ((Get-Date) -lt $deadline) {
        $notifications = (& adb -s $Device shell dumpsys notification --noredact) -join "`n"
        if ($notifications -match '第999期' -and $notifications -match '新建本期入学档案问卷') {
            $notifications | Set-Content -LiteralPath (Join-Path $outputDir 'background-notification.txt') -Encoding utf8
            $delivered = $true
            break
        }
        Start-Sleep -Milliseconds 750
    }
    if (-not $delivered) { throw 'No notification was delivered after app process exit.' }
    'PASS: 10 device tests; app process exited before scheduled notification; notification delivered without reopening UI.' |
        Set-Content -LiteralPath (Join-Path $outputDir 'background-check.txt') -Encoding utf8
    Write-Output 'PASS: encrypted cache, logout cleanup, completion suppression, notification routing, and process-exit reminder delivery.'
} finally {
    & adb -s $Device shell am instrument -w -r -e class 'cn.aurange.schedule.SystemIntegrationTest#clearTestFixture' $runner | Out-Null
}

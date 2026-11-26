param([string]$Device = 'emulator-5554')
$ErrorActionPreference = 'Stop'
$repoDir = Split-Path -Parent $PSScriptRoot
$outputDir = Join-Path $repoDir 'artifacts\android\ui-redesign'
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
if ((& adb -s $Device shell getprop ro.kernel.qemu).Trim() -ne '1') { throw 'UI checks require an emulator.' }
$package = 'cn.aurange.schedule'
$script:sequence = 0
function AdbChecked([string[]]$Arguments) {
    $result = & adb -s $Device @Arguments
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $($Arguments[0])" }
    return $result
}
function ReadUi {
    $foreground = (AdbChecked @('shell','dumpsys','activity','activities')) -join "`n"
    if ($foreground -notmatch 'topResumedActivity=.*cn\.aurange\.schedule/\.MainActivity') { throw 'Schedule app is not in the foreground; refusing to capture another app.' }
    $script:sequence++
    $remote = "/sdcard/camp-ui-$($script:sequence).xml"
    $dump = AdbChecked @('shell','uiautomator','dump',$remote)
    if (($dump -join '') -notmatch 'dumped to:') { throw 'UI hierarchy did not finish.' }
    $local = Join-Path $outputDir 'ui-current.xml'
    AdbChecked @('pull',$remote,$local) | Out-Null
    AdbChecked @('shell','rm',$remote) | Out-Null
    [xml]$script:ui = Get-Content -Raw -LiteralPath $local
    if (-not $script:ui.SelectSingleNode('//node[@package="cn.aurange.schedule"]')) { throw 'Wrong UI package.' }
    $crashes = (AdbChecked @('logcat','-d','-b','crash')) -join "`n"
    if ($crashes -match 'Process: cn\.aurange\.schedule') { throw 'Schedule app crashed.' }
}
function FindText([string]$Text) { return $script:ui.SelectNodes('//node') | Where-Object { $_.text -eq $Text } | Select-Object -First 1 }
function AssertText([string]$Text) { if (-not (FindText $Text)) { throw "Missing text: $Text" } }
function Bounds($Node) {
    if (-not $Node) { throw 'UI element not found.' }
    return [int[]]([regex]::Matches($Node.bounds,'\d+') | ForEach-Object { $_.Value })
}
function TapNode($Node) {
    $box = Bounds $Node
    AdbChecked @('shell','input','tap', [string][int](($box[0]+$box[2])/2), [string][int](($box[1]+$box[3])/2)) | Out-Null
    Start-Sleep -Milliseconds 450
    ReadUi
}
function TapText([string]$Text) { TapNode (FindText $Text) }
function Screenshot([string]$Name) {
    ReadUi
    AdbChecked @('shell','screencap','-p','/sdcard/camp-verified.png') | Out-Null
    AdbChecked @('pull','/sdcard/camp-verified.png',(Join-Path $outputDir "$Name.png")) | Out-Null
}

AdbChecked @('shell','am','force-stop',$package) | Out-Null
AdbChecked @('logcat','-c') | Out-Null
AdbChecked @('shell','am','start','-W','-n',"$package/.MainActivity") | Out-Null
ReadUi
if (FindText '演示预览') { TapText '登录' }
AssertText '先看看应用'
Screenshot 'login-verified'
TapText '先看看应用'
AssertText '今天的安排'
if (-not ($script:ui.SelectNodes('//node') | Where-Object { $_.text -match '^已完成 2 项' })) { throw 'Demo completion counts are incorrect.' }
Screenshot 'today-verified'
$todayLabel = ($script:ui.SelectNodes('//node') | Where-Object { $_.text -match '^\d+月\d+日 ·' } | Select-Object -First 1).text
$today = [DateTime]::ParseExact(($todayLabel -split ' ·')[0], 'M月d日', [cultureinfo]::InvariantCulture)
$calendar = $script:ui.SelectSingleNode('//node[@content-desc="周日历，左右滑动切换周"]')
$box = Bounds $calendar
$before = ($script:ui.SelectNodes('//node') | Where-Object { $_.'content-desc' -match '^\d{4}年\d+月\d+日' } | Select-Object -First 1).'content-desc'
$monday = [DateTime]::ParseExact(($before -split ' ')[0], 'yyyy年M月d日', [cultureinfo]::InvariantCulture)
AdbChecked @('shell','input','swipe',[string]($box[2]-40),[string][int](($box[1]+$box[3])/2),[string]($box[0]+40),[string][int](($box[1]+$box[3])/2),'350') | Out-Null
Start-Sleep -Milliseconds 600
ReadUi
$nextMonday = $monday.AddDays(7).ToString('yyyy年M月d日')
if (-not ($script:ui.SelectNodes('//node') | Where-Object { $_.'content-desc' -like "$nextMonday*" })) { throw 'Calendar did not advance by one week.' }
AssertText '今天'
AssertText ($today.AddDays(7).ToString('M月d日'))
TapText '营期'
AssertText '我的营期'
Screenshot 'camps-verified'
TapText '今日'
AssertText '今天'
TapText '今天'
AssertText '今天的安排'
TapText '已完成'
if ($script:ui.SelectSingleNode('//node[@content-desc="标记完成"]')) { throw 'Unfinished task appeared in completed filter.' }
if (-not $script:ui.SelectSingleNode('//node[@content-desc="取消完成"]')) { throw 'Completed filter is empty.' }
TapText '待办'
if ($script:ui.SelectSingleNode('//node[@content-desc="取消完成"]')) { throw 'Completed task appeared in pending filter.' }
TapText '全部'
$checkbox = $script:ui.SelectSingleNode('//node[@content-desc="标记完成"]')
$task = $checkbox.ParentNode
$box = Bounds $task
AdbChecked @('shell','input','tap',[string][int]($box[2]-100),[string][int](($box[1]+$box[3])/2)) | Out-Null
ReadUi
AssertText '本机提醒'
AssertText '演示模式（只读）'
Screenshot 'detail-verified'
TapNode ($script:ui.SelectSingleNode('//node[@content-desc="收起任务"]'))
TapText '设置'
AssertText '偏好设置'
Screenshot 'settings-verified'
TapText '任务提前提醒'
AssertText '提前 15 分钟'
TapText '取消'
TapText '每日汇总'
AdbChecked @('shell','input','keyevent','4') | Out-Null
ReadUi
AssertText '偏好设置'
TapText '今日'
AssertText '今天的安排'
'PASS: demo calendar rendered; week swipe; date retained across tabs; filters; detail; settings; guarded screenshots.' | Tee-Object -FilePath (Join-Path $outputDir 'ui-check.txt')

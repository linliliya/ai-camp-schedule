$ErrorActionPreference = 'Stop'
$websiteRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $websiteRoot
if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw '请先安装 Node.js 24 或更新版本。' }
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw '请先安装 GitHub CLI，并执行 gh auth login。' }
if (-not $env:HTTPS_PROXY) {
    $internetSettings = Get-ItemProperty -LiteralPath 'HKCU:/Software/Microsoft/Windows/CurrentVersion/Internet Settings' -ErrorAction SilentlyContinue
    if ($internetSettings.ProxyEnable -and $internetSettings.ProxyServer -match '^(127\.0\.0\.1|localhost):\d+$') { $env:HTTPS_PROXY = 'http://' + $internetSettings.ProxyServer }
}
node scripts/build-issue-editor.mjs
if ($LASTEXITCODE -ne 0) { throw '编辑器未能构建，请保留草稿并检查 local-sop 的 npm 依赖。' }
Write-Host '新电脑打开 http://127.0.0.1:8902/ 查看网站；已有 8899 预览的电脑可继续使用原地址和草稿。关闭这个窗口会停止提交服务。'
node scripts/issue-service.cjs

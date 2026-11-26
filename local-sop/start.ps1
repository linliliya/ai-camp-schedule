param([int]$Port = 8901, [switch]$Open)
$ErrorActionPreference = 'Stop'
$appDir = $PSScriptRoot
$contentDir = Join-Path (Split-Path -Parent $appDir) 'AI编程SOP'
$stateDir = Join-Path $appDir '.state'
New-Item -ItemType Directory -Force -Path $stateDir | Out-Null
if (-not (Test-Path -LiteralPath (Join-Path $appDir 'dist\index.html'))) {
    Push-Location $appDir
    try {
        if (-not (Test-Path -LiteralPath (Join-Path $appDir 'node_modules'))) {
            & npm ci --no-fund --no-audit
            if ($LASTEXITCODE -ne 0) { throw '前端依赖安装失败。' }
        }
        & npm run build
        if ($LASTEXITCODE -ne 0) { throw '前端构建失败。' }
    } finally { Pop-Location }
}
$url = "http://127.0.0.1:$Port"
$existing = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($existing) {
    try { $current = Invoke-RestMethod "$url/api/documents" -TimeoutSec 3 }
    catch { throw "端口 $Port 正被其他程序使用，请使用 -Port 指定其他端口。" }
    if ($current.root -ne $contentDir) { throw "端口 $Port 已有其他内容目录的服务，请换一个端口。" }
} else {
    $python = (Get-Command python).Source
    $arguments = @('-X','utf8',('"' + (Join-Path $appDir 'server.py') + '"'),'--port',[string]$Port,'--root',('"' + $contentDir + '"'),'--state',('"' + $stateDir + '"'))
    $serverProcess = Start-Process -FilePath $python -ArgumentList $arguments -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $stateDir 'server.log') -RedirectStandardError (Join-Path $stateDir 'server-error.log')
    $serverProcess.Id | Set-Content (Join-Path $stateDir 'server.pid')
    $ready = $false
    for ($attempt = 0; $attempt -lt 15; $attempt++) {
        try { $null = Invoke-RestMethod "$url/api/documents" -TimeoutSec 2; $ready = $true; break }
        catch { Start-Sleep -Milliseconds 300 }
    }
    if (-not $ready) { throw "启动失败，请查看 $stateDir\server-error.log。" }
}
Write-Output "本地 SOP 已启动：$url"
if ($Open) { Start-Process $url }

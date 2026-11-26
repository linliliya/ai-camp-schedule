param([string]$Slug = 'camp-sop-inbox-arvvinn', [string]$Owner = 'Arvvinn', [switch]$CheckOnly)
$ErrorActionPreference = 'Stop'
if ($Slug -notmatch '^[a-zA-Z0-9-]+$' -or $Owner -notmatch '^[a-zA-Z0-9-]+$') { throw 'Invalid GitHub App slug or owner.' }
$response = & gh api "apps/$Slug" 2>$null
if ($LASTEXITCODE -ne 0) { throw "GitHub App '$Slug' is not registered yet. Follow docs/github-issue-setup.md; no server deployment is needed." }
$app = $response | ConvertFrom-Json
if ($app.owner.login -ine $Owner) { throw 'The application owner does not match. No configuration was changed.' }
if (-not $app.client_id -or $app.permissions.issues -ne 'write' -or $app.permissions.contents -ne 'write') { throw 'Enable Issues and Contents read/write permissions for this GitHub App.' }
if (-not $CheckOnly) {
    $configPath = Join-Path (Split-Path -Parent $PSScriptRoot) 'android/app/src/main/assets/github-app.json'
    $json = @{appSlug=$app.slug;appOwner=$app.owner.login;clientId=$app.client_id} | ConvertTo-Json -Compress
    [IO.File]::WriteAllText($configPath, $json + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
}
Write-Output "GitHub App verified: $($app.owner.login)/$($app.slug)"
Write-Output 'Only public app metadata is used. No access token or application secret is copied into the APK.'
Write-Output 'Confirm Enable Device Flow in GitHub App settings, then connect from the phone.'

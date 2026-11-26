param([string]$Owner = 'Arvvinn', [string]$Repository = 'sop-inbox', [switch]$CheckOnly)
$ErrorActionPreference = 'Stop'
if ($Owner -notmatch '^[A-Za-z0-9-]+$' -or $Repository -notmatch '^[A-Za-z0-9_.-]+$') { throw 'Invalid repository.' }
function Invoke-Graph([string]$Query, [hashtable]$Variables = @{}) {
    $requestFile = [IO.Path]::GetTempFileName()
    try {
        $json = @{query=$Query;variables=$Variables} | ConvertTo-Json -Depth 12 -Compress
        [IO.File]::WriteAllText($requestFile, $json, [Text.UTF8Encoding]::new($false))
        $result = & gh api graphql --input $requestFile 2>$null
        if ($LASTEXITCODE -ne 0) { throw 'GitHub Projects permission is required. Run: gh auth refresh -h github.com -s project, then retry.' }
        $parsed = $result | ConvertFrom-Json
        if ($parsed.errors) { throw 'GitHub did not accept the project operation; no success was recorded.' }
        return $parsed.data
    } finally { Remove-Item -LiteralPath $requestFile -ErrorAction SilentlyContinue }
}
$preflight = Invoke-Graph 'query { viewer { login projectsV2(first: 1) { nodes { id } } } }'
if ($preflight.viewer.login -ine $Owner) { throw 'Use the GitHub account that owns this personal repository.' }
$repoInfo = & gh repo view "$Owner/$Repository" --json visibility,hasIssuesEnabled | ConvertFrom-Json
if ($LASTEXITCODE -ne 0 -or $repoInfo.visibility -ne 'PRIVATE' -or -not $repoInfo.hasIssuesEnabled) { throw 'Choose an existing private repository with Issues enabled.' }
if ($CheckOnly) { Write-Output "Projects access verified for $Owner/$Repository. No project was created or changed."; return }
$listing = & gh project list --owner $Owner --limit 100 --format json | ConvertFrom-Json
if ($LASTEXITCODE -ne 0) { throw 'Unable to list personal projects.' }
$title = "SOP Inbox - $Repository"
$project = @($listing.projects | Where-Object { $_.title -eq $title }) | Select-Object -First 1
if (-not $project) {
    $project = & gh project create --owner $Owner --title $title --format json | ConvertFrom-Json
    if ($LASTEXITCODE -ne 0) { throw 'Unable to create the project.' }
}
& gh project edit $project.number --owner $Owner --visibility PRIVATE
if ($LASTEXITCODE -ne 0) { throw 'Unable to set private visibility.' }
$fields = & gh project field-list $project.number --owner $Owner --format json | ConvertFrom-Json
if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect project fields.' }
$field = $fields.fields | Where-Object { $_.name -eq 'Status' } | Select-Object -First 1
if (-not $field) { throw 'The standard Status field was not found.' }
$items = & gh project item-list $project.number --owner $Owner --limit 1 --format json | ConvertFrom-Json
if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect existing project cards.' }
if ($items.totalCount -gt 0) {
    Write-Output "Existing populated project preserved: $($project.url)"
    Write-Output 'Configure its Status options manually rather than replacing live card statuses.'
    return
}
$options = @(
    @{name='待处理';color='GRAY';description='新提交，尚未入库'},
    @{name='待确认';color='YELLOW';description='存在问题或待同步飞书'},
    @{name='已完成';color='GREEN';description='已手动同步飞书并确认'}
)
Invoke-Graph 'mutation($input: UpdateProjectV2FieldInput!) { updateProjectV2Field(input: $input) { projectV2Field { ... on ProjectV2SingleSelectField { id name } } } }' @{input=@{fieldId=$field.id;singleSelectOptions=$options}} | Out-Null
Write-Output "Project created: $($project.url)"
Write-Output 'Finish the Board view and built-in Auto-add/initial-status workflow using docs/github-issue-setup.md.'
Write-Output 'No repository source files were uploaded. Feishu sync completion remains a manual card move.'

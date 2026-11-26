# Generate only the Lucide vector assets used by this app. Upstream ISC / Feather notices are bundled.
$ErrorActionPreference = 'Stop'
$revision = '02eb9161b57917ccaa262f1bf070507cddde3b08'
$repoDir = Split-Path -Parent $PSScriptRoot
$outputDir = Join-Path $repoDir 'android\app\src\main\res\drawable'
$assetDir = Join-Path $repoDir 'android\app\src\main\assets'
$iconNames = @('sun','calendar-days','calendar-check','layers','sliders-horizontal','arrow-up-right','arrow-right',
    'chevron-left','chevron-right','chevron-down','check','clock','bell','refresh-cw','cloud-check','cloud-off',
    'list-filter','circle-check','flag','repeat','shield-check','zap','smartphone','battery-full','log-out',
    'mail','lock-keyhole','eye','eye-off','sparkles','book-open','square-pen','link','info','external-link','arrow-left','coffee')
$baseUrl = "https://raw.githubusercontent.com/lucide-icons/lucide/$revision"
$assets = $iconNames | ForEach-Object -Parallel {
    $name = $_
    $svg = (Invoke-WebRequest -Uri "$using:baseUrl/icons/$name.svg" -TimeoutSec 30).Content
    @{name=$name; svg=$svg}
} -ThrottleLimit 6

function Number($node, [string]$attribute, [double]$fallback = 0) {
    if ($node.HasAttribute($attribute)) { return [double]::Parse($node.GetAttribute($attribute), [Globalization.CultureInfo]::InvariantCulture) }
    return $fallback
}
foreach ($asset in $assets) {
    [xml]$svg = $asset.svg
    $paths = foreach ($shape in $svg.DocumentElement.ChildNodes) {
        if ($shape.NodeType -ne [Xml.XmlNodeType]::Element) { continue }
        switch ($shape.LocalName) {
            'path' { $shape.GetAttribute('d') }
            'line' { "M$(Number $shape 'x1'),$(Number $shape 'y1')L$(Number $shape 'x2'),$(Number $shape 'y2')" }
            'polyline' { 'M' + $shape.GetAttribute('points') }
            'polygon' { 'M' + $shape.GetAttribute('points') + 'Z' }
            'circle' {
                $cx=Number $shape 'cx'; $cy=Number $shape 'cy'; $r=Number $shape 'r'
                "M$($cx-$r),${cy}a${r},${r} 0,1 0,$($r*2),0a${r},${r} 0,1 0,$(-$r*2),0"
            }
            'rect' {
                $x=Number $shape 'x'; $y=Number $shape 'y'; $w=Number $shape 'width'; $h=Number $shape 'height'; $r=Number $shape 'rx'
                "M$($x+$r),${y}h$($w-2*$r)q${r},0 ${r},${r}v$($h-2*$r)q0,${r} $(-$r),${r}h$(-$w+2*$r)q$(-$r),0 $(-$r),$(-$r)v$(-$h+2*$r)q0,$(-$r) ${r},$(-$r)Z"
            }
            default { throw "Unsupported Lucide shape: $($shape.LocalName)" }
        }
    }
    $lines = @('<!-- Lucide ISC / Feather MIT; see assets/lucide-license.txt. -->',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">')
    foreach ($path in $paths) {
        $escaped = [Security.SecurityElement]::Escape($path)
        $lines += "    <path android:pathData=`"$escaped`" android:fillColor=`"@android:color/transparent`" android:strokeColor=`"#000000`" android:strokeWidth=`"1.8`" android:strokeLineCap=`"round`" android:strokeLineJoin=`"round`" />"
    }
    $lines += '</vector>'
    $filename = 'ic_' + $asset.name.Replace('-', '_') + '.xml'
    [IO.File]::WriteAllLines((Join-Path $outputDir $filename), $lines, [Text.UTF8Encoding]::new($false))
}
$license = (Invoke-WebRequest -Uri "$baseUrl/LICENSE" -TimeoutSec 30).Content
[IO.File]::WriteAllText((Join-Path $assetDir 'lucide-license.txt'), $license, [Text.UTF8Encoding]::new($false))
$calendarLicense = (Invoke-WebRequest -Uri 'https://raw.githubusercontent.com/kizitonwose/Calendar/2.6.1/LICENSE.md' -TimeoutSec 30).Content
[IO.File]::WriteAllText((Join-Path $assetDir 'calendar-license.txt'), $calendarLicense, [Text.UTF8Encoding]::new($false))
Write-Output "Generated $($assets.Count) Lucide icons at revision $revision."

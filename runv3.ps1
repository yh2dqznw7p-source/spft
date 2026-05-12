# runv3.ps1 - bootstrapper that downloads buildv3.ps1.
# Fresh URL to bypass raw.githubusercontent.com CDN cache.
#
# Run:
#   iwr "https://raw.githubusercontent.com/yh2dqznw7p-source/spft/incoming/runv3.ps1" -UseBasicParsing | iex

$ErrorActionPreference = 'Continue'

$srcUrl   = 'https://raw.githubusercontent.com/yh2dqznw7p-source/spft/incoming/buildv3.ps1'
$buildPs1 = Join-Path $env:TEMP 'spft-buildv3.ps1'
$runCmd   = Join-Path $env:TEMP 'spft-runv3.cmd'

Write-Host '[GO] downloading buildv3.ps1 ...' -ForegroundColor Cyan

try {
    [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12
    $wc = New-Object System.Net.WebClient
    $wc.Headers.Add('User-Agent','Mozilla/5.0')
    $bytes = $wc.DownloadData($srcUrl)
} catch {
    Write-Host "[GO][FAIL] cannot download: $($_.Exception.Message)" -ForegroundColor Red
    return
}

if (-not $bytes -or $bytes.Length -lt 100) {
    Write-Host '[GO][FAIL] file empty.' -ForegroundColor Red
    return
}

if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
    $noBom = New-Object byte[] ($bytes.Length - 3)
    [Array]::Copy($bytes, 3, $noBom, 0, $bytes.Length - 3)
    $bytes = $noBom
}

$text = [System.Text.Encoding]::UTF8.GetString($bytes)
$utf8bom = New-Object System.Text.UTF8Encoding $true
[System.IO.File]::WriteAllText($buildPs1, $text, $utf8bom)

Write-Host "[GO] saved to $buildPs1 ($([math]::Round((Get-Item $buildPs1).Length / 1KB)) KB)" -ForegroundColor Cyan

# sanity check: new version must mention Ensure-Gradle
if ((Get-Content $buildPs1 -Raw) -notmatch 'Ensure-Gradle') {
    Write-Host '[GO][FAIL] downloaded buildv3.ps1 is stale (no Ensure-Gradle).' -ForegroundColor Red
    Write-Host '  github CDN still serving old data; wait 1-2 minutes and retry.' -ForegroundColor Yellow
    return
}

$parseErrors = $null
try {
    [System.Management.Automation.Language.Parser]::ParseFile($buildPs1, [ref]$null, [ref]$parseErrors) | Out-Null
} catch {
    Write-Host "[GO][FAIL] parser: $($_.Exception.Message)" -ForegroundColor Red
    return
}
if ($parseErrors -and $parseErrors.Count -gt 0) {
    Write-Host "[GO][FAIL] $($parseErrors.Count) parse errors:" -ForegroundColor Red
    $parseErrors | Select-Object -First 3 | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    return
}
Write-Host '[GO] script parses OK.' -ForegroundColor Green

$cmdText = @"
@echo off
chcp 65001 >nul
powershell -NoProfile -ExecutionPolicy Bypass -File `"$buildPs1`"
echo.
echo ================================================
echo   build finished. window will stay open.
echo   press any key to close.
echo ================================================
pause >nul
"@

[System.IO.File]::WriteAllText($runCmd, $cmdText, [System.Text.Encoding]::ASCII)

Write-Host '[GO] launching build in a new window (stays open until you press a key).' -ForegroundColor Cyan
Start-Process -FilePath $runCmd | Out-Null
Write-Host '[GO] done. you can close THIS window.' -ForegroundColor Green

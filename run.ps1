# go.ps1 - bootstrapper for SPFT/maxDLC build.ps1.
#
# Single-line runner:
#   iwr "https://raw.githubusercontent.com/yh2dqznw7p-source/spft/incoming/go.ps1?nocache=$(Get-Random)" -UseBasicParsing | iex
#
# Approach:
#   1. Download build.ps1 into %TEMP% with an explicit UTF-8 BOM
#      (so Windows PowerShell 5.1 parses it correctly regardless of codepage).
#   2. Self-check the saved file by parsing it - if parsing fails, report here,
#      do not even try to launch.
#   3. Generate a .cmd wrapper that invokes build.ps1 and ends with `pause`.
#   4. Launch the .cmd in its own cmd.exe window (which cannot close until
#      the user presses a key).

$ErrorActionPreference = 'Continue'

$srcUrl   = "https://raw.githubusercontent.com/yh2dqznw7p-source/spft/incoming/build.ps1?nocache=$(Get-Random)"
$buildPs1 = Join-Path $env:TEMP 'spft-build.ps1'
$runCmd   = Join-Path $env:TEMP 'spft-run.cmd'

Write-Host '[GO] downloading build.ps1 ...' -ForegroundColor Cyan

# Try raw-bytes HTTP first via .NET (always returns byte[], regardless of
# Content-Type), fall back to Invoke-WebRequest which for text/* returns string.
$bytes = $null
try {
    $wc = New-Object System.Net.WebClient
    $wc.Headers.Add('User-Agent','Mozilla/5.0')
    $bytes = $wc.DownloadData($srcUrl)
} catch {
    try {
        $resp = Invoke-WebRequest -Uri $srcUrl -UseBasicParsing -ErrorAction Stop
        # $resp.Content can be either [byte[]] (binary) or [string] (text) in PS5.1.
        if ($resp.Content -is [byte[]]) {
            $bytes = $resp.Content
        } else {
            $bytes = [System.Text.Encoding]::UTF8.GetBytes([string]$resp.Content)
        }
    } catch {
        Write-Host "[GO][FAIL] cannot download build.ps1: $($_.Exception.Message)" -ForegroundColor Red
        return
    }
}

if (-not $bytes -or $bytes.Length -lt 100) {
    Write-Host '[GO][FAIL] downloaded file is suspiciously small.' -ForegroundColor Red
    return
}

# Strip an existing UTF-8 BOM if GitHub already sent one (avoid double BOM).
if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
    $noBom = New-Object byte[] ($bytes.Length - 3)
    [Array]::Copy($bytes, 3, $noBom, 0, $bytes.Length - 3)
    $bytes = $noBom
}

# Decode as UTF-8 and write back with explicit BOM.
$text = [System.Text.Encoding]::UTF8.GetString($bytes)
$utf8bom = New-Object System.Text.UTF8Encoding $true
[System.IO.File]::WriteAllText($buildPs1, $text, $utf8bom)

Write-Host "[GO] saved to $buildPs1 ($([math]::Round((Get-Item $buildPs1).Length / 1KB)) KB)" -ForegroundColor Cyan

# Self-check: make sure the saved file actually parses as PowerShell.
$parseErrors = $null
try {
    [System.Management.Automation.Language.Parser]::ParseFile($buildPs1, [ref]$null, [ref]$parseErrors) | Out-Null
} catch {
    Write-Host "[GO][FAIL] parser threw: $($_.Exception.Message)" -ForegroundColor Red
    return
}
if ($parseErrors -and $parseErrors.Count -gt 0) {
    Write-Host "[GO][FAIL] saved build.ps1 has $($parseErrors.Count) parse errors, first three:" -ForegroundColor Red
    $parseErrors | Select-Object -First 3 | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    return
}
Write-Host '[GO] script parses OK.' -ForegroundColor Green

# Build a .cmd wrapper that runs the script then pauses.
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

Write-Host '[GO] launching build in a new window...' -ForegroundColor Cyan
Write-Host '[GO] the new window will stay open until you press a key.' -ForegroundColor Cyan

Start-Process -FilePath $runCmd | Out-Null

Write-Host '[GO] done. you can close THIS window.' -ForegroundColor Green

# go.ps1 - bootstrapper for SPFT/maxDLC build.ps1.
#
# Single-line runner:
#   iwr "https://raw.githubusercontent.com/yh2dqznw7p-source/spft/incoming/go.ps1?nocache=$(Get-Random)" -UseBasicParsing | iex
#
# Approach:
#   1. Download build.ps1 into %TEMP% with an explicit UTF-8 BOM (so PS5.1 parses it correctly).
#   2. Generate a .cmd wrapper that invokes build.ps1 and ends with `pause`.
#   3. Launch the .cmd in its own cmd.exe window.
#
# Why a .cmd wrapper instead of powershell -NoExit: cmd `pause` is 100% reliable
# on every Windows from XP onward, while PowerShell -NoExit can be bypassed by
# an internal `exit` or host termination. The .cmd window simply cannot close
# until the user presses a key.

$ErrorActionPreference = 'Continue'

$srcUrl   = "https://raw.githubusercontent.com/yh2dqznw7p-source/spft/incoming/build.ps1?nocache=$(Get-Random)"
$buildPs1 = Join-Path $env:TEMP 'spft-build.ps1'
$runCmd   = Join-Path $env:TEMP 'spft-run.cmd'

Write-Host '[GO] downloading build.ps1 ...' -ForegroundColor Cyan
try {
    # Download as bytes, decode as UTF-8, save with UTF-8 BOM.
    $resp = Invoke-WebRequest -Uri $srcUrl -UseBasicParsing -ErrorAction Stop
    $text = [System.Text.Encoding]::UTF8.GetString($resp.Content)
    $utf8bom = New-Object System.Text.UTF8Encoding $true
    [System.IO.File]::WriteAllText($buildPs1, $text, $utf8bom)
} catch {
    Write-Host "[GO][FAIL] cannot download build.ps1: $($_.Exception.Message)" -ForegroundColor Red
    return
}

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
Write-Host '[GO] the window will stay open until you press a key.' -ForegroundColor Cyan

Start-Process -FilePath $runCmd | Out-Null

Write-Host '[GO] done. you can close THIS window.' -ForegroundColor Green

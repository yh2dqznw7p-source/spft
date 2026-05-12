# go.ps1 — bootstrapper for SPFT/maxDLC build.ps1.
#
# Single-line runner:
#   iwr "https://raw.githubusercontent.com/yh2dqznw7p-source/spft/incoming/go.ps1?nocache=$(Get-Random)" -UseBasicParsing | iex
#
# What it does:
#   1. downloads build.ps1 into %TEMP%\spft-build.ps1
#   2. launches it in a NEW PowerShell window with -NoExit, so the window
#      never auto-closes no matter what, and you can scroll/copy the log
#   3. tees everything into %USERPROFILE%\Desktop\spft-build.log so that
#      even if something unexpected happens you have the full trace

$ErrorActionPreference = 'Continue'

$srcUrl = "https://raw.githubusercontent.com/yh2dqznw7p-source/spft/incoming/build.ps1?nocache=$(Get-Random)"
$dest   = Join-Path $env:TEMP 'spft-build.ps1'

Write-Host '[GO] downloading build.ps1 ...' -ForegroundColor Cyan
try {
    # download as string, then save with explicit UTF-8 BOM so Windows
    # PowerShell 5.1 does not mis-parse it in cp1251 and break on any
    # non-ASCII character.
    $resp = Invoke-WebRequest -Uri $srcUrl -UseBasicParsing -ErrorAction Stop
    $text = [System.Text.Encoding]::UTF8.GetString($resp.Content)
    $utf8bom = New-Object System.Text.UTF8Encoding $true
    [System.IO.File]::WriteAllText($dest, $text, $utf8bom)
} catch {
    Write-Host "[GO][FAIL] cannot download build.ps1: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host 'Press Enter to close...' -ForegroundColor Gray
    try { [void][System.Console]::ReadLine() } catch {}
    return
}

$desktop = [Environment]::GetFolderPath('Desktop')
if (-not $desktop -or -not (Test-Path $desktop)) { $desktop = Join-Path $env:USERPROFILE 'Desktop' }
if (-not (Test-Path $desktop)) { $desktop = Join-Path $env:USERPROFILE 'Рабочий стол' }
$logFile = Join-Path $desktop 'spft-build.log'

Write-Host '[GO] launching build in a new PowerShell window...' -ForegroundColor Cyan
Write-Host "[GO] log will also be written to: $logFile" -ForegroundColor Cyan

# -NoExit: окно НЕ закроется после завершения скрипта.
# -NoProfile: без пользовательского профиля (стабильнее).
# -ExecutionPolicy Bypass: обойти политику выполнения.
# Tee-Object дублирует всё в лог-файл, чтобы даже если окно потеряется —
# у пользователя на рабочем столе был полный лог.
$launchCmd = @"
& {
    `$ErrorActionPreference = 'Continue'
    try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}
    & '$dest' 2>&1 | Tee-Object -FilePath '$logFile'
    Write-Host ''
    Write-Host '======== build finished ========' -ForegroundColor Cyan
    Write-Host ('log: ' + '$logFile') -ForegroundColor Gray
    Write-Host 'This window will stay open. Close it manually when done.' -ForegroundColor Gray
}
"@

Start-Process -FilePath 'powershell.exe' -ArgumentList @(
    '-NoExit','-NoProfile','-ExecutionPolicy','Bypass',
    '-Command', $launchCmd
) | Out-Null

Write-Host '[GO] build window opened. You can close THIS window now.' -ForegroundColor Green

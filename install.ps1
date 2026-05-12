# install.ps1 - установщик SPFT (maxDLC) в .minecraft/mods
# Запуск одной строкой:
#   iwr https://raw.githubusercontent.com/yh2dqznw7p-source/spft/incoming/install.ps1 | iex
#
# Что делает:
#   1. Создаёт %APPDATA%\.minecraft\mods (если её нет).
#   2. Качает последний релиз maxDLC.jar из GitHub Releases.
#   3. Докачивает совместимый Fabric API 1.21.4, если его ещё нет.

$ErrorActionPreference = 'Stop'

# === настройки ===
$REPO          = 'yh2dqznw7p-source/spft'
$JAR_NAME      = 'maxDLC.jar'
# URL формата "latest release asset" — работает, даже если ты будешь релизить новые версии.
$JAR_URL       = "https://github.com/$REPO/releases/latest/download/$JAR_NAME"
# Fabric API под Minecraft 1.21.4 (modrinth primary download).
$FABRIC_API_URL  = 'https://cdn.modrinth.com/data/P7dR8mSH/versions/rH00tDfh/fabric-api-0.118.0%2B1.21.4.jar'
$FABRIC_API_NAME = 'fabric-api-0.118.0+1.21.4.jar'

# === пути ===
$modsDir = Join-Path $env:APPDATA '.minecraft\mods'

Write-Host ''
Write-Host '=== SPFT / maxDLC installer ===' -ForegroundColor Cyan
Write-Host "mods folder: $modsDir"

# === 1. убеждаемся что папка mods есть ===
if (-not (Test-Path $modsDir)) {
    Write-Host "[..] creating $modsDir" -ForegroundColor Yellow
    New-Item -ItemType Directory -Path $modsDir -Force | Out-Null
}

# === 2. качаем сам клиент ===
$target = Join-Path $modsDir $JAR_NAME
Write-Host "[..] downloading $JAR_NAME ..."
try {
    Invoke-WebRequest -Uri $JAR_URL -OutFile $target -UseBasicParsing
    $size = (Get-Item $target).Length
    Write-Host "[OK] $JAR_NAME  ($([math]::Round($size/1KB)) KB)" -ForegroundColor Green
} catch {
    Write-Host "[!!] не удалось скачать $JAR_URL" -ForegroundColor Red
    Write-Host '     проверь, что на github.com/' + $REPO + '/releases есть релиз с файлом ' + $JAR_NAME -ForegroundColor Red
    exit 1
}

# === 3. fabric api ===
$hasFabricApi = Get-ChildItem $modsDir -Filter 'fabric-api-*.jar' -ErrorAction SilentlyContinue
if (-not $hasFabricApi) {
    Write-Host "[..] Fabric API не найден, качаю $FABRIC_API_NAME ..."
    try {
        Invoke-WebRequest -Uri $FABRIC_API_URL -OutFile (Join-Path $modsDir $FABRIC_API_NAME) -UseBasicParsing
        Write-Host '[OK] Fabric API установлен' -ForegroundColor Green
    } catch {
        Write-Host '[!!] Fabric API не скачался — поставь вручную с https://modrinth.com/mod/fabric-api' -ForegroundColor Yellow
    }
} else {
    Write-Host "[OK] Fabric API уже есть: $($hasFabricApi.Name)" -ForegroundColor Green
}

# === 4. напоминание про Fabric Loader ===
Write-Host ''
Write-Host 'Готово!' -ForegroundColor Cyan
Write-Host 'Теперь убедись, что установлен Fabric Loader 0.16+ для Minecraft 1.21.4:'
Write-Host '    https://fabricmc.net/use/installer/' -ForegroundColor Yellow
Write-Host 'и запусти профиль "fabric-loader-1.21.4" в лаунчере.'

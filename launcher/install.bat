@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
title maxDLC

set "DIR=C:\maxDLC"
set "MC=%APPDATA%\.minecraft"
set "MCVER=1.21.4"
set "FL=0.16.9"
set "VER=fabric-loader-%FL%-%MCVER%"

set "FI=https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.0.1/fabric-installer-1.0.1.jar"
set "JAR=https://github.com/yh2dqznw7p-source/spft/releases/latest/download/maxdlc-1.0.0.jar"

rem Self-heal: if previous setup was marked "done" but key files are missing,
rem wipe the marker so setup re-runs automatically.
if exist "%DIR%\.installed" (
    if not exist "%DIR%\mods\fabric-api.jar" del /q "%DIR%\.installed" >nul 2>&1
)
if exist "%DIR%\.installed" goto :run

echo [maxDLC] First-time setup, please wait...
mkdir "%DIR%\mods" 2>nul

where java >nul 2>&1 || ( echo Install Java 21 and retry. & pause & exit /b 1 )
if not exist "%MC%" ( echo Run Minecraft Launcher once, then retry. & pause & exit /b 1 )

rem --- Fabric installer ------------------------------------------------------
call :get "%FI%" "%DIR%\fabric-installer.jar" "Fabric installer" || goto :net
java -jar "%DIR%\fabric-installer.jar" client -mcversion %MCVER% -loader %FL% -dir "%MC%" -noprofile
if errorlevel 1 ( echo [!] Fabric install failed & pause & exit /b 1 )

rem --- Fabric API (resolve actual URL via Modrinth API) ----------------------
echo [maxDLC] Resolving latest Fabric API for %MCVER%...
for /f "usebackq delims=" %%u in (`powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12;" ^
  "$v = Invoke-RestMethod -Uri 'https://api.modrinth.com/v2/project/fabric-api/version?game_versions=%%5B%%22%MCVER%%%22%%5D&loaders=%%5B%%22fabric%%22%%5D';" ^
  "($v ^| Where-Object { $_.version_type -eq 'release' } ^| Select-Object -First 1).files[0].url"`) do set "API=%%u"

if "!API!"=="" (
    echo [!] Could not resolve Fabric API URL from Modrinth.
    pause & exit /b 1
)
echo [maxDLC] Fabric API -> !API!
call :get "!API!" "%DIR%\mods\fabric-api.jar" "Fabric API" || goto :net

rem --- maxDLC jar (optional, no fail) ----------------------------------------
call :get "%JAR%" "%DIR%\mods\maxdlc.jar" "maxDLC"
if not exist "%DIR%\mods\maxdlc.jar" (
    echo.
    echo     [!] maxdlc.jar is not published in the GitHub release yet.
    echo         Build locally with ^(in cloned repo^):
    echo             gradlew.bat build
    echo             copy build\libs\maxdlc-1.0.0.jar "%DIR%\mods\maxdlc.jar"
    echo         Setup continues without it.
    echo.
)

rem --- Patch launcher_profiles.json ------------------------------------------
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$path = '%MC%\launcher_profiles.json';" ^
  "$p = Get-Content -Raw $path | ConvertFrom-Json;" ^
  "$now = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ss.fffZ');" ^
  "$e = [pscustomobject]@{ name='maxDLC'; type='custom'; created=$now; lastUsed=$now; lastVersionId='%VER%'; gameDir='%DIR:\=/%'; icon='Furnace_On'; javaArgs='-Xmx4G -Xms1G' };" ^
  "$p.profiles ^| Add-Member -NotePropertyName maxDLC -NotePropertyValue $e -Force;" ^
  "$p ^| ConvertTo-Json -Depth 20 ^| Set-Content $path -Encoding UTF8"

echo.> "%DIR%\.installed"
echo [maxDLC] Setup finished.

:run
echo [maxDLC] Opening Minecraft Launcher... pick profile "maxDLC" and click Play.
start "" "shell:AppsFolder\Microsoft.4297127D64EC6_8wekyb3d8bbwe!Minecraft" 2>nul && exit /b 0
start "" "minecraft:" 2>nul && exit /b 0
if exist "%ProgramFiles(x86)%\Minecraft Launcher\MinecraftLauncher.exe" ( start "" "%ProgramFiles(x86)%\Minecraft Launcher\MinecraftLauncher.exe" & exit /b 0 )
if exist "%ProgramFiles%\Minecraft Launcher\MinecraftLauncher.exe"      ( start "" "%ProgramFiles%\Minecraft Launcher\MinecraftLauncher.exe"      & exit /b 0 )
echo Open Minecraft Launcher manually and pick profile "maxDLC".
pause & exit /b 0

rem --- helpers ---------------------------------------------------------------

:get
rem %1 = URL, %2 = out, %3 = label
set "_URL=%~1"
set "_OUT=%~2"
set "_LABEL=%~3"
echo [maxDLC] Downloading %_LABEL%...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12;" ^
  "try { Invoke-WebRequest -Uri '%_URL%' -OutFile '%_OUT%' -UseBasicParsing; exit 0 } catch { exit 1 }" >nul 2>&1
if errorlevel 1 curl -sSLf -o "%_OUT%" "%_URL%"
if not exist "%_OUT%" ( echo     [!] %_LABEL% download failed. & exit /b 1 )
for %%S in ("%_OUT%") do if %%~zS LSS 1024 ( echo     [!] %_LABEL% file too small. & del "%_OUT%" & exit /b 1 )
exit /b 0

:net
echo [!] Required download failed. Check internet and retry.
pause & exit /b 1

@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
title maxDLC v3

rem ============================================================
rem   maxDLC play.bat  --  VERSION 3  (fabric-api via Modrinth API)
rem ============================================================
echo.
echo ============================================
echo   maxDLC launcher  [script version v3]
echo ============================================
echo.

set "DIR=C:\maxDLC"
set "MC=%APPDATA%\.minecraft"
set "MCVER=1.21.4"
set "FL=0.16.9"
set "VER=fabric-loader-%FL%-%MCVER%"

set "FI=https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.0.1/fabric-installer-1.0.1.jar"
set "JAR=https://github.com/yh2dqznw7p-source/spft/releases/latest/download/maxdlc-1.0.0.jar"

rem ----- pre-flight checks ---------------------------------------------------
where java >nul 2>&1 || ( echo [!] Java 21 not found in PATH. Install Temurin 21 and retry. & pause & exit /b 1 )
if not exist "%MC%" ( echo [!] %MC% does not exist. Run Minecraft Launcher once and retry. & pause & exit /b 1 )
mkdir "%DIR%\mods" 2>nul

rem ----- 1. Fabric installer & loader (only if not installed yet) ------------
if not exist "%MC%\versions\%VER%\%VER%.json" (
    echo [1/4] Fabric Loader %FL% is not installed yet. Installing...
    call :dl "%FI%" "%DIR%\fabric-installer.jar" "Fabric installer" || goto :net
    java -jar "%DIR%\fabric-installer.jar" client -mcversion %MCVER% -loader %FL% -dir "%MC%" -noprofile
    if errorlevel 1 ( echo [!] Fabric installer failed. & pause & exit /b 1 )
) else (
    echo [1/4] Fabric Loader %FL% already installed, skipping.
)

rem ----- 2. Fabric API (resolve URL dynamically) -----------------------------
echo [2/4] Resolving latest Fabric API for %MCVER% via Modrinth API...
for /f "usebackq delims=" %%u in (`powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12;" ^
  "$v = Invoke-RestMethod -Uri 'https://api.modrinth.com/v2/project/fabric-api/version?game_versions=%%5B%%22%MCVER%%%22%%5D&loaders=%%5B%%22fabric%%22%%5D';" ^
  "($v ^| Where-Object { $_.version_type -eq 'release' } ^| Select-Object -First 1).files[0].url"`) do set "API=%%u"

if "!API!"=="" (
    echo [!] Could not resolve Fabric API URL from Modrinth.
    echo     Download fabric-api.jar manually from https://modrinth.com/mod/fabric-api
    echo     and put it as %DIR%\mods\fabric-api.jar
    pause & exit /b 1
)
echo      URL: !API!
call :dl "!API!" "%DIR%\mods\fabric-api.jar" "Fabric API" || goto :net

rem ----- 3. maxdlc.jar (optional) --------------------------------------------
echo [3/4] Downloading maxDLC mod...
call :dl "%JAR%" "%DIR%\mods\maxdlc.jar" "maxDLC mod"
if not exist "%DIR%\mods\maxdlc.jar" (
    echo.
    echo     [!] maxdlc.jar is not published in the GitHub release yet.
    echo         Either wait for the release, or build locally:
    echo             git clone https://github.com/yh2dqznw7p-source/spft
    echo             cd spft
    echo             gradlew.bat build
    echo             copy build\libs\maxdlc-1.0.0.jar "%DIR%\mods\maxdlc.jar"
    echo         Continuing without it.
    echo.
)

rem ----- 4. Register launcher profile ----------------------------------------
echo [4/4] Registering launcher profile "maxDLC"...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$path = '%MC%\launcher_profiles.json';" ^
  "$p = Get-Content -Raw $path ^| ConvertFrom-Json;" ^
  "$now = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ss.fffZ');" ^
  "$e = [pscustomobject]@{ name='maxDLC'; type='custom'; created=$now; lastUsed=$now; lastVersionId='%VER%'; gameDir='%DIR:\=/%'; icon='Furnace_On'; javaArgs='-Xmx4G -Xms1G' };" ^
  "$p.profiles ^| Add-Member -NotePropertyName maxDLC -NotePropertyValue $e -Force;" ^
  "$p ^| ConvertTo-Json -Depth 20 ^| Set-Content $path -Encoding UTF8"

echo.
echo ============================================
echo   OK. Opening Minecraft Launcher...
echo   Pick profile "maxDLC" and click Play.
echo ============================================
echo.

start "" "shell:AppsFolder\Microsoft.4297127D64EC6_8wekyb3d8bbwe!Minecraft" 2>nul && exit /b 0
start "" "minecraft:" 2>nul && exit /b 0
if exist "%ProgramFiles(x86)%\Minecraft Launcher\MinecraftLauncher.exe" ( start "" "%ProgramFiles(x86)%\Minecraft Launcher\MinecraftLauncher.exe" & exit /b 0 )
if exist "%ProgramFiles%\Minecraft Launcher\MinecraftLauncher.exe"      ( start "" "%ProgramFiles%\Minecraft Launcher\MinecraftLauncher.exe"      & exit /b 0 )
echo Open Minecraft Launcher manually, pick profile "maxDLC".
pause & exit /b 0

rem --- helpers ---------------------------------------------------------------
:dl
set "_URL=%~1"
set "_OUT=%~2"
set "_LABEL=%~3"
echo      Downloading %_LABEL%...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12;" ^
  "try { Invoke-WebRequest -Uri '%_URL%' -OutFile '%_OUT%' -UseBasicParsing; exit 0 } catch { exit 1 }" >nul 2>&1
if errorlevel 1 curl -sSLf -o "%_OUT%" "%_URL%"
if not exist "%_OUT%" ( echo        [x] %_LABEL%: download failed ^(HTTP error^). & exit /b 1 )
for %%S in ("%_OUT%") do if %%~zS LSS 1024 ( echo        [x] %_LABEL%: file too small. & del "%_OUT%" & exit /b 1 )
echo        [ok] %_LABEL%
exit /b 0

:net
echo [!] Required download failed. Check your internet and retry. ^(v3^)
pause & exit /b 1

@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul

rem =============================================================
rem   maxDLC — self-installing launcher for Minecraft 1.21.4 Fabric
rem   - first run: copies itself to C:\maxDLC, installs Fabric,
rem     downloads fabric-api + maxdlc.jar, writes launcher profile
rem     pointing gameDir=C:\maxDLC
rem   - next runs: just opens the official Minecraft Launcher
rem
rem   Requires a purchased Microsoft Minecraft account and the
rem   official Minecraft Launcher already installed.
rem =============================================================

set "INSTALL_DIR=C:\maxDLC"
set "MC_DIR=%APPDATA%\.minecraft"
set "MC_VERSION=1.21.4"
set "FABRIC_LOADER=0.16.9"
set "FABRIC_INSTALLER=1.0.1"
set "PROFILE_ID=maxDLC"
set "VERSION_ID=fabric-loader-%FABRIC_LOADER%-%MC_VERSION%"

set "FABRIC_API_URL=https://cdn.modrinth.com/data/P7dR8mSH/versions/MSXtTJgZ/fabric-api-0.114.0%%2B1.21.4.jar"
set "MAXDLC_URL=https://github.com/yh2dqznw7p-source/spft/releases/latest/download/maxdlc-1.0.0.jar"
set "FI_URL=https://maven.fabricmc.net/net/fabricmc/fabric-installer/%FABRIC_INSTALLER%/fabric-installer-%FABRIC_INSTALLER%.jar"

title maxDLC

rem --- relocate ourselves to C:\maxDLC ---------------------------------------

if /i not "%~dp0"=="%INSTALL_DIR%\" (
    mkdir "%INSTALL_DIR%" 2>nul
    if not exist "%INSTALL_DIR%" (
        echo [!] Cannot create %INSTALL_DIR%, falling back to LocalAppData
        set "INSTALL_DIR=%LOCALAPPDATA%\maxDLC"
        mkdir "!INSTALL_DIR!" 2>nul
    )
    copy /Y "%~f0" "!INSTALL_DIR!\maxDLC.bat" >nul
    start "maxDLC" /d "!INSTALL_DIR!" "!INSTALL_DIR!\maxDLC.bat"
    exit /b
)

rem --- first-run setup -------------------------------------------------------

if not exist "%INSTALL_DIR%\.installed" (
    call :first_run
    if errorlevel 1 exit /b 1
) else (
    rem Always re-patch launcher_profiles.json to be safe (idempotent)
    call :patch_profile >nul 2>&1
)

call :launch
exit /b 0

rem ===========================================================================
:first_run
echo.
echo ============================================
echo   maxDLC first-run setup
echo ============================================
echo Install dir : %INSTALL_DIR%
echo MC folder   : %MC_DIR%
echo Version     : %VERSION_ID%
echo.

where java >nul 2>&1 || (
    echo [!] Java 21 is required but not found in PATH.
    echo     Install Temurin 21 from:  https://adoptium.net/temurin/releases/?version=21
    pause
    exit /b 1
)

if not exist "%MC_DIR%" (
    echo [!] %MC_DIR% not found.
    echo     Run the official Minecraft Launcher at least once to create it.
    pause
    exit /b 1
)

mkdir "%INSTALL_DIR%\mods"           2>nul
mkdir "%INSTALL_DIR%\saves"          2>nul
mkdir "%INSTALL_DIR%\resourcepacks"  2>nul
mkdir "%INSTALL_DIR%\screenshots"    2>nul
mkdir "%INSTALL_DIR%\config"         2>nul

set "FI_JAR=%INSTALL_DIR%\fabric-installer.jar"
echo [1/5] Downloading Fabric installer %FABRIC_INSTALLER%...
call :dl "%FI_URL%" "%FI_JAR%" || goto :err_net

echo [2/5] Installing Fabric Loader %FABRIC_LOADER% for MC %MC_VERSION%...
java -jar "%FI_JAR%" client -mcversion %MC_VERSION% -loader %FABRIC_LOADER% -dir "%MC_DIR%" -noprofile
if errorlevel 1 (
    echo [!] Fabric installer failed.
    pause
    exit /b 1
)

echo [3/5] Downloading Fabric API...
call :dl "%FABRIC_API_URL%" "%INSTALL_DIR%\mods\fabric-api.jar" || goto :err_net

echo [4/5] Downloading maxDLC...
call :dl "%MAXDLC_URL%" "%INSTALL_DIR%\mods\maxdlc.jar"
if not exist "%INSTALL_DIR%\mods\maxdlc.jar" (
    echo     [!] maxdlc.jar not found in GitHub release yet.
    echo         Build it yourself and drop into %INSTALL_DIR%\mods\maxdlc.jar
)

echo [5/5] Adding '%PROFILE_ID%' profile to Minecraft Launcher...
call :patch_profile

echo. > "%INSTALL_DIR%\.installed"
echo.
echo ============================================
echo   Setup complete.
echo   Profile 'maxDLC' added to Minecraft Launcher.
echo ============================================
timeout /t 2 >nul
exit /b 0

rem ===========================================================================
:launch
echo Opening Minecraft Launcher...
rem 1) MSIX / Store
start "" "shell:AppsFolder\Microsoft.4297127D64EC6_8wekyb3d8bbwe!Minecraft" 2>nul && exit /b 0
rem 2) minecraft:// URL handler
start "" "minecraft:" 2>nul && exit /b 0
rem 3) classic exe
if exist "%ProgramFiles(x86)%\Minecraft Launcher\MinecraftLauncher.exe" (
    start "" "%ProgramFiles(x86)%\Minecraft Launcher\MinecraftLauncher.exe"
    exit /b 0
)
if exist "%ProgramFiles%\Minecraft Launcher\MinecraftLauncher.exe" (
    start "" "%ProgramFiles%\Minecraft Launcher\MinecraftLauncher.exe"
    exit /b 0
)
echo [!] Minecraft Launcher not found. Open it manually and pick profile '%PROFILE_ID%'.
pause
exit /b 0

rem ===========================================================================
:patch_profile
set "PROFILES=%MC_DIR%\launcher_profiles.json"
if not exist "%PROFILES%" (
    echo [!] launcher_profiles.json not found — open Minecraft Launcher once, then rerun.
    exit /b 0
)
set "GAMEDIR=%INSTALL_DIR:\=/%"

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$path='%PROFILES%';" ^
  "$p = Get-Content -Raw $path | ConvertFrom-Json;" ^
  "if (-not $p.profiles) { $p | Add-Member -NotePropertyName profiles -NotePropertyValue ([pscustomobject]@{}) -Force };" ^
  "$now = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ss.fffZ');" ^
  "$entry = [pscustomobject]@{ name='%PROFILE_ID%'; type='custom'; created=$now; lastUsed=$now; lastVersionId='%VERSION_ID%'; gameDir='%GAMEDIR%'; icon='Furnace_On'; javaArgs='-Xmx4G -Xms1G' };" ^
  "$p.profiles | Add-Member -NotePropertyName '%PROFILE_ID%' -NotePropertyValue $entry -Force;" ^
  "$p | ConvertTo-Json -Depth 20 | Set-Content -Path $path -Encoding UTF8"
exit /b 0

rem ===========================================================================
:dl
rem  %1 = URL, %2 = destination path
set "_URL=%~1"
set "_OUT=%~2"
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference='Stop'; [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%_URL%' -OutFile '%_OUT%' -UseBasicParsing" >nul 2>&1
if errorlevel 1 curl -sSLf -o "%_OUT%" "%_URL%"
if not exist "%_OUT%" exit /b 1
for %%S in ("%_OUT%") do if %%~zS LSS 1024 exit /b 1
exit /b 0

:err_net
echo [!] Download failed. Check internet and retry.
pause
exit /b 1

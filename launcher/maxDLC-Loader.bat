@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 > nul

rem ============================================================
rem   maxDLC Loader for Minecraft 1.21.4 (Fabric)
rem   - installs Fabric Loader 0.16.9 for MC 1.21.4
rem   - drops Fabric API, maxDLC and optional optimization mods
rem     into %APPDATA%\.minecraft\mods
rem   NOTE: use only on your own single-player worlds / private
rem         servers where mods are allowed.
rem ============================================================

title maxDLC Loader

rem --- config ----------------------------------------------------------------

set "MC_VERSION=1.21.4"
set "FABRIC_LOADER=0.16.9"
set "FABRIC_INSTALLER=1.0.1"

set "FABRIC_API_URL=https://cdn.modrinth.com/data/P7dR8mSH/versions/MSXtTJgZ/fabric-api-0.114.0%%2B1.21.4.jar"
set "MAXDLC_URL=https://github.com/yh2dqznw7p-source/spft/releases/latest/download/maxdlc-1.0.0.jar"

set "SODIUM_URL=https://cdn.modrinth.com/data/AANobbMI/versions/m6cpiOZr/sodium-fabric-0.6.5%%2Bmc1.21.4.jar"
set "LITHIUM_URL=https://cdn.modrinth.com/data/gvQqBUqZ/versions/7iA7nDb1/lithium-fabric-0.14.1-mc1.21.4.jar"
set "FERRITE_URL=https://cdn.modrinth.com/data/uXXizFIs/versions/PxPZiDIl/ferritecore-7.0.2-fabric.jar"

set "MC_DIR=%APPDATA%\.minecraft"
set "MODS_DIR=%MC_DIR%\mods"
set "TMP_DIR=%TEMP%\maxdlc-loader"

rem --- intro -----------------------------------------------------------------

echo.
echo ============================================
echo   maxDLC Loader (Minecraft %MC_VERSION%)
echo ============================================
echo.
echo Target: %MC_DIR%
echo.

if not exist "%MC_DIR%" (
    echo [!] Minecraft folder not found: %MC_DIR%
    echo     Run the official Minecraft Launcher at least once.
    pause
    exit /b 1
)

if not exist "%MODS_DIR%" mkdir "%MODS_DIR%" >nul 2>&1
if not exist "%TMP_DIR%" mkdir "%TMP_DIR%" >nul 2>&1

rem --- find Java -------------------------------------------------------------

set "JAVA_BIN=java"
where java >nul 2>&1 || (
    echo [!] Java is not in PATH.
    echo     Install Java 21 ^(Adoptium / Temurin^) and rerun.
    pause
    exit /b 1
)

for /f "tokens=*" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JVER=%%v"
echo Java found: %JVER%
echo.

rem --- download Fabric installer --------------------------------------------

set "FI_JAR=%TMP_DIR%\fabric-installer.jar"
set "FI_URL=https://maven.fabricmc.net/net/fabricmc/fabric-installer/%FABRIC_INSTALLER%/fabric-installer-%FABRIC_INSTALLER%.jar"

echo [1/5] Downloading Fabric installer %FABRIC_INSTALLER%...
call :download "%FI_URL%" "%FI_JAR%" || goto :err_dl

echo [2/5] Installing Fabric Loader %FABRIC_LOADER% for Minecraft %MC_VERSION%...
"%JAVA_BIN%" -jar "%FI_JAR%" client -mcversion %MC_VERSION% -loader %FABRIC_LOADER% -dir "%MC_DIR%" -noprofile
if errorlevel 1 (
    echo [!] Fabric installer failed.
    pause
    exit /b 1
)

rem Manually ensure launcher profile exists (fabric installer creates it by default,
rem but we passed -noprofile; add minimal profile ourselves via JSON if missing)

rem --- required mods ---------------------------------------------------------

echo.
echo [3/5] Downloading Fabric API...
call :download "%FABRIC_API_URL%" "%MODS_DIR%\fabric-api.jar" || goto :err_dl

echo [4/5] Downloading maxDLC...
call :download "%MAXDLC_URL%" "%MODS_DIR%\maxdlc.jar" || goto :err_no_release

rem --- optional optimizations ------------------------------------------------

echo.
set /p WANT_OPT="Install optimization mods (Sodium+Lithium+FerriteCore)? [Y/n]: "
if /i "%WANT_OPT%"=="n" goto :skip_opt
if /i "%WANT_OPT%"=="no" goto :skip_opt

echo [5/5] Downloading Sodium...
call :download "%SODIUM_URL%"  "%MODS_DIR%\sodium.jar"      || echo     (skip sodium)
echo      Downloading Lithium...
call :download "%LITHIUM_URL%" "%MODS_DIR%\lithium.jar"     || echo     (skip lithium)
echo      Downloading FerriteCore...
call :download "%FERRITE_URL%" "%MODS_DIR%\ferritecore.jar" || echo     (skip ferritecore)

:skip_opt

echo.
echo ============================================
echo   Done.
echo   1. Open Minecraft Launcher
echo   2. Choose profile: fabric-loader-%MC_VERSION%
echo   3. Play. ClickGUI opens on RIGHT_SHIFT.
echo ============================================
echo.
start "" "%MODS_DIR%"
pause
exit /b 0

rem ===========================================================================
rem   helpers
rem ===========================================================================

:download
rem  %1 = URL, %2 = destination path
set "_URL=%~1"
set "_OUT=%~2"
rem try PowerShell first (TLS 1.2)
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference='Stop'; [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%_URL%' -OutFile '%_OUT%' -UseBasicParsing" >nul 2>&1
if errorlevel 1 (
    rem fallback to curl (Windows 10+ ships with curl.exe)
    curl -sSLf -o "%_OUT%" "%_URL%"
)
if not exist "%_OUT%" exit /b 1
for %%S in ("%_OUT%") do if %%~zS LSS 1024 exit /b 1
exit /b 0

:err_dl
echo [!] Download failed. Check your internet connection.
pause
exit /b 1

:err_no_release
echo.
echo [!] maxdlc.jar could not be downloaded from the GitHub release.
echo     Probably the release is not published yet. Build it yourself:
echo         git clone https://github.com/yh2dqznw7p-source/spft
echo         cd spft
echo         gradlew.bat build
echo         copy build\libs\maxdlc-1.0.0.jar "%MODS_DIR%\maxdlc.jar"
echo.
pause
exit /b 1

:err_no_release
echo.
echo [!] maxdlc.jar could not be downloaded.
echo     Probably the GitHub release is not published yet.
echo     Build it yourself:
echo         git clone https://github.com/yh2dqznw7p-source/spft
echo         cd spft
echo         gradlew.bat build
echo         copy build\libs\maxdlc-1.0.0.jar "%MODS_DIR%\maxdlc.jar"
echo.
pause
exit /b 1

@echo off
setlocal EnableExtensions
chcp 65001 >nul
title maxDLC

set "DIR=C:\maxDLC"
set "MC=%APPDATA%\.minecraft"
set "MCVER=1.21.4"
set "FL=0.16.9"
set "VER=fabric-loader-%FL%-%MCVER%"

set "FI=https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.0.1/fabric-installer-1.0.1.jar"
set "API=https://cdn.modrinth.com/data/P7dR8mSH/versions/MSXtTJgZ/fabric-api-0.114.0%%2B1.21.4.jar"
set "JAR=https://github.com/yh2dqznw7p-source/spft/releases/latest/download/maxdlc-1.0.0.jar"

if exist "%DIR%\.installed" goto :run

echo [maxDLC] First-time setup, please wait...
mkdir "%DIR%\mods" 2>nul

where java >nul 2>&1 || ( echo Install Java 21 and retry. & pause & exit /b 1 )
if not exist "%MC%" ( echo Run Minecraft Launcher once, then retry. & pause & exit /b 1 )

call :get "%FI%"  "%DIR%\fabric-installer.jar"   || goto :net
java -jar "%DIR%\fabric-installer.jar" client -mcversion %MCVER% -loader %FL% -dir "%MC%" -noprofile || ( echo Fabric install failed & pause & exit /b 1 )
call :get "%API%" "%DIR%\mods\fabric-api.jar"    || goto :net
call :get "%JAR%" "%DIR%\mods\maxdlc.jar"        || echo     (maxdlc.jar not in release yet - drop it manually)

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$p=Get-Content -Raw '%MC%\launcher_profiles.json' | ConvertFrom-Json;" ^
  "$now=(Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ss.fffZ');" ^
  "$e=[pscustomobject]@{name='maxDLC';type='custom';created=$now;lastUsed=$now;lastVersionId='%VER%';gameDir='%DIR:\=/%';icon='Furnace_On';javaArgs='-Xmx4G -Xms1G'};" ^
  "$p.profiles | Add-Member -NotePropertyName maxDLC -NotePropertyValue $e -Force;" ^
  "$p | ConvertTo-Json -Depth 20 | Set-Content '%MC%\launcher_profiles.json' -Encoding UTF8"

echo.> "%DIR%\.installed"
echo [maxDLC] Done.

:run
echo [maxDLC] Opening Minecraft Launcher... pick profile "maxDLC" and click Play.
start "" "shell:AppsFolder\Microsoft.4297127D64EC6_8wekyb3d8bbwe!Minecraft" 2>nul && exit /b 0
start "" "minecraft:" 2>nul && exit /b 0
if exist "%ProgramFiles(x86)%\Minecraft Launcher\MinecraftLauncher.exe" start "" "%ProgramFiles(x86)%\Minecraft Launcher\MinecraftLauncher.exe" & exit /b 0
if exist "%ProgramFiles%\Minecraft Launcher\MinecraftLauncher.exe"      start "" "%ProgramFiles%\Minecraft Launcher\MinecraftLauncher.exe"      & exit /b 0
echo Open Minecraft Launcher manually and pick profile "maxDLC".
pause & exit /b 0

:get
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
 "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%~1' -OutFile '%~2' -UseBasicParsing" >nul 2>&1
if errorlevel 1 curl -sSLf -o "%~2" "%~1"
if not exist "%~2" exit /b 1
for %%S in ("%~2") do if %%~zS LSS 1024 exit /b 1
exit /b 0

:net
echo No internet, try again.
pause & exit /b 1

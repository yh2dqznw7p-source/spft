# build.ps1 - SPFT (maxDLC) auto-builder.
#
# Run in PowerShell (one line):
#   iwr "https://raw.githubusercontent.com/yh2dqznw7p-source/spft/incoming/build.ps1?nocache=$(Get-Random)" -UseBasicParsing | iex
#
# What it does:
#   1. installs git via winget/choco if missing
#   2. installs Temurin JDK 21 if missing
#   3. clones (or updates) repo into %USERPROFILE%\spft-build on 'incoming' branch
#   4. generates gradle wrapper (downloads Gradle if needed)
#   5. runs .\gradlew build
#   6. copies built jar into %APPDATA%\.minecraft\mods\maxDLC.jar
#   7. downloads Fabric API 1.21.4 if not present
#
# NOTE: do NOT use exit here. This script is usually piped into iex,
# where 'exit' kills the whole PowerShell window and the user cannot
# see the error. Instead we throw/return and print a clear message.

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'Continue'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}

$REPO_URL    = 'https://github.com/yh2dqznw7p-source/spft.git'
$BRANCH      = 'incoming'
$WORK_DIR    = Join-Path $env:USERPROFILE 'spft-build'
$MODS_DIR    = Join-Path $env:APPDATA '.minecraft\mods'
$OUT_JAR     = Join-Path $MODS_DIR 'maxDLC.jar'
$FABRIC_URL  = 'https://cdn.modrinth.com/data/P7dR8mSH/versions/rH00tDfh/fabric-api-0.118.0%2B1.21.4.jar'
$FABRIC_NAME = 'fabric-api-0.118.0+1.21.4.jar'

function Say($m,$c='Cyan'){ Write-Host "[SPFT] $m" -ForegroundColor $c }
function Fail($m){ Write-Host "[SPFT][FAIL] $m" -ForegroundColor Red; throw $m }

function DL($url,$out,$label){
    Say "downloading $label"
    if (Get-Command curl.exe -ErrorAction SilentlyContinue) {
        & curl.exe -L --fail --retry 3 --progress-bar -o $out $url
        if ($LASTEXITCODE -ne 0) { Fail "download failed: $url" }
    } else {
        Invoke-WebRequest -Uri $url -OutFile $out -UseBasicParsing
    }
}

function Refresh-Path {
    $env:Path = [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')
}

function Ensure-Git {
    if (Get-Command git -ErrorAction SilentlyContinue) { Say 'git: OK'; return }
    Say 'git not found, installing...' 'Yellow'
    if (Get-Command winget -ErrorAction SilentlyContinue) {
        & winget install --id Git.Git -e --silent --accept-source-agreements --accept-package-agreements | Out-Null
    } elseif (Get-Command choco -ErrorAction SilentlyContinue) {
        & choco install git -y | Out-Null
    } else {
        Fail 'no winget/choco. install git manually: https://git-scm.com/download/win then re-run.'
    }
    Refresh-Path
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
        Fail 'git installed but PATH not refreshed. CLOSE PowerShell and run script again.'
    }
    Say 'git: installed'
}

function Ensure-Java {
    $need = $true
    if (Get-Command java -ErrorAction SilentlyContinue) {
        $out = ''
        try {
            $prev = $ErrorActionPreference
            $ErrorActionPreference = 'SilentlyContinue'
            $out = (& cmd /c 'java -version 2>&1') -join "`n"
            $ErrorActionPreference = $prev
        } catch { $out = '' }
        $m = [regex]::Match($out,'version "(\d+)')
        if ($m.Success -and [int]$m.Groups[1].Value -ge 21) {
            Say "java: OK ($($m.Groups[1].Value))"; $need = $false
        } else {
            Say 'java found but not 21+, installing Temurin 21' 'Yellow'
        }
    } else {
        Say 'java not found, installing Temurin 21' 'Yellow'
    }
    if (-not $need) { return }
    if (Get-Command winget -ErrorAction SilentlyContinue) {
        & winget install --id EclipseAdoptium.Temurin.21.JDK -e --silent --accept-source-agreements --accept-package-agreements | Out-Null
    } elseif (Get-Command choco -ErrorAction SilentlyContinue) {
        & choco install temurin21 -y | Out-Null
    } else {
        Fail 'install JDK 21 manually: https://adoptium.net/temurin/releases/?version=21'
    }
    Refresh-Path
    $env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME','Machine')
    if (-not $env:JAVA_HOME) {
        $m = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -ErrorAction SilentlyContinue |
             Where-Object Name -match 'jdk-21' | Select-Object -First 1
        if ($m) { $env:JAVA_HOME = $m.FullName; $env:Path = "$env:JAVA_HOME\bin;$env:Path" }
    }
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
        Fail 'Java installed but PATH not refreshed. CLOSE PowerShell and re-run.'
    }
    Say 'java: installed'
}

function Main {
    Say '============================================'
    Say '  SPFT / maxDLC auto-builder'
    Say '============================================'

    Ensure-Git
    Ensure-Java

    # try to update existing clone. If anything goes wrong (missing branch,
    # partial clone, shallow-vs-non-shallow mismatch), just nuke and reclone.
    $needClone = $true
    if (Test-Path (Join-Path $WORK_DIR '.git')) {
        Say "updating repo in $WORK_DIR"
        Push-Location $WORK_DIR
        try {
            $prev = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            & git fetch origin $BRANCH *>&1 | Out-String | Out-Null
            $fetchOk = ($LASTEXITCODE -eq 0)
            if ($fetchOk) {
                & git checkout -B $BRANCH "origin/$BRANCH" *>&1 | Out-String | Out-Null
                $coOk = ($LASTEXITCODE -eq 0)
                if ($coOk) {
                    & git reset --hard "origin/$BRANCH" *>&1 | Out-String | Out-Null
                    if ($LASTEXITCODE -eq 0) { $needClone = $false }
                }
            }
            $ErrorActionPreference = $prev
        } finally { Pop-Location }
        if ($needClone) {
            Say 'local repo is in a weird state, wiping and recloning' 'Yellow'
        }
    }

    if ($needClone) {
        if (Test-Path $WORK_DIR) { Remove-Item $WORK_DIR -Recurse -Force }
        Say "cloning into $WORK_DIR"
        # git clone writes "Cloning into '...'" to STDERR, so under
        # ErrorActionPreference=Stop PS treats it as a terminating error.
        # Switch to Continue for the duration of this command.
        $prev = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & cmd /c "git clone --branch $BRANCH --depth 1 $REPO_URL `"$WORK_DIR`" 2>&1"
        $cloneExit = $LASTEXITCODE
        $ErrorActionPreference = $prev
        if ($cloneExit -ne 0) { Fail "git clone failed (exit $cloneExit)" }
    }

    Push-Location $WORK_DIR
    try {
        if (-not (Test-Path '.\gradlew.bat')) {
            Say 'generating gradle wrapper'
            if (-not (Get-Command gradle -ErrorAction SilentlyContinue)) {
                Say 'gradle not installed, downloading distribution (~130 MB)' 'Yellow'
                $zip = Join-Path $env:TEMP 'gradle-8.10-bin.zip'
                DL 'https://services.gradle.org/distributions/gradle-8.10-bin.zip' $zip 'Gradle 8.10'
                $dir = Join-Path $env:USERPROFILE 'spft-gradle'
                if (Test-Path $dir) { Remove-Item $dir -Recurse -Force }
                Say 'extracting Gradle...'
                Expand-Archive $zip -DestinationPath $dir
                $bin = (Get-ChildItem $dir -Directory | Select-Object -First 1).FullName + '\bin'
                $env:Path = "$bin;$env:Path"
            }
            Say 'running gradle wrapper...'
            & cmd /c 'gradle wrapper --gradle-version 8.10 --no-daemon 2>&1'
            if ($LASTEXITCODE -ne 0 -or -not (Test-Path '.\gradlew.bat')) { Fail 'gradle wrapper did not create gradlew.bat' }
        }

        Say 'building (first run is slow: 5-15 min, downloading Minecraft + Fabric)' 'Yellow'
        & cmd /c '.\gradlew.bat build --no-daemon 2>&1'
        if ($LASTEXITCODE -ne 0) { Fail 'build failed (see log above)' }

        $jar = Get-ChildItem 'build\libs' -Filter 'spft-*.jar' -ErrorAction SilentlyContinue |
               Where-Object Name -notmatch '(sources|dev|javadoc)' | Select-Object -First 1
        if (-not $jar) { Fail 'no jar in build\libs' }

        if (-not (Test-Path $MODS_DIR)) { New-Item -ItemType Directory -Path $MODS_DIR -Force | Out-Null }
        Copy-Item $jar.FullName $OUT_JAR -Force
        Say "installed: $OUT_JAR" 'Green'
    } finally { Pop-Location }

    $hasFabric = Get-ChildItem $MODS_DIR -Filter 'fabric-api-*.jar' -ErrorAction SilentlyContinue
    if (-not $hasFabric) {
        try {
            DL $FABRIC_URL (Join-Path $MODS_DIR $FABRIC_NAME) 'Fabric API 0.118.0 (MC 1.21.4)'
            Say 'Fabric API: installed' 'Green'
        } catch {
            Say 'Fabric API download failed. install manually: https://modrinth.com/mod/fabric-api' 'Yellow'
        }
    } else {
        Say "Fabric API: already present ($($hasFabric.Name))" 'Green'
    }

    Say ''
    Say '============================================' 'Green'
    Say '  DONE' 'Green'
    Say "  JAR: $OUT_JAR" 'Green'
    Say '  Also install Fabric Loader 0.16+ for MC 1.21.4:' 'Green'
    Say '    https://fabricmc.net/use/installer/' 'Yellow'
    Say '============================================' 'Green'
}

try {
    Main
} catch {
    Write-Host ''
    Write-Host "[SPFT][FAIL] $($_.Exception.Message)" -ForegroundColor Red
    Write-Host ''
}
Write-Host 'Press Enter to close...' -ForegroundColor Gray
try { [void][System.Console]::ReadLine() } catch {}

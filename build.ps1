# build.ps1 - SPFT (maxDLC) auto-builder.
#
# Run in PowerShell (one line):
#   iwr "https://raw.githubusercontent.com/yh2dqznw7p-source/spft/incoming/build.ps1?nocache=$(Get-Random)" -UseBasicParsing | iex
#
# NOTES
#   - NO 'exit' and NO ErrorActionPreference=Stop here. Script is piped into iex,
#     any terminating error would kill the whole window before user sees the error.
#   - Native commands (git, curl, winget, gradle) print to stderr even on success.
#     We never trust stderr, only $LASTEXITCODE.
#   - Big downloads go through BITS (Start-BitsTransfer) which has a native
#     Windows progress bar and never touches stderr.

# IMPORTANT: leave default 'Continue' so stderr from native apps stays informational.
$ErrorActionPreference = 'Continue'
$ProgressPreference    = 'Continue'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}

$REPO_URL    = 'https://github.com/yh2dqznw7p-source/spft.git'
$BRANCH      = 'incoming'
$WORK_DIR    = Join-Path $env:USERPROFILE 'spft-build'
# Build output goes to the user's Desktop (both English and Russian path variants checked).
$DESKTOP = [Environment]::GetFolderPath('Desktop')
if (-not $DESKTOP -or -not (Test-Path $DESKTOP)) {
    $DESKTOP = Join-Path $env:USERPROFILE 'Desktop'
}
# if even that is missing, just pick user home — no non-ASCII strings here,
# because the script itself would get mis-parsed if saved in a different codepage.
if (-not (Test-Path $DESKTOP)) { $DESKTOP = $env:USERPROFILE }
$OUT_JAR     = Join-Path $DESKTOP 'maxDLC.jar'

$Global:SPFT_FAILED = $false
function Say($m,$c='Cyan'){ Write-Host "[SPFT] $m" -ForegroundColor $c }
function Fail($m){
    Write-Host "[SPFT][FAIL] $m" -ForegroundColor Red
    $Global:SPFT_FAILED = $true
}

function DL($url,$out,$label){
    Say "downloading $label"
    $ok = $false
    # Try BITS first (nice progress, no stderr nonsense).
    try {
        Import-Module BitsTransfer -ErrorAction SilentlyContinue
        if (Get-Command Start-BitsTransfer -ErrorAction SilentlyContinue) {
            Start-BitsTransfer -Source $url -Destination $out -ErrorAction Stop
            $ok = $true
        }
    } catch { $ok = $false }
    if (-not $ok) {
        try {
            Invoke-WebRequest -Uri $url -OutFile $out -UseBasicParsing -ErrorAction Stop
            $ok = $true
        } catch { $ok = $false }
    }
    if (-not $ok -or -not (Test-Path $out)) {
        Fail "download failed: $url"
        return $false
    }
    return $true
}

function Refresh-Path {
    $env:Path = [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')
}

function Run-Cmd($cmdline) {
    # Run a native command and return $LASTEXITCODE; never throws.
    & cmd /c "$cmdline"
    return $LASTEXITCODE
}

function Ensure-Git {
    if (Get-Command git -ErrorAction SilentlyContinue) { Say 'git: OK'; return $true }
    Say 'git not found, installing...' 'Yellow'
    if (Get-Command winget -ErrorAction SilentlyContinue) {
        Run-Cmd 'winget install --id Git.Git -e --silent --accept-source-agreements --accept-package-agreements' | Out-Null
    } elseif (Get-Command choco -ErrorAction SilentlyContinue) {
        Run-Cmd 'choco install git -y' | Out-Null
    } else {
        Fail 'no winget/choco. install git manually: https://git-scm.com/download/win then re-run.'
        return $false
    }
    Refresh-Path
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
        Fail 'git installed but PATH not refreshed. CLOSE PowerShell and run script again.'
        return $false
    }
    Say 'git: installed'
    return $true
}

function Ensure-Java {
    $need = $true
    if (Get-Command java -ErrorAction SilentlyContinue) {
        $out = (& cmd /c 'java -version 2>&1') -join "`n"
        $m = [regex]::Match($out,'version "(\d+)')
        if ($m.Success -and [int]$m.Groups[1].Value -ge 21) {
            Say "java: OK ($($m.Groups[1].Value))"
            $need = $false
        } else {
            Say 'java found but not 21+, installing Temurin 21' 'Yellow'
        }
    } else {
        Say 'java not found, installing Temurin 21' 'Yellow'
    }
    if (-not $need) { return $true }

    if (Get-Command winget -ErrorAction SilentlyContinue) {
        Run-Cmd 'winget install --id EclipseAdoptium.Temurin.21.JDK -e --silent --accept-source-agreements --accept-package-agreements' | Out-Null
    } elseif (Get-Command choco -ErrorAction SilentlyContinue) {
        Run-Cmd 'choco install temurin21 -y' | Out-Null
    } else {
        Fail 'install JDK 21 manually: https://adoptium.net/temurin/releases/?version=21'
        return $false
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
        return $false
    }
    Say 'java: installed'
    return $true
}

function Main {
    Say '============================================'
    Say '  SPFT / maxDLC auto-builder'
    Say '============================================'

    if (-not (Ensure-Git))  { return }
    if (-not (Ensure-Java)) { return }

    # reuse existing clone if possible, otherwise wipe & reclone
    $needClone = $true
    if (Test-Path (Join-Path $WORK_DIR '.git')) {
        Say "updating repo in $WORK_DIR"
        Push-Location $WORK_DIR
        Run-Cmd "git fetch origin $BRANCH 2>&1" | Out-Null
        $okF = ($LASTEXITCODE -eq 0)
        if ($okF) {
            Run-Cmd "git checkout -B $BRANCH origin/$BRANCH 2>&1" | Out-Null
            if ($LASTEXITCODE -eq 0) {
                Run-Cmd "git reset --hard origin/$BRANCH 2>&1" | Out-Null
                if ($LASTEXITCODE -eq 0) { $needClone = $false }
            }
        }
        Pop-Location
        if ($needClone) { Say 'local repo is in a weird state, wiping and recloning' 'Yellow' }
    }

    if ($needClone) {
        if (Test-Path $WORK_DIR) { Remove-Item $WORK_DIR -Recurse -Force }
        Say "cloning into $WORK_DIR"
        Run-Cmd "git clone --branch $BRANCH --depth 1 $REPO_URL `"$WORK_DIR`" 2>&1" | Out-Null
        if ($LASTEXITCODE -ne 0) { Fail "git clone failed (exit $LASTEXITCODE)"; return }
    }

    Push-Location $WORK_DIR
    try {
        if (-not (Test-Path '.\gradlew.bat')) {
            Say 'generating gradle wrapper'
            if (-not (Get-Command gradle -ErrorAction SilentlyContinue)) {
                Say 'gradle not installed, downloading distribution (~130 MB)' 'Yellow'
                $zip = Join-Path $env:TEMP 'gradle-8.10-bin.zip'
                if (-not (DL 'https://services.gradle.org/distributions/gradle-8.10-bin.zip' $zip 'Gradle 8.10')) { return }
                $dir = Join-Path $env:USERPROFILE 'spft-gradle'
                if (Test-Path $dir) { Remove-Item $dir -Recurse -Force }
                Say 'extracting Gradle...'
                Expand-Archive $zip -DestinationPath $dir -Force
                $bin = (Get-ChildItem $dir -Directory | Select-Object -First 1).FullName + '\bin'
                $env:Path = "$bin;$env:Path"
            }
            Say 'running gradle wrapper...'
            Run-Cmd 'gradle wrapper --gradle-version 8.10 --no-daemon 2>&1'
            if (-not (Test-Path '.\gradlew.bat')) { Fail 'gradle wrapper did not create gradlew.bat'; return }
        }

        Say 'building (first run is slow: 5-15 min, downloading Minecraft + Fabric)' 'Yellow'
        Run-Cmd '.\gradlew.bat build --no-daemon 2>&1'
        if ($LASTEXITCODE -ne 0) { Fail 'build failed (see log above)'; return }

        $jar = Get-ChildItem 'build\libs' -Filter 'spft-*.jar' -ErrorAction SilentlyContinue |
               Where-Object Name -notmatch '(sources|dev|javadoc)' | Select-Object -First 1
        if (-not $jar) { Fail 'no jar in build\libs'; return }

        if (-not (Test-Path $DESKTOP)) { New-Item -ItemType Directory -Path $DESKTOP -Force | Out-Null }
        Copy-Item $jar.FullName $OUT_JAR -Force
        Say "jar placed on Desktop: $OUT_JAR" 'Green'
    } finally { Pop-Location }

    Say ''
    Say '============================================' 'Green'
    Say '  DONE' 'Green'
    Say "  JAR: $OUT_JAR" 'Green'
    Say '  To run, drag it to %APPDATA%\.minecraft\mods\' 'Green'
    Say '  You also need Fabric Loader 0.16+ + Fabric API for MC 1.21.4:' 'Green'
    Say '    https://fabricmc.net/use/installer/' 'Yellow'
    Say '    https://modrinth.com/mod/fabric-api' 'Yellow'
    Say '============================================' 'Green'
}

Main

Write-Host ''
if ($Global:SPFT_FAILED) {
    Write-Host '[SPFT] finished with errors (see above)' -ForegroundColor Red
} else {
    Write-Host '[SPFT] finished OK' -ForegroundColor Green
}
Write-Host 'Press Enter to close...' -ForegroundColor Gray
try { [void][System.Console]::ReadLine() } catch {}

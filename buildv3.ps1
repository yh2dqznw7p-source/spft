# buildv3.ps1 - SPFT (maxDLC) auto-builder.
# v3: reuse existing Gradle if user already has it, extract zip via .NET ZipFile
# (Expand-Archive is painfully slow on 130MB on PS5.1), verbose progress logging.

$ErrorActionPreference = 'Continue'
$ProgressPreference    = 'Continue'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}

$REPO_URL = 'https://github.com/yh2dqznw7p-source/spft.git'
$BRANCH   = 'incoming'
$WORK_DIR = Join-Path $env:USERPROFILE 'spft-build'

$DESKTOP = [Environment]::GetFolderPath('Desktop')
if (-not $DESKTOP -or -not (Test-Path $DESKTOP)) { $DESKTOP = Join-Path $env:USERPROFILE 'Desktop' }
if (-not (Test-Path $DESKTOP)) { $DESKTOP = $env:USERPROFILE }
$OUT_JAR = Join-Path $DESKTOP 'maxDLC.jar'

$Global:SPFT_FAILED = $false
function Say($m,$c='Cyan'){ Write-Host "[SPFT] $m" -ForegroundColor $c }
function Fail($m){ Write-Host "[SPFT][FAIL] $m" -ForegroundColor Red; $Global:SPFT_FAILED = $true }

function DL($url,$out,$label){
    Say "downloading $label"
    Say "  url: $url"
    Say "  dst: $out"
    if (Test-Path $out) { Remove-Item $out -Force -ErrorAction SilentlyContinue }
    try {
        [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12
        $wc = New-Object System.Net.WebClient
        $wc.Headers.Add('User-Agent','Mozilla/5.0')
        $wc.DownloadFile($url, $out)
        $wc.Dispose()
    } catch {
        Fail "download failed: $($_.Exception.Message)"
        return $false
    }
    if (-not (Test-Path $out)) { Fail 'no file after download'; return $false }
    $size = (Get-Item $out).Length
    if ($size -lt 1024) { Fail "file too small: $size bytes"; return $false }
    Say "  OK, $([math]::Round($size / 1MB, 1)) MB"
    return $true
}

function Refresh-Path {
    $env:Path = [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')
}

function Run-Cmd($cmdline) {
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
        Fail 'install git manually: https://git-scm.com/download/win'; return $false
    }
    Refresh-Path
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
        Fail 'git installed but PATH not refreshed. Close PowerShell and re-run.'; return $false
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
            Say "java: OK ($($m.Groups[1].Value))"; $need = $false
        } else { Say 'java is not 21+, installing Temurin 21' 'Yellow' }
    } else { Say 'java not found, installing Temurin 21' 'Yellow' }
    if (-not $need) { return $true }
    if (Get-Command winget -ErrorAction SilentlyContinue) {
        Run-Cmd 'winget install --id EclipseAdoptium.Temurin.21.JDK -e --silent --accept-source-agreements --accept-package-agreements' | Out-Null
    } elseif (Get-Command choco -ErrorAction SilentlyContinue) {
        Run-Cmd 'choco install temurin21 -y' | Out-Null
    } else { Fail 'install JDK 21: https://adoptium.net/temurin/releases/?version=21'; return $false }
    Refresh-Path
    $env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME','Machine')
    if (-not $env:JAVA_HOME) {
        $m = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -ErrorAction SilentlyContinue |
             Where-Object Name -match 'jdk-21' | Select-Object -First 1
        if ($m) { $env:JAVA_HOME = $m.FullName; $env:Path = "$env:JAVA_HOME\bin;$env:Path" }
    }
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
        Fail 'java installed but PATH not refreshed. Close PowerShell and re-run.'; return $false
    }
    Say 'java: installed'
    return $true
}

function Ensure-Gradle {
    # Prefer any gradle already on PATH (user might have e.g. 9.4.1 installed).
    if (Get-Command gradle -ErrorAction SilentlyContinue) {
        $out = (& cmd /c 'gradle --version 2>&1') -join "`n"
        $m = [regex]::Match($out,'Gradle\s+(\S+)')
        if ($m.Success) { Say "gradle: OK ($($m.Groups[1].Value))"; return $true }
        Say 'gradle on PATH but version not parsed, still using it' 'Yellow'
        return $true
    }
    Say 'gradle not on PATH, downloading portable distribution (~130 MB)' 'Yellow'
    $zip = Join-Path $env:TEMP 'gradle-8.10-bin.zip'
    if (-not (DL 'https://services.gradle.org/distributions/gradle-8.10-bin.zip' $zip 'Gradle 8.10')) { return $false }
    $dir = Join-Path $env:USERPROFILE 'spft-gradle'
    if (Test-Path $dir) {
        Say 'removing previous spft-gradle dir' 'Yellow'
        Remove-Item $dir -Recurse -Force -ErrorAction SilentlyContinue
    }
    Say 'extracting (fast, .NET ZipFile)...'
    try {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        [System.IO.Compression.ZipFile]::ExtractToDirectory($zip, $dir)
    } catch {
        Fail "zip extract failed: $($_.Exception.Message)"
        return $false
    }
    $gradleBin = (Get-ChildItem $dir -Directory | Select-Object -First 1).FullName + '\bin'
    if (-not (Test-Path "$gradleBin\gradle.bat")) { Fail "no gradle.bat in $gradleBin"; return $false }
    $env:Path = "$gradleBin;$env:Path"
    Say "gradle bin added to PATH: $gradleBin"
    return $true
}

function Main {
    Say '============================================'
    Say '  SPFT / maxDLC auto-builder (v3)'
    Say '============================================'

    if (-not (Ensure-Git))  { return }
    if (-not (Ensure-Java)) { return }

    # clone / update
    $needClone = $true
    if (Test-Path (Join-Path $WORK_DIR '.git')) {
        Say "updating repo in $WORK_DIR"
        Push-Location $WORK_DIR
        Run-Cmd "git fetch origin $BRANCH 2>&1" | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Run-Cmd "git checkout -B $BRANCH origin/$BRANCH 2>&1" | Out-Null
            if ($LASTEXITCODE -eq 0) {
                Run-Cmd "git reset --hard origin/$BRANCH 2>&1" | Out-Null
                if ($LASTEXITCODE -eq 0) { $needClone = $false }
            }
        }
        Pop-Location
        if ($needClone) { Say 'repo in weird state, wiping and recloning' 'Yellow' }
    }
    if ($needClone) {
        if (Test-Path $WORK_DIR) { Remove-Item $WORK_DIR -Recurse -Force }
        Say "cloning into $WORK_DIR"
        Run-Cmd "git clone --branch $BRANCH --depth 1 $REPO_URL `"$WORK_DIR`" 2>&1" | Out-Null
        if ($LASTEXITCODE -ne 0) { Fail "git clone failed (exit $LASTEXITCODE)"; return }
    }

    # gradle
    if (-not (Ensure-Gradle)) { return }

    Push-Location $WORK_DIR
    try {
        if (-not (Test-Path '.\gradlew.bat')) {
            Say 'running gradle wrapper...'
            Run-Cmd 'gradle wrapper --gradle-version 8.10 --no-daemon 2>&1'
            if (-not (Test-Path '.\gradlew.bat')) { Fail 'gradle wrapper did not create gradlew.bat'; return }
            Say 'gradle wrapper: OK'
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
    Say '  Drag it into %APPDATA%\.minecraft\mods\ to play.' 'Green'
    Say '  You still need Fabric Loader 0.16+ and Fabric API for MC 1.21.4.' 'Green'
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

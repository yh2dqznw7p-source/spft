# build.ps1 — автосборщик SPFT (maxDLC) "под ключ".
#
# Запуск одной строкой в PowerShell:
#   iwr https://raw.githubusercontent.com/yh2dqznw7p-source/spft/incoming/build.ps1 | iex
#
# Что он делает:
#   1. Ставит git, если его нет (через winget или chocolatey).
#   2. Ставит Temurin JDK 21, если его нет.
#   3. Клонирует (или обновляет) репу в %USERPROFILE%\spft-build.
#   4. Переключается на ветку incoming.
#   5. Создаёт gradle wrapper.
#   6. Запускает ./gradlew build.
#   7. Копирует готовый .jar в %APPDATA%\.minecraft\mods\maxDLC.jar.
#   8. Докачивает Fabric API 1.21.4, если его ещё нет.

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

function Say($msg, $col='Cyan') { Write-Host "[SPFT] $msg" -ForegroundColor $col }
function Die($msg) { Write-Host "[SPFT][!!] $msg" -ForegroundColor Red; exit 1 }

$REPO_URL    = 'https://github.com/yh2dqznw7p-source/spft.git'
$BRANCH      = 'incoming'
$WORK_DIR    = Join-Path $env:USERPROFILE 'spft-build'
$MODS_DIR    = Join-Path $env:APPDATA '.minecraft\mods'
$OUT_JAR     = Join-Path $MODS_DIR 'maxDLC.jar'
$FABRIC_URL  = 'https://cdn.modrinth.com/data/P7dR8mSH/versions/rH00tDfh/fabric-api-0.118.0%2B1.21.4.jar'
$FABRIC_NAME = 'fabric-api-0.118.0+1.21.4.jar'

Say '============================================'
Say '  SPFT / maxDLC auto-builder'
Say '============================================'

# ---- 1. GIT ----
function Ensure-Git {
    if (Get-Command git -ErrorAction SilentlyContinue) { Say 'git: OK'; return }
    Say 'git не найден, ставлю...' 'Yellow'
    if (Get-Command winget -ErrorAction SilentlyContinue) {
        winget install --id Git.Git -e --silent --accept-source-agreements --accept-package-agreements | Out-Null
    } elseif (Get-Command choco -ErrorAction SilentlyContinue) {
        choco install git -y | Out-Null
    } else {
        Die "Нет ни winget, ни choco. Поставь Git вручную: https://git-scm.com/download/win и перезапусти PowerShell."
    }
    $env:Path = [System.Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [System.Environment]::GetEnvironmentVariable('Path','User')
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
        Die 'git установлен, но PATH не обновился. ЗАКРОЙ PowerShell и запусти скрипт ещё раз.'
    }
    Say 'git: установлен'
}

# ---- 2. JAVA 21 ----
function Ensure-Java {
    $needInstall = $true
    if (Get-Command java -ErrorAction SilentlyContinue) {
        # java -version пишет в stderr — аккуратно вытаскиваем версию без падения скрипта
        $out = ''
        try {
            $prev = $ErrorActionPreference
            $ErrorActionPreference = 'SilentlyContinue'
            $out = (& cmd /c 'java -version 2>&1') -join "`n"
            $ErrorActionPreference = $prev
        } catch { $out = '' }
        $m = [regex]::Match($out, 'version "(\d+)(?:\.(\d+))?')
        if ($m.Success) {
            $v = [int]$m.Groups[1].Value
            if ($v -ge 21) { Say "java: OK ($v)"; $needInstall = $false }
            else { Say "java версии $v, нужна 21+, доустанавливаю" 'Yellow' }
        } else {
            Say 'java найдена, но версию не распознал — ставлю Temurin 21' 'Yellow'
        }
    } else {
        Say 'java не найдена, ставлю Temurin 21' 'Yellow'
    }
    if (-not $needInstall) { return }

    if (Get-Command winget -ErrorAction SilentlyContinue) {
        winget install --id EclipseAdoptium.Temurin.21.JDK -e --silent --accept-source-agreements --accept-package-agreements | Out-Null
    } elseif (Get-Command choco -ErrorAction SilentlyContinue) {
        choco install temurin21 -y | Out-Null
    } else {
        Die 'Поставь вручную JDK 21: https://adoptium.net/temurin/releases/?version=21'
    }

    $env:Path = [System.Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [System.Environment]::GetEnvironmentVariable('Path','User')
    $env:JAVA_HOME = [System.Environment]::GetEnvironmentVariable('JAVA_HOME','Machine')
    if (-not $env:JAVA_HOME) {
        $maybe = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -ErrorAction SilentlyContinue |
                 Where-Object Name -match 'jdk-21' | Select-Object -First 1
        if ($maybe) { $env:JAVA_HOME = $maybe.FullName; $env:Path = "$env:JAVA_HOME\bin;$env:Path" }
    }
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
        Die 'Java поставлена, но PATH не обновился. ЗАКРОЙ PowerShell и запусти скрипт ещё раз.'
    }
    Say 'java: установлена'
}

Ensure-Git
Ensure-Java

# ---- 3. клон / обновление ----
if (Test-Path (Join-Path $WORK_DIR '.git')) {
    Say "обновляю репу в $WORK_DIR"
    Push-Location $WORK_DIR
    git fetch origin $BRANCH 2>$null
    git checkout $BRANCH 2>$null
    git reset --hard "origin/$BRANCH" 2>$null
    Pop-Location
} else {
    if (Test-Path $WORK_DIR) { Remove-Item $WORK_DIR -Recurse -Force }
    Say "клонирую в $WORK_DIR"
    git clone --branch $BRANCH --depth 1 $REPO_URL $WORK_DIR
}

Push-Location $WORK_DIR
try {
    # ---- 4. gradle wrapper ----
    if (-not (Test-Path '.\gradlew.bat')) {
        Say 'создаю gradle wrapper'
        if (Get-Command gradle -ErrorAction SilentlyContinue) {
            gradle wrapper --gradle-version 8.10 --no-daemon 2>&1 | Out-Null
        } else {
            Say 'gradle не установлен, скачиваю дистрибутив...' 'Yellow'
            $gradleZip = Join-Path $env:TEMP 'gradle-8.10-bin.zip'
            Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-8.10-bin.zip' -OutFile $gradleZip -UseBasicParsing
            $gradleDir = Join-Path $env:USERPROFILE 'spft-gradle'
            if (Test-Path $gradleDir) { Remove-Item $gradleDir -Recurse -Force }
            Expand-Archive $gradleZip -DestinationPath $gradleDir
            $gradleBin = (Get-ChildItem $gradleDir -Directory | Select-Object -First 1).FullName + '\bin'
            $env:Path = "$gradleBin;$env:Path"
            gradle wrapper --gradle-version 8.10 --no-daemon 2>&1 | Out-Null
        }
    }

    # ---- 5. build ----
    Say 'собираю (будет долго в первый раз, 5-15 мин)'
    cmd /c '.\gradlew.bat build --no-daemon 2>&1'
    if ($LASTEXITCODE -ne 0) { Die 'сборка не удалась (см. лог выше)' }

    # ---- 6. копирование ----
    $jar = Get-ChildItem 'build\libs' -Filter 'spft-*.jar' -ErrorAction SilentlyContinue |
           Where-Object Name -notmatch '(sources|dev|javadoc)' |
           Select-Object -First 1
    if (-not $jar) { Die 'не нашёл готовый jar в build\libs' }

    if (-not (Test-Path $MODS_DIR)) { New-Item -ItemType Directory -Path $MODS_DIR -Force | Out-Null }
    Copy-Item $jar.FullName $OUT_JAR -Force
    Say "установил: $OUT_JAR" 'Green'
} finally {
    Pop-Location
}

# ---- 7. fabric api ----
$hasFabric = Get-ChildItem $MODS_DIR -Filter 'fabric-api-*.jar' -ErrorAction SilentlyContinue
if (-not $hasFabric) {
    Say 'Fabric API не найден, качаю'
    try {
        Invoke-WebRequest $FABRIC_URL -OutFile (Join-Path $MODS_DIR $FABRIC_NAME) -UseBasicParsing
        Say 'Fabric API: установлен' 'Green'
    } catch {
        Say 'Fabric API не скачался, поставь вручную с https://modrinth.com/mod/fabric-api' 'Yellow'
    }
} else {
    Say "Fabric API: уже есть ($($hasFabric.Name))" 'Green'
}

Say ''
Say '============================================' 'Green'
Say '  ГОТОВО' 'Green'
Say "  JAR: $OUT_JAR" 'Green'
Say '  Осталось поставить Fabric Loader 0.16+ для 1.21.4:' 'Green'
Say '    https://fabricmc.net/use/installer/' 'Yellow'
Say '============================================' 'Green'

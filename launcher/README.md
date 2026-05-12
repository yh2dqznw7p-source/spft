# maxDLC Launcher

One-file installer+launcher for Minecraft 1.21.4 + Fabric + maxDLC.

## Files

| File | Purpose |
|---|---|
| `maxDLC.bat` | **Main launcher.** Self-copies to `C:\maxDLC\`, installs Fabric + mods on first run, opens Minecraft Launcher on subsequent runs. |
| `maxDLC-Loader.bat` | *(legacy)* One-shot installer into `%APPDATA%\.minecraft` without a dedicated folder. Kept for compatibility. |

## How `maxDLC.bat` works

### First run
1. Copies itself to **`C:\maxDLC\maxDLC.bat`**
   *(falls back to `%LOCALAPPDATA%\maxDLC\maxDLC.bat` if you have no write access to `C:\`)*.
2. Re-executes from that folder.
3. Checks Java 21 is in `PATH`.
4. Downloads official [Fabric Installer](https://fabricmc.net) and installs **Fabric Loader 0.16.9** for **Minecraft 1.21.4** into `%APPDATA%\.minecraft\versions\`.
5. Downloads into `C:\maxDLC\mods\`:
   - `fabric-api.jar` (Modrinth CDN)
   - `maxdlc.jar` (GitHub release)
6. Writes a new profile **`maxDLC`** into `%APPDATA%\.minecraft\launcher_profiles.json` with:
   - `lastVersionId = fabric-loader-0.16.9-1.21.4`
   - `gameDir = C:/maxDLC`
   - `javaArgs = -Xmx4G -Xms1G`
7. Opens the Minecraft Launcher.

### Every next run
Just opens the Minecraft Launcher. Pick profile **`maxDLC`** and click Play.

## What ends up on disk

```
C:\maxDLC\
├── maxDLC.bat                ← self-copy of the launcher
├── .installed                ← marker (skip setup on next run)
├── fabric-installer.jar
├── mods\
│   ├── fabric-api.jar
│   └── maxdlc.jar
├── saves\
├── resourcepacks\
├── screenshots\
└── config\
```

Minecraft runtime (client jar, libraries, assets) lives in the official `%APPDATA%\.minecraft\` — the launcher shares them across profiles automatically.

## Requirements

- Windows 10 / 11
- **Java 21** in `PATH` → [Adoptium Temurin 21](https://adoptium.net/temurin/releases/?version=21) → tick *Set JAVA_HOME / Add to PATH* during install.
- **Minecraft Launcher** (Store or classic) with a purchased Microsoft account.
  *(The launcher does NOT bypass authentication. Vanilla login through the official launcher is required.)*

## Controls in-game

- **RIGHT_SHIFT** — open ClickGUI
- **Chat `.`** — prefix for commands, e.g. `.bind add AimAssist R`
- ESC / RIGHT_SHIFT inside ClickGUI — close it

## Troubleshooting

| Problem | Fix |
|---|---|
| `Java is not in PATH` | Install Temurin 21, tick *Add to PATH*, reopen console. |
| `Cannot create C:\maxDLC` | The bat will auto-fallback to `%LOCALAPPDATA%\maxDLC`. To really install under `C:\` run the bat as administrator. |
| `launcher_profiles.json not found` | Open Minecraft Launcher once to create it, then rerun `maxDLC.bat`. |
| `maxdlc.jar not found in GitHub release yet` | Build yourself: `gradlew.bat build`, then copy `build/libs/maxdlc-1.0.0.jar` into `C:\maxDLC\mods\maxdlc.jar`. |
| Profile `maxDLC` doesn't appear in launcher | Close the launcher fully (tray icon too), rerun `maxDLC.bat`. Launcher caches profiles in memory while running. |
| Classic `MinecraftLauncher.exe` is not found | The bat tries Store/MSIX/classic paths. If none exist, it prints a message and you open the launcher manually. |

## Uninstall

```
rmdir /S /Q C:\maxDLC
```

In Minecraft Launcher → *Installations* → right-click *maxDLC* → Delete. *(The profile entry in `launcher_profiles.json` is harmless even if left.)*

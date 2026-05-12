# maxDLC Loader (`maxDLC-Loader.bat`)

Одноразовый установщик для Windows. Делает всё сам:

1. Проверяет наличие Java в `PATH` (нужна **Java 21**, например [Adoptium Temurin 21](https://adoptium.net/temurin/releases/?version=21)).
2. Скачивает Fabric Installer с официального `maven.fabricmc.net`.
3. Устанавливает **Fabric Loader 0.16.9** для **Minecraft 1.21.4**.
4. Кладёт в `%APPDATA%\.minecraft\mods`:
   - `fabric-api.jar`
   - `maxdlc.jar` (забирается из [последнего релиза GitHub](https://github.com/yh2dqznw7p-source/spft/releases/latest))
   - опционально — Sodium / Lithium / FerriteCore (по желанию, Y/N в процессе).

## Как пользоваться

1. Один раз запусти официальный Minecraft Launcher, чтобы появилась папка `.minecraft`.
2. ПКМ по `maxDLC-Loader.bat` → **Запустить от имени администратора** *(не обязательно, но иногда нужно для записи в `%APPDATA%`)*.
3. Подожди, пока всё скачается.
4. Открой Minecraft Launcher → выбери профиль **`fabric-loader-1.21.4`** → играй.
5. В игре — **RIGHT_SHIFT** открывает ClickGUI.

## Если Java не установлена

Скачай установщик Temurin 21 x64:
https://adoptium.net/temurin/releases/?version=21

Поставь, **отметь галочку «Set JAVA_HOME» и «Add to PATH»**, перезайди в консоль, запусти `.bat` снова.

## Важно

- Лоадер ничего не инжектит в чужие процессы. Он просто кладёт jar-моды в `mods/` — стандартный механизм Fabric.
- AimAssist / TriggerBot ловятся античитом на большинстве публичных серверов. Используй на single-player / своём приватном сервере, иначе получишь бан — это твоя ответственность.
- Если моды не подгрузились — убедись, что выбран профиль `fabric-loader-1.21.4`, а не обычный `Latest Release`.

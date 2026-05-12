package ru.maxdlc.config;

import net.fabricmc.loader.api.FabricLoader;
import ru.maxdlc.MaxDLCClient;
import ru.maxdlc.bind.BindManager;
import ru.maxdlc.module.Module;
import ru.maxdlc.setting.BooleanSetting;
import ru.maxdlc.setting.KeyNames;
import ru.maxdlc.setting.KeySetting;
import ru.maxdlc.setting.ModeSetting;
import ru.maxdlc.setting.NumberSetting;
import ru.maxdlc.setting.Setting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Очень простой плоский "properties"-like конфиг:
 *   module.<Module>.enabled=true/false
 *   module.<Module>.setting.<Name>=<value>
 *   bind=<Module>:<Key>
 * Без внешних зависимостей.
 */
public class ConfigManager {
    private Path configFile() {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("maxdlc");
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        return dir.resolve("config.txt");
    }

    public void save(MaxDLCClient client) {
        Path file = configFile();
        List<String> lines = new ArrayList<>();
        lines.add("# maxDLC config");
        for (Module m : client.getModuleManager().getModules()) {
            lines.add("module." + m.getName() + ".enabled=" + m.isEnabled());
            for (Setting s : m.getSettings()) {
                if (s instanceof BooleanSetting b) {
                    lines.add("module." + m.getName() + ".setting." + s.getName() + "=" + b.getValue());
                } else if (s instanceof NumberSetting n) {
                    lines.add("module." + m.getName() + ".setting." + s.getName() + "=" + n.getValue());
                } else if (s instanceof ModeSetting mo) {
                    lines.add("module." + m.getName() + ".setting." + s.getName() + "=" + mo.getValue());
                } else if (s instanceof KeySetting k) {
                    lines.add("module." + m.getName() + ".setting." + s.getName() + "=" + KeyNames.nameOf(k.getKeyCode()));
                }
            }
        }
        for (BindManager.BindEntry e : client.getBindManager().getBinds()) {
            lines.add("bind=" + e.module().getName() + ":" + KeyNames.nameOf(e.key()));
        }
        try { Files.write(file, lines); } catch (IOException ignored) {}
    }

    public void load(MaxDLCClient client) {
        Path file = configFile();
        if (!Files.exists(file)) return;
        try {
            for (String line : Files.readAllLines(file)) {
                if (line.isBlank() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                if (key.equals("bind")) {
                    String[] parts = value.split(":", 2);
                    if (parts.length == 2) {
                        Module m = client.getModuleManager().getModule(parts[0]);
                        int k = KeyNames.keyOf(parts[1]);
                        if (m != null && k > 0) client.getBindManager().add(m, k);
                    }
                    continue;
                }
                if (!key.startsWith("module.")) continue;
                String rest = key.substring("module.".length());
                int dot = rest.indexOf('.');
                if (dot < 0) continue;
                String moduleName = rest.substring(0, dot);
                String tail = rest.substring(dot + 1);
                Module module = client.getModuleManager().getModule(moduleName);
                if (module == null) continue;
                if (tail.equals("enabled")) {
                    if (Boolean.parseBoolean(value)) module.setEnabled(true);
                } else if (tail.startsWith("setting.")) {
                    String settingName = tail.substring("setting.".length());
                    for (Setting s : module.getSettings()) {
                        if (!s.getName().equalsIgnoreCase(settingName)) continue;
                        applyValue(s, value);
                        break;
                    }
                }
            }
        } catch (IOException ignored) {}
    }

    private void applyValue(Setting s, String value) {
        try {
            if (s instanceof BooleanSetting b) b.setValue(Boolean.parseBoolean(value));
            else if (s instanceof NumberSetting n) n.setValue(Double.parseDouble(value));
            else if (s instanceof ModeSetting mo) mo.setValue(value);
            else if (s instanceof KeySetting k) k.setKeyCode(KeyNames.keyOf(value));
        } catch (Exception ignored) {}
    }
}

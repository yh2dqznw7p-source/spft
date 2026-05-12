package ru.maxdlc.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import ru.maxdlc.setting.BooleanSetting;
import ru.maxdlc.setting.KeySetting;
import ru.maxdlc.setting.ModeSetting;
import ru.maxdlc.setting.NumberSetting;
import ru.maxdlc.setting.Setting;

import java.util.ArrayList;
import java.util.List;

/**
 * Базовый класс модуля чита.
 */
public abstract class Module {
    protected final MinecraftClient mc = MinecraftClient.getInstance();

    private final String name;
    private final String description;
    private final Category category;
    private final List<Setting> settings = new ArrayList<>();
    private final KeySetting keyBind;

    private boolean enabled = false;

    public Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.keyBind = new KeySetting("Bind", -1);
        this.settings.add(keyBind);
    }

    public final String getName() {
        return name;
    }

    public final String getDescription() {
        return description;
    }

    public final Category getCategory() {
        return category;
    }

    public final boolean isEnabled() {
        return enabled;
    }

    public final KeySetting getKeyBind() {
        return keyBind;
    }

    public List<Setting> getSettings() {
        return settings;
    }

    protected <T extends Setting> T addSetting(T setting) {
        settings.add(setting);
        return setting;
    }

    protected BooleanSetting addBoolean(String name, boolean def) {
        return addSetting(new BooleanSetting(name, def));
    }

    protected NumberSetting addNumber(String name, double def, double min, double max, double step) {
        return addSetting(new NumberSetting(name, def, min, max, step));
    }

    protected ModeSetting addMode(String name, String def, String... modes) {
        return addSetting(new ModeSetting(name, def, modes));
    }

    public final void toggle() {
        setEnabled(!enabled);
    }

    public final void setEnabled(boolean state) {
        if (this.enabled == state) return;
        this.enabled = state;
        if (state) {
            onEnable();
        } else {
            onDisable();
        }
    }

    public void onEnable() {}
    public void onDisable() {}

    public void onTick() {}
    public void onRender(float tickDelta) {}

    public String getDisplayInfo() {
        return null;
    }

    protected void chat(String message) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.of("§8[§cmaxDLC§8] §7" + message), false);
        }
    }
}

package ru.maxdlc.setting;

import java.util.List;

public class ModeSetting extends Setting {
    private final List<String> modes;
    private int index;

    public ModeSetting(String name, String defaultMode, String... modes) {
        super(name);
        this.modes = List.of(modes);
        int idx = this.modes.indexOf(defaultMode);
        this.index = Math.max(0, idx);
    }

    public String getValue() {
        return modes.get(index);
    }

    public void setValue(String mode) {
        int idx = modes.indexOf(mode);
        if (idx >= 0) index = idx;
    }

    public void cycle() {
        index = (index + 1) % modes.size();
    }

    public void cycleBack() {
        index = (index - 1 + modes.size()) % modes.size();
    }

    public List<String> getModes() {
        return modes;
    }

    public boolean is(String mode) {
        return getValue().equalsIgnoreCase(mode);
    }
}

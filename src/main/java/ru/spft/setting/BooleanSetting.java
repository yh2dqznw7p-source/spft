package ru.spft.setting;

public class BooleanSetting extends Setting {
    private boolean value;
    private final boolean defaultValue;

    public BooleanSetting(String name, boolean defaultValue) {
        super(name);
        this.value = defaultValue;
        this.defaultValue = defaultValue;
    }

    public boolean getValue() {
        return value;
    }

    public void setValue(boolean value) {
        this.value = value;
    }

    public void toggle() {
        this.value = !this.value;
    }

    public boolean getDefaultValue() {
        return defaultValue;
    }
}

package ru.spft.setting;

/**
 * Базовый класс настройки модуля.
 */
public abstract class Setting {
    private final String name;
    private java.util.function.BooleanSupplier visibility = () -> true;

    protected Setting(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Setting setVisibility(java.util.function.BooleanSupplier visibility) {
        this.visibility = visibility;
        return this;
    }

    public boolean isVisible() {
        return visibility.getAsBoolean();
    }
}

package ru.maxdlc.setting;

public class NumberSetting extends Setting {
    private double value;
    private final double min;
    private final double max;
    private final double step;

    public NumberSetting(String name, double defaultValue, double min, double max, double step) {
        super(name);
        this.value = defaultValue;
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public double getValue() {
        return value;
    }

    public float getFloat() {
        return (float) value;
    }

    public int getInt() {
        return (int) Math.round(value);
    }

    public void setValue(double value) {
        this.value = Math.max(min, Math.min(max, Math.round(value / step) * step));
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getStep() {
        return step;
    }
}

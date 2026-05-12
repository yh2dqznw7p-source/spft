package ru.maxdlc.setting;

import org.lwjgl.glfw.GLFW;

public class KeySetting extends Setting {
    private int keyCode;

    public KeySetting(String name, int defaultKey) {
        super(name);
        this.keyCode = defaultKey;
    }

    public int getKeyCode() {
        return keyCode;
    }

    public void setKeyCode(int keyCode) {
        this.keyCode = keyCode;
    }

    public String getKeyName() {
        if (keyCode == GLFW.GLFW_KEY_UNKNOWN || keyCode < 0) return "NONE";
        String name = GLFW.glfwGetKeyName(keyCode, 0);
        if (name != null) return name.toUpperCase();
        return KeyNames.nameOf(keyCode);
    }
}

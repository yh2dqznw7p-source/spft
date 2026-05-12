package ru.spft.setting;

import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * Утилита для перевода имён клавиш в коды GLFW и обратно.
 */
public final class KeyNames {
    private static final Map<String, Integer> NAME_TO_KEY = new HashMap<>();
    private static final Map<Integer, String> KEY_TO_NAME = new HashMap<>();

    static {
        // Буквы
        for (char c = 'A'; c <= 'Z'; c++) {
            int code = GLFW.GLFW_KEY_A + (c - 'A');
            register(String.valueOf(c), code);
        }
        // Цифры
        for (int i = 0; i <= 9; i++) {
            register(String.valueOf(i), GLFW.GLFW_KEY_0 + i);
        }
        // F1..F12
        for (int i = 1; i <= 12; i++) {
            register("F" + i, GLFW.GLFW_KEY_F1 + (i - 1));
        }
        register("ESCAPE", GLFW.GLFW_KEY_ESCAPE);
        register("ENTER", GLFW.GLFW_KEY_ENTER);
        register("TAB", GLFW.GLFW_KEY_TAB);
        register("BACKSPACE", GLFW.GLFW_KEY_BACKSPACE);
        register("INSERT", GLFW.GLFW_KEY_INSERT);
        register("DELETE", GLFW.GLFW_KEY_DELETE);
        register("RIGHT", GLFW.GLFW_KEY_RIGHT);
        register("LEFT", GLFW.GLFW_KEY_LEFT);
        register("DOWN", GLFW.GLFW_KEY_DOWN);
        register("UP", GLFW.GLFW_KEY_UP);
        register("HOME", GLFW.GLFW_KEY_HOME);
        register("END", GLFW.GLFW_KEY_END);
        register("CAPS_LOCK", GLFW.GLFW_KEY_CAPS_LOCK);
        register("SPACE", GLFW.GLFW_KEY_SPACE);
        register("LEFT_SHIFT", GLFW.GLFW_KEY_LEFT_SHIFT);
        register("RIGHT_SHIFT", GLFW.GLFW_KEY_RIGHT_SHIFT);
        register("LEFT_CONTROL", GLFW.GLFW_KEY_LEFT_CONTROL);
        register("RIGHT_CONTROL", GLFW.GLFW_KEY_RIGHT_CONTROL);
        register("LEFT_ALT", GLFW.GLFW_KEY_LEFT_ALT);
        register("RIGHT_ALT", GLFW.GLFW_KEY_RIGHT_ALT);
        register("MINUS", GLFW.GLFW_KEY_MINUS);
        register("EQUAL", GLFW.GLFW_KEY_EQUAL);
        register("COMMA", GLFW.GLFW_KEY_COMMA);
        register("PERIOD", GLFW.GLFW_KEY_PERIOD);
        register("SLASH", GLFW.GLFW_KEY_SLASH);
        register("SEMICOLON", GLFW.GLFW_KEY_SEMICOLON);
        register("APOSTROPHE", GLFW.GLFW_KEY_APOSTROPHE);
        register("GRAVE_ACCENT", GLFW.GLFW_KEY_GRAVE_ACCENT);
        register("LEFT_BRACKET", GLFW.GLFW_KEY_LEFT_BRACKET);
        register("RIGHT_BRACKET", GLFW.GLFW_KEY_RIGHT_BRACKET);
        register("BACKSLASH", GLFW.GLFW_KEY_BACKSLASH);
    }

    private KeyNames() {}

    private static void register(String name, int code) {
        NAME_TO_KEY.put(name.toUpperCase(), code);
        KEY_TO_NAME.put(code, name.toUpperCase());
    }

    public static int keyOf(String name) {
        if (name == null) return GLFW.GLFW_KEY_UNKNOWN;
        String n = name.toUpperCase();
        if (n.equals("NONE")) return GLFW.GLFW_KEY_UNKNOWN;
        Integer code = NAME_TO_KEY.get(n);
        if (code != null) return code;
        // fallback через GLFW
        try {
            return (int) GLFW.class.getField("GLFW_KEY_" + n).get(null);
        } catch (Exception ignored) {
            return GLFW.GLFW_KEY_UNKNOWN;
        }
    }

    public static String nameOf(int keyCode) {
        return KEY_TO_NAME.getOrDefault(keyCode, "KEY_" + keyCode);
    }
}

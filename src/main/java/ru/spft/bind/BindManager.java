package ru.spft.bind;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import ru.spft.SpftClient;
import ru.spft.module.Module;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Менеджер биндов. Поддерживает назначение НЕСКОЛЬКИХ биндов на одну клавишу
 * (команда .bind add <module> <key> добавляет ещё один бинд, не перезаписывая существующие).
 * Все биндят модули тоглятся при нажатии клавиши.
 */
public class BindManager {
    private final List<BindEntry> binds = new ArrayList<>();
    private final Set<Integer> pressed = new HashSet<>();

    public void add(Module module, int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_UNKNOWN) return;
        binds.add(new BindEntry(module, keyCode));
        // синхронизируем первый бинд модуля в его KeySetting для отображения в GUI
        if (module.getKeyBind().getKeyCode() == GLFW.GLFW_KEY_UNKNOWN
                || module.getKeyBind().getKeyCode() < 0) {
            module.getKeyBind().setKeyCode(keyCode);
        }
    }

    public boolean remove(Module module, int keyCode) {
        boolean removed = binds.removeIf(b -> b.module() == module && b.key() == keyCode);
        if (removed && module.getKeyBind().getKeyCode() == keyCode) {
            // восстанавливаем любую другую клавишу этого модуля или сбрасываем
            int other = binds.stream().filter(b -> b.module() == module).mapToInt(BindEntry::key).findFirst().orElse(-1);
            module.getKeyBind().setKeyCode(other);
        }
        return removed;
    }

    public int removeAll(Module module) {
        int count = 0;
        var it = binds.iterator();
        while (it.hasNext()) {
            if (it.next().module() == module) { it.remove(); count++; }
        }
        if (count > 0) module.getKeyBind().setKeyCode(-1);
        return count;
    }

    public void clear() {
        binds.clear();
    }

    public List<BindEntry> getBinds() {
        return binds;
    }

    public List<BindEntry> getBindsFor(Module module) {
        return binds.stream().filter(b -> b.module() == module).toList();
    }

    public void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getWindow() == null) return;
        long handle = mc.getWindow().getHandle();

        // если открыт чат/экран — не триггерим биндов (чтобы не тоглить при вводе)
        if (mc.currentScreen != null) return;

        // также учитываем первичный KeySetting модуля (для биндов, выставленных через GUI)
        for (Module m : SpftClient.get().getModuleManager().getModules()) {
            int key = m.getKeyBind().getKeyCode();
            if (key <= 0) continue;
            handleKey(handle, key, m);
        }

        for (BindEntry entry : binds) {
            handleKey(handle, entry.key(), entry.module());
        }
    }

    private void handleKey(long handle, int key, Module module) {
        boolean down = GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
        Integer id = System.identityHashCode(module) * 31 + key;
        if (down) {
            if (!pressed.contains(id)) {
                pressed.add(id);
                module.toggle();
            }
        } else {
            pressed.remove(id);
        }
    }

    public record BindEntry(Module module, int key) {}
}

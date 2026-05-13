package ru.spft.module;

import ru.spft.module.impl.combat.AimAssist;
import ru.spft.module.impl.combat.AutoSwap;
import ru.spft.module.impl.combat.TriggerBot;
import ru.spft.module.impl.misc.AutoInstallMods;
import ru.spft.module.impl.misc.ClickGuiModule;
import ru.spft.module.impl.movement.AutoSprint;
import ru.spft.module.impl.visual.AntiInvisible;
import ru.spft.module.impl.visual.NoRender;
import ru.spft.module.impl.visual.TargetEsp;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    private final List<Module> modules = new ArrayList<>();

    public void init() {
        // Combat
        register(new AimAssist());
        register(new TriggerBot());
        register(new AutoSwap());
        // Movement
        register(new AutoSprint());
        // Visual
        register(new NoRender());
        register(new TargetEsp());
        register(new AntiInvisible());
        // Misc
        register(new ClickGuiModule());
        register(new AutoInstallMods());
    }

    private void register(Module module) {
        modules.add(module);
    }

    public List<Module> getModules() {
        return modules;
    }

    public List<Module> getModulesIn(Category category) {
        List<Module> list = new ArrayList<>();
        for (Module m : modules) if (m.getCategory() == category) list.add(m);
        return list;
    }

    public Module getModule(String name) {
        for (Module m : modules) if (m.getName().equalsIgnoreCase(name)) return m;
        return null;
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T getModule(Class<T> clazz) {
        for (Module m : modules) if (clazz.isInstance(m)) return (T) m;
        return null;
    }

    public void onTick() {
        for (Module m : modules) if (m.isEnabled()) {
            try { m.onTick(); } catch (Exception ignored) {}
        }
    }

    public void onRender(float tickDelta) {
        for (Module m : modules) if (m.isEnabled()) {
            try { m.onRender(tickDelta); } catch (Exception ignored) {}
        }
    }
}

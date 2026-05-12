package ru.maxdlc.module;

import ru.maxdlc.module.impl.combat.AimAssist;
import ru.maxdlc.module.impl.combat.KillAura;
import ru.maxdlc.module.impl.combat.TriggerBot;
import ru.maxdlc.module.impl.misc.AutoInstallMods;
import ru.maxdlc.module.impl.misc.ClickGuiModule;
import ru.maxdlc.module.impl.movement.AutoSprint;
import ru.maxdlc.module.impl.player.AutoSwap;
import ru.maxdlc.module.impl.player.NoSlow;
import ru.maxdlc.module.impl.visual.AntiInvisible;
import ru.maxdlc.module.impl.visual.NoRender;
import ru.maxdlc.module.impl.visual.TargetEsp;
import ru.maxdlc.module.impl.visual.TargetHud;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    private final List<Module> modules = new ArrayList<>();

    public void init() {
        // Combat
        register(new AimAssist());
        register(new TriggerBot());
        register(new KillAura());
        // Movement
        register(new AutoSprint());
        // Player
        register(new AutoSwap());
        register(new NoSlow());
        // Visual
        register(new NoRender());
        register(new TargetEsp());
        register(new AntiInvisible());
        register(new TargetHud());
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

    public void onRender3D(RenderContext ctx) {
        for (Module m : modules) if (m.isEnabled()) {
            try { m.onRender3D(ctx); } catch (Exception ignored) {}
        }
    }
}

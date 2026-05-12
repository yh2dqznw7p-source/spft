package ru.maxdlc.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.maxdlc.MaxDLCClient;
import ru.maxdlc.module.Module;
import ru.maxdlc.module.impl.visual.NoRender;

import java.util.List;

/**
 * - отключает оверлеи тыквы/портала/огня когда выставлено NoOverlayFire/NoPumpkinOverlay/NoPortalOverlay
 * - рисует ArrayList включённых модулей в правом верхнем углу
 */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    // В 1.21.4 yarn метод называется renderMiscOverlays, а не renderOverlays.
    // Держим require=0 на случай, если в других yarn-ревизиях он снова переименован.
    @Inject(method = "renderMiscOverlays", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void spft$noMiscOverlays(DrawContext context, net.minecraft.client.render.RenderTickCounter tc, CallbackInfo ci) {
        NoRender nr = noRender();
        if (nr != null && nr.isEnabled() &&
                (nr.noPumpkinOverlay.getValue() || nr.noPortalOverlay.getValue() || nr.noOverlayFire.getValue())) {
            ci.cancel();
        }
    }

    // Точечный вариант — только огонь-оверлей на экране, если метод существует отдельно.
    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void spft$noFireOverlay(DrawContext context, CallbackInfo ci) {
        NoRender nr = noRender();
        if (nr != null && nr.isEnabled() && nr.noOverlayFire.getValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void spft$renderArrayList(DrawContext context, net.minecraft.client.render.RenderTickCounter tc, CallbackInfo ci) {
        if (MaxDLCClient.get() == null) return;
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.options.hudHidden || mc.getDebugHud().shouldShowDebugHud()) return;
        if (mc.currentScreen != null) return;

        List<Module> on = MaxDLCClient.get().getModuleManager().getModules().stream()
                .filter(Module::isEnabled).sorted((a, b) -> mc.textRenderer.getWidth(b.getName()) - mc.textRenderer.getWidth(a.getName()))
                .toList();
        int y = 2;
        int sw = mc.getWindow().getScaledWidth();
        for (Module m : on) {
            String info = m.getDisplayInfo();
            String text = info == null ? m.getName() : (m.getName() + " §7" + info);
            int w = mc.textRenderer.getWidth(text) + 4;
            context.fill(sw - w - 2, y, sw - 1, y + 10, 0x80000000);
            context.drawTextWithShadow(mc.textRenderer, "§c" + m.getName()
                    + (info != null ? " §7" + info : ""), sw - w, y + 1, 0xFFFFFFFF);
            y += 11;
        }
    }

    private static NoRender noRender() {
        if (MaxDLCClient.get() == null) return null;
        return MaxDLCClient.get().getModuleManager().getModule(NoRender.class);
    }
}

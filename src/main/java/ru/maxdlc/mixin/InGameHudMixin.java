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
import ru.maxdlc.module.impl.visual.TargetHud;

import java.util.List;

/**
 * - Отключает misc-оверлеи (тыква / портал / огонь) когда включён NoRender.
 * - Рисует ArrayList включённых модулей в правом верхнем углу.
 * - Вызывает TargetHud.draw() если модуль TargetHUD включён.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    // В 1.21.4 yarn метод называется renderMiscOverlays. require=0 — если метод
    // переименован в другой ревизии mappings, миксин молча не примется, вместо краша.
    @Inject(method = "renderMiscOverlays", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void spft$noMiscOverlays(DrawContext context, net.minecraft.client.render.RenderTickCounter tc, CallbackInfo ci) {
        NoRender nr = noRender();
        if (nr != null && nr.isEnabled() &&
                (nr.noPumpkinOverlay.getValue() || nr.noPortalOverlay.getValue() || nr.noOverlayFire.getValue())) {
            ci.cancel();
        }
    }

    // Отдельный fire-overlay, если в конкретном ревизии yarn он выделен.
    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void spft$noFireOverlay(DrawContext context, CallbackInfo ci) {
        NoRender nr = noRender();
        if (nr != null && nr.isEnabled() && nr.noOverlayFire.getValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void spft$renderMaxDLCOverlays(DrawContext context, net.minecraft.client.render.RenderTickCounter tc, CallbackInfo ci) {
        if (MaxDLCClient.get() == null) return;
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.options.hudHidden || mc.getDebugHud().shouldShowDebugHud()) return;
        if (mc.currentScreen != null) return;

        // TargetHUD — если включён.
        TargetHud thud = MaxDLCClient.get().getModuleManager().getModule(TargetHud.class);
        if (thud != null && thud.isEnabled()) {
            try { thud.draw(context); } catch (Exception ignored) {}
        }

        // ArrayList включённых модулей.
        List<Module> on = MaxDLCClient.get().getModuleManager().getModules().stream()
                .filter(Module::isEnabled)
                .sorted((a, b) -> mc.textRenderer.getWidth(b.getName()) - mc.textRenderer.getWidth(a.getName()))
                .toList();
        int y = 2;
        int sw = mc.getWindow().getScaledWidth();
        for (Module m : on) {
            String info = m.getDisplayInfo();
            int w = mc.textRenderer.getWidth(m.getName() + (info != null ? " " + info : "")) + 4;
            context.fill(sw - w - 2, y, sw - 1, y + 10, 0x80000000);
            context.drawTextWithShadow(mc.textRenderer,
                    "§c" + m.getName() + (info != null ? " §7" + info : ""),
                    sw - w, y + 1, 0xFFFFFFFF);
            y += 11;
        }
    }

    private static NoRender noRender() {
        if (MaxDLCClient.get() == null) return null;
        return MaxDLCClient.get().getModuleManager().getModule(NoRender.class);
    }
}

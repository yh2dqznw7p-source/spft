package ru.maxdlc.mixin;

import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.maxdlc.MaxDLCClient;
import ru.maxdlc.module.impl.visual.NoRender;

/**
 * Частицы-разрушения блоков (blockBreakingProgress) — отключаем их при NoBlockBreakParticles.
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {

    @Inject(method = "setBlockBreakingInfo", at = @At("HEAD"), cancellable = true)
    private void spft$noBlockBreakInfo(int entityId, net.minecraft.util.math.BlockPos pos, int progress, CallbackInfo ci) {
        NoRender nr = noRender();
        if (nr != null && nr.isEnabled() && nr.noBlockBreakParticles.getValue()) {
            ci.cancel();
        }
    }

    private static NoRender noRender() {
        if (MaxDLCClient.get() == null) return null;
        return MaxDLCClient.get().getModuleManager().getModule(NoRender.class);
    }
}

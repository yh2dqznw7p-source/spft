package ru.maxdlc.mixin;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.maxdlc.MaxDLCClient;
import ru.maxdlc.module.impl.visual.AntiInvisible;

/**
 * AntiInvisible: увеличивает альфа невидимых сущностей до заданного процента.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Inject(method = "getAlpha", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void spft$antiInvisibleAlpha(LivingEntityRenderState state, CallbackInfoReturnable<Float> cir) {
        if (MaxDLCClient.get() == null) return;
        AntiInvisible ai = MaxDLCClient.get().getModuleManager().getModule(AntiInvisible.class);
        if (ai == null || !ai.isEnabled()) return;
        if (!state.invisible) return;

        boolean isPlayer = state instanceof net.minecraft.client.render.entity.state.PlayerEntityRenderState;
        if (isPlayer && ai.players.getValue()) {
            cir.setReturnValue(ai.getAlpha());
        } else if (!isPlayer && ai.mobs.getValue()) {
            cir.setReturnValue(ai.getAlpha());
        }
    }
}

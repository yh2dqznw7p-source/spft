package ru.spft.mixin;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.spft.SpftClient;
import ru.spft.module.impl.visual.AntiInvisible;

/**
 * Перекрывает расчёт альфа-канала для невидимых игроков — вместо 0.15
 * рендерим их с заданной настройкой opacity (по умолчанию 50%).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Inject(method = "getAlpha", at = @At("HEAD"), cancellable = true)
    private void spft$antiInvisibleAlpha(LivingEntityRenderState state, CallbackInfoReturnable<Float> cir) {
        if (SpftClient.get() == null) return;
        AntiInvisible ai = SpftClient.get().getModuleManager().getModule(AntiInvisible.class);
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

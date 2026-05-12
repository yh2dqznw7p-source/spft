package ru.spft.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.spft.SpftClient;
import ru.spft.module.impl.visual.NoRender;

/**
 * Отключает тряску/качание/hurt-камеру когда соответствующие опции NoRender активны.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void spft$noBobbing(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        NoRender nr = noRender();
        if (nr != null && nr.isEnabled() && (nr.noBobbing.getValue() || nr.noCameraShake.getValue())) {
            ci.cancel();
        }
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void spft$noHurtCam(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        NoRender nr = noRender();
        if (nr != null && nr.isEnabled() && nr.noHurtCam.getValue()) {
            ci.cancel();
        }
    }

    private static NoRender noRender() {
        if (SpftClient.get() == null) return null;
        return SpftClient.get().getModuleManager().getModule(NoRender.class);
    }
}

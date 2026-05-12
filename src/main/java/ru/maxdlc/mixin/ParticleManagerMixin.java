package ru.maxdlc.mixin;

import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.maxdlc.MaxDLCClient;
import ru.maxdlc.module.impl.visual.NoRender;

/**
 * Отключает частицы, в том числе при ломании блоков, если включены соответствующие
 * настройки в NoRender.
 */
@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {

    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;", at = @At("HEAD"), cancellable = true)
    private void spft$onAddParticle(ParticleEffect parameters, double x, double y, double z,
                                    double velocityX, double velocityY, double velocityZ,
                                    CallbackInfoReturnable<?> cir) {
        NoRender nr = noRender();
        if (nr == null || !nr.isEnabled()) return;

        if (nr.noAllParticles.getValue()) {
            cir.setReturnValue(null);
            return;
        }
        if (nr.noBlockBreakParticles.getValue()) {
            String id = parameters.getType().toString();
            if (id.contains("block") || id.contains("minecraft:block")) {
                cir.setReturnValue(null);
            }
        }
    }

    private static NoRender noRender() {
        if (MaxDLCClient.get() == null) return null;
        return MaxDLCClient.get().getModuleManager().getModule(NoRender.class);
    }
}

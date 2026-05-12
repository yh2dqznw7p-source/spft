package ru.maxdlc.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.maxdlc.MaxDLCClient;
import ru.maxdlc.module.impl.visual.NoRender;

/**
 * Убирает рендер огня у горящих сущностей (включая самого игрока от 3-го лица),
 * когда NoRender.noFire включён.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void spft$noEntityFire(MatrixStack matrices, VertexConsumerProvider vcp, Entity entity, Quaternionf rotation, CallbackInfo ci) {
        if (MaxDLCClient.get() == null) return;
        NoRender nr = MaxDLCClient.get().getModuleManager().getModule(NoRender.class);
        if (nr != null && nr.isEnabled() && nr.noFire.getValue()) {
            ci.cancel();
        }
    }
}

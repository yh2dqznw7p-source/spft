package ru.maxdlc.module;

import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Контекст 3D-рендера, передаётся в Module.onRender3D.
 */
public record RenderContext(MatrixStack matrices, Camera camera, float tickDelta) {
    public float getPartialTicks() { return tickDelta; }
    public MatrixStack getMatrix() { return matrices; }
}

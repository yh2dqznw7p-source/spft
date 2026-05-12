package ru.maxdlc.module.impl.visual;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.maxdlc.MaxDLCClient;
import ru.maxdlc.module.Category;
import ru.maxdlc.module.Module;
import ru.maxdlc.module.RenderContext;
import ru.maxdlc.module.impl.combat.AimAssist;
import ru.maxdlc.module.impl.combat.KillAura;
import ru.maxdlc.setting.BooleanSetting;
import ru.maxdlc.setting.ModeSetting;
import ru.maxdlc.setting.NumberSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * TargetESP — полный порт из Javelin (7 режимов):
 *   Marker, Spirits, Spirits1, Spirits2, Circle, GhostOrbits, Crystals.
 * Цель берётся из KillAura, если активна; иначе — AimAssist; иначе — crosshair target.
 */
public class TargetEsp extends Module {
    private static final Identifier GLOW_TEXTURE   = Identifier.of("maxdlc", "icons/glow.png");
    private static final Identifier MARKER_TEXTURE = Identifier.of("maxdlc", "icons/marker.png");

    public final ModeSetting mode = addMode("Mode", "Marker",
            "Marker", "Spirits", "Spirits1", "Spirits2", "Circle", "GhostOrbits", "Crystals");
    public final NumberSetting red   = addNumber("Red",   80,  0, 255, 1);
    public final NumberSetting green = addNumber("Green", 150, 0, 255, 1);
    public final NumberSetting blue  = addNumber("Blue",  255, 0, 255, 1);
    public final NumberSetting alpha = addNumber("Alpha", 255, 0, 255, 1);
    public final BooleanSetting onlyAuraTarget = addBoolean("OnlyAuraTarget", true);

    // --- animation state ---
    private float anim   = 0f; // 0..1 smooth fade
    private float anim2  = 0f;
    private Entity lastTarget = null;
    private float rotationAngle = 0f;
    private float rotationSpeed = 0f;
    private boolean isReversing = false;
    private float animationNurik = 0f;
    private long currentTime = System.currentTimeMillis();
    private final long timestamp4 = System.currentTimeMillis();
    private long timestamp5 = System.nanoTime();
    private float value23 = 0f;

    // --- GhostOrbits state ---
    private static final int ORBIT_PARTICLE_COUNT = 3;
    private static final float ORBIT_BASE_RADIUS  = 0.4f;
    private static final float ORBIT_BASE_MUL     = 0.1f;
    private static final float ORBIT_SPEED        = 15.0f;
    private static final int ORBIT_TRAIL_LENGTH   = 40;
    private static final float[] SCALE_CACHE = new float[101];
    static {
        for (int k = 0; k <= 100; k++) SCALE_CACHE[k] = Math.max(0.28f * (k / 100f), 0.15f);
    }
    private final Vec3d[] orbitPositions = new Vec3d[ORBIT_PARTICLE_COUNT];
    private final Vec3d[] orbitMotions   = new Vec3d[ORBIT_PARTICLE_COUNT];
    @SuppressWarnings("unchecked")
    private final List<Vec3d>[] orbitTrails = new List[ORBIT_PARTICLE_COUNT];
    private float movingAngle = 0f;
    private long lastOrbitTime = 0L;
    private float orbitShrinkValue = 0f;

    private float crystalMoving = 0f;

    public TargetEsp() {
        super("TargetESP", "Подсветка цели (7 режимов)", Category.VISUAL);
        for (int i = 0; i < ORBIT_PARTICLE_COUNT; i++) {
            orbitTrails[i] = new ArrayList<>();
            orbitMotions[i] = Vec3d.ZERO;
        }
    }

    private int themeArgb(int alphaOverride) {
        int r = (int) red.getValue();
        int g = (int) green.getValue();
        int b = (int) blue.getValue();
        int a = MathHelper.clamp(alphaOverride, 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int themeArgb() { return themeArgb((int) alpha.getValue()); }

    private Entity pickTarget() {
        MaxDLCClient c = MaxDLCClient.get();
        if (c != null) {
            KillAura ka = c.getModuleManager().getModule(KillAura.class);
            if (ka != null && ka.isEnabled() && ka.getTarget() != null && ka.getTarget().isAlive())
                return ka.getTarget();
            AimAssist aa = c.getModuleManager().getModule(AimAssist.class);
            if (aa != null && aa.isEnabled() && aa.getCurrentTarget() != null && aa.getCurrentTarget().isAlive())
                return aa.getCurrentTarget();
        }
        if (onlyAuraTarget.getValue()) return null;
        if (mc.targetedEntity instanceof LivingEntity le && le.isAlive()) return le;
        return null;
    }

    @Override
    public void onRender3D(RenderContext ctx) {
        Entity target = pickTarget();
        float speed = 0.12f;
        if (target != null) {
            if (lastTarget != target) {
                for (int i = 0; i < ORBIT_PARTICLE_COUNT; i++) {
                    orbitPositions[i] = null;
                    orbitMotions[i] = Vec3d.ZERO;
                    orbitTrails[i].clear();
                }
            }
            lastTarget = target;
            anim  = lerp(anim,  1f, speed);
            anim2 = lerp(anim2, 1f, speed);
        } else {
            anim  = lerp(anim,  0f, speed);
            anim2 = lerp(anim2, 0f, speed);
            if (anim < 0.005f) lastTarget = null;
        }
        if (anim <= 0.01f || lastTarget == null) return;

        switch (mode.getValue()) {
            case "Marker"      -> renderMarker(ctx);
            case "Spirits"     -> drawSpiritsTrack(ctx);
            case "Spirits1"    -> drawSpirits(ctx);
            case "Spirits2"    -> renderNursultan(ctx);
            case "Circle"      -> drawCircle(ctx);
            case "GhostOrbits" -> drawGhostOrbits(ctx);
            case "Crystals"    -> renderCrystals(ctx);
        }
    }

    // ---------------- MARKER ----------------
    private void renderMarker(RenderContext e) {
        Vec3d camPos = e.camera().getPos();
        double td = e.getPartialTicks();
        MatrixStack matrices = e.getMatrix();

        double x = MathHelper.lerp(td, lastTarget.lastRenderX, lastTarget.getX());
        double y = MathHelper.lerp(td, lastTarget.lastRenderY, lastTarget.getY()) + lastTarget.getHeight() / 2.0;
        double z = MathHelper.lerp(td, lastTarget.lastRenderZ, lastTarget.getZ());

        matrices.push();
        matrices.translate(x - camPos.x, y - camPos.y, z - camPos.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-e.camera().getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(e.camera().getPitch()));
        float scale = 0.15f * anim;
        matrices.scale(-scale, -scale, scale);
        updateRotation();
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotationAngle));

        float size = 12f;
        int color = themeArgb((int) (anim * alpha.getValue()));
        drawTexturedQuad(matrices, MARKER_TEXTURE, -size / 2f, -size / 2f, size, size, color);
        matrices.pop();
    }

    private void updateRotation() {
        if (!isReversing) {
            rotationSpeed += 0.01f;
            if (rotationSpeed > 2.3f) { rotationSpeed = 2.3f; isReversing = true; }
        } else {
            rotationSpeed -= 0.01f;
            if (rotationSpeed < -2.3f) { rotationSpeed = -2.3f; isReversing = false; }
        }
        rotationAngle = (rotationAngle + rotationSpeed) % 360f;
    }

    private static double interpolate(double current, double old, double scale) {
        return old + (current - old) * scale;
    }

    // ---------------- SPIRITS (Spirits1) ----------------
    private void drawSpirits(RenderContext e) {
        MatrixStack matrices = e.getMatrix();
        Vec3d camPos = e.camera().getPos();

        double x = interpolate(lastTarget.getX(), lastTarget.lastRenderX, e.getPartialTicks()) - camPos.x;
        double y = interpolate(lastTarget.getY(), lastTarget.lastRenderY, e.getPartialTicks()) - camPos.y + lastTarget.getHeight() / 2.0;
        double z = interpolate(lastTarget.getZ(), lastTarget.lastRenderZ, e.getPartialTicks()) - camPos.z;

        float hurtTime = 0f;
        if (lastTarget instanceof LivingEntity living) {
            hurtTime = (living.hurtTime - (living.hurtTime != 0 ? e.getPartialTicks() : 0f)) / 10f;
        }

        float animValue = -0.15f * anim2 + 0.65f;
        long time = (long) ((System.currentTimeMillis() - timestamp4) / 2f);
        long nanoTime = System.nanoTime();
        float deltaTime = (nanoTime - timestamp5) / 2_000_000f;
        timestamp5 = nanoTime;
        value23 += hurtTime * deltaTime;

        matrices.push();
        matrices.translate(x, y, z);
        matrices.scale(1.5f, 1.5f, 1.5f);

        beginGlowQuads();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        int color = themeArgb((int) Math.min(255, 600 * anim2));
        for (int layer = 0; layer < 3; layer++) {
            for (int i = 0; i < 14; i++) {
                matrices.push();
                float progress = i / 13f;
                float size = (0.55f * (1f - progress) + 0.2f * progress) * anim2;
                double angle = 0.2f * (time + value23 - i * 7f) / 15f;

                float wave = progress < 0.5f ? progress * 2f : (1f - progress) * 2f;
                double amplitude = Math.sin(wave * Math.PI) * 2.0;

                Random rnd = new Random(i * 12345L);
                double oX = (rnd.nextDouble() - 0.5) * amplitude;
                double oY = (rnd.nextDouble() - 0.5) * amplitude;
                double oZ = (rnd.nextDouble() - 0.5) * amplitude;

                double aX = oX * anim2 - oX;
                double aY = oY * anim2 - oY;
                double aZ = oZ * anim2 - oZ;

                double posX = -Math.sin(angle) * animValue;
                double posZ = -Math.cos(angle) * animValue;

                switch (layer) {
                    case 0 -> { aY += i * 0.02; matrices.translate(posX + aX, posZ + aY, -posZ + aZ); }
                    case 1 -> { aY -= i * 0.02; matrices.translate(-posX + aX, posX + aY, -posZ + aZ); }
                    case 2 -> matrices.translate(-posX + aX, -posX + aY, posZ + aZ);
                }

                float ps = size * 0.5f;
                matrices.multiply(e.camera().getRotation());
                Matrix4f m = matrices.peek().getPositionMatrix();
                buffer.vertex(m, -ps, -ps, 0).texture(1, 1).color(color);
                buffer.vertex(m,  ps, -ps, 0).texture(0, 1).color(color);
                buffer.vertex(m,  ps,  ps, 0).texture(0, 0).color(color);
                buffer.vertex(m, -ps,  ps, 0).texture(1, 0).color(color);
                matrices.pop();
            }
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        endGlow();
        matrices.pop();
    }

    // ---------------- SPIRITS 2 (Nursultan) ----------------
    private void renderNursultan(RenderContext e) {
        MatrixStack matrices = e.getMatrix();
        Vec3d camPos = e.camera().getPos();

        double x = interpolate(lastTarget.getX(), lastTarget.lastRenderX, e.getPartialTicks()) - camPos.x;
        double y = interpolate(lastTarget.getY(), lastTarget.lastRenderY, e.getPartialTicks()) - camPos.y + lastTarget.getHeight() / 2.0;
        double z = interpolate(lastTarget.getZ(), lastTarget.lastRenderZ, e.getPartialTicks()) - camPos.z;

        float time = (System.currentTimeMillis() - timestamp4) / 1100f;
        float rotation = time * 360f;
        float radius = 0.5f;

        beginGlowQuads();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        for (int layer = 0; layer < 4; layer++) {
            float layerOffset = (layer - 1) * 0.4f;
            float prevSize = -1f;
            for (float i = 0f; i < 130f; i++) {
                float angle = rotation + i + layerOffset * 360f;
                double rad = Math.toRadians(-angle);
                double yOff = Math.sin(rad + 2) * layerOffset;
                float size = radius * (i / 140f);
                float finalSize = prevSize >= 0 ? (prevSize + size) / 2f : size;
                prevSize = size;
                finalSize *= anim2;

                float a = MathHelper.clamp(finalSize, 0f, 1f);
                int color = themeArgb((int) Math.min(255, 600 * anim2 * a));

                matrices.push();
                matrices.translate(x, y + yOff, z);
                matrices.multiply(e.camera().getRotation());

                float halfSize = finalSize / 2f;
                double ca = Math.cos(rad) * radius - halfSize;
                double sa = Math.sin(rad) * radius - halfSize;

                Matrix4f m = matrices.peek().getPositionMatrix();
                buffer.vertex(m, (float) ca, -halfSize, (float) sa).texture(0, 1).color(color);
                buffer.vertex(m, (float) (ca + finalSize), -halfSize, (float) sa).texture(1, 1).color(color);
                buffer.vertex(m, (float) (ca + finalSize),  halfSize, (float) sa).texture(1, 0).color(color);
                buffer.vertex(m, (float) ca,  halfSize, (float) sa).texture(0, 0).color(color);
                matrices.pop();
            }
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        endGlow();
    }

    // ---------------- CIRCLE ----------------
    private void drawCircle(RenderContext e) {
        MatrixStack matrices = e.getMatrix();
        Vec3d camPos = e.camera().getPos();

        double x = interpolate(lastTarget.getX(), lastTarget.lastRenderX, e.getPartialTicks()) - camPos.x;
        double y = interpolate(lastTarget.getY(), lastTarget.lastRenderY, e.getPartialTicks()) - camPos.y;
        double z = interpolate(lastTarget.getZ(), lastTarget.lastRenderZ, e.getPartialTicks()) - camPos.z;

        float height = lastTarget.getHeight();
        short period = 1500;
        double tm = System.currentTimeMillis() % (long) period;
        boolean ascending = tm > period / 2.0;
        float progress = (float) (tm / (period / 2f));
        if (ascending) progress -= 1f; else progress = 1f - progress;
        progress = progress < 0.5 ? 2f * progress * progress
                : (float) (1.0 - Math.pow(-2f * progress + 2f, 2.0) / 2.0);

        double yOffset = height / 2f * (progress > 0.5 ? 1f - progress : progress) * (ascending ? -1 : 1);

        matrices.push();
        matrices.translate(x, y + height * progress + yOffset, z);

        float hurtTime = 0f;
        if (lastTarget instanceof LivingEntity living) {
            hurtTime = (living.hurtTime - (living.hurtTime != 0 ? e.getPartialTicks() : 0f)) / 10f;
        }

        long timeMs = (long) ((System.currentTimeMillis() - timestamp4) / 2.5f);
        long nanoTime = System.nanoTime();
        float deltaTime = (nanoTime - timestamp5) / 2_000_000f;
        timestamp5 = nanoTime;
        value23 += hurtTime * deltaTime;

        beginGlowQuads();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        int color = themeArgb((int) Math.min(255, 400 * anim2));

        for (int layer = 0; layer < 4; layer++) {
            for (int i = 0; i < 15; i++) {
                matrices.push();
                float pp = i / 14f;
                float size = (0.5f * (1f - pp) + 0.5f * pp) * anim2;
                float angle = 0.2f * (timeMs + value23 - i * 3.5f) / 15f;

                float wave = pp < 0.5f ? pp * 2f : (1f - pp) * 2f;
                double amplitude = Math.sin(wave * Math.PI) * 2.0;

                Random rnd = new Random(i * 12345L);
                double oX = (rnd.nextDouble() - 0.5) * amplitude;
                double oY = (rnd.nextDouble() - 0.5) * amplitude;
                double oZ = (rnd.nextDouble() - 0.5) * amplitude;

                double aX = oX * anim2 - oX;
                double aY = oY * anim2 - oY;
                double aZ = oZ * anim2 - oZ;

                double r = 0.7;
                switch (layer) {
                    case 0 -> matrices.translate( Math.cos(angle) * r + aX, aY,  Math.sin(angle) * r + aZ);
                    case 1 -> matrices.translate(-Math.sin(angle) * r + aX, aY,  Math.cos(angle) * r + aZ);
                    case 2 -> matrices.translate(-Math.cos(angle) * r + aX, aY, -Math.sin(angle) * r + aZ);
                    case 3 -> matrices.translate( Math.sin(angle) * r + aX, aY, -Math.cos(angle) * r + aZ);
                }

                float ps = size * 0.5f;
                matrices.multiply(e.camera().getRotation());
                Matrix4f m = matrices.peek().getPositionMatrix();
                buffer.vertex(m, -ps, -ps, 0).texture(1, 1).color(color);
                buffer.vertex(m,  ps, -ps, 0).texture(0, 1).color(color);
                buffer.vertex(m,  ps,  ps, 0).texture(0, 0).color(color);
                buffer.vertex(m, -ps,  ps, 0).texture(1, 0).color(color);
                matrices.pop();
            }
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        endGlow();
        matrices.pop();
    }

    // ---------------- SPIRITS TRACK ----------------
    private void drawSpiritsTrack(RenderContext event3D) {
        if (anim2 == 0f) return;
        MatrixStack e = event3D.getMatrix();
        if (lastTarget == null) return;

        long currentTimeMs = System.currentTimeMillis();
        animationNurik += (currentTimeMs - currentTime) / 120f;
        currentTime = currentTimeMs;

        beginGlowQuads();

        Vec3d camPos = event3D.camera().getPos();
        double x = interpolate(lastTarget.getX(), lastTarget.lastRenderX, event3D.getPartialTicks()) - camPos.x;
        double y = interpolate(lastTarget.getY(), lastTarget.lastRenderY, event3D.getPartialTicks()) - camPos.y;
        double z = interpolate(lastTarget.getZ(), lastTarget.lastRenderZ, event3D.getPartialTicks()) - camPos.z;

        int n2 = 3, n3 = 12, n4 = 3 * n2;
        e.push();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        int rgba = themeArgb((int) Math.min(255, 600 * anim2));
        for (int i = 0; i < n4; i += n2) {
            for (int j = 0; j < n3; j++) {
                float f2 = animationNurik + j * 0.1f;
                int n5 = (int) Math.pow(i, 2);
                e.push();
                e.translate(
                        x + 0.8f * MathHelper.sin(f2 + n5),
                        y + 0.5 + 0.3f * MathHelper.sin(animationNurik + j * 0.2f) + 0.2f * i,
                        z + 0.8f * MathHelper.cos(f2 - n5)
                );
                float s = anim2 * (0.005f + j / 2000f);
                e.scale(s, s, s);
                e.multiply(event3D.camera().getRotation());
                int n7 = -25, n8 = 50;
                Matrix4f m = e.peek().getPositionMatrix();
                buffer.vertex(m, n7, n7 + n8, 0).texture(0, 1).color(rgba);
                buffer.vertex(m, n7 + n8, n7 + n8, 0).texture(1, 1).color(rgba);
                buffer.vertex(m, n7 + n8, n7, 0).texture(1, 0).color(rgba);
                buffer.vertex(m, n7, n7, 0).texture(0, 0).color(rgba);
                e.pop();
            }
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        e.pop();
        endGlow();
    }

    // ---------------- GHOST ORBITS ----------------
    private void drawGhostOrbits(RenderContext e) {
        if (lastTarget == null) return;

        MatrixStack matrices = e.getMatrix();
        Vec3d camPos = e.camera().getPos();
        float delta = e.getPartialTicks();
        Camera camera = e.camera();

        double tx = interpolate(lastTarget.getX(), lastTarget.lastRenderX, delta);
        double ty = interpolate(lastTarget.getY(), lastTarget.lastRenderY, delta);
        double tz = interpolate(lastTarget.getZ(), lastTarget.lastRenderZ, delta);
        Vec3d targetCenter = new Vec3d(tx, ty + lastTarget.getHeight() / 2.0, tz);

        long now = System.currentTimeMillis();
        if (lastOrbitTime == 0) lastOrbitTime = now;
        float dtMs = now - lastOrbitTime;
        lastOrbitTime = now;

        int fps = mc.getCurrentFps();
        float fpsFactor = 500f / Math.max(fps, 10);
        movingAngle += (20f * dtMs / 16.667f) * (ORBIT_SPEED / 55f);

        boolean isHurt = lastTarget instanceof LivingEntity living && living.hurtTime > 7;
        orbitShrinkValue = lerp(orbitShrinkValue, isHurt ? 1f : 0f, 0.15f);

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, GLOW_TEXTURE);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(770, 1);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        for (int i = 0; i < ORBIT_PARTICLE_COUNT; i++) {
            float angleOffset = i * 360f / ORBIT_PARTICLE_COUNT;
            float currentAngle = movingAngle + angleOffset;
            double radian = Math.toRadians(currentAngle);

            float orbitRadius = ORBIT_BASE_RADIUS - orbitShrinkValue * ORBIT_BASE_RADIUS;
            float ox = (float) Math.sin(radian) * orbitRadius;
            float oz = (float) Math.cos(radian) * orbitRadius;
            double oy = 0.3 * Math.sin(Math.toRadians(movingAngle / (i + 1f)));

            Vec3d targetGhostPos = targetCenter.add(ox, oy, oz);

            if (orbitPositions[i] == null || orbitPositions[i].distanceTo(targetGhostPos) > 10) {
                orbitPositions[i] = targetGhostPos;
                orbitMotions[i] = Vec3d.ZERO;
            }

            float mul = ORBIT_BASE_MUL * fpsFactor;
            Vec3d diff = targetGhostPos.subtract(orbitPositions[i]);
            orbitMotions[i] = diff.multiply(mul, mul, mul);
            orbitPositions[i] = orbitPositions[i].add(orbitMotions[i]);

            if (orbitTrails[i].isEmpty() || orbitTrails[i].get(0).distanceTo(orbitPositions[i]) > 0.01) {
                orbitTrails[i].add(0, orbitPositions[i]);
                while (orbitTrails[i].size() > ORBIT_TRAIL_LENGTH) orbitTrails[i].remove(orbitTrails[i].size() - 1);
            }

            for (int j = 0; j < orbitTrails[i].size(); j++) {
                Vec3d p = orbitTrails[i].get(j);
                float offset = 1f - (float) j / ORBIT_TRAIL_LENGTH;

                matrices.push();
                matrices.translate(p.x - camPos.x, p.y - camPos.y, p.z - camPos.z);
                matrices.multiply(camera.getRotation());
                Matrix4f m = matrices.peek().getPositionMatrix();

                float opacity = (float) Math.pow(offset, 1.8) * anim2 * 0.7f;
                int color = themeArgb((int) (opacity * 255));
                float scale = SCALE_CACHE[Math.min((int) (offset * 100), 100)] * 0.8f;

                buffer.vertex(m, -scale,  scale, 0).texture(0, 1).color(color);
                buffer.vertex(m,  scale,  scale, 0).texture(1, 1).color(color);
                buffer.vertex(m,  scale, -scale, 0).texture(1, 0).color(color);
                buffer.vertex(m, -scale, -scale, 0).texture(0, 0).color(color);
                matrices.pop();
            }

            if (!orbitTrails[i].isEmpty()) {
                Vec3d head = orbitTrails[i].get(0);
                matrices.push();
                matrices.translate(head.x - camPos.x, head.y - camPos.y, head.z - camPos.z);
                matrices.multiply(camera.getRotation());
                Matrix4f m = matrices.peek().getPositionMatrix();

                float headScale = 0.35f * anim2;
                int headColor = themeArgb((int) (120 * anim2));

                buffer.vertex(m, -headScale,  headScale, 0).texture(0, 1).color(headColor);
                buffer.vertex(m,  headScale,  headScale, 0).texture(1, 1).color(headColor);
                buffer.vertex(m,  headScale, -headScale, 0).texture(1, 0).color(headColor);
                buffer.vertex(m, -headScale, -headScale, 0).texture(0, 0).color(headColor);
                matrices.pop();
            }
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        endGlow();
    }

    // ---------------- CRYSTALS ----------------
    private void renderCrystals(RenderContext e) {
        float a = anim2;
        if (a <= 0f || lastTarget == null || mc.player == null) return;

        MatrixStack matrices = e.getMatrix();
        Vec3d camPos = e.camera().getPos();
        float tickDelta = e.getPartialTicks();

        double tx = interpolate(lastTarget.getX(), lastTarget.lastRenderX, tickDelta);
        double ty = interpolate(lastTarget.getY(), lastTarget.lastRenderY, tickDelta);
        double tz = interpolate(lastTarget.getZ(), lastTarget.lastRenderZ, tickDelta);

        crystalMoving += 1f;

        float entityHeight = lastTarget.getHeight();
        float width = lastTarget.getWidth() * 1.5f;

        int cr = (int) red.getValue();
        int cg = (int) green.getValue();
        int cb = (int) blue.getValue();
        int crystalAlpha = Math.min(255, (int) (a * alpha.getValue()));

        int cTop   = (crystalAlpha << 24) | (Math.min(255, cr + 60) << 16) | (Math.min(255, cg + 60) << 8) | Math.min(255, cb + 60);
        int cSide1 = (crystalAlpha << 24) | (Math.min(255, cr + 30) << 16) | (Math.min(255, cg + 30) << 8) | Math.min(255, cb + 30);
        int cSide2 = (crystalAlpha << 24) | (cr << 16) | (cg << 8) | cb;
        int cBot   = (crystalAlpha << 24) | (Math.max(0, cr - 30) << 16) | (Math.max(0, cg - 30) << 8) | Math.max(0, cb - 30);

        matrices.push();
        matrices.translate(tx - camPos.x, ty - camPos.y, tz - camPos.z);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(770, 771);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder crystalBuffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        float cw = 0.075f, ch = 0.20f;

        for (int i = 0; i < 360; i += 19) {
            float val = 1.2f - 0.5f * a;
            float angleDeg = i + crystalMoving * 0.3f;
            float rad = (float) Math.toRadians(angleDeg);
            float sin = (float) (Math.sin(rad) * width * val);
            float cos = (float) (Math.cos(rad) * width * val);
            float heightPrc = ((i / 20f) * 0.6180339f) % 1f;
            float crystalY = entityHeight * heightPrc;

            matrices.push();
            matrices.translate(sin, crystalY, cos);

            Vector3f dir = new Vector3f(-sin, 0, -cos).normalize();
            Quaternionf rot = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), dir);
            matrices.multiply(rot);

            Matrix4f m = matrices.peek().getPositionMatrix();
            float[] ex = { cw, 0, -cw, 0 };
            float[] ez = { 0, cw, 0, -cw };

            for (int j = 0; j < 4; j++) {
                int next = (j + 1) % 4;
                int fc = (j % 2 == 0) ? cTop : cSide1;
                crystalBuffer.vertex(m, 0, ch, 0).color(fc);
                crystalBuffer.vertex(m, ex[j], 0, ez[j]).color(fc);
                crystalBuffer.vertex(m, ex[next], 0, ez[next]).color(fc);
            }
            for (int j = 0; j < 4; j++) {
                int next = (j + 1) % 4;
                int fc = (j % 2 == 0) ? cBot : cSide2;
                crystalBuffer.vertex(m, 0, -ch, 0).color(fc);
                crystalBuffer.vertex(m, ex[next], 0, ez[next]).color(fc);
                crystalBuffer.vertex(m, ex[j], 0, ez[j]).color(fc);
            }
            matrices.pop();
        }
        BufferRenderer.drawWithGlobalProgram(crystalBuffer.end());

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, GLOW_TEXTURE);
        RenderSystem.blendFunc(770, 1);

        BufferBuilder glow = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        Camera camera = e.camera();
        for (int i = 0; i < 360; i += 19) {
            float val = 1.2f - 0.5f * a;
            float angleDeg = i + crystalMoving * 0.3f;
            float rad = (float) Math.toRadians(angleDeg);
            float sin = (float) (Math.sin(rad) * width * val);
            float cos = (float) (Math.cos(rad) * width * val);
            float heightPrc = ((i / 20f) * 0.6180339f) % 1f;
            float crystalY = entityHeight * heightPrc;

            matrices.push();
            matrices.translate(sin, crystalY, cos);
            matrices.multiply(camera.getRotation());

            Matrix4f m = matrices.peek().getPositionMatrix();
            float glowSize = 0.15f * a;
            int glowAlpha = (int) (a * 100);
            int glowColor = (glowAlpha << 24) | (cr << 16) | (cg << 8) | cb;

            glow.vertex(m, -glowSize,  glowSize, 0).texture(0, 1).color(glowColor);
            glow.vertex(m,  glowSize,  glowSize, 0).texture(1, 1).color(glowColor);
            glow.vertex(m,  glowSize, -glowSize, 0).texture(1, 0).color(glowColor);
            glow.vertex(m, -glowSize, -glowSize, 0).texture(0, 0).color(glowColor);
            matrices.pop();
        }
        BufferRenderer.drawWithGlobalProgram(glow.end());

        matrices.pop();

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    // ---------------- helpers ----------------
    private static void beginGlowQuads() {
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, GLOW_TEXTURE);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(770, 1, 0, 1);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
    }
    private static void endGlow() {
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.blendFunc(770, 771);
        RenderSystem.enableCull();
    }

    private static void drawTexturedQuad(MatrixStack matrices, Identifier tex,
                                         float x, float y, float w, float h, int argb) {
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f m = matrices.peek().getPositionMatrix();
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        b.vertex(m, x,     y + h, 0).texture(0, 1).color(argb);
        b.vertex(m, x + w, y + h, 0).texture(1, 1).color(argb);
        b.vertex(m, x + w, y,     0).texture(1, 0).color(argb);
        b.vertex(m, x,     y,     0).texture(0, 0).color(argb);
        BufferRenderer.drawWithGlobalProgram(b.end());
        RenderSystem.disableBlend();
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}

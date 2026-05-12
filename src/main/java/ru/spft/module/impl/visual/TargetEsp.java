package ru.spft.module.impl.visual;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import ru.spft.SpftClient;
import ru.spft.module.Category;
import ru.spft.module.Module;
import ru.spft.module.impl.combat.AimAssist;
import ru.spft.setting.BooleanSetting;
import ru.spft.setting.ModeSetting;
import ru.spft.setting.NumberSetting;

/**
 * TargetESP — подсветка целей. 3 режима:
 *  - Box        — простая рамка вокруг цели.
 *  - Outline    — обводка по граням хитбокса (толстые линии).
 *  - TwoDee     — плоский 2D-прямоугольник вокруг AimAssist-цели.
 */
public class TargetEsp extends Module {
    public final ModeSetting mode     = addMode("Mode", "Box", "Box", "Outline", "TwoDee");
    public final BooleanSetting players = addBoolean("Players", true);
    public final BooleanSetting mobs    = addBoolean("Mobs", false);
    public final BooleanSetting onlyAimTarget = addBoolean("OnlyAimTarget", false);
    public final NumberSetting range  = addNumber("Range", 32, 4, 128, 1);
    public final NumberSetting red    = addNumber("Red", 255, 0, 255, 1);
    public final NumberSetting green  = addNumber("Green", 60, 0, 255, 1);
    public final NumberSetting blue   = addNumber("Blue", 60, 0, 255, 1);
    public final NumberSetting alpha  = addNumber("Alpha", 180, 20, 255, 5);

    public TargetEsp() {
        super("TargetESP", "Подсветка целей (3 режима)", Category.VISUAL);
    }

    @Override
    public void onRender(float tickDelta) {
        if (mc.world == null || mc.player == null) return;
        Camera camera = mc.gameRenderer.getCamera();
        Vec3d camPos = camera.getPos();

        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();

        MatrixStack matrices = new MatrixStack();
        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

        AimAssist aim = SpftClient.get().getModuleManager().getModule(AimAssist.class);
        Entity aimTarget = (aim != null && aim.isEnabled()) ? aim.getCurrentTarget() : null;

        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player) continue;
            if (!(e instanceof LivingEntity le) || !le.isAlive()) continue;
            if (le instanceof PlayerEntity p) {
                if (!players.getValue()) continue;
                if (p.isSpectator()) continue;
            } else {
                if (!mobs.getValue()) continue;
            }
            if (onlyAimTarget.getValue() && e != aimTarget) continue;
            if (mc.player.squaredDistanceTo(e) > range.getValue() * range.getValue()) continue;

            Box box = e.getBoundingBox();
            int r = (int) red.getValue();
            int g = (int) green.getValue();
            int b = (int) blue.getValue();
            int a = (int) alpha.getValue();

            switch (mode.getValue()) {
                case "Box"     -> drawBox(matrices, immediate, box, r, g, b, a);
                case "Outline" -> drawOutline(matrices, immediate, box, r, g, b, a);
                case "TwoDee"  -> drawTwoDee(matrices, immediate, e, tickDelta, r, g, b, a);
            }
        }

        matrices.pop();
        immediate.draw();
    }

    private void drawBox(MatrixStack matrices, VertexConsumerProvider vcp,
                         Box box, int r, int g, int b, int a) {
        var consumer = vcp.getBuffer(RenderLayer.getLines());
        Matrix4f m = matrices.peek().getPositionMatrix();
        var n = matrices.peek();
        // используем встроенный helper из DebugRenderer-like: рисуем 12 рёбер куба
        drawBoxLines(consumer, m, n.getNormalMatrix(), (float) box.minX, (float) box.minY, (float) box.minZ,
                (float) box.maxX, (float) box.maxY, (float) box.maxZ, r / 255f, g / 255f, b / 255f, a / 255f);
    }

    private void drawOutline(MatrixStack matrices, VertexConsumerProvider vcp,
                             Box box, int r, int g, int b, int a) {
        // тот же контур, но чуть раздутый для эффекта "outline"
        Box expanded = box.expand(0.05);
        drawBox(matrices, vcp, expanded, r, g, b, a);
    }

    private void drawTwoDee(MatrixStack matrices, VertexConsumerProvider vcp,
                            Entity e, float tickDelta, int r, int g, int b, int a) {
        // Плоский квадрат — имитация 2D-рамки в мире. Простейшая версия —
        // рисуем 4 линии-рёбра вертикальной "таблички" чуть шире игрока.
        Vec3d pos = e.getLerpedPos(tickDelta);
        double halfW = e.getWidth() / 2.0 + 0.15;
        double h = e.getHeight() + 0.15;

        double minX = pos.x - halfW;
        double maxX = pos.x + halfW;
        double minY = pos.y - 0.05;
        double maxY = pos.y + h;
        double z = pos.z;

        var consumer = vcp.getBuffer(RenderLayer.getLines());
        Matrix4f m = matrices.peek().getPositionMatrix();
        var n = matrices.peek().getNormalMatrix();
        float rf = r / 255f, gf = g / 255f, bf = b / 255f, af = a / 255f;

        line(consumer, m, n, (float) minX, (float) minY, (float) z, (float) maxX, (float) minY, (float) z, rf, gf, bf, af);
        line(consumer, m, n, (float) maxX, (float) minY, (float) z, (float) maxX, (float) maxY, (float) z, rf, gf, bf, af);
        line(consumer, m, n, (float) maxX, (float) maxY, (float) z, (float) minX, (float) maxY, (float) z, rf, gf, bf, af);
        line(consumer, m, n, (float) minX, (float) maxY, (float) z, (float) minX, (float) minY, (float) z, rf, gf, bf, af);
    }

    private static void drawBoxLines(net.minecraft.client.render.VertexConsumer c,
                                     Matrix4f m, org.joml.Matrix3f n,
                                     float x1, float y1, float z1, float x2, float y2, float z2,
                                     float r, float g, float b, float a) {
        // нижняя грань
        line(c, m, n, x1, y1, z1, x2, y1, z1, r, g, b, a);
        line(c, m, n, x2, y1, z1, x2, y1, z2, r, g, b, a);
        line(c, m, n, x2, y1, z2, x1, y1, z2, r, g, b, a);
        line(c, m, n, x1, y1, z2, x1, y1, z1, r, g, b, a);
        // верхняя грань
        line(c, m, n, x1, y2, z1, x2, y2, z1, r, g, b, a);
        line(c, m, n, x2, y2, z1, x2, y2, z2, r, g, b, a);
        line(c, m, n, x2, y2, z2, x1, y2, z2, r, g, b, a);
        line(c, m, n, x1, y2, z2, x1, y2, z1, r, g, b, a);
        // вертикальные
        line(c, m, n, x1, y1, z1, x1, y2, z1, r, g, b, a);
        line(c, m, n, x2, y1, z1, x2, y2, z1, r, g, b, a);
        line(c, m, n, x2, y1, z2, x2, y2, z2, r, g, b, a);
        line(c, m, n, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }

    private static void line(net.minecraft.client.render.VertexConsumer c,
                             Matrix4f m, org.joml.Matrix3f n,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float r, float g, float b, float a) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float len = MathHelper.sqrt(dx * dx + dy * dy + dz * dz);
        if (len == 0) return;
        dx /= len; dy /= len; dz /= len;
        c.vertex(m, x1, y1, z1).color(r, g, b, a).normal(n, dx, dy, dz);
        c.vertex(m, x2, y2, z2).color(r, g, b, a).normal(n, dx, dy, dz);
    }
}

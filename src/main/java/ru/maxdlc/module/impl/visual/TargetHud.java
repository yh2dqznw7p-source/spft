package ru.maxdlc.module.impl.visual;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import ru.maxdlc.MaxDLCClient;
import ru.maxdlc.module.Category;
import ru.maxdlc.module.Module;
import ru.maxdlc.module.impl.combat.KillAura;
import ru.maxdlc.setting.BooleanSetting;
import ru.maxdlc.setting.NumberSetting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * TargetHUD — панель текущей цели KillAura:
 *  - голова (скин) либо иконка моба-заглушка,
 *  - имя,
 *  - кольцевой HP-бар с градиентом,
 *  - ряд предметов (off-hand / броня / main-hand) с индикатором прочности.
 *
 * Рисуем через HudRenderCallback (InGameHudMixin@TAIL вызовет onRender косвенно),
 * но проще привязать напрямую к Fabric API HUD-колбэку. Здесь используем onRender() модуля,
 * который вызывается из рендер-цикла WorldRender'а, поэтому рисуем оверлей через
 * статический хук ниже (см. MaxDLCClient#onHud не требуется — InGameHudMixin уже
 * имеет @Inject в render#TAIL — используем его).
 */
public class TargetHud extends Module {
    public final NumberSetting hudX = addNumber("X", 40, 0, 4000, 1);
    public final NumberSetting hudY = addNumber("Y", 40, 0, 4000, 1);
    public final NumberSetting scale = addNumber("Scale", 1.0, 0.5, 2.0, 0.05);
    public final BooleanSetting showItems = addBoolean("ShowItems", true);
    public final BooleanSetting animate = addBoolean("Animate", true);

    // animation state
    private float alphaAnim = 0f;
    private float hpAnim = 1f;
    private LivingEntity lastTarget = null;

    public TargetHud() {
        super("TargetHUD", "HUD с информацией о цели KillAura", Category.VISUAL);
    }

    /** Вызывается из InGameHudMixin#render TAIL для каждого включённого модуля, если он живой. */
    public void draw(DrawContext context) {
        if (mc.player == null) return;
        MaxDLCClient client = MaxDLCClient.get();
        if (client == null) return;
        KillAura ka = client.getModuleManager().getModule(KillAura.class);

        LivingEntity target = null;
        if (ka != null && ka.isEnabled() && ka.getTarget() != null && ka.getTarget().isAlive()) {
            target = ka.getTarget();
        } else if (mc.targetedEntity instanceof LivingEntity le && le.isAlive()) {
            target = le;
        }

        float speed = animate.getValue() ? 0.10f : 1f;
        if (target != null) {
            lastTarget = target;
            alphaAnim = lerp(alphaAnim, 1f, speed);
        } else {
            alphaAnim = lerp(alphaAnim, 0f, speed);
        }

        if (alphaAnim <= 0.02f || lastTarget == null) return;

        renderPanel(context, lastTarget);
    }

    private void renderPanel(DrawContext context, LivingEntity entity) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        float s = scale.getFloat();

        String name = entity.getName().getString();
        float nameWidth = tr.getWidth(name);
        float width  = (Math.max(110f, 36 + nameWidth + 35f + 10f));
        float height = 36f;

        float x = hudX.getFloat();
        float y = hudY.getFloat();
        int alphaI = (int) (255 * alphaAnim);

        // scale
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().scale(s, s, 1);

        // bg
        int bgColor = rgba(20, 20, 20, (int) (190 * alphaAnim));
        context.fill(0, 0, (int) width, (int) height, bgColor);

        // head
        float headSize = 30f;
        float headX = 3f, headY = 3f;
        float hurt = entity.hurtTime / 10f;
        int headTint = rgba(255,
                (int) (255 * (1 - hurt)),
                (int) (255 * (1 - hurt)),
                alphaI);

        if (entity instanceof AbstractClientPlayerEntity ape) {
            try {
                Identifier skin = ape.getSkinTextures().texture();
                // face (8,8)-(16,16) и hat (40,8)-(48,16)
                context.drawTexture(RenderLayer::getGuiTextured, skin,
                        (int) headX, (int) headY,
                        8f, 8f, (int) headSize, (int) headSize,
                        8, 8, 64, 64);
                context.drawTexture(RenderLayer::getGuiTextured, skin,
                        (int) headX, (int) headY,
                        40f, 8f, (int) headSize, (int) headSize,
                        8, 8, 64, 64);
            } catch (Exception ignored) {
                context.fill((int) headX, (int) headY,
                        (int) (headX + headSize), (int) (headY + headSize), headTint);
            }
        } else {
            // заглушка для мобов
            context.fill((int) headX, (int) headY,
                    (int) (headX + headSize), (int) (headY + headSize), rgba(80, 80, 80, alphaI));
        }

        // name
        float textX = 36f;
        context.drawText(tr, name, (int) textX, (int) 5, rgba(255, 255, 255, alphaI), true);

        // circular HP
        float circleX = width - 18f;
        float circleY = height / 2f;
        float outerR = 12f;
        float innerR = 10.5f;

        float cur = Math.max(0, entity.getHealth());
        float max = Math.max(1, entity.getMaxHealth());
        float pct = MathHelper.clamp(cur / max, 0f, 1f);
        hpAnim = lerp(hpAnim, pct, 0.15f);

        int hpCol = healthColor(pct, alphaI);
        int hpBg  = rgba(40, 40, 40, (int) (150 * alphaAnim));
        drawCircularProgress(context.getMatrices().peek().getPositionMatrix(),
                circleX, circleY, outerR, innerR, hpAnim, hpCol, hpBg, alphaI);

        String hpText = String.format("%.1f", cur);
        float hpTextW = tr.getWidth(hpText);
        context.drawText(tr, hpText,
                (int) (circleX - hpTextW / 2f),
                (int) (circleY - tr.fontHeight / 2f),
                rgba(255, 255, 255, alphaI), true);

        // items
        if (showItems.getValue()) {
            drawEquipment(context, entity, (int) textX, 14, alphaAnim);
        }

        context.getMatrices().pop();
    }

    private void drawEquipment(DrawContext context, LivingEntity entity, int startX, int startY, float alpha) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        List<ItemStack> items = new ArrayList<>();
        items.add(entity.getMainHandStack());
        for (ItemStack stack : entity.getArmorItems()) items.add(stack);
        items.add(entity.getOffHandStack());
        Collections.reverse(items);

        float itemScale = 0.5f;
        float slotSize = 16 * itemScale;
        float itemX = startX;

        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 100);

        for (ItemStack stack : items) {
            if (stack.isEmpty()) {
                itemX += slotSize;
                continue;
            }

            context.getMatrices().push();
            context.getMatrices().translate(itemX, startY, 0);
            context.getMatrices().scale(itemScale, itemScale, 1);
            context.drawItem(stack, 0, 0);

            if (stack.getCount() > 1) {
                String countText = String.valueOf(stack.getCount());
                context.getMatrices().push();
                context.getMatrices().scale(1f / itemScale, 1f / itemScale, 1);
                context.drawText(tr, countText,
                        (int) (10 * itemScale - tr.getWidth(countText)),
                        (int) (10 * itemScale),
                        0xFFFFFFFF, true);
                context.getMatrices().pop();
            }

            if (stack.isDamageable() && stack.getDamage() > 0) {
                float percent = 1.0f - ((float) stack.getDamage() / stack.getMaxDamage());
                int color = percent > 0.5f ? 0xFF00FF00 : percent > 0.25f ? 0xFFFFFF00 : 0xFFFF0000;
                int barX = 1, barY = 13, barW = 13, barH = 2;
                context.fill(barX, barY, barX + barW, barY + barH, 0xFF000000);
                context.fill(barX, barY, barX + (int) (barW * percent), barY + barH, color);
            }

            context.getMatrices().pop();
            itemX += slotSize;
        }
        context.getMatrices().pop();
    }

    // ---- helpers ----
    private static int rgba(int r, int g, int b, int a) {
        return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }

    private static int healthColor(float pct, int alpha) {
        int r, g, b = 60;
        if (pct > 0.5f) {
            float t = (pct - 0.5f) / 0.5f;
            r = (int) (255 * (1 - t));
            g = 255;
        } else {
            float t = pct / 0.5f;
            r = 255;
            g = (int) (255 * t);
        }
        return rgba(r, g, b, alpha);
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    /**
     * Кольцевой прогресс-бар с градиентом и antialiasing на краях.
     */
    private static void drawCircularProgress(Matrix4f matrix,
                                             float cx, float cy,
                                             float outerR, float innerR,
                                             float progress,
                                             int progressColor, int bgColor, int alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

        int segments = 180;
        float step = (float) (2 * Math.PI / segments);
        float startA = (float) (-Math.PI / 2);
        float aa = 0.5f;

        int bgR = (bgColor >> 16) & 0xFF, bgG = (bgColor >> 8) & 0xFF, bgB = bgColor & 0xFF;

        // --- background ring: outer AA
        BufferBuilder b = Tessellator.getInstance()
                .begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= segments; i++) {
            float a = startA + step * i;
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            b.vertex(matrix, cx + c * (outerR + aa), cy + s * (outerR + aa), 0)
                    .color(rgba(bgR, bgG, bgB, 0));
            b.vertex(matrix, cx + c * outerR, cy + s * outerR, 0).color(bgColor);
        }
        BufferRenderer.drawWithGlobalProgram(b.end());

        // --- background ring: body
        b = Tessellator.getInstance()
                .begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= segments; i++) {
            float a = startA + step * i;
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            b.vertex(matrix, cx + c * outerR, cy + s * outerR, 0).color(bgColor);
            b.vertex(matrix, cx + c * innerR, cy + s * innerR, 0).color(bgColor);
        }
        BufferRenderer.drawWithGlobalProgram(b.end());

        // --- background ring: inner AA
        b = Tessellator.getInstance()
                .begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= segments; i++) {
            float a = startA + step * i;
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            b.vertex(matrix, cx + c * innerR, cy + s * innerR, 0).color(bgColor);
            b.vertex(matrix, cx + c * (innerR - aa), cy + s * (innerR - aa), 0)
                    .color(rgba(bgR, bgG, bgB, 0));
        }
        BufferRenderer.drawWithGlobalProgram(b.end());

        // --- progress
        if (progress > 0.001f) {
            int segs = Math.max(1, (int) (segments * progress));
            int r = (progressColor >> 16) & 0xFF;
            int g = (progressColor >> 8) & 0xFF;
            int bl = progressColor & 0xFF;

            b = Tessellator.getInstance()
                    .begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
            for (int i = 0; i <= segs; i++) {
                float a = startA + step * i;
                float c = (float) Math.cos(a), s = (float) Math.sin(a);
                float t = (float) i / segs;
                float bright = 1f - t * 0.3f;
                int cr = (int) (r * bright), cg = (int) (g * bright), cb = (int) (bl * bright);
                int fill = rgba(cr, cg, cb, alpha);
                b.vertex(matrix, cx + c * outerR, cy + s * outerR, 0).color(fill);
                b.vertex(matrix, cx + c * innerR, cy + s * innerR, 0).color(fill);
            }
            BufferRenderer.drawWithGlobalProgram(b.end());
        }

        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        RenderSystem.disableBlend();
    }
}

package ru.maxdlc.gui.menu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Кастомное главное меню Max DLC — порт ru.levin.screens.mainmenu.MainMenu на 1.21.4,
 * без Levin-шрифтов/частиц-утилит. Часы убраны, заголовок "Max DLC".
 */
public class MaxDLCMainMenu extends Screen {

    private final String title = "Max DLC";
    private final String version = "v1.0";

    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();

    private Btn singleBtn, multiBtn, altBtn;
    private ComboBtn optionsQuit;

    private int shakeTime = 0;
    private float shakeY = 0;
    private float fadeIn = 0;

    public MaxDLCMainMenu() {
        super(Text.literal("Max DLC"));
    }

    @Override
    protected void init() {
        super.init();
        fadeIn = 0;
        particles.clear();
        for (int i = 0; i < 50; i++) particles.add(new Particle());

        int btnW = 200, btnH = 30;
        singleBtn = new Btn("Singleplayer", 0, 0, btnW, btnH);
        multiBtn  = new Btn("Multiplayer", 0, 0, btnW, btnH);
        altBtn    = new Btn("AltManager", 0, 0, btnW, btnH);
        optionsQuit = new ComboBtn(0, 0, btnW, btnH, "Options", "Quit");
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (fadeIn < 1f) fadeIn = Math.min(1f, fadeIn + 0.02f);

        MenuUI.animatedBackground(ctx, this.width, this.height);

        for (Particle p : particles) {
            p.update();
            p.render(ctx);
        }

        if (shakeTime > 0) {
            shakeTime--;
            shakeY = (float) (Math.sin(shakeTime * 0.5) * 3);
        } else shakeY = 0;

        TextRenderer tr = this.textRenderer;

        // Title
        float titleScale = 3.5f;
        int titleW = (int) (tr.getWidth(title) * titleScale);
        float titleX = (this.width - titleW) / 2f;
        float titleBaseY = this.height / 5f;
        float titleY = titleBaseY + shakeY;

        int gradA = MenuUI.argb(255, 180, 180, 255);
        int gradB = MenuUI.argb(255, 140, 140, 255);
        float phase = (System.currentTimeMillis() % 4000L) / 1500f;
        int titleColor = MenuUI.animatedGradient(gradA, gradB, phase);

        // shadow
        ctx.getMatrices().push();
        ctx.getMatrices().translate(titleX + 2, titleY + 2, 0);
        ctx.getMatrices().scale(titleScale, titleScale, 1);
        ctx.drawText(tr, title, 0, 0, MenuUI.argb(0, 0, 0, 100), false);
        ctx.getMatrices().pop();

        // main title
        ctx.getMatrices().push();
        ctx.getMatrices().translate(titleX, titleY, 0);
        ctx.getMatrices().scale(titleScale, titleScale, 1);
        ctx.drawText(tr, title, 0, 0, titleColor, false);
        ctx.getMatrices().pop();

        float titleHeight = 54;
        int spacing = 12;
        int btnW = 200, btnH = 30;

        float buttonsStartY = titleBaseY + titleHeight + spacing * 2;
        int centerX = this.width / 2 - btnW / 2;

        singleBtn.x = centerX; singleBtn.y = (int) buttonsStartY;
        multiBtn.x  = centerX; multiBtn.y  = (int) (buttonsStartY + btnH + spacing);
        altBtn.x    = centerX; altBtn.y    = (int) (buttonsStartY + 2 * (btnH + spacing));
        optionsQuit.x = centerX; optionsQuit.y = (int) (buttonsStartY + 3 * (btnH + spacing));
        optionsQuit.width = btnW;

        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, (1 - fadeIn) * 20, 0);

        singleBtn.render(ctx, mouseX, mouseY, fadeIn);
        multiBtn.render(ctx, mouseX, mouseY, fadeIn);
        altBtn.render(ctx, mouseX, mouseY, fadeIn);
        optionsQuit.render(ctx, mouseX, mouseY, fadeIn);

        ctx.getMatrices().pop();

        // version bottom-right
        int vw = tr.getWidth(version);
        int vx = this.width - vw - 10;
        int vy = this.height - 14;
        ctx.drawText(tr, version, vx + 1, vy + 1, MenuUI.argb(0, 0, 0, 150), false);
        ctx.drawText(tr, version, vx, vy, MenuUI.argb(180, 180, 180, 200), false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // title shake
        TextRenderer tr = this.textRenderer;
        float titleScale = 3.5f;
        int titleW = (int) (tr.getWidth(title) * titleScale);
        float titleX = (this.width - titleW) / 2f;
        float titleY = this.height / 5f;
        float titleH = 9 * titleScale;
        if (mouseX >= titleX && mouseX <= titleX + titleW
                && mouseY >= titleY && mouseY <= titleY + titleH) {
            shakeTime = 20;
            return true;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (singleBtn.hovered(mouseX, mouseY)) { mc.setScreen(new SelectWorldScreen(this)); return true; }
        if (multiBtn.hovered(mouseX, mouseY))  { mc.setScreen(new MultiplayerScreen(this)); return true; }
        if (altBtn.hovered(mouseX, mouseY))    { mc.setScreen(new AltManagerScreen(this)); return true; }
        if (optionsQuit.leftHovered(mouseX, mouseY)) { mc.setScreen(new OptionsScreen(this, mc.options)); return true; }
        if (optionsQuit.rightHovered(mouseX, mouseY)) { mc.scheduleStop(); return true; }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ---- Particle ----
    private class Particle {
        float x, y, vx, vy, size;
        int alpha;

        Particle() {
            reset();
            y = random.nextFloat() * height;
        }

        void reset() {
            x = random.nextFloat() * width;
            y = -10;
            vx = (random.nextFloat() - 0.5f) * 0.5f;
            vy = random.nextFloat() * 0.5f + 0.3f;
            size = random.nextFloat() * 2 + 1;
            alpha = random.nextInt(100) + 50;
        }

        void update() {
            x += vx;
            y += vy;
            if (y > height + 10 || x < -10 || x > width + 10) reset();
        }

        void render(DrawContext ctx) {
            int c = MenuUI.argb(255, 180, 180, alpha);
            int s = Math.max(1, (int) size);
            ctx.fill((int) x, (int) y, (int) x + s, (int) y + s, c);
        }
    }

    // ---- Btn ----
    private class Btn {
        final String name;
        int x, y, width, height;
        float hoverAnim = 0, glowAnim = 0, scale = 1;

        Btn(String name, int x, int y, int width, int height) {
            this.name = name;
            this.x = x; this.y = y; this.width = width; this.height = height;
        }

        void render(DrawContext ctx, int mx, int my, float fade) {
            boolean h = hovered(mx, my);
            float sp = 0.08f;
            hoverAnim = h ? Math.min(1, hoverAnim + sp) : Math.max(0, hoverAnim - sp);
            scale     = h ? Math.min(1.03f, scale + sp * 0.75f) : Math.max(1, scale - sp * 0.75f);
            glowAnim  = h ? Math.min(1, glowAnim + sp * 2) : Math.max(0, glowAnim - sp * 2);

            ctx.getMatrices().push();
            ctx.getMatrices().translate(x + width / 2f, y + height / 2f, 0);
            ctx.getMatrices().scale(scale, scale, 1);
            ctx.getMatrices().translate(-(x + width / 2f), -(y + height / 2f), 0);

            if (glowAnim > 0) {
                int ga = (int) (glowAnim * 80 * fade);
                MenuUI.roundedRect(ctx, x - 3, y - 3, width + 6, height + 6, 10,
                        MenuUI.argb(255, 140, 140, ga));
            }
            MenuUI.roundedRect(ctx, x + 2, y + 2, width, height, 7,
                    MenuUI.argb(0, 0, 0, (int) (100 * fade)));

            int baseC = MenuUI.argb(25, 25, 25, (int) (120 * fade));
            int hovC  = MenuUI.argb(50, 30, 30, (int) (180 * fade));
            int bgC   = MenuUI.lerpColor(baseC, hovC, hoverAnim);
            MenuUI.roundedRect(ctx, x, y, width, height, 7, bgC);

            if (hoverAnim > 0) {
                int ba = (int) (hoverAnim * 150 * fade);
                // border-effect: рисуем рамку 4 линиями
                int borderC = MenuUI.argb(255, 140, 140, ba);
                ctx.fill(x, y, x + width, y + 1, borderC);
                ctx.fill(x, y + height - 1, x + width, y + height, borderC);
                ctx.fill(x, y, x + 1, y + height, borderC);
                ctx.fill(x + width - 1, y, x + width, y + height, borderC);
            }

            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            int tw = tr.getWidth(name);
            int textColor = MenuUI.argb(255, 255, 255, (int) (255 * fade));
            ctx.drawText(tr, name, x + (width - tw) / 2, y + (height - 8) / 2, textColor, false);

            ctx.getMatrices().pop();
        }

        boolean hovered(double mx, double my) {
            return mx >= x && mx <= x + width && my >= y && my <= y + height;
        }
    }

    // ---- ComboBtn ----
    private class ComboBtn {
        int x, y, width, height;
        final String left, right;
        float lHover = 0, rHover = 0, lGlow = 0, rGlow = 0, lScale = 1, rScale = 1;

        ComboBtn(int x, int y, int width, int height, String left, String right) {
            this.x = x; this.y = y; this.width = width; this.height = height;
            this.left = left; this.right = right;
        }

        void render(DrawContext ctx, int mx, int my, float fade) {
            int gap = 2, half = width / 2, shrink = 4;
            int bw = half - shrink;
            float sp = 0.08f;

            boolean lh = leftHovered(mx, my), rh = rightHovered(mx, my);
            lHover = lh ? Math.min(1, lHover + sp) : Math.max(0, lHover - sp);
            lScale = lh ? Math.min(1.03f, lScale + sp * 0.75f) : Math.max(1, lScale - sp * 0.75f);
            lGlow  = lh ? Math.min(1, lGlow + sp * 2) : Math.max(0, lGlow - sp * 2);
            rHover = rh ? Math.min(1, rHover + sp) : Math.max(0, rHover - sp);
            rScale = rh ? Math.min(1.03f, rScale + sp * 0.75f) : Math.max(1, rScale - sp * 0.75f);
            rGlow  = rh ? Math.min(1, rGlow + sp * 2) : Math.max(0, rGlow - sp * 2);

            int baseC = MenuUI.argb(25, 25, 25, (int) (120 * fade));
            int hovC  = MenuUI.argb(50, 30, 30, (int) (180 * fade));

            renderHalf(ctx, x + gap, y, bw, height, left, lHover, lScale, lGlow, baseC, hovC, fade);
            renderHalf(ctx, x + half + gap, y, bw, height, right, rHover, rScale, rGlow, baseC, hovC, fade);
        }

        private void renderHalf(DrawContext ctx, int bx, int by, int bw, int bh,
                                String label, float hover, float scale, float glow,
                                int baseC, int hovC, float fade) {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(bx + bw / 2f, by + bh / 2f, 0);
            ctx.getMatrices().scale(scale, scale, 1);
            ctx.getMatrices().translate(-(bx + bw / 2f), -(by + bh / 2f), 0);

            if (glow > 0) {
                int ga = (int) (glow * 80 * fade);
                MenuUI.roundedRect(ctx, bx - 3, by - 3, bw + 6, bh + 6, 10,
                        MenuUI.argb(255, 140, 140, ga));
            }
            MenuUI.roundedRect(ctx, bx + 2, by + 2, bw, bh, 7,
                    MenuUI.argb(0, 0, 0, (int) (100 * fade)));
            int bg = MenuUI.lerpColor(baseC, hovC, hover);
            MenuUI.roundedRect(ctx, bx, by, bw, bh, 7, bg);

            if (hover > 0) {
                int ba = (int) (hover * 150 * fade);
                int borderC = MenuUI.argb(255, 140, 140, ba);
                ctx.fill(bx, by, bx + bw, by + 1, borderC);
                ctx.fill(bx, by + bh - 1, bx + bw, by + bh, borderC);
                ctx.fill(bx, by, bx + 1, by + bh, borderC);
                ctx.fill(bx + bw - 1, by, bx + bw, by + bh, borderC);
            }

            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            int tw = tr.getWidth(label);
            int textColor = MenuUI.argb(255, 255, 255, (int) (255 * fade));
            ctx.drawText(tr, label, bx + (bw - tw) / 2, by + (bh - 8) / 2, textColor, false);

            ctx.getMatrices().pop();
        }

        boolean leftHovered(double mx, double my) {
            return mx >= x && mx < x + width / 2 - 1 && my >= y && my <= y + height;
        }
        boolean rightHovered(double mx, double my) {
            return mx > x + width / 2 + 1 && mx <= x + width && my >= y && my <= y + height;
        }
    }
}

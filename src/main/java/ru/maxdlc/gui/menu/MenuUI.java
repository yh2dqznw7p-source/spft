package ru.maxdlc.gui.menu;

import net.minecraft.client.gui.DrawContext;

/** Общие helper-ы для кастомных экранов Max DLC. */
public final class MenuUI {
    private MenuUI() {}

    public static int argb(int r, int g, int b, int a) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public static int lerpColor(int a, int b, float t) {
        int aa = (a >>> 24) & 0xFF, ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
        int ra = (int) (aa + (ba - aa) * t);
        int rr = (int) (ar + (br - ar) * t);
        int rg = (int) (ag + (bg - ag) * t);
        int rb = (int) (ab + (bb - ab) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    public static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    /** Анимированный градиентный фон + затемнение поверх — как в оригинале. */
    public static void animatedBackground(DrawContext ctx, int w, int h) {
        long t = System.currentTimeMillis();
        double a = ((t % 10000L) / 10000.0) * Math.PI * 2;
        float sin1 = (float) Math.sin(a), cos1 = (float) Math.cos(a);
        float sin2 = (float) Math.sin(a + Math.PI / 2), cos2 = (float) Math.cos(a + Math.PI / 2);

        int top    = argb(c(40 + (int)(sin1 * 30)), c(20 + (int)(cos1 * 20)), c(30 + (int)(sin2 * 25)), 255);
        int middle = argb(c(50 + (int)(cos2 * 35)), c(25 + (int)(sin1 * 25)), c(45 + (int)(cos1 * 30)), 255);
        int bottom = argb(c(35 + (int)(cos1 * 30)), c(15 + (int)(sin2 * 20)), c(40 + (int)(sin1 * 30)), 255);

        ctx.fillGradient(0, 0, w, h / 2, top, middle);
        ctx.fillGradient(0, h / 2, w, h, middle, bottom);
        ctx.fill(0, 0, w, h, argb(0, 0, 0, 180));
    }

    /** Закруглённый (ступеньками) прямоугольник через fill. */
    public static void roundedRect(DrawContext ctx, int x, int y, int w, int h, int radius, int color) {
        radius = Math.min(radius, Math.min(w, h) / 2);
        // центральный блок
        ctx.fill(x + radius, y, x + w - radius, y + h, color);
        ctx.fill(x, y + radius, x + radius, y + h - radius, color);
        ctx.fill(x + w - radius, y + radius, x + w, y + h - radius, color);
        // углы — грубо по "пикселю" (округление малое)
        for (int i = 0; i < radius; i++) {
            int dx = radius - i;
            int cut = (int) (radius - Math.sqrt(radius * radius - dx * dx));
            ctx.fill(x + cut, y + i, x + w - cut, y + i + 1, color);
            ctx.fill(x + cut, y + h - i - 1, x + w - cut, y + h - i, color);
        }
    }

    /** Цветная анимированная текст-волна (псевдо-градиент: 2 цвета, смешиваемые по sin). */
    public static int animatedGradient(int colorA, int colorB, float phase) {
        float t = (float) (0.5 + 0.5 * Math.sin(phase));
        return lerpColor(colorA, colorB, t);
    }

    private static int c(int v) { return Math.max(0, Math.min(255, v)); }
}

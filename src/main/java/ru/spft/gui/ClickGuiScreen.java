package ru.spft.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import ru.spft.SpftClient;
import ru.spft.gui.anim.Animation;
import ru.spft.gui.anim.Easing;
import ru.spft.module.Category;

import java.util.ArrayList;
import java.util.List;

/**
 * ClickGUI в стиле rockstar "DropDown" — 5 панелей-категорий в ряд по центру.
 *  - ЛКМ по модулю: toggle
 *  - ПКМ по модулю: перейти к настройкам (появляется "◂ Имя" в заголовке)
 *  - СКМ по модулю: слушаем клавишу для бинда
 *  - Ctrl+F: поиск
 *
 * Плавное открытие/закрытие: масштаб 0.94 → 1.0 + alpha 0 → 1 за ANIM_MS мс,
 * easeOutCubic на вход, easeInCubic на выход. Фон — встроенный блюр + мой
 * тонирующий слой (liquid-glass).
 *
 * Архитектура по мотивам rockstar DropDownScreen/MenuPanel/ModuleComponent,
 * написано с нуля под SPFT Module/Setting API.
 */
public class ClickGuiScreen extends Screen {
    public static final int PANEL_W = 140;
    public static final int PANEL_H = 240;
    public static final int PANEL_SPACING = 10;

    private static final long ANIM_MS = 320;
    /** Задержка появления каждой следующей панели (stagger), мс. Как в rockstar через BAKEK_PAGES. */
    private static final long PANEL_STAGGER_MS = 45;

    private final List<PanelComponent> panels = new ArrayList<>();

    private boolean searchOpen = false;
    private String searchText = "";

    // одна общая "пружина" 0..1: 1 = открыт, 0 = закрыт. BAKEK — их фирменный easing.
    private final Animation openness = new Animation(ANIM_MS, 0f, Easing.BAKEK);
    private boolean closing = false;
    private boolean actuallyClosed = false;

    public ClickGuiScreen() {
        super(Text.of("SPFT ClickGUI"));
    }

    @Override
    protected void init() {
        super.init();
        panels.clear();
        Category[] cats = Category.values();
        int totalW = cats.length * PANEL_W + (cats.length - 1) * PANEL_SPACING;
        int startX = (this.width - totalW) / 2;
        int y = (this.height - PANEL_H) / 2;
        for (int i = 0; i < cats.length; i++) {
            panels.add(new PanelComponent(cats[i], startX + i * (PANEL_W + PANEL_SPACING), y, i));
        }
        openness.setValue(0f); // при (пере)инициализации экрана стартуем с 0
    }

    @Override
    public boolean shouldPause() { return false; }

    public String getSearchText() { return searchText; }

    // ===== анимация =====

    /** Общий прогресс "открытости" окна (1 = открыт, 0 = закрыт). */
    public float getGlobalProgress() {
        return openness.getValue();
    }

    /** Умножить альфа-канал цвета на множитель 0..1. Используется панелями. */
    public static int withAlpha(int argb, float mul) {
        int a = (argb >>> 24) & 0xFF;
        int na = Math.max(0, Math.min(255, Math.round(a * mul)));
        return (na << 24) | (argb & 0x00FFFFFF);
    }

    // ===== рендер =====

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Блюр мира + стандартное затемнение (встроенный Screen#renderBackground)
        super.renderBackground(ctx, mouseX, mouseY, delta);

        // тик анимации: тянемся к 1 пока открываемся, к 0 — пока закрываемся
        openness.update(closing ? 0f : 1f);
        float p = openness.getValue();

        // закончили close-анимацию — теперь реально закрываем
        if (closing && openness.isDone() && p <= 0.001f && !actuallyClosed) {
            actuallyClosed = true;
            if (SpftClient.get() != null) {
                SpftClient.get().getConfigManager().save(SpftClient.get());
            }
            super.close();
            return;
        }

        // Тонирующий слой для glass-эффекта (поверх блюра)
        ctx.fill(0, 0, this.width, this.height, withAlpha(0x60101014, p));

        // Общая анимация сцены: scale+fade вокруг центра экрана
        float globalScale = 0.92f + 0.08f * p;
        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.translate(this.width / 2f, this.height / 2f, 0f);
        ms.scale(globalScale, globalScale, 1f);
        ms.translate(-this.width / 2f, -this.height / 2f, 0f);

        // заголовок сверху
        String title = "§cSPFT §fClient";
        int titleW = this.textRenderer.getWidth(title);
        ctx.drawTextWithShadow(this.textRenderer, title,
                (this.width - titleW) / 2, 8, withAlpha(0xFFFFFFFF, p));

        // панели — каждая со своим staggered-прогрессом
        String desc = "";
        for (PanelComponent pc : panels) {
            float panelP = panelStaggered(p, pc.getIndex(), panels.size());
            pc.render(ctx, this.textRenderer, mouseX, mouseY, searchText, panelP);
            String d = pc.getHoveredDescription(mouseX, mouseY);
            if (d != null) desc = d;
        }

        // описание модуля под курсором
        if (!desc.isEmpty()) {
            int dw = this.textRenderer.getWidth(desc);
            ctx.drawTextWithShadow(this.textRenderer, "§7" + desc,
                    (this.width - dw) / 2,
                    this.height / 2 + PANEL_H / 2 + 12,
                    withAlpha(0xFFAAAAAA, p));
        }

        // поиск внизу
        if (searchOpen) {
            int fw = 180;
            int fx = (this.width - fw) / 2;
            int fy = this.height - 32;
            ctx.fill(fx, fy, fx + fw, fy + 16, withAlpha(0xCC000000, p));
            int bc = withAlpha(0xFFFF5555, p);
            ctx.fill(fx,          fy,          fx + fw,     fy + 1,      bc);
            ctx.fill(fx,          fy + 15,     fx + fw,     fy + 16,     bc);
            ctx.fill(fx,          fy,          fx + 1,      fy + 16,     bc);
            ctx.fill(fx + fw - 1, fy,          fx + fw,     fy + 16,     bc);
            String shown = "§fSearch: §7" + searchText + "§f_";
            ctx.drawTextWithShadow(this.textRenderer, shown, fx + 6, fy + 4,
                    withAlpha(0xFFFFFFFF, p));
        } else {
            String hint = "§8Ctrl+F §7— поиск  §8| §7ЛКМ §8— вкл/выкл  §8| §7ПКМ §8— настройки";
            int hw = this.textRenderer.getWidth(hint);
            ctx.drawTextWithShadow(this.textRenderer, hint,
                    (this.width - hw) / 2, this.height - 20,
                    withAlpha(0xFFAAAAAA, p));
        }

        ms.pop();
    }

    /**
     * Растянутое "появление" — каждая следующая панель стартует чуть позже.
     * Маппит общий прогресс 0..1 в нестрогий локальный 0..1 для конкретной панели.
     */
    private static float panelStaggered(float global, int index, int total) {
        float range = 1f - (PANEL_STAGGER_MS / (float) ANIM_MS) * Math.max(0, total - 1);
        range = Math.max(0.1f, range);
        float start = (PANEL_STAGGER_MS / (float) ANIM_MS) * index;
        float local = (global - start) / range;
        if (local < 0f) local = 0f;
        if (local > 1f) local = 1f;
        // BAKEK_PAGES — их фирменный easing для страничного появления
        return Easing.BAKEK_PAGES.ease(local, 0f, 1f, 1f);
    }

    // ===== input =====

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closing) return true; // во время close-анимации клики глушим
        for (PanelComponent p : panels) if (p.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (PanelComponent p : panels) p.mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        for (PanelComponent p : panels) if (p.mouseDragged(mouseX, mouseY, button, dx, dy)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmt, double vAmt) {
        for (PanelComponent p : panels) if (p.mouseScrolled(mouseX, mouseY, vAmt)) return true;
        return super.mouseScrolled(mouseX, mouseY, hAmt, vAmt);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (closing) return true;

        for (PanelComponent p : panels) if (p.keyPressed(keyCode)) return true;

        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_F) {
            searchOpen = !searchOpen;
            if (!searchOpen) searchText = "";
            return true;
        }

        if (searchOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { searchOpen = false; searchText = ""; return true; }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchText.isEmpty()) searchText = searchText.substring(0, searchText.length() - 1);
                return true;
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchOpen && chr >= 32 && chr != 127) {
            searchText += chr;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public void close() {
        // не закрываем сразу — запускаем close-анимацию.
        // реальный super.close() вызывается в render() после openness -> 0.
        closing = true;
    }
}

package ru.spft.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import ru.spft.SpftClient;
import ru.spft.module.Category;
import ru.spft.module.Module;
import ru.spft.setting.BooleanSetting;
import ru.spft.setting.KeySetting;
import ru.spft.setting.ModeSetting;
import ru.spft.setting.NumberSetting;
import ru.spft.setting.Setting;

/**
 * Панель категории в стиле rockstar: фиксированный размер, заголовок сверху.
 * Два состояния:
 *   - showingSettings=false: показываем список модулей
 *   - showingSettings=true : показываем настройки выбранного модуля + кнопка "◂ Назад"
 *
 * ЛКМ по модулю — toggle, ПКМ — выбрать как selectedModule (переход на настройки).
 * Скролл — листает список модулей или настройки.
 */
public class PanelComponent {
    private static final int HEADER_H = 22;
    private static final int ROW_H = 18;
    private static final int SEP_Y = HEADER_H + 2; // где начинается контент

    // rockstar colors (взяты стилистически)
    private static final int COLOR_BG = 0xE81A1A1D;
    private static final int COLOR_HEADER = 0xFF23232A;
    private static final int COLOR_HOVER = 0x4027272F;
    private static final int COLOR_ACCENT = 0xFFFF4141; // акцент SPFT — красный
    private static final int COLOR_TEXT = 0xFFE6E6E6;
    private static final int COLOR_TEXT_DIM = 0xFF888888;
    private static final int COLOR_SEP = 0xFF2C2C33;

    private final Category category;
    private final int x, y;

    private Module selectedModule;
    private int moduleScroll = 0;
    private int settingScroll = 0;

    // слайдер в активном драге
    private NumberSetting activeSlider;
    private int sliderBarX, sliderBarW;

    // ожидание клавиши для KeySetting
    private KeySetting listeningKey;
    // ожидание клавиши для биндинга модуля (ПКМ+Shift — в нашем случае: средняя кнопка)
    private Module bindingModule;

    public PanelComponent(Category category, int x, int y) {
        this.category = category;
        this.x = x;
        this.y = y;
    }

    public int width()  { return ClickGuiScreen.PANEL_W; }
    public int height() { return ClickGuiScreen.PANEL_H; }

    public void render(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY, String searchText) {
        int w = width(), h = height();

        // тень
        ctx.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x50000000);
        // фон панели
        ctx.fill(x, y, x + w, y + h, COLOR_BG);
        // заголовок
        ctx.fill(x, y, x + w, y + HEADER_H, COLOR_HEADER);
        // разделитель
        ctx.fill(x, y + HEADER_H, x + w, y + HEADER_H + 1, COLOR_SEP);

        // заголовок: если в настройках модуля — показываем "◂ Имя", иначе — категорию
        if (selectedModule != null) {
            ctx.drawTextWithShadow(tr, "§f◂ §f" + selectedModule.getName(), x + 8, y + 7, COLOR_TEXT);
        } else {
            ctx.drawTextWithShadow(tr, "§f" + category.getName(), x + 8, y + 7, COLOR_TEXT);
        }

        // clip (scissor)
        ctx.enableScissor(x + 1, y + HEADER_H + 1, x + w - 1, y + h - 1);
        try {
            if (selectedModule != null) {
                renderSettings(ctx, tr, mouseX, mouseY);
            } else {
                renderModules(ctx, tr, mouseX, mouseY, searchText);
            }
        } finally {
            ctx.disableScissor();
        }
    }

    private void renderModules(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY, String searchText) {
        int cx = x;
        int cy = y + SEP_Y - moduleScroll;
        String q = searchText == null ? "" : searchText.toLowerCase().trim();

        for (Module m : SpftClient.get().getModuleManager().getModulesIn(category)) {
            if (!q.isEmpty() && !m.getName().toLowerCase().contains(q)) continue;

            boolean hovered = mouseY >= cy && mouseY < cy + ROW_H && mouseX >= cx && mouseX < cx + width()
                    && mouseY >= y + SEP_Y && mouseY < y + height();
            if (hovered) ctx.fill(cx, cy, cx + width(), cy + ROW_H, COLOR_HOVER);

            // индикатор включения — цветная полоска слева
            if (m.isEnabled()) {
                ctx.fill(cx, cy, cx + 2, cy + ROW_H, COLOR_ACCENT);
            }

            int txtColor = m.isEnabled() ? COLOR_ACCENT : COLOR_TEXT;
            ctx.drawTextWithShadow(tr, m.getName(), cx + 8, cy + 5, txtColor);

            // стрелка раскрытия (если есть что настраивать)
            if (hasVisibleSettings(m)) {
                ctx.drawTextWithShadow(tr, "§7›", cx + width() - 10, cy + 5, COLOR_TEXT_DIM);
            }

            cy += ROW_H;
        }
    }

    private void renderSettings(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY) {
        int cx = x;
        int cy = y + SEP_Y - settingScroll;

        for (Setting s : selectedModule.getSettings()) {
            if (!s.isVisible()) continue;
            int h = settingHeight(s);
            boolean hovered = mouseY >= cy && mouseY < cy + h && mouseX >= cx && mouseX < cx + width()
                    && mouseY >= y + SEP_Y && mouseY < y + height();
            if (hovered) ctx.fill(cx, cy, cx + width(), cy + h, COLOR_HOVER);
            renderSetting(ctx, tr, s, cx, cy);
            cy += h;
        }
    }

    private int settingHeight(Setting s) {
        if (s instanceof NumberSetting) return 22;
        if (s instanceof ModeSetting) return 18;
        return ROW_H;
    }

    private boolean hasVisibleSettings(Module m) {
        for (Setting s : m.getSettings()) if (s.isVisible()) return true;
        return false;
    }

    private void renderSetting(DrawContext ctx, TextRenderer tr, Setting s, int cx, int cy) {
        int w = width();
        if (s instanceof BooleanSetting b) {
            ctx.drawTextWithShadow(tr, "§f" + s.getName(), cx + 10, cy + 5, COLOR_TEXT);
            // toggle-пилюля справа
            int pillW = 18, pillH = 8;
            int px = cx + w - pillW - 10;
            int py = cy + 5;
            int bg = b.getValue() ? COLOR_ACCENT : 0xFF3A3A42;
            ctx.fill(px, py, px + pillW, py + pillH, bg);
            int knobX = b.getValue() ? px + pillW - pillH : px;
            ctx.fill(knobX, py, knobX + pillH, py + pillH, 0xFFFFFFFF);
        } else if (s instanceof NumberSetting n) {
            ctx.drawTextWithShadow(tr, "§f" + s.getName(), cx + 10, cy + 3, COLOR_TEXT);
            ctx.drawTextWithShadow(tr, "§7" + prettyNumber(n.getValue()), cx + w - 10 - tr.getWidth(prettyNumber(n.getValue())), cy + 3, COLOR_TEXT_DIM);
            int barX = cx + 10;
            int barW = w - 20;
            int barY = cy + 16;
            double frac = (n.getValue() - n.getMin()) / (n.getMax() - n.getMin());
            frac = Math.max(0, Math.min(1, frac));
            ctx.fill(barX, barY, barX + barW, barY + 2, 0xFF3A3A42);
            ctx.fill(barX, barY, barX + (int) (barW * frac), barY + 2, COLOR_ACCENT);
        } else if (s instanceof ModeSetting mo) {
            ctx.drawTextWithShadow(tr, "§f" + s.getName(), cx + 10, cy + 5, COLOR_TEXT);
            String val = "< " + mo.getValue() + " >";
            ctx.drawTextWithShadow(tr, "§f" + val, cx + w - 10 - tr.getWidth(val), cy + 5, COLOR_TEXT);
        } else if (s instanceof KeySetting k) {
            boolean waiting = (listeningKey == k);
            ctx.drawTextWithShadow(tr, "§f" + s.getName(), cx + 10, cy + 5, COLOR_TEXT);
            String label = waiting ? "§e..." : "§f[§7" + k.getKeyName() + "§f]";
            ctx.drawTextWithShadow(tr, label, cx + w - 10 - tr.getWidth(label.replaceAll("§.", "")), cy + 5, COLOR_TEXT);
        }
    }

    private String prettyNumber(double v) {
        if (Math.abs(v - Math.round(v)) < 1e-6) return String.valueOf((long) v);
        return String.format("%.2f", v);
    }

    /** Вернёт описание модуля, над которым курсор (для подписи под панелями). */
    public String getHoveredDescription(int mouseX, int mouseY) {
        if (selectedModule != null) {
            if (mouseX >= x && mouseX < x + width() && mouseY >= y + SEP_Y && mouseY < y + height()) {
                return selectedModule.getDescription();
            }
            return null;
        }
        int cy = y + SEP_Y - moduleScroll;
        for (Module m : SpftClient.get().getModuleManager().getModulesIn(category)) {
            if (mouseY >= cy && mouseY < cy + ROW_H && mouseX >= x && mouseX < x + width()
                    && mouseY >= y + SEP_Y && mouseY < y + height()) {
                return m.getDescription();
            }
            cy += ROW_H;
        }
        return null;
    }

    // ===== mouse =====

    public boolean mouseClicked(double mx, double my, int button) {
        if (!isInside(mx, my)) return false;

        // клик по заголовку:
        if (my >= y && my < y + HEADER_H) {
            if (selectedModule != null && button == 0) {
                // кнопка назад
                selectedModule = null;
                settingScroll = 0;
                return true;
            }
            return true; // глушим клики по заголовку
        }

        if (selectedModule != null) {
            return handleSettingsClick(mx, my, button);
        } else {
            return handleModuleListClick(mx, my, button);
        }
    }

    private boolean handleModuleListClick(double mx, double my, int button) {
        int cy = y + SEP_Y - moduleScroll;
        String q = getSearchText();
        for (Module m : SpftClient.get().getModuleManager().getModulesIn(category)) {
            if (!q.isEmpty() && !m.getName().toLowerCase().contains(q)) continue;
            boolean on = my >= cy && my < cy + ROW_H && mx >= x && mx < x + width()
                    && my >= y + SEP_Y && my < y + height();
            if (on) {
                if (button == 0) { // LMB — toggle
                    m.toggle();
                    return true;
                } else if (button == 1) { // RMB — открыть настройки
                    if (hasVisibleSettings(m)) {
                        selectedModule = m;
                        settingScroll = 0;
                    }
                    return true;
                } else if (button == 2) { // MMB — режим биндинга (ждём клавишу)
                    bindingModule = m;
                    return true;
                }
            }
            cy += ROW_H;
        }
        return false;
    }

    private boolean handleSettingsClick(double mx, double my, int button) {
        int cy = y + SEP_Y - settingScroll;
        for (Setting s : selectedModule.getSettings()) {
            if (!s.isVisible()) continue;
            int h = settingHeight(s);
            boolean on = my >= cy && my < cy + h && mx >= x && mx < x + width()
                    && my >= y + SEP_Y && my < y + height();
            if (on) {
                if (handleSettingClick(s, button, mx, cy)) return true;
            }
            cy += h;
        }
        return false;
    }

    private boolean handleSettingClick(Setting s, int button, double mx, int cy) {
        if (s instanceof BooleanSetting b) {
            if (button == 0) { b.toggle(); return true; }
        } else if (s instanceof ModeSetting mo) {
            if (button == 0) { mo.cycle(); return true; }
            if (button == 1) { mo.cycleBack(); return true; }
        } else if (s instanceof NumberSetting n) {
            if (button == 0) {
                activeSlider = n;
                sliderBarX = x + 10;
                sliderBarW = width() - 20;
                updateSliderFromMouse(mx);
                return true;
            }
        } else if (s instanceof KeySetting k) {
            if (button == 0) { listeningKey = k; return true; }
            if (button == 1) { k.setKeyCode(-1); return true; }
        }
        return false;
    }

    public void mouseReleased(double mx, double my, int button) {
        if (button == 0) activeSlider = null;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (activeSlider != null && button == 0) {
            updateSliderFromMouse(mx);
            return true;
        }
        return false;
    }

    private void updateSliderFromMouse(double mx) {
        if (activeSlider == null) return;
        double frac = (mx - sliderBarX) / (double) sliderBarW;
        frac = Math.max(0, Math.min(1, frac));
        double val = activeSlider.getMin() + frac * (activeSlider.getMax() - activeSlider.getMin());
        activeSlider.setValue(val);
    }

    public boolean mouseScrolled(double mx, double my, double v) {
        if (!isInside(mx, my)) return false;
        int step = (int) (v * 12);
        if (selectedModule != null) {
            // при наведении на NumberSetting — меняем значение
            int cy = y + SEP_Y - settingScroll;
            for (Setting s : selectedModule.getSettings()) {
                if (!s.isVisible()) continue;
                int h = settingHeight(s);
                boolean on = my >= cy && my < cy + h && mx >= x && mx < x + width();
                if (on && s instanceof NumberSetting n) {
                    n.setValue(n.getValue() + v * n.getStep());
                    return true;
                }
                cy += h;
            }
            settingScroll = Math.max(0, settingScroll - step);
            return true;
        } else {
            moduleScroll = Math.max(0, moduleScroll - step);
            return true;
        }
    }

    public boolean keyPressed(int keyCode) {
        if (listeningKey != null) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) listeningKey.setKeyCode(-1);
            else listeningKey.setKeyCode(keyCode);
            listeningKey = null;
            return true;
        }
        if (bindingModule != null) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) bindingModule.getKeyBind().setKeyCode(-1);
            else bindingModule.getKeyBind().setKeyCode(keyCode);
            bindingModule = null;
            return true;
        }
        return false;
    }

    private boolean isInside(double mx, double my) {
        return mx >= x && mx < x + width() && my >= y && my < y + height();
    }

    private String getSearchText() {
        if (SpftClient.get() == null) return "";
        // GUI тянет search из ClickGuiScreen — но чтобы не создавать обратной связи,
        // просто читаем currentScreen, если это ClickGuiScreen.
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc != null && mc.currentScreen instanceof ClickGuiScreen cgs) {
            String t = cgs.getSearchText();
            return t == null ? "" : t.toLowerCase().trim();
        }
        return "";
    }
}

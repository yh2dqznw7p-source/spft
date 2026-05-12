package ru.spft.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import ru.spft.SpftClient;
import ru.spft.module.Category;

import java.util.ArrayList;
import java.util.List;

/**
 * ClickGUI в стиле rockstar "DropDown": 5 панелей-категорий в ряд по центру экрана,
 * в каждой сверху заголовок + список модулей. ПКМ по модулю — переключиться на
 * экран настроек (кнопка "◂ Назад" возвращает к списку).
 *
 * Основан на концепции rockstar DropDownScreen/MenuPanel/ModuleComponent,
 * переписано под SPFT API.
 */
public class ClickGuiScreen extends Screen {
    /** Ширина одной панели */
    public static final int PANEL_W = 140;
    /** Высота одной панели */
    public static final int PANEL_H = 240;
    /** Отступ между панелями */
    public static final int PANEL_SPACING = 10;

    private final List<PanelComponent> panels = new ArrayList<>();

    // поиск
    private boolean searchOpen = false;
    private String searchText = "";

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
            panels.add(new PanelComponent(cats[i], startX + i * (PANEL_W + PANEL_SPACING), y));
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    public String getSearchText() {
        return searchText;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // затемнённый фон
        ctx.fill(0, 0, this.width, this.height, 0xB0000000);

        // заголовок сверху
        String title = "SPFT Client";
        int titleW = this.textRenderer.getWidth(title);
        ctx.drawTextWithShadow(this.textRenderer, "§cSPFT §fClient", (this.width - titleW) / 2, 8, 0xFFFFFFFF);

        // панели
        String desc = "";
        for (PanelComponent p : panels) {
            p.render(ctx, this.textRenderer, mouseX, mouseY, searchText);
            String d = p.getHoveredDescription(mouseX, mouseY);
            if (d != null) desc = d;
        }

        // описание наведённого модуля (под панелями)
        if (!desc.isEmpty()) {
            int dw = this.textRenderer.getWidth(desc);
            ctx.drawTextWithShadow(this.textRenderer, "§7" + desc, (this.width - dw) / 2, this.height / 2 + PANEL_H / 2 + 12, 0xFFAAAAAA);
        }

        // поиск внизу
        if (searchOpen) {
            int fw = 180;
            int fx = (this.width - fw) / 2;
            int fy = this.height - 32;
            ctx.fill(fx, fy, fx + fw, fy + 16, 0xCC000000);
            // рамка (4 стороны)
            int col = 0xFFFF5555;
            ctx.fill(fx,          fy,          fx + fw,     fy + 1,      col);
            ctx.fill(fx,          fy + 16 - 1, fx + fw,     fy + 16,     col);
            ctx.fill(fx,          fy,          fx + 1,      fy + 16,     col);
            ctx.fill(fx + fw - 1, fy,          fx + fw,     fy + 16,     col);
            String shown = "§fSearch: §7" + searchText + "§f_";
            ctx.drawTextWithShadow(this.textRenderer, shown, fx + 6, fy + 4, 0xFFFFFFFF);
        } else {
            String hint = "§8Ctrl+F §7— поиск  §8| §7ЛКМ §8— вкл/выкл  §8| §7ПКМ §8— настройки";
            int hw = this.textRenderer.getWidth(hint);
            ctx.drawTextWithShadow(this.textRenderer, hint, (this.width - hw) / 2, this.height - 20, 0xFFAAAAAA);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (PanelComponent p : panels) {
            if (p.mouseClicked(mouseX, mouseY, button)) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (PanelComponent p : panels) p.mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        for (PanelComponent p : panels) {
            if (p.mouseDragged(mouseX, mouseY, button, dx, dy)) return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmt, double vAmt) {
        for (PanelComponent p : panels) {
            if (p.mouseScrolled(mouseX, mouseY, vAmt)) return true;
        }
        return super.mouseScrolled(mouseX, mouseY, hAmt, vAmt);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // сначала — если какой-то модуль в режиме биндинга, он заберёт клавишу
        for (PanelComponent p : panels) {
            if (p.keyPressed(keyCode)) return true;
        }

        // Ctrl+F — открыть поиск
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_F) {
            searchOpen = !searchOpen;
            if (!searchOpen) searchText = "";
            return true;
        }

        if (searchOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchOpen = false;
                searchText = "";
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchText.isEmpty()) searchText = searchText.substring(0, searchText.length() - 1);
                return true;
            }
            // остальные символы придут через charTyped
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
        if (searchOpen) {
            if (chr >= 32 && chr != 127) {
                searchText += chr;
                return true;
            }
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public void close() {
        SpftClient.get().getConfigManager().save(SpftClient.get());
        super.close();
    }
}

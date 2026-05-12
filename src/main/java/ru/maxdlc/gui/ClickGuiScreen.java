package ru.maxdlc.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import ru.maxdlc.MaxDLCClient;
import ru.maxdlc.module.Category;

import java.util.ArrayList;
import java.util.List;

public class ClickGuiScreen extends Screen {
    private final List<PanelComponent> panels = new ArrayList<>();

    public ClickGuiScreen() {
        super(Text.of("maxDLC ClickGUI"));
    }

    @Override
    protected void init() {
        super.init();
        if (panels.isEmpty()) {
            int x = 12;
            int y = 12;
            for (Category cat : Category.values()) {
                panels.add(new PanelComponent(cat, x, y, 150));
                x += 160;
            }
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // полупрозрачный фон
        ctx.fill(0, 0, this.width, this.height, 0x80000000);
        this.renderBackground(ctx, mouseX, mouseY, delta);

        // заголовок
        ctx.drawTextWithShadow(this.textRenderer, "§cmaxDLC §7Client §8— §fRIGHT_SHIFT", 10, this.height - 16, 0xFFFFFFFF);
        ctx.drawTextWithShadow(this.textRenderer, "§7ПКМ по модулю §8— §fраскрыть настройки", 10, this.height - 28, 0xFFAAAAAA);

        // панели
        for (PanelComponent p : panels) {
            p.render(ctx, this.textRenderer, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // сначала предлагаем панелям захватить клик (drag / ПКМ / LMB по модулю / слайдеры)
        for (PanelComponent p : panels) {
            if (p.mouseClicked(mouseX, mouseY, button)) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (PanelComponent p : panels) {
            p.mouseReleased(mouseX, mouseY, button);
        }
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
        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }
        // передаём KeySetting-компонентам для записи бинда
        for (PanelComponent p : panels) {
            if (p.keyPressed(keyCode)) return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        MaxDLCClient.get().getConfigManager().save(MaxDLCClient.get());
        super.close();
    }
}

package ru.maxdlc.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import ru.maxdlc.MaxDLCClient;
import ru.maxdlc.module.Category;
import ru.maxdlc.module.Module;
import ru.maxdlc.setting.BooleanSetting;
import ru.maxdlc.setting.KeySetting;
import ru.maxdlc.setting.ModeSetting;
import ru.maxdlc.setting.NumberSetting;
import ru.maxdlc.setting.Setting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Панель категории — перетаскиваемая, содержит модули. У каждого модуля:
 *   ЛКМ — toggle
 *   ПКМ — раскрыть настройки
 */
public class PanelComponent {
    private static final int HEADER_H = 16;
    private static final int MODULE_H = 14;
    private static final int SETTING_H = 12;

    private final Category category;
    private int x, y;
    private final int width;
    private boolean open = true;

    private boolean dragging = false;
    private int dragOffX, dragOffY;

    private final Set<Module> expanded = new HashSet<>();
    // активный "ожидающий клавишу" KeySetting (после клика по нему)
    private KeySetting listeningKey = null;
    // активный слайдер для перетаскивания
    private NumberSetting activeSlider = null;
    private int activeSliderX, activeSliderW;

    public PanelComponent(Category category, int x, int y, int width) {
        this.category = category;
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public int totalHeight() {
        int h = HEADER_H;
        if (open) {
            for (Module m : MaxDLCClient.get().getModuleManager().getModulesIn(category)) {
                h += MODULE_H;
                if (expanded.contains(m)) {
                    for (Setting s : m.getSettings()) if (s.isVisible()) h += SETTING_H;
                }
            }
        }
        return h;
    }

    public void render(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY) {
        int h = totalHeight();
        // фон
        ctx.fill(x, y, x + width, y + h, 0xCC1A1A1A);
        // заголовок
        ctx.fill(x, y, x + width, y + HEADER_H, 0xFFC01010);
        ctx.drawTextWithShadow(tr, "§f" + category.getName(), x + 6, y + 4, 0xFFFFFFFF);
        ctx.drawTextWithShadow(tr, open ? "§f−" : "§f+", x + width - 10, y + 4, 0xFFFFFFFF);

        if (!open) return;

        int cy = y + HEADER_H;
        for (Module m : MaxDLCClient.get().getModuleManager().getModulesIn(category)) {
            boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= cy && mouseY <= cy + MODULE_H;
            int bg = hovered ? 0xFF2A2A2A : 0x00000000;
            if ((bg & 0xFF000000) != 0) ctx.fill(x, cy, x + width, cy + MODULE_H, bg);

            int color = m.isEnabled() ? 0xFFFF5555 : 0xFFBBBBBB;
            ctx.drawTextWithShadow(tr, m.getName(), x + 6, cy + 3, color);

            // индикатор раскрытия
            if (!m.getSettings().isEmpty()) {
                ctx.drawTextWithShadow(tr, expanded.contains(m) ? "§7▾" : "§7▸", x + width - 10, cy + 3, 0xFFAAAAAA);
            }
            cy += MODULE_H;

            if (expanded.contains(m)) {
                for (Setting s : m.getSettings()) {
                    if (!s.isVisible()) continue;
                    renderSetting(ctx, tr, m, s, cy, mouseX, mouseY);
                    cy += SETTING_H;
                }
            }
        }
    }

    private void renderSetting(DrawContext ctx, TextRenderer tr, Module m, Setting s, int cy, int mouseX, int mouseY) {
        int pad = 14;
        if (s instanceof BooleanSetting b) {
            String state = b.getValue() ? "§a[ON]" : "§8[OFF]";
            ctx.drawTextWithShadow(tr, "§7" + s.getName(), x + pad, cy + 2, 0xFFBBBBBB);
            ctx.drawTextWithShadow(tr, state, x + width - 24, cy + 2, 0xFFFFFFFF);
        } else if (s instanceof NumberSetting n) {
            double frac = (n.getValue() - n.getMin()) / (n.getMax() - n.getMin());
            frac = Math.max(0, Math.min(1, frac));
            int barX = x + pad;
            int barW = width - pad - 4;
            // бар
            ctx.fill(barX, cy + 9, barX + barW, cy + 11, 0xFF444444);
            ctx.fill(barX, cy + 9, barX + (int) (barW * frac), cy + 11, 0xFFFF5555);
            String label = s.getName() + ": " + prettyNumber(n.getValue());
            ctx.drawTextWithShadow(tr, "§7" + label, x + pad, cy + 1, 0xFFBBBBBB);
        } else if (s instanceof ModeSetting mo) {
            ctx.drawTextWithShadow(tr, "§7" + s.getName(), x + pad, cy + 2, 0xFFBBBBBB);
            ctx.drawTextWithShadow(tr, "§f< " + mo.getValue() + " >", x + width - 50, cy + 2, 0xFFFFFFFF);
        } else if (s instanceof KeySetting k) {
            boolean waiting = (listeningKey == k);
            String label = waiting ? "§e..." : "§f" + k.getKeyName();
            ctx.drawTextWithShadow(tr, "§7" + s.getName(), x + pad, cy + 2, 0xFFBBBBBB);
            ctx.drawTextWithShadow(tr, label, x + width - 40, cy + 2, 0xFFFFFFFF);
        }
    }

    private String prettyNumber(double v) {
        if (Math.abs(v - Math.round(v)) < 1e-6) return String.valueOf((long) v);
        return String.format("%.2f", v);
    }

    public boolean mouseClicked(double mx, double my, int button) {
        // заголовок — drag / свернуть
        if (mx >= x && mx <= x + width && my >= y && my <= y + HEADER_H) {
            if (button == 0) {
                dragging = true;
                dragOffX = (int) (mx - x);
                dragOffY = (int) (my - y);
                return true;
            } else if (button == 1) {
                open = !open;
                return true;
            }
        }
        if (!open) return false;

        int cy = y + HEADER_H;
        for (Module m : MaxDLCClient.get().getModuleManager().getModulesIn(category)) {
            boolean onModuleRow = mx >= x && mx <= x + width && my >= cy && my <= cy + MODULE_H;
            if (onModuleRow) {
                if (button == 0) {          // ЛКМ — toggle
                    m.toggle();
                    return true;
                } else if (button == 1) {   // ПКМ — раскрыть/свернуть настройки
                    if (!m.getSettings().isEmpty()) {
                        if (expanded.contains(m)) expanded.remove(m); else expanded.add(m);
                    }
                    return true;
                }
            }
            cy += MODULE_H;

            if (expanded.contains(m)) {
                for (Setting s : m.getSettings()) {
                    if (!s.isVisible()) { continue; }
                    boolean onSetting = mx >= x && mx <= x + width && my >= cy && my <= cy + SETTING_H;
                    if (onSetting) {
                        if (handleSettingClick(s, button, mx, cy)) return true;
                    }
                    cy += SETTING_H;
                }
            }
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
                activeSliderX = x + 14;
                activeSliderW = width - 14 - 4;
                updateSliderFromMouse(mx);
                return true;
            }
        } else if (s instanceof KeySetting k) {
            if (button == 0) {
                listeningKey = k;
                return true;
            } else if (button == 1) {
                k.setKeyCode(-1);
                return true;
            }
        }
        return false;
    }

    public void mouseReleased(double mx, double my, int button) {
        if (button == 0) {
            dragging = false;
            activeSlider = null;
        }
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging && button == 0) {
            x = (int) (mx - dragOffX);
            y = (int) (my - dragOffY);
            return true;
        }
        if (activeSlider != null && button == 0) {
            updateSliderFromMouse(mx);
            return true;
        }
        return false;
    }

    private void updateSliderFromMouse(double mx) {
        if (activeSlider == null) return;
        double frac = (mx - activeSliderX) / (double) activeSliderW;
        frac = Math.max(0, Math.min(1, frac));
        double val = activeSlider.getMin() + frac * (activeSlider.getMax() - activeSlider.getMin());
        activeSlider.setValue(val);
    }

    public boolean mouseScrolled(double mx, double my, double v) {
        // прокрутка числового значения под курсором
        if (!open) return false;
        int cy = y + HEADER_H;
        for (Module m : MaxDLCClient.get().getModuleManager().getModulesIn(category)) {
            cy += MODULE_H;
            if (expanded.contains(m)) {
                for (Setting s : m.getSettings()) {
                    if (!s.isVisible()) continue;
                    boolean onSetting = mx >= x && mx <= x + width && my >= cy && my <= cy + SETTING_H;
                    if (onSetting && s instanceof NumberSetting n) {
                        n.setValue(n.getValue() + v * n.getStep());
                        return true;
                    }
                    cy += SETTING_H;
                }
            }
        }
        return false;
    }

    public boolean keyPressed(int keyCode) {
        if (listeningKey != null) {
            // ESC = очистить
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                listeningKey.setKeyCode(-1);
            } else {
                listeningKey.setKeyCode(keyCode);
            }
            listeningKey = null;
            return true;
        }
        return false;
    }

    /** служебный список, если когда-то понадобится */
    public List<Module> getModules() {
        return new ArrayList<>(MaxDLCClient.get().getModuleManager().getModulesIn(category));
    }
}

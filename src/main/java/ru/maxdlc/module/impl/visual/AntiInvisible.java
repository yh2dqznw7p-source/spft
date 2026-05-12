package ru.maxdlc.module.impl.visual;

import ru.maxdlc.module.Category;
import ru.maxdlc.module.Module;
import ru.maxdlc.setting.BooleanSetting;
import ru.maxdlc.setting.NumberSetting;

/**
 * AntiInvisible — делает невидимых игроков частично видимыми.
 * По умолчанию — 50% (0.5 alpha), как и просил пользователь.
 * Реализация — через LivingEntityRendererMixin.
 */
public class AntiInvisible extends Module {
    public final NumberSetting opacity = addNumber("Opacity", 50, 5, 100, 5); // проценты
    public final BooleanSetting players = addBoolean("Players", true);
    public final BooleanSetting mobs    = addBoolean("Mobs", false);

    public AntiInvisible() {
        super("AntiInvisible", "Показывает невидимых игроков с заданной прозрачностью", Category.VISUAL);
    }

    public float getAlpha() {
        return (float) Math.max(0.05, Math.min(1.0, opacity.getValue() / 100.0));
    }
}

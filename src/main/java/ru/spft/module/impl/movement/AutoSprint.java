package ru.spft.module.impl.movement;

import ru.spft.module.Category;
import ru.spft.module.Module;
import ru.spft.setting.BooleanSetting;

public class AutoSprint extends Module {
    public final BooleanSetting allDirections = addBoolean("AllDirections", false);
    public final BooleanSetting keepWhenBlind = addBoolean("KeepWhenBlind", false);
    public final BooleanSetting ignoreHunger  = addBoolean("IgnoreHunger", false);

    public AutoSprint() {
        super("AutoSprint", "Автоматически включает спринт", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        var p = mc.player;

        if (!ignoreHunger.getValue() && p.getHungerManager().getFoodLevel() <= 6) return;
        if (!keepWhenBlind.getValue() && p.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.BLINDNESS)) return;
        if (p.isUsingItem() || p.isSneaking()) return;
        if (p.horizontalCollision) return;

        boolean forward = mc.options.forwardKey.isPressed();
        boolean anyMove = forward || mc.options.backKey.isPressed()
                || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed();

        boolean shouldSprint = allDirections.getValue() ? anyMove : forward;
        if (shouldSprint) {
            p.setSprinting(true);
        }
    }

    @Override
    public void onDisable() {
        if (mc.player != null) mc.player.setSprinting(false);
    }
}

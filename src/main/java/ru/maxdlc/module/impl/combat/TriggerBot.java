package ru.maxdlc.module.impl.combat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import ru.maxdlc.module.Category;
import ru.maxdlc.module.Module;
import ru.maxdlc.setting.BooleanSetting;
import ru.maxdlc.setting.NumberSetting;

import java.util.concurrent.ThreadLocalRandom;

/**
 * TriggerBot — авто-атака цели под прицелом.
 * Настройки:
 *  - RandomDelayMs: случайная задержка между атаками, не более 1000 мс (1 секунда).
 *  - OnlyCrits: атакует только если удар будет критическим.
 *  - Players / Mobs — фильтры целей.
 */
public class TriggerBot extends Module {
    public final NumberSetting minDelay = addNumber("MinDelayMs", 50, 0, 1000, 10);
    public final NumberSetting maxDelay = addNumber("MaxDelayMs", 250, 0, 1000, 10);
    public final BooleanSetting onlyCrits = addBoolean("OnlyCrits", true);
    public final BooleanSetting players   = addBoolean("Players", true);
    public final BooleanSetting mobs      = addBoolean("Mobs", true);
    public final BooleanSetting requireFullCharge = addBoolean("FullCharge", true);

    private long lastAttack = 0L;
    private long nextDelay = 0L;

    public TriggerBot() {
        super("TriggerBot", "Авто-атака по цели под прицелом", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        lastAttack = System.currentTimeMillis();
        pickNextDelay();
    }

    private void pickNextDelay() {
        double min = Math.min(minDelay.getValue(), maxDelay.getValue());
        double max = Math.max(minDelay.getValue(), maxDelay.getValue());
        // потолок — 1000 мс
        if (max > 1000) max = 1000;
        if (min > 1000) min = 1000;
        nextDelay = (long) (min + ThreadLocalRandom.current().nextDouble() * (max - min));
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        long now = System.currentTimeMillis();
        if (now - lastAttack < nextDelay) return;

        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof EntityHitResult eh)) return;
        Entity target = eh.getEntity();
        if (!(target instanceof LivingEntity le) || !le.isAlive()) return;

        if (target instanceof PlayerEntity p) {
            if (!players.getValue()) return;
            if (p.isCreative() || p.isSpectator()) return;
            if (p == mc.player) return;
        } else {
            if (!mobs.getValue()) return;
        }

        if (requireFullCharge.getValue() && mc.player.getAttackCooldownProgress(0.5f) < 0.95f) return;

        if (onlyCrits.getValue() && !isCriticalCondition()) return;

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(mc.player.getActiveHand() != null ? mc.player.getActiveHand() : net.minecraft.util.Hand.MAIN_HAND);

        lastAttack = now;
        pickNextDelay();
    }

    /**
     * Критический удар возможен при падении (fallDistance > 0),
     * когда игрок не на земле, не в воде/лаве, не на лестнице, не в vehicle
     * и не под эффектом Слепоты/Левитации.
     */
    private boolean isCriticalCondition() {
        var p = mc.player;
        if (p.fallDistance <= 0.0f) return false;
        if (p.isOnGround()) return false;
        if (p.isTouchingWater() || p.isInLava()) return false;
        if (p.isClimbing()) return false;
        if (p.hasVehicle()) return false;
        if (p.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.BLINDNESS)) return false;
        if (p.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.LEVITATION)) return false;
        if (p.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOW_FALLING)) return false;
        return true;
    }
}

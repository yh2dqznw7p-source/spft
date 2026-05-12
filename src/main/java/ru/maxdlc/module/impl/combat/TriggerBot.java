package ru.maxdlc.module.impl.combat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import ru.maxdlc.module.Category;
import ru.maxdlc.module.Module;
import ru.maxdlc.setting.BooleanSetting;
import ru.maxdlc.setting.NumberSetting;

import java.util.concurrent.ThreadLocalRandom;

/**
 * TriggerBot — авто-атака по цели под прицелом.
 *
 *  MinDelayMs / MaxDelayMs — случайная пауза между атаками (макс. 1000).
 *  OnlyCrits  — бить только когда удар будет криткой (падаешь / прыжок).
 *  FullCharge — ждать полного кулдауна атаки (как ванильно).
 *  Players / Mobs — кого бить.
 *  Debug — печатать в ActionBar, почему НЕ бьёт (для настройки).
 */
public class TriggerBot extends Module {
    public final NumberSetting minDelay         = addNumber("MinDelayMs", 50, 0, 1000, 10);
    public final NumberSetting maxDelay         = addNumber("MaxDelayMs", 300, 0, 1000, 10);
    public final BooleanSetting onlyCrits       = addBoolean("OnlyCrits", false);
    public final BooleanSetting players         = addBoolean("Players", true);
    public final BooleanSetting mobs            = addBoolean("Mobs", true);
    public final BooleanSetting requireFullCharge = addBoolean("FullCharge", true);
    public final BooleanSetting debug           = addBoolean("Debug", false);

    private long lastAttack = 0L;
    private long nextDelay = 0L;
    private String lastStatus = "";

    public TriggerBot() {
        super("TriggerBot", "Авто-атака по цели под прицелом", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        lastAttack = System.currentTimeMillis();
        pickNextDelay();
        chat("§aTriggerBot ON §8| §7crits=" + onlyCrits.getValue()
                + " fullCharge=" + requireFullCharge.getValue()
                + " delay=" + (int) minDelay.getValue() + ".." + (int) maxDelay.getValue() + "ms");
    }

    @Override
    public void onDisable() {
        chat("§cTriggerBot OFF");
    }

    @Override
    public String getDisplayInfo() {
        if (!debug.getValue()) return null;
        return lastStatus.isEmpty() ? null : lastStatus;
    }

    private void pickNextDelay() {
        double min = Math.min(minDelay.getValue(), maxDelay.getValue());
        double max = Math.max(minDelay.getValue(), maxDelay.getValue());
        if (max > 1000) max = 1000;
        if (min > 1000) min = 1000;
        if (max <= min) { nextDelay = (long) min; return; }
        nextDelay = (long) (min + ThreadLocalRandom.current().nextDouble() * (max - min));
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            status("no-world"); return;
        }

        long now = System.currentTimeMillis();
        if (now - lastAttack < nextDelay) { status("wait:" + (nextDelay - (now - lastAttack)) + "ms"); return; }

        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof EntityHitResult eh)) { status("no-entity"); return; }

        Entity target = eh.getEntity();
        if (!(target instanceof LivingEntity le) || !le.isAlive()) { status("not-living"); return; }

        if (target instanceof PlayerEntity p) {
            if (!players.getValue())   { status("players-off"); return; }
            if (p.isCreative() || p.isSpectator()) { status("creative"); return; }
            if (p == mc.player)        { status("self"); return; }
        } else {
            if (!mobs.getValue()) { status("mobs-off"); return; }
        }

        if (requireFullCharge.getValue() && mc.player.getAttackCooldownProgress(0.5f) < 0.95f) {
            status("charging"); return;
        }

        if (onlyCrits.getValue() && !isCriticalCondition()) { status("not-crit"); return; }

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);

        lastAttack = now;
        pickNextDelay();
        status("hit!");
    }

    private void status(String s) {
        if (!debug.getValue()) return;
        if (s.equals(lastStatus)) return;
        lastStatus = s;
        if (mc.player != null) mc.player.sendMessage(Text.of("§8[§cTB§8] §7" + s), true);
    }

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

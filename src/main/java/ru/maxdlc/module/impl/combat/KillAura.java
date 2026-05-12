package ru.maxdlc.module.impl.combat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ru.maxdlc.module.Category;
import ru.maxdlc.module.Module;
import ru.maxdlc.setting.BooleanSetting;
import ru.maxdlc.setting.ModeSetting;
import ru.maxdlc.setting.NumberSetting;

import java.util.concurrent.ThreadLocalRandom;

/**
 * KillAura с выбором типа ротации (как Zenith/Rich DLC):
 *  - Silent   : камера НЕ двигается, правильные yaw/pitch уходят пакетом.
 *  - Legit    : плавный поворот клиентской камеры к цели.
 *  - FunTime  : порт ротации FTAngle — lerp 0.85 с clamp 130°, возвратный shake-fade.
 */
public class KillAura extends Module {
    public final ModeSetting mode        = addMode("Mode", "Silent", "Silent", "Legit", "FunTime");
    public final NumberSetting range     = addNumber("Range", 4.2, 1.0, 6.0, 0.1);
    public final NumberSetting fov       = addNumber("FOV", 180, 10, 360, 5);
    public final NumberSetting aps       = addNumber("APS", 12, 1, 20, 0.5);
    public final NumberSetting smooth    = addNumber("LegitSmooth", 35, 1, 180, 1);
    public final BooleanSetting players  = addBoolean("Players", true);
    public final BooleanSetting mobs     = addBoolean("Mobs", false);
    public final BooleanSetting onlyCrits  = addBoolean("OnlyCrits", false);
    public final BooleanSetting fullCharge = addBoolean("FullCharge", true);
    public final BooleanSetting throughWalls = addBoolean("ThroughWalls", false);
    public final BooleanSetting ignoreInvisible = addBoolean("IgnoreInvisible", true);
    public final BooleanSetting randomize = addBoolean("RandomizeTiming", true);

    private long lastAttack = 0L;
    private long nextInterval = 0L;
    private LivingEntity target = null;

    // --- FunTime rotation state ---
    private float ftYaw, ftPitch;
    private long ftNoTargetSinceMs = -1L;
    private long ftAttackCountStartMs = 0L;
    private int  ftAttackCount = 0;
    private long ftLastAttackTimerStart = 0L;

    public KillAura() {
        super("KillAura", "Авто-удары по ближайшему врагу (silent / legit / funtime)", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        lastAttack = 0L;
        target = null;
        pickNextInterval();
        if (mc.player != null) {
            ftYaw = mc.player.getYaw();
            ftPitch = mc.player.getPitch();
        }
        ftNoTargetSinceMs = -1L;
        ftAttackCount = 0;
    }

    @Override
    public void onDisable() {
        target = null;
    }

    @Override
    public String getDisplayInfo() {
        if (target instanceof PlayerEntity p) return mode.getValue() + " -> " + p.getName().getString();
        if (target != null) return mode.getValue() + " -> mob";
        return mode.getValue();
    }

    /** Для TargetHUD. */
    public LivingEntity getTarget() {
        return (target != null && target.isAlive()) ? target : null;
    }

    private void pickNextInterval() {
        double base = 1000.0 / Math.max(1, aps.getValue());
        if (randomize.getValue()) {
            double jitter = base * 0.25;
            nextInterval = (long) Math.max(0, base + (ThreadLocalRandom.current().nextDouble() * 2 - 1) * jitter);
        } else {
            nextInterval = (long) base;
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.getNetworkHandler() == null) return;

        target = findTarget();

        // FunTime: гоним ротацию каждый тик, даже когда нет цели (возвратный shake)
        if (mode.is("FunTime")) {
            tickFunTime();
        }

        if (target == null) return;

        long now = System.currentTimeMillis();
        if (now - lastAttack < nextInterval) return;

        if (fullCharge.getValue() && mc.player.getAttackCooldownProgress(0.0f) < 0.92f) return;
        if (onlyCrits.getValue() && !isCritical()) return;

        Vec3d aim = getAimPoint(target);
        float[] rot = calcRotations(aim);
        float yaw   = MathHelper.wrapDegrees(rot[0]);
        float pitch = MathHelper.clamp(rot[1], -90f, 90f);

        switch (mode.getValue()) {
            case "Silent"  -> doSilent(yaw, pitch, target);
            case "Legit"   -> doLegit(yaw, pitch, target);
            case "FunTime" -> doFunTimeAttack(target);
            default        -> doSilent(yaw, pitch, target);
        }

        lastAttack = now;
        pickNextInterval();
    }

    /** Silent: послать пакет Look на сервер, ударить; клиентские yaw/pitch не трогаем. */
    private void doSilent(float yaw, float pitch, Entity target) {
        var handler = mc.getNetworkHandler();
        boolean onGround = mc.player.isOnGround();
        boolean horizCol = mc.player.horizontalCollision;
        handler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, onGround, horizCol));
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    /** Legit: плавный поворот клиентской камеры. */
    private void doLegit(float yaw, float pitch, Entity target) {
        float newYaw   = stepRotate(mc.player.getYaw(),   yaw,   smooth.getFloat());
        float newPitch = stepRotate(mc.player.getPitch(), pitch, smooth.getFloat());
        mc.player.setYaw(newYaw);
        mc.player.setPitch(newPitch);
        mc.player.setHeadYaw(newYaw);
        mc.player.setBodyYaw(newYaw);
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    /**
     * FunTime ротация: шлём ftYaw/ftPitch пакетом (silent-style),
     * клиентскую камеру не двигаем — так поведение ближе к оригиналу Rich (видимая
     * камера стоит, а серверная «ft» плавает с lerp/clamp/shake).
     */
    private void doFunTimeAttack(Entity target) {
        var handler = mc.getNetworkHandler();
        handler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                ftYaw, ftPitch, mc.player.isOnGround(), mc.player.horizontalCollision));
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        ftAttackCount++;
        ftLastAttackTimerStart = System.currentTimeMillis();
    }

    /** Реализация ротации FTAngle: lerp 0.85 с clamp 130° на delta; shake-fade на возврате. */
    private void tickFunTime() {
        float playerYaw = mc.player.getYaw();
        float playerPitch = mc.player.getPitch();

        if (target != null) {
            ftNoTargetSinceMs = -1L;

            Vec3d aim = getAimPoint(target);
            float[] rot = calcRotations(aim);
            float targetYaw = rot[0];
            float targetPitch = rot[1];

            float yawDelta   = MathHelper.wrapDegrees(targetYaw - ftYaw);
            float pitchDelta = MathHelper.wrapDegrees(targetPitch - ftPitch);
            float total = (float) Math.hypot(yawDelta, pitchDelta);
            if (total < 1e-4f) total = 1e-4f;

            float yawLimit   = Math.abs(yawDelta   / total) * 130.0f;
            float pitchLimit = Math.abs(pitchDelta / total) * 130.0f;

            float newYaw   = MathHelper.lerp(0.85f, ftYaw,   ftYaw   + MathHelper.clamp(yawDelta,   -yawLimit,   yawLimit));
            float newPitch = MathHelper.lerp(0.85f, ftPitch, ftPitch + MathHelper.clamp(pitchDelta, -pitchLimit, pitchLimit));
            ftYaw = MathHelper.wrapDegrees(newYaw);
            ftPitch = MathHelper.clamp(newPitch, -90f, 90f);
        } else {
            // Цели нет — плавно возвращаем к камере игрока с затухающим шейком.
            float retYaw   = MathHelper.wrapDegrees(playerYaw   - ftYaw);
            float retPitch = MathHelper.wrapDegrees(playerPitch - ftPitch);
            float retTotal = (float) Math.hypot(retYaw, retPitch);
            if (retTotal < 1e-4f) retTotal = 1e-4f;

            float shakeYaw   = (float) (randomBetween(18.0f, 28.0f) * Math.sin(System.currentTimeMillis() / 60.0));
            float shakePitch = (float) (randomBetween( 6.0f, 16.0f) * Math.cos(System.currentTimeMillis() / 60.0));

            if (ftNoTargetSinceMs < 0L) ftNoTargetSinceMs = System.currentTimeMillis();
            float fade = 1.0f - MathHelper.clamp(
                    (System.currentTimeMillis() - ftNoTargetSinceMs) / 1000.0f, 0f, 1f);
            shakeYaw   *= fade;
            shakePitch *= fade;

            float limitMul   = (System.currentTimeMillis() - ftLastAttackTimerStart < 535L) ? 0f : 45f;
            float yawLimit   = Math.abs(retYaw   / retTotal) * limitMul;
            float pitchLimit = Math.abs(retPitch / retTotal) * limitMul;

            float newYaw   = MathHelper.lerp(0.85f, ftYaw,   ftYaw   + MathHelper.clamp(retYaw,   -yawLimit,   yawLimit) + shakeYaw);
            float newPitch = MathHelper.lerp(0.85f, ftPitch, ftPitch + MathHelper.clamp(retPitch, -pitchLimit, pitchLimit) + shakePitch);
            ftYaw = MathHelper.wrapDegrees(newYaw);
            ftPitch = MathHelper.clamp(newPitch, -90f, 90f);
        }
    }

    private float randomBetween(float min, float max) {
        if (min == max) return min;
        if (min > max) { float t = min; min = max; max = t; }
        return (float) ThreadLocalRandom.current().nextDouble(min, max);
    }

    private float stepRotate(float current, float desired, float step) {
        float diff = MathHelper.wrapDegrees(desired - current);
        float clamped = MathHelper.clamp(diff, -step, step);
        return MathHelper.wrapDegrees(current + clamped);
    }

    private LivingEntity findTarget() {
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        double rangeSq = range.getValue() * range.getValue();

        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player) continue;
            if (!(e instanceof LivingEntity le) || !le.isAlive()) continue;
            if (le instanceof PlayerEntity p) {
                if (!players.getValue()) continue;
                if (p.isCreative() || p.isSpectator()) continue;
            } else {
                if (!mobs.getValue()) continue;
            }
            if (ignoreInvisible.getValue() && le.isInvisible()) continue;

            double distSq = mc.player.squaredDistanceTo(le);
            if (distSq > rangeSq) continue;

            if (!throughWalls.getValue() && !mc.player.canSee(le)) continue;

            float[] rot = calcRotations(getAimPoint(le));
            float deltaYaw = Math.abs(MathHelper.wrapDegrees(rot[0] - mc.player.getYaw()));
            if (deltaYaw > fov.getFloat() / 2f) continue;

            double score = distSq + le.getHealth() * 0.25;
            if (score < bestScore) {
                bestScore = score;
                best = le;
            }
        }
        return best;
    }

    private Vec3d getAimPoint(Entity e) {
        return new Vec3d(e.getX(), e.getY() + e.getHeight() * 0.85, e.getZ());
    }

    private float[] calcRotations(Vec3d aim) {
        var p = mc.player;
        double dx = aim.x - p.getX();
        double dy = aim.y - (p.getY() + p.getStandingEyeHeight());
        double dz = aim.z - p.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw   = (float) (MathHelper.atan2(dz, dx) * (180D / Math.PI)) - 90f;
        float pitch = (float) -(MathHelper.atan2(dy, horiz) * (180D / Math.PI));
        return new float[]{ MathHelper.wrapDegrees(yaw), MathHelper.wrapDegrees(pitch) };
    }

    private boolean isCritical() {
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

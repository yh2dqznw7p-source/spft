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

import java.security.SecureRandom;
import java.util.concurrent.ThreadLocalRandom;

/**
 * KillAura — авто-аура с выбором типа ротации (как Zenith/Rich DLC):
 *  - Silent     : камера НЕ двигается, правильные yaw/pitch уходят пакетом.
 *  - Legit      : плавный поворот клиентской камеры к цели.
 *  - FunTime    : порт FTAngle — lerp 0.85 с clamp 130°, возвратный shake-fade.
 *  - Spookytime : порт SPAngle — multipoint (три зоны тела), гауссовый jitter,
 *                 дыхание sin/cos, случайный overshoot, динамическая скорость по дистанции.
 */
public class KillAura extends Module {
    public final ModeSetting mode        = addMode("Mode", "Silent", "Silent", "Legit", "FunTime", "Spookytime");
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

    // --- "server-side" rotation state for Silent-style modes (FunTime / Spookytime) ---
    private float srvYaw, srvPitch;
    private long noTargetSinceMs = -1L;
    private long lastAttackTimerStart = 0L;

    // --- Spookytime state ---
    private final SecureRandom spRandom = new SecureRandom();
    private Vec3d spCurrentPoint = null;
    private long spLastPointChange = 0L;
    private int spBodyPart = 0;
    private long spLastBodyPartChange = 0L;

    public KillAura() {
        super("KillAura", "Авто-удары: silent / legit / funtime / spookytime", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        lastAttack = 0L;
        target = null;
        pickNextInterval();
        if (mc.player != null) {
            srvYaw = mc.player.getYaw();
            srvPitch = mc.player.getPitch();
        }
        noTargetSinceMs = -1L;
        spCurrentPoint = null;
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

        // Silent-style modes: прогоняем ротацию каждый тик.
        if (mode.is("FunTime"))    tickFunTime();
        if (mode.is("Spookytime")) tickSpookytime();

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
            case "Silent"     -> doSilent(yaw, pitch, target);
            case "Legit"      -> doLegit(yaw, pitch, target);
            case "FunTime"    -> doSrvPacketAttack(target);
            case "Spookytime" -> doSrvPacketAttack(target);
            default           -> doSilent(yaw, pitch, target);
        }

        lastAttack = now;
        pickNextInterval();
    }

    // ---- attack modes ----

    private void doSilent(float yaw, float pitch, Entity target) {
        var handler = mc.getNetworkHandler();
        handler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                yaw, pitch, mc.player.isOnGround(), mc.player.horizontalCollision));
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

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

    /** Общая атака для silent-style режимов: шлём srvYaw/srvPitch пакетом, камеру не трогаем. */
    private void doSrvPacketAttack(Entity target) {
        var handler = mc.getNetworkHandler();
        handler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                srvYaw, srvPitch, mc.player.isOnGround(), mc.player.horizontalCollision));
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        lastAttackTimerStart = System.currentTimeMillis();
    }

    // ---- rotations ----

    /** FunTime (FTAngle): lerp 0.85 с clamp 130° на delta; shake-fade на возврате. */
    private void tickFunTime() {
        float playerYaw = mc.player.getYaw();
        float playerPitch = mc.player.getPitch();

        if (target != null) {
            noTargetSinceMs = -1L;

            Vec3d aim = getAimPoint(target);
            float[] rot = calcRotations(aim);
            float targetYaw = rot[0];
            float targetPitch = rot[1];

            float yawDelta   = MathHelper.wrapDegrees(targetYaw - srvYaw);
            float pitchDelta = MathHelper.wrapDegrees(targetPitch - srvPitch);
            float total = (float) Math.hypot(yawDelta, pitchDelta);
            if (total < 1e-4f) total = 1e-4f;

            float yawLimit   = Math.abs(yawDelta   / total) * 130.0f;
            float pitchLimit = Math.abs(pitchDelta / total) * 130.0f;

            float newYaw   = MathHelper.lerp(0.85f, srvYaw,   srvYaw   + MathHelper.clamp(yawDelta,   -yawLimit,   yawLimit));
            float newPitch = MathHelper.lerp(0.85f, srvPitch, srvPitch + MathHelper.clamp(pitchDelta, -pitchLimit, pitchLimit));
            srvYaw = MathHelper.wrapDegrees(newYaw);
            srvPitch = MathHelper.clamp(newPitch, -90f, 90f);
        } else {
            float retYaw   = MathHelper.wrapDegrees(playerYaw   - srvYaw);
            float retPitch = MathHelper.wrapDegrees(playerPitch - srvPitch);
            float retTotal = (float) Math.hypot(retYaw, retPitch);
            if (retTotal < 1e-4f) retTotal = 1e-4f;

            float shakeYaw   = (float) (randomBetween(18.0f, 28.0f) * Math.sin(System.currentTimeMillis() / 60.0));
            float shakePitch = (float) (randomBetween( 6.0f, 16.0f) * Math.cos(System.currentTimeMillis() / 60.0));

            if (noTargetSinceMs < 0L) noTargetSinceMs = System.currentTimeMillis();
            float fade = 1.0f - MathHelper.clamp(
                    (System.currentTimeMillis() - noTargetSinceMs) / 1000.0f, 0f, 1f);
            shakeYaw   *= fade;
            shakePitch *= fade;

            float limitMul   = (System.currentTimeMillis() - lastAttackTimerStart < 535L) ? 0f : 45f;
            float yawLimit   = Math.abs(retYaw   / retTotal) * limitMul;
            float pitchLimit = Math.abs(retPitch / retTotal) * limitMul;

            float newYaw   = MathHelper.lerp(0.85f, srvYaw,   srvYaw   + MathHelper.clamp(retYaw,   -yawLimit,   yawLimit) + shakeYaw);
            float newPitch = MathHelper.lerp(0.85f, srvPitch, srvPitch + MathHelper.clamp(retPitch, -pitchLimit, pitchLimit) + shakePitch);
            srvYaw = MathHelper.wrapDegrees(newYaw);
            srvPitch = MathHelper.clamp(newPitch, -90f, 90f);
        }
    }

    /**
     * Spookytime (SPAngle): "нейронная" ротация — мультиточка по телу,
     * гауссов jitter, дыхание sin/cos, overshoot, скорость зависит от дистанции.
     */
    private void tickSpookytime() {
        if (target == null) {
            spCurrentPoint = null;
            return;
        }

        long now = System.currentTimeMillis();
        if (spCurrentPoint == null || now - spLastPointChange > 50L + spRandom.nextInt(100)) {
            spCurrentPoint = pickMultiPoint(target);
            spLastPointChange = now;
        }

        float[] rot = calcRotations(spCurrentPoint);
        float targetYaw = rot[0];
        float targetPitch = rot[1];

        float yawDelta   = MathHelper.wrapDegrees(targetYaw - srvYaw);
        float pitchDelta = MathHelper.wrapDegrees(targetPitch - srvPitch);

        if (Math.abs(yawDelta) < 1e-4f && Math.abs(pitchDelta) > 0f) {
            yawDelta += spRandomF(0.1f, 0.5f) + 0.1f * 1.0313f;
        }
        if (Math.abs(pitchDelta) < 1e-4f && Math.abs(yawDelta) > 0f) {
            pitchDelta += spRandomF(0.1f, 0.5f) + 0.1f * 1.0313f;
        }

        yawDelta   = Math.min(Math.abs(yawDelta),   60f + spRandom.nextFloat() * 1.0329834f) * Math.signum(yawDelta);
        pitchDelta = Math.min(Math.abs(pitchDelta), spRandomF(23.133f, 26.477f)) * Math.signum(pitchDelta);

        float distance = mc.player.distanceTo(target);
        float speed = calcDynamicSpeed(distance, yawDelta, pitchDelta);

        float breathX = (float) Math.sin(now / 300.0) * 0.03f;
        float breathY = (float) Math.cos(now / 500.0) * 0.02f;

        float jitterYaw = 0f, jitterPitch = 0f;
        if (spRandom.nextFloat() < 0.02f) {
            jitterYaw   = (spRandom.nextFloat() - 0.5f) * 1.2f;
            jitterPitch = (spRandom.nextFloat() - 0.5f) * 0.8f;
        }

        float smoothYaw   = yawDelta   * speed * 0.7f + breathX + jitterYaw;
        float smoothPitch = pitchDelta * speed * 0.5f + breathY + jitterPitch;

        if (spRandom.nextFloat() < 0.05f && Math.abs(yawDelta) > 5.0f) {
            float overshoot = 1.1f + spRandom.nextFloat() * 0.3f;
            smoothYaw *= overshoot;
        }

        srvYaw   = MathHelper.wrapDegrees(srvYaw + smoothYaw);
        srvPitch = MathHelper.clamp(srvPitch + smoothPitch, -90f, 90f);
    }

    private Vec3d pickMultiPoint(Entity entity) {
        long now = System.currentTimeMillis();
        if (now - spLastBodyPartChange > 800L + spRandom.nextInt(400)) {
            spBodyPart = spRandom.nextInt(3);
            spLastBodyPartChange = now;
        }

        double width = entity.getWidth();
        double height = entity.getHeight();

        double randomX = entity.getX() + (spRandom.nextGaussian() * 0.4) * width;
        double randomZ = entity.getZ() + (spRandom.nextGaussian() * 0.4) * width;

        double randomY;
        switch (spBodyPart) {
            case 0 -> randomY = entity.getY() + height * spRandomF(0.6f, 0.8f);   // грудь
            case 1 -> randomY = entity.getY() + height * spRandomF(0.85f, 0.95f); // голова
            default -> randomY = entity.getY() + height * spRandomF(0.4f, 0.6f);  // живот
        }

        randomX += Math.sin(now / 200.0) * width * 0.1;
        randomZ += Math.cos(now / 200.0) * width * 0.1;

        return new Vec3d(randomX, randomY, randomZ);
    }

    private float calcDynamicSpeed(float distance, float yawDelta, float pitchDelta) {
        float r = spRandom.nextFloat();

        float distanceFactor;
        if (distance < 2.0f)      distanceFactor = 1.4f + r * 0.6f;
        else if (distance < 4.0f) distanceFactor = 1.2f + r * 0.6f;
        else                      distanceFactor = 0.9f + r * 0.5f;

        float smoothBase;
        if (distance < 3.0f && Math.abs(yawDelta) < 10.0f)
            smoothBase = 0.25f + spRandom.nextFloat() * 0.15f;
        else if (distance > 5.0f || Math.abs(yawDelta) > 30.0f)
            smoothBase = 0.35f + spRandom.nextFloat() * 0.2f;
        else
            smoothBase = 0.28f + spRandom.nextFloat() * 0.15f;

        return smoothBase * distanceFactor;
    }

    private float spRandomF(float min, float max) {
        return min + spRandom.nextFloat() * (max - min);
    }

    // ---- common ----

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

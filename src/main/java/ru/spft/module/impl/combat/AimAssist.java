package ru.spft.module.impl.combat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ru.spft.module.Category;
import ru.spft.module.Module;
import ru.spft.setting.BooleanSetting;
import ru.spft.setting.ModeSetting;
import ru.spft.setting.NumberSetting;

/**
 * Aim Assist — "полностью сам наводится" на ближайшего врага:
 *  - автоматически ищет цель в радиусе range
 *  - плавно интерполирует yaw/pitch игрока к цели (без мгновенного snap)
 *  - учитывает через-стену (throughWalls) и FOV-ограничение
 *
 * ВНИМАНИЕ: модуль потенциально нарушает правила большинства серверов.
 * Это технический пример работы с Fabric API и поворотом камеры клиента.
 */
public class AimAssist extends Module {
    public final NumberSetting range    = addNumber("Range", 5.0, 1.0, 8.0, 0.1);
    public final NumberSetting fov      = addNumber("FOV", 120, 10, 360, 5);
    public final NumberSetting yawSpeed = addNumber("YawSpeed", 35, 1, 180, 1);
    public final NumberSetting pitchSpeed = addNumber("PitchSpeed", 25, 1, 180, 1);
    public final ModeSetting aimPart    = addMode("AimPart", "Body", "Head", "Body", "Legs");
    public final BooleanSetting throughWalls = addBoolean("ThroughWalls", false);
    public final BooleanSetting players     = addBoolean("Players", true);
    public final BooleanSetting mobs        = addBoolean("Mobs", true);
    public final BooleanSetting ignoreInvisible = addBoolean("IgnoreInvisible", true);
    public final BooleanSetting onlyWhileAttacking = addBoolean("OnlyWhileAttacking", false);

    private Entity currentTarget = null;

    public AimAssist() {
        super("AimAssist", "Автонаведение камеры на ближайшего врага", Category.COMBAT);
    }

    @Override
    public void onDisable() {
        currentTarget = null;
    }

    public Entity getCurrentTarget() {
        return currentTarget;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (onlyWhileAttacking.getValue() && !mc.options.attackKey.isPressed()) {
            currentTarget = null;
            return;
        }

        Entity target = findTarget();
        currentTarget = target;
        if (target == null) return;

        Vec3d aimPoint = getAimPoint(target);
        float[] rotations = calcRotations(aimPoint);
        float targetYaw = rotations[0];
        float targetPitch = rotations[1];

        float newYaw = smoothRotate(mc.player.getYaw(), targetYaw, yawSpeed.getFloat());
        float newPitch = smoothRotate(mc.player.getPitch(), targetPitch, pitchSpeed.getFloat());
        newPitch = MathHelper.clamp(newPitch, -90f, 90f);

        mc.player.setYaw(newYaw);
        mc.player.setPitch(newPitch);
        mc.player.setHeadYaw(newYaw);
        mc.player.setBodyYaw(newYaw);
    }

    private Entity findTarget() {
        PlayerEntity self = mc.player;
        Entity best = null;
        double bestPriority = Double.MAX_VALUE;
        double rangeSq = range.getValue() * range.getValue();

        for (Entity e : mc.world.getEntities()) {
            if (e == self) continue;
            if (!(e instanceof LivingEntity le)) continue;
            if (!le.isAlive() || le.isRemoved()) continue;
            if (le instanceof PlayerEntity p) {
                if (!players.getValue()) continue;
                if (p.isCreative() || p.isSpectator()) continue;
            } else {
                if (!mobs.getValue()) continue;
            }
            if (ignoreInvisible.getValue() && le.isInvisible()) continue;

            double distSq = self.squaredDistanceTo(le);
            if (distSq > rangeSq) continue;

            if (!throughWalls.getValue() && !mc.player.getWorld().raycast(
                    new net.minecraft.world.RaycastContext(
                            mc.player.getEyePos(),
                            new Vec3d(le.getX(), le.getY() + le.getHeight() * 0.5, le.getZ()),
                            net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                            net.minecraft.world.RaycastContext.FluidHandling.NONE,
                            mc.player)).getType().equals(net.minecraft.util.hit.HitResult.Type.MISS)) {
                continue;
            }

            // FOV filter
            float[] rot = calcRotations(getAimPoint(le));
            float deltaYaw = Math.abs(MathHelper.wrapDegrees(rot[0] - self.getYaw()));
            if (deltaYaw > fov.getFloat() / 2f) continue;

            // приоритет: меньше угол + меньше дистанция
            double priority = deltaYaw * 0.5 + distSq * 0.25;
            if (priority < bestPriority) {
                bestPriority = priority;
                best = le;
            }
        }
        return best;
    }

    private Vec3d getAimPoint(Entity e) {
        double x = e.getX();
        double z = e.getZ();
        double y;
        String part = aimPart.getValue();
        switch (part.toLowerCase()) {
            case "head" -> y = e.getY() + e.getStandingEyeHeight();
            case "legs" -> y = e.getY() + 0.2;
            default     -> y = e.getY() + e.getHeight() * 0.5;
        }
        return new Vec3d(x, y, z);
    }

    private float[] calcRotations(Vec3d target) {
        PlayerEntity p = mc.player;
        double dx = target.x - p.getX();
        double dy = target.y - (p.getY() + p.getStandingEyeHeight());
        double dz = target.z - p.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (MathHelper.atan2(dz, dx) * (180D / Math.PI)) - 90f;
        float pitch = (float) -(MathHelper.atan2(dy, horiz) * (180D / Math.PI));
        return new float[]{ MathHelper.wrapDegrees(yaw), MathHelper.wrapDegrees(pitch) };
    }

    private float smoothRotate(float current, float target, float step) {
        float diff = MathHelper.wrapDegrees(target - current);
        float clamped = MathHelper.clamp(diff, -step, step);
        return MathHelper.wrapDegrees(current + clamped);
    }
}

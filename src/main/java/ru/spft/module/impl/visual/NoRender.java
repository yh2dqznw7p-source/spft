package ru.spft.module.impl.visual;

import ru.spft.module.Category;
import ru.spft.module.Module;
import ru.spft.setting.BooleanSetting;

/**
 * NoRender — отключает тяжёлые/раздражающие эффекты.
 *
 * Все опции реально подключены к миксинам:
 *   - NoBlockBreakParticles → ParticleManagerMixin + WorldRendererMixin
 *   - NoAllParticles        → ParticleManagerMixin
 *   - NoFire                → ParticleManagerMixin (частицы flame/soul/small/campfire/lava)
 *   - NoOverlayFire/NoPumpkinOverlay/NoPortalOverlay → InGameHudMixin
 *     (если включена хотя бы одна — отключается блок оверлеев целиком)
 *   - NoCameraShake/NoBobbing → GameRendererMixin (bobView)
 *   - NoHurtCam             → GameRendererMixin (tiltViewWhenHurt)
 */
public class NoRender extends Module {
    public final BooleanSetting noBlockBreakParticles = addBoolean("NoBlockBreakParticles", true);
    public final BooleanSetting noAllParticles        = addBoolean("NoAllParticles", false);
    public final BooleanSetting noFire                = addBoolean("NoFire", true);
    public final BooleanSetting noOverlayFire         = addBoolean("NoOverlayFire", true);
    public final BooleanSetting noCameraShake         = addBoolean("NoCameraShake", true);
    public final BooleanSetting noHurtCam             = addBoolean("NoHurtCam", true);
    public final BooleanSetting noBobbing             = addBoolean("NoBobbing", true);
    public final BooleanSetting noPumpkinOverlay      = addBoolean("NoPumpkinOverlay", true);
    public final BooleanSetting noPortalOverlay       = addBoolean("NoPortalOverlay", true);

    public NoRender() {
        super("NoRender", "Убирает частицы, огонь, качку камеры и прочие нагружающие эффекты", Category.VISUAL);
    }
}

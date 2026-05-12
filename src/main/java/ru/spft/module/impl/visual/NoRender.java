package ru.spft.module.impl.visual;

import ru.spft.module.Category;
import ru.spft.module.Module;
import ru.spft.setting.BooleanSetting;

/**
 * NoRender — отключает тяжёлые/раздражающие эффекты.
 * Работает совместно с миксинами (ParticleManagerMixin, GameRendererMixin, WorldRendererMixin).
 * Фактическое отключение делается через проверку SpftClient.get().getModuleManager().getModule(NoRender.class).
 */
public class NoRender extends Module {
    public final BooleanSetting noBlockBreakParticles = addBoolean("NoBlockBreakParticles", true);
    public final BooleanSetting noAllParticles        = addBoolean("NoAllParticles", false);
    public final BooleanSetting noFire                = addBoolean("NoFire", true);
    public final BooleanSetting noOverlayFire         = addBoolean("NoOverlayFire", true);
    public final BooleanSetting noGrass               = addBoolean("NoGrass", true);
    public final BooleanSetting noCameraShake         = addBoolean("NoCameraShake", true);
    public final BooleanSetting noHurtCam             = addBoolean("NoHurtCam", true);
    public final BooleanSetting noBobbing             = addBoolean("NoBobbing", true);
    public final BooleanSetting noWeather             = addBoolean("NoWeather", false);
    public final BooleanSetting noFog                 = addBoolean("NoFog", false);
    public final BooleanSetting noPumpkinOverlay      = addBoolean("NoPumpkinOverlay", true);
    public final BooleanSetting noPortalOverlay       = addBoolean("NoPortalOverlay", true);
    public final BooleanSetting noArmorStand          = addBoolean("NoArmorStand", false);
    public final BooleanSetting noXpOrb               = addBoolean("NoXpOrb", false);

    public NoRender() {
        super("NoRender", "Убирает частицы, огонь, качку камеры и прочие нагружающие эффекты", Category.VISUAL);
    }
}

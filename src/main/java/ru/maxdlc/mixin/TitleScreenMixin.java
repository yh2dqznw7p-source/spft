package ru.maxdlc.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.maxdlc.MaxDLCClient;
import ru.maxdlc.gui.menu.MaxDLCMainMenu;
import ru.maxdlc.module.impl.misc.CustomMainMenu;

/**
 * Подменяет vanilla TitleScreen на Max DLC, если модуль CustomMainMenu включён.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void maxdlc$replaceTitle(CallbackInfo ci) {
        if (MaxDLCClient.get() == null) return;
        CustomMainMenu cmm = MaxDLCClient.get().getModuleManager().getModule(CustomMainMenu.class);
        if (cmm == null || !cmm.isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen instanceof MaxDLCMainMenu) return;
        mc.setScreen(new MaxDLCMainMenu());
        ci.cancel();
    }
}

package ru.maxdlc.module.impl.misc;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import ru.maxdlc.gui.menu.MaxDLCMainMenu;
import ru.maxdlc.module.Category;
import ru.maxdlc.module.Module;

/**
 * CustomMainMenu — если включён, ваниль TitleScreen подменяется на MaxDLCMainMenu
 * (см. TitleScreenMixin). Можно выключить — получишь обычное меню Minecraft.
 */
public class CustomMainMenu extends Module {

    public CustomMainMenu() {
        super("CustomMainMenu", "Заменяет главное меню Minecraft на Max DLC", Category.MISC);
    }

    @Override
    public void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen instanceof TitleScreen) {
            mc.setScreen(new MaxDLCMainMenu());
        }
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen instanceof MaxDLCMainMenu) {
            mc.setScreen(new TitleScreen());
        }
    }
}

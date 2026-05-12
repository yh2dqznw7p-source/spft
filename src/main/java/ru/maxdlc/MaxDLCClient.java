package ru.maxdlc;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.maxdlc.bind.BindManager;
import ru.maxdlc.command.CommandManager;
import ru.maxdlc.config.ConfigManager;
import ru.maxdlc.gui.ClickGuiScreen;
import ru.maxdlc.module.ModuleManager;
import ru.maxdlc.module.impl.misc.ClickGuiModule;

public class MaxDLCClient implements ClientModInitializer {
    public static final String MOD_ID = "maxdlc";
    public static final String NAME = "maxDLC";
    public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

    private static MaxDLCClient INSTANCE;

    private final ModuleManager moduleManager = new ModuleManager();
    private final BindManager bindManager = new BindManager();
    private final CommandManager commandManager = new CommandManager();
    private final ConfigManager configManager = new ConfigManager();

    private ClickGuiScreen clickGuiScreen;

    public static MaxDLCClient get() {
        return INSTANCE;
    }

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        LOGGER.info("Инициализация {}", NAME);

        moduleManager.init();
        commandManager.init();
        configManager.load(this);

        // Tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> onClientTick(client));

        // Перехватываем клиентский чат для префикса команд ".xxx"
        ClientSendMessageEvents.ALLOW_CHAT.register(msg -> !commandManager.handle(msg));

        // Рендер
        WorldRenderEvents.LAST.register(context -> {
            moduleManager.onRender(context.tickCounter().getTickDelta(true));
        });

        // Сохраняем конфиг при закрытии
        Runtime.getRuntime().addShutdownHook(new Thread(() -> configManager.save(this)));
    }

    private void onClientTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        // Открытие ClickGUI на RIGHT_SHIFT
        long handle = client.getWindow().getHandle();
        if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS) {
            if (!rightShiftDown && client.currentScreen == null) {
                openClickGui();
            }
            rightShiftDown = true;
        } else {
            rightShiftDown = false;
        }

        bindManager.tick();
        moduleManager.onTick();
    }

    private boolean rightShiftDown = false;

    public void openClickGui() {
        if (clickGuiScreen == null) clickGuiScreen = new ClickGuiScreen();
        mc().setScreen(clickGuiScreen);
        ClickGuiModule gui = moduleManager.getModule(ClickGuiModule.class);
        if (gui != null && !gui.isEnabled()) gui.setEnabled(true);
    }

    public static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }

    public ModuleManager getModuleManager() { return moduleManager; }
    public BindManager getBindManager() { return bindManager; }
    public CommandManager getCommandManager() { return commandManager; }
    public ConfigManager getConfigManager() { return configManager; }
}

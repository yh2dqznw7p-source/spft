package ru.maxdlc.command;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import ru.maxdlc.command.impl.BindCommand;
import ru.maxdlc.command.impl.HelpCommand;
import ru.maxdlc.command.impl.ToggleCommand;

import java.util.ArrayList;
import java.util.List;

/**
 * Менеджер клиентских команд. Перехватывает сообщения, начинающиеся с префикса ".".
 */
public class CommandManager {
    public static final String PREFIX = ".";
    private final List<Command> commands = new ArrayList<>();

    public void init() {
        register(new BindCommand());
        register(new ToggleCommand());
        register(new HelpCommand());
    }

    public void register(Command c) {
        commands.add(c);
    }

    public List<Command> getCommands() {
        return commands;
    }

    /**
     * @return true если сообщение было клиентской командой и НЕ должно отправляться на сервер.
     */
    public boolean handle(String message) {
        if (!message.startsWith(PREFIX)) return false;
        String raw = message.substring(PREFIX.length()).trim();
        if (raw.isEmpty()) return false;
        String[] parts = raw.split("\\s+");
        String name = parts[0];
        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);

        for (Command c : commands) {
            if (c.getName().equalsIgnoreCase(name) || matchAlias(c, name)) {
                try {
                    c.execute(args);
                } catch (Exception e) {
                    chat("§cОшибка: " + e.getMessage());
                }
                return true;
            }
        }
        chat("§cНеизвестная команда: §f" + name + "§7. Используйте §f.help");
        return true;
    }

    private boolean matchAlias(Command c, String name) {
        for (String a : c.getAliases()) if (a.equalsIgnoreCase(name)) return true;
        return false;
    }

    public static void chat(String message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.of("§8[§cmaxDLC§8] §7" + message), false);
    }
}

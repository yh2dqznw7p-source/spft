package ru.spft.command.impl;

import ru.spft.SpftClient;
import ru.spft.command.Command;
import ru.spft.command.CommandManager;

public class HelpCommand extends Command {
    public HelpCommand() {
        super("help", "Список команд", ".help", "?");
    }

    @Override
    public void execute(String[] args) {
        CommandManager.chat("§7Доступные команды (§fпрефикс " + CommandManager.PREFIX + "§7):");
        for (Command c : SpftClient.get().getCommandManager().getCommands()) {
            CommandManager.chat(" §f" + c.getUsage() + " §8— §7" + c.getDescription());
        }
        CommandManager.chat("§7Открыть ClickGUI: §fRIGHT_SHIFT");
    }
}

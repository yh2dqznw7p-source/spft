package ru.spft.command.impl;

import ru.spft.SpftClient;
import ru.spft.command.Command;
import ru.spft.command.CommandManager;
import ru.spft.module.Module;

public class ToggleCommand extends Command {
    public ToggleCommand() {
        super("toggle", "Включить/выключить модуль по имени", ".toggle <module>", "t");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            CommandManager.chat("§7Использование: §f.toggle <module>");
            return;
        }
        Module module = SpftClient.get().getModuleManager().getModule(args[0]);
        if (module == null) {
            CommandManager.chat("§cМодуль §f" + args[0] + "§c не найден.");
            return;
        }
        module.toggle();
        CommandManager.chat((module.isEnabled() ? "§aВключен §f" : "§cВыключен §f") + module.getName());
    }
}

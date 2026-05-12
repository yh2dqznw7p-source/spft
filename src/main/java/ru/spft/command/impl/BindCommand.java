package ru.spft.command.impl;

import ru.spft.SpftClient;
import ru.spft.bind.BindManager;
import ru.spft.command.Command;
import ru.spft.command.CommandManager;
import ru.spft.module.Module;
import ru.spft.setting.KeyNames;

/**
 * Команда .bind
 * Форматы:
 *   .bind add <Module> <Key>    — добавить ещё один бинд (не перезаписывая существующие)
 *   .bind remove <Module> <Key> — удалить конкретный бинд
 *   .bind clear <Module>         — удалить все биндовые комбинации у модуля
 *   .bind list                   — список всех биндов
 *   .bind help                   — краткая подсказка
 */
public class BindCommand extends Command {
    public BindCommand() {
        super("bind",
                "Управление биндами (add/remove/clear/list)",
                ".bind add <module> <key>",
                "b", "keybind");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) { help(); return; }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "add" -> add(args);
            case "remove", "rm", "del" -> remove(args);
            case "clear" -> clear(args);
            case "list", "ls" -> list();
            case "help", "?" -> help();
            default -> CommandManager.chat("§cНеизвестная подкоманда: §f" + sub);
        }
    }

    private void add(String[] args) {
        if (args.length < 3) {
            CommandManager.chat("§7Использование: §f.bind add <module> <key>");
            return;
        }
        Module module = SpftClient.get().getModuleManager().getModule(args[1]);
        if (module == null) { CommandManager.chat("§cМодуль §f" + args[1] + "§c не найден."); return; }
        int key = KeyNames.keyOf(args[2]);
        if (key < 0) { CommandManager.chat("§cКлавиша §f" + args[2] + "§c не распознана."); return; }
        SpftClient.get().getBindManager().add(module, key);
        CommandManager.chat("§aБинд §f" + module.getName() + " §8→ §f" + args[2].toUpperCase() + " §aдобавлен.");
    }

    private void remove(String[] args) {
        if (args.length < 3) {
            CommandManager.chat("§7Использование: §f.bind remove <module> <key>");
            return;
        }
        Module module = SpftClient.get().getModuleManager().getModule(args[1]);
        if (module == null) { CommandManager.chat("§cМодуль §f" + args[1] + "§c не найден."); return; }
        int key = KeyNames.keyOf(args[2]);
        boolean ok = SpftClient.get().getBindManager().remove(module, key);
        CommandManager.chat(ok ? "§aБинд удалён." : "§eТакой бинд не найден.");
    }

    private void clear(String[] args) {
        if (args.length < 2) { CommandManager.chat("§7Использование: §f.bind clear <module>"); return; }
        Module module = SpftClient.get().getModuleManager().getModule(args[1]);
        if (module == null) { CommandManager.chat("§cМодуль §f" + args[1] + "§c не найден."); return; }
        int n = SpftClient.get().getBindManager().removeAll(module);
        CommandManager.chat("§aУдалено биндов: §f" + n);
    }

    private void list() {
        var binds = SpftClient.get().getBindManager().getBinds();
        if (binds.isEmpty()) { CommandManager.chat("§7Нет активных биндов."); return; }
        CommandManager.chat("§7Биндов: §f" + binds.size());
        for (BindManager.BindEntry e : binds) {
            CommandManager.chat(" §8- §f" + e.module().getName() + " §8→ §a" + KeyNames.nameOf(e.key()));
        }
    }

    private void help() {
        CommandManager.chat("§7Команды бинд-менеджера:");
        CommandManager.chat(" §f.bind add <module> <key> §8— §7добавить (несколько биндов разрешены)");
        CommandManager.chat(" §f.bind remove <module> <key> §8— §7удалить конкретный");
        CommandManager.chat(" §f.bind clear <module> §8— §7удалить все биндом модуля");
        CommandManager.chat(" §f.bind list §8— §7список");
    }
}

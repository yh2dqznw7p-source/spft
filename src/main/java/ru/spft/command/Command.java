package ru.spft.command;

public abstract class Command {
    private final String name;
    private final String description;
    private final String usage;
    private final String[] aliases;

    protected Command(String name, String description, String usage, String... aliases) {
        this.name = name;
        this.description = description;
        this.usage = usage;
        this.aliases = aliases;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getUsage() { return usage; }
    public String[] getAliases() { return aliases; }

    public abstract void execute(String[] args);
}

package ru.spft.module;

public enum Category {
    COMBAT("Combat"),
    MOVEMENT("Movement"),
    VISUAL("Visual"),
    PLAYER("Player"),
    MISC("Misc");

    private final String name;

    Category(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}

package ru.maxdlc.gui.menu;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import ru.maxdlc.mixin.MinecraftClientAccessor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Хранилище offline-ников для AltManager + смена сессии клиента.
 * Файл: config/maxdlc/accounts.txt. Первая строка с префиксом #LAST= — последний выбранный ник.
 */
public final class AccountStore {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("maxdlc").resolve("accounts.txt");

    private static final List<String> accounts = new ArrayList<>();
    private static String lastSelected = "";
    private static boolean loaded = false;

    private AccountStore() {}

    public static synchronized List<String> getAccounts() {
        ensureLoaded();
        return accounts;
    }

    public static synchronized String getLastSelected() {
        ensureLoaded();
        return lastSelected;
    }

    public static synchronized void add(String name) {
        ensureLoaded();
        if (name == null || name.isBlank()) return;
        if (!accounts.contains(name)) {
            accounts.add(name);
            save();
        }
    }

    public static synchronized void remove(String name) {
        ensureLoaded();
        if (accounts.remove(name)) save();
    }

    public static synchronized void clearAll() {
        ensureLoaded();
        accounts.clear();
        save();
    }

    /** Сменить текущую сессию на offline-ник. Работает только в кастомных/пиратских лаунчерах. */
    public static void loginOffline(String name) {
        if (name == null || name.isBlank()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        UUID uuid = offlineUuid(name);
        try {
            Session s = new Session(name, uuid, "0", Optional.empty(), Optional.empty(),
                    Session.AccountType.LEGACY);
            ((MinecraftClientAccessor) mc).maxdlc$setSession(s);
            lastSelected = name;
            save();
        } catch (Throwable t) {
            System.err.println("[maxDLC] Failed to set session: " + t.getMessage());
        }
    }

    /** Тот же алгоритм, что использует Mojang для offline-игроков. */
    private static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            if (!Files.exists(FILE)) return;
            for (String line : Files.readAllLines(FILE)) {
                String t = line.trim();
                if (t.isEmpty()) continue;
                if (t.startsWith("#LAST=")) lastSelected = t.substring(6);
                else accounts.add(t);
            }
        } catch (IOException ignored) {}
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            List<String> lines = new ArrayList<>();
            lines.add("#LAST=" + lastSelected);
            lines.addAll(accounts);
            Files.write(FILE, lines);
        } catch (IOException ignored) {}
    }
}

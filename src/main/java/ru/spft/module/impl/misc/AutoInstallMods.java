package ru.spft.module.impl.misc;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;
import ru.spft.command.CommandManager;
import ru.spft.module.Category;
import ru.spft.module.Module;
import ru.spft.setting.BooleanSetting;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AutoInstallMods — одним кликом скачивает в папку mods/ рекомендуемые
 * оптимизационные Fabric-моды для 1.21.4 (Sodium, Lithium, FerriteCore, ImmediatelyFast,
 * EntityCulling, ModernFix) прямо с Modrinth CDN.
 *
 * Установка запускается при включении модуля (один раз); после установки модуль выключается.
 * Требуется перезапуск игры для применения.
 */
public class AutoInstallMods extends Module {
    public final BooleanSetting sodium       = addBoolean("Sodium", true);
    public final BooleanSetting lithium      = addBoolean("Lithium", true);
    public final BooleanSetting ferriteCore  = addBoolean("FerriteCore", true);
    public final BooleanSetting immediatelyFast = addBoolean("ImmediatelyFast", true);
    public final BooleanSetting entityCulling = addBoolean("EntityCulling", true);
    public final BooleanSetting modernFix    = addBoolean("ModernFix", false);
    public final BooleanSetting openFolderWhenDone = addBoolean("OpenFolderWhenDone", true);

    public AutoInstallMods() {
        super("AutoInstallMods", "Скачивает оптимизационные моды (Sodium, Lithium, ...) в mods/", Category.MISC);
    }

    @Override
    public void onEnable() {
        CompletableFuture.runAsync(this::installAll);
    }

    private void installAll() {
        Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");
        try { Files.createDirectories(modsDir); } catch (IOException e) {
            CommandManager.chat("§cНе удалось открыть папку mods: " + e.getMessage());
            setEnabled(false);
            return;
        }

        Map<String, String> targets = new LinkedHashMap<>();
        // URL-ы Modrinth CDN на конкретные версии для 1.21.4. При желании легко обновить.
        if (sodium.getValue())        targets.put("sodium",       "https://cdn.modrinth.com/data/AANobbMI/versions/m6cpiOZr/sodium-fabric-0.6.5%2Bmc1.21.4.jar");
        if (lithium.getValue())       targets.put("lithium",      "https://cdn.modrinth.com/data/gvQqBUqZ/versions/7iA7nDb1/lithium-fabric-0.14.1-mc1.21.4.jar");
        if (ferriteCore.getValue())   targets.put("ferritecore",  "https://cdn.modrinth.com/data/uXXizFIs/versions/PxPZiDIl/ferritecore-7.0.2-fabric.jar");
        if (immediatelyFast.getValue()) targets.put("immediatelyfast","https://cdn.modrinth.com/data/5ZwdcRci/versions/Xr2Ig5RF/ImmediatelyFast-Fabric-1.4.0%2B1.21.4.jar");
        if (entityCulling.getValue()) targets.put("entityculling","https://cdn.modrinth.com/data/NNAgCjsB/versions/QdmE6KkQ/entityculling-fabric-1.7.2-mc1.21.4.jar");
        if (modernFix.getValue())     targets.put("modernfix",    "https://cdn.modrinth.com/data/nmDcB62a/versions/xYqGQ2qX/modernfix-fabric-5.20.0%2Bmc1.21.4.jar");

        int ok = 0, fail = 0;
        for (Map.Entry<String, String> e : targets.entrySet()) {
            CommandManager.chat("§7Скачиваю §f" + e.getKey() + "§7...");
            if (download(e.getValue(), modsDir)) ok++; else fail++;
        }

        CommandManager.chat("§aГотово: §f" + ok + "§a установлено, §c" + fail + "§7 ошибок. Перезапустите игру.");
        if (openFolderWhenDone.getValue() && ok > 0) {
            Util.getOperatingSystem().open(modsDir.toFile());
        }
        setEnabled(false);
    }

    private boolean download(String url, Path modsDir) {
        try {
            URL u = URI.create(url).toURL();
            URLConnection conn = u.openConnection();
            conn.setRequestProperty("User-Agent", "SPFT-Client/1.0");
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(60_000);

            String fileName = url.substring(url.lastIndexOf('/') + 1);
            // url-decode %20 и %2B
            fileName = java.net.URLDecoder.decode(fileName, java.nio.charset.StandardCharsets.UTF_8);
            Path out = modsDir.resolve(fileName);
            try (InputStream in = conn.getInputStream()) {
                Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception ex) {
            CommandManager.chat("§cОшибка скачивания: " + ex.getMessage());
            return false;
        }
    }
}

package ru.spft.module.impl.combat;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import ru.spft.module.Category;
import ru.spft.module.Module;
import ru.spft.setting.BooleanSetting;
import ru.spft.setting.ModeSetting;

/**
 * AutoSwap — крутой авто-свап по биндингу модуля.
 *
 * Логика (по rockstar): нажал кнопку — свапнулся на "Первый предмет",
 * нажал ещё раз — свапнулся на "Второй предмет", и так туда-сюда.
 *
 * В SPFT модуль использует стандартный {@link Module#getKeyBind()} как триггер:
 * при активации модуля (из BindManager это тоггл) выполняется один swap. Чтобы
 * не превращать модуль в "залипший включён" — после swap сразу выключается сам.
 *
 * Реализация swap: 3 клика по слотам (pickup hotbar → pickup offhand → pickup hotbar),
 * между ними, как у rockstar, временно останавливаем спринт, чтобы сервер не ругался
 * на movement во время инвентарных пакетов.
 */
public class AutoSwap extends Module {
    public final ModeSetting firstItem  = addMode("Первый предмет", "Шар",  "Шар", "Золотое яблоко", "Щит", "Тотем");
    public final ModeSetting secondItem = addMode("Второй предмет", "Тотем", "Шар", "Золотое яблоко", "Щит", "Тотем");
    public final BooleanSetting swapRender    = addBoolean("Показ свапа", true);
    public final BooleanSetting onlyEnchanted = addBoolean("Только Чар. тотемы", false);

    private boolean isFirstItem = true;
    private boolean bypassActive;
    private boolean bypassSwapped;
    private int bypassSlot = -1;
    private String bypassItemName = "";
    private long swapStartMs = 0;

    public AutoSwap() {
        super("AutoSwap", "Свап предметов в оффхенд одной кнопкой", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        // Триггерим один свап при "включении" модуля (бинд).
        doTrigger();
    }

    private void doTrigger() {
        if (mc.player == null) { disableSelf(); return; }
        String selected = isFirstItem ? firstItem.getValue() : secondItem.getValue();
        switch (selected) {
            case "Шар"             -> prepareSwap(Items.PLAYER_HEAD,       "Шар",             false);
            case "Тотем"           -> prepareSwap(Items.TOTEM_OF_UNDYING,  "Тотем",           onlyEnchanted.getValue());
            case "Золотое яблоко"  -> prepareSwap(Items.GOLDEN_APPLE,      "Золотое яблоко",  false);
            case "Щит"             -> prepareSwap(Items.SHIELD,            "Щит",             false);
            default -> { disableSelf(); return; }
        }
        isFirstItem = !isFirstItem;
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        ScreenHandler screenHandler = mc.player.playerScreenHandler;
        if (!bypassActive) return;

        if (!bypassSwapped && bypassSlot != -1) {
            int syncSlot = bypassSlot < 9 ? bypassSlot + 36 : bypassSlot;
            boolean wasSprinting = mc.player.isSprinting();
            if (wasSprinting && mc.player.networkHandler != null) {
                mc.player.networkHandler.sendPacket(
                    new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
            }
            if (mc.interactionManager != null) {
                mc.interactionManager.clickSlot(screenHandler.syncId, syncSlot, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(screenHandler.syncId, 45,       0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(screenHandler.syncId, syncSlot, 0, SlotActionType.PICKUP, mc.player);
            }
            if (wasSprinting && mc.player.networkHandler != null) {
                mc.player.networkHandler.sendPacket(
                    new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
            }
            if (swapRender.getValue()) {
                chat("§aAutoSwap §7— свапнул на §f" + bypassItemName);
            }
            bypassSwapped = true;
            swapStartMs = System.currentTimeMillis();
        }
        if (bypassSwapped && System.currentTimeMillis() - swapStartMs >= 35) {
            bypassActive = false;
            bypassSwapped = false;
            bypassSlot = -1;
            disableSelf();
        }
    }

    private void prepareSwap(Item item, String itemName, boolean enchantedTotem) {
        int slot = (item == Items.TOTEM_OF_UNDYING)
                ? findTotem(enchantedTotem)
                : findItem(item);

        if (slot != -1) {
            bypassActive = true;
            bypassSwapped = false;
            bypassSlot = slot;
            bypassItemName = itemName;
            swapStartMs = System.currentTimeMillis();
        } else {
            if (swapRender.getValue()) {
                chat("§cAutoSwap §7— предмет §f" + itemName + " §7не найден.");
            }
            disableSelf();
        }
    }

    private int findItem(Item item) {
        if (mc.player == null) return -1;
        for (int i = 0; i <= 35; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == item) return i;
        }
        return -1;
    }

    private int findTotem(boolean onlyEnch) {
        if (mc.player == null) return -1;
        for (int i = 35; i >= 0; i--) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                if (onlyEnch && stack.getEnchantments().isEmpty()) continue;
                return i;
            }
        }
        return -1;
    }

    private void disableSelf() {
        // выключаем без зацикливания (onDisable не перезапускает swap)
        if (isEnabled()) setEnabled(false);
    }

    @Override
    public void onDisable() {
        bypassActive = false;
        bypassSwapped = false;
        bypassSlot = -1;
    }
}

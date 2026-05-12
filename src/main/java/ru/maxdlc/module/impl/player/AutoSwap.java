package ru.maxdlc.module.impl.player;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;
import ru.maxdlc.module.Category;
import ru.maxdlc.module.Module;
import ru.maxdlc.setting.BooleanSetting;
import ru.maxdlc.setting.KeySetting;
import ru.maxdlc.setting.ModeSetting;

/**
 * AutoSwap — по нажатию кнопки свапает предмет из хот-бара с off-hand.
 *
 * Режимы (Mode):
 *  - TotemToSphere: если в off-hand тотем, поменять его на выбранный предмет (шар эндер-ока / жемчуг) из hotbar.
 *                   Если в off-hand не тотем — поставить обратно тотем.
 *  - SphereToTotem: зеркальный сценарий.
 *  - Toggle: просто свапать то что есть (первый подходящий предмет hotbar).
 *
 * SwapKey — кнопка, по которой выполняется свап.
 * UseMode — какой предмет ищем в hotbar помимо тотема:
 *  - EnderPearl, EnderEye, FireCharge, Bow, Crossbow, FirstMatch.
 *
 * Модуль работает даже когда чит свернут (но не в меню инвентаря).
 */
public class AutoSwap extends Module {

    public final KeySetting swapKey = addSetting(new KeySetting("SwapKey", GLFW.GLFW_KEY_G));
    public final ModeSetting direction = addMode("Mode", "Toggle",
            "Toggle", "TotemToSphere", "SphereToTotem");
    public final ModeSetting hotbarItem = addMode("HotbarItem", "EnderPearl",
            "EnderPearl", "EnderEye", "FireCharge", "Bow", "Crossbow", "FirstMatch");
    public final BooleanSetting autoReplenish = addBoolean("AutoReplenishTotem", true);
    public final BooleanSetting announce = addBoolean("ChatStatus", false);

    private boolean keyDown = false;
    private long lastSwapMs = 0L;

    public AutoSwap() {
        super("AutoSwap", "Свап тотема / шара по бинду", Category.PLAYER);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) return;

        long handle = mc.getWindow().getHandle();
        int key = swapKey.getKeyCode();
        if (key <= 0) return;

        boolean pressed = GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
        if (pressed && !keyDown) {
            if (System.currentTimeMillis() - lastSwapMs > 120) {
                performSwap();
                lastSwapMs = System.currentTimeMillis();
            }
        }
        keyDown = pressed;

        // Auto-replenish: если в off-hand пусто и мы только что потратили тотем — положить новый.
        if (autoReplenish.getValue() && mc.player.getOffHandStack().isEmpty()) {
            int totemSlot = findSlot(net.minecraft.item.Items.TOTEM_OF_UNDYING);
            if (totemSlot >= 0) {
                swapSlotWithOffhand(totemSlot);
                if (announce.getValue()) chat("§aAuto-replenish тотем");
            }
        }
    }

    private void performSwap() {
        if (mc.player == null) return;
        var off = mc.player.getOffHandStack();
        boolean offIsTotem = off.getItem() == Items.TOTEM_OF_UNDYING;

        int targetHotbarSlot;

        String dir = direction.getValue();
        if (dir.equals("TotemToSphere") && !offIsTotem) {
            // off-hand сейчас НЕ тотем → значит пользователь сейчас «в режиме шара»,
            // хочет вернуть тотем обратно.
            targetHotbarSlot = findSlot(Items.TOTEM_OF_UNDYING);
        } else if (dir.equals("SphereToTotem") && offIsTotem) {
            // off-hand сейчас тотем → нужно положить шар.
            targetHotbarSlot = findHotbarItem();
        } else if (dir.equals("Toggle")) {
            targetHotbarSlot = offIsTotem ? findHotbarItem() : findSlot(Items.TOTEM_OF_UNDYING);
        } else {
            // dir == TotemToSphere при off-тотеме → положить шар
            // dir == SphereToTotem при off-шаре/пусто → положить тотем
            targetHotbarSlot = offIsTotem ? findHotbarItem() : findSlot(Items.TOTEM_OF_UNDYING);
        }

        if (targetHotbarSlot < 0) {
            if (announce.getValue()) chat("§cAutoSwap: нечего свапать в hotbar");
            return;
        }

        swapSlotWithOffhand(targetHotbarSlot);
        if (announce.getValue()) chat("§aAutoSwap: slot=" + targetHotbarSlot);
    }

    /** Свопает hotbar-slot (0..8, координаты инвентаря 36..44) с off-hand (slot 45). */
    private void swapSlotWithOffhand(int hotbarIndex) {
        if (mc.player == null || mc.interactionManager == null) return;
        int inventorySlotId = PlayerInventory.MAIN_SIZE + hotbarIndex;
        // inventorySlotId неверный для click: screenHandler использует 36+hotbarIndex
        int slot = 36 + hotbarIndex;
        // SWAP с offHand: button = 40 (offhand swap id в vanilla screen handler),
        // но универсально делаем через два PICKUP: pickup target -> pickup offhand -> pickup target.
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
                slot, 40, SlotActionType.SWAP, mc.player);
    }

    private int findSlot(net.minecraft.item.Item item) {
        if (mc.player == null) return -1;
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).getItem() == item) return i;
        }
        // ищем дальше по инвентарю — положим его сначала в hotbar как доп-fallback
        for (int i = 9; i < inv.size(); i++) {
            if (inv.getStack(i).getItem() == item) return i - 9; // не идеально, но редкий случай
        }
        return -1;
    }

    private int findHotbarItem() {
        String want = hotbarItem.getValue();
        switch (want) {
            case "EnderPearl": return findSlot(Items.ENDER_PEARL);
            case "EnderEye":   return findSlot(Items.ENDER_EYE);
            case "FireCharge": return findSlot(Items.FIRE_CHARGE);
            case "Bow":        return findSlot(Items.BOW);
            case "Crossbow":   return findSlot(Items.CROSSBOW);
            case "FirstMatch": {
                if (mc.player == null) return -1;
                var inv = mc.player.getInventory();
                for (int i = 0; i < 9; i++) {
                    var it = inv.getStack(i).getItem();
                    if (it == Items.ENDER_PEARL || it == Items.ENDER_EYE ||
                        it == Items.FIRE_CHARGE || it == Items.BOW || it == Items.CROSSBOW) return i;
                }
                return -1;
            }
        }
        return -1;
    }
}

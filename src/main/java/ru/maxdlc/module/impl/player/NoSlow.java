package ru.maxdlc.module.impl.player;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import ru.maxdlc.module.Category;
import ru.maxdlc.module.Module;
import ru.maxdlc.setting.BooleanSetting;
import ru.maxdlc.setting.ModeSetting;

/**
 * NoSlow — отключает замедление при использовании еды / зелий.
 *
 *  Mode:
 *    - Vanilla     : принудительно setSprinting(true) во время isUsingItem() — работает
 *                    локально на single-player / на честных серверах; на FunTime ловится.
 *    - FuntimeNew  : при использовании consumable предмета (еда/зелье/молоко/мёд)
 *                    в main-hand — временно свапает в off-hand арбалет из инвентаря.
 *                    Сервер видит, что игрок «натягивает арбалет» в offhand, а основной
 *                    слот с едой не считается use-slot, замедления нет. После отмены
 *                    использования арбалет свапается обратно.
 *    - Spookytime  : как FuntimeNew, но с меньшим cooldown между свапами
 *                    (минимизирует rubber-banding при быстрых последовательных use).
 */
public class NoSlow extends Module {

    public final ModeSetting mode = addMode("Mode", "FuntimeNew", "FuntimeNew", "Spookytime", "Vanilla");
    public final BooleanSetting onlyGround = addBoolean("OnlyGround", true);
    public final BooleanSetting announce = addBoolean("ChatStatus", false);

    private boolean swapped = false;
    private int originalCrossbowSlot = -1;
    private long lastSwapMs = 0L;

    public NoSlow() {
        super("NoSlow", "Без замедления при использовании предметов", Category.PLAYER);
    }

    @Override
    public void onDisable() {
        restoreCrossbow();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) return;

        if (mode.is("Vanilla")) {
            tickVanilla();
            return;
        }

        // FuntimeNew / Spookytime — общий crossbow-swap bypass
        tickCrossbowSwap();
    }

    /** Простой клиентский setSprinting — работает там, где нет анти-чита. */
    private void tickVanilla() {
        if (!mc.player.isUsingItem()) return;
        if (onlyGround.getValue() && !mc.player.isOnGround()) return;
        boolean canSprint = mc.player.getHungerManager().getFoodLevel() > 6;
        if (!canSprint) return;
        mc.player.setSprinting(true);
    }

    /** FunTime bypass: свап crossbow -> offhand на время использования consumable. */
    private void tickCrossbowSwap() {
        boolean using = mc.player.isUsingItem();
        boolean handOk = mc.player.getActiveHand() == Hand.MAIN_HAND;
        ItemStack active = mc.player.getActiveItem();
        boolean consumable = using && handOk && isConsumable(active);

        if (consumable) {
            if (!swapped) {
                if (mc.player.getOffHandStack().isOf(Items.CROSSBOW)) return;
                int cbSlot = findCrossbowSlot();
                if (cbSlot < 0) return;
                long now = System.currentTimeMillis();
                long cooldown = mode.is("Spookytime") ? 50 : 120;
                if (now - lastSwapMs < cooldown) return;

                originalCrossbowSlot = cbSlot;
                swapSlotWithOffhand(cbSlot);
                swapped = true;
                lastSwapMs = now;
                if (announce.getValue()) chat("§aNoSlow: offhand = crossbow");
            }
        } else {
            restoreCrossbow();
        }
    }

    private void restoreCrossbow() {
        if (!swapped) return;
        if (mc.player == null || mc.interactionManager == null) {
            swapped = false;
            originalCrossbowSlot = -1;
            return;
        }
        if (originalCrossbowSlot >= 0) {
            swapSlotWithOffhand(originalCrossbowSlot);
            if (announce.getValue()) chat("§7NoSlow: crossbow вернут");
        }
        swapped = false;
        originalCrossbowSlot = -1;
    }

    /** Ищет слот с арбалетом в hotbar (0..8). Вернёт -1 если нет. */
    private int findCrossbowSlot() {
        if (mc.player == null) return -1;
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isOf(Items.CROSSBOW)) return i;
        }
        return -1;
    }

    /** Свап hotbar-слот (0..8) <-> offhand через vanilla SWAP. */
    private void swapSlotWithOffhand(int hotbarIndex) {
        if (mc.player == null || mc.interactionManager == null) return;
        int slot = 36 + hotbarIndex;
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
                slot, 40, SlotActionType.SWAP, mc.player);
    }

    private static boolean isConsumable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.contains(DataComponentTypes.FOOD)) return true;
        return stack.isOf(Items.POTION)
                || stack.isOf(Items.MILK_BUCKET)
                || stack.isOf(Items.HONEY_BOTTLE);
    }
}

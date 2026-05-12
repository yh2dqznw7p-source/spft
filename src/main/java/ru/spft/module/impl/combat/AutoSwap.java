package ru.spft.module.impl.combat;

@Oblamovvv(name = "AutoSwap", category = Category.COMBAT, desc = "крутой авто свап!")
public final class AutoSwap extends Module {

    public final ModeSetting firstItemSetting = new ModeSetting("Первый предмет", "Шар", "Золотое яблоко", "Щит", "Шар", "Тотем");
    public final ModeSetting secondItemSetting = new ModeSetting("Второй предмет", "Тотем 2", "Золотое яблоко 2", "Щит 2", "Шар 2", "Тотем 2");
    public final KeySetting bind = new KeySetting("Кнопка", -1);
    public final BooleanSetting swaprender = new BooleanSetting("Показ свапа", true);
    public final BooleanSetting onlyEnchanted = new BooleanSetting("Только Чар. тотемы", false);

    private boolean isFirstItem = true;
    private boolean triggerSwap;

    private final StopWatch swapWatch = new StopWatch();
    private final StopWatch swapWatchK = new StopWatch();

    private boolean bypassActive;
    private boolean bypassSwapped;
    private int bypassSlot = -1;
    private String bypassItemName = "";

    private AutoSwap() {
    }

    @EventTarget
    public void onUpdate(EventUpdate event) {
        if (mc.player == null) return;

        ScreenHandler screenHandler = mc.player.playerScreenHandler;
        //супер байпас
        if (bypassActive) {
            if (!bypassSwapped && bypassSlot != -1) {
                int syncSlot = bypassSlot < 9 ? bypassSlot + 36 : bypassSlot;

                boolean wasSprinting = mc.player.isSprinting();
                if (wasSprinting) {
                    mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                }
                mc.interactionManager.clickSlot(screenHandler.syncId, syncSlot, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(screenHandler.syncId, 45, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(screenHandler.syncId, syncSlot, 0, SlotActionType.PICKUP, mc.player);

                if (wasSprinting) {
                    mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
                }

                if (swaprender.get()) {
                    Exec.getInstance().getNotificationManager().newNotification("Swap", Text.literal("AutoSwap - свапнул на " + bypassItemName));
                }
                bypassSwapped = true;
                swapWatch.reset();
            }
            if (bypassSwapped && swapWatch.getElapsedTime() >= 35) {
                MovementManager.getInstance().unlockMovement("AutoSwap");
                bypassActive = false;
                bypassSwapped = false;
                bypassSlot = -1;
            }
            return;
        }

        if (this.triggerSwap) {
            String selectedMode = isFirstItem ? firstItemSetting.get() : secondItemSetting.get();
            String modeBase = selectedMode.replace(" 2", "");

            switch (modeBase) {
                case "Шар" -> this.prepareSwap(Items.PLAYER_HEAD, "Шар", false);
                case "Тотем" -> this.prepareSwap(Items.TOTEM_OF_UNDYING, "Тотем", onlyEnchanted.get());
                case "Золотое яблоко" -> this.prepareSwap(Items.GOLDEN_APPLE, "Золотое яблоко", false);
                case "Щит" -> this.prepareSwap(Items.SHIELD, "Щит", false);
            }

            this.isFirstItem = !this.isFirstItem;
            this.triggerSwap = false;
        }
    }

    @EventTarget
    public void input(EventKey event) {
        if (mc.currentScreen == null && swapWatchK.getElapsedTime() >= 300) {
            if (event.isKeyDown(bind.getKeyCode())) {
                this.triggerSwap = true;
                swapWatchK.reset();
            }
        }
    }

    private void prepareSwap(Item item, String itemName, boolean enchTotem) {
        int slot = item == Items.TOTEM_OF_UNDYING ? findTotem(enchTotem) : InventoryUtil.findItem(item, 0, 35);

        if (slot != -1) {
            MovementManager.getInstance().lockMovement("AutoSwap");
            bypassActive = true;
            bypassSwapped = false;
            bypassSlot = slot;
            bypassItemName = itemName;
            swapWatch.reset();
        } else {
            if (swaprender.get()) {
                Exec.getInstance().getNotificationManager().newNotification("Swap", Text.literal("Предмет " + itemName + " не найден!"));
            }
        }
    }

    private int findTotem(boolean бабка) {
        for (int i = 35; i >= 0; i--) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                if (бабка && !stack.hasEnchantments()) continue;
                return i;
            }
        }
        return -1;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        MovementManager.getInstance().unlockMovement("AutoSwap");
        bypassActive = false;
        bypassSwapped = false;
        bypassSlot = -1;
        isFirstItem = true;
    }
}

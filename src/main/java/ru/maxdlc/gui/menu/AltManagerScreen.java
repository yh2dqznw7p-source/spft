package ru.maxdlc.gui.menu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * AltManager: offline-аккаунты, выбор / удаление / random-ник.
 * Порт Levin/ru.levin.screens.altmanager.AltManager на нативный DrawContext, без Levin-fonts и Scissor.
 */
public class AltManagerScreen extends Screen {
    private final Screen parent;
    private final List<String> accounts = AccountStore.getAccounts();

    private boolean isTyping = false;
    private final StringBuilder inputText = new StringBuilder();

    private float scrollOffset = 0;
    private float targetScroll = 0;
    private float hoverInput = 0;
    private float[] hoverSelect = new float[0];
    private float[] hoverDelete = new float[0];
    private int selectedIndex = -1;

    private static final float SCALE = 1.5f;
    private static final int MAX_NAME = 16;

    private float createHover = 0, clearHover = 0, randomHover = 0;
    private float createScale = 1, clearScale = 1, randomScale = 1;

    private int shakeTime = 0;
    private float shakeY = 0;
    private boolean showConfirmDialog = false;

    private final String title = "AltManager";

    public AltManagerScreen(Screen parent) {
        super(Text.literal("Max DLC AltManager"));
        this.parent = parent;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        scrollOffset = MenuUI.lerp(scrollOffset, targetScroll, 0.35f);

        MenuUI.animatedBackground(ctx, this.width, this.height);

        if (shakeTime > 0) {
            shakeTime--;
            shakeY = (float) (Math.sin(shakeTime * 0.5) * 3);
        } else shakeY = 0;

        TextRenderer tr = this.textRenderer;

        // Заголовок
        float titleScale = 3.0f;
        int titleWidth = (int) (tr.getWidth(title) * titleScale);
        float titleX = (this.width - titleWidth) / 2f;
        float titleY = this.height / 7f + shakeY;
        int titleColor = MenuUI.animatedGradient(MenuUI.argb(255, 180, 180, 255),
                MenuUI.argb(255, 140, 140, 255),
                (System.currentTimeMillis() % 4000L) / 1500f);

        ctx.getMatrices().push();
        ctx.getMatrices().translate(titleX, titleY, 0);
        ctx.getMatrices().scale(titleScale, titleScale, 1);
        ctx.drawTextWithShadow(tr, title, 0, 0, titleColor);
        ctx.getMatrices().pop();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // --- input ---
        int inputWidth = (int) (220 * SCALE);
        int inputHeight = (int) (17 * SCALE);
        int inputX = centerX - (int) (110 * SCALE);
        int inputY = centerY - (int) (92 * SCALE);

        boolean overInput = inRegion(mouseX, mouseY, inputX, inputY, inputWidth, inputHeight);
        hoverInput = MenuUI.lerp(hoverInput, overInput ? 1 : 0, 0.3f);
        int nameColor = MenuUI.lerpColor(MenuUI.argb(180, 180, 180, 255),
                MenuUI.argb(230, 230, 230, 255), hoverInput);

        MenuUI.roundedRect(ctx, inputX, inputY, inputWidth, inputHeight, 4,
                MenuUI.argb(25, 25, 25, 120));

        String textToShow;
        if (!isTyping) {
            StringBuilder ph = new StringBuilder("Enter your name");
            for (int i = 0; i < (System.currentTimeMillis() / 500 % 4); i++) ph.append(".");
            textToShow = ph.toString();
        } else {
            textToShow = inputText + ((System.currentTimeMillis() / 500 % 2) == 0 ? "_" : "");
        }
        ctx.drawText(tr, textToShow, inputX + 6, inputY + inputHeight / 2 - 4, nameColor, false);

        // --- list ---
        int listX = inputX;
        int listY = centerY - (int) (70 * SCALE);
        int listWidth = (int) (220 * SCALE);
        int listHeight = (int) (140 * SCALE);

        MenuUI.roundedRect(ctx, listX, listY, listWidth, listHeight, 4, MenuUI.argb(25, 25, 25, 120));

        if (hoverSelect.length != accounts.size()) {
            hoverSelect = new float[accounts.size()];
            hoverDelete = new float[accounts.size()];
        }

        ctx.enableScissor(listX, listY, listX + listWidth, listY + listHeight);

        float startY = listY + 5;
        float itemHeight = 35 * SCALE;

        int entryX = centerX - (int) (105 * SCALE);
        int entryWidth = (int) (140 * SCALE);
        int entryHeight = (int) (30 * SCALE);

        int btnWidth = (int) (60 * SCALE);
        int btnHeight = (int) (13 * SCALE);

        for (int i = 0; i < accounts.size(); i++) {
            int y = (int) (startY - scrollOffset + i * itemHeight);

            int bgColor = (i == selectedIndex) ? MenuUI.argb(50, 50, 80, 150) : MenuUI.argb(23, 23, 23, 100);
            MenuUI.roundedRect(ctx, entryX, y, entryWidth + 10, entryHeight, 4, bgColor);

            ctx.drawText(tr, accounts.get(i), entryX + 10, y + 5, MenuUI.argb(200, 200, 200, 255), false);
            String dateText = "Date " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            ctx.drawText(tr, dateText, entryX + 10, y + 18, MenuUI.argb(140, 140, 140, 255), false);

            int selBtnX = entryX + entryWidth + (int) (10 * SCALE);
            boolean overSel = inRegion(mouseX, mouseY, selBtnX, y, btnWidth, btnHeight);
            hoverSelect[i] = MenuUI.lerp(hoverSelect[i], overSel ? 1 : 0, 0.35f);
            int selBg = MenuUI.lerpColor(MenuUI.argb(25, 25, 25, 120),
                    MenuUI.argb(40, 40, 40, 160), hoverSelect[i]);
            MenuUI.roundedRect(ctx, selBtnX, y, btnWidth, btnHeight + 2, 4, selBg);
            drawCenteredText(ctx, tr, "Select",
                    selBtnX + btnWidth / 2f, y + btnHeight / 2f - 3, 0xFFFFFFFF);

            int delBtnY = y + btnHeight + (int) (3 * SCALE);
            boolean overDel = inRegion(mouseX, mouseY, selBtnX, delBtnY, btnWidth, btnHeight);
            hoverDelete[i] = MenuUI.lerp(hoverDelete[i], overDel ? 1 : 0, 0.35f);
            int delBg = MenuUI.lerpColor(MenuUI.argb(25, 25, 25, 120),
                    MenuUI.argb(40, 40, 40, 160), hoverDelete[i]);
            MenuUI.roundedRect(ctx, selBtnX, delBtnY, btnWidth, btnHeight + 2, 4, delBg);
            drawCenteredText(ctx, tr, "Delete",
                    selBtnX + btnWidth / 2f, delBtnY + btnHeight / 2f - 3, 0xFFFFFFFF);
        }

        ctx.disableScissor();

        // --- bottom buttons ---
        int buttonsY = listY + listHeight + (int) (10 * SCALE);
        int buttonWidth = (int) (70 * SCALE);
        int buttonH = inputHeight;
        int createX = centerX - buttonWidth - (int) (40 * SCALE);
        int clearX = centerX - (buttonWidth / 2);
        int randomX = centerX + buttonWidth + (int) (-30 * SCALE);

        createHover = bottomBtn(ctx, tr, "Create", mouseX, mouseY,
                createX, buttonsY, buttonWidth, buttonH, createHover);
        clearHover = bottomBtn(ctx, tr, "Clear all", mouseX, mouseY,
                clearX, buttonsY, buttonWidth, buttonH, clearHover);
        randomHover = bottomBtn(ctx, tr, "Random", mouseX, mouseY,
                randomX, buttonsY, buttonWidth, buttonH, randomHover);

        String accName = MinecraftClient.getInstance().getSession().getUsername();
        drawCenteredText(ctx, tr, "Selected account: " + accName,
                centerX, buttonsY + buttonH + (int) (20 * SCALE), 0xFFFFFFFF);
        drawCenteredText(ctx, tr, "Quantity: " + accounts.size(),
                centerX, buttonsY + buttonH + (int) (40 * SCALE), 0xFFFFFFFF);

        if (showConfirmDialog) {
            drawConfirmDialog(ctx, tr);
        }
    }

    private float bottomBtn(DrawContext ctx, TextRenderer tr, String label,
                            int mx, int my, int x, int y, int w, int h, float anim) {
        boolean over = inRegion(mx, my, x, y, w, h);
        anim = MenuUI.lerp(anim, over ? 1 : 0, 0.3f);
        int bg = MenuUI.lerpColor(MenuUI.argb(25, 25, 25, 120),
                MenuUI.argb(40, 40, 40, 160), anim);
        MenuUI.roundedRect(ctx, x, y, w, h, 4, bg);
        drawCenteredText(ctx, tr, label, x + w / 2f, y + h / 2f - 4, 0xFFFFFFFF);
        return anim;
    }

    private void drawConfirmDialog(DrawContext ctx, TextRenderer tr) {
        int boxW = 300, boxH = 130;
        int boxX = (width - boxW) / 2, boxY = (height - boxH) / 2;
        ctx.fill(0, 0, width, height, MenuUI.argb(0, 0, 0, 120));
        MenuUI.roundedRect(ctx, boxX, boxY, boxW, boxH, 6, MenuUI.argb(40, 40, 40, 240));

        drawCenteredText(ctx, tr, "Clear all accounts?", width / 2f, boxY + 30, 0xFFFFFFFF);

        int btnW = 90, btnH = 28;
        int yesX = boxX + 35;
        int noX = boxX + boxW - 35 - btnW;
        int btnY = boxY + boxH - 50;

        MenuUI.roundedRect(ctx, yesX, btnY, btnW, btnH, 5, MenuUI.argb(60, 180, 75, 255));
        drawCenteredText(ctx, tr, "Yes", yesX + btnW / 2f, btnY + btnH / 2f - 4, 0xFFFFFFFF);
        MenuUI.roundedRect(ctx, noX, btnY, btnW, btnH, 5, MenuUI.argb(200, 60, 60, 255));
        drawCenteredText(ctx, tr, "No", noX + btnW / 2f, btnY + btnH / 2f - 4, 0xFFFFFFFF);
    }

    private static void drawCenteredText(DrawContext ctx, TextRenderer tr, String text,
                                         float cx, float cy, int color) {
        int w = tr.getWidth(text);
        ctx.drawText(tr, text, (int) (cx - w / 2f), (int) cy, color, false);
    }

    private static boolean inRegion(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int centerX = width / 2;
        int centerY = height / 2;

        int inputWidth = (int) (220 * SCALE);
        int inputHeight = (int) (17 * SCALE);
        int inputX = centerX - (int) (110 * SCALE);
        int inputY = centerY - (int) (92 * SCALE);

        int buttonWidth = (int) (70 * SCALE);
        int listY = centerY - (int) (70 * SCALE);
        int listHeight = (int) (140 * SCALE);
        int buttonsY = listY + listHeight + (int) (10 * SCALE);
        int createX = centerX - buttonWidth - (int) (40 * SCALE);
        int clearX = centerX - (buttonWidth / 2);
        int randomX = centerX + buttonWidth + (int) (-30 * SCALE);

        if (showConfirmDialog) {
            int boxW = 300, boxH = 130;
            int boxX = (width - boxW) / 2, boxY = (height - boxH) / 2;
            int btnW = 90, btnH = 28;
            int yesX = boxX + 35;
            int noX = boxX + boxW - 35 - btnW;
            int btnY = boxY + boxH - 50;

            if (inRegion(mouseX, mouseY, yesX, btnY, btnW, btnH)) {
                AccountStore.clearAll();
                selectedIndex = -1;
                showConfirmDialog = false;
                return true;
            }
            if (inRegion(mouseX, mouseY, noX, btnY, btnW, btnH)) {
                showConfirmDialog = false;
                return true;
            }
            return true;
        }

        if (inRegion(mouseX, mouseY, inputX, inputY, inputWidth, inputHeight)) {
            isTyping = true;
            return true;
        } else if (isTyping) {
            isTyping = false;
        }

        if (inRegion(mouseX, mouseY, createX, buttonsY, buttonWidth, inputHeight) && button == 0) {
            String newAccount = inputText.toString().trim();
            if (!newAccount.isEmpty()) {
                AccountStore.add(newAccount);
                inputText.setLength(0);
                isTyping = false;
            }
            return true;
        }

        if (inRegion(mouseX, mouseY, clearX, buttonsY, buttonWidth, inputHeight) && button == 0) {
            showConfirmDialog = true;
            return true;
        }

        if (inRegion(mouseX, mouseY, randomX, buttonsY, buttonWidth, inputHeight) && button == 0) {
            String rnd = generateRandomName();
            AccountStore.add(rnd);
            AccountStore.loginOffline(rnd);
            selectedIndex = accounts.indexOf(rnd);
            return true;
        }

        // accounts list
        int listX = inputX;
        int listWidth = (int) (220 * SCALE);
        if (inRegion(mouseX, mouseY, listX, listY, listWidth, listHeight)) {
            float startY = listY + 5;
            float itemHeight = 35 * SCALE;
            int entryX = centerX - (int) (105 * SCALE);
            int entryWidth = (int) (140 * SCALE);
            int entryHeight = (int) (30 * SCALE);
            int btnWidth = (int) (60 * SCALE);
            int btnHeight = (int) (13 * SCALE);

            for (int i = 0; i < accounts.size(); i++) {
                int y = (int) (startY - scrollOffset + i * itemHeight);

                int selBtnX = entryX + entryWidth + (int) (10 * SCALE);
                if (inRegion(mouseX, mouseY, selBtnX, y, btnWidth, btnHeight) && button == 0) {
                    String name = accounts.get(i);
                    AccountStore.loginOffline(name);
                    selectedIndex = i;
                    return true;
                }
                int delBtnY = y + btnHeight + (int) (3 * SCALE);
                if (inRegion(mouseX, mouseY, selBtnX, delBtnY, btnWidth, btnHeight) && button == 0) {
                    String name = accounts.get(i);
                    if (selectedIndex == i) selectedIndex = -1;
                    AccountStore.remove(name);
                    return true;
                }
                if (inRegion(mouseX, mouseY, entryX, y, entryWidth + 10, entryHeight) && button == 0) {
                    String name = accounts.get(i);
                    AccountStore.loginOffline(name);
                    selectedIndex = i;
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int centerY = height / 2;
        int listY = centerY - (int) (70 * SCALE);
        int listHeight = (int) (140 * SCALE);
        if (mouseY >= listY && mouseY <= listY + listHeight) {
            targetScroll -= vertical * (int) (30 * SCALE);
            int maxOffset = Math.max(0, (accounts.size() * (int) (36 * SCALE)) - listHeight);
            targetScroll = Math.max(0, Math.min(targetScroll, maxOffset));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isTyping) {
            boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
            if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
                String clip = GLFW.glfwGetClipboardString(MinecraftClient.getInstance().getWindow().getHandle());
                if (clip != null) {
                    String filtered = clip.replaceAll("[^A-Za-z0-9_]", "");
                    int maxLen = MAX_NAME - inputText.length();
                    if (maxLen > 0) {
                        if (filtered.length() > maxLen) filtered = filtered.substring(0, maxLen);
                        inputText.append(filtered);
                    }
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                String n = inputText.toString().trim();
                if (!n.isEmpty()) {
                    AccountStore.add(n);
                    inputText.setLength(0);
                    isTyping = false;
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && inputText.length() > 0) {
                inputText.deleteCharAt(inputText.length() - 1);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (isTyping) {
            if (chr == '\n' || chr == '\r') return false;
            if (inputText.length() < MAX_NAME && (Character.isLetterOrDigit(chr) || chr == '_')) {
                inputText.append(chr);
                return true;
            }
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public void close() {
        if (parent != null) MinecraftClient.getInstance().setScreen(parent);
        else super.close();
    }

    private static String generateRandomName() {
        Random rand = ThreadLocalRandom.current();
        String[] words = {
                "Alex", "Iq", "Termo", "Al", "Silent", "Cat", "Lone", "Pro", "Meow", "Gator",
                "Ninja", "Shadow", "Fire", "Ice", "Dragon", "Wolf", "Eagle", "Storm", "Blade", "Ghost",
                "Pixel", "Neo", "Cyber", "Volt", "Echo", "Falcon", "Hawk", "Jaguar", "Knight", "Legend",
                "Mystic", "Nova", "Orbit", "Phantom", "Quest", "Ranger", "Spark", "Titan", "Ultra", "Vortex",
                "Warrior", "Xenon", "Yeti", "Zenith", "Alpha", "Beta", "Gamma", "Delta", "Epsilon"
        };
        int numWords = rand.nextInt(2) + 1;
        StringBuilder name = new StringBuilder();
        for (int w = 0; w < numWords; w++) {
            String word = words[rand.nextInt(words.length)];
            if (rand.nextBoolean()) word = word.toLowerCase();
            if (rand.nextDouble() < 0.3 && word.length() > 2) {
                int ins = rand.nextInt(word.length() - 2) + 1;
                word = word.substring(0, ins) + rand.nextInt(10) + word.substring(ins + 1);
            }
            name.append(word);
            if (w < numWords - 1 && rand.nextInt(3) != 2) {
                name.append(rand.nextBoolean() ? "_" : "__");
            }
        }
        if (rand.nextDouble() < 0.7) {
            int n = rand.nextInt(4) + 1;
            for (int d = 0; d < n; d++) name.append(rand.nextInt(10));
        }
        String s = name.toString();
        if (s.length() > MAX_NAME) s = s.substring(0, MAX_NAME);
        if (s.isEmpty()) s = "Player" + rand.nextInt(1000);
        return s;
    }
}

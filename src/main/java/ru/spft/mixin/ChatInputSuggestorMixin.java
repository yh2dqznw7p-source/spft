package ru.spft.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.spft.SpftClient;
import ru.spft.command.Command;

import java.util.ArrayList;
import java.util.List;

/**
 * Когда игрок открывает чат и начинает писать с префикса ".", мы рисуем
 * собственный автокомплит-дропдаун над полем ввода — как у ванильного
 * суггестора для "/". Реализовано миксином на {@link ChatScreen#render}.
 *
 * Мы не трогаем внутренности ChatInputSuggestor (его структуры мэппингов
 * меняются от версии к версии) — просто читаем текущий текст из текстового
 * поля через рефлексию и рисуем список подходящих команд своим DrawContext.
 */
@Mixin(ChatScreen.class)
public abstract class ChatInputSuggestorMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void spft$renderDotSuggestions(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (SpftClient.get() == null) return;
        ChatScreen self = (ChatScreen) (Object) this;
        TextFieldWidget field = findChatField(self);
        if (field == null) return;
        String text = field.getText();
        if (text == null || !text.startsWith(".")) return;

        String firstToken = text.contains(" ") ? text.substring(1, text.indexOf(' ')) : text.substring(1);
        String typed = firstToken.toLowerCase();

        List<String> matches = new ArrayList<>();
        for (Command c : SpftClient.get().getCommandManager().getCommands()) {
            if (c.getName().toLowerCase().startsWith(typed)) {
                matches.add("§f." + c.getName() + " §8- §7" + c.getDescription());
            }
            for (String a : c.getAliases()) {
                if (a.toLowerCase().startsWith(typed))
                    matches.add("§f." + a + " §8(" + c.getName() + ")");
            }
        }
        if (matches.isEmpty()) return;

        var tr = net.minecraft.client.MinecraftClient.getInstance().textRenderer;

        int fieldX = field.getX();
        int fieldY = field.getY();
        int lineH = tr.fontHeight + 2;
        int totalH = matches.size() * lineH + 4;
        int width = 0;
        for (String m : matches) width = Math.max(width, tr.getWidth(m) + 8);

        int y0 = fieldY - totalH - 2;
        // фон
        ctx.fill(fieldX, y0, fieldX + width, fieldY - 2, 0xCC000000);
        // рамка
        int col = 0xFFFF5555;
        ctx.fill(fieldX,            y0,            fieldX + width, y0 + 1,       col);
        ctx.fill(fieldX,            fieldY - 3,    fieldX + width, fieldY - 2,   col);
        ctx.fill(fieldX,            y0,            fieldX + 1,     fieldY - 2,   col);
        ctx.fill(fieldX + width - 1,y0,            fieldX + width, fieldY - 2,   col);

        int yy = y0 + 2;
        for (String m : matches) {
            ctx.drawTextWithShadow(tr, m, fieldX + 4, yy, 0xFFFFFFFF);
            yy += lineH;
        }
    }

    /** ChatScreen держит приватное поле chatField (TextFieldWidget). Находим его рефлексией. */
    private static TextFieldWidget findChatField(ChatScreen screen) {
        try {
            for (var f : ChatScreen.class.getDeclaredFields()) {
                if (TextFieldWidget.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    return (TextFieldWidget) f.get(screen);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }
}

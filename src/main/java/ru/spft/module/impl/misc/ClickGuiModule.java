package ru.spft.module.impl.misc;

import ru.spft.module.Category;
import ru.spft.module.Module;

/**
 * Фиктивный модуль, нужен только для отображения статуса GUI.
 * Фактическое открытие окна — по RIGHT_SHIFT в SpftClient.onClientTick.
 */
public class ClickGuiModule extends Module {
    public ClickGuiModule() {
        super("ClickGUI", "Главное окно настроек (RIGHT_SHIFT)", Category.MISC);
        // RIGHT_SHIFT перехватывается напрямую в SpftClient.onClientTick,
        // поэтому собственный keybind не задаём — иначе BindManager дважды тоглил бы модуль.
    }

    @Override
    public void onEnable() {
        // включение = показываем, что открыт
    }
}

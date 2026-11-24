package dev.growops;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class Text {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {
    }

    public static Component c(String s) {
        return MM.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }
}

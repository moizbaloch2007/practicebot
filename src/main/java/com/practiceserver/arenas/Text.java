package com.practiceserver.arenas;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

final class Text {
    private Text() {}

    static Component c(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s)
                .decoration(TextDecoration.ITALIC, false);
    }

    static void send(CommandSender to, String s) {
        to.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(s));
    }
}

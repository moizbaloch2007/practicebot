package com.practiceserver.arenas;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

/**
 * Removes a player's PracticeBot bots when their fight ends, so an old bot can never
 * attack the same player again in a later session.
 * It lists the player's bots with PracticeBot's own command and despawns each id.
 */
final class BotCleaner {

    private static final Pattern ID_WORD = Pattern.compile("(?i)\\bid\\b\\s*[:=#]?\\s*(\\d{1,7})");
    private static final Pattern HASH = Pattern.compile("#(\\d{1,7})\\b");
    private static final Pattern LEADING = Pattern.compile("^\\s*[-*>\\s]*(\\d{1,7})[.:)\\s]");

    private final ArenasPlugin plugin;

    BotCleaner(ArenasPlugin plugin) {
        this.plugin = plugin;
    }

    void cleanup(String playerName) {
        if (!plugin.getConfig().getBoolean("bot-cleanup.enabled", true)) return;
        boolean debug = plugin.getConfig().getBoolean("debug", false);

        String listCmd = plugin.getConfig().getString("bot-cleanup.list-command", "pbadmin bot list --target {player}");
        String killCmd = plugin.getConfig().getString("bot-cleanup.despawn-command", "pbadmin bot despawn {id}");

        List<String> lines = new ArrayList<>();
        CommandSender capture = Bukkit.createCommandSender(c ->
                lines.add(PlainTextComponentSerializer.plainText().serialize(c)));
        try {
            Bukkit.dispatchCommand(capture, listCmd.replace("{player}", playerName));
        } catch (Exception e) {
            plugin.getLogger().warning("Bot cleanup list failed: " + e.getMessage());
            return;
        }

        Set<String> ids = new LinkedHashSet<>();
        for (String line : lines) {
            if (debug) plugin.getLogger().info("[cleanup] " + line);
            if (line.toLowerCase().contains("page")) continue;
            Matcher m = ID_WORD.matcher(line);
            boolean found = false;
            while (m.find()) { ids.add(m.group(1)); found = true; }
            if (found) continue;
            m = HASH.matcher(line);
            while (m.find()) { ids.add(m.group(1)); found = true; }
            if (found) continue;
            m = LEADING.matcher(line);
            if (m.find()) ids.add(m.group(1));
        }

        for (String id : ids) {
            String cmd = killCmd.replace("{id}", id).replace("{player}", playerName);
            if (debug) plugin.getLogger().info("[cleanup] running: " + cmd);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
        if (debug && ids.isEmpty()) {
            plugin.getLogger().info("[cleanup] no bot ids found for " + playerName
                    + " (lines captured: " + lines.size() + ")");
        }
    }
}

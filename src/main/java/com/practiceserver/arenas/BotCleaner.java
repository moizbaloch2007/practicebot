package com.practiceserver.arenas;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Removes PracticeBot bots at once when a fight ends, so an old bot can never attack a player again.
 * It talks to PracticeBot's own API (through reflection, so PracticeCore needs no PracticeBot jar to build):
 * every active bot that is owned by, or targets, the player is despawned immediately.
 * If the API is not reachable it falls back to PracticeBot's list/despawn commands.
 * A sweep also removes bots that belong to players who are not in a practice session.
 */
final class BotCleaner {

    private static final Pattern ID_WORD = Pattern.compile("(?i)\\bid\\b\\s*[:=#]?\\s*(\\d{1,7})");
    private static final Pattern HASH = Pattern.compile("#(\\d{1,7})\\b");
    private static final Pattern LEADING = Pattern.compile("^\\s*[-*>\\s]*(\\d{1,7})[.:)\\s]");

    private final ArenasPlugin plugin;

    BotCleaner(ArenasPlugin plugin) {
        this.plugin = plugin;
        startSweep();
    }

    private boolean debug() {
        return plugin.getConfig().getBoolean("debug", false);
    }

    // ------------------------------------------------------------ API access

    private static final class Api {
        Object api;
        Method getActiveBots;
        Method getState;
        Method despawn;
        Method owner;
        Method target;
        Method targetEntity;
    }

    private Api api() throws Exception {
        Plugin pb = Bukkit.getPluginManager().getPlugin("PracticeBot");
        if (pb == null || !pb.isEnabled()) return null;
        ClassLoader cl = pb.getClass().getClassLoader();
        Api a = new Api();
        Class<?> provider = Class.forName("com.sheldera.practicebot.api.PracticeBotApiProvider", true, cl);
        a.api = provider.getMethod("get").invoke(null);
        Class<?> apiClass = Class.forName("com.sheldera.practicebot.api.PracticeBotApi", true, cl);
        Class<?> handle = Class.forName("com.sheldera.practicebot.api.bot.PracticeBotHandle", true, cl);
        Class<?> state = Class.forName("com.sheldera.practicebot.api.bot.PracticeBotStateSnapshot", true, cl);
        Class<?> targetSnap = Class.forName("com.sheldera.practicebot.api.bot.PracticeBotTargetSnapshot", true, cl);
        a.getActiveBots = apiClass.getMethod("getActiveBots");
        a.getState = handle.getMethod("getStateSnapshot");
        a.despawn = handle.getMethod("despawn");
        a.owner = state.getMethod("ownerUuid");
        a.target = state.getMethod("target");
        a.targetEntity = targetSnap.getMethod("entityUuid");
        return a;
    }

    private UUID uuid(Object o) {
        return o instanceof UUID u ? u : null;
    }

    /** Removes all bots owned by or targeting the player. Returns how many, or -1 if the API is unavailable. */
    private int removeViaApi(UUID player) {
        try {
            Api a = api();
            if (a == null) return -1;
            int removed = 0;
            for (Object bot : (Collection<?>) a.getActiveBots.invoke(a.api)) {
                Object state = a.getState.invoke(bot);
                boolean mine = player.equals(uuid(a.owner.invoke(state)));
                if (!mine) {
                    Object t = a.target.invoke(state);
                    if (t != null) mine = player.equals(uuid(a.targetEntity.invoke(t)));
                }
                if (mine) {
                    a.despawn.invoke(bot);
                    removed++;
                }
            }
            return removed;
        } catch (Throwable t) {
            if (debug()) plugin.getLogger().warning("[cleanup] API not usable: " + t);
            return -1;
        }
    }

    // ------------------------------------------------------------ public

    /** Removes the player's bots right now. */
    void cleanup(UUID player, String playerName) {
        if (!plugin.getConfig().getBoolean("bot-cleanup.enabled", true)) return;
        int removed = removeViaApi(player);
        if (debug()) plugin.getLogger().info("[cleanup] " + playerName + ": removed " + removed + " bot(s) through the API");
        if (removed >= 0) return;
        removeViaCommands(playerName);
    }

    private void startSweep() {
        if (!plugin.getConfig().getBoolean("bot-cleanup.sweep", true)) return;
        Bukkit.getScheduler().runTaskTimer(plugin, this::sweep, 40L, 40L);
    }

    /** Despawns bots that belong to online players who are not in a practice session. */
    private void sweep() {
        if (!plugin.getConfig().getBoolean("bot-cleanup.enabled", true)) return;
        try {
            Api a = api();
            if (a == null) return;
            for (Object bot : new ArrayList<>((Collection<?>) a.getActiveBots.invoke(a.api))) {
                Object state = a.getState.invoke(bot);
                UUID who = uuid(a.owner.invoke(state));
                Object t = a.target.invoke(state);
                if (who == null && t != null) who = uuid(a.targetEntity.invoke(t));
                if (who == null) continue;
                Player p = Bukkit.getPlayer(who);
                if (p == null) continue;
                if (plugin.sessions().get(who) != null) continue;
                if (p.hasPermission("practicecore.bypass")) continue;
                a.despawn.invoke(bot);
                if (debug()) plugin.getLogger().info("[cleanup] sweep removed a bot of " + p.getName());
            }
        } catch (Throwable t) {
            if (debug()) plugin.getLogger().warning("[cleanup] sweep failed: " + t);
        }
    }

    // ------------------------------------------------------------ fallback: PracticeBot commands

    private void removeViaCommands(String playerName) {
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
            if (debug()) plugin.getLogger().info("[cleanup] " + line);
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
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    killCmd.replace("{id}", id).replace("{player}", playerName));
        }
    }
}

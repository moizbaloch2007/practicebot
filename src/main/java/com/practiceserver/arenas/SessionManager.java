package com.practiceserver.arenas;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * A practice session.
 * Normal: PREP (set up your kit) -> COUNTDOWN -> FIGHT (bot spawned by the plugin).
 * Custom: CUSTOM (no automatic bot, the player uses PracticeBot's /spawnbot menu).
 */
final class SessionManager {

    enum Phase { PREP, COUNTDOWN, FIGHT, CUSTOM }

    static final class Session {
        final String playerName;
        final String mode;
        final String difficulty;
        final String template;
        final Location spawn;
        final boolean custom;
        Phase phase = Phase.PREP;
        BukkitTask task;

        Session(String playerName, String mode, String difficulty, String template, Location spawn, boolean custom) {
            this.playerName = playerName;
            this.mode = mode;
            this.difficulty = difficulty;
            this.template = template;
            this.spawn = spawn;
            this.custom = custom;
        }
    }

    private final ArenasPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    SessionManager(ArenasPlugin plugin) {
        this.plugin = plugin;
    }

    Session get(UUID id) {
        return sessions.get(id);
    }

    /** Ends the session: frees the slot, removes the player's bots at once and starts the arena restore. */
    void end(UUID id) {
        end(id, true);
    }

    private void end(UUID id, boolean regen) {
        Session s = sessions.remove(id);
        if (s != null && s.task != null) s.task.cancel();
        ArenaManager.Assignment as = plugin.arenas().release(id);
        if (s != null) {
            plugin.cleaner().cleanup(id, s.playerName);
            if (regen && s.phase != Phase.PREP && as != null && plugin.regen() != null) {
                plugin.regen().start(as.arena(), as.index());
            }
        }
    }

    void endAll() {
        for (UUID id : new ArrayList<>(sessions.keySet())) end(id, false);
    }

    void leave(Player p) {
        end(p.getUniqueId());
        plugin.kits().clearKit(p);
        if (!plugin.lobby().teleport(p)) {
            String c = plugin.getConfig().getString("lobby-command", "");
            if (c != null && !c.isBlank()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.replace("{player}", p.getName()));
            } else {
                Text.send(p, "&cThe lobby spawn is not set. An admin must run &e/parena setlobby&c.");
            }
        }
        plugin.lobby().giveItemsLater(p, 3L);
    }

    // ------------------------------------------------------------ entering an arena

    /** Frees the old session, removes old bots, takes a free slot and teleports. Returns the spawn or null. */
    private Location enterArena(Player p, String type) {
        final UUID id = p.getUniqueId();
        p.closeInventory();
        end(id);
        plugin.cleaner().cleanup(id, p.getName());

        ArenaManager am = plugin.arenas();
        ArenaManager.Assignment as = am.assign(type, id);
        if (as == null) {
            Text.send(p, plugin.msg("arena-full", "&cAll arenas of that type are full or not built yet. Try another one."));
            return null;
        }
        Location spawn = as.arena().spawns.get(as.index()).toLocation();
        if (spawn == null) {
            am.release(id);
            Text.send(p, plugin.msg("world-missing", "&cThat arena's world is not loaded. Tell an admin."));
            return null;
        }
        p.teleport(spawn);
        p.setGameMode(GameMode.SURVIVAL);
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max == null ? 20.0 : max.getValue());
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        for (PotionEffect fx : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(fx.getType());
        return spawn;
    }

    // ------------------------------------------------------------ normal mode: prep, countdown, fight

    void beginPrep(Player p, String mode, String diff, String type) {
        final UUID id = p.getUniqueId();
        Location spawn = enterArena(p, type);
        if (spawn == null) return;

        plugin.kits().giveSelected(p, mode);
        plugin.kits().giveArenaItems(p, false);

        String template = plugin.getConfig().getString("modes." + mode + ".templates." + diff,
                plugin.getConfig().getString("modes." + mode + ".template", ""));
        final Session s = new Session(p.getName(), mode, diff, template, spawn, false);
        sessions.put(id, s);

        Text.send(p, plugin.msg("prep", "&aSet up your hotbar. Nothing attacks you until you start."));
        Component click = Text.c("&a&l[ CLICK HERE TO START THE FIGHT ]")
                .clickEvent(ClickEvent.runCommand("/fight"))
                .hoverEvent(HoverEvent.showText(Text.c("&7Starts the countdown")));
        p.sendMessage(click);
        Text.send(p, plugin.msg("prep-help", "&7Or type &e/fight&7. Change kit: &e/kits&7. Effects: &e/effects&7. Leave: &e/leave"));

        final int prep = plugin.prepSeconds();
        if (prep > 0) {
            s.task = new BukkitRunnable() {
                int left = prep;

                @Override
                public void run() {
                    Player pl = Bukkit.getPlayer(id);
                    if (pl == null || sessions.get(id) != s || s.phase != Phase.PREP) {
                        cancel();
                        return;
                    }
                    if (left <= 0) {
                        cancel();
                        fight(pl);
                        return;
                    }
                    pl.sendActionBar(Text.c("&eFight starts in &f" + left + "s &7- or type &e/fight"));
                    left--;
                }
            }.runTaskTimer(plugin, 20L, 20L);
        }
    }

    void fight(Player p) {
        final UUID id = p.getUniqueId();
        final Session s = sessions.get(id);
        if (s == null) {
            Text.send(p, plugin.msg("no-session", "&cYou are not in a practice session. Use &e/pc&c."));
            return;
        }
        if (s.custom) {
            Text.send(p, plugin.msg("custom-no-fight", "&7Custom mode has no /fight. Use &e/spawnbot &7to control your bot."));
            return;
        }
        if (s.phase != Phase.PREP) return;
        if (s.task != null) s.task.cancel();
        s.phase = Phase.COUNTDOWN;

        final int secs = Math.max(0, plugin.countdownSeconds());
        s.task = new BukkitRunnable() {
            int left = secs;

            @Override
            public void run() {
                Player pl = Bukkit.getPlayer(id);
                if (pl == null || sessions.get(id) != s) {
                    cancel();
                    return;
                }
                if (left > 0) {
                    title(pl, "&e" + left, "&7Get ready");
                    left--;
                    return;
                }
                cancel();
                title(pl, "&a&lFIGHT!", "");
                s.phase = Phase.FIGHT;
                spawnBot(pl, s);
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // ------------------------------------------------------------ custom mode (PracticeBot's own /spawnbot menu)

    void beginCustom(Player p, String type) {
        final UUID id = p.getUniqueId();
        Location spawn = enterArena(p, type);
        if (spawn == null) return;

        p.getInventory().clear();
        plugin.kits().giveArenaItems(p, true);

        final Session s = new Session(p.getName(), "custom", "custom", "", spawn, true);
        s.phase = Phase.CUSTOM;
        sessions.put(id, s);

        Text.send(p, plugin.msg("custom-start", "&aCustom mode: you have no kit yet and no bot. Pick a kit with &e/kits&a, then use &e/spawnbot &ato set up and start your own bot."));
        Text.send(p, plugin.msg("custom-help", "&7Effects: &e/effects&7. Leave: &e/leave&7. Your bots are removed when you leave."));
        if (plugin.getConfig().getBoolean("custom-mode.auto-spawnbot", true)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player pl = Bukkit.getPlayer(id);
                if (pl != null && sessions.get(id) == s) Bukkit.dispatchCommand(pl, "spawnbot");
            }, 20L);
        }
    }

    // ------------------------------------------------------------ changing the kit inside a session

    /** Gives one of the player's kits ("default" or "1".."3"). Only before the fight, or any time in custom mode. */
    void applyKit(Player p, String mode, String key) {
        final UUID id = p.getUniqueId();
        Session s = sessions.get(id);
        if (s == null) {
            Text.send(p, plugin.msg("no-session", "&cYou are not in a practice session. Use &e/pc&c."));
            return;
        }
        if (!s.custom && s.phase != Phase.PREP) {
            Text.send(p, plugin.msg("kit-locked", "&cYou can only change your kit before the fight starts."));
            return;
        }
        String useMode = s.custom ? mode : s.mode;
        plugin.kits().give(p, useMode, key);
        plugin.layouts().select(id, useMode, key);
        plugin.kits().giveArenaItems(p, s.custom);
        Text.send(p, plugin.msg("kit-applied", "&aKit changed."));
    }

    // ------------------------------------------------------------ helpers

    private void title(Player p, String main, String sub) {
        p.showTitle(Title.title(Text.c(main), Text.c(sub),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
    }

    private void spawnBot(Player p, Session s) {
        if (s.template == null || s.template.isEmpty()) {
            plugin.getLogger().warning("Mode '" + s.mode + "' has no template for difficulty '" + s.difficulty + "'.");
            return;
        }
        // make sure no older bot of this player is still alive
        plugin.cleaner().cleanup(p.getUniqueId(), p.getName());

        Location spawn = s.spawn;
        double dist = plugin.getConfig().getDouble("bot-distance", 8.0);
        double rad = Math.toRadians(spawn.getYaw());
        double bx = spawn.getX() - Math.sin(rad) * dist;
        double bz = spawn.getZ() + Math.cos(rad) * dist;
        float byaw = spawn.getYaw() + 180f;
        if (byaw > 180f) byaw -= 360f;

        String cmd = plugin.getConfig().getString("bot-spawn-command",
                "pbadmin template spawn {template} {player} {world} {x} {y} {z} {yaw} {pitch}")
                .replace("{template}", s.template)
                .replace("{player}", p.getName())
                .replace("{world}", spawn.getWorld().getName())
                .replace("{x}", String.format(Locale.US, "%.2f", bx))
                .replace("{y}", String.format(Locale.US, "%.2f", spawn.getY()))
                .replace("{z}", String.format(Locale.US, "%.2f", bz))
                .replace("{yaw}", String.format(Locale.US, "%.1f", byaw))
                .replace("{pitch}", "0");
        if (plugin.getConfig().getBoolean("debug", false)) plugin.getLogger().info("Running: " + cmd);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }
}

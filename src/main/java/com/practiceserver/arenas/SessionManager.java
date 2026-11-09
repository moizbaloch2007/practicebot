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

/** A practice session: PREP (set up your kit) -> COUNTDOWN -> FIGHT (bot spawned). */
final class SessionManager {

    enum Phase { PREP, COUNTDOWN, FIGHT }

    static final class Session {
        final String playerName;
        final String mode;
        final String difficulty;
        final String template;
        final Location spawn;
        Phase phase = Phase.PREP;
        BukkitTask task;

        Session(String playerName, String mode, String difficulty, String template, Location spawn) {
            this.playerName = playerName;
            this.mode = mode;
            this.difficulty = difficulty;
            this.template = template;
            this.spawn = spawn;
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

    /** Ends the session and frees the arena slot. */
    void end(UUID id) {
        end(id, true);
    }

    private void end(UUID id, boolean regen) {
        Session s = sessions.remove(id);
        if (s != null && s.task != null) s.task.cancel();
        ArenaManager.Assignment as = plugin.arenas().release(id);
        if (s != null && s.phase != Phase.PREP) {
            plugin.cleaner().cleanup(s.playerName);
            if (regen && as != null && plugin.regen() != null) {
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
        String c = plugin.getConfig().getString("lobby-command", "warp lobby {player}");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.replace("{player}", p.getName()));
    }

    // ------------------------------------------------------------ prep

    void beginPrep(Player p, String mode, String diff, String type) {
        final UUID id = p.getUniqueId();
        p.closeInventory();
        end(id);

        ArenaManager am = plugin.arenas();
        ArenaManager.Assignment as = am.assign(type, id);
        if (as == null) {
            Text.send(p, plugin.msg("arena-full", "&cAll arenas of that type are full or not built yet. Try another one."));
            return;
        }
        Location spawn = as.arena().spawns.get(as.index()).toLocation();
        if (spawn == null) {
            am.release(id);
            Text.send(p, plugin.msg("world-missing", "&cThat arena's world is not loaded. Tell an admin."));
            return;
        }

        p.teleport(spawn);
        p.setGameMode(GameMode.SURVIVAL);
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max == null ? 20.0 : max.getValue());
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        for (PotionEffect fx : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(fx.getType());
        plugin.kits().give(p, mode);

        String template = plugin.getConfig().getString("modes." + mode + ".templates." + diff,
                plugin.getConfig().getString("modes." + mode + ".template", ""));
        final Session s = new Session(p.getName(), mode, diff, template, spawn);
        sessions.put(id, s);

        Text.send(p, plugin.msg("prep", "&aSet up your hotbar. Nothing attacks you until you start."));
        Component click = Text.c("&a&l[ CLICK HERE TO START THE FIGHT ]")
                .clickEvent(ClickEvent.runCommand("/fight"))
                .hoverEvent(HoverEvent.showText(Text.c("&7Starts the countdown")));
        p.sendMessage(click);
        Text.send(p, plugin.msg("prep-help", "&7Or type &e/fight&7. Use &e/leave &7to go back to the lobby."));

        final int prep = plugin.getConfig().getInt("prep-seconds", 30);
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

    // ------------------------------------------------------------ fight

    void fight(Player p) {
        final UUID id = p.getUniqueId();
        final Session s = sessions.get(id);
        if (s == null) {
            Text.send(p, plugin.msg("no-session", "&cYou are not in a practice session. Use &e/practice&c."));
            return;
        }
        if (s.phase != Phase.PREP) return;
        if (s.task != null) s.task.cancel();
        s.phase = Phase.COUNTDOWN;

        final int secs = Math.max(0, plugin.getConfig().getInt("countdown-seconds", 3));
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

    private void title(Player p, String main, String sub) {
        p.showTitle(Title.title(Text.c(main), Text.c(sub),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
    }

    private void spawnBot(Player p, Session s) {
        if (s.template == null || s.template.isEmpty()) {
            plugin.getLogger().warning("Mode '" + s.mode + "' has no template for difficulty '" + s.difficulty + "'.");
            return;
        }
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

package com.practiceserver.arenas;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Ends the session (frees the slot, removes the bot, clears the kit) when a player leaves, dies or changes world. */
final class SessionListener implements Listener {

    private final ArenasPlugin plugin;
    private final Set<UUID> clearOnRespawn = new HashSet<>();

    SessionListener(ArenasPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        plugin.editor().forget(id);
        if (plugin.sessions().get(id) != null) {
            plugin.sessions().end(id);
            plugin.kits().clearKit(p);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        SessionManager.Session s = plugin.sessions().get(p.getUniqueId());
        if (s == null) return;
        if (!p.getWorld().getName().equals(s.spawn.getWorld().getName())) {
            plugin.sessions().end(p.getUniqueId());
            plugin.kits().clearKit(p);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        UUID id = p.getUniqueId();
        if (plugin.sessions().get(id) == null) return;
        plugin.sessions().end(id);
        clearOnRespawn.add(id);
        if (plugin.getConfig().getBoolean("clear-drops-on-death", true)) {
            e.getDrops().removeIf(plugin.kits()::isKit);
            e.setDroppedExp(0);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (!clearOnRespawn.remove(p.getUniqueId())) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && plugin.sessions().get(p.getUniqueId()) == null) plugin.kits().clearKit(p);
        }, 5L);
    }
}

package com.practiceserver.arenas;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Frees the arena slot when the player leaves, dies or changes world. */
final class SessionListener implements Listener {

    private final ArenasPlugin plugin;

    SessionListener(ArenasPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (plugin.arenas().release(p.getUniqueId()) != null) {
            p.getInventory().clear();
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        ArenaManager.Assignment as = plugin.arenas().current(e.getPlayer().getUniqueId());
        if (as == null) return;
        String arenaWorld = as.arena().spawns.get(as.index()).world();
        if (!e.getPlayer().getWorld().getName().equals(arenaWorld)) {
            plugin.arenas().release(e.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (plugin.arenas().release(p.getUniqueId()) == null) return;
        if (plugin.getConfig().getBoolean("clear-drops-on-death", true)) {
            e.setKeepInventory(false);
            e.getDrops().clear();
            e.setKeepLevel(false);
            e.setDroppedExp(0);
        }
    }
}

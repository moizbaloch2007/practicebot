package com.practiceserver.arenas;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Spawn on join / respawn, lobby items and simple lobby protection. */
final class LobbyListener implements Listener {

    private final ArenasPlugin plugin;
    private final Map<UUID, Long> lastUse = new HashMap<>();

    LobbyListener(ArenasPlugin plugin) {
        this.plugin = plugin;
    }

    private LobbyManager lobby() {
        return plugin.lobby();
    }

    private boolean protect(String flag, Player p) {
        if (!plugin.getConfig().getBoolean("lobby.protect.enabled", true)) return false;
        if (!plugin.getConfig().getBoolean("lobby.protect." + flag, true)) return false;
        if (!lobby().isLobbyWorld(p.getWorld())) return false;
        return !(p.getGameMode() == GameMode.CREATIVE && p.hasPermission("practicecore.admin"));
    }

    // ------------------------------------------------------------ join / respawn / world change

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (plugin.getConfig().getBoolean("lobby.spawn-on-join", true) && lobby().location() != null) {
            lobby().teleport(p);
        }
        lobby().giveItemsLater(p, 5L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (plugin.getConfig().getBoolean("lobby.respawn-at-lobby", true)) {
            Location l = lobby().location();
            if (l != null) e.setRespawnLocation(l);
        }
        lobby().giveItemsLater(e.getPlayer(), 6L);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        lobby().giveItemsLater(e.getPlayer(), 3L);
    }

    // ------------------------------------------------------------ item use

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() == Action.PHYSICAL) return;
        String id = lobby().itemId(e.getItem());
        if (id == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        Long last = lastUse.get(p.getUniqueId());
        if (last != null && now - last < 400L) return;
        lastUse.put(p.getUniqueId(), now);
        lobby().runAction(p, id);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (p.getGameMode() == GameMode.CREATIVE && p.hasPermission("practicecore.admin")) return;
        boolean touches = lobby().itemId(e.getCurrentItem()) != null || lobby().itemId(e.getCursor()) != null;
        if (!touches && e.getHotbarButton() >= 0) {
            touches = lobby().itemId(p.getInventory().getItem(e.getHotbarButton())) != null;
        }
        if (!touches && e.getClick().name().equals("SWAP_OFFHAND")) {
            touches = lobby().itemId(p.getInventory().getItemInOffHand()) != null;
        }
        if (touches) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent e) {
        if (lobby().itemId(e.getItemDrop().getItemStack()) != null || protect("drop-items", e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        ItemStack a = e.getMainHandItem();
        ItemStack b = e.getOffHandItem();
        if (lobby().itemId(a) != null || lobby().itemId(b) != null) e.setCancelled(true);
    }

    // ------------------------------------------------------------ protection

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (protect("build", e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (protect("build", e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && protect("hunger", p)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!lobby().isLobbyWorld(p.getWorld())) return;
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
            e.setCancelled(true);
            lobby().teleport(p);
            return;
        }
        if (protect("damage", p)) e.setCancelled(true);
    }
}

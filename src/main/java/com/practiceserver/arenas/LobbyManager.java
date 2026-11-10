package com.practiceserver.arenas;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Lobby spawn, /spawn, /leave target and the lobby hotbar items (replaces ItemJoin and Essentials spawn). */
final class LobbyManager {

    private final ArenasPlugin plugin;
    private final NamespacedKey itemKey;
    private File file;
    private YamlConfiguration yaml = new YamlConfiguration();

    LobbyManager(ArenasPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "lobby_item");
        load();
    }

    void load() {
        file = new File(plugin.getDataFolder(), "data.yml");
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    // ------------------------------------------------------------ spawn

    SpawnPoint spawn() {
        String s = yaml.getString("lobby.spawn", "");
        return s == null || s.isEmpty() ? null : SpawnPoint.parse(s);
    }

    Location location() {
        SpawnPoint sp = spawn();
        return sp == null ? null : sp.toLocation();
    }

    void setSpawn(Location l) {
        yaml.set("lobby.spawn", SpawnPoint.of(l).serialize());
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save data.yml: " + e.getMessage());
        }
    }

    boolean teleport(Player p) {
        Location l = location();
        if (l == null) return false;
        p.teleport(l);
        return true;
    }

    boolean isLobbyWorld(World w) {
        if (w == null) return false;
        List<String> worlds = plugin.getConfig().getStringList("lobby.worlds");
        if (!worlds.isEmpty()) return worlds.contains(w.getName());
        SpawnPoint sp = spawn();
        return sp != null && sp.world().equals(w.getName());
    }

    // ------------------------------------------------------------ lobby items

    boolean itemsEnabled() {
        return plugin.getConfig().getBoolean("lobby-items.enabled", true);
    }

    /** Puts the configured items in their slots (removes arena kit items first). */
    void giveItems(Player p) {
        plugin.kits().clearKit(p);
        if (!itemsEnabled()) return;
        ConfigurationSection items = plugin.getConfig().getConfigurationSection("lobby-items.items");
        if (items == null) return;
        for (String key : items.getKeys(false)) {
            ConfigurationSection s = items.getConfigurationSection(key);
            if (s == null) continue;
            Material m = Material.matchMaterial(s.getString("material", "STONE"));
            if (m == null) m = Material.STONE;
            ItemStack it = new ItemStack(m);
            ItemMeta meta = it.getItemMeta();
            meta.displayName(Text.c(s.getString("name", key)));
            List<Component> lore = new ArrayList<>();
            for (String line : s.getStringList("lore")) lore.add(Text.c(line));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, key);
            it.setItemMeta(meta);
            int slot = s.getInt("slot", 4);
            if (slot >= 0 && slot < 36) p.getInventory().setItem(slot, it);
        }
    }

    void giveItemsLater(Player p, long ticks) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && isLobbyWorld(p.getWorld())) giveItems(p);
        }, ticks);
    }

    String itemId(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    /** Runs the action of a lobby item. Format: "pc kit" (player) or "console: say hi {player}". */
    void runAction(Player p, String itemId) {
        String action = plugin.getConfig().getString("lobby-items.items." + itemId + ".action", "");
        if (action == null || action.isBlank()) return;
        action = action.trim();
        if (action.toLowerCase().startsWith("console:")) {
            String cmd = action.substring(8).trim().replace("{player}", p.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        } else {
            if (action.toLowerCase().startsWith("player:")) action = action.substring(7).trim();
            Bukkit.dispatchCommand(p, action.replace("{player}", p.getName()));
        }
    }
}

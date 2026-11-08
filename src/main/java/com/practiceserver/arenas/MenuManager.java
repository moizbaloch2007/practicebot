package com.practiceserver.arenas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;

final class MenuManager implements Listener {

    enum Kind { MODE, TYPE }

    static final class Holder implements InventoryHolder {
        final Kind kind;
        final String mode;
        final Map<Integer, String> slots = new HashMap<>();
        Inventory inventory;

        Holder(Kind kind, String mode) {
            this.kind = kind;
            this.mode = mode;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final ArenasPlugin plugin;

    MenuManager(ArenasPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------ menus

    void openModes(Player p) {
        ConfigurationSection modes = plugin.getConfig().getConfigurationSection("modes");
        if (modes == null) {
            Text.send(p, plugin.msg("no-modes", "&cNo modes are configured."));
            return;
        }
        int size = plugin.getConfig().getInt("menus.mode-size", 27);
        Holder h = new Holder(Kind.MODE, null);
        Inventory inv = Bukkit.createInventory(h, size, Text.c(plugin.getConfig().getString("menus.mode-title", "&8Practice Modes")));
        h.inventory = inv;
        fill(inv);
        for (String key : modes.getKeys(false)) {
            ConfigurationSection m = modes.getConfigurationSection(key);
            if (m == null) continue;
            int slot = m.getInt("slot", -1);
            if (slot < 0 || slot >= size) continue;
            List<String> lore = new ArrayList<>(m.getStringList("lore"));
            lore.add("");
            lore.add("&eClick to choose");
            inv.setItem(slot, icon(m.getString("icon", "STONE"), m.getString("name", key), lore));
            h.slots.put(slot, key);
        }
        p.openInventory(inv);
    }

    void openTypes(Player p, String mode) {
        ConfigurationSection types = plugin.getConfig().getConfigurationSection("arena-types");
        if (types == null) {
            Text.send(p, plugin.msg("no-types", "&cNo arena types are configured."));
            return;
        }
        int size = plugin.getConfig().getInt("menus.type-size", 27);
        Holder h = new Holder(Kind.TYPE, mode);
        Inventory inv = Bukkit.createInventory(h, size, Text.c(plugin.getConfig().getString("menus.type-title", "&8Choose Arena")));
        h.inventory = inv;
        fill(inv);
        ArenaManager am = plugin.arenas();
        for (String key : types.getKeys(false)) {
            ConfigurationSection t = types.getConfigurationSection(key);
            if (t == null) continue;
            int slot = t.getInt("slot", -1);
            if (slot < 0 || slot >= size) continue;
            List<String> lore = new ArrayList<>(t.getStringList("lore"));
            lore.add("");
            int cap = am.capacity(key);
            if (cap == 0) {
                lore.add("&cNo arena built yet");
            } else {
                int used = am.used(key);
                lore.add("&7Arenas: &f" + am.arenaCount(key));
                lore.add("&7Players: &f" + used + "&7/&f" + cap);
                lore.add(used >= cap ? "&cAll arenas are full" : "&eClick to play");
            }
            inv.setItem(slot, icon(t.getString("icon", "STONE"), t.getString("name", key), lore));
            h.slots.put(slot, key);
        }
        p.openInventory(inv);
    }

    private void fill(Inventory inv) {
        if (!plugin.getConfig().getBoolean("menus.filler", true)) return;
        ItemStack pane = icon("BLACK_STAINED_GLASS_PANE", " ", new ArrayList<>());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
    }

    private ItemStack icon(String material, String name, List<String> lore) {
        Material m = Material.matchMaterial(material);
        if (m == null) m = Material.STONE;
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Text.c(name));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(Text.c(s));
        meta.lore(l);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(meta);
        return it;
    }

    // ------------------------------------------------------------ clicks

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        String key = h.slots.get(e.getRawSlot());
        if (key == null) return;

        if (h.kind == Kind.MODE) {
            String node = plugin.getConfig().getString("modes." + key + ".permission", "");
            if (node != null && !node.isEmpty() && !p.hasPermission(node)) {
                p.closeInventory();
                Text.send(p, plugin.msg("no-permission", "&cYou do not have permission for this mode."));
                return;
            }
            openTypes(p, key);
        } else {
            start(p, h.mode, key);
        }
    }

    // ------------------------------------------------------------ session start

    void start(Player p, String mode, String type) {
        p.closeInventory();
        ArenaManager am = plugin.arenas();
        am.release(p.getUniqueId());

        ArenaManager.Assignment as = am.assign(type, p.getUniqueId());
        if (as == null) {
            Text.send(p, plugin.msg("arena-full", "&cAll arenas of that type are full or not built yet. Try another one."));
            return;
        }
        Location spawn = as.arena().spawns.get(as.index()).toLocation();
        if (spawn == null) {
            am.release(p.getUniqueId());
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

        final String template = plugin.getConfig().getString("modes." + mode + ".template", "");
        final java.util.UUID id = p.getUniqueId();
        long delay = plugin.getConfig().getLong("bot-spawn-delay-ticks", 10L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> spawnBot(id, template, spawn), delay);

        Text.send(p, plugin.msg("started", "&aGood luck! Use &e/leave &ato go back to the lobby."));
    }

    private void spawnBot(java.util.UUID id, String template, Location spawn) {
        Player p = Bukkit.getPlayer(id);
        if (p == null || !p.isOnline()) return;
        ArenaManager.Assignment as = plugin.arenas().current(id);
        if (as == null) return;
        if (template == null || template.isEmpty()) {
            plugin.getLogger().warning("Mode has no template set.");
            return;
        }
        double dist = plugin.getConfig().getDouble("bot-distance", 8.0);
        double rad = Math.toRadians(spawn.getYaw());
        double bx = spawn.getX() - Math.sin(rad) * dist;
        double bz = spawn.getZ() + Math.cos(rad) * dist;
        float byaw = spawn.getYaw() + 180f;
        if (byaw > 180f) byaw -= 360f;

        String cmd = plugin.getConfig().getString("bot-spawn-command",
                "pbadmin template spawn {template} {player} {world} {x} {y} {z} {yaw} {pitch}")
                .replace("{template}", template)
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

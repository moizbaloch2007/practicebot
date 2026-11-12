package com.practiceserver.arenas;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

/**
 * Gives the player gear.
 * Item format: MATERIAL [amount] [enchant:level ...] [potion:type] [unbreakable]
 * A player's saved layout (Kit Editor) is used if they have one, otherwise the default from config.
 * Layout index: 0-35 inventory, 36 helmet, 37 chestplate, 38 leggings, 39 boots, 40 offhand.
 */
final class KitManager {

    private final ArenasPlugin plugin;
    private final NamespacedKey kitKey;
    private final NamespacedKey arenaItemKey;

    KitManager(ArenasPlugin plugin) {
        this.plugin = plugin;
        this.kitKey = new NamespacedKey(plugin, "kit");
        this.arenaItemKey = new NamespacedKey(plugin, "arena_item");
    }

    // ------------------------------------------------------------ tagging

    private ItemStack tag(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.getPersistentDataContainer().set(kitKey, PersistentDataType.BYTE, (byte) 1);
            it.setItemMeta(m);
        }
        return it;
    }

    boolean isKit(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return false;
        Byte b = it.getItemMeta().getPersistentDataContainer().get(kitKey, PersistentDataType.BYTE);
        return b != null && b == 1;
    }

    /** Removes only the arena kit items. Other items (lobby items) are left alone. */
    void clearKit(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] c = inv.getContents();
        for (int i = 0; i < c.length; i++) {
            if (isKit(c[i])) inv.setItem(i, null);
        }
    }

    // ------------------------------------------------------------ layouts

    static boolean isArmor(ItemStack it) {
        String n = it.getType().name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS");
    }

    /** Which equipment slot (36-39) an item belongs in, or -1. */
    static int equipIndex(ItemStack it) {
        String n = it.getType().name();
        if (n.endsWith("_HELMET")) return 36;
        if (n.endsWith("_CHESTPLATE") || n.equals("ELYTRA")) return 37;
        if (n.endsWith("_LEGGINGS")) return 38;
        if (n.endsWith("_BOOTS")) return 39;
        return -1;
    }

    private List<ItemStack> parseItems(String mode) {
        List<ItemStack> out = new ArrayList<>();
        ConfigurationSection kit = plugin.getConfig().getConfigurationSection("kits." + mode);
        if (kit == null) return out;
        for (String line : kit.getStringList("items")) {
            ItemStack it = parse(line);
            if (it != null) out.add(it);
        }
        return out;
    }

    /** The default kit from config as a 41 slot layout. */
    ItemStack[] defaultLayout(String mode) {
        ItemStack[] out = new ItemStack[LayoutManager.SIZE];
        Inventory tmp = Bukkit.createInventory(null, 36);
        for (ItemStack it : parseItems(mode)) {
            if (equipIndex(it) >= 0 && out[equipIndex(it)] == null) out[equipIndex(it)] = it;
            else tmp.addItem(it);
        }
        ItemStack[] contents = tmp.getContents();
        for (int i = 0; i < 36 && i < contents.length; i++) out[i] = contents[i];
        String off = plugin.getConfig().getString("kits." + mode + ".offhand", "");
        if (off != null && !off.isBlank()) out[40] = parse(off);
        return out;
    }

    /** A saved kit of the player (slot 1-3), or the default if that slot is empty. */
    ItemStack[] slotLayout(Player p, String mode, int slot) {
        ItemStack[] saved = plugin.layouts().get(p.getUniqueId(), mode, slot);
        return saved != null ? saved : defaultLayout(mode);
    }

    /** key = "default" or "1".."3". */
    ItemStack[] layoutFor(Player p, String mode, String key) {
        if (!"default".equals(key) && key.matches("[1-3]")) return slotLayout(p, mode, Integer.parseInt(key));
        return defaultLayout(mode);
    }

    /** The kit the player last selected for this mode ("default" if none, or if it was deleted). */
    String selectedKey(Player p, String mode) {
        String key = plugin.layouts().selected(p.getUniqueId(), mode);
        if (key.matches("[1-3]") && plugin.layouts().has(p.getUniqueId(), mode, Integer.parseInt(key))) return key;
        return "default";
    }

    void giveSelected(Player p, String mode) {
        give(p, mode, selectedKey(p, mode));
    }

    void give(Player p, String mode, String key) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        ItemStack[] l = layoutFor(p, mode, key);
        for (int i = 0; i < 36; i++) {
            if (l[i] != null) inv.setItem(i, tag(l[i].clone()));
        }
        if (l[36] != null) inv.setHelmet(tag(l[36].clone()));
        if (l[37] != null) inv.setChestplate(tag(l[37].clone()));
        if (l[38] != null) inv.setLeggings(tag(l[38].clone()));
        if (l[39] != null) inv.setBoots(tag(l[39].clone()));
        if (l[40] != null) inv.setItemInOffHand(tag(l[40].clone()));
    }

    // ------------------------------------------------------------ arena items (kit selector, effects)

    /** Gives the items from "arena-items" (kit selector, effects). They are removed with the kit. */
    void giveArenaItems(Player p, boolean custom) {
        if (!plugin.getConfig().getBoolean("arena-items.enabled", true)) return;
        ConfigurationSection items = plugin.getConfig().getConfigurationSection("arena-items.items");
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
            meta.getPersistentDataContainer().set(arenaItemKey, PersistentDataType.STRING, key);
            it.setItemMeta(meta);
            tag(it);
            int slot = custom ? s.getInt("custom-slot", s.getInt("slot", 35)) : s.getInt("slot", 35);
            if (slot >= 0 && slot < 36) p.getInventory().setItem(slot, it);
        }
    }

    String arenaItemId(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(arenaItemKey, PersistentDataType.STRING);
    }

    // ------------------------------------------------------------ parsing

    ItemStack parseLine(String line) {
        return parse(line);
    }

    private ItemStack parse(String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return null;
        Material m = Material.matchMaterial(parts[0]);
        if (m == null || m.isAir()) {
            plugin.getLogger().warning("Kit item not found, skipped: " + line);
            return null;
        }
        int amount = 1;
        int start = 1;
        if (parts.length > 1 && parts[1].matches("\\d+")) {
            amount = Math.max(1, Integer.parseInt(parts[1]));
            start = 2;
        }
        ItemStack it = new ItemStack(m, amount);
        boolean unbreakable = false;
        for (int i = start; i < parts.length; i++) {
            String token = parts[i];
            if (token.equalsIgnoreCase("unbreakable")) {
                unbreakable = true;
                continue;
            }
            String[] kv = token.split(":");
            if (kv.length != 2) continue;
            if (kv[0].equalsIgnoreCase("potion")) {
                PotionType type = Registry.POTION.get(NamespacedKey.minecraft(kv[1].toLowerCase()));
                ItemMeta meta = it.getItemMeta();
                if (type != null && meta instanceof PotionMeta pm) {
                    pm.setBasePotionType(type);
                    it.setItemMeta(pm);
                } else {
                    plugin.getLogger().warning("Unknown potion '" + kv[1] + "' in: " + line);
                }
                continue;
            }
            if (!kv[1].matches("\\d+")) continue;
            Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(kv[0].toLowerCase()));
            if (e == null) {
                plugin.getLogger().warning("Unknown enchantment '" + kv[0] + "' in: " + line);
                continue;
            }
            it.addUnsafeEnchantment(e, Integer.parseInt(kv[1]));
        }
        if (unbreakable) {
            ItemMeta meta = it.getItemMeta();
            if (meta != null) {
                meta.setUnbreakable(true);
                it.setItemMeta(meta);
            }
        }
        return it;
    }
}

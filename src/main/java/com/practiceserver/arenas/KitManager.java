package com.practiceserver.arenas;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Gives the player gear. Item format: MATERIAL [amount] [enchant:level ...] [unbreakable]
 * Armor and offhand come from config. Main inventory/hotbar come from the player's saved
 * layout (Kit Editor) or, if none, the default order from config.
 */
final class KitManager {

    private final ArenasPlugin plugin;
    private final NamespacedKey kitKey;

    KitManager(ArenasPlugin plugin) {
        this.plugin = plugin;
        this.kitKey = new NamespacedKey(plugin, "kit");
    }

    /** Marks an item as part of an arena kit. */
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

    /** Removes only the arena kit items. Other items (like the ItemJoin star) are left alone. */
    void clearKit(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] c = inv.getContents();
        for (int i = 0; i < c.length; i++) {
            if (isKit(c[i])) inv.setItem(i, null);
        }
    }

    private static boolean isArmor(ItemStack it) {
        String n = it.getType().name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS");
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

    /** Default arrangement (player slots 0-35, hotbar first). */
    ItemStack[] defaultLayout(String mode) {
        Inventory tmp = Bukkit.createInventory(null, 36);
        for (ItemStack it : parseItems(mode)) {
            if (!isArmor(it)) tmp.addItem(it);
        }
        ItemStack[] out = new ItemStack[36];
        ItemStack[] contents = tmp.getContents();
        for (int i = 0; i < 36 && i < contents.length; i++) out[i] = contents[i];
        return out;
    }

    ItemStack[] layoutFor(Player p, String mode) {
        ItemStack[] saved = plugin.layouts().get(p.getUniqueId(), mode);
        return saved != null ? saved : defaultLayout(mode);
    }

    void give(Player p, String mode) {
        PlayerInventory inv = p.getInventory();
        inv.clear();

        ItemStack[] layout = layoutFor(p, mode);
        for (int i = 0; i < 36; i++) {
            if (layout[i] != null) inv.setItem(i, tag(layout[i].clone()));
        }

        for (ItemStack it : parseItems(mode)) {
            String n = it.getType().name();
            if (n.endsWith("_HELMET")) inv.setHelmet(tag(it));
            else if (n.endsWith("_CHESTPLATE")) inv.setChestplate(tag(it));
            else if (n.endsWith("_LEGGINGS")) inv.setLeggings(tag(it));
            else if (n.endsWith("_BOOTS")) inv.setBoots(tag(it));
        }

        String off = plugin.getConfig().getString("kits." + mode + ".offhand", "");
        if (off != null && !off.isBlank()) {
            ItemStack it = parse(off);
            if (it != null) inv.setItemInOffHand(tag(it));
        }
    }

    ItemStack parseLine(String line) {
        return parse(line);
    }

    /** Fixed gear shown in the editor: helmet, chestplate, leggings, boots, offhand. */
    ItemStack[] armorAndOffhand(String mode) {
        ItemStack[] out = new ItemStack[5];
        for (ItemStack it : parseItems(mode)) {
            String n = it.getType().name();
            if (n.endsWith("_HELMET")) out[0] = it;
            else if (n.endsWith("_CHESTPLATE")) out[1] = it;
            else if (n.endsWith("_LEGGINGS")) out[2] = it;
            else if (n.endsWith("_BOOTS")) out[3] = it;
        }
        String off = plugin.getConfig().getString("kits." + mode + ".offhand", "");
        if (off != null && !off.isBlank()) out[4] = parse(off);
        return out;
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
            if (kv.length != 2 || !kv[1].matches("\\d+")) continue;
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

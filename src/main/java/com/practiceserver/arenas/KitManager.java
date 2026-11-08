package com.practiceserver.arenas;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

/** Gives the player gear. Item format: MATERIAL [amount] [enchant:level ...] [unbreakable] */
final class KitManager {

    private final ArenasPlugin plugin;

    KitManager(ArenasPlugin plugin) {
        this.plugin = plugin;
    }

    void give(Player p, String mode) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        ConfigurationSection kit = plugin.getConfig().getConfigurationSection("kits." + mode);
        if (kit == null) return;

        for (String line : kit.getStringList("items")) {
            ItemStack it = parse(line);
            if (it == null) continue;
            String n = it.getType().name();
            if (n.endsWith("_HELMET") && inv.getHelmet() == null) {
                inv.setHelmet(it);
            } else if (n.endsWith("_CHESTPLATE") && inv.getChestplate() == null) {
                inv.setChestplate(it);
            } else if (n.endsWith("_LEGGINGS") && inv.getLeggings() == null) {
                inv.setLeggings(it);
            } else if (n.endsWith("_BOOTS") && inv.getBoots() == null) {
                inv.setBoots(it);
            } else {
                inv.addItem(it);
            }
        }

        String off = kit.getString("offhand", "");
        if (off != null && !off.isBlank()) {
            ItemStack it = parse(off);
            if (it != null) inv.setItemInOffHand(it);
        }
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

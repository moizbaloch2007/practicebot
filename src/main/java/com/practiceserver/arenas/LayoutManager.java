package com.practiceserver.arenas;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/**
 * Stores each player's saved kits (layouts.yml): up to 3 kits per mode, and which one is selected.
 * Index 0-35 = inventory slots, 36 helmet, 37 chestplate, 38 leggings, 39 boots, 40 offhand.
 */
final class LayoutManager {

    static final int SIZE = 41;
    static final int MAX_KITS = 3;

    private final ArenasPlugin plugin;
    private File file;
    private YamlConfiguration yaml = new YamlConfiguration();

    LayoutManager(ArenasPlugin plugin) {
        this.plugin = plugin;
    }

    void load() {
        file = new File(plugin.getDataFolder(), "layouts.yml");
        yaml = YamlConfiguration.loadConfiguration(file);
        migrate();
    }

    /** Older versions saved one kit per mode as a list. That becomes Kit 1. */
    private void migrate() {
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) return;
        boolean changed = false;
        for (String uuid : players.getKeys(false)) {
            ConfigurationSection modes = players.getConfigurationSection(uuid);
            if (modes == null) continue;
            for (String mode : modes.getKeys(false)) {
                String path = "players." + uuid + "." + mode;
                Object v = yaml.get(path);
                if (v instanceof List<?>) {
                    yaml.set(path, null);
                    yaml.set(path + ".1", v);
                    changed = true;
                }
            }
        }
        if (changed) save();
    }

    private String path(UUID id, String mode, int slot) {
        return "players." + id + "." + mode + "." + slot;
    }

    boolean has(UUID id, String mode, int slot) {
        List<?> list = yaml.getList(path(id, mode, slot));
        return list != null && list.size() >= SIZE;
    }

    ItemStack[] get(UUID id, String mode, int slot) {
        List<?> list = yaml.getList(path(id, mode, slot));
        if (list == null || list.size() < SIZE) return null;
        ItemStack[] out = new ItemStack[SIZE];
        for (int i = 0; i < SIZE; i++) {
            Object o = list.get(i);
            if (o instanceof ItemStack s && !s.getType().isAir()) out[i] = s;
        }
        return out;
    }

    void set(UUID id, String mode, int slot, ItemStack[] layout) {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) {
            list.add(layout[i] == null ? new ItemStack(Material.AIR) : layout[i]);
        }
        yaml.set(path(id, mode, slot), list);
        save();
    }

    void reset(UUID id, String mode, int slot) {
        yaml.set(path(id, mode, slot), null);
        if (String.valueOf(slot).equals(selected(id, mode))) yaml.set("selected." + id + "." + mode, null);
        save();
    }

    /** "default" or "1".."3". */
    String selected(UUID id, String mode) {
        return yaml.getString("selected." + id + "." + mode, "default");
    }

    void select(UUID id, String mode, String key) {
        yaml.set("selected." + id + "." + mode, key.equals("default") ? null : key);
        save();
    }

    private void save() {
        if (file == null) file = new File(plugin.getDataFolder(), "layouts.yml");
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save layouts.yml: " + e.getMessage());
        }
    }
}

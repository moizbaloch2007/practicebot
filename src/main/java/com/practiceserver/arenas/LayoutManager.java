package com.practiceserver.arenas;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/**
 * Stores each player's saved kit layout per mode (layouts.yml).
 * Index 0-35 = inventory slots, 36 helmet, 37 chestplate, 38 leggings, 39 boots, 40 offhand.
 */
final class LayoutManager {

    static final int SIZE = 41;

    private final ArenasPlugin plugin;
    private File file;
    private YamlConfiguration yaml = new YamlConfiguration();

    LayoutManager(ArenasPlugin plugin) {
        this.plugin = plugin;
    }

    void load() {
        file = new File(plugin.getDataFolder(), "layouts.yml");
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    ItemStack[] get(UUID id, String mode) {
        List<?> list = yaml.getList("players." + id + "." + mode);
        if (list == null) return null;
        if (list.size() < SIZE) return null; // older layouts without armor are reset to the default
        ItemStack[] out = new ItemStack[SIZE];
        for (int i = 0; i < SIZE; i++) {
            Object o = list.get(i);
            if (o instanceof ItemStack s && !s.getType().isAir()) out[i] = s;
        }
        return out;
    }

    void set(UUID id, String mode, ItemStack[] layout) {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) {
            list.add(layout[i] == null ? new ItemStack(Material.AIR) : layout[i]);
        }
        yaml.set("players." + id + "." + mode, list);
        save();
    }

    void reset(UUID id, String mode) {
        yaml.set("players." + id + "." + mode, null);
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

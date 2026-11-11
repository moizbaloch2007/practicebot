package com.practiceserver.arenas;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class ArenaManager {

    record Assignment(Arena arena, int index) {}

    private final ArenasPlugin plugin;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private final Map<UUID, Assignment> byPlayer = new HashMap<>();
    private File file;

    ArenaManager(ArenasPlugin plugin) {
        this.plugin = plugin;
    }

    void load() {
        file = new File(plugin.getDataFolder(), "arenas.yml");
        arenas.clear();
        byPlayer.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = yaml.getConfigurationSection("arenas");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            Arena a = new Arena(id, sec.getString(id + ".type", ""));
            for (String s : sec.getStringList(id + ".spawns")) {
                SpawnPoint p = SpawnPoint.parse(s);
                if (p != null) a.spawns.add(p);
            }
            a.schem = sec.getString(id + ".schem", "");
            String[] o = sec.getString(id + ".origin", "").split(";");
            if (o.length == 4) {
                try {
                    a.originWorld = o[0];
                    a.ox = Integer.parseInt(o[1]);
                    a.oy = Integer.parseInt(o[2]);
                    a.oz = Integer.parseInt(o[3]);
                    a.hasOrigin = true;
                } catch (NumberFormatException ignored) {
                    a.hasOrigin = false;
                }
            }
            a.snap = sec.getBoolean(id + ".snap", false);
            String[] sz = sec.getString(id + ".size", "").split(";");
            if (sz.length == 3) {
                try {
                    a.sx = Integer.parseInt(sz[0]);
                    a.sy = Integer.parseInt(sz[1]);
                    a.sz = Integer.parseInt(sz[2]);
                } catch (NumberFormatException ignored) {
                    a.sx = 0;
                }
            }
            arenas.put(id.toLowerCase(Locale.ROOT), a);
        }
    }

    void save() {
        if (file == null) file = new File(plugin.getDataFolder(), "arenas.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        for (Arena a : arenas.values()) {
            yaml.set("arenas." + a.id + ".type", a.type);
            List<String> list = new ArrayList<>();
            for (SpawnPoint p : a.spawns) list.add(p.serialize());
            yaml.set("arenas." + a.id + ".spawns", list);
            if (a.schem != null && !a.schem.isEmpty()) yaml.set("arenas." + a.id + ".schem", a.schem);
            if (a.hasOrigin) yaml.set("arenas." + a.id + ".origin", a.originWorld + ";" + a.ox + ";" + a.oy + ";" + a.oz);
            if (a.snap) yaml.set("arenas." + a.id + ".snap", true);
            if (a.sx > 0) yaml.set("arenas." + a.id + ".size", a.sx + ";" + a.sy + ";" + a.sz);
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save arenas.yml: " + e.getMessage());
        }
    }

    void setOrigin(Arena a, String schem, String world, int x, int y, int z) {
        a.schem = schem;
        a.originWorld = world;
        a.ox = x;
        a.oy = y;
        a.oz = z;
        a.hasOrigin = true;
        save();
    }

    void setSnapshot(Arena a, String world, int x, int y, int z, int sx, int sy, int sz) {
        a.schem = a.id;
        a.originWorld = world;
        a.ox = x;
        a.oy = y;
        a.oz = z;
        a.sx = sx;
        a.sy = sy;
        a.sz = sz;
        a.snap = true;
        a.hasOrigin = true;
        save();
    }

    void register(Arena a) {
        arenas.put(a.id.toLowerCase(Locale.ROOT), a);
        save();
    }

    Arena get(String id) {
        return arenas.get(id.toLowerCase(Locale.ROOT));
    }

    List<Arena> all() {
        return new ArrayList<>(arenas.values());
    }

    Arena create(String type, String id) {
        if (get(id) != null) return null;
        Arena a = new Arena(id, type);
        arenas.put(id.toLowerCase(Locale.ROOT), a);
        save();
        return a;
    }

    boolean remove(String id) {
        Arena a = arenas.remove(id.toLowerCase(Locale.ROOT));
        if (a == null) return false;
        for (UUID u : new ArrayList<>(a.taken.values())) byPlayer.remove(u);
        save();
        return true;
    }

    int capacity(String type) {
        int n = 0;
        for (Arena a : arenas.values()) if (a.type.equalsIgnoreCase(type)) n += a.spawns.size();
        return n;
    }

    /** Slots that can be taken right now (not in use, not being restored, arena not locked). */
    int freeSlots(String type) {
        int n = 0;
        for (Arena a : arenas.values()) {
            if (!a.type.equalsIgnoreCase(type) || a.locked) continue;
            n += Math.max(0, a.spawns.size() - a.taken.size() - a.regenerating.size());
        }
        return n;
    }

    int used(String type) {
        int n = 0;
        for (Arena a : arenas.values()) if (a.type.equalsIgnoreCase(type)) n += a.taken.size();
        return n;
    }

    int arenaCount(String type) {
        int n = 0;
        for (Arena a : arenas.values()) if (a.type.equalsIgnoreCase(type)) n++;
        return n;
    }

    /** Puts the player in the first arena of this type that has a free spawn. */
    Assignment assign(String type, UUID player) {
        for (Arena a : arenas.values()) {
            if (!a.type.equalsIgnoreCase(type)) continue;
            int idx = a.freeIndex();
            if (idx >= 0) {
                a.taken.put(idx, player);
                a.dirty = true;
                Assignment as = new Assignment(a, idx);
                byPlayer.put(player, as);
                return as;
            }
        }
        return null;
    }

    Assignment release(UUID player) {
        Assignment as = byPlayer.remove(player);
        if (as != null) as.arena().taken.remove(as.index());
        return as;
    }

    Assignment current(UUID player) {
        return byPlayer.get(player);
    }
}

package com.practiceserver.arenas;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.util.BoundingBox;

/**
 * Arena snapshots, cloning and regeneration (through FastAsyncWorldEdit, in the background).
 *  - /parena save   : take a snapshot of the selected region (the "clean" arena)
 *  - /parena clone  : paste copies of that snapshot with the same spawn points
 *  - slot regen     : after a fight the area around that slot is restored
 *  - auto regen     : every few minutes the whole arena is restored
 */
final class RegenManager {

    private final ArenasPlugin plugin;
    private final Map<String, Clipboard> cache = new ConcurrentHashMap<>();
    private boolean busy;

    RegenManager(ArenasPlugin plugin) {
        this.plugin = plugin;
        startAuto();
    }

    void clearCache() {
        cache.clear();
    }

    // ------------------------------------------------------------ snapshot

    /** Saves the blocks inside the player's WorldEdit selection as this arena's clean copy. */
    void save(Player p, Arena arena) {
        final com.sk89q.worldedit.entity.Player wePlayer = BukkitAdapter.adapt(p);
        final Region selection;
        try {
            LocalSession session = WorldEdit.getInstance().getSessionManager().get(wePlayer);
            selection = session.getSelection(wePlayer.getWorld());
        } catch (IncompleteRegionException e) {
            Text.send(p, "&cSelect the whole arena first: get the wand with &e//wand&c, left click one corner and right click the opposite corner (or use //pos1 and //pos2).");
            return;
        }
        if (busy) {
            Text.send(p, "&cAnother save or clone is still running. Wait for it to finish.");
            return;
        }
        busy = true;

        final BlockVector3 min = selection.getMinimumPoint();
        final BlockVector3 max = selection.getMaximumPoint();
        final com.sk89q.worldedit.world.World weWorld = wePlayer.getWorld();
        final String worldName = p.getWorld().getName();
        final UUID id = p.getUniqueId();
        Text.send(p, "&eSaving the arena snapshot in the background. This can take a while for big arenas...");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String result;
            try {
                CuboidRegion region = new CuboidRegion(weWorld, min, max);
                try (EditSession es = WorldEdit.getInstance().newEditSession(weWorld)) {
                    BlockArrayClipboard clip = new BlockArrayClipboard(region);
                    clip.setOrigin(region.getMinimumPoint());
                    ForwardExtentCopy copy = new ForwardExtentCopy(es, region, clip, region.getMinimumPoint());
                    copy.setCopyingEntities(false);
                    Operations.complete(copy);

                    File dir = new File(plugin.getDataFolder(), "snapshots");
                    dir.mkdirs();
                    File f = new File(dir, arena.id + ".schem");
                    ClipboardFormat format = ClipboardFormats.findByAlias("schem");
                    try (ClipboardWriter writer = format.getWriter(new FileOutputStream(f))) {
                        writer.write(clip);
                    }
                }
                cache.remove(arena.id);
                final int sx = max.getBlockX() - min.getBlockX() + 1;
                final int sy = max.getBlockY() - min.getBlockY() + 1;
                final int sz = max.getBlockZ() - min.getBlockZ() + 1;
                Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.arenas().setSnapshot(arena, worldName, min.getBlockX(), min.getBlockY(), min.getBlockZ(), sx, sy, sz));
                result = "&aSnapshot saved for arena &f" + arena.id + " &7(" + sx + " x " + sy + " x " + sz + ")&a. Regeneration and cloning are ready.";
            } catch (Throwable t) {
                plugin.getLogger().warning("Snapshot failed: " + t);
                result = "&cSnapshot failed: " + t.getClass().getSimpleName() + " (see console)";
            }
            final String msg = result;
            Bukkit.getScheduler().runTask(plugin, () -> {
                busy = false;
                Player pl = Bukkit.getPlayer(id);
                if (pl != null) Text.send(pl, msg);
            });
        });
    }

    // ------------------------------------------------------------ clone

    /** Pastes copies of the arena next to the existing ones, each with the same spawn points. */
    void cloneArena(Player p, Arena src, int count) {
        if (busy) {
            Text.send(p, "&cAnother save or clone is still running. Wait for it to finish.");
            return;
        }
        if (!src.snap || !src.canRegen() || src.sx <= 0) {
            Text.send(p, "&cSave this arena first: select it with //wand and run &e/parena save " + src.id);
            return;
        }
        if (count < 1 || count > 20) {
            Text.send(p, "&cCount must be 1-20.");
            return;
        }
        final World world = Bukkit.getWorld(src.originWorld);
        if (world == null) {
            Text.send(p, "&cThe arena's world is not loaded.");
            return;
        }

        final int gap = plugin.getConfig().getInt("clone.gap", 100);
        final String prefix = src.id.replaceAll("-\\d+$", "");
        int next = 1;
        int maxEnd = src.ox + src.sx;
        for (Arena a : plugin.arenas().all()) {
            if (a.id.startsWith(prefix + "-")) {
                String suffix = a.id.substring(prefix.length() + 1);
                if (suffix.matches("\\d+")) next = Math.max(next, Integer.parseInt(suffix) + 1);
            }
            if (a.hasOrigin && a.originWorld.equals(src.originWorld)) {
                maxEnd = Math.max(maxEnd, a.ox + Math.max(a.sx, src.sx));
            }
        }
        final List<String> ids = new ArrayList<>();
        final List<Integer> xs = new ArrayList<>();
        for (int k = 0; k < count; k++) {
            ids.add(prefix + "-" + (next + k));
            xs.add(maxEnd + gap + k * (src.sx + gap));
        }

        busy = true;
        final UUID pid = p.getUniqueId();
        Text.send(p, "&eCreating " + count + " cop" + (count == 1 ? "y" : "ies") + " of &f" + src.id + "&e in the background...");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int made = 0;
            String error = null;
            try {
                Clipboard clip = clipboard(src.schem);
                if (clip == null) throw new IllegalStateException("snapshot file not found");
                BlockVector3 from = clip.getRegion().getMinimumPoint();
                com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
                for (int k = 0; k < ids.size(); k++) {
                    try (EditSession es = WorldEdit.getInstance().newEditSession(weWorld)) {
                        ForwardExtentCopy copy = new ForwardExtentCopy(clip, clip.getRegion(), from, es,
                                BlockVector3.at(xs.get(k), src.oy, src.oz));
                        copy.setCopyingEntities(false);
                        Operations.complete(copy);
                    }
                    made++;
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("Clone failed: " + t);
                error = t.getClass().getSimpleName();
            }
            final int total = made;
            final String err = error;
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (int k = 0; k < total; k++) register(src, ids.get(k), xs.get(k));
                busy = false;
                Player pl = Bukkit.getPlayer(pid);
                if (pl != null) {
                    if (err == null) Text.send(pl, "&aCreated " + total + " arena(s): &f" + String.join(", ", ids.subList(0, total)) + "&a. Use /parena list.");
                    else Text.send(pl, "&cClone stopped after " + total + " copies (" + err + "). See console.");
                }
            });
        });
    }

    private void register(Arena src, String id, int newX) {
        Arena a = new Arena(id, src.type);
        int dx = newX - src.ox;
        for (SpawnPoint s : src.spawns) {
            a.spawns.add(new SpawnPoint(s.world(), s.x() + dx, s.y(), s.z(), s.yaw(), s.pitch()));
        }
        a.schem = src.schem;
        a.originWorld = src.originWorld;
        a.ox = newX;
        a.oy = src.oy;
        a.oz = src.oz;
        a.sx = src.sx;
        a.sy = src.sy;
        a.sz = src.sz;
        a.snap = true;
        a.hasOrigin = true;
        plugin.arenas().register(a);
    }

    // ------------------------------------------------------------ regen after a fight (one slot)

    void start(Arena arena, int index) {
        if (!arena.canRegen() || index < 0 || index >= arena.spawns.size()) return;
        Location loc = arena.spawns.get(index).toLocation();
        if (loc == null) return;

        arena.regenerating.add(index);
        long delay = plugin.getConfig().getLong("regen.delay-ticks", 20L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            removeEntities(loc.getWorld(), slotBox(loc));
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    restoreSlot(arena, loc);
                } catch (Throwable t) {
                    plugin.getLogger().warning("Arena regen failed for " + arena.id + ": " + t);
                }
                Bukkit.getScheduler().runTask(plugin, () -> arena.regenerating.remove(index));
            });
        }, delay);
    }

    private int radius() {
        return plugin.getConfig().getInt("regen.radius", 60);
    }

    private int below() {
        return plugin.getConfig().getInt("regen.below", 15);
    }

    private int above() {
        return plugin.getConfig().getInt("regen.above", 60);
    }

    private BoundingBox slotBox(Location loc) {
        int r = radius();
        return new BoundingBox(
                loc.getBlockX() - r, loc.getBlockY() - below(), loc.getBlockZ() - r,
                loc.getBlockX() + r + 1, loc.getBlockY() + above() + 1, loc.getBlockZ() + r + 1);
    }

    /** Removes crystals, minecarts, TNT, dropped items and projectiles left behind. */
    private void removeEntities(World w, BoundingBox box) {
        if (w == null) return;
        for (Entity e : w.getNearbyEntities(box)) {
            if (e instanceof Player) continue;
            if (e instanceof EnderCrystal || e instanceof Minecart || e instanceof TNTPrimed
                    || e instanceof Item || e instanceof Projectile || e instanceof ExperienceOrb
                    || e instanceof FallingBlock || e instanceof AreaEffectCloud) {
                e.remove();
            }
        }
    }

    private void restoreSlot(Arena arena, Location loc) throws Exception {
        Clipboard clip = clipboard(arena.schem);
        if (clip == null) {
            plugin.getLogger().warning("Regen: snapshot/schematic '" + arena.schem + "' not found.");
            return;
        }
        BlockVector3 pasteAt = BlockVector3.at(arena.ox, arena.oy, arena.oz);
        Region cr = clip.getRegion();
        BlockVector3 from = arena.snap ? cr.getMinimumPoint() : clip.getOrigin();
        int dx = from.getBlockX() - pasteAt.getBlockX();
        int dy = from.getBlockY() - pasteAt.getBlockY();
        int dz = from.getBlockZ() - pasteAt.getBlockZ();

        int r = radius();
        int minX = loc.getBlockX() - r + dx;
        int maxX = loc.getBlockX() + r + dx;
        int minY = loc.getBlockY() - below() + dy;
        int maxY = loc.getBlockY() + above() + dy;
        int minZ = loc.getBlockZ() - r + dz;
        int maxZ = loc.getBlockZ() + r + dz;

        BlockVector3 cMin = cr.getMinimumPoint();
        BlockVector3 cMax = cr.getMaximumPoint();
        int loX = Math.max(minX, cMin.getBlockX());
        int loY = Math.max(minY, cMin.getBlockY());
        int loZ = Math.max(minZ, cMin.getBlockZ());
        int hiX = Math.min(maxX, cMax.getBlockX());
        int hiY = Math.min(maxY, cMax.getBlockY());
        int hiZ = Math.min(maxZ, cMax.getBlockZ());
        if (loX > hiX || loY > hiY || loZ > hiZ) return;

        Region sub = new CuboidRegion(BlockVector3.at(loX, loY, loZ), BlockVector3.at(hiX, hiY, hiZ));
        try (EditSession es = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(loc.getWorld()))) {
            ForwardExtentCopy copy = new ForwardExtentCopy(clip, sub, from, es, pasteAt);
            copy.setCopyingEntities(false);
            Operations.complete(copy);
        }
    }

    // ------------------------------------------------------------ regen the whole arena

    void fullRegen(Arena arena) {
        if (!arena.canRegen() || !arena.regenerating.isEmpty()) return;
        final World w = Bukkit.getWorld(arena.originWorld);
        if (w == null) return;
        final List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < arena.spawns.size(); i++) {
            arena.regenerating.add(i);
            idx.add(i);
        }
        if (arena.sx > 0) {
            removeEntities(w, new BoundingBox(arena.ox, arena.oy, arena.oz,
                    arena.ox + arena.sx, arena.oy + arena.sy, arena.oz + arena.sz));
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                restoreFull(arena, w);
            } catch (Throwable t) {
                plugin.getLogger().warning("Full regen failed for " + arena.id + ": " + t);
            }
            Bukkit.getScheduler().runTask(plugin, () -> arena.regenerating.removeAll(idx));
        });
    }

    private void restoreFull(Arena arena, World w) throws Exception {
        Clipboard clip = clipboard(arena.schem);
        if (clip == null) {
            plugin.getLogger().warning("Regen: snapshot/schematic '" + arena.schem + "' not found.");
            return;
        }
        BlockVector3 pasteAt = BlockVector3.at(arena.ox, arena.oy, arena.oz);
        BlockVector3 from = arena.snap ? clip.getRegion().getMinimumPoint() : clip.getOrigin();
        try (EditSession es = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(w))) {
            ForwardExtentCopy copy = new ForwardExtentCopy(clip, clip.getRegion(), from, es, pasteAt);
            copy.setCopyingEntities(false);
            Operations.complete(copy);
        }
    }

    // ------------------------------------------------------------ timed regeneration

    private final Map<String, Long> nextReset = new ConcurrentHashMap<>();

    private boolean autoOn() {
        return plugin.getConfig().getBoolean("auto-regen.enabled", false);
    }

    /** Minutes between resets. /parena interval overrides the config value. */
    int intervalMinutes() {
        int def = plugin.getConfig().getInt("auto-regen.interval-minutes", 5);
        return Math.max(1, plugin.lobby().dataInt("settings.interval-minutes", def));
    }

    private long intervalMs() {
        return intervalMinutes() * 60_000L;
    }

    /** Restart every arena's countdown (used after the interval changed). */
    void resetTimers() {
        nextReset.clear();
    }

    /** Seconds until this arena resets, or -1 if timed reset is off. */
    long secondsUntil(Arena a) {
        if (!autoOn()) return -1;
        long now = System.currentTimeMillis();
        long next = nextReset.getOrDefault(a.id, now + intervalMs());
        return Math.max(0L, (next - now) / 1000L);
    }

    /** Seconds until the next reset of the soonest arena of this type, or -1 if timed reset is off. */
    long secondsUntilReset(String type) {
        if (!autoOn()) return -1;
        long now = System.currentTimeMillis();
        long best = -1;
        for (Arena a : plugin.arenas().all()) {
            if (!a.type.equalsIgnoreCase(type) || !a.canRegen()) continue;
            long next = nextReset.getOrDefault(a.id, now + intervalMs());
            long left = Math.max(0L, (next - now) / 1000L);
            if (best < 0 || left < best) best = left;
        }
        return best;
    }

    private void startAuto() {
        if (!autoOn()) return;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        plugin.getLogger().info("Timed arena reset every " + intervalMinutes() + " minutes.");
    }

    private void tick() {
        long now = System.currentTimeMillis();
        boolean kick = "kick".equalsIgnoreCase(plugin.getConfig().getString("auto-regen.players", "kick"));
        int noJoin = plugin.getConfig().getInt("auto-regen.no-join-seconds", 10);
        List<Integer> warn = plugin.getConfig().getIntegerList("auto-regen.warn-seconds");

        for (Arena a : plugin.arenas().all()) {
            if (!a.canRegen()) continue;
            long next = nextReset.computeIfAbsent(a.id, k -> now + intervalMs());
            long left = (next - now + 999L) / 1000L;

            if (left > 0) {
                a.locked = a.dirty && left <= noJoin;
                if (!a.taken.isEmpty() && warn.contains((int) left)) {
                    for (UUID u : new ArrayList<>(a.taken.values())) {
                        Player pl = Bukkit.getPlayer(u);
                        if (pl != null) {
                            Text.send(pl, plugin.msg("regen-warning", "&eThis arena resets in &f{s} &eseconds.")
                                    .replace("{s}", String.valueOf(left)));
                        }
                    }
                }
                continue;
            }

            // time is up
            if (!a.regenerating.isEmpty()) continue;
            if (!a.dirty) {
                nextReset.put(a.id, now + intervalMs());
                a.locked = false;
                continue;
            }
            a.locked = true;
            if (!a.taken.isEmpty()) {
                if (!kick) continue; // wait until the arena is empty
                for (UUID u : new ArrayList<>(a.taken.values())) {
                    Player pl = Bukkit.getPlayer(u);
                    if (pl == null) continue;
                    Text.send(pl, plugin.msg("regen-kick", "&eThe arena is being reset. You were sent back to the lobby."));
                    plugin.sessions().leave(pl);
                }
                continue; // restored on a following tick, once the slots are free
            }
            fullRegen(a);
            a.dirty = false;
            a.locked = false;
            nextReset.put(a.id, now + intervalMs());
        }
    }

    // ------------------------------------------------------------ snapshot loading

    private Clipboard clipboard(String name) throws Exception {
        Clipboard cached = cache.get(name);
        if (cached != null) return cached;
        File f = find(name);
        if (f == null) return null;
        ClipboardFormat format = ClipboardFormats.findByFile(f);
        if (format == null) return null;
        try (ClipboardReader reader = format.getReader(new FileInputStream(f))) {
            Clipboard clip = reader.read();
            cache.put(name, clip);
            return clip;
        }
    }

    private File find(String name) {
        File snapshots = new File(plugin.getDataFolder(), "snapshots");
        for (String ext : new String[]{".schem", ""}) {
            File f = new File(snapshots, name + ext);
            if (f.isFile()) return f;
        }
        File root = plugin.getDataFolder().getParentFile().getParentFile();
        List<String> folders = plugin.getConfig().getStringList("regen.schematic-folders");
        for (String folder : folders) {
            for (String ext : new String[]{"", ".schem", ".schematic"}) {
                File f = new File(new File(root, folder), name + ext);
                if (f.isFile()) return f;
            }
        }
        return null;
    }
}

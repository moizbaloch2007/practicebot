package com.practiceserver.arenas;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.Map;
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
 * Restores the area around a player slot from the arena's schematic after a fight.
 * The restore runs through FastAsyncWorldEdit in the background (no lag).
 */
final class RegenManager {

    private final ArenasPlugin plugin;
    private final Map<String, Clipboard> cache = new ConcurrentHashMap<>();

    RegenManager(ArenasPlugin plugin) {
        this.plugin = plugin;
    }

    void clearCache() {
        cache.clear();
    }

    // ------------------------------------------------------------ regen after a fight

    void start(Arena arena, int index) {
        if (!arena.canRegen() || index < 0 || index >= arena.spawns.size()) return;
        Location loc = arena.spawns.get(index).toLocation();
        if (loc == null) return;

        arena.regenerating.add(index);
        long delay = plugin.getConfig().getLong("regen.delay-ticks", 20L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            removeEntities(loc);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    restore(arena, loc);
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

    /** Removes crystals, minecarts, TNT, dropped items and projectiles left behind. */
    private void removeEntities(Location loc) {
        World w = loc.getWorld();
        if (w == null) return;
        int r = radius();
        BoundingBox box = new BoundingBox(
                loc.getBlockX() - r, loc.getBlockY() - below(), loc.getBlockZ() - r,
                loc.getBlockX() + r + 1, loc.getBlockY() + above() + 1, loc.getBlockZ() + r + 1);
        for (Entity e : w.getNearbyEntities(box)) {
            if (e instanceof Player) continue;
            if (e instanceof EnderCrystal || e instanceof Minecart || e instanceof TNTPrimed
                    || e instanceof Item || e instanceof Projectile || e instanceof ExperienceOrb
                    || e instanceof FallingBlock || e instanceof AreaEffectCloud) {
                e.remove();
            }
        }
    }

    private void restore(Arena arena, Location loc) throws Exception {
        Clipboard clip = clipboard(arena.schem);
        if (clip == null) {
            plugin.getLogger().warning("Regen: schematic '" + arena.schem + "' not found.");
            return;
        }
        BlockVector3 pasteAt = BlockVector3.at(arena.ox, arena.oy, arena.oz);
        BlockVector3 origin = clip.getOrigin();
        int dx = origin.getBlockX() - pasteAt.getBlockX();
        int dy = origin.getBlockY() - pasteAt.getBlockY();
        int dz = origin.getBlockZ() - pasteAt.getBlockZ();

        int r = radius();
        int minX = loc.getBlockX() - r + dx;
        int maxX = loc.getBlockX() + r + dx;
        int minY = loc.getBlockY() - below() + dy;
        int maxY = loc.getBlockY() + above() + dy;
        int minZ = loc.getBlockZ() - r + dz;
        int maxZ = loc.getBlockZ() + r + dz;

        Region cr = clip.getRegion();
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
            ForwardExtentCopy copy = new ForwardExtentCopy(clip, sub, origin, es, pasteAt);
            copy.setCopyingEntities(false);
            Operations.complete(copy);
        }
    }

    // ------------------------------------------------------------ paste a whole arena

    /** Pastes the schematic at the player's position and remembers it for regeneration. */
    void paste(Player p, Arena arena, String schem) {
        final Location at = p.getLocation();
        final World world = at.getWorld();
        final BlockVector3 pos = BlockVector3.at(at.getBlockX(), at.getBlockY(), at.getBlockZ());
        final java.util.UUID id = p.getUniqueId();
        Text.send(p, "&eLoading and pasting &f" + schem + "&e in the background...");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String result;
            try {
                Clipboard clip = clipboard(schem);
                if (clip == null) {
                    result = "&cSchematic not found: " + schem;
                } else {
                    try (EditSession es = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
                        Operations.complete(new ClipboardHolder(clip).createPaste(es).to(pos)
                                .ignoreAirBlocks(false).build());
                    }
                    Bukkit.getScheduler().runTask(plugin, () ->
                            plugin.arenas().setOrigin(arena, schem, world.getName(),
                                    pos.getBlockX(), pos.getBlockY(), pos.getBlockZ()));
                    result = "&aPasted and linked to arena &f" + arena.id + "&a. Regeneration is ready.";
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("Paste failed: " + t);
                result = "&cPaste failed: " + t.getClass().getSimpleName() + " (see console)";
            }
            final String msg = result;
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player pl = Bukkit.getPlayer(id);
                if (pl != null) Text.send(pl, msg);
            });
        });
    }

    // ------------------------------------------------------------ schematic loading

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

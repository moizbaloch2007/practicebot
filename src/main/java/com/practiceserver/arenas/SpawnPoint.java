package com.practiceserver.arenas;

import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

record SpawnPoint(String world, double x, double y, double z, float yaw, float pitch) {

    static SpawnPoint of(Location l) {
        return new SpawnPoint(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    static SpawnPoint parse(String s) {
        String[] p = s.split(";");
        if (p.length < 6) return null;
        try {
            return new SpawnPoint(p[0], Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                    Double.parseDouble(p[3]), Float.parseFloat(p[4]), Float.parseFloat(p[5]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    String serialize() {
        return String.format(Locale.US, "%s;%.2f;%.2f;%.2f;%.1f;%.1f", world, x, y, z, yaw, pitch);
    }

    Location toLocation() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }
}

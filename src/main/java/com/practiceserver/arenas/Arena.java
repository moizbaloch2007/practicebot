package com.practiceserver.arenas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One arena copy. Every spawn point is one player slot. */
final class Arena {
    final String id;
    final String type;
    final List<SpawnPoint> spawns = new ArrayList<>();
    final Map<Integer, UUID> taken = new HashMap<>();
    /** Slots that are being restored after a fight. They can not be picked until done. */
    final Set<Integer> regenerating = new HashSet<>();

    /** Regeneration source: the schematic and the block where it was pasted. */
    String schem = "";
    String originWorld = "";
    int ox;
    int oy;
    int oz;
    boolean hasOrigin;
    /** true = schem is a snapshot made by /parena save, origin is its minimum corner. */
    boolean snap;
    /** Someone used this arena since its last full reset. */
    boolean dirty;
    /** A reset is about to happen, nobody new may join. */
    boolean locked;
    int sx;
    int sy;
    int sz;

    Arena(String id, String type) {
        this.id = id;
        this.type = type;
    }

    int freeIndex() {
        if (locked) return -1;
        for (int i = 0; i < spawns.size(); i++) {
            if (!taken.containsKey(i) && !regenerating.contains(i)) return i;
        }
        return -1;
    }

    boolean canRegen() {
        return hasOrigin && schem != null && !schem.isEmpty();
    }
}

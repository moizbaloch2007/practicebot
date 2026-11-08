package com.practiceserver.arenas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One arena copy. Every spawn point is one player slot. */
final class Arena {
    final String id;
    final String type;
    final List<SpawnPoint> spawns = new ArrayList<>();
    final Map<Integer, UUID> taken = new HashMap<>();

    Arena(String id, String type) {
        this.id = id;
        this.type = type;
    }

    int freeIndex() {
        for (int i = 0; i < spawns.size(); i++) {
            if (!taken.containsKey(i)) return i;
        }
        return -1;
    }
}

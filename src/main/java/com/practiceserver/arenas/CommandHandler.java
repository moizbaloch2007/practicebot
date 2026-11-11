package com.practiceserver.arenas;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

final class CommandHandler implements CommandExecutor, TabCompleter {

    private final ArenasPlugin plugin;

    CommandHandler(ArenasPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "practicecore" -> core(sender, args);
            case "leave" -> player(sender, p -> plugin.sessions().leave(p));
            case "spawn" -> player(sender, p -> {
                if (plugin.sessions().get(p.getUniqueId()) != null) plugin.sessions().leave(p);
                else if (plugin.lobby().teleport(p)) plugin.lobby().giveItemsLater(p, 3L);
                else Text.send(p, "&cThe lobby spawn is not set. An admin must run &e/parena setlobby&c.");
            });
            case "fight" -> player(sender, p -> plugin.sessions().fight(p));
            case "parena" -> admin(sender, args);
            default -> { return false; }
        }
        return true;
    }

    private interface PlayerAction { void run(Player p); }

    private void player(CommandSender s, PlayerAction a) {
        if (s instanceof Player p) a.run(p);
        else Text.send(s, "&cOnly players can use this.");
    }

    /** /pc, /practicecore */
    private void core(CommandSender s, String[] a) {
        if (a.length == 0) {
            player(s, p -> plugin.menus().openHub(p));
            return;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "play" -> player(s, p -> plugin.menus().openModes(p, false));
            case "kit", "kits", "editor" -> player(s, p -> plugin.menus().openModes(p, true));
            case "fight", "start" -> player(s, p -> plugin.sessions().fight(p));
            case "leave", "lobby" -> player(s, p -> plugin.sessions().leave(p));
            case "admin" -> {
                if (!s.hasPermission("practicecore.admin")) {
                    Text.send(s, "&cYou do not have permission.");
                    return;
                }
                admin(s, Arrays.copyOfRange(a, 1, a.length));
            }
            default -> {
                Text.send(s, "&e/pc &7- open the menu");
                Text.send(s, "&e/pc kit &7- kit editor");
                Text.send(s, "&e/pc fight &7- start the fight");
                Text.send(s, "&e/pc leave &7- back to the lobby");
            }
        }
    }

    private void admin(CommandSender s, String[] a) {
        if (!s.hasPermission("practicecore.admin")) {
            Text.send(s, "&cYou do not have permission.");
            return;
        }
        if (a.length == 0) {
            Text.send(s, "&e/parena setlobby &7- set the lobby spawn where you stand");
            Text.send(s, "&e/parena create <type> <id> &7- new arena of a type");
            Text.send(s, "&e/parena addspawn <id> &7- add your position as a player slot");
            Text.send(s, "&e/parena slots <id> &7- list the slots of an arena");
            Text.send(s, "&e/parena setspawn <id> <slot> &7- move a slot to where you stand");
            Text.send(s, "&e/parena removespawn <id> <slot> &7- delete a slot");
            Text.send(s, "&e/parena sync <id> &7- copy this arena's slots to its clones");
            Text.send(s, "&e/parena interval [minutes] &7- show or change the reset time");
            Text.send(s, "&e/parena status &7- arenas, free slots and time until reset");
            Text.send(s, "&e/parena remove <id> &7- delete an arena");
            Text.send(s, "&e/parena list &7- show arenas and slots");
            Text.send(s, "&e/parena tp <id> [slot] &7- teleport to a spawn");
            Text.send(s, "&e/parena save <id> &7- save your //wand selection as the clean copy of this arena");
            Text.send(s, "&e/parena clone <id> [count] &7- make copies with the same spawn points");
            Text.send(s, "&e/parena regen <id> [slot] &7- restore one slot now (test)");
            Text.send(s, "&e/parena regenall <id> &7- restore the whole arena now");
            Text.send(s, "&e/parena reload &7- reload config and arenas");
            return;
        }
        ArenaManager am = plugin.arenas();
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "slots" -> {
                if (a.length < 2) { Text.send(s, "&cUsage: /parena slots <id>"); return; }
                Arena ar = am.get(a[1]);
                if (ar == null) { Text.send(s, "&cNo such arena."); return; }
                if (ar.spawns.isEmpty()) { Text.send(s, "&7No slots yet. Use /parena addspawn " + ar.id); return; }
                for (int i = 0; i < ar.spawns.size(); i++) {
                    SpawnPoint sp = ar.spawns.get(i);
                    String state = ar.taken.containsKey(i) ? "&cin use" : (ar.regenerating.contains(i) ? "&eresetting" : "&afree");
                    Text.send(s, "&e#" + (i + 1) + " &7" + (int) sp.x() + ", " + (int) sp.y() + ", " + (int) sp.z() + " " + state);
                }
            }
            case "setspawn" -> {
                if (!(s instanceof Player p)) { Text.send(s, "&cOnly players can use this."); return; }
                if (a.length < 3 || !a[2].matches("\\d+")) { Text.send(s, "&cUsage: /parena setspawn <id> <slot>"); return; }
                Arena ar = am.get(a[1]);
                if (ar == null) { Text.send(s, "&cNo such arena."); return; }
                int slot = Integer.parseInt(a[2]);
                if (slot < 1 || slot > ar.spawns.size()) { Text.send(s, "&cSlot must be 1-" + ar.spawns.size()); return; }
                if (!ar.taken.isEmpty() || !ar.regenerating.isEmpty()) { Text.send(s, "&cPlayers are in this arena or it is resetting. Try again in a moment."); return; }
                ar.spawns.set(slot - 1, SpawnPoint.of(p.getLocation()));
                am.save();
                Text.send(s, "&aSlot " + slot + " of &f" + ar.id + " &amoved here. Run &e/parena sync " + ar.id + " &ato update its clones.");
            }
            case "removespawn" -> {
                if (a.length < 3 || !a[2].matches("\\d+")) { Text.send(s, "&cUsage: /parena removespawn <id> <slot>"); return; }
                Arena ar = am.get(a[1]);
                if (ar == null) { Text.send(s, "&cNo such arena."); return; }
                int slot = Integer.parseInt(a[2]);
                if (slot < 1 || slot > ar.spawns.size()) { Text.send(s, "&cSlot must be 1-" + ar.spawns.size()); return; }
                if (!ar.taken.isEmpty() || !ar.regenerating.isEmpty()) { Text.send(s, "&cPlayers are in this arena or it is resetting. Try again in a moment."); return; }
                ar.spawns.remove(slot - 1);
                am.save();
                Text.send(s, "&aSlot " + slot + " removed from &f" + ar.id + "&a (" + ar.spawns.size() + " left, later slots moved up by one). Run &e/parena sync " + ar.id + " &ato update its clones.");
            }
            case "sync" -> {
                if (a.length < 2) { Text.send(s, "&cUsage: /parena sync <id>  (copies this arena's slots to its clones)"); return; }
                Arena src = am.get(a[1]);
                if (src == null) { Text.send(s, "&cNo such arena."); return; }
                if (!src.hasOrigin || src.schem.isEmpty()) { Text.send(s, "&cSave this arena first with /parena save."); return; }
                int updated = 0;
                int skipped = 0;
                for (Arena t : am.all()) {
                    if (t == src || !t.hasOrigin || !t.schem.equals(src.schem)) continue;
                    if (!t.taken.isEmpty() || !t.regenerating.isEmpty()) { skipped++; continue; }
                    int dx = t.ox - src.ox;
                    int dy = t.oy - src.oy;
                    int dz = t.oz - src.oz;
                    t.spawns.clear();
                    for (SpawnPoint sp : src.spawns) {
                        t.spawns.add(new SpawnPoint(t.originWorld, sp.x() + dx, sp.y() + dy, sp.z() + dz, sp.yaw(), sp.pitch()));
                    }
                    updated++;
                }
                am.save();
                Text.send(s, "&aUpdated the slots of " + updated + " clone(s)." + (skipped > 0 ? " &e" + skipped + " skipped because players are inside." : ""));
            }
            case "interval" -> {
                if (plugin.regen() == null) { Text.send(s, "&cTimed reset needs FastAsyncWorldEdit and regen.enabled: true."); return; }
                if (a.length < 2) {
                    Text.send(s, "&7Arenas reset every &f" + plugin.regen().intervalMinutes() + " &7minutes. Change it with &e/parena interval <minutes>");
                    return;
                }
                if (!a[1].matches("\\d+")) { Text.send(s, "&cUsage: /parena interval <minutes>"); return; }
                int minutes = Integer.parseInt(a[1]);
                if (minutes < 1 || minutes > 1440) { Text.send(s, "&cMinutes must be 1-1440."); return; }
                plugin.lobby().dataSet("settings.interval-minutes", minutes);
                plugin.regen().resetTimers();
                Text.send(s, "&aArenas now reset every &f" + minutes + " &aminutes. The countdowns were restarted.");
            }
            case "status" -> {
                if (am.all().isEmpty()) { Text.send(s, "&7No arenas yet."); return; }
                for (Arena ar : am.all()) {
                    int free = ar.locked ? 0 : Math.max(0, ar.spawns.size() - ar.taken.size() - ar.regenerating.size());
                    String reset = "off";
                    if (plugin.regen() != null && ar.canRegen()) {
                        long secs = plugin.regen().secondsUntil(ar);
                        if (secs >= 0) reset = (secs / 60) + ":" + String.format("%02d", secs % 60);
                    }
                    Text.send(s, "&e" + ar.id + " &7(" + ar.type + ") free &f" + free + "&7/&f" + ar.spawns.size()
                            + " &7next reset &f" + reset + (ar.dirty ? " &7(used)" : ""));
                }
            }
            case "setlobby" -> {
                if (!(s instanceof Player p)) { Text.send(s, "&cOnly players can use this."); return; }
                plugin.lobby().setSpawn(p.getLocation());
                Text.send(s, "&aLobby spawn set. Players land here on join, respawn, /spawn and /leave.");
            }
            case "create" -> {
                if (a.length < 3) { Text.send(s, "&cUsage: /parena create <type> <id>"); return; }
                String type = a[1].toLowerCase(Locale.ROOT);
                String id = a[2];
                if (!id.matches("[A-Za-z0-9_-]+")) { Text.send(s, "&cUse only letters, numbers, - and _ in the id."); return; }
                ConfigurationSection types = plugin.getConfig().getConfigurationSection("arena-types");
                if (types == null || !types.contains(type)) { Text.send(s, "&cUnknown type. Types are listed under arena-types in config.yml."); return; }
                Arena created = am.create(type, id);
                if (created == null) Text.send(s, "&cAn arena with that id already exists.");
                else Text.send(s, "&aCreated arena &f" + id + "&a. Stand on a spawn and run &e/parena addspawn " + id);
            }
            case "addspawn" -> {
                if (!(s instanceof Player p)) { Text.send(s, "&cOnly players can use this."); return; }
                if (a.length < 2) { Text.send(s, "&cUsage: /parena addspawn <id>"); return; }
                Arena ar = am.get(a[1]);
                if (ar == null) { Text.send(s, "&cNo such arena."); return; }
                ar.spawns.add(SpawnPoint.of(p.getLocation()));
                am.save();
                Text.send(s, "&aAdded slot #" + ar.spawns.size() + " to &f" + ar.id);
                SpawnPoint np = ar.spawns.get(ar.spawns.size() - 1);
                double min = Double.MAX_VALUE;
                for (int i = 0; i < ar.spawns.size() - 1; i++) {
                    SpawnPoint o = ar.spawns.get(i);
                    if (!o.world().equals(np.world())) continue;
                    min = Math.min(min, Math.hypot(o.x() - np.x(), o.z() - np.z()));
                }
                int need = plugin.getConfig().getInt("regen.radius", 60) * 2;
                if (min < need) {
                    Text.send(s, "&eWarning: this slot is only " + (int) min + " blocks from another slot. Keep slots at least "
                            + need + " blocks apart (regen.radius x 2) so restoring one slot never touches another player.");
                }
            }
            case "remove" -> {
                if (a.length < 2) { Text.send(s, "&cUsage: /parena remove <id>"); return; }
                Text.send(s, am.remove(a[1]) ? "&aRemoved." : "&cNo such arena.");
            }
            case "list" -> {
                if (am.all().isEmpty()) { Text.send(s, "&7No arenas yet."); return; }
                for (Arena ar : am.all()) {
                    Text.send(s, "&e" + ar.id + " &7(" + ar.type + ") slots: &f" + ar.spawns.size() + " &7in use: &f" + ar.taken.size()
                            + " &7regen: " + (ar.canRegen() ? "&a" + ar.schem : "&cnot linked"));
                }
            }
            case "tp" -> {
                if (!(s instanceof Player p)) { Text.send(s, "&cOnly players can use this."); return; }
                if (a.length < 2) { Text.send(s, "&cUsage: /parena tp <id> [slot]"); return; }
                Arena ar = am.get(a[1]);
                if (ar == null || ar.spawns.isEmpty()) { Text.send(s, "&cNo such arena or no slots."); return; }
                int slot = 1;
                if (a.length > 2 && a[2].matches("\\d+")) slot = Integer.parseInt(a[2]);
                if (slot < 1 || slot > ar.spawns.size()) { Text.send(s, "&cSlot must be 1-" + ar.spawns.size()); return; }
                Location l = ar.spawns.get(slot - 1).toLocation();
                if (l == null) { Text.send(s, "&cThat world is not loaded."); return; }
                p.teleport(l);
            }
            case "save" -> {
                if (!(s instanceof Player p)) { Text.send(s, "&cOnly players can use this."); return; }
                if (plugin.regen() == null) { Text.send(s, "&cNeeds FastAsyncWorldEdit and regen.enabled: true."); return; }
                if (a.length < 2) { Text.send(s, "&cUsage: /parena save <id>  (select the arena with //wand first)"); return; }
                Arena ar = am.get(a[1]);
                if (ar == null) { Text.send(s, "&cNo such arena. Create it first with /parena create."); return; }
                plugin.regen().save(p, ar);
            }
            case "clone" -> {
                if (!(s instanceof Player p)) { Text.send(s, "&cOnly players can use this."); return; }
                if (plugin.regen() == null) { Text.send(s, "&cNeeds FastAsyncWorldEdit and regen.enabled: true."); return; }
                if (a.length < 2) { Text.send(s, "&cUsage: /parena clone <id> [count]"); return; }
                Arena ar = am.get(a[1]);
                if (ar == null) { Text.send(s, "&cNo such arena."); return; }
                int count = 1;
                if (a.length > 2 && a[2].matches("\\d+")) count = Integer.parseInt(a[2]);
                plugin.regen().cloneArena(p, ar, count);
            }
            case "regenall" -> {
                if (plugin.regen() == null) { Text.send(s, "&cNeeds FastAsyncWorldEdit and regen.enabled: true."); return; }
                if (a.length < 2) { Text.send(s, "&cUsage: /parena regenall <id>"); return; }
                Arena ar = am.get(a[1]);
                if (ar == null) { Text.send(s, "&cNo such arena."); return; }
                if (!ar.canRegen()) { Text.send(s, "&cThis arena has no snapshot yet. Use /parena save."); return; }
                if (!ar.taken.isEmpty()) { Text.send(s, "&cPlayers are inside this arena right now."); return; }
                plugin.regen().fullRegen(ar);
                Text.send(s, "&aRestoring the whole arena &f" + ar.id + "&a...");
            }
            case "regen" -> {
                if (plugin.regen() == null) { Text.send(s, "&cNeeds FastAsyncWorldEdit and regen.enabled: true."); return; }
                if (a.length < 2) { Text.send(s, "&cUsage: /parena regen <id> [slot]"); return; }
                Arena ar = am.get(a[1]);
                if (ar == null || ar.spawns.isEmpty()) { Text.send(s, "&cNo such arena or no slots."); return; }
                if (!ar.canRegen()) { Text.send(s, "&cThis arena is not linked to a schematic. Use /parena paste or /parena link."); return; }
                int slot = 1;
                if (a.length > 2 && a[2].matches("\\d+")) slot = Integer.parseInt(a[2]);
                if (slot < 1 || slot > ar.spawns.size()) { Text.send(s, "&cSlot must be 1-" + ar.spawns.size()); return; }
                plugin.regen().start(ar, slot - 1);
                Text.send(s, "&aRestoring slot " + slot + " of &f" + ar.id + "&a...");
            }
            case "reload" -> {
                plugin.reloadConfig();
                am.load();
                plugin.layouts().load();
                plugin.lobby().load();
                if (plugin.regen() != null) plugin.regen().clearCache();
                Text.send(s, "&aReloaded.");
            }
            default -> Text.send(s, "&cUnknown subcommand. Type /parena for help.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        if (name.equals("practicecore") && args.length == 1) {
            for (String x : new String[]{"play", "kit", "fight", "leave", "admin"})
                if (x.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(x);
            return out;
        }
        if (!name.equals("parena")) return out;
        if (args.length == 1) {
            for (String x : new String[]{"setlobby", "create", "addspawn", "slots", "setspawn", "removespawn", "sync", "interval", "status", "remove", "list", "tp", "save", "clone", "regen", "regenall", "reload"})
                if (x.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(x);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            ConfigurationSection types = plugin.getConfig().getConfigurationSection("arena-types");
            if (types != null) out.addAll(types.getKeys(false));
        } else if (args.length == 2) {
            for (Arena ar : plugin.arenas().all()) out.add(ar.id);
        }
        return out;
    }
}

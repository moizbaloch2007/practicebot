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
            for (String x : new String[]{"setlobby", "create", "addspawn", "remove", "list", "tp", "save", "clone", "regen", "regenall", "reload"})
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

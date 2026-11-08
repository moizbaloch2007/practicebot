package com.practiceserver.arenas;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
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
            case "practice" -> {
                if (sender instanceof Player p) plugin.menus().openModes(p);
                else Text.send(sender, "&cOnly players can use this.");
            }
            case "leave" -> {
                if (sender instanceof Player p) leave(p);
                else Text.send(sender, "&cOnly players can use this.");
            }
            case "parena" -> admin(sender, args);
            default -> { return false; }
        }
        return true;
    }

    private void leave(Player p) {
        plugin.arenas().release(p.getUniqueId());
        p.getInventory().clear();
        String c = plugin.getConfig().getString("lobby-command", "warp lobby {player}");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.replace("{player}", p.getName()));
    }

    private void admin(CommandSender s, String[] a) {
        if (a.length == 0) {
            Text.send(s, "&e/parena create <type> <id> &7- new arena of a type");
            Text.send(s, "&e/parena addspawn <id> &7- add your position as a player slot");
            Text.send(s, "&e/parena remove <id> &7- delete an arena");
            Text.send(s, "&e/parena list &7- show arenas and slots");
            Text.send(s, "&e/parena tp <id> [slot] &7- teleport to a spawn");
            Text.send(s, "&e/parena reload &7- reload config and arenas");
            return;
        }
        ArenaManager am = plugin.arenas();
        switch (a[0].toLowerCase(Locale.ROOT)) {
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
            }
            case "remove" -> {
                if (a.length < 2) { Text.send(s, "&cUsage: /parena remove <id>"); return; }
                Text.send(s, am.remove(a[1]) ? "&aRemoved." : "&cNo such arena.");
            }
            case "list" -> {
                if (am.all().isEmpty()) { Text.send(s, "&7No arenas yet."); return; }
                for (Arena ar : am.all()) {
                    Text.send(s, "&e" + ar.id + " &7(" + ar.type + ") slots: &f" + ar.spawns.size() + " &7in use: &f" + ar.taken.size());
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
            case "reload" -> {
                plugin.reloadConfig();
                am.load();
                Text.send(s, "&aReloaded.");
            }
            default -> Text.send(s, "&cUnknown subcommand. Type /parena for help.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (!cmd.getName().equalsIgnoreCase("parena")) return out;
        if (args.length == 1) {
            for (String x : new String[]{"create", "addspawn", "remove", "list", "tp", "reload"})
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

package com.practiceserver.arenas;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ArenasPlugin extends JavaPlugin {

    private ArenaManager arenas;
    private KitManager kits;
    private MenuManager menus;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        arenas = new ArenaManager(this);
        kits = new KitManager(this);
        menus = new MenuManager(this);
        arenas.load();

        getServer().getPluginManager().registerEvents(menus, this);
        getServer().getPluginManager().registerEvents(new SessionListener(this), this);

        CommandHandler handler = new CommandHandler(this);
        for (String name : new String[]{"practice", "leave", "parena"}) {
            PluginCommand pc = getCommand(name);
            if (pc != null) {
                pc.setExecutor(handler);
                pc.setTabCompleter(handler);
            }
        }
        getLogger().info("PracticeArenas enabled. Arenas loaded: " + arenas.all().size());
    }

    @Override
    public void onDisable() {
        if (arenas != null) arenas.save();
    }

    ArenaManager arenas() { return arenas; }
    KitManager kits() { return kits; }
    MenuManager menus() { return menus; }

    String msg(String key, String def) {
        String prefix = getConfig().getString("messages.prefix", "&8[&bPractice&8] ");
        return prefix + getConfig().getString("messages." + key, def);
    }
}

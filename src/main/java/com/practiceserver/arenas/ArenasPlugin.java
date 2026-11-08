package com.practiceserver.arenas;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** PracticeCore main class. */
public final class ArenasPlugin extends JavaPlugin {

    private ArenaManager arenas;
    private KitManager kits;
    private LayoutManager layouts;
    private MenuManager menus;
    private KitEditor editor;
    private SessionManager sessions;
    private BotCleaner cleaner;
    private RegenManager regen;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        arenas = new ArenaManager(this);
        layouts = new LayoutManager(this);
        kits = new KitManager(this);
        menus = new MenuManager(this);
        editor = new KitEditor(this);
        sessions = new SessionManager(this);
        cleaner = new BotCleaner(this);
        arenas.load();
        layouts.load();

        boolean hasWorldEdit = getServer().getPluginManager().getPlugin("FastAsyncWorldEdit") != null
                || getServer().getPluginManager().getPlugin("WorldEdit") != null;
        if (hasWorldEdit && getConfig().getBoolean("regen.enabled", true)) {
            try {
                regen = new RegenManager(this);
                getLogger().info("Arena regeneration enabled.");
            } catch (Throwable t) {
                regen = null;
                getLogger().warning("Arena regeneration is off: " + t);
            }
        } else {
            getLogger().warning("Arena regeneration is off (FastAsyncWorldEdit not found or regen.enabled is false).");
        }

        getServer().getPluginManager().registerEvents(menus, this);
        getServer().getPluginManager().registerEvents(editor, this);
        getServer().getPluginManager().registerEvents(new SessionListener(this), this);

        CommandHandler handler = new CommandHandler(this);
        for (String name : new String[]{"practicecore", "leave", "fight", "parena"}) {
            PluginCommand pc = getCommand(name);
            if (pc != null) {
                pc.setExecutor(handler);
                pc.setTabCompleter(handler);
            }
        }
        getLogger().info("PracticeCore enabled. Arenas loaded: " + arenas.all().size());
    }

    @Override
    public void onDisable() {
        if (sessions != null) sessions.endAll();
        if (arenas != null) arenas.save();
    }

    ArenaManager arenas() { return arenas; }
    KitManager kits() { return kits; }
    LayoutManager layouts() { return layouts; }
    MenuManager menus() { return menus; }
    KitEditor editor() { return editor; }
    SessionManager sessions() { return sessions; }
    BotCleaner cleaner() { return cleaner; }
    RegenManager regen() { return regen; }

    String msg(String key, String def) {
        String prefix = getConfig().getString("messages.prefix", "&8[&bPractice&8] ");
        return prefix + getConfig().getString("messages." + key, def);
    }
}

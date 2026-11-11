package com.practiceserver.arenas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Hub -> mode -> difficulty -> arena menus.
 * menus.style: dialog (Minecraft dialogs, default) or chest (inventory menus).
 */
final class MenuManager implements Listener {

    enum Kind { HUB, MODE, DIFF, TYPE }

    static final class Holder implements InventoryHolder {
        final Kind kind;
        final boolean edit;
        final String mode;
        final String diff;
        final Map<Integer, String> slots = new HashMap<>();
        Inventory inventory;

        Holder(Kind kind, boolean edit, String mode, String diff) {
            this.kind = kind;
            this.edit = edit;
            this.mode = mode;
            this.diff = diff;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final ArenasPlugin plugin;
    private DialogMenus dialogs;
    private boolean dialogsFailed;

    MenuManager(ArenasPlugin plugin) {
        this.plugin = plugin;
    }

    /** Dialogs need Paper 1.21.7+. If they fail the chest menu is used instead. */
    private DialogMenus dialogs() {
        if (dialogsFailed) return null;
        if (!"dialog".equalsIgnoreCase(plugin.getConfig().getString("menus.style", "dialog"))) return null;
        if (dialogs == null) {
            try {
                dialogs = new DialogMenus(plugin);
            } catch (Throwable t) {
                dialogsFailed = true;
                plugin.getLogger().warning("Dialogs are not available, using chest menus: " + t);
                return null;
            }
        }
        return dialogs;
    }

    private boolean tryDialog(Runnable r) {
        if (dialogs() == null) return false;
        try {
            r.run();
            return true;
        } catch (Throwable t) {
            dialogsFailed = true;
            plugin.getLogger().warning("Dialogs failed, using chest menus: " + t);
            return false;
        }
    }

    // ------------------------------------------------------------ shared click logic

    void hubClicked(Player p, String key) {
        switch (key) {
            case "play" -> openModes(p, false);
            case "editor" -> openModes(p, true);
            case "leave" -> {
                p.closeInventory();
                plugin.sessions().leave(p);
            }
            default -> { }
        }
    }

    void modeClicked(Player p, String key, boolean edit) {
        String node = plugin.getConfig().getString("modes." + key + ".permission", "");
        if (node != null && !node.isEmpty() && !p.hasPermission(node)) {
            p.closeInventory();
            Text.send(p, plugin.msg("no-permission", "&cYou do not have permission for this mode."));
            return;
        }
        if (edit) plugin.editor().open(p, key);
        else openDifficulty(p, key);
    }

    void diffClicked(Player p, String mode, String diff) {
        openTypes(p, mode, diff);
    }

    void typeClicked(Player p, String mode, String diff, String type) {
        plugin.sessions().beginPrep(p, mode, diff, type);
    }

    /** Lore lines about an arena type: arena count, players and time until reset. */
    List<String> arenaInfo(String type) {
        List<String> extra = new ArrayList<>();
        extra.add("");
        ArenaManager am = plugin.arenas();
        int cap = am.capacity(type);
        if (cap == 0) {
            extra.add("&cNo arena built yet");
            return extra;
        }
        int used = am.used(type);
        extra.add("&7Arenas: &f" + am.arenaCount(type));
        extra.add("&7Players: &f" + used + "&7/&f" + cap);
        if (plugin.regen() != null) {
            long secs = plugin.regen().secondsUntilReset(type);
            if (secs >= 0) extra.add("&7Next reset: &f" + (secs / 60) + ":" + String.format("%02d", secs % 60));
        }
        extra.add(used >= cap ? "&cAll arenas are full" : "&eClick to play");
        return extra;
    }

    // ------------------------------------------------------------ chest builders

    private Inventory create(Holder h, int size, String title) {
        Inventory inv = Bukkit.createInventory(h, size, Text.c(title));
        h.inventory = inv;
        if (plugin.getConfig().getBoolean("menus.filler", true)) {
            ItemStack pane = icon("BLACK_STAINED_GLASS_PANE", " ", new ArrayList<>());
            for (int i = 0; i < size; i++) inv.setItem(i, pane);
        }
        return inv;
    }

    private void put(Holder h, Inventory inv, String key, ConfigurationSection sec, List<String> extra) {
        int slot = sec.getInt("slot", -1);
        if (slot < 0 || slot >= inv.getSize()) return;
        List<String> lore = new ArrayList<>(sec.getStringList("lore"));
        lore.addAll(extra);
        inv.setItem(slot, icon(sec.getString("icon", "STONE"), sec.getString("name", key), lore));
        h.slots.put(slot, key);
    }

    private ItemStack icon(String material, String name, List<String> lore) {
        Material m = Material.matchMaterial(material);
        if (m == null) m = Material.STONE;
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Text.c(name));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(Text.c(s));
        meta.lore(l);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(meta);
        return it;
    }

    // ------------------------------------------------------------ open menus

    void openHub(Player p) {
        if (tryDialog(() -> dialogs.openHub(p))) return;
        ConfigurationSection hub = plugin.getConfig().getConfigurationSection("hub");
        if (hub == null) return;
        Holder h = new Holder(Kind.HUB, false, null, null);
        Inventory inv = create(h, plugin.getConfig().getInt("menus.hub-size", 27),
                plugin.getConfig().getString("menus.hub-title", "&8&lPracticeCore"));
        for (String key : hub.getKeys(false)) {
            ConfigurationSection s = hub.getConfigurationSection(key);
            if (s != null) put(h, inv, key, s, List.of("", "&eClick to open"));
        }
        p.openInventory(inv);
    }

    void openModes(Player p, boolean edit) {
        if (tryDialog(() -> dialogs.openModes(p, edit))) return;
        ConfigurationSection modes = plugin.getConfig().getConfigurationSection("modes");
        if (modes == null) return;
        Holder h = new Holder(Kind.MODE, edit, null, null);
        String title = edit
                ? plugin.getConfig().getString("menus.editor-mode-title", "&8&lKit Editor - Choose Mode")
                : plugin.getConfig().getString("menus.mode-title", "&8&lChoose Mode");
        Inventory inv = create(h, plugin.getConfig().getInt("menus.mode-size", 27), title);
        for (String key : modes.getKeys(false)) {
            ConfigurationSection s = modes.getConfigurationSection(key);
            if (s != null) put(h, inv, key, s, List.of("", edit ? "&eClick to edit this kit" : "&eClick to choose"));
        }
        p.openInventory(inv);
    }

    void openDifficulty(Player p, String mode) {
        if (tryDialog(() -> dialogs.openDifficulty(p, mode))) return;
        ConfigurationSection diffs = plugin.getConfig().getConfigurationSection("difficulties");
        if (diffs == null) return;
        Holder h = new Holder(Kind.DIFF, false, mode, null);
        Inventory inv = create(h, plugin.getConfig().getInt("menus.diff-size", 27),
                plugin.getConfig().getString("menus.diff-title", "&8&lChoose Difficulty"));
        for (String key : diffs.getKeys(false)) {
            ConfigurationSection s = diffs.getConfigurationSection(key);
            if (s != null) put(h, inv, key, s, List.of("", "&eClick to choose"));
        }
        p.openInventory(inv);
    }

    void openTypes(Player p, String mode, String diff) {
        if (tryDialog(() -> dialogs.openTypes(p, mode, diff))) return;
        ConfigurationSection types = plugin.getConfig().getConfigurationSection("arena-types");
        if (types == null) return;
        Holder h = new Holder(Kind.TYPE, false, mode, diff);
        Inventory inv = create(h, plugin.getConfig().getInt("menus.type-size", 27),
                plugin.getConfig().getString("menus.type-title", "&8&lChoose Arena"));
        for (String key : types.getKeys(false)) {
            ConfigurationSection s = types.getConfigurationSection(key);
            if (s != null) put(h, inv, key, s, arenaInfo(key));
        }
        p.openInventory(inv);
    }

    // ------------------------------------------------------------ chest clicks

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getRawSlot() < 0 || e.getRawSlot() >= e.getView().getTopInventory().getSize()) return;
        String key = h.slots.get(e.getRawSlot());
        if (key == null) return;
        switch (h.kind) {
            case HUB -> hubClicked(p, key);
            case MODE -> modeClicked(p, key, h.edit);
            case DIFF -> diffClicked(p, h.mode, key);
            case TYPE -> typeClicked(p, h.mode, h.diff, key);
        }
    }
}

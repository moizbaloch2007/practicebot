package com.practiceserver.arenas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
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
 * All menus: hub, mode, difficulty, arena, kit slots, kit selection and effects.
 * menus.style: dialog (Minecraft dialogs, default) or chest (inventory menus).
 */
final class MenuManager implements Listener {

    /** One clickable line of a list menu. */
    record Entry(String label, List<String> lore, String icon, Consumer<Player> action) {}

    enum Kind { HUB, MODE, DIFF, TYPE, LIST }

    static final class Holder implements InventoryHolder {
        final Kind kind;
        final boolean edit;
        final String mode;
        final String diff;
        final Map<Integer, String> slots = new HashMap<>();
        final Map<Integer, Consumer<Player>> actions = new HashMap<>();
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

    // ------------------------------------------------------------ dialog helpers

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

    // ------------------------------------------------------------ click logic (shared)

    void hubClicked(Player p, String key) {
        switch (key) {
            case "play" -> openModes(p, false);
            case "custom" -> openTypes(p, "custom", "custom");
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
        if (edit) openKitSlots(p, key);
        else openDifficulty(p, key);
    }

    void diffClicked(Player p, String mode, String diff) {
        openTypes(p, mode, diff);
    }

    void typeClicked(Player p, String mode, String diff, String type) {
        if ("custom".equals(mode)) plugin.sessions().beginCustom(p, type);
        else plugin.sessions().beginPrep(p, mode, diff, type);
    }

    /** Lore lines about an arena type: arenas, players and time until reset. */
    List<String> arenaInfo(String type) {
        List<String> extra = new ArrayList<>();
        extra.add("");
        ArenaManager am = plugin.arenas();
        int total = am.capacity(type);
        if (total == 0) {
            extra.add("&cNo arena built yet");
            return extra;
        }
        int used = am.used(type);
        extra.add("&7Arenas: &f" + am.arenaCount(type));
        extra.add("&7Players: &f" + used + "&7/&f" + total);
        if (plugin.getConfig().getBoolean("menus.show-arena-list", false)) {
            for (Arena a : am.all()) {
                if (a.type.equalsIgnoreCase(type)) {
                    extra.add("&8- &f" + a.id + "&7: &f" + a.taken.size() + "&7/&f" + a.spawns.size());
                }
            }
        }
        if (plugin.regen() != null) {
            long secs = plugin.regen().secondsUntilReset(type);
            if (secs >= 0) extra.add("&7Next reset: &f" + (secs / 60) + ":" + String.format("%02d", secs % 60));
        }
        extra.add(am.freeSlots(type) <= 0 ? "&cAll arenas are full" : "&eClick to play");
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

    // ------------------------------------------------------------ main menus

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

    // ------------------------------------------------------------ list menus (kits, effects)

    /** A simple menu with one button per entry. Works as dialog or chest. */
    void openList(Player p, String title, List<Entry> entries, int columns) {
        if (entries.isEmpty()) return;
        if (tryDialog(() -> dialogs.showList(p, title, entries, columns))) return;
        Holder h = new Holder(Kind.LIST, false, null, null);
        int rows = (entries.size() + 6) / 7;
        int size = Math.min(54, (rows + 2) * 9);
        Inventory inv = create(h, size, title);
        for (int i = 0; i < entries.size() && i < 21; i++) {
            int slot = 10 + (i / 7) * 9 + (i % 7);
            if (slot >= size) break;
            Entry e = entries.get(i);
            inv.setItem(slot, icon(e.icon(), e.label(), e.lore()));
            h.actions.put(slot, e.action());
        }
        p.openInventory(inv);
    }

    /** Kit 1-3 of a mode, to edit. */
    void openKitSlots(Player p, String mode) {
        List<Entry> list = new ArrayList<>();
        String modeName = plugin.getConfig().getString("modes." + mode + ".name", mode);
        for (int i = 1; i <= LayoutManager.MAX_KITS; i++) {
            final int slot = i;
            boolean saved = plugin.layouts().has(p.getUniqueId(), mode, slot);
            list.add(new Entry("&aKit " + slot + (saved ? " &7(saved)" : " &8(empty)"),
                    List.of("&7Edit your own kit " + slot + " for " + modeName, "&eClick to edit"),
                    saved ? "ENCHANTED_BOOK" : "BOOK",
                    pl -> plugin.editor().open(pl, mode, slot)));
        }
        openList(p, plugin.getConfig().getString("menus.kit-slot-title", "&8&lEdit a Kit"), list, 1);
    }

    /** /kits: pick a kit inside a session, or open the kit editor outside one. */
    void openKitSelect(Player p) {
        SessionManager.Session s = plugin.sessions().get(p.getUniqueId());
        if (s == null) {
            openModes(p, true);
            return;
        }
        if (s.custom) {
            ConfigurationSection modes = plugin.getConfig().getConfigurationSection("modes");
            if (modes == null) return;
            List<Entry> list = new ArrayList<>();
            for (String key : modes.getKeys(false)) {
                String name = modes.getString(key + ".name", key);
                list.add(new Entry(name, List.of("&7Choose a kit for this mode", "&eClick to open"),
                        modes.getString(key + ".icon", "CHEST"), pl -> openKitChoice(pl, key)));
            }
            openList(p, plugin.getConfig().getString("menus.kit-mode-title", "&8&lKits - Choose Mode"), list, 2);
        } else {
            openKitChoice(p, s.mode);
        }
    }

    /** Default kit and the player's own kits for one mode. */
    void openKitChoice(Player p, String mode) {
        List<Entry> list = new ArrayList<>();
        String selected = plugin.kits().selectedKey(p, mode);
        list.add(new Entry("&eDefault Kit" + ("default".equals(selected) ? " &a(selected)" : ""),
                List.of("&7The standard kit", "&eClick to use"), "CHEST",
                pl -> plugin.sessions().applyKit(pl, mode, "default")));
        for (int i = 1; i <= LayoutManager.MAX_KITS; i++) {
            final String key = String.valueOf(i);
            if (!plugin.layouts().has(p.getUniqueId(), mode, i)) continue;
            list.add(new Entry("&aKit " + i + (key.equals(selected) ? " &a(selected)" : ""),
                    List.of("&7Your own kit " + i, "&eClick to use"), "ENCHANTED_BOOK",
                    pl -> plugin.sessions().applyKit(pl, mode, key)));
        }
        list.add(new Entry("&bEdit my kits", List.of("&7Create or change your kits", "&7Leave the arena to edit, or use the lobby anvil"),
                "ANVIL", pl -> openKitSlots(pl, mode)));
        openList(p, plugin.getConfig().getString("menus.kit-select-title", "&8&lChoose Your Kit"), list, 1);
    }

    void openEffects(Player p) {
        if (!plugin.effects().allowed(p)) return;
        openList(p, plugin.getConfig().getString("menus.effects-title", "&8&lEffects"), plugin.effects().entries(), 2);
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
        if (h.kind == Kind.LIST) {
            Consumer<Player> action = h.actions.get(e.getRawSlot());
            if (action != null) {
                p.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> action.accept(p));
            }
            return;
        }
        String key = h.slots.get(e.getRawSlot());
        if (key == null) return;
        switch (h.kind) {
            case HUB -> hubClicked(p, key);
            case MODE -> modeClicked(p, key, h.edit);
            case DIFF -> diffClicked(p, h.mode, key);
            case TYPE -> typeClicked(p, h.mode, h.diff, key);
            default -> { }
        }
    }
}

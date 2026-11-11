package com.practiceserver.arenas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import org.bukkit.persistence.PersistentDataType;

/**
 * In-game kit editor (each player edits only their own layout).
 * Rows 1-3 = inventory, row 4 = hotbar, row 5 = helmet / chestplate / leggings / boots / offhand,
 * row 6 = buttons. Move items, delete with the trash slot, add from "Extra Materials", then Save.
 */
final class KitEditor implements Listener {

    private static final int EQUIP_START = 36;   // 36 helmet, 37 chestplate, 38 leggings, 39 boots, 40 offhand
    private static final int EQUIP_END = 40;
    private static final int SAVE = 45;
    private static final int ADD = 47;
    private static final int RESET = 49;
    private static final int TRASH = 51;
    private static final int CLOSE = 53;
    private static final int BACK = 40;

    private static final String[] EQUIP_NAMES = {"Helmet", "Chestplate / Elytra", "Leggings", "Boots", "Offhand"};

    static final class EditorHolder implements InventoryHolder {
        final String mode;
        Inventory inventory;
        EditorHolder(String mode) { this.mode = mode; }
        @Override public Inventory getInventory() { return inventory; }
    }

    static final class ExtrasHolder implements InventoryHolder {
        final String mode;
        final List<ItemStack> items = new ArrayList<>();
        Inventory inventory;
        ExtrasHolder(String mode) { this.mode = mode; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private final ArenasPlugin plugin;
    private final NamespacedKey placeholderKey;
    private final Map<UUID, ItemStack[]> working = new HashMap<>();

    KitEditor(ArenasPlugin plugin) {
        this.plugin = plugin;
        this.placeholderKey = new NamespacedKey(plugin, "placeholder");
    }

    void forget(UUID id) {
        working.remove(id);
    }

    private String modeName(String mode) {
        return plugin.getConfig().getString("modes." + mode + ".name", mode).replaceAll("&.", "");
    }

    // ------------------------------------------------------------ open

    void open(Player p, String mode) {
        ItemStack[] layout = plugin.kits().layoutFor(p, mode);
        ItemStack[] copy = new ItemStack[LayoutManager.SIZE];
        for (int i = 0; i < copy.length; i++) copy[i] = layout[i] == null ? null : layout[i].clone();
        working.put(p.getUniqueId(), copy);
        showEditor(p, mode);
    }

    private void showEditor(Player p, String mode) {
        ItemStack[] w = working.get(p.getUniqueId());
        if (w == null) {
            open(p, mode);
            return;
        }
        EditorHolder h = new EditorHolder(mode);
        Inventory inv = Bukkit.createInventory(h, 54, Text.c("&8Editing Kit: &b" + modeName(mode)));
        h.inventory = inv;

        for (int i = 0; i < 27; i++) inv.setItem(i, w[9 + i] == null ? null : w[9 + i].clone());
        for (int i = 0; i < 9; i++) inv.setItem(27 + i, w[i] == null ? null : w[i].clone());

        ItemStack pane = icon(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = EQUIP_END + 1; i < 54; i++) inv.setItem(i, pane);
        for (int i = 0; i <= EQUIP_END - EQUIP_START; i++) {
            ItemStack it = w[EQUIP_START + i];
            if (it != null) {
                inv.setItem(EQUIP_START + i, it.clone());
            } else {
                inv.setItem(EQUIP_START + i, placeholder(EQUIP_NAMES[i]));
            }
        }

        inv.setItem(SAVE, icon(Material.LIME_DYE, "&a&lSave Layout",
                List.of("&7Saves this kit for you only.", "&7It is used every time you play this mode.")));
        inv.setItem(ADD, icon(Material.CHEST, "&b&lAdd Items",
                List.of("&7Open the extra materials for this mode.")));
        inv.setItem(RESET, icon(Material.RED_DYE, "&c&lReset to Default",
                List.of("&7Restore the default kit.")));
        inv.setItem(TRASH, icon(Material.HOPPER, "&4&lDelete Item",
                List.of("&7Pick up an item and click here", "&7to delete it from the kit.")));
        inv.setItem(CLOSE, icon(Material.BARRIER, "&c&lClose",
                List.of("&7Close without saving.")));
        p.openInventory(inv);
    }

    private void openExtras(Player p, String mode) {
        ExtrasHolder h = new ExtrasHolder(mode);
        Inventory inv = Bukkit.createInventory(h, 45, Text.c("&8Extra Materials: &b" + modeName(mode)));
        h.inventory = inv;
        int i = 0;
        for (String line : plugin.getConfig().getStringList("kits." + mode + ".extras")) {
            ItemStack it = plugin.kits().parseLine(line);
            if (it == null || i >= 36) continue;
            h.items.add(it);
            ItemStack shown = it.clone();
            ItemMeta meta = shown.getItemMeta();
            if (meta != null) {
                List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                lore.add(Text.c("&eClick to add to your kit"));
                meta.lore(lore);
                shown.setItemMeta(meta);
            }
            inv.setItem(i++, shown);
        }
        ItemStack pane = icon(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int s = 36; s < 45; s++) inv.setItem(s, pane);
        inv.setItem(BACK, icon(Material.ARROW, "&a&lBack to Editor", List.of("&7Your changes are kept.")));
        p.openInventory(inv);
    }

    // ------------------------------------------------------------ helpers

    private ItemStack icon(Material m, String name, List<String> lore) {
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

    private ItemStack placeholder(String slotName) {
        ItemStack it = icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "&7" + slotName + " slot",
                List.of("&8Put your " + slotName.toLowerCase() + " here"));
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(placeholderKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    private boolean isPlaceholder(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(placeholderKey, PersistentDataType.BYTE);
    }

    private static boolean hasItem(ItemStack it) {
        return it != null && !it.getType().isAir();
    }

    private ItemStack clean(ItemStack it) {
        return (it == null || it.getType().isAir() || isPlaceholder(it)) ? null : it.clone();
    }

    private void harvest(UUID id, Inventory top) {
        ItemStack[] w = new ItemStack[LayoutManager.SIZE];
        for (int i = 0; i < 27; i++) w[9 + i] = clean(top.getItem(i));
        for (int i = 0; i < 9; i++) w[i] = clean(top.getItem(27 + i));
        for (int i = EQUIP_START; i <= EQUIP_END; i++) w[i] = clean(top.getItem(i));
        working.put(id, w);
    }

    /** Armor slots only take the right piece. The offhand takes anything. */
    private boolean equipOk(int raw, ItemStack it) {
        if (raw == 40) return true;
        return KitManager.equipIndex(it) == raw;
    }

    // ------------------------------------------------------------ events

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder holder = e.getView().getTopInventory().getHolder();
        if (holder instanceof ExtrasHolder) {
            e.setCancelled(true);
        } else if (holder instanceof EditorHolder) {
            for (int raw : e.getRawSlots()) {
                if (raw >= EQUIP_START) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (holder instanceof EditorHolder h) {
            editorClick(e, p, h, top);
        } else if (holder instanceof ExtrasHolder x) {
            extrasClick(e, p, x);
        }
    }

    private void editorClick(InventoryClickEvent e, Player p, EditorHolder h, Inventory top) {
        switch (e.getClick()) {
            case SHIFT_LEFT, SHIFT_RIGHT, NUMBER_KEY, DOUBLE_CLICK, DROP, CONTROL_DROP,
                 SWAP_OFFHAND, MIDDLE, CREATIVE, UNKNOWN -> {
                e.setCancelled(true);
                return;
            }
            default -> { }
        }
        int raw = e.getRawSlot();
        if (raw < 0 || raw >= 54) {
            e.setCancelled(true);
            return;
        }
        if (raw < EQUIP_START) return; // free editing area

        if (raw <= EQUIP_END) {        // helmet, chestplate, leggings, boots, offhand
            ItemStack cursor = e.getCursor();
            ItemStack inSlot = top.getItem(raw);
            if (isPlaceholder(inSlot)) {
                e.setCancelled(true);
                if (hasItem(cursor) && equipOk(raw, cursor)) {
                    top.setItem(raw, cursor.clone());
                    p.setItemOnCursor(null);
                }
                return;
            }
            if (hasItem(cursor) && !equipOk(raw, cursor)) e.setCancelled(true);
            return;
        }

        e.setCancelled(true);
        UUID id = p.getUniqueId();
        if (raw == TRASH) {
            if (hasItem(e.getCursor())) {
                p.setItemOnCursor(null);
                Text.send(p, plugin.msg("kit-deleted", "&cItem deleted from the kit."));
            }
        } else if (raw == SAVE) {
            if (hasItem(e.getCursor())) {
                Text.send(p, plugin.msg("kit-cursor", "&cPut the item you are holding on a slot first."));
                return;
            }
            harvest(id, top);
            plugin.layouts().set(id, h.mode, working.get(id));
            working.remove(id);
            Bukkit.getScheduler().runTask(plugin, () -> p.closeInventory());
            Text.send(p, plugin.msg("kit-saved", "&aKit layout saved."));
        } else if (raw == ADD) {
            if (hasItem(e.getCursor())) {
                Text.send(p, plugin.msg("kit-cursor", "&cPut the item you are holding on a slot first."));
                return;
            }
            harvest(id, top);
            Bukkit.getScheduler().runTask(plugin, () -> openExtras(p, h.mode));
        } else if (raw == RESET) {
            p.setItemOnCursor(null);
            plugin.layouts().reset(id, h.mode);
            working.put(id, plugin.kits().defaultLayout(h.mode));
            Bukkit.getScheduler().runTask(plugin, () -> showEditor(p, h.mode));
            Text.send(p, plugin.msg("kit-reset", "&eKit reset to the default layout."));
        } else if (raw == CLOSE) {
            working.remove(id);
            Bukkit.getScheduler().runTask(plugin, () -> p.closeInventory());
        }
    }

    private void extrasClick(InventoryClickEvent e, Player p, ExtrasHolder x) {
        e.setCancelled(true);
        int raw = e.getRawSlot();
        if (raw < 0 || raw >= 45) return;
        UUID id = p.getUniqueId();
        if (raw == BACK) {
            Bukkit.getScheduler().runTask(plugin, () -> showEditor(p, x.mode));
            return;
        }
        if (raw >= x.items.size()) return;
        ItemStack[] w = working.get(id);
        if (w == null) return;
        ItemStack add = x.items.get(raw).clone();

        int eq = KitManager.equipIndex(add);
        if (eq >= 0 && w[eq] == null) {
            w[eq] = add;
            Text.send(p, plugin.msg("kit-added", "&aAdded to your kit."));
            return;
        }
        for (int i = 0; i < 36; i++) {
            if (w[i] == null) {
                w[i] = add;
                Text.send(p, plugin.msg("kit-added", "&aAdded to your kit."));
                return;
            }
        }
        Text.send(p, plugin.msg("kit-full", "&cYour kit is full. Delete an item first."));
    }
}

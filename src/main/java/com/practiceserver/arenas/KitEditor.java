package com.practiceserver.arenas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
 * In-game kit editor.
 * Rows 1-3 = inventory, row 4 = hotbar, row 5 = fixed armor/offhand, row 6 = buttons.
 * Move items around, delete with the trash slot, add items from "Extra Materials", then Save.
 */
final class KitEditor implements Listener {

    private static final int ARMOR_ROW = 36;
    private static final int SAVE = 45;
    private static final int ADD = 47;
    private static final int RESET = 49;
    private static final int TRASH = 51;
    private static final int CLOSE = 53;
    private static final int BACK = 40;

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
    private final Map<UUID, ItemStack[]> working = new HashMap<>();

    KitEditor(ArenasPlugin plugin) {
        this.plugin = plugin;
    }

    void forget(UUID id) {
        working.remove(id);
    }

    private String modeName(String mode) {
        return plugin.getConfig().getString("modes." + mode + ".name", mode);
    }

    // ------------------------------------------------------------ open

    void open(Player p, String mode) {
        ItemStack[] layout = plugin.kits().layoutFor(p, mode);
        ItemStack[] copy = new ItemStack[36];
        for (int i = 0; i < 36; i++) copy[i] = layout[i] == null ? null : layout[i].clone();
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
        Inventory inv = Bukkit.createInventory(h, 54, Text.c("&8Editing Kit: &b" + modeName(mode).replaceAll("&.", "")));
        h.inventory = inv;

        for (int i = 0; i < 27; i++) inv.setItem(i, w[9 + i] == null ? null : w[9 + i].clone());
        for (int i = 0; i < 9; i++) inv.setItem(27 + i, w[i] == null ? null : w[i].clone());

        ItemStack pane = icon(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = ARMOR_ROW; i < 54; i++) inv.setItem(i, pane);

        ItemStack[] gear = plugin.kits().armorAndOffhand(mode);
        for (int i = 0; i < 4; i++) if (gear[i] != null) inv.setItem(ARMOR_ROW + i, gear[i].clone());
        if (gear[4] != null) inv.setItem(ARMOR_ROW + 5, gear[4].clone());

        inv.setItem(SAVE, icon(Material.LIME_DYE, "&a&lSave Layout",
                List.of("&7Saves this hotbar and inventory order.", "&7It is used every time you play this mode.")));
        inv.setItem(ADD, icon(Material.CHEST, "&b&lAdd Items",
                List.of("&7Open the extra materials for this mode.")));
        inv.setItem(RESET, icon(Material.RED_DYE, "&c&lReset to Default",
                List.of("&7Restore the default kit layout.")));
        inv.setItem(TRASH, icon(Material.HOPPER, "&4&lDelete Item",
                List.of("&7Pick up an item and click here", "&7to delete it from the kit.")));
        inv.setItem(CLOSE, icon(Material.BARRIER, "&c&lClose",
                List.of("&7Close without saving.")));
        p.openInventory(inv);
    }

    private void openExtras(Player p, String mode) {
        ExtrasHolder h = new ExtrasHolder(mode);
        Inventory inv = Bukkit.createInventory(h, 45, Text.c("&8Extra Materials: &b" + modeName(mode).replaceAll("&.", "")));
        h.inventory = inv;
        int i = 0;
        for (String line : plugin.getConfig().getStringList("kits." + mode + ".extras")) {
            ItemStack it = plugin.kits().parseLine(line);
            if (it == null || i >= 36) continue;
            h.items.add(it);
            ItemStack shown = it.clone();
            ItemMeta meta = shown.getItemMeta();
            if (meta != null) {
                meta.lore(List.of(Text.c("&eClick to add to your kit")));
                shown.setItemMeta(meta);
            }
            inv.setItem(i++, shown);
        }
        ItemStack pane = icon(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int s = 36; s < 45; s++) inv.setItem(s, pane);
        inv.setItem(BACK, icon(Material.ARROW, "&a&lBack to Editor", List.of("&7Your changes are kept.")));
        p.openInventory(inv);
    }

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

    // ------------------------------------------------------------ helpers

    private static ItemStack clean(ItemStack it) {
        return (it == null || it.getType().isAir()) ? null : it.clone();
    }

    private void harvest(UUID id, Inventory top) {
        ItemStack[] w = new ItemStack[36];
        for (int i = 0; i < 27; i++) w[9 + i] = clean(top.getItem(i));
        for (int i = 0; i < 9; i++) w[i] = clean(top.getItem(27 + i));
        working.put(id, w);
    }

    private static boolean hasItem(ItemStack it) {
        return it != null && !it.getType().isAir();
    }

    // ------------------------------------------------------------ events

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder holder = e.getView().getTopInventory().getHolder();
        if (holder instanceof ExtrasHolder) {
            e.setCancelled(true);
        } else if (holder instanceof EditorHolder) {
            for (int raw : e.getRawSlots()) {
                if (raw >= ARMOR_ROW) {
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
        if (raw < ARMOR_ROW) return; // free editing area

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
            Bukkit.getScheduler().runTask(plugin, p::closeInventory);
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
            ItemStack[] def = plugin.kits().defaultLayout(h.mode);
            working.put(id, def);
            Bukkit.getScheduler().runTask(plugin, () -> showEditor(p, h.mode));
            Text.send(p, plugin.msg("kit-reset", "&eKit reset to the default layout."));
        } else if (raw == CLOSE) {
            working.remove(id);
            Bukkit.getScheduler().runTask(plugin, p::closeInventory);
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
        for (int i = 0; i < 36; i++) {
            if (w[i] == null) {
                w[i] = x.items.get(raw).clone();
                Text.send(p, plugin.msg("kit-added", "&aAdded to your kit."));
                return;
            }
        }
        Text.send(p, plugin.msg("kit-full", "&cYour kit is full. Delete an item first."));
    }
}

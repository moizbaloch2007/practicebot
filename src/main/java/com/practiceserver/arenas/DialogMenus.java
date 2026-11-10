package com.practiceserver.arenas;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/** The same menus as MenuManager, shown as Minecraft dialogs (1.21.7+). */
final class DialogMenus {

    private final ArenasPlugin plugin;

    DialogMenus(ArenasPlugin plugin) {
        this.plugin = plugin;
    }

    private ActionButton button(String label, List<String> lore, Consumer<Player> onClick) {
        ActionButton.Builder b = ActionButton.builder(Text.c(label));
        if (lore != null && !lore.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (String s : lore) lines.add(Text.c(s));
            b.tooltip(Component.join(JoinConfiguration.newlines(), lines));
        }
        b.action(DialogAction.customClick((view, audience) -> {
            if (audience instanceof Player pl) {
                Bukkit.getScheduler().runTask(plugin, () -> onClick.accept(pl));
            }
        }, ClickCallback.Options.builder().build()));
        return b.build();
    }

    private void show(Player p, String title, List<ActionButton> buttons, int columns) {
        if (buttons.isEmpty()) return;
        ActionButton close = ActionButton.builder(Text.c("&cClose")).build();
        Dialog dialog = Dialog.create(f -> f.empty()
                .base(DialogBase.builder(Text.c(title)).build())
                .type(DialogType.multiAction(buttons, close, columns)));
        p.showDialog(dialog);
    }

    void openHub(Player p) {
        ConfigurationSection hub = plugin.getConfig().getConfigurationSection("hub");
        if (hub == null) return;
        List<ActionButton> buttons = new ArrayList<>();
        for (String key : hub.getKeys(false)) {
            ConfigurationSection s = hub.getConfigurationSection(key);
            if (s == null) continue;
            buttons.add(button(s.getString("name", key), s.getStringList("lore"),
                    pl -> plugin.menus().hubClicked(pl, key)));
        }
        show(p, plugin.getConfig().getString("menus.hub-title", "&lPracticeCore"), buttons, 1);
    }

    void openModes(Player p, boolean edit) {
        ConfigurationSection modes = plugin.getConfig().getConfigurationSection("modes");
        if (modes == null) return;
        List<ActionButton> buttons = new ArrayList<>();
        for (String key : modes.getKeys(false)) {
            ConfigurationSection s = modes.getConfigurationSection(key);
            if (s == null) continue;
            List<String> lore = new ArrayList<>(s.getStringList("lore"));
            lore.add(edit ? "&eClick to edit this kit" : "&eClick to choose");
            buttons.add(button(s.getString("name", key), lore, pl -> plugin.menus().modeClicked(pl, key, edit)));
        }
        String title = edit
                ? plugin.getConfig().getString("menus.editor-mode-title", "&lKit Editor - Choose Mode")
                : plugin.getConfig().getString("menus.mode-title", "&lChoose Mode");
        show(p, title, buttons, 2);
    }

    void openDifficulty(Player p, String mode) {
        ConfigurationSection diffs = plugin.getConfig().getConfigurationSection("difficulties");
        if (diffs == null) return;
        List<ActionButton> buttons = new ArrayList<>();
        for (String key : diffs.getKeys(false)) {
            ConfigurationSection s = diffs.getConfigurationSection(key);
            if (s == null) continue;
            buttons.add(button(s.getString("name", key), s.getStringList("lore"),
                    pl -> plugin.menus().diffClicked(pl, mode, key)));
        }
        show(p, plugin.getConfig().getString("menus.diff-title", "&lChoose Difficulty"), buttons, 3);
    }

    void openTypes(Player p, String mode, String diff) {
        ConfigurationSection types = plugin.getConfig().getConfigurationSection("arena-types");
        if (types == null) return;
        List<ActionButton> buttons = new ArrayList<>();
        for (String key : types.getKeys(false)) {
            ConfigurationSection s = types.getConfigurationSection(key);
            if (s == null) continue;
            List<String> lore = new ArrayList<>(s.getStringList("lore"));
            lore.addAll(plugin.menus().arenaInfo(key));
            int cap = plugin.arenas().capacity(key);
            String label = s.getString("name", key)
                    + (cap == 0 ? " &c(not built)" : " &7(" + plugin.arenas().used(key) + "/" + cap + ")");
            buttons.add(button(label, lore, pl -> plugin.menus().typeClicked(pl, mode, diff, key)));
        }
        show(p, plugin.getConfig().getString("menus.type-title", "&lChoose Arena"), buttons, 2);
    }
}

package com.practiceserver.arenas;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Effects players can give themselves in a practice session (no permission needed). */
final class EffectsManager {

    private final ArenasPlugin plugin;

    EffectsManager(ArenasPlugin plugin) {
        this.plugin = plugin;
    }

    /** Players need to be in a session, and in a fight only if effects.allow-in-fight is true. */
    boolean allowed(Player p) {
        if (!plugin.getConfig().getBoolean("effects.enabled", true)) return false;
        SessionManager.Session s = plugin.sessions().get(p.getUniqueId());
        if (s == null) {
            Text.send(p, plugin.msg("effects-no-session", "&cYou can only use effects inside a practice arena."));
            return false;
        }
        boolean inFight = s.phase == SessionManager.Phase.COUNTDOWN || s.phase == SessionManager.Phase.FIGHT;
        if (inFight && !plugin.getConfig().getBoolean("effects.allow-in-fight", true)) {
            Text.send(p, plugin.msg("effects-locked", "&cEffects can only be used before the fight starts."));
            return false;
        }
        return true;
    }

    List<MenuManager.Entry> entries() {
        List<MenuManager.Entry> out = new ArrayList<>();
        ConfigurationSection list = plugin.getConfig().getConfigurationSection("effects.list");
        if (list != null) {
            for (String key : list.getKeys(false)) {
                ConfigurationSection s = list.getConfigurationSection(key);
                if (s == null) continue;
                List<String> lore = new ArrayList<>(s.getStringList("lore"));
                lore.add("&7Lasts &f" + s.getInt("seconds", 120) + " &7seconds");
                out.add(new MenuManager.Entry(s.getString("name", key), lore, s.getString("icon", "POTION"),
                        pl -> apply(pl, key)));
            }
        }
        ConfigurationSection clear = plugin.getConfig().getConfigurationSection("effects.clear");
        String label = clear == null ? "&7Remove all effects" : clear.getString("name", "&7Remove all effects");
        String icon = clear == null ? "MILK_BUCKET" : clear.getString("icon", "MILK_BUCKET");
        List<String> lore = clear == null ? List.of("&7Clears every effect") : clear.getStringList("lore");
        out.add(new MenuManager.Entry(label, lore, icon, this::clearAll));
        return out;
    }

    void apply(Player p, String key) {
        if (!allowed(p)) return;
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("effects.list." + key);
        if (s == null) return;
        PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(s.getString("effect", "regeneration").toLowerCase()));
        if (type == null) {
            plugin.getLogger().warning("Unknown effect '" + s.getString("effect") + "' in effects.list." + key);
            return;
        }
        int amp = Math.max(0, s.getInt("level", 1) - 1);
        int ticks = Math.max(1, s.getInt("seconds", 120)) * 20;
        p.addPotionEffect(new PotionEffect(type, ticks, amp, false, true, true));
        Text.send(p, plugin.msg("effect-given", "&aEffect added: &f{name}").replace("{name}", s.getString("name", key)));
    }

    void clearAll(Player p) {
        for (PotionEffect fx : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(fx.getType());
        Text.send(p, plugin.msg("effects-cleared", "&aAll effects removed."));
    }
}

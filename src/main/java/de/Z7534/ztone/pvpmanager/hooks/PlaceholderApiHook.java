package de.Z7534.ztone.pvpmanager.hooks;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.pvpmanager.utils.ColorUtils;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PlaceholderApiHook extends PlaceholderExpansion {

    private final Pvpmanager plugin;

    public PlaceholderApiHook(Pvpmanager plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "pvpmanager";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Z7534";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPlugin().getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        String lower = params.toLowerCase();

        if (lower.equals("dimension_pvp") || lower.equals("world_pvp")) {
            if (player != null) {
                return String.valueOf(plugin.isDimensionPvPEnabled(player.getWorld()));
            }
            return String.valueOf(plugin.isDimensionPvPEnabled("minecraft:overworld"));
        }

        if (lower.startsWith("dimension_pvp_") || lower.startsWith("world_pvp_")) {
            String dim = params.substring(params.indexOf('_', params.indexOf('_') + 1) + 1);
            return String.valueOf(plugin.isDimensionPvPEnabled(dim));
        }

        if (lower.equals("dimension_forced") || lower.equals("world_forced")
                || lower.equals("dimension_force") || lower.equals("world_force")
                || lower.equals("force") || lower.equals("is_forced")) {
            if (player != null) {
                return String.valueOf(plugin.isDimensionPvPForced(player.getWorld()));
            }
            return "false";
        }

        if (lower.startsWith("dimension_forced_") || lower.startsWith("world_forced_")
                || lower.startsWith("dimension_force_") || lower.startsWith("world_force_")) {
            String dim = params.substring(params.indexOf('_', params.indexOf('_') + 1) + 1);
            return String.valueOf(plugin.isDimensionPvPForced(dim));
        }

        if (player == null) {
            return "";
        }

        if (lower.equals("can_pvp")) {
            return String.valueOf(plugin.canPvP(player));
        }

        return switch (lower) {
            case "pvp" -> booleanDisplay(plugin.getPvpToggleManager().isPvpEnabled(player.getUniqueId()));
            case "combat_status" -> plugin.getCombatManager().isTagged(player.getUniqueId())
                    ? "im Kampf" : "nicht im Kampf";
            case "combat_time", "combat_timeleft" -> String.valueOf(plugin.getCombatManager().remainingSeconds(player.getUniqueId()));
            case "player_health" -> String.valueOf(Math.round(player.getHealth()));
            case "current_enemy" -> plugin.getCombatManager().getOpponents(player.getUniqueId()).stream()
                    .findFirst()
                    .map(Bukkit::getPlayer)
                    .filter(java.util.Objects::nonNull)
                    .map(Player::getName)
                    .orElse("-");
            case "current_enemy_health" -> plugin.getCombatManager().getOpponents(player.getUniqueId()).stream()
                    .findFirst()
                    .map(Bukkit::getPlayer)
                    .filter(java.util.Objects::nonNull)
                    .map(opponent -> String.valueOf(Math.round(opponent.getHealth())))
                    .orElse("0");
            case "respawn_time", "spawn_time" -> String.valueOf(plugin.getRespawnProtectionManager().remainingSeconds(player.getUniqueId()));
            case "respawn_protected", "spawn_protected" -> booleanDisplay(plugin.getRespawnProtectionManager().isProtected(player.getUniqueId()));
            default -> null;
        };
    }

    private String booleanDisplay(boolean value) {
        String path = value ? "Placeholders.Boolean Format.True" : "Placeholders.Boolean Format.False";
        String fallback = value ? "&a✓" : "&c✗";
        return ColorUtils.colorize(plugin.getConfigManager().config().getString(path, fallback));
    }
}

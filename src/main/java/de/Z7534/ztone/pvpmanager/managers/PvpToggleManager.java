package de.Z7534.ztone.pvpmanager.managers;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.pvpmanager.database.models.PlayerData;
import de.Z7534.ztone.pvpmanager.permissions.Permissions;
import de.Z7534.ztone.pvpmanager.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PvpToggleManager {

    private final Pvpmanager plugin;
    private final Map<UUID, Long> lastToggleAt = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> pendingState = new ConcurrentHashMap<>();

    public PvpToggleManager(Pvpmanager plugin) {
        this.plugin = plugin;
    }

    public boolean isPvpEnabled(UUID uuid) {
        return plugin.getPlayerDataManager().get(uuid).isPvpEnabled();
    }

    public boolean isOnCooldown(Player player) {
        if (player.hasPermission(Permissions.EXEMPT_PVPTOGGLECOOLDOWN) || player.hasPermission(Permissions.EXEMPT)) {
            return false;
        }
        long cooldownSeconds = plugin.getConfigManager().config().getLong("PvP Toggle.Cooldown", 5);
        long last = lastToggleAt.getOrDefault(player.getUniqueId(), 0L);
        return (System.currentTimeMillis() - last) < cooldownSeconds * 1000L;
    }

    public long cooldownRemaining(Player player) {
        long cooldownSeconds = plugin.getConfigManager().config().getLong("PvP Toggle.Cooldown", 5);
        long last = lastToggleAt.getOrDefault(player.getUniqueId(), 0L);
        long remaining = cooldownSeconds - ((System.currentTimeMillis() - last) / 1000L);
        return Math.max(0, remaining);
    }

    public void requestToggle(Player player, boolean newState) {
        UUID uuid = player.getUniqueId();

        if (isPvpEnabled(uuid) == newState) {
            plugin.getMessageDispatcher().send(player, newState ? "PvPToggle_Already_Enabled" : "PvPToggle_Already_Disabled");
            return;
        }
        if (isOnCooldown(player)) {
            plugin.getMessageDispatcher().sendWithTime(player, "Error_PvP_Cooldown", cooldownRemaining(player));
            return;
        }

        lastToggleAt.put(uuid, System.currentTimeMillis());

        boolean stateCooldownEnabled = plugin.getConfigManager().config().getBoolean("PvP Toggle.State Cooldown.Enabled", false);
        long stateCooldown = plugin.getConfigManager().config().getLong("PvP Toggle.State Cooldown.Time", 0);
        if (!stateCooldownEnabled || stateCooldown <= 0) {
            applyState(player, newState);
            return;
        }

        pendingState.put(uuid, newState);
        plugin.getMessageDispatcher().sendWithTime(player,
                newState ? "PvP_Enabled_StateCooldown" : "PvP_Disabled_StateCooldown", stateCooldown);

        runCommands("PvP Toggle.State Cooldown.Commands On Cooldown Start", player);

        Bukkit.getScheduler().runTaskLater(plugin.getPlugin(), () -> {
            Boolean state = pendingState.remove(uuid);
            Player target = Bukkit.getPlayer(uuid);
            if (state != null && target != null) {
                applyState(target, state);
            }
        }, stateCooldown * 20L);
    }

    private void applyState(Player player, boolean newState) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        data.setPvpEnabled(newState);
        plugin.getPlayerDataManager().save(data);

        plugin.getMessageDispatcher().send(player, newState ? "PvP_Enabled" : "PvP_Disabled");

        String cmdPath = newState ? "PvP Toggle.Commands PvP On" : "PvP Toggle.Commands PvP Off";
        runCommands(cmdPath, player);
    }

    public void setPvpEnabled(Player target, boolean newState, Player actor) {
        applyState(target, newState);
        String stateText = ColorUtils.colorize(plugin.getLocaleManager().raw(newState ? "Enabled" : "Disabled"));
        plugin.getMessageDispatcher().send(actor, "PvPToggle_Admin_Changed",
                Map.of("player", target.getName(), "state", stateText));
    }

    public void applyDimensionDefault(Player player, boolean newState) {
        if (isPvpEnabled(player.getUniqueId()) == newState) {
            return;
        }
        applyState(player, newState);
    }

    private void runCommands(String path, Player player) {
        for (String command : plugin.getConfigManager().config().getStringList(path)) {
            String parsed = command.replace("{player}", player.getName());
            if (parsed.startsWith("!PLAYER ")) {
                Bukkit.dispatchCommand(player, parsed.substring("!PLAYER ".length()));
            } else if (parsed.startsWith("!CONSOLE ")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed.substring("!CONSOLE ".length()));
            } else {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            }
        }
    }
}

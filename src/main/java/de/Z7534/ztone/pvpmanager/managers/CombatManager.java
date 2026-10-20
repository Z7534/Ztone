package de.Z7534.ztone.pvpmanager.managers;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.pvpmanager.permissions.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatManager {

    private final Pvpmanager plugin;

    private final Map<UUID, Long> tagEndsAt = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> opponents = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastAttacker = new ConcurrentHashMap<>();
    private final Set<UUID> teleportBypass = ConcurrentHashMap.newKeySet();

    public CombatManager(Pvpmanager plugin) {
        this.plugin = plugin;
    }

    public void start() {

        Bukkit.getScheduler().runTaskTimer(plugin.getPlugin(), this::tick, 20L, 20L);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (UUID uuid : new HashSet<>(tagEndsAt.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            Long endTime = tagEndsAt.get(uuid);
            if (endTime == null) {
                continue;
            }
            if (endTime <= now) {
                untag(player);
                continue;
            }
            plugin.getDisplayManager().updateCombatDisplay(player, (endTime - now) / 1000);
            plugin.getGlowManager().reassert(player);
        }
    }

    public boolean isTagged(UUID uuid) {
        return tagEndsAt.getOrDefault(uuid, 0L) > System.currentTimeMillis();
    }

    public long remainingSeconds(UUID uuid) {
        long remainingMs = tagEndsAt.getOrDefault(uuid, 0L) - System.currentTimeMillis();
        return Math.max(0L, remainingMs / 1000L);
    }

    public boolean tag(Player player, Player opponent) {
        if (player.hasPermission(Permissions.EXEMPT_COMBATTAG)
                || player.hasPermission(Permissions.EXEMPT)) {
            return false;
        }

        int seconds = plugin.getConfigManager().config().getInt("Combat Tag.Time", 15);

        boolean wasTagged = isTagged(player.getUniqueId());
        tagEndsAt.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000L));

        if (opponent != null) {
            opponents.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet())
                    .add(opponent.getUniqueId());
            lastAttacker.put(player.getUniqueId(), opponent.getUniqueId());
        }

        if (plugin.getConfigManager().config().getBoolean("Combat Tag.Glowing", true)
                && de.Z7534.ztone.pvpmanager.utils.VersionUtils.supportsGlowing()) {
            plugin.getGlowManager().setGlowing(player, true);
        }

        if (!wasTagged) {
            runCommands("Commands On Tag", player);
        }

        long remaining = remainingSeconds(player.getUniqueId());
        if (remaining > 0) {
            plugin.getDisplayManager().updateCombatDisplay(player, remaining);
        }
        return !wasTagged;
    }

    public void untag(Player player) {
        boolean wasTagged = isTagged(player.getUniqueId());
        tagEndsAt.remove(player.getUniqueId());
        opponents.remove(player.getUniqueId());
        plugin.getGlowManager().setGlowing(player, false);
        plugin.getDisplayManager().clearCombatDisplay(player);

        if (wasTagged) {
            plugin.getMessageDispatcher().send(player, "Out_Of_Combat");
            runCommands("Commands On Untag", player);
        }
    }

    public void untagAll() {
        for (UUID uuid : new HashSet<>(tagEndsAt.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                untag(player);
            }
        }
    }

    public void onOpponentDeath(UUID victim) {
        if (!plugin.getConfigManager().config().getBoolean("Combat Tag.Untag On Kill", true)) {
            return;
        }
        for (Map.Entry<UUID, Set<UUID>> entry : opponents.entrySet()) {
            entry.getValue().remove(victim);
            if (entry.getValue().isEmpty()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null) {
                    untag(player);
                }
            }
        }
    }

    public Set<UUID> getOpponents(UUID uuid) {
        return opponents.getOrDefault(uuid, Set.of());
    }

    public UUID getLastAttacker(UUID uuid) {
        return lastAttacker.get(uuid);
    }

    public void setTeleportBypass(UUID uuid, boolean bypass) {
        if (bypass) {
            teleportBypass.add(uuid);
        } else {
            teleportBypass.remove(uuid);
        }
    }

    public boolean hasTeleportBypass(UUID uuid) {
        return teleportBypass.contains(uuid);
    }

    private void runCommands(String path, Player player) {
        java.util.List<String> commands = plugin.getConfigManager().config().getStringList("Combat Tag." + path);
        for (String command : commands) {
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

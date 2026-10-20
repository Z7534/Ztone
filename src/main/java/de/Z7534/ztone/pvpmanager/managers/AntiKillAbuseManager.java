package de.Z7534.ztone.pvpmanager.managers;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.pvpmanager.permissions.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AntiKillAbuseManager {

    private final Pvpmanager plugin;
    private final Map<UUID, Map<UUID, Deque<Long>>> killTimestamps = new ConcurrentHashMap<>();

    public AntiKillAbuseManager(Pvpmanager plugin) {
        this.plugin = plugin;
    }

    public boolean registerKillAndCheckAbuse(Player killer, Player victim) {
        if (!plugin.getConfigManager().config().getBoolean("Player Kills.Anti Kill Abuse.Enabled", false)) {
            return false;
        }
        if (killer.hasPermission(Permissions.EXEMPT_KILLABUSE) || killer.hasPermission(Permissions.EXEMPT)) {
            return false;
        }

        long windowSeconds = plugin.getConfigManager().config().getLong("Player Kills.Anti Kill Abuse.Time Limit", 20);
        int maxKills = plugin.getConfigManager().config().getInt("Player Kills.Anti Kill Abuse.Max Kills", 10);

        Deque<Long> timestamps = killTimestamps
                .computeIfAbsent(killer.getUniqueId(), k -> new ConcurrentHashMap<>())
                .computeIfAbsent(victim.getUniqueId(), k -> new ArrayDeque<>());

        long now = System.currentTimeMillis();
        timestamps.addLast(now);

        while (!timestamps.isEmpty() && (now - timestamps.peekFirst()) > windowSeconds * 1000L) {
            timestamps.pollFirst();
        }

        boolean warnBefore = plugin.getConfigManager().config().getBoolean("Player Kills.Anti Kill Abuse.Warn Before", true);
        if (timestamps.size() == maxKills - 1 && warnBefore) {
            plugin.getMessageDispatcher().send(killer, "Kill_Abuse_Warning", Map.of("player", victim.getName()));
        }

        if (timestamps.size() >= maxKills) {
            timestamps.clear();
            punish(killer, victim);
            return true;
        }
        return false;
    }

    private void punish(Player killer, Player victim) {
        for (String command : plugin.getConfigManager().config().getStringList("Player Kills.Anti Kill Abuse.Commands on Abuse")) {
            String parsed = command.replace("{player}", killer.getName()).replace("{victim}", victim.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
    }
}

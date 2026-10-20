package de.Z7534.ztone.pvpmanager.managers;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.pvpmanager.permissions.Permissions;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RespawnProtectionManager {

    private final Pvpmanager plugin;
    private final Map<UUID, Long> protectionEndsAt = new ConcurrentHashMap<>();

    public RespawnProtectionManager(Pvpmanager plugin) {
        this.plugin = plugin;
    }

    public boolean isProtected(UUID uuid) {
        Long endsAt = protectionEndsAt.get(uuid);
        if (endsAt == null) {
            return false;
        }
        if (System.currentTimeMillis() >= endsAt) {
            protectionEndsAt.remove(uuid);
            return false;
        }
        return true;
    }

    public long remainingSeconds(UUID uuid) {
        Long endsAt = protectionEndsAt.get(uuid);
        if (endsAt == null) {
            return 0;
        }
        long diff = endsAt - System.currentTimeMillis();
        if (diff <= 0) {
            protectionEndsAt.remove(uuid);
            return 0;
        }
        return Math.max(0, (diff + 999L) / 1000L);
    }

    public void giveProtection(Player player) {
        if (player.hasPermission(Permissions.EXEMPT_RESPAWNPROTECTION) || player.hasPermission(Permissions.EXEMPT)) {
            return;
        }
        long seconds = plugin.getConfigManager().config().getLong("Player Kills.Respawn Protection", 15);
        if (seconds <= 0) {
            return;
        }
        protectionEndsAt.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000L));
        plugin.getMessageDispatcher().sendWithTime(player, "Respawn_Protection_Started", seconds);
    }

    public void removeProtection(UUID uuid) {
        protectionEndsAt.remove(uuid);
    }
}

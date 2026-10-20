package de.Z7534.ztone.pvpmanager.managers;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.pvpmanager.permissions.Permissions;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LootProtectionManager {

    public record ProtectedDrop(UUID killerUuid, long expiresAt) {}

    private final Pvpmanager plugin;
    private final Map<UUID, ProtectedDrop> protectedItems = new ConcurrentHashMap<>();
    private final Map<UUID, ProtectedDrop> protectedOrbs = new ConcurrentHashMap<>();

    public LootProtectionManager(Pvpmanager plugin) {
        this.plugin = plugin;
    }

    public void registerItem(Item item, UUID killerUuid, long seconds) {
        if (item == null || killerUuid == null || seconds <= 0) {
            return;
        }
        protectedItems.put(item.getUniqueId(), new ProtectedDrop(killerUuid, System.currentTimeMillis() + (seconds * 1000L)));
    }

    public void registerOrb(ExperienceOrb orb, UUID killerUuid, long seconds) {
        if (orb == null || killerUuid == null || seconds <= 0) {
            return;
        }
        protectedOrbs.put(orb.getUniqueId(), new ProtectedDrop(killerUuid, System.currentTimeMillis() + (seconds * 1000L)));
    }

    public boolean canPickupItem(Item item, Player player) {
        if (item == null || player == null) {
            return true;
        }
        ProtectedDrop drop = protectedItems.get(item.getUniqueId());
        if (drop == null) {
            return true;
        }
        if (System.currentTimeMillis() >= drop.expiresAt()) {
            protectedItems.remove(item.getUniqueId());
            return true;
        }
        if (player.getUniqueId().equals(drop.killerUuid())) {
            return true;
        }
        return player.hasPermission(Permissions.BYPASS_LOOTPROTECTION)
                || player.hasPermission(Permissions.ADMIN)
                || player.hasPermission(Permissions.WILDCARD);
    }

    public boolean canPickupOrb(ExperienceOrb orb, Player player) {
        if (orb == null || player == null) {
            return true;
        }
        ProtectedDrop drop = protectedOrbs.get(orb.getUniqueId());
        if (drop == null) {
            return true;
        }
        if (System.currentTimeMillis() >= drop.expiresAt()) {
            protectedOrbs.remove(orb.getUniqueId());
            return true;
        }
        if (player.getUniqueId().equals(drop.killerUuid())) {
            return true;
        }
        return player.hasPermission(Permissions.BYPASS_LOOTPROTECTION)
                || player.hasPermission(Permissions.ADMIN)
                || player.hasPermission(Permissions.WILDCARD);
    }

    public long remainingSeconds(Item item) {
        ProtectedDrop drop = protectedItems.get(item.getUniqueId());
        if (drop == null) {
            return 0;
        }
        long diff = drop.expiresAt() - System.currentTimeMillis();
        if (diff <= 0) {
            protectedItems.remove(item.getUniqueId());
            return 0;
        }
        return Math.max(0, (diff + 999L) / 1000L);
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        protectedItems.entrySet().removeIf(entry -> now >= entry.getValue().expiresAt());
        protectedOrbs.entrySet().removeIf(entry -> now >= entry.getValue().expiresAt());
    }
}

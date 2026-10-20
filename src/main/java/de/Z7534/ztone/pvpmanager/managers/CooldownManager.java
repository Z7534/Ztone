package de.Z7534.ztone.pvpmanager.managers;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {

    private final Pvpmanager plugin;
    private final Map<UUID, Long> egappleLastUse = new ConcurrentHashMap<>();

    public CooldownManager(Pvpmanager plugin) {
        this.plugin = plugin;
    }

    public long getEgappleCooldownSeconds() {
        long value = plugin.getConfigManager().config().getLong("Item Cooldowns.Enchanted Golden Apple", -1);
        if (value < 0) {
            value = plugin.getConfigManager().config().getLong("Item Cooldowns.ENCHANTED_GOLDEN_APPLE", 60);
        }
        return Math.max(0, value);
    }

    public boolean isEgappleOnCooldown(Player player) {
        return getEgappleRemaining(player) > 0;
    }

    public long getEgappleRemaining(Player player) {
        long cd = getEgappleCooldownSeconds();
        if (cd <= 0) {
            return 0;
        }
        long last = egappleLastUse.getOrDefault(player.getUniqueId(), 0L);
        long diff = (last + (cd * 1000L)) - System.currentTimeMillis();
        if (diff <= 0) {
            return 0;
        }
        return Math.max(0, (diff + 999L) / 1000L);
    }

    public void triggerEgapple(Player player) {
        long cd = getEgappleCooldownSeconds();
        if (cd <= 0) {
            return;
        }
        egappleLastUse.put(player.getUniqueId(), System.currentTimeMillis());
        try {
            player.setCooldown(Material.ENCHANTED_GOLDEN_APPLE, (int) (cd * 20L));
        } catch (Exception ignored) {
        }
    }
}

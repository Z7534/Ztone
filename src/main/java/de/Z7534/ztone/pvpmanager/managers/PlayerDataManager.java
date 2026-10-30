package de.Z7534.ztone.pvpmanager.managers;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.pvpmanager.database.PlayerDataDAO;
import de.Z7534.ztone.pvpmanager.database.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {

    private final Pvpmanager plugin;
    private final PlayerDataDAO dao;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public PlayerDataManager(Pvpmanager plugin, PlayerDataDAO dao) {
        this.plugin = plugin;
        this.dao = dao;
    }

    public void loadOnJoin(Player player) {
        loadOnJoin(player, null);
    }

    public void loadOnJoin(Player player, Runnable afterLoad) {
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin.getPlugin(), () -> {
            PlayerData data = dao.find(uuid).orElseGet(() -> {
                boolean defaultPvp = plugin.getConfigManager().config().getBoolean("PvP Toggle.Default PvP", false);
                return PlayerData.createDefault(uuid, defaultPvp);
            });
            cache.put(uuid, data);
            if (afterLoad != null) {
                Bukkit.getScheduler().runTask(plugin.getPlugin(), afterLoad);
            }
        });
    }

    public void saveAndUnload(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerData data = cache.remove(uuid);
        if (data != null) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin.getPlugin(), () -> dao.save(data));
        }
    }

    public void saveAll() {
        for (PlayerData data : cache.values()) {
            dao.save(data);
        }
    }

    public PlayerData get(UUID uuid) {
        return cache.computeIfAbsent(uuid, id -> {
            return dao.find(id).orElseGet(() -> {
                boolean defaultPvp = plugin.getConfigManager().config().getBoolean("PvP Toggle.Default PvP", false);
                return PlayerData.createDefault(id, defaultPvp);
            });
        });
    }

    public void save(PlayerData data) {
        cache.put(data.getUuid(), data);
        Bukkit.getScheduler().runTaskAsynchronously(plugin.getPlugin(), () -> dao.save(data));
    }
}

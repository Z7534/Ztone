package de.Z7534.ztone.pvpmanager.managers;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.pvpmanager.database.PlayerDataDAO;
import de.Z7534.ztone.pvpmanager.database.models.PlayerData;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verwaltet Spieler-PvP-Daten rein im Arbeitsspeicher (Session-basiert).
 * Keine dauerhafte Speicherung in der Datenbank (temporaere Daten).
 */
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
        boolean defaultPvp = plugin.getConfigManager().config().getBoolean("PvP Toggle.Default PvP", false);
        cache.computeIfAbsent(uuid, id -> PlayerData.createDefault(id, defaultPvp));

        if (afterLoad != null) {
            afterLoad.run();
        }
    }

    public void saveAndUnload(Player player) {
        UUID uuid = player.getUniqueId();
        cache.remove(uuid);
    }

    public void saveAll() {
        // Nicht persistent in der Datenbank speichern
    }

    public PlayerData get(UUID uuid) {
        return cache.computeIfAbsent(uuid, id -> {
            boolean defaultPvp = plugin.getConfigManager().config().getBoolean("PvP Toggle.Default PvP", false);
            return PlayerData.createDefault(id, defaultPvp);
        });
    }

    public void save(PlayerData data) {
        // Rein im Arbeitsspeicher verwaltet
    }
}

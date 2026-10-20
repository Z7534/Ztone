package de.Z7534.ztone.playrewards.database;

import de.Z7534.ztone.playrewards.PlayReward;
import de.Z7534.ztone.playrewards.model.PlayerData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class DatabaseManager {

    private final PlayReward plugin;
    private final SQLiteStorage sqliteStorage;
    private final Map<UUID, PlayerData> cachedPlayerData = new ConcurrentHashMap<>();

    public DatabaseManager(PlayReward plugin) {
        this.plugin = plugin;
        this.sqliteStorage = new SQLiteStorage(plugin.getPlugin().getDatabaseManager(), plugin.getLogger());
    }

    public void initialize() {
        sqliteStorage.initialize();
    }

    public CompletableFuture<PlayerData> loadPlayerDataAsync(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerData data = sqliteStorage.loadPlayerData(uuid);
            cachedPlayerData.put(uuid, data);
            return data;
        });
    }

    public CompletableFuture<Void> savePlayerDataAsync(PlayerData data) {
        return CompletableFuture.runAsync(() -> sqliteStorage.savePlayerData(data));
    }

    public PlayerData getCachedData(UUID uuid) {
        return cachedPlayerData.get(uuid);
    }

    public void cacheData(PlayerData data) {
        if (data != null) {
            cachedPlayerData.put(data.getUuid(), data);
        }
    }

    public void uncacheData(UUID uuid) {
        cachedPlayerData.remove(uuid);
    }

    public Map<UUID, PlayerData> getAllCachedData() {
        return cachedPlayerData;
    }

    public void close() {
        // Alle verbleibenden Daten synchron vor dem Beenden sichern
        for (PlayerData data : cachedPlayerData.values()) {
            sqliteStorage.savePlayerData(data);
        }
        sqliteStorage.close();
    }
}

package de.Z7534.ztone.pvpmanager.database;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.pvpmanager.database.models.PlayerData;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PlayerDataDAO: Spielerbezogene PvP-Statusdaten sind rein temporaer
 * und werden nicht in der Datenbank persistiert.
 */
public class PlayerDataDAO {

    private final Pvpmanager plugin;
    private final DatabaseManager databaseManager;

    public PlayerDataDAO(Pvpmanager plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public Optional<PlayerData> find(UUID uuid) {
        return Optional.empty();
    }

    public List<PlayerData> findAll() {
        return Collections.emptyList();
    }

    public void save(PlayerData data) {
        // Nicht persistent in der Datenbank speichern
    }
}

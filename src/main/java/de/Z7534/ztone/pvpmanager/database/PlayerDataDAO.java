package de.Z7534.ztone.pvpmanager.database;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.pvpmanager.database.models.PlayerData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public class PlayerDataDAO {

    private final Pvpmanager plugin;
    private final DatabaseManager databaseManager;

    public PlayerDataDAO(Pvpmanager plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public Optional<PlayerData> find(UUID uuid) {
        String sql = "SELECT uuid, pvp_enabled, teleport_override FROM pvpmanager_players WHERE uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new PlayerData(
                            uuid,
                            rs.getBoolean("pvp_enabled"),
                            rs.getBoolean("teleport_override")
                    ));
                }
                return Optional.empty();
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("Fehler beim Laden von PlayerData: " + exception.getMessage());
            return Optional.empty();
        }
    }

    public java.util.List<PlayerData> findAll() {
        java.util.List<PlayerData> results = new java.util.ArrayList<>();
        String sql = "SELECT uuid, pvp_enabled, teleport_override FROM pvpmanager_players";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                results.add(new PlayerData(
                        UUID.fromString(rs.getString("uuid")),
                        rs.getBoolean("pvp_enabled"),
                        rs.getBoolean("teleport_override")
                ));
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("Fehler beim Laden aller PlayerData-Einträge: " + exception.getMessage());
        }
        return results;
    }

    public void save(PlayerData data) {
        String sql = plugin.getClansystem().getDatabaseManager().getDialect().upsert(
                "INSERT INTO pvpmanager_players (uuid, pvp_enabled, teleport_override) VALUES (?, ?, ?)",
                new String[]{"uuid"},
                new String[]{"pvp_enabled", "teleport_override"}
        );

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, data.getUuid().toString());
            statement.setBoolean(2, data.isPvpEnabled());
            statement.setBoolean(3, data.isTeleportOverride());
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().severe("Fehler beim Speichern von PlayerData: " + exception.getMessage());
        }
    }
}

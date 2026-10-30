package de.Z7534.ztone.pvpmanager.database;

import de.Z7534.ztone.pvpmanager.Pvpmanager;
import de.Z7534.ztone.pvpmanager.database.models.PlayerData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PlayerDataDAO {

    private final Pvpmanager plugin;
    private final DatabaseManager databaseManager;

    public PlayerDataDAO(Pvpmanager plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    private String getTableName() {
        String prefix = "";
        if (plugin.getClansystem() != null && plugin.getClansystem().getDatabaseManager() != null) {
            prefix = plugin.getClansystem().getDatabaseManager().getTablePrefix();
            if (prefix == null) prefix = "";
        }
        return prefix + "pvpmanager_players";
    }

    public Optional<PlayerData> find(UUID uuid) {
        String table = getTableName();
        String sql = "SELECT uuid, pvp_enabled, teleport_override FROM " + table + " WHERE uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    boolean pvpEnabled = rs.getInt("pvp_enabled") == 1;
                    boolean teleportOverride = rs.getInt("teleport_override") == 1;
                    return Optional.of(new PlayerData(uuid, pvpEnabled, teleportOverride));
                }
            }
        } catch (SQLException e) {
            if (!table.equals("pvpmanager_players")) {
                try (Connection connection = databaseManager.getConnection();
                     PreparedStatement ps = connection.prepareStatement("SELECT uuid, pvp_enabled, teleport_override FROM pvpmanager_players WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            boolean pvpEnabled = rs.getInt("pvp_enabled") == 1;
                            boolean teleportOverride = rs.getInt("teleport_override") == 1;
                            return Optional.of(new PlayerData(uuid, pvpEnabled, teleportOverride));
                        }
                    }
                } catch (SQLException ignored) {}
            }
            plugin.getLogger().warning("Fehler beim Laden von PlayerData fuer " + uuid + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    public List<PlayerData> findAll() {
        List<PlayerData> list = new ArrayList<>();
        String table = getTableName();
        String sql = "SELECT uuid, pvp_enabled, teleport_override FROM " + table;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                boolean pvpEnabled = rs.getInt("pvp_enabled") == 1;
                boolean teleportOverride = rs.getInt("teleport_override") == 1;
                list.add(new PlayerData(uuid, pvpEnabled, teleportOverride));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Fehler beim Laden aller PlayerData: " + e.getMessage());
        }
        return list;
    }

    public void save(PlayerData data) {
        String table = getTableName();
        String sql = "INSERT INTO " + table + " (uuid, pvp_enabled, teleport_override) VALUES (?, ?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET pvp_enabled = excluded.pvp_enabled, teleport_override = excluded.teleport_override";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, data.getUuid().toString());
            ps.setInt(2, data.isPvpEnabled() ? 1 : 0);
            ps.setInt(3, data.isTeleportOverride() ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement ps = connection.prepareStatement("REPLACE INTO " + table + " (uuid, pvp_enabled, teleport_override) VALUES (?, ?, ?)")) {
                ps.setString(1, data.getUuid().toString());
                ps.setInt(2, data.isPvpEnabled() ? 1 : 0);
                ps.setInt(3, data.isTeleportOverride() ? 1 : 0);
                ps.executeUpdate();
            } catch (SQLException ex) {
                plugin.getLogger().severe("Fehler beim Speichern von PlayerData fuer " + data.getUuid() + ": " + ex.getMessage());
            }
        }
    }
}

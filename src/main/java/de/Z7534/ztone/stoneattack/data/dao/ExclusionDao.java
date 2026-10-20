package de.Z7534.ztone.stoneattack.data.dao;

import de.Z7534.ztone.stoneattack.data.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Persistiert die Ausschlussliste (removeplayer/addplayer). */
public class ExclusionDao {

    private final Database database;

    public ExclusionDao(Database database) {
        this.database = database;
    }

    public void exclude(UUID uuid, String leaderboardType) {
        String sql = "INSERT OR IGNORE INTO stoneattack_exclusions (uuid, leaderboard_type) VALUES (?, ?)";
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, leaderboardType);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Fehler beim Ausschliessen eines Spielers", e);
        }
    }

    public void include(UUID uuid, String leaderboardType) {
        String sql = "DELETE FROM stoneattack_exclusions WHERE uuid = ? AND leaderboard_type = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, leaderboardType);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Fehler beim Wiederzulassen eines Spielers", e);
        }
    }

    public Set<UUID> loadExcluded(String leaderboardType) {
        Set<UUID> result = new HashSet<>();
        String sql = "SELECT uuid FROM stoneattack_exclusions WHERE leaderboard_type = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, leaderboardType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(UUID.fromString(rs.getString("uuid")));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Fehler beim Laden der Ausschlussliste", e);
        }
        return result;
    }
}

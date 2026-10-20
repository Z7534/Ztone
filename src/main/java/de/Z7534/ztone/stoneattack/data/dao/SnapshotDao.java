package de.Z7534.ztone.stoneattack.data.dao;

import de.Z7534.ztone.stoneattack.data.Database;
import de.Z7534.ztone.stoneattack.data.model.SnapshotRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Alle Methoden hier sind synchrones JDBC - IMMER aus einem
 * Async-Kontext aufrufen (siehe LeaderboardCache/PeriodResetManager),
 * niemals vom Main-Thread aus.
 */
public class SnapshotDao {

    private final Database database;

    public SnapshotDao(Database database) {
        this.database = database;
    }

    /** Laedt alle Basiswerte fuer einen Zeitraum+Typ, Key = UUID. */
    public Map<UUID, SnapshotRecord> loadAll(String leaderboardType, String period) {
        Map<UUID, SnapshotRecord> result = new HashMap<>();
        String sql = "SELECT uuid, last_name, leaderboard_type, period, base_value, period_start "
                + "FROM stoneattack_snapshots WHERE leaderboard_type = ? AND period = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, leaderboardType);
            ps.setString(2, period);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    result.put(uuid, new SnapshotRecord(
                            uuid,
                            rs.getString("last_name"),
                            rs.getString("leaderboard_type"),
                            rs.getString("period"),
                            rs.getDouble("base_value"),
                            rs.getLong("period_start")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Fehler beim Laden der Snapshots", e);
        }
        return result;
    }

    public void upsert(SnapshotRecord record) {
        String sql = """
                INSERT INTO stoneattack_snapshots (uuid, last_name, leaderboard_type, period, base_value, period_start)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid, leaderboard_type, period)
                DO UPDATE SET last_name = excluded.last_name,
                              base_value = excluded.base_value,
                              period_start = excluded.period_start
                """;
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, record.uuid().toString());
            ps.setString(2, record.lastName());
            ps.setString(3, record.leaderboardType());
            ps.setString(4, record.period());
            ps.setDouble(5, record.baseValue());
            ps.setLong(6, record.periodStart());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Fehler beim Speichern eines Snapshots", e);
        }
    }

    public void deleteAll(String leaderboardType, String period) {
        String sql = "DELETE FROM stoneattack_snapshots WHERE leaderboard_type = ? AND period = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, leaderboardType);
            ps.setString(2, period);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Fehler beim Zuruecksetzen der Snapshots", e);
        }
    }

    /** Zuletzt bekannter Periodenbeginn (Epoch-Millis), oder -1 falls unbekannt. */
    public long getPeriodStart(String period) {
        String sql = "SELECT period_start FROM stoneattack_period_state WHERE period = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, period);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("period_start");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Fehler beim Lesen des Periodenstatus", e);
        }
        return -1L;
    }

    public void setPeriodStart(String period, long periodStart) {
        String sql = """
                INSERT INTO stoneattack_period_state (period, period_start) VALUES (?, ?)
                ON CONFLICT(period) DO UPDATE SET period_start = excluded.period_start
                """;
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, period);
            ps.setLong(2, periodStart);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Fehler beim Speichern des Periodenstatus", e);
        }
    }
}

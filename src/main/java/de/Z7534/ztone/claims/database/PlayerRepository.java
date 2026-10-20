package de.Z7534.ztone.claims.database;

import de.Z7534.ztone.claims.model.PlayerData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PlayerRepository {

    private final Database db;
    private final Logger logger;

    public PlayerRepository(Database db, Logger logger) {
        this.db = db;
        this.logger = logger;
    }

    /**
     * Reiht einen Schreibzugriff in die gemeinsame, geordnete Warteschlange ein.
     */
    public void async(Runnable task) {
        db.runAsync(task);
    }

    public Map<UUID, PlayerData> loadAll() {
        Map<UUID, PlayerData> result = new HashMap<>();
        String sql = "SELECT * FROM claims_players";

        synchronized (db) {
            Connection c = db.connection();
            try (PreparedStatement ps = c.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PlayerData pd = fromRow(rs);
                    result.put(pd.uuid(), pd);
                }
            } catch (SQLException | IllegalArgumentException e) {
                logger.log(Level.SEVERE, "Fehler beim Laden der Spielerdaten", e);
            }
        }
        return result;
    }

    public Optional<PlayerData> findByName(String name) {
        String sql = "SELECT * FROM claims_players WHERE LOWER(name) = LOWER(?) LIMIT 1";
        synchronized (db) {
            Connection c = db.connection();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(fromRow(rs));
                    }
                }
            } catch (SQLException | IllegalArgumentException e) {
                logger.log(Level.SEVERE, "Fehler bei der Spielersuche", e);
            }
        }
        return Optional.empty();
    }

    public void upsert(PlayerData pd) {
        String sql = """
            INSERT INTO claims_players (uuid, name, bonus_claim_slots, playtime_seconds, board_line_enabled,
                                  fence_line_enabled, entry_hint_enabled, explosions_enabled, claim_map_enabled, last_seen)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                name = excluded.name,
                bonus_claim_slots = excluded.bonus_claim_slots,
                playtime_seconds = excluded.playtime_seconds,
                board_line_enabled = excluded.board_line_enabled,
                fence_line_enabled = excluded.fence_line_enabled,
                entry_hint_enabled = excluded.entry_hint_enabled,
                explosions_enabled = excluded.explosions_enabled,
                claim_map_enabled = excluded.claim_map_enabled,
                last_seen = excluded.last_seen
            """;
        synchronized (db) {
            Connection c = db.connection();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, pd.uuid().toString());
                ps.setString(2, pd.name());
                ps.setInt(3, pd.bonusClaimSlots());
                ps.setLong(4, pd.playtimeSeconds());
                ps.setInt(5, pd.boardLineEnabled() ? 1 : 0);
                ps.setInt(6, pd.fenceLineEnabled() ? 1 : 0);
                ps.setInt(7, pd.entryHintEnabled() ? 1 : 0);
                ps.setInt(8, pd.explosionsEnabled() ? 1 : 0);
                ps.setInt(9, pd.claimMapEnabled() ? 1 : 0);
                ps.setLong(10, pd.lastSeen());
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Fehler beim Speichern der Spielerdaten", e);
            }
        }
    }

    private PlayerData fromRow(ResultSet rs) throws SQLException {
        PlayerData pd = new PlayerData(UUID.fromString(rs.getString("uuid")), rs.getString("name"));
        pd.setBonusClaimSlots(rs.getInt("bonus_claim_slots"));
        pd.setPlaytimeSeconds(rs.getLong("playtime_seconds"));
        pd.setBoardLineEnabled(rs.getInt("board_line_enabled") == 1);
        pd.setFenceLineEnabled(rs.getInt("fence_line_enabled") == 1);
        pd.setEntryHintEnabled(rs.getInt("entry_hint_enabled") == 1);
        pd.setExplosionsEnabled(rs.getInt("explosions_enabled") == 1);
        try {
            pd.setClaimMapEnabled(rs.getInt("claim_map_enabled") == 1);
        } catch (SQLException ignored) {
            pd.setClaimMapEnabled(true);
        }
        pd.setLastSeen(rs.getLong("last_seen"));
        return pd;
    }
}

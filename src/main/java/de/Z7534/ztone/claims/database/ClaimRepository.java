package de.Z7534.ztone.claims.database;

import de.Z7534.ztone.claims.model.Claim;
import de.Z7534.ztone.claims.model.ChunkPos;
import de.Z7534.ztone.claims.model.TrustedPlayer;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ClaimRepository {

    private final Database db;
    private final Logger logger;

    public ClaimRepository(Database db, Logger logger) {
        this.db = db;
        this.logger = logger;
    }

    /**
     * Reiht einen Schreibzugriff in die gemeinsame, geordnete Warteschlange ein.
     */
    public void async(Runnable task) {
        db.runAsync(task);
    }

    public List<Claim> loadAll() {
        Map<Long, Claim> claims = new HashMap<>();

        synchronized (db) {
            Connection c = db.connection();
            try {
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT * FROM claims_claims")) {
                    while (rs.next()) {
                        long id = rs.getLong("id");
                        Claim claim = new Claim(id, UUID.fromString(rs.getString("owner_uuid")),
                                rs.getString("name"), rs.getString("world"));
                        claim.setVisitorPermissionMask(rs.getInt("visitor_perm_mask"));
                        claim.setCreatedAt(rs.getLong("created_at"));
                        try {
                            claim.setClanTrustEnabled(rs.getInt("clan_trust_enabled") == 1);
                        } catch (Exception ignored) {}
                        try {
                            claim.setAllyTrustEnabled(rs.getInt("ally_trust_enabled") == 1);
                        } catch (Exception ignored) {}
                        claims.put(id, claim);
                    }
                }
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT * FROM claims_chunks")) {
                    while (rs.next()) {
                        Claim claim = claims.get(rs.getLong("claim_id"));
                        if (claim != null) {
                            claim.chunks().add(new ChunkPos(claim.world(), rs.getInt("chunk_x"), rs.getInt("chunk_z")));
                        }
                    }
                }
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT * FROM claims_trust")) {
                    while (rs.next()) {
                        Claim claim = claims.get(rs.getLong("claim_id"));
                        if (claim != null) {
                            UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                            claim.trusted().put(uuid, new TrustedPlayer(uuid, rs.getInt("perm_mask")));
                        }
                    }
                }
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT * FROM claims_bans")) {
                    while (rs.next()) {
                        Claim claim = claims.get(rs.getLong("claim_id"));
                        if (claim != null) {
                            claim.banned().add(UUID.fromString(rs.getString("player_uuid")));
                        }
                    }
                }
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT * FROM claims_managers")) {
                    while (rs.next()) {
                        Claim claim = claims.get(rs.getLong("claim_id"));
                        if (claim != null) {
                            claim.managers().add(UUID.fromString(rs.getString("player_uuid")));
                        }
                    }
                } catch (SQLException ignored) {}
            } catch (SQLException | IllegalArgumentException e) {
                logger.log(Level.SEVERE, "Fehler beim Laden der Claims", e);
            }
        }
        return new ArrayList<>(claims.values());
    }

    /**
     * Legt einen Claim samt Chunks an (in einer Transaktion). Wird synchron aufgerufen,
     * weil die generierte ID sofort gebraucht wird. Liefert -1 bei einem Fehler.
     */
    public long insertClaim(Claim claim) {
        String sql = "INSERT INTO claims_claims (owner_uuid, name, world, visitor_perm_mask, created_at) VALUES (?, ?, ?, ?, ?)";
        synchronized (db) {
            long[] result = {-1};
            try {
                db.inTransaction(c -> {
                    long id;
                    try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, claim.owner().toString());
                        ps.setString(2, claim.name());
                        ps.setString(3, claim.world());
                        ps.setInt(4, claim.visitorPermissionMask());
                        ps.setLong(5, claim.createdAt());
                        ps.executeUpdate();
                        try (ResultSet keys = ps.getGeneratedKeys()) {
                            if (!keys.next()) {
                                throw new SQLException("Keine ID für den neuen Claim erhalten");
                            }
                            id = keys.getLong(1);
                        }
                    }
                    writeChunks(c, id, claim.chunks());
                    result[0] = id;
                });
                claim.setId(result[0]);
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Fehler beim Anlegen eines Claims", e);
                return -1;
            }
            return result[0];
        }
    }

    public void deleteClaim(long claimId) {
        synchronized (db) {
            Connection c = db.connection();
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM claims_claims WHERE id = ?")) {
                ps.setLong(1, claimId);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Fehler beim Löschen eines Claims", e);
            }
        }
    }

    public void updateName(long claimId, String name) {
        synchronized (db) {
            Connection c = db.connection();
            try (PreparedStatement ps = c.prepareStatement("UPDATE claims_claims SET name = ? WHERE id = ?")) {
                ps.setString(1, name);
                ps.setLong(2, claimId);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Fehler beim Umbenennen eines Claims", e);
            }
        }
    }

    public void updateOwner(long claimId, UUID owner) {
        synchronized (db) {
            Connection c = db.connection();
            try (PreparedStatement ps = c.prepareStatement("UPDATE claims_claims SET owner_uuid = ? WHERE id = ?")) {
                ps.setString(1, owner.toString());
                ps.setLong(2, claimId);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Fehler beim Ändern des Besitzers", e);
            }
        }
    }

    public void updateVisitorMask(long claimId, int mask) {
        synchronized (db) {
            Connection c = db.connection();
            try (PreparedStatement ps = c.prepareStatement("UPDATE claims_claims SET visitor_perm_mask = ? WHERE id = ?")) {
                ps.setInt(1, mask);
                ps.setLong(2, claimId);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Fehler beim Speichern der Besucherrechte", e);
            }
        }
    }

    /**
     * Ersetzt alle Chunks eines Claims in einer Transaktion: Entweder wird alles
     * übernommen oder (bei einem Fehler) nichts verändert, es gehen also keine Chunks
     * verloren, wenn zwischen Löschen und Einfügen etwas schiefgeht.
     */
    public void replaceChunks(long claimId, Set<ChunkPos> chunks) {
        synchronized (db) {
            try {
                db.inTransaction(c -> writeChunks(c, claimId, chunks));
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Fehler beim Speichern der Chunks", e);
            }
        }
    }

    private void writeChunks(Connection c, long claimId, Set<ChunkPos> chunks) throws SQLException {
        try (PreparedStatement del = c.prepareStatement("DELETE FROM claims_chunks WHERE claim_id = ?")) {
            del.setLong(1, claimId);
            del.executeUpdate();
        }
        try (PreparedStatement ins = c.prepareStatement(
                "INSERT INTO claims_chunks (claim_id, chunk_x, chunk_z) VALUES (?, ?, ?)")) {
            for (ChunkPos pos : chunks) {
                ins.setLong(1, claimId);
                ins.setInt(2, pos.x());
                ins.setInt(3, pos.z());
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }

    public void setTrust(long claimId, UUID player, int mask) {
        String sql = """
            INSERT INTO claims_trust (claim_id, player_uuid, perm_mask) VALUES (?, ?, ?)
            ON CONFLICT(claim_id, player_uuid) DO UPDATE SET perm_mask = excluded.perm_mask
            """;
        synchronized (db) {
            Connection c = db.connection();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setLong(1, claimId);
                ps.setString(2, player.toString());
                ps.setInt(3, mask);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Fehler beim Speichern von Trust", e);
            }
        }
    }

    public void removeTrust(long claimId, UUID player) {
        synchronized (db) {
            Connection c = db.connection();
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM claims_trust WHERE claim_id = ? AND player_uuid = ?")) {
                ps.setLong(1, claimId);
                ps.setString(2, player.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Fehler beim Entfernen von Trust", e);
            }
        }
    }

    public void setBan(long claimId, UUID player) {
        String sql = "INSERT OR IGNORE INTO claims_bans (claim_id, player_uuid) VALUES (?, ?)";
        synchronized (db) {
            Connection c = db.connection();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setLong(1, claimId);
                ps.setString(2, player.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Fehler beim Speichern eines Bans", e);
            }
        }
    }

    public void removeBan(long claimId, UUID player) {
        synchronized (db) {
            Connection c = db.connection();
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM claims_bans WHERE claim_id = ? AND player_uuid = ?")) {
                ps.setLong(1, claimId);
                ps.setString(2, player.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Fehler beim Entfernen eines Bans", e);
            }
        }
    }

    public void setClanTrust(long claimId, boolean enabled) {
        synchronized (db) {
            Connection c = db.connection();
            try (PreparedStatement ps = c.prepareStatement("UPDATE claims_claims SET clan_trust_enabled = ? WHERE id = ?")) {
                ps.setInt(1, enabled ? 1 : 0);
                ps.setLong(2, claimId);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Fehler beim Speichern von Clan-Trust", e);
            }
        }
    }

    public void setAllyTrust(long claimId, boolean enabled) {
        synchronized (db) {
            Connection c = db.connection();
            try (PreparedStatement ps = c.prepareStatement("UPDATE claims_claims SET ally_trust_enabled = ? WHERE id = ?")) {
                ps.setInt(1, enabled ? 1 : 0);
                ps.setLong(2, claimId);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Fehler beim Speichern von Allianz-Trust", e);
            }
        }
    }

    public void setManager(long claimId, UUID player) {
        String sql = "INSERT OR IGNORE INTO claims_managers (claim_id, player_uuid) VALUES (?, ?)";
        synchronized (db) {
            Connection c = db.connection();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setLong(1, claimId);
                ps.setString(2, player.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Fehler beim Speichern eines Truster/Managers", e);
            }
        }
    }

    public void removeManager(long claimId, UUID player) {
        synchronized (db) {
            Connection c = db.connection();
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM claims_managers WHERE claim_id = ? AND player_uuid = ?")) {
                ps.setLong(1, claimId);
                ps.setString(2, player.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Fehler beim Entfernen eines Truster/Managers", e);
            }
        }
    }
}

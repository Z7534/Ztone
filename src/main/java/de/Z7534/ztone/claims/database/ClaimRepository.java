package de.Z7534.ztone.claims.database;

import de.Z7534.ztone.claims.model.Claim;
import de.Z7534.ztone.claims.model.ChunkPos;
import de.Z7534.ztone.claims.model.TrustedPlayer;
import de.Z7534.ztone.claims.model.ClaimLogEntry;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ClaimRepository {

    private final Database db;
    private final Logger logger;

    private record ClaimLogCandidate(long id, String name, String world, UUID ownerUuid, String playerName, long createdAt) {
    }

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
                        try {
                            claim.setBanAll(rs.getInt("ban_all") == 1);
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
        String sql = "INSERT INTO claims_claims (owner_uuid, name, world, visitor_perm_mask, created_at, clan_trust_enabled, ally_trust_enabled, ban_all) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
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
                        ps.setInt(6, claim.isClanTrustEnabled() ? 1 : 0);
                        ps.setInt(7, claim.isAllyTrustEnabled() ? 1 : 0);
                        ps.setInt(8, claim.isBanAll() ? 1 : 0);
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

    public void setBanAll(long claimId, boolean enabled) {
        synchronized (db) {
            Connection c = db.connection();
            try (PreparedStatement ps = c.prepareStatement("UPDATE claims_claims SET ban_all = ? WHERE id = ?")) {
                ps.setInt(1, enabled ? 1 : 0);
                ps.setLong(2, claimId);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Fehler beim Speichern von Ban-All", e);
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

    /**
     * Fügt einen Logeintrag synchron in die Datenbank ein.
     */
    public void insertLog(Long claimId, String claimName, String world, UUID playerUuid, String playerName,
                          String action, String details, Collection<ChunkPos> chunks) {
        synchronized (db) {
            insertLogInternal(db.connection(), claimId, claimName, world, playerUuid, playerName, action, details, System.currentTimeMillis(), chunks);
        }
    }

    /**
     * Fügt einen Logeintrag asynchron in die gemeinsame Warteschlange ein.
     */
    public void logAsync(Long claimId, String claimName, String world, UUID playerUuid, String playerName,
                         String action, String details, Collection<ChunkPos> chunks) {
        final Set<ChunkPos> chunkCopy = (chunks != null && !chunks.isEmpty()) ? new HashSet<>(chunks) : Collections.emptySet();
        async(() -> insertLog(claimId, claimName, world, playerUuid, playerName, action, details, chunkCopy));
    }

    private void insertLogInternal(Connection c, Long claimId, String claimName, String world,
                                   UUID playerUuid, String playerName, String action,
                                   String details, long timestamp, Collection<ChunkPos> chunks) {
        String insertLogSql = "INSERT INTO claims_logs (claim_id, claim_name, world, player_uuid, player_name, action, timestamp, details) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(insertLogSql, Statement.RETURN_GENERATED_KEYS)) {
            if (claimId != null && claimId > 0) {
                ps.setLong(1, claimId);
            } else {
                ps.setNull(1, Types.BIGINT);
            }
            ps.setString(2, claimName != null ? claimName : "Unbekannt");
            ps.setString(3, world != null ? world : "world");
            ps.setString(4, playerUuid != null ? playerUuid.toString() : "");
            ps.setString(5, playerName != null ? playerName : "Unbekannt");
            ps.setString(6, action != null ? action : "CLAIM");
            ps.setLong(7, timestamp > 0 ? timestamp : System.currentTimeMillis());
            ps.setString(8, details != null ? details : "");
            ps.executeUpdate();

            long logId = -1;
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    logId = rs.getLong(1);
                }
            }

            if (logId > 0 && chunks != null && !chunks.isEmpty()) {
                String chunkSql = "INSERT INTO claims_log_chunks (log_id, chunk_x, chunk_z) VALUES (?, ?, ?)";
                try (PreparedStatement cps = c.prepareStatement(chunkSql)) {
                    for (ChunkPos pos : chunks) {
                        cps.setLong(1, logId);
                        cps.setInt(2, pos.x());
                        cps.setInt(3, pos.z());
                        cps.addBatch();
                    }
                    cps.executeBatch();
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Fehler beim Speichern eines Claim-Logs", e);
        }
    }

    /**
     * Extrahiert bestehende Claims aus der Datenbank und erzeugt initiale Logeinträge,
     * falls für diese Claims noch keine Logs vorliegen.
     */
    public void migrateExistingClaimsToLogs() {
        synchronized (db) {
            Connection c = db.connection();
            String checkSql = """
                SELECT c.id, c.owner_uuid, c.name, c.world, c.created_at, p.name AS player_name
                FROM claims_claims c
                LEFT JOIN claims_players p ON c.owner_uuid = p.uuid
                WHERE NOT EXISTS (SELECT 1 FROM claims_logs l WHERE l.claim_id = c.id)
                """;
            List<ClaimLogCandidate> candidates = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(checkSql)) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    String ownerStr = rs.getString("owner_uuid");
                    String name = rs.getString("name");
                    String world = rs.getString("world");
                    long createdAt = rs.getLong("created_at");
                    String playerName = rs.getString("player_name");
                    UUID ownerUuid = null;
                    try {
                        ownerUuid = UUID.fromString(ownerStr);
                    } catch (Exception ignored) {}
                    if (playerName == null || playerName.isBlank()) {
                        if (ownerUuid != null) {
                            org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(ownerUuid);
                            playerName = op.getName() != null ? op.getName() : ownerStr;
                        } else {
                            playerName = ownerStr;
                        }
                    }
                    candidates.add(new ClaimLogCandidate(id, name, world, ownerUuid, playerName, createdAt));
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Fehler beim Abfragen bestehender Claims für Log-Migration", e);
                return;
            }

            if (candidates.isEmpty()) {
                return;
            }

            int count = 0;
            for (ClaimLogCandidate cand : candidates) {
                Set<ChunkPos> chunks = new HashSet<>();
                try (PreparedStatement ps = c.prepareStatement("SELECT chunk_x, chunk_z FROM claims_chunks WHERE claim_id = ?")) {
                    ps.setLong(1, cand.id);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            chunks.add(new ChunkPos(cand.world, rs.getInt("chunk_x"), rs.getInt("chunk_z")));
                        }
                    }
                } catch (SQLException e) {
                    logger.warning("Konnte Chunks für Claim ID " + cand.id + " nicht laden: " + e.getMessage());
                }

                long ts = cand.createdAt > 0 ? cand.createdAt : System.currentTimeMillis();
                String details = "Initialer Claim aus Datenbank extrahiert (" + chunks.size() + " Chunks)";
                insertLogInternal(c, cand.id, cand.name, cand.world, cand.ownerUuid != null ? cand.ownerUuid : UUID.randomUUID(), cand.playerName, "CLAIM", details, ts, chunks);
                count++;
            }
            if (count > 0) {
                logger.info("Claims-Log: " + count + " bestehende Claims erfolgreich in die Log-Datenbank migriert.");
            }
        }
    }

    /**
     * Ruft Logeinträge für einen Standort (Chunk + Claim-ID falls vorhanden) ab.
     */
    public List<ClaimLogEntry> getLogsForLocation(String world, int chunkX, int chunkZ, Long claimId, int limit) {
        List<ClaimLogEntry> list = new ArrayList<>();
        synchronized (db) {
            Connection c = db.connection();
            String sql;
            if (claimId != null && claimId > 0) {
                sql = """
                    SELECT DISTINCT l.id, l.claim_id, l.claim_name, l.world, l.player_uuid, l.player_name, l.action, l.timestamp, l.details
                    FROM claims_logs l
                    LEFT JOIN claims_log_chunks c ON l.id = c.log_id
                    WHERE l.claim_id = ? OR (l.world = ? AND c.chunk_x = ? AND c.chunk_z = ?)
                    ORDER BY l.timestamp DESC, l.id DESC
                    LIMIT ?
                    """;
            } else {
                sql = """
                    SELECT DISTINCT l.id, l.claim_id, l.claim_name, l.world, l.player_uuid, l.player_name, l.action, l.timestamp, l.details
                    FROM claims_logs l
                    JOIN claims_log_chunks c ON l.id = c.log_id
                    WHERE l.world = ? AND c.chunk_x = ? AND c.chunk_z = ?
                    ORDER BY l.timestamp DESC, l.id DESC
                    LIMIT ?
                    """;
            }
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                if (claimId != null && claimId > 0) {
                    ps.setLong(1, claimId);
                    ps.setString(2, world);
                    ps.setInt(3, chunkX);
                    ps.setInt(4, chunkZ);
                    ps.setInt(5, limit);
                } else {
                    ps.setString(1, world);
                    ps.setInt(2, chunkX);
                    ps.setInt(3, chunkZ);
                    ps.setInt(4, limit);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long id = rs.getLong("id");
                        long cId = rs.getLong("claim_id");
                        Long claimIdObj = rs.wasNull() ? null : cId;
                        String claimName = rs.getString("claim_name");
                        String w = rs.getString("world");
                        String uuidStr = rs.getString("player_uuid");
                        UUID uuid = null;
                        try {
                            if (uuidStr != null && !uuidStr.isBlank()) {
                                uuid = UUID.fromString(uuidStr);
                            }
                        } catch (Exception ignored) {}
                        String playerName = rs.getString("player_name");
                        String action = rs.getString("action");
                        long ts = rs.getLong("timestamp");
                        String details = rs.getString("details");
                        list.add(new ClaimLogEntry(id, claimIdObj, claimName, w, uuid, playerName, action, ts, details));
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Fehler beim Abrufen der Claim-Logs", e);
            }
        }
        return list;
    }

    /**
     * Ruft Logeinträge für eine bestimmte Claim-ID ab.
     */
    public List<ClaimLogEntry> getLogsForClaim(long claimId, int limit) {
        List<ClaimLogEntry> list = new ArrayList<>();
        synchronized (db) {
            Connection c = db.connection();
            String sql = """
                SELECT l.id, l.claim_id, l.claim_name, l.world, l.player_uuid, l.player_name, l.action, l.timestamp, l.details
                FROM claims_logs l
                WHERE l.claim_id = ?
                ORDER BY l.timestamp DESC, l.id DESC
                LIMIT ?
                """;
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setLong(1, claimId);
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long id = rs.getLong("id");
                        long cId = rs.getLong("claim_id");
                        Long claimIdObj = rs.wasNull() ? null : cId;
                        String claimName = rs.getString("claim_name");
                        String w = rs.getString("world");
                        String uuidStr = rs.getString("player_uuid");
                        UUID uuid = null;
                        try {
                            if (uuidStr != null && !uuidStr.isBlank()) {
                                uuid = UUID.fromString(uuidStr);
                            }
                        } catch (Exception ignored) {}
                        String playerName = rs.getString("player_name");
                        String action = rs.getString("action");
                        long ts = rs.getLong("timestamp");
                        String details = rs.getString("details");
                        list.add(new ClaimLogEntry(id, claimIdObj, claimName, w, uuid, playerName, action, ts, details));
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Fehler beim Abrufen der Claim-Logs für Claim ID " + claimId, e);
            }
        }
        return list;
    }
}

package de.Z7534.ztone.clansystem.managers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.models.*;
import de.Z7534.ztone.clansystem.storage.MariaDbDialect;
import de.Z7534.ztone.clansystem.storage.SqlDialect;
import de.Z7534.ztone.clansystem.storage.SqliteDialect;
import de.Z7534.ztone.clansystem.storage.StorageType;
import de.Z7534.ztone.clansystem.utils.ItemSerializer;
import org.bukkit.inventory.ItemStack;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class DatabaseManager {

    private final Ztone plugin;
    private HikariDataSource dataSource;
    private String tablePrefix;
    private SqlDialect dialect;
    private ExecutorService dbExecutor;

    public DatabaseManager(Ztone plugin) {
        this.plugin = plugin;
    }

    public boolean connect() {
        ConfigManager config = plugin.getConfigManager();
        tablePrefix = config.getDbTablePrefix();
        dialect = createDialect(config.getStorageType());

        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            HikariConfig hikariConfig = new HikariConfig();
            dialect.configureDataSource(hikariConfig, config, plugin.getDataFolder());
            dataSource = new HikariDataSource(hikariConfig);
            dbExecutor = createExecutor();

            try (Connection conn = dataSource.getConnection()) {
                dialect.afterConnect(conn);
            }

            if (dialect.type() == StorageType.SQLITE) {
                try (Connection conn = dataSource.getConnection();
                     Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
                    boolean hasClanClans = false;
                    boolean hasZtoneClans = false;
                    while (rs.next()) {
                        String t = rs.getString(1);
                        if ("clan_clans".equalsIgnoreCase(t)) hasClanClans = true;
                        if ("ztone_clans".equalsIgnoreCase(t)) hasZtoneClans = true;
                    }
                    if (hasClanClans && !hasZtoneClans) {
                        tablePrefix = "clan_";
                        plugin.getLogger().info("Bestehende Clan-Datenbank mit 'clan_'-Präfix erkannt - verwende 'clan_'.");
                    }
                } catch (SQLException ignored) {}
            }

            plugin.getLogger().info("Datenbankverbindung hergestellt! (" + dialect.type() + ")");
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Fehler beim Verbinden zur Datenbank: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private ExecutorService createExecutor() {
        AtomicInteger counter = new AtomicInteger(1);
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "Ztone-DB-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newCachedThreadPool(threadFactory);
    }

    private SqlDialect createDialect(StorageType storageType) {
        if (storageType == StorageType.MARIADB) {
            return new MariaDbDialect();
        }
        return new SqliteDialect();
    }

    public void disconnect() {
        if (dbExecutor != null && !dbExecutor.isShutdown()) {
            dbExecutor.shutdown();
            try {
                if (!dbExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    dbExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                dbExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Datenbankverbindung geschlossen.");
        }
    }

    public HikariDataSource getDataSource() {
        return dataSource;
    }

    public Ztone getPlugin() {
        return plugin;
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void createTables() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + tablePrefix + "clans (" +
                            "id " + dialect.autoIncrementPrimaryKey() + "," +
                            "name VARCHAR(32) UNIQUE NOT NULL," +
                            "suffix VARCHAR(32) DEFAULT ''," +
                            "level INT DEFAULT 1," +
                            "points INT DEFAULT 0," +
                            "leader_uuid VARCHAR(36) NOT NULL," +
                            "created_at BIGINT NOT NULL," +
                            "join_type VARCHAR(16) DEFAULT 'INVITE'," +
                            "icon VARCHAR(64) DEFAULT 'SHIELD'," +
                            "glow_enabled INTEGER DEFAULT 0," +
                            "glow_color VARCHAR(32) DEFAULT 'WHITE'," +
                            "name_color VARCHAR(8) DEFAULT '&f'," +
                            "name_strikethrough INTEGER DEFAULT 0," +
                            "gradient_enabled INTEGER DEFAULT 0," +
                            "gradient_color_start VARCHAR(9) DEFAULT '#FF0000'," +
                            "gradient_color_end VARCHAR(9) DEFAULT '#0000FF'" +
                            ")"
            );

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + tablePrefix + "members (" +
                            "clan_id INT NOT NULL," +
                            "player_uuid VARCHAR(36) NOT NULL," +
                            "rank_id INT NOT NULL," +
                            "joined_at BIGINT NOT NULL," +
                            "PRIMARY KEY (player_uuid)," +
                            "FOREIGN KEY (clan_id) REFERENCES " + tablePrefix + "clans(id) ON DELETE CASCADE" +
                            ")"
            );

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + tablePrefix + "ranks (" +
                            "id " + dialect.autoIncrementPrimaryKey() + "," +
                            "clan_id INT NOT NULL," +
                            "name VARCHAR(32) NOT NULL," +
                            "priority INT DEFAULT 0," +
                            "permissions TEXT," +
                            "FOREIGN KEY (clan_id) REFERENCES " + tablePrefix + "clans(id) ON DELETE CASCADE" +
                            ")"
            );

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + tablePrefix + "homes (" +
                            "id " + dialect.autoIncrementPrimaryKey() + "," +
                            "clan_id INT NOT NULL," +
                            "name VARCHAR(32) NOT NULL," +
                            "world VARCHAR(64) NOT NULL," +
                            "x DOUBLE NOT NULL," +
                            "y DOUBLE NOT NULL," +
                            "z DOUBLE NOT NULL," +
                            "yaw FLOAT NOT NULL," +
                            "pitch FLOAT NOT NULL," +
                            "UNIQUE (clan_id, name)," +
                            "FOREIGN KEY (clan_id) REFERENCES " + tablePrefix + "clans(id) ON DELETE CASCADE" +
                            ")"
            );

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + tablePrefix + "warps (" +
                            "id " + dialect.autoIncrementPrimaryKey() + "," +
                            "clan_id INT NOT NULL," +
                            "name VARCHAR(32) NOT NULL," +
                            "world VARCHAR(64) NOT NULL," +
                            "x DOUBLE NOT NULL," +
                            "y DOUBLE NOT NULL," +
                            "z DOUBLE NOT NULL," +
                            "yaw FLOAT NOT NULL," +
                            "pitch FLOAT NOT NULL," +
                            "visible_to_allies INTEGER DEFAULT 0," +
                            "UNIQUE (clan_id, name)," +
                            "FOREIGN KEY (clan_id) REFERENCES " + tablePrefix + "clans(id) ON DELETE CASCADE" +
                            ")"
            );

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + tablePrefix + "chests (" +
                            "clan_id INT PRIMARY KEY," +
                            "contents TEXT," +
                            "FOREIGN KEY (clan_id) REFERENCES " + tablePrefix + "clans(id) ON DELETE CASCADE" +
                            ")"
            );

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + tablePrefix + "chest_logs (" +
                            "id " + dialect.autoIncrementPrimaryKey() + "," +
                            "clan_id INT NOT NULL," +
                            "player_uuid VARCHAR(36) NOT NULL," +
                            "action VARCHAR(16) NOT NULL," +
                            "item_data TEXT," +
                            "timestamp BIGINT NOT NULL," +
                            "FOREIGN KEY (clan_id) REFERENCES " + tablePrefix + "clans(id) ON DELETE CASCADE" +
                            ")"
            );

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + tablePrefix + "alliances (" +
                            "clan_id_a INT NOT NULL," +
                            "clan_id_b INT NOT NULL," +
                            "created_at BIGINT NOT NULL," +
                            "PRIMARY KEY (clan_id_a, clan_id_b)," +
                            "FOREIGN KEY (clan_id_a) REFERENCES " + tablePrefix + "clans(id) ON DELETE CASCADE," +
                            "FOREIGN KEY (clan_id_b) REFERENCES " + tablePrefix + "clans(id) ON DELETE CASCADE" +
                            ")"
            );

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + tablePrefix + "wars (" +
                            "id " + dialect.autoIncrementPrimaryKey() + "," +
                            "attacker_clan_id INT NOT NULL," +
                            "defender_clan_id INT NOT NULL," +
                            "status VARCHAR(16) DEFAULT 'ACTIVE'," +
                            "started_at BIGINT NOT NULL," +
                            "ended_at BIGINT DEFAULT 0," +
                            "winner_clan_id INT," +
                            "attacker_points INT DEFAULT 0," +
                            "defender_points INT DEFAULT 0," +
                            "FOREIGN KEY (attacker_clan_id) REFERENCES " + tablePrefix + "clans(id) ON DELETE CASCADE," +
                            "FOREIGN KEY (defender_clan_id) REFERENCES " + tablePrefix + "clans(id) ON DELETE CASCADE" +
                            ")"
            );

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + tablePrefix + "war_kills (" +
                            "id " + dialect.autoIncrementPrimaryKey() + "," +
                            "war_id INT NOT NULL," +
                            "killer_uuid VARCHAR(36) NOT NULL," +
                            "victim_uuid VARCHAR(36) NOT NULL," +
                            "killer_clan_id INT NOT NULL," +
                            "points INT NOT NULL," +
                            "timestamp BIGINT NOT NULL," +
                            "FOREIGN KEY (war_id) REFERENCES " + tablePrefix + "wars(id) ON DELETE CASCADE" +
                            ")"
            );

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + tablePrefix + "cooldowns (" +
                            "player_uuid VARCHAR(36) NOT NULL," +
                            "cooldown_type VARCHAR(32) NOT NULL," +
                            "expires_at BIGINT NOT NULL," +
                            "PRIMARY KEY (player_uuid, cooldown_type)" +
                            ")"
            );

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + tablePrefix + "bank_transactions (" +
                            "id " + dialect.autoIncrementPrimaryKey() + "," +
                            "clan_id INT NOT NULL," +
                            "player_uuid VARCHAR(36) NOT NULL," +
                            "player_name VARCHAR(32) NOT NULL," +
                            "action VARCHAR(16) NOT NULL," +
                            "amount DOUBLE NOT NULL," +
                            "timestamp BIGINT NOT NULL," +
                            "FOREIGN KEY (clan_id) REFERENCES " + tablePrefix + "clans(id) ON DELETE CASCADE" +
                            ")"
            );

            stmt.executeUpdate("DROP TABLE IF EXISTS " + tablePrefix + "pvpmanager_players");
            if (!tablePrefix.isEmpty()) {
                stmt.executeUpdate("DROP TABLE IF EXISTS pvpmanager_players");
            }

            ensureColumn(conn, tablePrefix + "clans", "icon", "VARCHAR(64) DEFAULT 'SHIELD'");
            ensureColumn(conn, tablePrefix + "clans", "glow_enabled", "INTEGER DEFAULT 0");
            ensureColumn(conn, tablePrefix + "clans", "glow_color", "VARCHAR(32) DEFAULT 'WHITE'");
            ensureColumn(conn, tablePrefix + "clans", "name_color", "VARCHAR(8) DEFAULT '&f'");
            ensureColumn(conn, tablePrefix + "clans", "name_strikethrough", "INTEGER DEFAULT 0");
            ensureColumn(conn, tablePrefix + "clans", "gradient_enabled", "INTEGER DEFAULT 0");
            ensureColumn(conn, tablePrefix + "clans", "gradient_color_start", "VARCHAR(9) DEFAULT '#FF0000'");
            ensureColumn(conn, tablePrefix + "clans", "gradient_color_end", "VARCHAR(9) DEFAULT '#0000FF'");
            ensureColumn(conn, tablePrefix + "clans", "balance", "DOUBLE DEFAULT 0.0");
            ensureColumn(conn, tablePrefix + "clans", "bonus_warps", "INTEGER DEFAULT 0");

            if (dialect.type() == StorageType.SQLITE) {
                if ("clan_".equals(tablePrefix)) {
                    createViewIfNotExists(stmt, "ztone_clans", "clan_clans");
                    createViewIfNotExists(stmt, "ztone_members", "clan_members");
                    createViewIfNotExists(stmt, "ztone_ranks", "clan_ranks");
                    createViewIfNotExists(stmt, "ztone_homes", "clan_homes");
                    createViewIfNotExists(stmt, "ztone_warps", "clan_warps");
                    createViewIfNotExists(stmt, "ztone_chests", "clan_chests");
                    createViewIfNotExists(stmt, "ztone_chest_logs", "clan_chest_logs");
                    createViewIfNotExists(stmt, "ztone_alliances", "clan_alliances");
                    createViewIfNotExists(stmt, "ztone_wars", "clan_wars");
                    createViewIfNotExists(stmt, "ztone_war_kills", "clan_war_kills");
                    createViewIfNotExists(stmt, "ztone_cooldowns", "clan_cooldowns");
                    createViewIfNotExists(stmt, "ztone_bank_transactions", "clan_bank_transactions");
                } else if ("ztone_".equals(tablePrefix)) {
                    createViewIfNotExists(stmt, "clan_clans", "ztone_clans");
                    createViewIfNotExists(stmt, "clan_members", "ztone_members");
                    createViewIfNotExists(stmt, "clan_ranks", "ztone_ranks");
                    createViewIfNotExists(stmt, "clan_homes", "ztone_homes");
                    createViewIfNotExists(stmt, "clan_warps", "ztone_warps");
                    createViewIfNotExists(stmt, "clan_chests", "ztone_chests");
                    createViewIfNotExists(stmt, "clan_chest_logs", "ztone_chest_logs");
                    createViewIfNotExists(stmt, "clan_alliances", "ztone_alliances");
                    createViewIfNotExists(stmt, "clan_wars", "ztone_wars");
                    createViewIfNotExists(stmt, "clan_war_kills", "ztone_war_kills");
                    createViewIfNotExists(stmt, "clan_cooldowns", "ztone_cooldowns");
                    createViewIfNotExists(stmt, "clan_bank_transactions", "ztone_bank_transactions");
                }
            }

            plugin.getLogger().info("Datenbanktabellen erstellt/überprüft.");

        } catch (SQLException e) {
            plugin.getLogger().severe("Fehler beim Erstellen der Tabellen: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createViewIfNotExists(Statement stmt, String viewName, String targetTable) {
        try {
            stmt.executeUpdate("CREATE VIEW IF NOT EXISTS " + viewName + " AS SELECT * FROM " + targetTable);
        } catch (SQLException ignored) {}
    }

    private void ensureColumn(Connection conn, String table, String column, String definition) {
        try {
            if (!dialect.hasColumn(conn, table, column)) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
                }
                plugin.getLogger().info("Spalte '" + column + "' zu Tabelle '" + table + "' hinzugefügt.");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Konnte Spalte '" + column + "' nicht zu '" + table + "' hinzufügen: " + e.getMessage());
        }
    }

    public CompletableFuture<Integer> createClan(Clan clan) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO " + tablePrefix + "clans (name, suffix, level, points, leader_uuid, created_at, join_type, icon, glow_enabled, glow_color, name_color, name_strikethrough, gradient_enabled, gradient_color_start, gradient_color_end, balance, bonus_warps) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                ps.setString(1, clan.getName());
                ps.setString(2, clan.getSuffix());
                ps.setInt(3, clan.getLevel());
                ps.setInt(4, clan.getPoints());
                ps.setString(5, clan.getLeaderUuid().toString());
                ps.setLong(6, clan.getCreatedAt());
                ps.setString(7, clan.getJoinType().name());
                ps.setString(8, clan.getIcon());
                ps.setInt(9, clan.isGlowEnabled() ? 1 : 0);
                ps.setString(10, clan.getGlowColor());
                ps.setString(11, clan.getNameColor());
                ps.setInt(12, clan.isNameStrikethrough() ? 1 : 0);
                ps.setInt(13, clan.isGradientEnabled() ? 1 : 0);
                ps.setString(14, clan.getGradientColorStart());
                ps.setString(15, clan.getGradientColorEnd());
                ps.setDouble(16, clan.getBalance());
                ps.setInt(17, clan.getBonusWarps());

                ps.executeUpdate();

                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) {
                    return rs.getInt(1);
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("Fehler beim Erstellen des Clans: " + e.getMessage());
                e.printStackTrace();
            }
            return -1;
        }, dbExecutor);
    }

    public CompletableFuture<Void> updateClan(Clan clan) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE " + tablePrefix + "clans SET name = ?, suffix = ?, level = ?, points = ?, leader_uuid = ?, join_type = ?, icon = ?, glow_enabled = ?, glow_color = ?, name_color = ?, name_strikethrough = ?, gradient_enabled = ?, gradient_color_start = ?, gradient_color_end = ?, balance = ?, bonus_warps = ? WHERE id = ?";

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, clan.getName());
                ps.setString(2, clan.getSuffix());
                ps.setInt(3, clan.getLevel());
                ps.setInt(4, clan.getPoints());
                ps.setString(5, clan.getLeaderUuid().toString());
                ps.setString(6, clan.getJoinType().name());
                ps.setString(7, clan.getIcon());
                ps.setInt(8, clan.isGlowEnabled() ? 1 : 0);
                ps.setString(9, clan.getGlowColor());
                ps.setString(10, clan.getNameColor());
                ps.setInt(11, clan.isNameStrikethrough() ? 1 : 0);
                ps.setInt(12, clan.isGradientEnabled() ? 1 : 0);
                ps.setString(13, clan.getGradientColorStart());
                ps.setString(14, clan.getGradientColorEnd());
                ps.setDouble(15, clan.getBalance());
                ps.setInt(16, clan.getBonusWarps());
                ps.setInt(17, clan.getId());

                ps.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().severe("Fehler beim Aktualisieren des Clans: " + e.getMessage());
                e.printStackTrace();
            }
        }, dbExecutor);
    }

    public CompletableFuture<Void> deleteClan(int clanId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM " + tablePrefix + "clans WHERE id = ?";

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setInt(1, clanId);
                ps.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().severe("Fehler beim Löschen des Clans: " + e.getMessage());
                e.printStackTrace();
            }
        }, dbExecutor);
    }

    public List<Clan> loadAllClans() {
        List<Clan> clans = new ArrayList<>();
        String sql = "SELECT * FROM " + tablePrefix + "clans";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Clan clan = new Clan(
                        rs.getInt("id"),
                        rs.getString("name"),
                        UUID.fromString(rs.getString("leader_uuid"))
                );
                clan.setSuffix(rs.getString("suffix"));
                clan.setLevel(rs.getInt("level"));
                clan.setPoints(rs.getInt("points"));
                clan.setBalance(rs.getDouble("balance"));
                try {
                    clan.setBonusWarps(rs.getInt("bonus_warps"));
                } catch (Exception ignored) {}
                clan.setCreatedAt(rs.getLong("created_at"));
                clan.setIcon(rs.getString("icon"));
                clan.setGlowEnabled(rs.getInt("glow_enabled") == 1);
                clan.setGlowColor(rs.getString("glow_color"));
                clan.setNameColor(rs.getString("name_color"));
                clan.setNameStrikethrough(rs.getInt("name_strikethrough") == 1);
                clan.setGradientEnabled(rs.getInt("gradient_enabled") == 1);
                clan.setGradientColorStart(rs.getString("gradient_color_start"));
                clan.setGradientColorEnd(rs.getString("gradient_color_end"));
                try {
                    clan.setJoinType(Clan.JoinType.valueOf(rs.getString("join_type")));
                } catch (Exception e) {
                    clan.setJoinType(Clan.JoinType.INVITE);
                }

                loadRanksForClan(conn, clan);

                loadMembersForClan(conn, clan);

                loadHomesForClan(conn, clan);

                loadWarpsForClan(conn, clan);

                loadChestForClan(conn, clan);

                loadAlliancesForClan(conn, clan);

                clans.add(clan);
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Fehler beim Laden der Clans: " + e.getMessage());
            e.printStackTrace();
        }

        return clans;
    }

    private void loadRanksForClan(Connection conn, Clan clan) throws SQLException {
        String sql = "SELECT * FROM " + tablePrefix + "ranks WHERE clan_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, clan.getId());
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                ClanRank rank = new ClanRank(
                        rs.getInt("id"),
                        rs.getInt("clan_id"),
                        rs.getString("name"),
                        rs.getInt("priority")
                );
                rank.permissionsFromString(rs.getString("permissions"));
                clan.addRank(rank);
            }
        }
    }

    private void loadMembersForClan(Connection conn, Clan clan) throws SQLException {
        String sql = "SELECT * FROM " + tablePrefix + "members WHERE clan_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, clan.getId());
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                ClanMember member = new ClanMember(
                        UUID.fromString(rs.getString("player_uuid")),
                        rs.getInt("clan_id"),
                        rs.getInt("rank_id"),
                        rs.getLong("joined_at")
                );

                ClanRank rank = clan.getRank(member.getRankId());
                member.setRank(rank);
                member.setLeader(clan.isLeader(member.getUuid()));

                clan.addMember(member);
            }
        }
    }

    private void loadHomesForClan(Connection conn, Clan clan) throws SQLException {
        String sql = "SELECT * FROM " + tablePrefix + "homes WHERE clan_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, clan.getId());
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                ClanHome home = new ClanHome(
                        rs.getInt("id"),
                        rs.getInt("clan_id"),
                        rs.getString("name"),
                        rs.getString("world"),
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getDouble("z"),
                        rs.getFloat("yaw"),
                        rs.getFloat("pitch")
                );
                clan.addHome(home);
            }
        }
    }

    private void loadWarpsForClan(Connection conn, Clan clan) throws SQLException {
        String sql = "SELECT * FROM " + tablePrefix + "warps WHERE clan_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, clan.getId());
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                ClanWarp warp = new ClanWarp(
                        rs.getInt("id"),
                        rs.getInt("clan_id"),
                        rs.getString("name"),
                        rs.getString("world"),
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getDouble("z"),
                        rs.getFloat("yaw"),
                        rs.getFloat("pitch"),
                        rs.getBoolean("visible_to_allies")
                );
                clan.addWarp(warp);
            }
        }
    }

    private void loadChestForClan(Connection conn, Clan clan) throws SQLException {
        String sql = "SELECT contents FROM " + tablePrefix + "chests WHERE clan_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, clan.getId());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String contents = rs.getString("contents");
                if (contents != null && !contents.isEmpty()) {
                    ItemStack[] items = ItemSerializer.deserialize(contents);
                    clan.setChestContents(items);
                }
            }
        }

        int expectedSize = plugin.getLevelManager().getChestSize(clan.getLevel());
        if (clan.getChestSize() < expectedSize) {
            clan.resizeChest(expectedSize);
        }
    }

    private void loadAlliancesForClan(Connection conn, Clan clan) throws SQLException {
        String sql = "SELECT clan_id_a, clan_id_b FROM " + tablePrefix + "alliances WHERE clan_id_a = ? OR clan_id_b = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, clan.getId());
            ps.setInt(2, clan.getId());
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                int idA = rs.getInt("clan_id_a");
                int idB = rs.getInt("clan_id_b");
                int allyId = (idA == clan.getId()) ? idB : idA;
                clan.addAlly(allyId);
            }
        }
    }

    public CompletableFuture<Integer> createRank(ClanRank rank) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO " + tablePrefix + "ranks (clan_id, name, priority, permissions) VALUES (?, ?, ?, ?)";

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                ps.setInt(1, rank.getClanId());
                ps.setString(2, rank.getName());
                ps.setInt(3, rank.getPriority());
                ps.setString(4, rank.permissionsToString());

                ps.executeUpdate();

                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) {
                    return rs.getInt(1);
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("Fehler beim Erstellen des Rangs: " + e.getMessage());
                e.printStackTrace();
            }
            return -1;
        }, dbExecutor);
    }

    public CompletableFuture<Void> updateRank(ClanRank rank) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE " + tablePrefix + "ranks SET name = ?, priority = ?, permissions = ? WHERE id = ?";

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, rank.getName());
                ps.setInt(2, rank.getPriority());
                ps.setString(3, rank.permissionsToString());
                ps.setInt(4, rank.getId());

                ps.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().severe("Fehler beim Aktualisieren des Rangs: " + e.getMessage());
                e.printStackTrace();
            }
        }, dbExecutor);
    }

    public CompletableFuture<Void> deleteRank(int rankId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM " + tablePrefix + "ranks WHERE id = ?";

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setInt(1, rankId);
                ps.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().severe("Fehler beim Löschen des Rangs: " + e.getMessage());
                e.printStackTrace();
            }
        }, dbExecutor);
    }

    public CompletableFuture<Void> addMember(ClanMember member) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO " + tablePrefix + "members (clan_id, player_uuid, rank_id, joined_at) VALUES (?, ?, ?, ?)";

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setInt(1, member.getClanId());
                ps.setString(2, member.getUuid().toString());
                ps.setInt(3, member.getRankId());
                ps.setLong(4, member.getJoinedAt());

                ps.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().severe("Fehler beim Hinzufügen des Mitglieds: " + e.getMessage());
                e.printStackTrace();
            }
        }, dbExecutor);
    }

    public CompletableFuture<Void> updateMember(ClanMember member) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE " + tablePrefix + "members SET rank_id = ? WHERE player_uuid = ?";

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setInt(1, member.getRankId());
                ps.setString(2, member.getUuid().toString());

                ps.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().severe("Fehler beim Aktualisieren des Mitglieds: " + e.getMessage());
                e.printStackTrace();
            }
        }, dbExecutor);
    }

    public CompletableFuture<Void> removeMember(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM " + tablePrefix + "members WHERE player_uuid = ?";

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, uuid.toString());
                ps.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().severe("Fehler beim Entfernen des Mitglieds: " + e.getMessage());
                e.printStackTrace();
            }
        }, dbExecutor);
    }

    public CompletableFuture<Integer> saveHome(ClanHome home) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = dialect.upsert(
                    "INSERT INTO " + tablePrefix + "homes (clan_id, name, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    new String[]{"clan_id", "name"},
                    new String[]{"world", "x", "y", "z", "yaw", "pitch"});

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                ps.setInt(1, home.getClanId());
                ps.setString(2, home.getName());
                ps.setString(3, home.getWorldName());
                ps.setDouble(4, home.getX());
                ps.setDouble(5, home.getY());
                ps.setDouble(6, home.getZ());
                ps.setFloat(7, home.getYaw());
                ps.setFloat(8, home.getPitch());

                ps.executeUpdate();

                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) {
                    return rs.getInt(1);
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("Fehler beim Speichern des Homes: " + e.getMessage());
                e.printStackTrace();
            }
            return home.getId();
        }, dbExecutor);
    }

    public CompletableFuture<Void> deleteHome(int clanId, String name) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM " + tablePrefix + "homes WHERE clan_id = ? AND name = ?";

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setInt(1, clanId);
                ps.setString(2, name);
                ps.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().severe("Fehler beim Löschen des Homes: " + e.getMessage());
                e.printStackTrace();
            }
        }, dbExecutor);
    }

    public CompletableFuture<Integer> saveWarp(ClanWarp warp) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = dialect.upsert(
                    "INSERT INTO " + tablePrefix + "warps (clan_id, name, world, x, y, z, yaw, pitch, visible_to_allies) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    new String[]{"clan_id", "name"},
                    new String[]{"world", "x", "y", "z", "yaw", "pitch", "visible_to_allies"});

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                ps.setInt(1, warp.getClanId());
                ps.setString(2, warp.getName());
                ps.setString(3, warp.getWorldName());
                ps.setDouble(4, warp.getX());
                ps.setDouble(5, warp.getY());
                ps.setDouble(6, warp.getZ());
                ps.setFloat(7, warp.getYaw());
                ps.setFloat(8, warp.getPitch());
                ps.setBoolean(9, warp.isVisibleToAllies());

                ps.executeUpdate();

                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) {
                    return rs.getInt(1);
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("Fehler beim Speichern des Warps: " + e.getMessage());
                e.printStackTrace();
            }
            return warp.getId();
        }, dbExecutor);
    }

    public CompletableFuture<Void> deleteWarp(int clanId, String name) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM " + tablePrefix + "warps WHERE clan_id = ? AND name = ?";

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setInt(1, clanId);
                ps.setString(2, name);
                ps.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().severe("Fehler beim Löschen des Warps: " + e.getMessage());
                e.printStackTrace();
            }
        }, dbExecutor);
    }

    public CompletableFuture<Void> saveChest(int clanId, ItemStack[] contents) {
        if (contents == null) {
            return CompletableFuture.completedFuture(null);
        }
        String serialized = ItemSerializer.serialize(contents);
        if (serialized == null || serialized.isEmpty()) {
            boolean hasItems = false;
            for (ItemStack is : contents) {
                if (is != null && !is.getType().isAir()) {
                    hasItems = true;
                    break;
                }
            }
            if (hasItems) {
                plugin.getLogger().severe("WARNUNG: Truheninhalt von Clan " + clanId + " konnte nicht serialisiert werden! Speichern abgebrochen.");
                return CompletableFuture.completedFuture(null);
            }
        }
        return CompletableFuture.runAsync(() -> {
            String sql = dialect.upsert(
                    "INSERT INTO " + tablePrefix + "chests (clan_id, contents) VALUES (?, ?)",
                    new String[]{"clan_id"},
                    new String[]{"contents"});

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setInt(1, clanId);
                ps.setString(2, serialized);

                ps.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().severe("Fehler beim Speichern der Truhe: " + e.getMessage());
                e.printStackTrace();
            }
        }, dbExecutor);
    }

    public void saveChestSync(int clanId, ItemStack[] contents) {
        if (contents == null) return;
        String serialized = ItemSerializer.serialize(contents);
        if (serialized == null || serialized.isEmpty()) {
            for (ItemStack is : contents) {
                if (is != null && !is.getType().isAir()) {
                    plugin.getLogger().severe("WARNUNG: Truheninhalt von Clan " + clanId + " konnte nicht serialisiert werden! Synchrones Speichern abgebrochen.");
                    return;
                }
            }
        }
        String sql = dialect.upsert(
                "INSERT INTO " + tablePrefix + "chests (clan_id, contents) VALUES (?, ?)",
                new String[]{"clan_id"},
                new String[]{"contents"});

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, clanId);
            ps.setString(2, serialized);

            ps.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().severe("Fehler beim synchronen Speichern der Truhe: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public CompletableFuture<Void> logChestAction(int clanId, UUID playerUuid, String action, ItemStack item) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO " + tablePrefix + "chest_logs (clan_id, player_uuid, action, item_data, timestamp) VALUES (?, ?, ?, ?, ?)";

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setInt(1, clanId);
                ps.setString(2, playerUuid.toString());
                ps.setString(3, action);
                ps.setString(4, item != null ? ItemSerializer.serializeItem(item) : null);
                ps.setLong(5, System.currentTimeMillis());

                ps.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().severe("Fehler beim Loggen der Chest-Aktion: " + e.getMessage());
                e.printStackTrace();
            }
        }, dbExecutor);
    }

    public CompletableFuture<Void> createAlliance(int clanIdA, int clanIdB) {
        return CompletableFuture.runAsync(() -> {

            int first = Math.min(clanIdA, clanIdB);
            int second = Math.max(clanIdA, clanIdB);

            String sql = "INSERT OR IGNORE INTO " + tablePrefix + "alliances (clan_id_a, clan_id_b, created_at) VALUES (?, ?, ?)";

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setInt(1, first);
                ps.setInt(2, second);
                ps.setLong(3, System.currentTimeMillis());

                ps.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().severe("Fehler beim Erstellen der Allianz: " + e.getMessage());
                e.printStackTrace();
            }
        }, dbExecutor);
    }

    public CompletableFuture<Void> deleteAlliance(int clanIdA, int clanIdB) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM " + tablePrefix + "alliances WHERE (clan_id_a = ? AND clan_id_b = ?) OR (clan_id_a = ? AND clan_id_b = ?)";

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setInt(1, clanIdA);
                ps.setInt(2, clanIdB);
                ps.setInt(3, clanIdB);
                ps.setInt(4, clanIdA);

                ps.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().severe("Fehler beim Löschen der Allianz: " + e.getMessage());
                e.printStackTrace();
            }
        }, dbExecutor);
    }

    public CompletableFuture<Integer> createWar(ClanWar war) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO " + tablePrefix + "wars (attacker_clan_id, defender_clan_id, status, started_at) VALUES (?, ?, ?, ?)";

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                ps.setInt(1, war.getAttackerClanId());
                ps.setInt(2, war.getDefenderClanId());
                ps.setString(3, war.getStatus().name());
                ps.setLong(4, war.getStartedAt());

                ps.executeUpdate();

                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) {
                    return rs.getInt(1);
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("Fehler beim Erstellen des Krieges: " + e.getMessage());
                e.printStackTrace();
            }
            return -1;
        }, dbExecutor);
    }

    public CompletableFuture<Void> updateWar(ClanWar war) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE " + tablePrefix + "wars SET status = ?, ended_at = ?, winner_clan_id = ?, attacker_points = ?, defender_points = ? WHERE id = ?";

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, war.getStatus().name());
                ps.setLong(2, war.getEndedAt());
                if (war.getWinnerClanId() != null) {
                    ps.setInt(3, war.getWinnerClanId());
                } else {
                    ps.setNull(3, Types.INTEGER);
                }
                ps.setInt(4, war.getAttackerPoints());
                ps.setInt(5, war.getDefenderPoints());
                ps.setInt(6, war.getId());

                ps.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().severe("Fehler beim Aktualisieren des Krieges: " + e.getMessage());
                e.printStackTrace();
            }
        }, dbExecutor);
    }

    public CompletableFuture<Void> addWarKill(int warId, UUID killerUuid, UUID victimUuid, int killerClanId, int points) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO " + tablePrefix + "war_kills (war_id, killer_uuid, victim_uuid, killer_clan_id, points, timestamp) VALUES (?, ?, ?, ?, ?, ?)";

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setInt(1, warId);
                ps.setString(2, killerUuid.toString());
                ps.setString(3, victimUuid.toString());
                ps.setInt(4, killerClanId);
                ps.setInt(5, points);
                ps.setLong(6, System.currentTimeMillis());

                ps.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().severe("Fehler beim Hinzufügen des War-Kills: " + e.getMessage());
                e.printStackTrace();
            }
        }, dbExecutor);
    }

    private Integer getNullableInteger(ResultSet rs, String column) {
        try {
            int val = rs.getInt(column);
            if (rs.wasNull()) {
                return null;
            }
            return val;
        } catch (Exception e) {
            try {
                Object obj = rs.getObject(column);
                if (obj == null) return null;
                if (obj instanceof Number num) return num.intValue();
                String s = String.valueOf(obj).trim();
                return s.isEmpty() ? null : Integer.parseInt(s);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    public List<ClanWar> loadActiveWars() {
        List<ClanWar> wars = new ArrayList<>();
        String sql = "SELECT * FROM " + tablePrefix + "wars WHERE status = 'ACTIVE'";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                ClanWar war = new ClanWar(
                        rs.getInt("id"),
                        rs.getInt("attacker_clan_id"),
                        rs.getInt("defender_clan_id"),
                        ClanWar.WarStatus.valueOf(rs.getString("status")),
                        rs.getLong("started_at"),
                        rs.getLong("ended_at"),
                        getNullableInteger(rs, "winner_clan_id")
                );
                try (PreparedStatement kps = conn.prepareStatement("SELECT victim_uuid, COUNT(*) as cnt FROM " + tablePrefix + "war_kills WHERE war_id = ? GROUP BY victim_uuid")) {
                    kps.setInt(1, war.getId());
                    try (ResultSet krs = kps.executeQuery()) {
                        while (krs.next()) {
                            war.setParticipantDeaths(UUID.fromString(krs.getString("victim_uuid")), krs.getInt("cnt"));
                        }
                    }
                } catch (Exception ignored) {}
                wars.add(war);
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Fehler beim Laden der aktiven Kriege: " + e.getMessage());
            e.printStackTrace();
        }

        return wars;
    }

    public List<ClanWar> loadWarHistory(int clanId) {
        List<ClanWar> wars = new ArrayList<>();
        String sql = "SELECT * FROM " + tablePrefix + "wars WHERE (attacker_clan_id = ? OR defender_clan_id = ?) ORDER BY started_at DESC LIMIT 50";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, clanId);
            ps.setInt(2, clanId);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ClanWar war = new ClanWar(
                        rs.getInt("id"),
                        rs.getInt("attacker_clan_id"),
                        rs.getInt("defender_clan_id"),
                        ClanWar.WarStatus.valueOf(rs.getString("status")),
                        rs.getLong("started_at"),
                        rs.getLong("ended_at"),
                        getNullableInteger(rs, "winner_clan_id")
                );
                wars.add(war);
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Fehler beim Laden der Kriegshistorie: " + e.getMessage());
            e.printStackTrace();
        }

        return wars;
    }

    public CompletableFuture<Void> setCooldown(UUID uuid, String type, long expiresAt) {
        return CompletableFuture.runAsync(() -> {
            String sql = dialect.upsert(
                    "INSERT INTO " + tablePrefix + "cooldowns (player_uuid, cooldown_type, expires_at) VALUES (?, ?, ?)",
                    new String[]{"player_uuid", "cooldown_type"},
                    new String[]{"expires_at"});

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, uuid.toString());
                ps.setString(2, type);
                ps.setLong(3, expiresAt);

                ps.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().severe("Fehler beim Setzen des Cooldowns: " + e.getMessage());
                e.printStackTrace();
            }
        }, dbExecutor);
    }

    public long getCooldown(UUID uuid, String type) {
        String sql = "SELECT expires_at FROM " + tablePrefix + "cooldowns WHERE player_uuid = ? AND cooldown_type = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            ps.setString(2, type);

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getLong("expires_at");
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Fehler beim Abrufen des Cooldowns: " + e.getMessage());
            e.printStackTrace();
        }

        return 0;
    }

    public CompletableFuture<Void> clearExpiredCooldowns() {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM " + tablePrefix + "cooldowns WHERE expires_at < ?";

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setLong(1, System.currentTimeMillis());
                ps.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().severe("Fehler beim Löschen abgelaufener Cooldowns: " + e.getMessage());
                e.printStackTrace();
            }
        }, dbExecutor);
    }

    public CompletableFuture<Void> logBankTransaction(ClanBankTransaction tx) {
        return logBankTransaction(tx.getClanId(), tx.getPlayerUuid(), tx.getPlayerName(), tx.getType(), tx.getAmount());
    }

    public CompletableFuture<Void> logBankTransaction(int clanId, UUID playerUuid, String playerName, ClanBankTransaction.Type type, double amount) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO " + tablePrefix + "bank_transactions (clan_id, player_uuid, player_name, action, amount, timestamp) VALUES (?, ?, ?, ?, ?, ?)";

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setInt(1, clanId);
                ps.setString(2, playerUuid.toString());
                ps.setString(3, playerName != null ? playerName : "Unbekannt");
                ps.setString(4, type.name());
                ps.setDouble(5, amount);
                ps.setLong(6, System.currentTimeMillis());

                ps.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().severe("Fehler beim Loggen der Bank-Transaktion: " + e.getMessage());
                e.printStackTrace();
            }
        }, dbExecutor);
    }

    public List<ClanBankTransaction> loadBankTransactions(int clanId, int limit) {
        List<ClanBankTransaction> list = new ArrayList<>();
        String sql = "SELECT * FROM " + tablePrefix + "bank_transactions WHERE clan_id = ? ORDER BY timestamp DESC LIMIT ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, clanId);
            ps.setInt(2, limit);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ClanBankTransaction tx = new ClanBankTransaction(
                        rs.getInt("id"),
                        rs.getInt("clan_id"),
                        UUID.fromString(rs.getString("player_uuid")),
                        rs.getString("player_name"),
                        ClanBankTransaction.Type.valueOf(rs.getString("action")),
                        rs.getDouble("amount"),
                        rs.getLong("timestamp")
                );
                list.add(tx);
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Fehler beim Laden der Bank-Transaktionen: " + e.getMessage());
            e.printStackTrace();
        }

        return list;
    }

    public String getTablePrefix() {
        return tablePrefix;
    }

    public SqlDialect getDialect() {
        return dialect;
    }
}

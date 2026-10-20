package de.Z7534.ztone.claims.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.Z7534.ztone.clansystem.managers.DatabaseManager;
import de.Z7534.ztone.clansystem.storage.StorageType;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Database {

    private final Logger logger;
    private final File dataFolder;
    private final DatabaseManager dbManager;
    private final String dbFileName;
    private HikariDataSource ownDataSource;
    private Connection connection;

    /**
     * Alle Schreibzugriffe laufen nacheinander ueber einen einzigen Thread.
     */
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Claims-DB-Writer");
        t.setDaemon(true);
        return t;
    });

    @FunctionalInterface
    public interface SqlWork {
        void run(Connection connection) throws SQLException;
    }

    public Database(Logger logger, DatabaseManager dbManager) {
        this(logger, null, dbManager, "claims.db");
    }

    public Database(Logger logger, File dataFolder, DatabaseManager dbManager, String dbFileName) {
        this.logger = logger;
        this.dataFolder = dataFolder;
        this.dbManager = dbManager;
        this.dbFileName = (dbFileName != null && !dbFileName.isBlank()) ? dbFileName : "claims.db";
    }

    public synchronized void connect() {
        try {
            // Wenn MariaDB in der zentralen Konfiguration aktiv ist, die zentrale DataSource nutzen
            if (dbManager != null && dbManager.getDialect() != null && dbManager.getDialect().type() == StorageType.MARIADB) {
                if (dbManager.getDataSource() == null) {
                    logger.severe("Zentraler DatabaseManager ist nicht bereit fuer Claims (MariaDB)!");
                    return;
                }
                createTables();
                return;
            }

            // Ansonsten SQLite: separate claims.db wie vorher verwenden!
            File folder = (dataFolder != null) ? dataFolder : (dbManager != null ? dbManager.getPlugin().getDataFolder() : new File("."));
            File targetFile = new File(folder, dbFileName);

            // Automatische Suche in alten Speicherorten und Unterordnern
            File parentFolder = folder.getParentFile();
            File[] candidates = new File[] {
                    new File(folder, "claims.db"),
                    new File(folder, "data/claims.db"),
                    parentFolder != null ? new File(parentFolder, "Claims/claims.db") : null,
                    parentFolder != null ? new File(parentFolder, "Claims/data/claims.db") : null,
                    parentFolder != null ? new File(parentFolder, "claims/claims.db") : null,
                    parentFolder != null ? new File(parentFolder, "claims/data/claims.db") : null,
                    new File("claims.db"),
                    new File(folder, "data/ztone.db"),
                    new File(folder, "ztone.db")
            };

            for (File candidate : candidates) {
                if (candidate != null && candidate.exists() && candidate.length() > 0) {
                    if (!targetFile.exists() || targetFile.length() == 0) {
                        try {
                            if (targetFile.getParentFile() != null) {
                                targetFile.getParentFile().mkdirs();
                            }
                            Files.copy(candidate.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            File wal = new File(candidate.getAbsolutePath() + "-wal");
                            if (wal.exists()) {
                                Files.copy(wal.toPath(), new File(targetFile.getAbsolutePath() + "-wal").toPath(), StandardCopyOption.REPLACE_EXISTING);
                            }
                            File shm = new File(candidate.getAbsolutePath() + "-shm");
                            if (shm.exists()) {
                                Files.copy(shm.toPath(), new File(targetFile.getAbsolutePath() + "-shm").toPath(), StandardCopyOption.REPLACE_EXISTING);
                            }
                            logger.info("Bestehende Claims-Datenbank von '" + candidate.getAbsolutePath() + "' geladen.");
                            break;
                        } catch (Exception e) {
                            logger.warning("Konnte Claims-Datenbank von '" + candidate.getAbsolutePath() + "' nicht kopieren: " + e.getMessage());
                        }
                    }
                }
            }

            if (targetFile.getParentFile() != null && !targetFile.getParentFile().exists()) {
                targetFile.getParentFile().mkdirs();
            }

            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setPoolName("Ztone-Claims-Pool");
            hikariConfig.setDriverClassName("org.sqlite.JDBC");
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + targetFile.getAbsolutePath());
            hikariConfig.setMaximumPoolSize(5);
            hikariConfig.setConnectionTimeout(15000);
            hikariConfig.setMaxLifetime(0);
            hikariConfig.setConnectionInitSql("PRAGMA foreign_keys = ON;");

            this.ownDataSource = new HikariDataSource(hikariConfig);
            try (Connection conn = ownDataSource.getConnection(); Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA journal_mode = WAL;");
                stmt.execute("PRAGMA busy_timeout = 5000;");
            }

            createTables();
            logger.info("Claims-Datenbank erfolgreich geoeffnet: " + targetFile.getName());

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Konnte Claims-Tabellen in der Datenbank nicht anlegen", e);
        }
    }

    private void migrateOldTables(Connection conn) {
        migrateTable(conn, "players", "claims_players");
        migrateTable(conn, "claims", "claims_claims");
        migrateTable(conn, "claim_chunks", "claims_chunks");
        migrateTable(conn, "claim_trust", "claims_trust");
        migrateTable(conn, "claim_managers", "claims_managers");
        migrateTable(conn, "claim_bans", "claims_bans");
    }

    private void migrateTable(Connection conn, String oldTable, String newTable) {
        try {
            DatabaseMetaData meta = conn.getMetaData();
            boolean oldExists = false;
            boolean newExists = false;
            try (ResultSet rs = meta.getTables(null, null, null, new String[]{"TABLE"})) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    if (oldTable.equalsIgnoreCase(name)) oldExists = true;
                    if (newTable.equalsIgnoreCase(name)) newExists = true;
                }
            }
            if (oldExists && !newExists) {
                try (Statement st = conn.createStatement()) {
                    st.execute("ALTER TABLE " + oldTable + " RENAME TO " + newTable);
                    logger.info("Claims-Datenbank: Tabelle '" + oldTable + "' erfolgreich zu '" + newTable + "' migriert.");
                }
            }
        } catch (Exception e) {
            logger.warning("Hinweis bei Claims-Tabellen-Migration von '" + oldTable + "' zu '" + newTable + "': " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            migrateOldTables(conn);

            st.execute("""
                CREATE TABLE IF NOT EXISTS claims_players (
                    uuid TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    bonus_claim_slots INTEGER NOT NULL DEFAULT 0,
                    playtime_seconds INTEGER NOT NULL DEFAULT 0,
                    board_line_enabled INTEGER NOT NULL DEFAULT 1,
                    fence_line_enabled INTEGER NOT NULL DEFAULT 1,
                    entry_hint_enabled INTEGER NOT NULL DEFAULT 1,
                    explosions_enabled INTEGER NOT NULL DEFAULT 0,
                    claim_map_enabled INTEGER NOT NULL DEFAULT 1,
                    last_seen INTEGER NOT NULL DEFAULT 0
                );
                """);

            try {
                st.execute("ALTER TABLE claims_players ADD COLUMN explosions_enabled INTEGER NOT NULL DEFAULT 0;");
            } catch (SQLException ignored) {
            }
            try {
                st.execute("ALTER TABLE claims_players ADD COLUMN claim_map_enabled INTEGER NOT NULL DEFAULT 1;");
            } catch (SQLException ignored) {
            }

            st.execute("""
                CREATE TABLE IF NOT EXISTS claims_claims (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    owner_uuid TEXT NOT NULL,
                    name TEXT NOT NULL,
                    world TEXT NOT NULL,
                    visitor_perm_mask INTEGER NOT NULL DEFAULT 0,
                    clan_trust_enabled INTEGER NOT NULL DEFAULT 0,
                    ally_trust_enabled INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL
                );
                """);

            try {
                st.execute("ALTER TABLE claims_claims ADD COLUMN clan_trust_enabled INTEGER NOT NULL DEFAULT 0;");
            } catch (SQLException ignored) {
            }
            try {
                st.execute("ALTER TABLE claims_claims ADD COLUMN ally_trust_enabled INTEGER NOT NULL DEFAULT 0;");
            } catch (SQLException ignored) {
            }

            st.execute("""
                CREATE TABLE IF NOT EXISTS claims_chunks (
                    claim_id INTEGER NOT NULL REFERENCES claims_claims(id) ON DELETE CASCADE,
                    chunk_x INTEGER NOT NULL,
                    chunk_z INTEGER NOT NULL,
                    PRIMARY KEY (claim_id, chunk_x, chunk_z)
                );
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS claims_trust (
                    claim_id INTEGER NOT NULL REFERENCES claims_claims(id) ON DELETE CASCADE,
                    player_uuid TEXT NOT NULL,
                    perm_mask INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (claim_id, player_uuid)
                );
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS claims_managers (
                    claim_id INTEGER NOT NULL REFERENCES claims_claims(id) ON DELETE CASCADE,
                    player_uuid TEXT NOT NULL,
                    PRIMARY KEY (claim_id, player_uuid)
                );
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS claims_bans (
                    claim_id INTEGER NOT NULL REFERENCES claims_claims(id) ON DELETE CASCADE,
                    player_uuid TEXT NOT NULL,
                    PRIMARY KEY (claim_id, player_uuid)
                );
                """);

            st.execute("CREATE INDEX IF NOT EXISTS idx_claims_chunks_world ON claims_chunks(claim_id);");
            st.execute("CREATE INDEX IF NOT EXISTS idx_claims_owner ON claims_claims(owner_uuid);");

            // Kompatibilitaets-Views fuer alte Namen anlegen
            try {
                st.executeUpdate("CREATE VIEW IF NOT EXISTS players AS SELECT * FROM claims_players;");
                st.executeUpdate("CREATE VIEW IF NOT EXISTS claims AS SELECT * FROM claims_claims;");
                st.executeUpdate("CREATE VIEW IF NOT EXISTS claim_chunks AS SELECT * FROM claims_chunks;");
                st.executeUpdate("CREATE VIEW IF NOT EXISTS claim_trust AS SELECT * FROM claims_trust;");
                st.executeUpdate("CREATE VIEW IF NOT EXISTS claim_managers AS SELECT * FROM claims_managers;");
                st.executeUpdate("CREATE VIEW IF NOT EXISTS claim_bans AS SELECT * FROM claims_bans;");
            } catch (SQLException ignored) {
            }
        }
    }

    public synchronized boolean isConnected() {
        if (ownDataSource != null) {
            return !ownDataSource.isClosed();
        }
        return dbManager != null && dbManager.getDataSource() != null && !dbManager.getDataSource().isClosed();
    }

    public Connection getConnection() throws SQLException {
        if (ownDataSource != null) {
            return ownDataSource.getConnection();
        }
        if (dbManager != null) {
            return dbManager.getConnection();
        }
        throw new SQLException("Keine Datenbankverbindung fuer Claims verfuegbar!");
    }

    public synchronized Connection connection() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = getConnection();
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Fehler beim Abrufen der Datenbankverbindung: " + e.getMessage(), e);
        }
        return connection;
    }

    public void runAsync(Runnable task) {
        try {
            writer.execute(() -> {
                try {
                    task.run();
                } catch (Throwable t) {
                    logger.log(Level.SEVERE, "Fehler bei einer asynchronen Datenbank-Aufgabe", t);
                }
            });
        } catch (RejectedExecutionException ex) {
            logger.warning("Datenbank-Aufgabe verworfen, da die Datenbank bereits geschlossen wird.");
        }
    }

    public synchronized void inTransaction(SqlWork work) throws SQLException {
        Connection c = connection();
        boolean previous = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            work.run(c);
            c.commit();
        } catch (SQLException | RuntimeException e) {
            try {
                c.rollback();
            } catch (SQLException rollbackError) {
                logger.warning("Rollback fehlgeschlagen: " + rollbackError.getMessage());
            }
            throw e;
        } finally {
            c.setAutoCommit(previous);
        }
    }

    public void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(15, TimeUnit.SECONDS)) {
                logger.warning("Nicht alle Datenbank-Schreibzugriffe wurden rechtzeitig fertig.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        closeConnection();
        if (ownDataSource != null && !ownDataSource.isClosed()) {
            ownDataSource.close();
        }
    }

    private synchronized void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            logger.warning("Fehler beim Schliessen der Verbindung: " + e.getMessage());
        }
    }
}

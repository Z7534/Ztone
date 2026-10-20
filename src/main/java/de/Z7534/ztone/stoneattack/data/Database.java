package de.Z7534.ztone.stoneattack.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.Z7534.ztone.clansystem.storage.StorageType;
import de.Z7534.ztone.stoneattack.Stoneattack;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Anbindung fuer das StoneAttack Leaderboard.
 * Unterstuetzt standardmaessig die separate leaderboard.db wie vorher,
 * sowie zentrale MariaDB/MySQL.
 */
public class Database {

    private final Stoneattack plugin;
    private HikariDataSource ownDataSource;

    public Database(Stoneattack plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        // Falls MariaDB in der zentralen Config konfiguriert ist, die zentrale Verbindung nutzen
        if (plugin.getPlugin().getDatabaseManager() != null &&
                plugin.getPlugin().getDatabaseManager().getDialect() != null &&
                plugin.getPlugin().getDatabaseManager().getDialect().type() == StorageType.MARIADB) {
            ownDataSource = plugin.getPlugin().getDatabaseManager().getDataSource();
            if (ownDataSource == null) {
                plugin.getLogger().warning("Zentrale MariaDB-Datenbank nicht verfuegbar - Tabellen fuer StoneAttack konnten nicht geprueft werden.");
                return;
            }
            initSchema();
            return;
        }

        // Ansonsten SQLite: separate leaderboard.db wie vorher verwenden!
        File dataDir = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        File targetFile = new File(dataDir, "leaderboard.db");

        // Automatische Suche in alten Speicherorten und Unterordnern
        File parentFolder = plugin.getDataFolder().getParentFile();
        File[] candidates = new File[] {
                new File(dataDir, "leaderboard.db"),
                new File(plugin.getDataFolder(), "leaderboard.db"),
                parentFolder != null ? new File(parentFolder, "Stoneattack/data/leaderboard.db") : null,
                parentFolder != null ? new File(parentFolder, "Stoneattack/leaderboard.db") : null,
                parentFolder != null ? new File(parentFolder, "StoneAttack/data/leaderboard.db") : null,
                parentFolder != null ? new File(parentFolder, "StoneAttack/leaderboard.db") : null,
                parentFolder != null ? new File(parentFolder, "Leaderboard/leaderboard.db") : null,
                new File("leaderboard.db"),
                new File(plugin.getDataFolder(), "data/ztone.db"),
                new File(plugin.getDataFolder(), "ztone.db")
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
                        plugin.getLogger().info("Bestehende Leaderboard-Datenbank von '" + candidate.getAbsolutePath() + "' geladen.");
                        break;
                    } catch (Exception e) {
                        plugin.getLogger().warning("Konnte Leaderboard-Datenbank von '" + candidate.getAbsolutePath() + "' nicht kopieren: " + e.getMessage());
                    }
                }
            }
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("Ztone-Leaderboard-Pool");
        hikariConfig.setDriverClassName("org.sqlite.JDBC");
        hikariConfig.setJdbcUrl("jdbc:sqlite:" + targetFile.getAbsolutePath());
        hikariConfig.setMaximumPoolSize(2);
        hikariConfig.setConnectionTimeout(15000);
        hikariConfig.setMaxLifetime(0);
        hikariConfig.setConnectionInitSql("PRAGMA foreign_keys = ON;");

        this.ownDataSource = new HikariDataSource(hikariConfig);
        try (Connection conn = ownDataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode = WAL;");
            stmt.execute("PRAGMA busy_timeout = 5000;");
        } catch (SQLException ignored) {}

        initSchema();
        plugin.getLogger().info("Leaderboard-Datenbank erfolgreich geoeffnet: " + targetFile.getName());
    }

    private void initSchema() {
        try (Connection connection = getConnection()) {
            migrateOldTables(connection);

            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS stoneattack_snapshots (\n                            uuid TEXT NOT NULL,\n                            last_name TEXT NOT NULL,\n                            leaderboard_type TEXT NOT NULL,\n                            period TEXT NOT NULL,\n                            base_value REAL NOT NULL,\n                            period_start INTEGER NOT NULL,\n                            PRIMARY KEY (uuid, leaderboard_type, period)\n                        )\n                        """);

                statement.execute("""
                        CREATE TABLE IF NOT EXISTS stoneattack_exclusions (\n                            uuid TEXT NOT NULL,\n                            leaderboard_type TEXT NOT NULL,\n                            PRIMARY KEY (uuid, leaderboard_type)\n                        )\n                        """);

                statement.execute("""
                        CREATE TABLE IF NOT EXISTS stoneattack_period_state (\n                            period TEXT PRIMARY KEY,\n                            period_start INTEGER NOT NULL\n                        )\n                        """);

                // Kompatibilitaets-Views fuer alte Namen anlegen
                try {
                    statement.executeUpdate("CREATE VIEW IF NOT EXISTS snapshots AS SELECT * FROM stoneattack_snapshots;");
                    statement.executeUpdate("CREATE VIEW IF NOT EXISTS exclusions AS SELECT * FROM stoneattack_exclusions;");
                    statement.executeUpdate("CREATE VIEW IF NOT EXISTS period_state AS SELECT * FROM stoneattack_period_state;");
                } catch (SQLException ignored) {}
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Konnte Datenbankschema fuer StoneAttack nicht anlegen: " + e.getMessage());
        }
    }

    private void migrateOldTables(Connection conn) {
        migrateTable(conn, "snapshots", "stoneattack_snapshots");
        migrateTable(conn, "exclusions", "stoneattack_exclusions");
        migrateTable(conn, "period_state", "stoneattack_period_state");
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
                    plugin.getLogger().info("StoneAttack-Datenbank: Tabelle '" + oldTable + "' erfolgreich zu '" + newTable + "' migriert.");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Hinweis bei StoneAttack-Tabellen-Migration von '" + oldTable + "' zu '" + newTable + "': " + e.getMessage());
        }
    }

    public Connection getConnection() throws SQLException {
        if (ownDataSource != null) {
            return ownDataSource.getConnection();
        }
        if (plugin.getPlugin().getDatabaseManager() != null && plugin.getPlugin().getDatabaseManager().getDataSource() != null) {
            return plugin.getPlugin().getDatabaseManager().getConnection();
        }
        throw new SQLException("Keine DataSource fuer StoneAttack verfuegbar!");
    }

    public void close() {
        if (ownDataSource != null && !ownDataSource.isClosed()) {
            // Nur schliessen wenn es eine eigene DataSource ist (nicht die zentrale DataSource)
            if (plugin.getPlugin().getDatabaseManager() == null ||
                    ownDataSource != plugin.getPlugin().getDatabaseManager().getDataSource()) {
                ownDataSource.close();
            }
        }
    }
}

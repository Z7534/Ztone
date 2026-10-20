package de.Z7534.ztone.pvpmanager.database;

import com.zaxxer.hikari.HikariDataSource;
import de.Z7534.ztone.pvpmanager.Pvpmanager;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private final Pvpmanager plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(Pvpmanager plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        if (plugin.getClansystem().getDatabaseManager() != null) {
            this.dataSource = plugin.getClansystem().getDatabaseManager().getDataSource();
        }
        createTables();
    }

    private void createTables() {
        if (dataSource == null) {
            plugin.getLogger().warning("Zentrale Datenbank nicht verfuegbar - Tabellen fuer PvPManager konnten nicht geprueft werden.");
            return;
        }
        String sql = "CREATE TABLE IF NOT EXISTS pvpmanager_players ("
                + "uuid VARCHAR(36) PRIMARY KEY,"
                + "pvp_enabled BOOLEAN NOT NULL,"
                + "teleport_override BOOLEAN NOT NULL DEFAULT 0"
                + ");";

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException exception) {
            plugin.getLogger().severe("Konnte die Tabellen nicht erstellen: " + exception.getMessage());
        }
    }

    public void close() {
        // DataSource wird zentral in Ztone verwaltet
    }

    public void disconnect() {
        // DataSource wird zentral in Ztone verwaltet
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null && plugin.getClansystem().getDatabaseManager() != null) {
            this.dataSource = plugin.getClansystem().getDatabaseManager().getDataSource();
        }
        if (dataSource == null) {
            throw new SQLException("Zentrale DataSource ist nicht initialisiert.");
        }
        return dataSource.getConnection();
    }
}

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
            plugin.getLogger().warning("Zentrale Datenbank nicht verfuegbar - Bereinigung fuer PvPManager uebersprungen.");
            return;
        }
        // Temporaere Spieler-PvP-Daten werden nicht persistent in der DB gespeichert,
        // daher wird die alte Tabelle vollstaendig zurueckgesetzt/entfernt.
        String sql = "DROP TABLE IF EXISTS pvpmanager_players;";

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException exception) {
            plugin.getLogger().severe("Konnte temporaere PvPManager-Tabelle nicht bereinigen: " + exception.getMessage());
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

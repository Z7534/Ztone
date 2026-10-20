package de.Z7534.ztone.clansystem.storage;

import com.zaxxer.hikari.HikariConfig;
import de.Z7534.ztone.clansystem.managers.ConfigManager;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

public interface SqlDialect {

    StorageType type();

    void configureDataSource(HikariConfig hikariConfig, ConfigManager config, File dataFolder);

    void afterConnect(Connection connection) throws SQLException;

    String autoIncrementPrimaryKey();

    boolean hasColumn(Connection connection, String table, String column) throws SQLException;

    String upsert(String insertSql, String[] conflictColumns, String[] updateColumns);
}

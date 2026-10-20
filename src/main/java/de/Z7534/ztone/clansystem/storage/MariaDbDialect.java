package de.Z7534.ztone.clansystem.storage;

import com.zaxxer.hikari.HikariConfig;
import de.Z7534.ztone.clansystem.managers.ConfigManager;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class MariaDbDialect implements SqlDialect {

    @Override
    public StorageType type() {
        return StorageType.MARIADB;
    }

    @Override
    public void configureDataSource(HikariConfig hikariConfig, ConfigManager config, File dataFolder) {
        String jdbcUrl = "jdbc:mariadb://" + config.getMariaDbHost() + ":" + config.getMariaDbPort()
                + "/" + config.getMariaDbDatabase()
                + "?useUnicode=true&characterEncoding=utf8&useSSL=" + config.isMariaDbUseSsl();

        hikariConfig.setDriverClassName("org.mariadb.jdbc.Driver");
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(config.getMariaDbUsername());
        hikariConfig.setPassword(config.getMariaDbPassword());
        hikariConfig.setMaximumPoolSize(Math.max(1, config.getMariaDbPoolSize()));
        hikariConfig.setConnectionTimeout(10000);
        hikariConfig.setMaxLifetime(1800000);
    }

    @Override
    public void afterConnect(Connection connection) {
    }

    @Override
    public String autoIncrementPrimaryKey() {
        return "INT PRIMARY KEY AUTO_INCREMENT";
    }

    @Override
    public boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    @Override
    public String upsert(String insertSql, String[] conflictColumns, String[] updateColumns) {
        StringBuilder sb = new StringBuilder(insertSql);
        sb.append(" ON DUPLICATE KEY UPDATE ");
        for (int i = 0; i < updateColumns.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(updateColumns[i]).append(" = VALUES(").append(updateColumns[i]).append(")");
        }
        return sb.toString();
    }
}

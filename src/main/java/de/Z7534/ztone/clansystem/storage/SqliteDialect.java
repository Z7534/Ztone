package de.Z7534.ztone.clansystem.storage;

import com.zaxxer.hikari.HikariConfig;
import de.Z7534.ztone.clansystem.managers.ConfigManager;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class SqliteDialect implements SqlDialect {

    @Override
    public StorageType type() {
        return StorageType.SQLITE;
    }

    @Override
    public void configureDataSource(HikariConfig hikariConfig, ConfigManager config, File dataFolder) {
        File dataDir = new File(dataFolder, "data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        String rawName = config.getDbFile();
        if (rawName == null || rawName.isBlank()) {
            rawName = "clans.db";
        }

        File targetFile;
        if (new File(rawName).isAbsolute()) {
            targetFile = new File(rawName);
        } else {
            if (!rawName.contains("/") && !rawName.contains("\\")) {
                // Wenn kein Ordner angegeben ist, direkt im dataFolder (plugins/Ztone/clans.db) oder data/
                File rootCandidate = new File(dataFolder, rawName);
                File dataCandidate = new File(dataDir, rawName);
                if (rootCandidate.exists()) {
                    targetFile = rootCandidate;
                } else if (dataCandidate.exists()) {
                    targetFile = dataCandidate;
                } else {
                    targetFile = rootCandidate;
                }
            } else {
                targetFile = new File(dataFolder, rawName);
            }
        }

        if (targetFile.getParentFile() != null && !targetFile.getParentFile().exists()) {
            targetFile.getParentFile().mkdirs();
        }

        // Automatische Erkennung und Migration alter Speicherorte (wie vorher)
        File parentFolder = dataFolder.getParentFile();
        File[] candidates = new File[] {
                new File(dataFolder, "clans.db"),
                new File(dataFolder, "data/clans.db"),
                new File(dataFolder, "data/clansystem/clans.db"),
                parentFolder != null ? new File(parentFolder, "Clansystem/clans.db") : null,
                parentFolder != null ? new File(parentFolder, "Clansystem/data/clans.db") : null,
                parentFolder != null ? new File(parentFolder, "Clans/clans.db") : null,
                parentFolder != null ? new File(parentFolder, "Clans/data/clans.db") : null,
                new File("clans.db"),
                new File(dataFolder, "data/ztone.db"),
                new File(dataFolder, "ztone.db")
        };

        for (File candidate : candidates) {
            if (candidate != null && candidate.exists() && candidate.length() > 0) {
                if (!targetFile.exists() || targetFile.length() == 0) {
                    migrateFile(candidate, targetFile);
                    if (targetFile.exists() && targetFile.length() > 0) {
                        break;
                    }
                }
            }
        }

        hikariConfig.setDriverClassName("org.sqlite.JDBC");
        hikariConfig.setJdbcUrl("jdbc:sqlite:" + targetFile.getAbsolutePath());
        hikariConfig.setMaximumPoolSize(5);
        hikariConfig.setConnectionTimeout(15000);
        hikariConfig.setMaxLifetime(0);
        hikariConfig.setConnectionInitSql("PRAGMA foreign_keys = ON;");
    }

    private void migrateFile(File oldFile, File newFile) {
        if (oldFile != null && oldFile.exists() && !oldFile.equals(newFile) && (!newFile.exists() || newFile.length() == 0)) {
            try {
                if (newFile.getParentFile() != null) {
                    newFile.getParentFile().mkdirs();
                }
                Files.copy(oldFile.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                File oldWal = new File(oldFile.getAbsolutePath() + "-wal");
                if (oldWal.exists()) {
                    Files.copy(oldWal.toPath(), new File(newFile.getAbsolutePath() + "-wal").toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                File oldShm = new File(oldFile.getAbsolutePath() + "-shm");
                if (oldShm.exists()) {
                    Files.copy(oldShm.toPath(), new File(newFile.getAbsolutePath() + "-shm").toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void afterConnect(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode = WAL");
            stmt.execute("PRAGMA busy_timeout = 5000");
        }
    }

    @Override
    public String autoIncrementPrimaryKey() {
        return "INTEGER PRIMARY KEY AUTOINCREMENT";
    }

    @Override
    public boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (rs.getString("name").equalsIgnoreCase(column)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String upsert(String insertSql, String[] conflictColumns, String[] updateColumns) {
        StringBuilder sb = new StringBuilder(insertSql);
        sb.append(" ON CONFLICT(").append(String.join(", ", conflictColumns)).append(") DO UPDATE SET ");
        for (int i = 0; i < updateColumns.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(updateColumns[i]).append(" = excluded.").append(updateColumns[i]);
        }
        return sb.toString();
    }
}

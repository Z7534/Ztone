package de.Z7534.ztone.playrewards.database;

import de.Z7534.ztone.clansystem.managers.DatabaseManager;
import de.Z7534.ztone.playrewards.model.PlayerData;

import java.sql.*;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SQLiteStorage {

    private final DatabaseManager dbManager;
    private final Logger logger;

    public SQLiteStorage(DatabaseManager dbManager, Logger logger) {
        this.dbManager = dbManager;
        this.logger = logger;
    }

    public synchronized void initialize() {
        try {
            if (dbManager == null || dbManager.getDataSource() == null) {
                logger.warning("Zentraler DatabaseManager ist fuer PlayRewards nicht verfuegbar!");
                return;
            }
            try (Connection connection = dbManager.getConnection()) {
                migrateOldTable(connection);

                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS playrewards_player_data (" +
                            "uuid VARCHAR(36) PRIMARY KEY, " +
                            "current_streak INT DEFAULT 0, " +
                            "last_streak_date VARCHAR(30), " +
                            "streak_claimed_today INT DEFAULT 0, " +
                            "protection_points INT DEFAULT 1, " +
                            "last_daily_date VARCHAR(30), " +
                            "claimed_daily_tiers TEXT, " +
                            "claimed_playtime_tiers TEXT, " +
                            "claimed_streak_milestones TEXT, " +
                            "local_playtime_seconds BIGINT DEFAULT 0, " +
                            "daily_playtime_seconds BIGINT DEFAULT 0" +
                            ");");
                }

                ensureColumns(connection);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Fehler beim Initialisieren der PlayRewards-Tabelle: " + e.getMessage(), e);
        }
    }

    private void ensureColumns(Connection conn) {
        try {
            DatabaseMetaData meta = conn.getMetaData();
            boolean hasDaily = false;
            try (ResultSet rs = meta.getColumns(null, null, "playrewards_player_data", "daily_playtime_seconds")) {
                if (rs.next()) {
                    hasDaily = true;
                }
            }
            if (!hasDaily) {
                try (Statement st = conn.createStatement()) {
                    st.executeUpdate("ALTER TABLE playrewards_player_data ADD COLUMN daily_playtime_seconds BIGINT DEFAULT 0;");
                    logger.info("Spalte 'daily_playtime_seconds' zu playrewards_player_data hinzugefuegt.");
                }
            }
        } catch (Exception e) {
            logger.warning("Hinweis beim Pruefen der daily_playtime_seconds Spalte: " + e.getMessage());
        }
    }

    private void migrateOldTable(Connection conn) {
        try {
            DatabaseMetaData meta = conn.getMetaData();
            boolean oldExists = false;
            boolean newExists = false;
            try (ResultSet rs = meta.getColumns(null, null, null, null)) {
                // Check tables
            }
            try (ResultSet rs = meta.getTables(null, null, null, new String[]{"TABLE"})) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    if ("player_data".equalsIgnoreCase(name)) oldExists = true;
                    if ("playrewards_player_data".equalsIgnoreCase(name)) newExists = true;
                }
            }
            if (oldExists && !newExists) {
                try (Statement st = conn.createStatement()) {
                    st.execute("ALTER TABLE player_data RENAME TO playrewards_player_data");
                    logger.info("PlayRewards-Datenbank: Tabelle 'player_data' erfolgreich zu 'playrewards_player_data' migriert.");
                }
            }
        } catch (Exception e) {
            logger.warning("Hinweis bei PlayRewards-Tabellen-Migration: " + e.getMessage());
        }
    }

    public synchronized PlayerData loadPlayerData(UUID uuid) {
        PlayerData data = new PlayerData(uuid);
        String sql = "SELECT * FROM playrewards_player_data WHERE uuid = ?;";

        try (Connection connection = dbManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    data.setCurrentStreak(rs.getInt("current_streak"));
                    String streakDateStr = rs.getString("last_streak_date");
                    if (streakDateStr != null && !streakDateStr.isEmpty()) {
                        data.setLastStreakDate(LocalDate.parse(streakDateStr));
                    }
                    data.setStreakClaimedToday(rs.getInt("streak_claimed_today") == 1);
                    data.setProtectionPointsLeft(rs.getInt("protection_points"));

                    String dailyDateStr = rs.getString("last_daily_date");
                    if (dailyDateStr != null && !dailyDateStr.isEmpty()) {
                        data.setLastDailyDate(LocalDate.parse(dailyDateStr));
                    }

                    String dailyTiers = rs.getString("claimed_daily_tiers");
                    if (dailyTiers != null && !dailyTiers.isEmpty()) {
                        data.getClaimedDailyTiers().addAll(Arrays.asList(dailyTiers.split(",")));
                    }

                    String ptTiers = rs.getString("claimed_playtime_tiers");
                    if (ptTiers != null && !ptTiers.isEmpty()) {
                        data.getClaimedPlaytimeTiers().addAll(Arrays.asList(ptTiers.split(",")));
                    }

                    String streakMilestones = rs.getString("claimed_streak_milestones");
                    if (streakMilestones != null && !streakMilestones.isEmpty()) {
                        for (String s : streakMilestones.split(",")) {
                            try {
                                data.getClaimedStreakMilestones().add(Integer.parseInt(s.trim()));
                            } catch (NumberFormatException ignored) {}
                        }
                    }

                    data.setLocalPlaytimeSeconds(rs.getLong("local_playtime_seconds"));
                    try {
                        data.setDailyPlaytimeSeconds(rs.getLong("daily_playtime_seconds"));
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Konnte Spielerdaten fuer " + uuid + " nicht aus SQLite laden: " + e.getMessage(), e);
        }
        return data;
    }

    public synchronized void savePlayerData(PlayerData data) {
        if (data == null) return;
        String sql = "INSERT INTO playrewards_player_data (" +
                "uuid, current_streak, last_streak_date, streak_claimed_today, protection_points, " +
                "last_daily_date, claimed_daily_tiers, claimed_playtime_tiers, claimed_streak_milestones, " +
                "local_playtime_seconds, daily_playtime_seconds" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET " +
                "current_streak = excluded.current_streak, " +
                "last_streak_date = excluded.last_streak_date, " +
                "streak_claimed_today = excluded.streak_claimed_today, " +
                "protection_points = excluded.protection_points, " +
                "last_daily_date = excluded.last_daily_date, " +
                "claimed_daily_tiers = excluded.claimed_daily_tiers, " +
                "claimed_playtime_tiers = excluded.claimed_playtime_tiers, " +
                "claimed_streak_milestones = excluded.claimed_streak_milestones, " +
                "local_playtime_seconds = excluded.local_playtime_seconds, " +
                "daily_playtime_seconds = excluded.daily_playtime_seconds;";

        try (Connection connection = dbManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, data.getUuid().toString());
            ps.setInt(2, data.getCurrentStreak());
            ps.setString(3, data.getLastStreakDate() != null ? data.getLastStreakDate().toString() : null);
            ps.setInt(4, data.isStreakClaimedToday() ? 1 : 0);
            ps.setInt(5, data.getProtectionPointsLeft());
            ps.setString(6, data.getLastDailyDate() != null ? data.getLastDailyDate().toString() : null);
            ps.setString(7, String.join(",", data.getClaimedDailyTiers()));
            ps.setString(8, String.join(",", data.getClaimedPlaytimeTiers()));

            StringBuilder sbMilestones = new StringBuilder();
            for (Integer m : data.getClaimedStreakMilestones()) {
                if (sbMilestones.length() > 0) sbMilestones.append(",");
                sbMilestones.append(m);
            }
            ps.setString(9, sbMilestones.toString());
            ps.setLong(10, data.getLocalPlaytimeSeconds());
            ps.setLong(11, data.getDailyPlaytimeSeconds());

            ps.executeUpdate();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Konnte Spielerdaten fuer " + data.getUuid() + " nicht in SQLite speichern: " + e.getMessage(), e);
        }
    }

    public synchronized void close() {
        // DataSource wird zentral in Ztone verwaltet
    }
}

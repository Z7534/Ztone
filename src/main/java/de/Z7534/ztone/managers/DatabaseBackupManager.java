package de.Z7534.ztone.managers;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.clansystem.managers.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

public class DatabaseBackupManager {

    private final Ztone plugin;
    private final File backupDir;
    private BukkitTask task;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

    public DatabaseBackupManager(Ztone plugin) {
        this.plugin = plugin;
        this.backupDir = new File(plugin.getDataFolder(), "backups");
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }
    }

    public void start() {
        ConfigManager cfg = plugin.getConfigManager();
        if (cfg != null && !cfg.isBackupEnabled()) {
            plugin.getLogger().info("Automatisches Datenbank-Backup ist deaktiviert.");
            return;
        }

        int intervalMinutes = cfg != null ? Math.max(1, cfg.getBackupIntervalMinutes()) : 5;
        long intervalTicks = intervalMinutes * 60L * 20L;

        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::createBackup, intervalTicks, intervalTicks);
        plugin.getLogger().info("Automatisches Datenbank-Backup aktiv (alle " + intervalMinutes + " Minuten). Zielordner: plugins/Ztone/backups/");
    }

    public void stop() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
            task = null;
        }
        createBackup();
    }

    public synchronized void createBackup() {
        try {
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }

            String timestamp = DATE_FORMAT.format(new Date());

            // 1. Zentrale Clan-Datenbank sichern
            if (plugin.getDatabaseManager() != null && plugin.getDatabaseManager().getDataSource() != null) {
                String centralDbName = plugin.getConfigManager() != null ? plugin.getConfigManager().getDbFile() : "clans.db";
                if (centralDbName == null || centralDbName.isBlank()) {
                    centralDbName = "clans.db";
                }
                File baseFile = new File(centralDbName);
                String baseName = baseFile.getName().replace(".db", "");
                File targetBackup = new File(backupDir, baseName + "_" + timestamp + ".db");

                boolean success = false;
                try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                    success = backupSqliteViaVacuum(conn, targetBackup);
                } catch (Exception ignored) {}

                if (!success) {
                    File srcFile = resolveDbFile(centralDbName);
                    if (srcFile != null && srcFile.exists()) {
                        copyFileWithWal(srcFile, targetBackup);
                        success = targetBackup.exists() && targetBackup.length() > 0;
                    }
                }
                if (success) {
                    plugin.getLogger().info("Datenbank-Backup erfolgreich erstellt: " + targetBackup.getName() + " (" + (targetBackup.length() / 1024) + " KB)");
                }
            }

            // 2. Claims-Datenbank sichern (falls separat vorhanden)
            if (plugin.getClaims() != null && plugin.getClaims().getDatabase() != null) {
                File claimsTarget = new File(backupDir, "claims_" + timestamp + ".db");
                boolean claimsSuccess = false;
                try (Connection claimsConn = plugin.getClaims().getDatabase().getConnection()) {
                    claimsSuccess = backupSqliteViaVacuum(claimsConn, claimsTarget);
                } catch (Exception ignored) {}

                if (!claimsSuccess) {
                    File claimsSrc = new File(plugin.getDataFolder(), "claims.db");
                    if (claimsSrc.exists()) {
                        copyFileWithWal(claimsSrc, claimsTarget);
                        claimsSuccess = claimsTarget.exists() && claimsTarget.length() > 0;
                    }
                }
                if (claimsSuccess && claimsTarget.exists()) {
                    plugin.getLogger().info("Claims-Datenbank-Backup erfolgreich erstellt: " + claimsTarget.getName() + " (" + (claimsTarget.length() / 1024) + " KB)");
                }
            }

            // 3. Leaderboard / StoneAttack Datenbank sichern
            File lbFile = new File(plugin.getDataFolder(), "data/leaderboard.db");
            if (!lbFile.exists()) lbFile = new File(plugin.getDataFolder(), "leaderboard.db");
            if (lbFile.exists() && lbFile.length() > 0) {
                File lbTarget = new File(backupDir, "leaderboard_" + timestamp + ".db");
                copyFileWithWal(lbFile, lbTarget);
            }

            cleanOldBackups();

        } catch (Throwable t) {
            plugin.getLogger().warning("Fehler beim Erstellen des Datenbank-Backups: " + t.getMessage());
        }
    }

    private boolean backupSqliteViaVacuum(Connection conn, File target) {
        if (conn == null) return false;
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA wal_checkpoint(PASSIVE)");
            String path = target.getAbsolutePath().replace('\\', '/');
            st.execute("VACUUM INTO '" + path + "'");
            return target.exists() && target.length() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private File resolveDbFile(String rawName) {
        File dataDir = new File(plugin.getDataFolder(), "data");
        File candidate1 = new File(plugin.getDataFolder(), rawName);
        if (candidate1.exists()) return candidate1;
        File candidate2 = new File(dataDir, rawName);
        if (candidate2.exists()) return candidate2;
        return candidate1;
    }

    private void copyFileWithWal(File src, File dst) {
        try {
            if (dst.getParentFile() != null) dst.getParentFile().mkdirs();
            Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
            File wal = new File(src.getAbsolutePath() + "-wal");
            if (wal.exists()) {
                Files.copy(wal.toPath(), new File(dst.getAbsolutePath() + "-wal").toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            File shm = new File(src.getAbsolutePath() + "-shm");
            if (shm.exists()) {
                Files.copy(shm.toPath(), new File(dst.getAbsolutePath() + "-shm").toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {}
    }

    private void cleanOldBackups() {
        ConfigManager cfg = plugin.getConfigManager();
        int maxBackups = cfg != null ? Math.max(5, cfg.getBackupMaxCount()) : 50;
        File[] files = backupDir.listFiles((dir, name) -> name.endsWith(".db"));
        if (files != null && files.length > maxBackups) {
            Arrays.sort(files, Comparator.comparingLong(File::lastModified));
            int toDelete = files.length - maxBackups;
            for (int i = 0; i < toDelete; i++) {
                files[i].delete();
            }
        }
    }
}

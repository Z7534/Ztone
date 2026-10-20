package de.Z7534.ztone.playrewards.service;

import de.Z7534.ztone.playrewards.PlayReward;
import de.Z7534.ztone.playrewards.model.PlayerData;
import de.Z7534.ztone.playrewards.util.MessageUtil;
import de.Z7534.ztone.playrewards.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public class StreakService {

    private final PlayReward plugin;
    private LocalDate lastCheckDate = TimeUtil.getTodayInBerlin();

    public StreakService(PlayReward plugin) {
        this.plugin = plugin;
    }

    /**
     * Wird beim Server-Beitritt aufgerufen, um die Streak des Spielers zu prüfen.
     */
    public void handlePlayerLogin(Player player) {
        PlayerData data = plugin.getDatabaseManager().getCachedData(player.getUniqueId());
        if (data == null) return;

        LocalDate today = TimeUtil.getTodayInBerlin();
        LocalDate lastLogin = data.getLastStreakDate();

        // Überprüfung, ob ein neuer Tag für Daily Rewards angebrochen ist
        if (data.getLastDailyDate() == null || !data.getLastDailyDate().equals(today)) {
            data.setLastDailyDate(today);
            data.getClaimedDailyTiers().clear();
            data.setDailyPlaytimeSeconds(0L);
        }

        if (lastLogin == null) {
            // Allererster Login
            data.setCurrentStreak(1);
            data.setLastStreakDate(today);
            data.setStreakClaimedToday(false);
            return;
        }

        long daysBetween = ChronoUnit.DAYS.between(lastLogin, today);
        if (daysBetween == 0) {
            // Am selben Tag wieder eingeloggt - Streak bleibt wie sie ist
            return;
        }

        if (daysBetween == 1) {
            // Ein Tag später -> Streak kann heute erhöht/abgeholt werden
            data.setStreakClaimedToday(false);
            data.setLastStreakDate(today);
            data.setCurrentStreak(data.getCurrentStreak() + 1);
        } else if (daysBetween == 2) {
            // Genau einen Tag verpasst -> Streak-Saver prüfen!
            if (player.hasPermission("playrewards.streak.saver") && data.getProtectionPointsLeft() > 0) {
                data.setProtectionPointsLeft(data.getProtectionPointsLeft() - 1);
                data.setLastStreakDate(today);
                data.setStreakClaimedToday(false);
                data.setCurrentStreak(data.getCurrentStreak() + 1);

                MessageUtil.sendMessage(player, plugin.getPrefix(),
                        "<green>🛡 Dein <yellow>Streak-Saver</yellow> hat deine Serie von <gold>" +
                        data.getCurrentStreak() + " Tagen</gold> gerettet! (Verbleibende Schutzpunkte: " +
                        data.getProtectionPointsLeft() + ")");
            } else {
                // Streak verloren
                data.setCurrentStreak(1);
                data.setLastStreakDate(today);
                data.setStreakClaimedToday(false);
                data.getClaimedStreakMilestones().clear();

                MessageUtil.sendMessage(player, plugin.getPrefix(),
                        "<red>Deine Streak wurde zurückgesetzt, da du mehr als einen Tag abwesend warst. Starte jetzt bei Tag 1!");
            }
        } else {
            // Mehr als einen Tag verpasst
            data.setCurrentStreak(1);
            data.setLastStreakDate(today);
            data.setStreakClaimedToday(false);
            data.getClaimedStreakMilestones().clear();
        }
    }

    /**
     * Tägliche Überprüfung (z. B. periodischer Scheduler um 0:00 Uhr).
     */
    public void checkMidnightResets() {
        LocalDate today = TimeUtil.getTodayInBerlin();
        if (!today.equals(lastCheckDate)) {
            lastCheckDate = today;

            // Neuer Tag: Daily Belohnungen zurücksetzen
            for (PlayerData data : plugin.getDatabaseManager().getAllCachedData().values()) {
                data.setLastDailyDate(today);
                data.getClaimedDailyTiers().clear();
                data.setDailyPlaytimeSeconds(0L);
                data.setStreakClaimedToday(false);
                data.resetSessionStartTime();
            }

            // Prüfen, ob heute Montag ist -> Streak-Saver Schutzpunkte resetten
            if (TimeUtil.isMondayToday()) {
                int maxPoints = plugin.getConfig().getInt("streak_saver.max_protection_points", 1);
                for (PlayerData data : plugin.getDatabaseManager().getAllCachedData().values()) {
                    data.setProtectionPointsLeft(maxPoints);
                }
                plugin.getLogger().info("Wöchentlicher Streak-Saver Reset durchgeführt (Montag 00:00 Uhr).");
            }
        }
    }

    /**
     * Setzt die Streak eines Spielers manuell zurück (Admin-Kommando).
     */
    public boolean resetStreak(UUID uuid) {
        PlayerData data = plugin.getDatabaseManager().getCachedData(uuid);
        if (data != null) {
            data.setCurrentStreak(0);
            data.setLastStreakDate(null);
            data.setStreakClaimedToday(false);
            data.getClaimedStreakMilestones().clear();
            plugin.getDatabaseManager().savePlayerDataAsync(data);
            return true;
        }
        return false;
    }
}

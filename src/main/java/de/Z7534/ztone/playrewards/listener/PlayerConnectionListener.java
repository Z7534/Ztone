package de.Z7534.ztone.playrewards.listener;

import de.Z7534.ztone.playrewards.PlayReward;
import de.Z7534.ztone.playrewards.model.PlayerData;
import de.Z7534.ztone.playrewards.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerConnectionListener implements Listener {

    private final PlayReward plugin;

    public PlayerConnectionListener(PlayReward plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Daten asynchron laden
        plugin.getDatabaseManager().loadPlayerDataAsync(player.getUniqueId()).thenAccept(data -> {
            Bukkit.getScheduler().runTask(plugin.getPlugin(), () -> {
                if (!player.isOnline()) return;

                // Session initialisieren
                data.resetSessionStartTime();
                data.setLastActiveTime(System.currentTimeMillis());

                // Streak überprüfen
                plugin.getStreakService().handlePlayerLogin(player);

                // Spielzeit abfragen / Cache aufwärmen
                plugin.getPlaytimeService().getActivePlaytimeSeconds(player.getUniqueId());

                // Join-Reminder nach konfigurierter Verzögerung senden
                scheduleJoinReminder(player);
            });
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getDatabaseManager().getCachedData(player.getUniqueId());

        if (data != null) {
            // Netto-Session-Zeit zur lokalen und täglichen Spielzeit addieren
            long sessionSec = (System.currentTimeMillis() - data.getSessionStartTime()) / 1000L;
            long activeSec = Math.max(0, sessionSec - data.getTotalAfkSecondsInSession());
            data.addLocalPlaytimeSeconds(activeSec);
            data.addDailyPlaytimeSeconds(activeSec);

            // Asynchron in SQLite sichern
            plugin.getDatabaseManager().savePlayerDataAsync(data);
        }
    }

    private void scheduleJoinReminder(Player player) {
        if (!plugin.getConfig().getBoolean("join_reminder.enabled", true)) {
            return;
        }

        long delayTicks = plugin.getConfig().getLong("join_reminder.delay_ticks", 40);
        Bukkit.getScheduler().runTaskLater(plugin.getPlugin(), () -> {
            if (!player.isOnline()) return;

            PlayerData data = plugin.getDatabaseManager().getCachedData(player.getUniqueId());
            if (data == null) return;

            int availableDaily = plugin.getRewardClaimService().getAvailableDailyRewardsCount(player.getUniqueId());
            boolean streakAvailable = !data.isStreakClaimedToday();

            if (availableDaily > 0 || streakAvailable) {
                String type = plugin.getConfig().getString("join_reminder.type", "ACTIONBAR");
                String dailyMsg = plugin.getConfig().getString("join_reminder.message_daily", "<gold>★ Du hast noch unbeanspruchte Tages-Belohnungen! <yellow>(/playrewards)");
                String streakMsg = plugin.getConfig().getString("join_reminder.message_streak", "<green>🔥 Deine tägliche Streak ist bereit! <yellow>(/playrewards)");

                String finalMsg = (availableDaily > 0) ? dailyMsg : streakMsg;

                if ("ACTIONBAR".equalsIgnoreCase(type) || "BOTH".equalsIgnoreCase(type)) {
                    MessageUtil.sendActionBar(player, finalMsg);
                }
                if ("CHAT".equalsIgnoreCase(type) || "BOTH".equalsIgnoreCase(type)) {
                    MessageUtil.sendMessage(player, plugin.getPrefix(), finalMsg);
                }
            }
        }, delayTicks);
    }
}

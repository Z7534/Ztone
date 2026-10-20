package de.Z7534.ztone.playrewards.hook;

import de.Z7534.ztone.playrewards.PlayReward;
import de.Z7534.ztone.playrewards.model.PlayerData;
import de.Z7534.ztone.playrewards.util.TimeUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

public class PlaceholderAPIHook extends PlaceholderExpansion {

    private final PlayReward plugin;

    public PlaceholderAPIHook(PlayReward plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "playrewards";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Z7534";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        PlayerData data = plugin.getDatabaseManager().getCachedData(player.getUniqueId());
        long playtimeSeconds = plugin.getPlaytimeService().getCachedPlaytimeSeconds(player.getUniqueId());

        switch (params.toLowerCase()) {
            case "streak":
                return String.valueOf(data != null ? data.getCurrentStreak() : 0);

            case "streak_claimed_today":
                return (data != null && data.isStreakClaimedToday()) ? "true" : "false";

            case "playtime_seconds":
                return String.valueOf(playtimeSeconds);

            case "playtime_hours":
            case "playtime_hour":
            case "hours":
                // Reine Spielzeit in vollen Stunden als Zahl (z. B. 128)
                return String.valueOf(TimeUtil.getHours(playtimeSeconds));

            case "playtime_days":
            case "days":
                // Reine Spielzeit in vollen Tagen als Zahl
                return String.valueOf(TimeUnit.SECONDS.toDays(playtimeSeconds));

            case "playtime_formatted":
                return TimeUtil.formatDuration(playtimeSeconds);

            case "playtime_days_formatted":
            case "playtime_detailed":
                // Detailliert formatiert (z. B. "5 Tage, 8 Stunden, 24 Minuten")
                return TimeUtil.formatDaysAndHours(playtimeSeconds);

            case "daily_available": {
                if (data == null) return "0";
                int availableCount = plugin.getRewardClaimService().getAvailableDailyRewardsCount(player.getUniqueId());
                return String.valueOf(availableCount);
            }

            case "next_reward_time":
                return TimeUtil.getTimeUntilMidnight();

            case "streak_saver_points":
                return String.valueOf(data != null ? data.getProtectionPointsLeft() : 0);

            default:
                return null;
        }
    }
}

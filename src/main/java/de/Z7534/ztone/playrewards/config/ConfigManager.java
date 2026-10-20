package de.Z7534.ztone.playrewards.config;

import de.Z7534.ztone.playrewards.PlayReward;

import java.io.File;

public class ConfigManager {

    private final PlayReward plugin;
    private final DailyConfig dailyConfig;
    private final StreakConfig streakConfig;
    private final PlaytimeConfig playtimeConfig;

    public ConfigManager(PlayReward plugin) {
        this.plugin = plugin;
        this.dailyConfig = new DailyConfig(plugin.getDataFolder());
        this.streakConfig = new StreakConfig(plugin.getDataFolder());
        this.playtimeConfig = new PlaytimeConfig(plugin.getDataFolder());
    }

    public void initialize() {
        plugin.saveDefaultConfig();

        saveResourceIfNotExists("rewards/daily.yml");
        saveResourceIfNotExists("rewards/streak.yml");
        saveResourceIfNotExists("rewards/playtime.yml");

        reloadAll();
    }

    private void saveResourceIfNotExists(String resourcePath) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            plugin.saveResource(resourcePath, false);
        }
    }

    public void reloadAll() {
        plugin.reloadConfig();
        dailyConfig.load();
        streakConfig.load();
        playtimeConfig.load();
    }

    public DailyConfig getDailyConfig() {
        return dailyConfig;
    }

    public StreakConfig getStreakConfig() {
        return streakConfig;
    }

    public PlaytimeConfig getPlaytimeConfig() {
        return playtimeConfig;
    }
}

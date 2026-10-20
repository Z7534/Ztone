package de.Z7534.ztone.playrewards;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.playrewards.command.PlayRewardsCommand;
import de.Z7534.ztone.playrewards.command.PlayRewardsTabCompleter;
import de.Z7534.ztone.playrewards.config.ConfigManager;
import de.Z7534.ztone.playrewards.database.DatabaseManager;
import de.Z7534.ztone.playrewards.hook.GeyserHook;
import de.Z7534.ztone.playrewards.hook.PlaceholderAPIHook;
import de.Z7534.ztone.playrewards.hook.PlanHook;
import de.Z7534.ztone.playrewards.listener.AfkDetectionListener;
import de.Z7534.ztone.playrewards.listener.InventoryClickListener;
import de.Z7534.ztone.playrewards.listener.PlayerConnectionListener;
import de.Z7534.ztone.playrewards.service.PlaytimeService;
import de.Z7534.ztone.playrewards.service.RewardClaimService;
import de.Z7534.ztone.playrewards.service.StreakService;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class PlayReward {

    private static PlayReward instance;

    private final Ztone plugin;
    private FileConfiguration config;
    private final List<BukkitTask> scheduledTasks = new ArrayList<>();

    private ConfigManager configManager;
    private DatabaseManager databaseManager;

    private PlanHook planHook;
    private GeyserHook geyserHook;
    private PlaceholderAPIHook placeholderAPIHook;

    private PlaytimeService playtimeService;
    private StreakService streakService;
    private RewardClaimService rewardClaimService;

    public PlayReward(Ztone plugin) {
        this.plugin = plugin;
    }

    public void onEnable() {
        instance = this;

        // 1. Konfiguration initialisieren
        this.configManager = new ConfigManager(this);
        this.configManager.initialize();

        // 2. Datenbank (SQLite Fallback & Speicher) initialisieren
        this.databaseManager = new DatabaseManager(this);
        this.databaseManager.initialize();

        // 3. Externe Plugin-Hooks initialisieren
        this.planHook = new PlanHook(getLogger());
        this.geyserHook = new GeyserHook();

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            this.placeholderAPIHook = new PlaceholderAPIHook(this);
            this.placeholderAPIHook.register();
            getLogger().info("PlayRewards: PlaceholderAPI erfolgreich angebunden!");
        }

        // 4. Kern-Services initialisieren
        this.playtimeService = new PlaytimeService(this);
        this.streakService = new StreakService(this);
        this.rewardClaimService = new RewardClaimService(this);

        // 5. Listener registrieren
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new PlayerConnectionListener(this), plugin);
        pm.registerEvents(new InventoryClickListener(this), plugin);
        pm.registerEvents(new AfkDetectionListener(this), plugin);

        // 6. Befehle registrieren
        PluginCommand command = plugin.getCommand("playrewards");
        if (command != null) {
            command.setExecutor(new PlayRewardsCommand(this));
            command.setTabCompleter(new PlayRewardsTabCompleter());
        }

        // 7. Periodische Hintergrund-Aufgaben starten
        startScheduledTasks();

        getLogger().info("PlayRewards-Modul erfolgreich aktiviert!");
    }

    public void onDisable() {
        // Scheduler stoppen
        for (BukkitTask task : scheduledTasks) {
            try {
                task.cancel();
            } catch (Exception ignored) {}
        }
        scheduledTasks.clear();

        if (placeholderAPIHook != null) {
            try {
                placeholderAPIHook.unregister();
            } catch (Throwable ignored) {}
        }

        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info("PlayRewards-Modul wurde deaktiviert.");
    }

    private void startScheduledTasks() {
        // Task für AFK-Erkennung & Spielzeit-Aktualisierung
        scheduledTasks.add(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (playtimeService != null) {
                playtimeService.updateAfkAndSessions();
            }
        }, 100L, 200L));

        // Task für 00:00 Uhr Mitternachts-Resets & montaglichen Streak-Saver-Reset (Prüfung jede Minute)
        scheduledTasks.add(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (streakService != null) {
                streakService.checkMidnightResets();
            }
        }, 200L, 1200L));
    }

    public void saveDefaultConfig() {
        saveResource("config.yml", false);
    }

    public void saveResource(String resourcePath, boolean replace) {
        if (resourcePath == null || resourcePath.isEmpty()) return;
        resourcePath = resourcePath.replace('\\', '/');
        File outFile = new File(getDataFolder(), resourcePath);
        if (!replace && outFile.exists()) {
            return;
        }
        outFile.getParentFile().mkdirs();
        String embeddedPath = "playrewards/" + resourcePath;
        try (InputStream in = plugin.getResource(embeddedPath)) {
            if (in != null) {
                Files.copy(in, outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                getLogger().warning("Resource not found: " + embeddedPath);
            }
        } catch (Exception e) {
            getLogger().severe("Could not save resource " + resourcePath + ": " + e.getMessage());
        }
    }

    public void reloadConfig() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveDefaultConfig();
        }
        this.config = YamlConfiguration.loadConfiguration(configFile);
        try (InputStream defStream = plugin.getResource("playrewards/config.yml")) {
            if (defStream != null) {
                config.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defStream, StandardCharsets.UTF_8)));
            }
        } catch (Exception ignored) {
        }
    }

    public FileConfiguration getConfig() {
        if (config == null) {
            reloadConfig();
        }
        return config;
    }

    public File getDataFolder() {
        return new File(plugin.getDataFolder(), "playrewards");
    }

    public Ztone getPlugin() {
        return plugin;
    }

    public Server getServer() {
        return plugin.getServer();
    }

    public Logger getLogger() {
        return plugin.getLogger();
    }

    public PluginDescriptionFile getDescription() {
        return plugin.getDescription();
    }

    public static PlayReward getInstance() {
        return instance;
    }

    public String getPrefix() {
        return getConfig().getString("prefix", "<gray>[<gradient:#FFAA00:#FF5500><bold>PlayReward</bold></gradient><gray>] <reset>");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public PlanHook getPlanHook() {
        return planHook;
    }

    public GeyserHook getGeyserHook() {
        return geyserHook;
    }

    public PlaytimeService getPlaytimeService() {
        return playtimeService;
    }

    public StreakService getStreakService() {
        return streakService;
    }

    public RewardClaimService getRewardClaimService() {
        return rewardClaimService;
    }
}

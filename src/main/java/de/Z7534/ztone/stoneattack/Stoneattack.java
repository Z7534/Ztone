package de.Z7534.ztone.stoneattack;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.stoneattack.commands.LeaderboardCommand;
import de.Z7534.ztone.stoneattack.commands.PerformanceCommand;
import de.Z7534.ztone.stoneattack.commands.PlayerLimitCommand;
import de.Z7534.ztone.stoneattack.commands.StoneattackCommand;
import de.Z7534.ztone.stoneattack.config.ConfigManager;
import de.Z7534.ztone.stoneattack.config.MessagesManager;
import de.Z7534.ztone.stoneattack.placeholder.StoneattackPlaceholderExpansion;
import de.Z7534.ztone.stoneattack.modules.leaderboard.LeaderboardModule;
import de.Z7534.ztone.stoneattack.listeners.leaderboard.SessionResetListener;
import de.Z7534.ztone.stoneattack.modules.performance.PerformanceModule;
import de.Z7534.ztone.stoneattack.modules.playerlimit.PlayerLimitModule;
import de.Z7534.ztone.stoneattack.listeners.playerlimit.PlayerLoginListener;
import de.Z7534.ztone.stoneattack.listeners.playerlimit.PlayerQuitListener;
import de.Z7534.ztone.stoneattack.listeners.playerlimit.ServerListPingListener;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

public class Stoneattack {

    private final Ztone plugin;

    private ConfigManager configManager;
    private MessagesManager messagesManager;
    private PlayerLimitModule playerLimitModule;
    private PerformanceModule performanceModule;
    private LeaderboardModule leaderboardModule;
    private Economy economy;

    public Stoneattack(Ztone plugin) {
        this.plugin = plugin;
    }

    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.configManager.load();
        this.messagesManager = new MessagesManager(this);
        this.messagesManager.load();

        setupEconomy();
        setupPlaceholderApi();
        loadModules();
        registerCommands();

        getLogger().info("Stoneattack-Modul wurde aktiviert.");
    }

    public void onDisable() {
        if (playerLimitModule != null) {
            playerLimitModule.disable();
        }
        if (performanceModule != null) {
            performanceModule.disable();
        }
        if (leaderboardModule != null) {
            leaderboardModule.disable();
        }
        getLogger().info("Stoneattack-Modul wurde deaktiviert.");
    }

    private void loadModules() {
        if (configManager.get().getBoolean("module.playerlimit", true)) {
            playerLimitModule = new PlayerLimitModule(this);
            playerLimitModule.enable();
            getServer().getPluginManager().registerEvents(new PlayerLoginListener(this), plugin);
            getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), plugin);
            getServer().getPluginManager().registerEvents(new ServerListPingListener(this), plugin);
        }

        if (configManager.get().getBoolean("module.performance", true)) {
            performanceModule = new PerformanceModule(this);
            performanceModule.enable();
        }

        if (configManager.get().getBoolean("module.leaderboard", true)) {
            if (economy == null) {
                getLogger().severe(messagesManager.raw("leaderboard.money-unavailable-console"));
            }
            leaderboardModule = new LeaderboardModule(this);
            leaderboardModule.enable();
            getServer().getPluginManager().registerEvents(new SessionResetListener(this), plugin);
        }
    }

    private void registerCommands() {
        var stoneattackCommand = new StoneattackCommand(this);
        var cmd = getCommand("stoneattack");
        if (cmd != null) {
            cmd.setExecutor(stoneattackCommand);
            cmd.setTabCompleter(stoneattackCommand);
        }

        if (playerLimitModule != null) {
            var plCmd = getCommand("playerlimit");
            if (plCmd != null) {
                var pCmd = new PlayerLimitCommand(this);
                plCmd.setExecutor(pCmd);
                plCmd.setTabCompleter(pCmd);
            }
        }

        if (performanceModule != null) {
            var perfCmd = getCommand("performance");
            if (perfCmd != null) {
                var pCmd = new PerformanceCommand(this);
                perfCmd.setExecutor(pCmd);
            }
        }

        if (leaderboardModule != null) {
            var lbCmd = getCommand("leaderboard");
            if (lbCmd != null) {
                var lCmd = new LeaderboardCommand(this);
                lbCmd.setExecutor(lCmd);
                lbCmd.setTabCompleter(lCmd);
            }
        }
    }

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return;
        }
        this.economy = rsp.getProvider();
    }

    private void setupPlaceholderApi() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new StoneattackPlaceholderExpansion(this).register();
        }
    }

    public void reloadAll() {
        if (configManager != null) {
            try {
                configManager.reload();
            } catch (Throwable t) {
                getLogger().severe("Fehler beim Neuladen von configManager: " + t.getMessage());
            }
        }
        if (messagesManager != null) {
            try {
                messagesManager.reload();
            } catch (Throwable t) {
                getLogger().severe("Fehler beim Neuladen von messagesManager: " + t.getMessage());
            }
        }

        if (playerLimitModule != null) {
            try {
                playerLimitModule.reload();
            } catch (Throwable t) {
                getLogger().severe("Fehler beim Neuladen von PlayerLimit: " + t.getMessage());
            }
        }
        if (performanceModule != null) {
            try {
                performanceModule.reload();
            } catch (Throwable t) {
                getLogger().severe("Fehler beim Neuladen von Performance: " + t.getMessage());
            }
        }
        if (leaderboardModule != null) {
            try {
                leaderboardModule.reload();
            } catch (Throwable t) {
                getLogger().severe("Fehler beim Neuladen von Leaderboard: " + t.getMessage());
            }
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessagesManager getMessagesManager() {
        return messagesManager;
    }

    public PlayerLimitModule getPlayerLimitModule() {
        return playerLimitModule;
    }

    public PerformanceModule getPerformanceModule() {
        return performanceModule;
    }

    public LeaderboardModule getLeaderboardModule() {
        return leaderboardModule;
    }

    public Economy getEconomy() {
        return economy;
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

    public File getDataFolder() {
        return new File(plugin.getDataFolder(), "stoneattack");
    }

    public InputStream getResource(String filename) {
        return plugin.getResource("stoneattack/" + filename);
    }

    public void saveResource(String resourcePath, boolean replace) {
        File out = new File(getDataFolder(), resourcePath);
        if (out.exists() && !replace) {
            return;
        }
        File parent = out.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (InputStream in = getResource(resourcePath)) {
            if (in == null) {
                getLogger().warning("Ressource 'stoneattack/" + resourcePath + "' nicht im Jar gefunden!");
                return;
            }
            Files.copy(in, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            getLogger().severe("Konnte Ressource 'stoneattack/" + resourcePath + "' nicht speichern: " + e.getMessage());
        }
    }

    public PluginCommand getCommand(String name) {
        return plugin.getCommand(name);
    }

    public PluginDescriptionFile getDescription() {
        return plugin.getDescription();
    }
}

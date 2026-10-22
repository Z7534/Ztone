package de.Z7534.ztone.stonelog;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.stonelog.command.StonelogCommand;
import de.Z7534.ztone.stonelog.config.ConfigManager;
import de.Z7534.ztone.stonelog.discord.DiscordWebhookSender;
import de.Z7534.ztone.stonelog.listener.ChatListener;
import de.Z7534.ztone.stonelog.listener.CommandListener;
import de.Z7534.ztone.stonelog.listener.DeathListener;
import de.Z7534.ztone.stonelog.listener.JoinLeaveListener;
import de.Z7534.ztone.util.ConfigUtil;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.logging.Logger;

public class Stonelog {

    private static Stonelog instance;

    private final Ztone plugin;
    private ConfigManager configManager;
    private DiscordWebhookSender webhookSender;
    private FileConfiguration config;

    public Stonelog(Ztone plugin) {
        this.plugin = plugin;
    }

    public void onEnable() {
        instance = this;

        reloadConfig();
        this.configManager = new ConfigManager(this);
        this.webhookSender = new DiscordWebhookSender(this);

        getServer().getPluginManager().registerEvents(new CommandListener(this), plugin);
        new ChatListener(this).register();
        getServer().getPluginManager().registerEvents(new DeathListener(this), plugin);
        getServer().getPluginManager().registerEvents(new JoinLeaveListener(this), plugin);

        StonelogCommand commandExecutor = new StonelogCommand(this);
        var pluginCommand = getCommand("stonelog");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(commandExecutor);
            pluginCommand.setTabCompleter(commandExecutor);
        }

        getLogger().info("Stonelog-Modul wurde aktiviert.");
    }

    public void onDisable() {
        if (webhookSender != null) {
            webhookSender.shutdown();
        }
        getLogger().info("Stonelog-Modul wurde deaktiviert.");
    }

    public void reload() {
        reloadConfig();
        this.configManager.reload();
    }

    public File getDataFolder() {
        return new File(plugin.getDataFolder(), "stonelog");
    }

    public FileConfiguration getConfig() {
        if (config == null) {
            reloadConfig();
        }
        return config;
    }

    public void reloadConfig() {
        File file = new File(getDataFolder(), "config.yml");
        this.config = ConfigUtil.loadAndSync(plugin, file, "stonelog/config.yml");
    }

    public void saveDefaultConfig() {
        reloadConfig();
    }

    public Ztone getPlugin() {
        return plugin;
    }

    public Logger getLogger() {
        return plugin.getLogger();
    }

    public Server getServer() {
        return plugin.getServer();
    }

    public PluginCommand getCommand(String name) {
        return plugin.getCommand(name);
    }

    public static Stonelog getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DiscordWebhookSender getWebhookSender() {
        return webhookSender;
    }
}

package de.Z7534.ztone.stonelog;

import de.Z7534.ztone.Ztone;
import de.Z7534.ztone.stonelog.command.StonelogCommand;
import de.Z7534.ztone.stonelog.config.ConfigManager;
import de.Z7534.ztone.stonelog.discord.DiscordWebhookSender;
import de.Z7534.ztone.stonelog.listener.ChatListener;
import de.Z7534.ztone.stonelog.listener.CommandListener;
import de.Z7534.ztone.stonelog.listener.DeathListener;
import de.Z7534.ztone.stonelog.listener.JoinLeaveListener;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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

        saveDefaultConfig();
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
        if (!file.exists()) {
            saveDefaultConfig();
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public void saveDefaultConfig() {
        File out = new File(getDataFolder(), "config.yml");
        if (!out.exists()) {
            out.getParentFile().mkdirs();
            try (InputStream in = plugin.getResource("stonelog/config.yml")) {
                if (in != null) {
                    Files.copy(in, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception e) {
                getLogger().warning("Konnte stonelog/config.yml nicht speichern: " + e.getMessage());
            }
        }
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
